package com.apexstore.nodo4;

import com.apexstore.contratos.EstadoPago;
import com.apexstore.contratos.MedioPago;
import com.apexstore.contratos.RespuestaPago;
import com.apexstore.contratos.ResultadoPago;
import com.apexstore.contratos.SolicitudPago;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * Transacciones de pago: registro, idempotencia, resultados asincronos y marcas de fallo. Atiende RAS-03: la clave de
 * idempotencia, el UNIQUE y el advisory lock evitan el doble cobro; cada operacion es una transaccion local.
 */
public final class RepositorioTransacciones {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SIN_DETALLE = "{}";

    private final DataSource dataSource;

    public RepositorioTransacciones(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public record RegistroPago(UUID id, boolean nueva, String transaccionExterna, EstadoPago estado,
                               Map<String, String> instrucciones) { }

    public record Pending(String claveIdempotencia, MedioPago medio) { }

    private record Transaccion(UUID id, UUID orden, EstadoPago estado) { }

    public RegistroPago registrarPendiente(SolicitudPago solicitud) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                bloquearClave(c, solicitud.claveIdempotencia());
                String requestHash = requestHash(solicitud);
                RegistroPago existente = buscarExistente(c, solicitud, requestHash);
                if (existente != null) {
                    c.commit();
                    return existente;
                }
                UUID id = UUID.randomUUID();
                verificarOrdenPagable(c, solicitud.idOrden());
                insertarPendiente(c, id, solicitud, requestHash);
                marcarOrdenPagoPendiente(c, solicitud.idOrden());
                auditoria(c, id, null, EstadoPago.PENDIENTE, "checkout");
                c.commit();
                return new RegistroPago(id, true, null, EstadoPago.PENDIENTE, Map.of());
            } catch (Exception e) {
                c.rollback();
                throw aSqlException(e);
            }
        }
    }

    public void guardarRespuesta(String clave, RespuestaPago respuesta) throws SQLException {
        String instrucciones = aJson(respuesta.instrucciones());
        String sql = "UPDATE transacciones_pago SET id_transaccion_externa=COALESCE(id_transaccion_externa,?), "
                + "respuesta_instrucciones=CASE WHEN respuesta_instrucciones='{}'::jsonb THEN ?::jsonb ELSE respuesta_instrucciones END, "
                + "actualizada_en=now() WHERE clave_idempotencia=?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, respuesta.idTransaccionExterna());
            p.setString(2, instrucciones);
            p.setString(3, clave);
            p.executeUpdate();
        }
    }

    /** Aplica un resultado una sola vez por idEvento (RAS-03). Devuelve false si ya se habia procesado. */
    public boolean aplicarResultado(ResultadoPago resultado) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                if (!registrarEvento(c, resultado.idEvento())) {
                    c.commit();
                    return false;
                }
                aplicarSiCorresponde(c, resultado);
                c.commit();
                return true;
            } catch (Exception e) {
                c.rollback();
                throw aSqlException(e);
            }
        }
    }

    public String ordenDeClave(String clave) throws SQLException {
        String sql = "SELECT orden_id::text FROM transacciones_pago WHERE clave_idempotencia=?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, clave);
            try (ResultSet r = p.executeQuery()) {
                return r.next() ? r.getString(1) : null;
            }
        }
    }

    public List<Pending> pendientes() throws SQLException {
        var out = new ArrayList<Pending>();
        String sql = "SELECT clave_idempotencia,medio FROM transacciones_pago WHERE estado='PENDIENTE' "
                + "AND actualizada_en < now()-interval '5 seconds' AND vence_en>now() ORDER BY actualizada_en LIMIT 100";
        try (Connection c = dataSource.getConnection();
             PreparedStatement p = c.prepareStatement(sql);
             ResultSet r = p.executeQuery()) {
            while (r.next()) {
                out.add(new Pending(r.getString(1), MedioPago.valueOf(r.getString(2))));
            }
        }
        return out;
    }

    /** Marca fallida una transaccion pendiente y devuelve la orden a CREADA para que pueda pagarse de nuevo. */
    public void marcarFallida(String clave, String origen) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                UUID tx = getTx(c, clave);
                if (tx != null) {
                    actualizarFallida(c, tx);
                    auditoria(c, tx, EstadoPago.PENDIENTE, EstadoPago.FALLIDA, origen);
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw aSqlException(e);
            }
        }
    }

    private static void bloquearClave(Connection c, String clave) throws SQLException {
        try (PreparedStatement lock = c.prepareStatement("SELECT pg_advisory_xact_lock(hashtext(?))")) {
            lock.setString(1, clave);
            lock.executeQuery().close();
        }
    }

    private static RegistroPago buscarExistente(Connection c, SolicitudPago s, String requestHash) throws SQLException {
        String sql = "SELECT id, orden_id, medio, monto_menor, moneda, request_hash, id_transaccion_externa, estado, "
                + "respuesta_instrucciones FROM transacciones_pago WHERE clave_idempotencia=? FOR UPDATE";
        try (PreparedStatement q = c.prepareStatement(sql)) {
            q.setString(1, s.claveIdempotencia());
            try (ResultSet r = q.executeQuery()) {
                if (!r.next()) {
                    return null;
                }
                if (!mismoContenido(r, s, requestHash)) {
                    throw new SQLException("Idempotency-Key reutilizada con contenido distinto", "23505");
                }
                return new RegistroPago(r.getObject("id", UUID.class), false, r.getString("id_transaccion_externa"),
                        EstadoPago.valueOf(r.getString("estado")), leerInstrucciones(r.getString("respuesta_instrucciones")));
            }
        }
    }

    private static boolean mismoContenido(ResultSet r, SolicitudPago s, String requestHash) throws SQLException {
        return r.getString("orden_id").equals(s.idOrden())
                && r.getString("medio").equals(s.medio().name())
                && r.getLong("monto_menor") == s.monto().valorMenor()
                && r.getString("moneda").trim().equals(s.monto().moneda())
                && r.getString("request_hash").trim().equals(requestHash);
    }

    private static void verificarOrdenPagable(Connection c, String idOrden) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT estado FROM ordenes WHERE id=? FOR UPDATE")) {
            p.setObject(1, UUID.fromString(idOrden));
            try (ResultSet r = p.executeQuery()) {
                if (!r.next() || !r.getString(1).equals("CREADA")) {
                    throw new SQLException("La orden no esta disponible para pago");
                }
            }
        }
    }

    private static void insertarPendiente(Connection c, UUID id, SolicitudPago s, String requestHash) throws SQLException {
        String sql = "INSERT INTO transacciones_pago(id,orden_id,medio,clave_idempotencia,request_hash,estado,monto_menor,moneda,vence_en) "
                + "VALUES (?,?,?,?,?,?,?,?,now()+interval '15 minutes')";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setObject(1, id);
            p.setObject(2, UUID.fromString(s.idOrden()));
            p.setString(3, s.medio().name());
            p.setString(4, s.claveIdempotencia());
            p.setString(5, requestHash);
            p.setString(6, EstadoPago.PENDIENTE.name());
            p.setLong(7, s.monto().valorMenor());
            p.setString(8, s.monto().moneda());
            p.executeUpdate();
        }
    }

    private static void marcarOrdenPagoPendiente(Connection c, String idOrden) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("UPDATE ordenes SET estado='PAGO_PENDIENTE',version=version+1 WHERE id=?")) {
            p.setObject(1, UUID.fromString(idOrden));
            p.executeUpdate();
        }
    }

    private static boolean registrarEvento(Connection c, String idEvento) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("INSERT INTO eventos_procesados(id_evento) VALUES (?) ON CONFLICT DO NOTHING")) {
            p.setString(1, idEvento);
            return p.executeUpdate() != 0;
        }
    }

    private static void aplicarSiCorresponde(Connection c, ResultadoPago resultado) throws SQLException {
        Transaccion t = leerTransaccion(c, resultado.claveIdempotencia());
        if (!debeAplicarse(t.estado(), resultado.estado())) {
            return;
        }
        EstadoPago nuevo = t.estado() == EstadoPago.EXPIRADA ? EstadoPago.REEMBOLSO_PENDIENTE : resultado.estado();
        actualizarEstado(c, t.id(), nuevo, resultado);
        if (nuevo == EstadoPago.CONFIRMADA) {
            ejecutar(c, "UPDATE ordenes SET estado='PAGADA',version=version+1 WHERE id=?", t.orden());
        }
        if (nuevo == EstadoPago.FALLIDA) {
            ejecutar(c, "UPDATE ordenes SET estado='CREADA',version=version+1 WHERE id=? AND estado='PAGO_PENDIENTE'", t.orden());
        }
        if (nuevo == EstadoPago.REEMBOLSO_PENDIENTE) {
            auditoria(c, t.id(), EstadoPago.EXPIRADA, EstadoPago.REEMBOLSO_PENDIENTE, "callback-tardio");
            ejecutar(c, "UPDATE transacciones_pago SET estado='REEMBOLSADA',actualizada_en=now() WHERE id=?", t.id());
            nuevo = EstadoPago.REEMBOLSADA;
        }
        String detalle = "{\"idEvento\":\"" + jsonSafe(resultado.idEvento()) + "\"}";
        auditoriaConDetalle(c, t.id(), t.estado(), nuevo, "callback", detalle);
    }

    /** Solo se aplica si la transaccion sigue pendiente, o si llega un pago confirmado tras expirar (callback tardio). */
    private static boolean debeAplicarse(EstadoPago actual, EstadoPago recibido) {
        return actual == EstadoPago.PENDIENTE
                || (actual == EstadoPago.EXPIRADA && recibido == EstadoPago.CONFIRMADA);
    }

    private static Transaccion leerTransaccion(Connection c, String clave) throws SQLException {
        try (PreparedStatement q = c.prepareStatement("SELECT id,orden_id,estado FROM transacciones_pago WHERE clave_idempotencia=? FOR UPDATE")) {
            q.setString(1, clave);
            try (ResultSet r = q.executeQuery()) {
                if (!r.next()) {
                    throw new SQLException("Transaccion desconocida");
                }
                return new Transaccion(r.getObject(1, UUID.class), r.getObject(2, UUID.class), EstadoPago.valueOf(r.getString(3)));
            }
        }
    }

    private static void actualizarEstado(Connection c, UUID tx, EstadoPago nuevo, ResultadoPago resultado) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("UPDATE transacciones_pago SET estado=?,id_transaccion_externa=?,actualizada_en=now() WHERE id=?")) {
            p.setString(1, nuevo.name());
            p.setString(2, resultado.idTransaccionExterna());
            p.setObject(3, tx);
            p.executeUpdate();
        }
    }

    private static UUID getTx(Connection c, String clave) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT id FROM transacciones_pago WHERE clave_idempotencia=? AND estado='PENDIENTE' FOR UPDATE")) {
            p.setString(1, clave);
            try (ResultSet r = p.executeQuery()) {
                return r.next() ? r.getObject(1, UUID.class) : null;
            }
        }
    }

    private static void actualizarFallida(Connection c, UUID tx) throws SQLException {
        ejecutar(c, "UPDATE transacciones_pago SET estado='FALLIDA',actualizada_en=now() WHERE id=?", tx);
        ejecutar(c, "UPDATE ordenes SET estado='CREADA',version=version+1 "
                + "WHERE id=(SELECT orden_id FROM transacciones_pago WHERE id=?) AND estado='PAGO_PENDIENTE'", tx);
    }

    private static void ejecutar(Connection c, String sql, UUID id) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setObject(1, id);
            p.executeUpdate();
        }
    }

    private static void auditoria(Connection c, UUID tx, EstadoPago anterior, EstadoPago nuevo, String origen) throws SQLException {
        auditoriaConDetalle(c, tx, anterior, nuevo, origen, SIN_DETALLE);
    }

    private static void auditoriaConDetalle(Connection c, UUID tx, EstadoPago anterior, EstadoPago nuevo, String origen,
                                            String detalle) throws SQLException {
        String sql = "INSERT INTO bitacora_auditoria(transaccion_id,estado_anterior,estado_nuevo,origen,detalle) VALUES (?,?,?,?,?::jsonb)";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setObject(1, tx);
            p.setString(2, anterior == null ? null : anterior.name());
            p.setString(3, nuevo.name());
            p.setString(4, origen);
            p.setString(5, detalle);
            p.executeUpdate();
        }
    }

    private static String requestHash(SolicitudPago s) throws Exception {
        String contenido = s.idOrden() + "|" + s.medio() + "|" + s.monto().valorMenor() + "|" + s.monto().moneda() + "|" + s.tokenPago();
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(contenido.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }

    private static String aJson(Map<String, String> instrucciones) throws SQLException {
        try {
            return JSON.writeValueAsString(instrucciones);
        } catch (JsonProcessingException e) {
            throw new SQLException("No se pudieron guardar las instrucciones simuladas", e);
        }
    }

    private static Map<String, String> leerInstrucciones(String json) throws SQLException {
        try {
            return JSON.readValue(json, new TypeReference<Map<String, String>>() { });
        } catch (JsonProcessingException e) {
            throw new SQLException("Instrucciones guardadas invalidas", e);
        }
    }

    private static String jsonSafe(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static SQLException aSqlException(Exception e) {
        return e instanceof SQLException sql ? sql : new SQLException(e);
    }
}
