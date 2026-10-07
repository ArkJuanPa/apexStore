package com.apexstore.nodo2;

import com.apexstore.contratos.Dinero;
import com.apexstore.contratos.MedioPago;
import com.apexstore.contratos.RespuestaPago;
import com.apexstore.contratos.SolicitudPago;
import com.apexstore.ice.pagos.ConflictoIdempotencia;
import com.apexstore.ice.pagos.IGestionCompras;
import com.apexstore.ice.pagos.Item;
import com.apexstore.ice.pagos.IObservadorPagoPrx;
import com.apexstore.ice.pagos.Orden;
import com.apexstore.ice.pagos.OrdenNoEncontrada;
import com.apexstore.ice.pagos.Producto;
import com.apexstore.ice.pagos.SolicitudInvalida;
import com.zeroc.Ice.Current;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Componente ServicioCheckout del diagrama: provee IGestionCompras por ICE (endpoints tcp). El pago corre fuera
 * del hilo de ICE y el resultado final llega por IObservadorPago. Atiende RAS-01 (no bloquea la compra).
 */
public final class ServicioCheckout implements IGestionCompras {
    private static final Set<String> TOKENS_SIMULADOS =
            Set.of("tok_sim_ok", "tok_sim_rechazado", "tok_sim_fondos", "tok_sim_lento", "tok_sim_caido");
    private static final ExecutorService EJECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final RepositorioPagosIce repo;
    private final ProcesadorPagosContexto contexto;
    private final NotificadorObservadores notificador;

    public ServicioCheckout(RepositorioPagosIce repo, ProcesadorPagosContexto contexto, NotificadorObservadores notificador) {
        this.repo = repo;
        this.contexto = contexto;
        this.notificador = notificador;
    }

    @Override
    public Producto[] listarProductos(Current current) {
        try {
            var productos = new ArrayList<Producto>();
            for (Map<String, Object> p : repo.productos()) {
                var precio = new com.apexstore.ice.pagos.Dinero(((Number) p.get("precioMenor")).longValue(), (String) p.get("moneda"));
                productos.add(new Producto(((Number) p.get("productoId")).longValue(), (String) p.get("nombre"),
                        precio, ((Number) p.get("stock")).intValue()));
            }
            return productos.toArray(new Producto[0]);
        } catch (SQLException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    public Orden crearOrden(Item[] items, Current current) throws SolicitudInvalida {
        try {
            return aOrden(repo.crearOrden(Arrays.asList(items)));
        } catch (SQLException e) {
            throw new SolicitudInvalida(e.getMessage());
        }
    }

    @Override
    public CompletionStage<com.apexstore.ice.pagos.RespuestaPago> pagarOrdenAsync(
            String idOrden, String medio, String tokenPago, String claveIdempotencia, Current current) {
        var futuro = new CompletableFuture<com.apexstore.ice.pagos.RespuestaPago>();
        EJECUTOR.execute(() -> {
            try {
                futuro.complete(pagar(idOrden, medio, tokenPago, claveIdempotencia));
            } catch (Exception e) {
                futuro.completeExceptionally(e);
            }
        });
        return futuro;
    }

    @Override
    public Orden consultarOrden(String idOrden, Current current) throws OrdenNoEncontrada {
        try {
            Map<String, Object> m = repo.obtenerOrden(idOrden);
            if (m == null) {
                throw new OrdenNoEncontrada(idOrden);
            }
            return aOrden(m);
        } catch (SQLException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    public void suscribir(String idOrden, IObservadorPagoPrx observador, Current current) throws OrdenNoEncontrada {
        consultarOrden(idOrden, current);
        // El observador llego por esta conexion: se fija a ella para las notificaciones (bidireccional).
        notificador.suscribir(idOrden, IObservadorPagoPrx.uncheckedCast(observador.ice_fixed(current.con)));
    }

    private com.apexstore.ice.pagos.RespuestaPago pagar(String idOrden, String medio, String tokenPago, String clave)
            throws Exception {
        if (!TOKENS_SIMULADOS.contains(tokenPago)) {
            throw new SolicitudInvalida("Use uno de los escenarios de simulacion disponibles");
        }
        Map<String, Object> orden = repo.obtenerOrden(idOrden);
        if (orden == null) {
            throw new OrdenNoEncontrada(idOrden);
        }
        MedioPago medioPago = medioValido(medio);
        var monto = new Dinero(((Number) orden.get("totalMenor")).longValue(), String.valueOf(orden.get("moneda")));
        try {
            RespuestaPago r = contexto.iniciarPagoOrden(new SolicitudPago(idOrden, clave, monto, medioPago, tokenPago));
            return new com.apexstore.ice.pagos.RespuestaPago(texto(r.idTransaccionExterna()), r.estado().name(), r.instrucciones());
        } catch (Exception e) {
            if (TraductorErrores.esConflictoIdempotencia(e)) {
                throw new ConflictoIdempotencia(clave);
            }
            if (TraductorErrores.esTimeout(e)) {
                return new com.apexstore.ice.pagos.RespuestaPago("", "PENDIENTE", Map.of());
            }
            throw TraductorErrores.aExcepcionSlice(e);
        }
    }

    private static MedioPago medioValido(String medio) throws SolicitudInvalida {
        try {
            return MedioPago.valueOf(medio);
        } catch (IllegalArgumentException e) {
            throw new SolicitudInvalida("Medio no soportado");
        }
    }

    /** Mapeo del dominio al Slice. Compartido con el notificador de observadores. */
    static Orden aOrden(Map<String, Object> m) {
        var total = new com.apexstore.ice.pagos.Dinero(((Number) m.get("totalMenor")).longValue(), texto(m.get("moneda")));
        return new Orden(texto(m.get("id")), texto(m.get("estado")), total,
                texto(m.get("pagoEstado")), texto(m.get("medio")), texto(m.get("referencia")));
    }

    private static String texto(Object o) {
        return o == null ? "" : o.toString();
    }
}
