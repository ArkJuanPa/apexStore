package com.apexstore.nodo2;

import com.apexstore.contratos.MedioPago;
import com.apexstore.ice.pagos.ConflictoIdempotencia;
import com.apexstore.ice.pagos.SolicitudInvalida;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bulkhead por medio y circuit breaker (RAS-01). Solo cuentan como fallo las caídas del medio
 * (MedioNoDisponible, timeouts y errores de transporte); los errores de negocio no abren el breaker (B6).
 * La ventana del breaker viene de Resiliencia.BreakerAbiertoSeg.
 */
public final class PoliticasResiliencia {
    private static final int FALLOS_PARA_ABRIR = 3;
    private final long ventanaMs;
    private final Map<MedioPago, Semaphore> cupos = new EnumMap<>(MedioPago.class);
    private final Map<MedioPago, AtomicInteger> fallos = new EnumMap<>(MedioPago.class);
    private final Map<MedioPago, Long> abiertoHasta = new EnumMap<>(MedioPago.class);
    private final Map<MedioPago, Boolean> sondeo = new EnumMap<>(MedioPago.class);

    public PoliticasResiliencia(long ventanaMs) {
        this.ventanaMs = ventanaMs;
        for (var medio : MedioPago.values()) {
            cupos.put(medio, new Semaphore(medio == MedioPago.PSE ? 16 : 64));
            fallos.put(medio, new AtomicInteger());
            abiertoHasta.put(medio, 0L);
            sondeo.put(medio, false);
        }
    }

    public <T> T ejecutar(MedioPago medio, Callable<T> llamada) throws Exception {
        Semaphore cupo = cupos.get(medio);
        if (!cupo.tryAcquire()) { throw new RejectedExecutionException("Capacidad ocupada; pruebe otro medio"); }
        try {
            boolean sonda = admitir(medio);
            try {
                T resultado = conReintentos(llamada);
                reiniciar(medio);
                return resultado;
            } catch (Exception e) {
                if (contaComoFallo(e)) { registrarFallo(medio); }
                throw e;
            } finally {
                if (sonda) { cerrarSonda(medio); }
            }
        } finally {
            cupo.release();
        }
    }

    private synchronized boolean admitir(MedioPago medio) {
        long ahora = System.currentTimeMillis();
        if (abiertoHasta.get(medio) > ahora) { throw new RejectedExecutionException("Circuit breaker abierto; pruebe otro medio"); }
        if (fallos.get(medio).get() < FALLOS_PARA_ABRIR) { return false; }
        if (sondeo.get(medio)) { throw new RejectedExecutionException("Circuit breaker semiabierto; espere o pruebe otro medio"); }
        sondeo.put(medio, true);
        return true;
    }

    private synchronized void registrarFallo(MedioPago medio) {
        if (fallos.get(medio).incrementAndGet() >= FALLOS_PARA_ABRIR) {
            abiertoHasta.put(medio, System.currentTimeMillis() + ventanaMs);
        }
    }

    private synchronized void reiniciar(MedioPago medio) {
        fallos.get(medio).set(0);
        abiertoHasta.put(medio, 0L);
    }

    private synchronized void cerrarSonda(MedioPago medio) {
        sondeo.put(medio, false);
    }

    private static <T> T conReintentos(Callable<T> llamada) throws Exception {
        for (int intento = 0; ; intento++) {
            try {
                return llamada.call();
            } catch (Exception e) {
                if (intento == 2 || !falloDeConexion(e)) { throw e; }
                Thread.sleep(40L * (intento + 1) + ThreadLocalRandom.current().nextLong(40));
            }
        }
    }

    private static boolean contaComoFallo(Throwable e) {
        for (Throwable x = e; x != null; x = x.getCause()) {
            if (x instanceof SolicitudInvalida || x instanceof ConflictoIdempotencia) { return false; }
        }
        return true;
    }

    private static boolean falloDeConexion(Throwable e) {
        for (Throwable x = e; x != null; x = x.getCause()) {
            String nombre = x.getClass().getSimpleName();
            if (nombre.equals("ConnectFailedException") || nombre.equals("ConnectTimeoutException") || nombre.equals("ConnectionRefusedException")) { return true; }
        }
        return false;
    }
}
