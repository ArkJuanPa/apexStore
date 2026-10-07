package com.apexstore.nodo2;

import com.apexstore.contratos.EstadoPago;
import com.apexstore.contratos.ResultadoPago;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Recupera pagos PENDIENTE consultando a la estrategia y expira los vencidos. Atiende RAS-03: no quedan pagos
 * huerfanos aunque se pierda un callback.
 */
public final class ReconciliadorPagos implements AutoCloseable {
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(ReconciliadorPagos::hiloDemonio);

    public ReconciliadorPagos(RepositorioPagosIce repo, RegistroEstrategias registro, ProcesadorPagosContexto contexto, int periodoSeg) {
        executor.scheduleWithFixedDelay(() -> reconciliar(repo, registro, contexto), periodoSeg, periodoSeg, TimeUnit.SECONDS);
    }

    private static Thread hiloDemonio(Runnable tarea) {
        Thread hilo = new Thread(tarea, "reconciliador-pagos");
        hilo.setDaemon(true);
        return hilo;
    }

    private static void reconciliar(RepositorioPagosIce repo, RegistroEstrategias registro, ProcesadorPagosContexto contexto) {
        try {
            for (var tx : repo.pendientes()) {
                consultarYAplicar(tx, registro, contexto);
            }
            repo.expirarVencidas();
        } catch (Exception ignorado) {
            // El siguiente ciclo vuelve a intentarlo.
        }
    }

    private static void consultarYAplicar(RepositorioPagosIce.Pending tx, RegistroEstrategias registro, ProcesadorPagosContexto contexto) {
        try {
            EstadoPago estado = registro.resolver(tx.medio()).consultarEstado(tx.claveIdempotencia());
            if (estado != EstadoPago.PENDIENTE) {
                contexto.procesarResultadoPago(new ResultadoPago("reconciliacion-" + UUID.randomUUID(),
                        "SIM-REC-" + UUID.randomUUID(), tx.claveIdempotencia(), estado, System.currentTimeMillis()));
            }
        } catch (Exception ignorado) {
            // Una clave que falla no detiene las demas.
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
