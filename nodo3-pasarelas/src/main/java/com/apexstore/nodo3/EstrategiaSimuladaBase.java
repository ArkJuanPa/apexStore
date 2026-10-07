package com.apexstore.nodo3;

import com.apexstore.contratos.*;
import com.apexstore.ice.ConversorIce;
import com.apexstore.ice.pagos.IEstrategiaPago;
import com.apexstore.ice.pagos.MedioNoDisponible;
import com.apexstore.ice.pagos.IReceptorResultadosPrx;
import com.apexstore.ice.pagos.SolicitudInvalida;
import com.zeroc.Ice.Current;
import java.util.*;
import java.util.concurrent.*;

/**
 * Base de las pasarelas simuladas. Responde en la aceptación configurada (RAS-02) y entrega el resultado final
 * por callback (RAS-01). Los escenarios salen del token: tok_sim_caido, tok_sim_lento,
 * tok_sim_rechazado y tok_sim_fondos; cualquier otro token confirma.
 */
abstract class EstrategiaSimuladaBase implements IEstrategiaPago {
    private final IReceptorResultadosPrx receptor;
    private final int aceptacionMinMs;
    private final int aceptacionMaxMs;
    private final Map<String, RespuestaPago> conocidas = new ConcurrentHashMap<>();

    EstrategiaSimuladaBase(IReceptorResultadosPrx receptor, int aceptacionMinMs, int aceptacionMaxMs) {
        this.receptor = receptor;
        this.aceptacionMinMs = aceptacionMinMs;
        this.aceptacionMaxMs = aceptacionMaxMs;
    }

    protected abstract MedioPago medio();
    protected abstract RespuestaPago traducir(SolicitudPago solicitud);
    protected abstract long demoraMs(String token);

    @Override
    public CompletionStage<com.apexstore.ice.pagos.RespuestaPago> iniciarPagoAsync(com.apexstore.ice.pagos.SolicitudPago s, Current current) {
        RespuestaPago previa = conocidas.get(s.claveIdempotencia);
        if (previa != null) { return CompletableFuture.completedFuture(ice(previa)); }
        if (s.tokenPago.equals("tok_sim_caido")) {
            return CompletableFuture.failedFuture(new MedioNoDisponible("Simulador no disponible", "Seleccione otro medio de pago"));
        }
        if (!s.medio.equals(medio().name())) { return CompletableFuture.failedFuture(new SolicitudInvalida("Medio no soportado")); }
        RespuestaPago r = traducir(new SolicitudPago(s.idOrden, s.claveIdempotencia, new Dinero(s.monto.valorMenor, s.monto.moneda), medio(), s.tokenPago));
        conocidas.put(s.claveIdempotencia, r);
        programarResultado(s, r, demoraMs(s.tokenPago));
        long respuestaMs = aceptacionMinMs + ThreadLocalRandom.current().nextInt(aceptacionMaxMs - aceptacionMinMs + 1);
        return CompletableFuture.supplyAsync(() -> ice(r), CompletableFuture.delayedExecutor(respuestaMs, TimeUnit.MILLISECONDS));
    }

    // B3: una clave desconocida es PENDIENTE, no FALLIDA (el reconciliador no debe marcar fallidos pagos que pudieron confirmarse).
    @Override
    public CompletionStage<String> consultarEstadoAsync(String clave, Current current) {
        RespuestaPago r = conocidas.get(clave);
        return CompletableFuture.completedFuture(r == null ? "PENDIENTE" : ConversorIce.aSlice(r.estado()));
    }

    private void programarResultado(com.apexstore.ice.pagos.SolicitudPago s, RespuestaPago r, long demora) {
        CompletableFuture.delayedExecutor(demora, TimeUnit.MILLISECONDS).execute(() -> {
            long cuando = System.currentTimeMillis();
            boolean rechazado = s.tokenPago.equals("tok_sim_rechazado") || s.tokenPago.equals("tok_sim_fondos");
            EstadoPago estado = rechazado ? EstadoPago.FALLIDA : EstadoPago.CONFIRMADA;
            conocidas.put(s.claveIdempotencia, new RespuestaPago(r.idTransaccionExterna(), estado, r.instrucciones()));
            var resultado = new ResultadoPago(UUID.randomUUID().toString(), r.idTransaccionExterna(), s.claveIdempotencia, estado, cuando);
            enviar(ConversorIce.aSlice(resultado), Integer.parseInt(Entorno.valor("CALLBACK_REINTENTOS", "3")));
        });
    }

    private void enviar(com.apexstore.ice.pagos.ResultadoPago r, int intentosRestantes) {
        receptor.notificarResultadoPagoAsync(r).whenComplete((v, e) -> {
            if (e != null && intentosRestantes > 1) {
                CompletableFuture.delayedExecutor((4 - intentosRestantes) * 250L, TimeUnit.MILLISECONDS)
                        .execute(() -> enviar(r, intentosRestantes - 1));
            }
        });
    }

    private static com.apexstore.ice.pagos.RespuestaPago ice(RespuestaPago r) {
        return new com.apexstore.ice.pagos.RespuestaPago(r.idTransaccionExterna(), ConversorIce.aSlice(r.estado()), r.instrucciones());
    }
}
