package com.apexstore.nodo2;

import com.apexstore.contratos.*;
import javax.sql.DataSource;
import java.sql.*;
import java.util.UUID;
import java.util.*;

/** Único componente de negocio autorizado a leer o escribir PostgreSQL. */
public final class RepositorioTransacciones {
    private final DataSource dataSource;
    public RepositorioTransacciones(DataSource dataSource) { this.dataSource = dataSource; }

    public RegistroPago registrarPendiente(SolicitudPago solicitud) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                try(PreparedStatement lock=c.prepareStatement("SELECT pg_advisory_xact_lock(hashtext(?))")){lock.setString(1,solicitud.claveIdempotencia());lock.executeQuery().close();}
                String requestHash=requestHash(solicitud);
                try (PreparedStatement q = c.prepareStatement("SELECT id, orden_id, medio, monto_menor, moneda, request_hash, id_transaccion_externa, estado, respuesta_instrucciones FROM transacciones_pago WHERE clave_idempotencia=? FOR UPDATE")) {
                    q.setString(1, solicitud.claveIdempotencia());
                    try (ResultSet r=q.executeQuery()) { if (r.next()) {
                        boolean same = r.getString("orden_id").equals(solicitud.idOrden()) && r.getString("medio").equals(solicitud.medio().name()) && r.getLong("monto_menor")==solicitud.monto().valorMenor() && r.getString("moneda").trim().equals(solicitud.monto().moneda()) && r.getString("request_hash").trim().equals(requestHash);
                        if (!same) throw new SQLException("Idempotency-Key reutilizada con contenido distinto", "23505");
                        Map<String,String> instructions = readInstructions(r.getString("respuesta_instrucciones"));
                        c.commit(); return new RegistroPago(r.getObject("id", UUID.class),false,r.getString("id_transaccion_externa"),EstadoPago.valueOf(r.getString("estado")),instructions);
                    } }
                }
                UUID id=UUID.randomUUID();
                try(PreparedStatement p=c.prepareStatement("SELECT estado FROM ordenes WHERE id=? FOR UPDATE")){p.setObject(1,UUID.fromString(solicitud.idOrden()));try(ResultSet r=p.executeQuery()){if(!r.next()||!r.getString(1).equals("CREADA"))throw new SQLException("La orden no está disponible para pago");}}
                try (PreparedStatement p=c.prepareStatement("INSERT INTO transacciones_pago(id,orden_id,medio,clave_idempotencia,request_hash,estado,monto_menor,moneda,vence_en) VALUES (?,?,?,?,?,?,?,?,now()+interval '15 minutes')")) {
                    p.setObject(1,id); p.setObject(2,UUID.fromString(solicitud.idOrden())); p.setString(3,solicitud.medio().name()); p.setString(4,solicitud.claveIdempotencia()); p.setString(5,requestHash); p.setString(6,EstadoPago.PENDIENTE.name()); p.setLong(7,solicitud.monto().valorMenor()); p.setString(8,solicitud.monto().moneda()); p.executeUpdate();
                }
                try(PreparedStatement p=c.prepareStatement("UPDATE ordenes SET estado='PAGO_PENDIENTE',version=version+1 WHERE id=?")){p.setObject(1,UUID.fromString(solicitud.idOrden()));p.executeUpdate();}
                auditoria(c,id,null,EstadoPago.PENDIENTE,"checkout","{}"); c.commit(); return new RegistroPago(id,true,null,EstadoPago.PENDIENTE,Map.of());
            } catch (Exception e) { c.rollback(); if (e instanceof SQLException se) throw se; throw new SQLException(e); }
        }
    }
    public void guardarRespuesta(String key, RespuestaPago response) throws SQLException {
        String instructions;
        try { instructions = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(response.instrucciones()); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new SQLException("No se pudieron guardar las instrucciones simuladas", e); }
        try(Connection c=dataSource.getConnection();PreparedStatement p=c.prepareStatement("UPDATE transacciones_pago SET id_transaccion_externa=COALESCE(id_transaccion_externa,?), respuesta_instrucciones=CASE WHEN respuesta_instrucciones='{}'::jsonb THEN ?::jsonb ELSE respuesta_instrucciones END, actualizada_en=now() WHERE clave_idempotencia=?")){
            p.setString(1,response.idTransaccionExterna()); p.setString(2,instructions); p.setString(3,key); p.executeUpdate();
        }
    }
    private static Map<String,String> readInstructions(String json) throws SQLException {
        try { return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {}); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new SQLException("Instrucciones guardadas inválidas", e); }
    }
    public record RegistroPago(UUID id,boolean nueva,String transaccionExterna,EstadoPago estado,Map<String,String> instrucciones) { }

    public List<Map<String,Object>> productos() throws SQLException {
        var out=new ArrayList<Map<String,Object>>();
        try(Connection c=dataSource.getConnection();PreparedStatement p=c.prepareStatement("SELECT id,nombre,precio_menor,moneda,stock FROM productos ORDER BY id");ResultSet r=p.executeQuery()){
            while(r.next())out.add(Map.of("productoId",r.getLong(1),"nombre",r.getString(2),"precioMenor",r.getLong(3),"moneda",r.getString(4).trim(),"stock",r.getInt(5)));
        } return out;
    }

    public Map<String,Object> crearOrden(List<Item> items) throws SQLException {
        if(items.isEmpty())throw new SQLException("La orden requiere productos");
        if(items.stream().anyMatch(i->i.productoId()<=0||i.cantidad()<=0))throw new SQLException("Producto y cantidad deben ser positivos");
        items=items.stream().collect(java.util.stream.Collectors.toMap(Item::productoId,Item::cantidad,Integer::sum,LinkedHashMap::new)).entrySet().stream().map(e->new Item(e.getKey(),e.getValue())).toList();
        try(Connection c=dataSource.getConnection()){
            c.setAutoCommit(false);
            try{
                UUID id=UUID.randomUUID();long total=0;String currency=null;
                for(Item item:items){try(PreparedStatement p=c.prepareStatement("SELECT precio_menor,moneda FROM productos WHERE id=?")){p.setLong(1,item.productoId());try(ResultSet r=p.executeQuery()){if(!r.next())throw new SQLException("Producto inexistente");long price=r.getLong(1);String curr=r.getString(2).trim();if(currency!=null&&!currency.equals(curr))throw new SQLException("Una orden debe usar una sola moneda");currency=curr;total=Math.addExact(total,Math.multiplyExact(price,item.cantidad()));}}}
                try(PreparedStatement p=c.prepareStatement("INSERT INTO ordenes(id,estado,total_menor,moneda) VALUES(?,'CREADA',?,?)")){p.setObject(1,id);p.setLong(2,total);p.setString(3,currency);p.executeUpdate();}
                for(Item item:items){try(PreparedStatement p=c.prepareStatement("UPDATE productos SET stock=stock-? WHERE id=? AND stock>=?")){p.setInt(1,item.cantidad());p.setLong(2,item.productoId());p.setInt(3,item.cantidad());if(p.executeUpdate()!=1)throw new SQLException("Stock insuficiente");}try(PreparedStatement p=c.prepareStatement("INSERT INTO reservas_stock(orden_id,producto_id,cantidad) VALUES(?,?,?)")){p.setObject(1,id);p.setLong(2,item.productoId());p.setInt(3,item.cantidad());p.executeUpdate();}}
                c.commit();return Map.of("id",id.toString(),"estado","CREADA","totalMenor",total,"moneda",currency);
            }catch(Exception e){c.rollback();if(e instanceof SQLException se)throw se;throw new SQLException(e);}
        }
    }

    public Map<String,Object> obtenerOrden(String id) throws SQLException {
        String sql="SELECT o.id,o.estado,o.total_menor,o.moneda,t.estado,t.medio,t.clave_idempotencia,t.id_transaccion_externa FROM ordenes o LEFT JOIN transacciones_pago t ON t.orden_id=o.id WHERE o.id=? ORDER BY t.creada_en DESC LIMIT 1";
        try(Connection c=dataSource.getConnection();PreparedStatement p=c.prepareStatement(sql)){p.setObject(1,UUID.fromString(id));try(ResultSet r=p.executeQuery()){if(!r.next())return null;Map<String,Object> m=new LinkedHashMap<>();m.put("id",r.getString(1));m.put("estado",r.getString(2));m.put("totalMenor",r.getLong(3));m.put("moneda",r.getString(4).trim());m.put("pagoEstado",r.getString(5));m.put("medio",r.getString(6));m.put("idempotencyKey",r.getString(7));m.put("referencia",r.getString(8));return m;}}
    }
    public String ordenDeClave(String clave) throws SQLException {try(Connection c=dataSource.getConnection();PreparedStatement p=c.prepareStatement("SELECT orden_id::text FROM transacciones_pago WHERE clave_idempotencia=?")){p.setString(1,clave);try(ResultSet r=p.executeQuery()){return r.next()?r.getString(1):null;}}}
    public List<Pending> pendientes() throws SQLException {List<Pending> out=new ArrayList<>();try(Connection c=dataSource.getConnection();PreparedStatement p=c.prepareStatement("SELECT clave_idempotencia,medio FROM transacciones_pago WHERE estado='PENDIENTE' AND actualizada_en < now()-interval '5 seconds' AND vence_en>now() ORDER BY actualizada_en LIMIT 100");ResultSet r=p.executeQuery()){while(r.next())out.add(new Pending(r.getString(1),MedioPago.valueOf(r.getString(2))));}return out;}
    public int expirarVencidas() throws SQLException {
        try(Connection c=dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                int count=0;
                try(PreparedStatement p=c.prepareStatement("SELECT id,orden_id FROM transacciones_pago WHERE estado='PENDIENTE' AND vence_en<=now() FOR UPDATE SKIP LOCKED");ResultSet r=p.executeQuery()) {
                    while(r.next()) {
                        UUID tx=r.getObject(1,UUID.class),order=r.getObject(2,UUID.class);
                        try(PreparedStatement u=c.prepareStatement("UPDATE transacciones_pago SET estado='EXPIRADA',actualizada_en=now() WHERE id=?")){u.setObject(1,tx);u.executeUpdate();}
                        try(PreparedStatement u=c.prepareStatement("UPDATE productos p SET stock=p.stock+r.cantidad FROM reservas_stock r WHERE r.orden_id=? AND r.producto_id=p.id AND r.liberada=false")){u.setObject(1,order);u.executeUpdate();}
                        try(PreparedStatement u=c.prepareStatement("UPDATE reservas_stock SET liberada=true WHERE orden_id=?")){u.setObject(1,order);u.executeUpdate();}
                        try(PreparedStatement u=c.prepareStatement("UPDATE ordenes SET estado='EXPIRADA',version=version+1 WHERE id=?")){u.setObject(1,order);u.executeUpdate();}
                        auditoria(c,tx,EstadoPago.PENDIENTE,EstadoPago.EXPIRADA,"reconciliador","{}");count++;
                    }
                }
                c.commit();return count;
            } catch(Exception e) {c.rollback();if(e instanceof SQLException se)throw se;throw new SQLException(e);}
        }
    }
    public record Pending(String claveIdempotencia,MedioPago medio) { }

    public void marcarFallida(String clave,String origen,String detalle) throws SQLException {
        try(Connection c=dataSource.getConnection()){c.setAutoCommit(false);try{UUID tx=getTx(c,clave,EstadoPago.PENDIENTE);if(tx!=null){try(PreparedStatement p=c.prepareStatement("UPDATE transacciones_pago SET estado='FALLIDA',actualizada_en=now() WHERE id=?")){p.setObject(1,tx);p.executeUpdate();}try(PreparedStatement p=c.prepareStatement("UPDATE ordenes SET estado='CREADA',version=version+1 WHERE id=(SELECT orden_id FROM transacciones_pago WHERE id=?) AND estado='PAGO_PENDIENTE'")){p.setObject(1,tx);p.executeUpdate();}auditoria(c,tx,EstadoPago.PENDIENTE,EstadoPago.FALLIDA,origen,"{}");}c.commit();}catch(Exception e){c.rollback();if(e instanceof SQLException se)throw se;throw new SQLException(e);}}
    }
    private static UUID getTx(Connection c,String key,EstadoPago ignored)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT id FROM transacciones_pago WHERE clave_idempotencia=? AND estado='PENDIENTE' FOR UPDATE")){p.setString(1,key);try(ResultSet r=p.executeQuery()){return r.next()?r.getObject(1,UUID.class):null;}}}
    public record Item(long productoId,int cantidad) { }
    private static String requestHash(SolicitudPago s) throws Exception {var d=java.security.MessageDigest.getInstance("SHA-256");byte[] b=d.digest((s.idOrden()+"|"+s.medio()+"|"+s.monto().valorMenor()+"|"+s.monto().moneda()+"|"+s.tokenPago()).getBytes(java.nio.charset.StandardCharsets.UTF_8));return java.util.HexFormat.of().formatHex(b);}

    public boolean aplicarResultado(ResultadoPago resultado) throws SQLException {
        try (Connection c=dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement event=c.prepareStatement("INSERT INTO eventos_procesados(id_evento) VALUES (?) ON CONFLICT DO NOTHING")) {
                    event.setString(1,resultado.idEvento()); if (event.executeUpdate()==0) { c.commit(); return false; }
                }
                UUID tx; UUID orden; EstadoPago actual;
                try (PreparedStatement q=c.prepareStatement("SELECT id,orden_id,estado FROM transacciones_pago WHERE clave_idempotencia=? FOR UPDATE")) {
                    q.setString(1,resultado.claveIdempotencia()); try (ResultSet r=q.executeQuery()) { if (!r.next()) throw new SQLException("Transacción desconocida"); tx=r.getObject(1,UUID.class); orden=r.getObject(2,UUID.class); actual=EstadoPago.valueOf(r.getString(3)); }
                }
                if (actual==EstadoPago.PENDIENTE || (actual==EstadoPago.EXPIRADA && resultado.estado()==EstadoPago.CONFIRMADA)) {
                    EstadoPago nuevo=actual==EstadoPago.EXPIRADA?EstadoPago.REEMBOLSO_PENDIENTE:resultado.estado();
                    try (PreparedStatement p=c.prepareStatement("UPDATE transacciones_pago SET estado=?,id_transaccion_externa=?,actualizada_en=now() WHERE id=?")) { p.setString(1,nuevo.name()); p.setString(2,resultado.idTransaccionExterna()); p.setObject(3,tx); p.executeUpdate(); }
                    if (nuevo==EstadoPago.CONFIRMADA) try (PreparedStatement p=c.prepareStatement("UPDATE ordenes SET estado='PAGADA',version=version+1 WHERE id=?")) { p.setObject(1,orden); p.executeUpdate(); }
                    if (nuevo==EstadoPago.FALLIDA) try (PreparedStatement p=c.prepareStatement("UPDATE ordenes SET estado='CREADA',version=version+1 WHERE id=? AND estado='PAGO_PENDIENTE'")) { p.setObject(1,orden); p.executeUpdate(); }
                    if (nuevo==EstadoPago.REEMBOLSO_PENDIENTE) {auditoria(c,tx,EstadoPago.EXPIRADA,EstadoPago.REEMBOLSO_PENDIENTE,"callback-tardio","{}");try (PreparedStatement p=c.prepareStatement("UPDATE transacciones_pago SET estado='REEMBOLSADA',actualizada_en=now() WHERE id=?")) { p.setObject(1,tx); p.executeUpdate(); } nuevo=EstadoPago.REEMBOLSADA; }
                    auditoria(c,tx,actual,nuevo,"callback","{\"idEvento\":\""+jsonSafe(resultado.idEvento())+"\"}");
                }
                c.commit(); return true;
            } catch (Exception e) { c.rollback(); if (e instanceof SQLException se) throw se; throw new SQLException(e); }
        }
    }

    private static String jsonSafe(String s) { return s.replace("\\","\\\\").replace("\"","\\\""); }
    private static void auditoria(Connection c,UUID tx,EstadoPago anterior,EstadoPago nuevo,String origen,String detalle) throws SQLException {
        try (PreparedStatement p=c.prepareStatement("INSERT INTO bitacora_auditoria(transaccion_id,estado_anterior,estado_nuevo,origen,detalle) VALUES (?,?,?,?,?::jsonb)")) { p.setObject(1,tx); p.setString(2,anterior==null?null:anterior.name()); p.setString(3,nuevo.name()); p.setString(4,origen); p.setString(5,detalle); p.executeUpdate(); }
    }
}
