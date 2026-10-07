package com.apexstore.nodo2;

import com.apexstore.contratos.*;
import com.apexstore.ice.ConversorIce;
import com.apexstore.ice.pagos.Item;
import com.apexstore.ice.pagos.IRepositorioPagosPrx;
import com.zeroc.Ice.Communicator;
import java.sql.SQLException;
import java.util.*;

/** Cliente ICE de IRepositorioPagos (Nodo 4). Mantiene las firmas que usaba el JDBC anterior. */
public final class RepositorioPagosIce {
    private final IRepositorioPagosPrx prx;

    public RepositorioPagosIce(Communicator comunicador) {
        this.prx = IRepositorioPagosPrx.uncheckedCast(comunicador.propertyToProxy("Repositorio.Proxy"));
    }

    public record RegistroPago(boolean nueva, String transaccionExterna, EstadoPago estado, Map<String, String> instrucciones) { }
    public record Pending(String claveIdempotencia, MedioPago medio) { }

    public List<Map<String, Object>> productos() throws SQLException {
        var out = new ArrayList<Map<String, Object>>();
        for (var p : prx.productos()) {
            out.add(Map.of("productoId", p.id, "nombre", p.nombre, "precioMenor", p.precio.valorMenor, "moneda", p.precio.moneda, "stock", p.stock));
        }
        return out;
    }

    public Map<String, Object> crearOrden(List<Item> items) throws SQLException {
        try {
            return orden(prx.crearOrden(items.toArray(new Item[0])));
        } catch (com.apexstore.ice.pagos.SolicitudInvalida e) {
            throw new SQLException(e.motivo);
        }
    }

    public Map<String, Object> obtenerOrden(String id) throws SQLException {
        try {
            return orden(prx.obtenerOrden(id));
        } catch (com.apexstore.ice.pagos.OrdenNoEncontrada e) {
            return null;
        }
    }

    public RegistroPago registrarPendiente(SolicitudPago s) throws SQLException {
        try {
            var r = prx.registrarPendiente(ConversorIce.aSlice(s));
            var respuesta = ConversorIce.desdeSlice(r.returnValue);
            return new RegistroPago(r.nueva, vacioANull(respuesta.idTransaccionExterna()), respuesta.estado(), respuesta.instrucciones());
        } catch (com.apexstore.ice.pagos.ConflictoIdempotencia e) {
            throw new SQLException("Idempotency-Key reutilizada con contenido distinto", "23505");
        } catch (com.apexstore.ice.pagos.SolicitudInvalida e) {
            throw new SQLException(e.motivo);
        }
    }

    public void guardarRespuesta(String clave, RespuestaPago r) {
        prx.guardarRespuesta(clave, new com.apexstore.ice.pagos.RespuestaPago(texto(r.idTransaccionExterna()), r.estado().name(), r.instrucciones()));
    }

    public boolean aplicarResultado(ResultadoPago r) {
        return prx.aplicarResultado(ConversorIce.aSlice(r));
    }

    /** El parámetro detalle no se persiste: el repositorio anterior tampoco lo usaba. */
    public void marcarFallida(String clave, String origen, String detalle) {
        prx.marcarFallida(clave, origen);
    }

    public String ordenDeClave(String clave) {
        return vacioANull(prx.ordenDeClave(clave));
    }

    public List<Pending> pendientes() {
        var out = new ArrayList<Pending>();
        for (var p : prx.pendientes()) {
            out.add(new Pending(p.claveIdempotencia, MedioPago.valueOf(p.medio)));
        }
        return out;
    }

    public int expirarVencidas() {
        return prx.expirarVencidas();
    }

    private static Map<String, Object> orden(com.apexstore.ice.pagos.Orden o) {
        var m = new LinkedHashMap<String, Object>();
        m.put("id", o.id);
        m.put("estado", o.estado);
        m.put("totalMenor", o.total.valorMenor);
        m.put("moneda", o.total.moneda);
        m.put("pagoEstado", vacioANull(o.estadoPago));
        m.put("medio", vacioANull(o.medio));
        m.put("referencia", vacioANull(o.referencia));
        return m;
    }

    private static String vacioANull(String s) { return s == null || s.isEmpty() ? null : s; }
    private static String texto(String s) { return s == null ? "" : s; }
}
