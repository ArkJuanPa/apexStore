package com.apexstore.nodo4;

import com.apexstore.contratos.*;
import com.apexstore.ice.ConversorIce;
import com.apexstore.ice.pagos.ConflictoIdempotencia;
import com.apexstore.ice.pagos.OrdenNoEncontrada;
import com.apexstore.ice.pagos.IRepositorioPagos;
import com.zeroc.Ice.Current;
import java.sql.SQLException;
import java.util.*;

/** Servant ICE de IRepositorioPagos (Nodo 4). Cada operación es una transacción local (RAS-03). */
public final class RepositorioPagos implements IRepositorioPagos {
    private final RepositorioOrdenes ordenes;
    private final RepositorioTransacciones transacciones;

    public RepositorioPagos(RepositorioOrdenes ordenes, RepositorioTransacciones transacciones) {
        this.ordenes = ordenes;
        this.transacciones = transacciones;
    }

    @Override
    public com.apexstore.ice.pagos.Producto[] productos(Current current) {
        try {
            var out = new ArrayList<com.apexstore.ice.pagos.Producto>();
            for (Map<String, Object> p : ordenes.productos()) {
                out.add(new com.apexstore.ice.pagos.Producto(((Number) p.get("productoId")).longValue(), (String) p.get("nombre"),
                        new com.apexstore.ice.pagos.Dinero(((Number) p.get("precioMenor")).longValue(), (String) p.get("moneda")),
                        ((Number) p.get("stock")).intValue()));
            }
            return out.toArray(new com.apexstore.ice.pagos.Producto[0]);
        } catch (SQLException e) { throw new IllegalStateException(e.getMessage(), e); }
    }

    @Override
    public com.apexstore.ice.pagos.Orden crearOrden(com.apexstore.ice.pagos.Item[] items, Current current) {
        try {
            var lista = new ArrayList<RepositorioOrdenes.Item>();
            for (var it : items) { lista.add(new RepositorioOrdenes.Item(it.productoId, it.cantidad)); }
            return orden(ordenes.crearOrden(lista));
        } catch (SQLException e) { throw new IllegalStateException(e.getMessage(), e); }
    }

    @Override
    public com.apexstore.ice.pagos.Orden obtenerOrden(String idOrden, Current current) throws OrdenNoEncontrada {
        try {
            Map<String, Object> m = ordenes.obtenerOrden(idOrden);
            if (m == null) { throw new OrdenNoEncontrada(idOrden); }
            return orden(m);
        } catch (SQLException e) { throw new IllegalStateException(e.getMessage(), e); }
    }

    @Override
    public IRepositorioPagos.RegistrarPendienteResult registrarPendiente(com.apexstore.ice.pagos.SolicitudPago s, Current current) throws ConflictoIdempotencia {
        try {
            var solicitud = new SolicitudPago(s.idOrden, s.claveIdempotencia, new Dinero(s.monto.valorMenor, s.monto.moneda), MedioPago.valueOf(s.medio), s.tokenPago);
            var registro = transacciones.registrarPendiente(solicitud);
            var respuesta = new com.apexstore.ice.pagos.RespuestaPago(texto(registro.transaccionExterna()), registro.estado().name(), registro.instrucciones());
            return new IRepositorioPagos.RegistrarPendienteResult(respuesta, registro.nueva());
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) { throw new ConflictoIdempotencia(s.claveIdempotencia); }
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    public void guardarRespuesta(String clave, com.apexstore.ice.pagos.RespuestaPago r, Current current) {
        try { transacciones.guardarRespuesta(clave, ConversorIce.desdeSlice(r)); }
        catch (SQLException e) { throw new IllegalStateException(e.getMessage(), e); }
    }

    @Override
    public boolean aplicarResultado(com.apexstore.ice.pagos.ResultadoPago r, Current current) {
        try { return transacciones.aplicarResultado(ConversorIce.desdeSlice(r)); }
        catch (SQLException e) { throw new IllegalStateException(e.getMessage(), e); }
    }

    @Override
    public void marcarFallida(String clave, String origen, Current current) {
        try { transacciones.marcarFallida(clave, origen); }
        catch (SQLException e) { throw new IllegalStateException(e.getMessage(), e); }
    }

    @Override
    public com.apexstore.ice.pagos.PendienteTx[] pendientes(Current current) {
        try {
            var out = new ArrayList<com.apexstore.ice.pagos.PendienteTx>();
            for (var p : transacciones.pendientes()) { out.add(new com.apexstore.ice.pagos.PendienteTx(p.claveIdempotencia(), p.medio().name())); }
            return out.toArray(new com.apexstore.ice.pagos.PendienteTx[0]);
        } catch (SQLException e) { throw new IllegalStateException(e.getMessage(), e); }
    }

    @Override
    public int expirarVencidas(Current current) {
        try { return ordenes.expirarVencidas(); }
        catch (SQLException e) { throw new IllegalStateException(e.getMessage(), e); }
    }

    @Override
    public String ordenDeClave(String clave, Current current) {
        try { return texto(transacciones.ordenDeClave(clave)); }
        catch (SQLException e) { throw new IllegalStateException(e.getMessage(), e); }
    }

    private static com.apexstore.ice.pagos.Orden orden(Map<String, Object> m) {
        return new com.apexstore.ice.pagos.Orden(texto(m.get("id")), texto(m.get("estado")),
                new com.apexstore.ice.pagos.Dinero(((Number) m.get("totalMenor")).longValue(), texto(m.get("moneda"))),
                texto(m.get("pagoEstado")), texto(m.get("medio")), texto(m.get("referencia")));
    }

    /** ICE no admite null en strings: se envía vacío y Nodo 2 lo vuelve a null. */
    private static String texto(Object o) { return o == null ? "" : o.toString(); }
}
