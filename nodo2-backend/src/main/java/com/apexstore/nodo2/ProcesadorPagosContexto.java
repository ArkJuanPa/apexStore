package com.apexstore.nodo2;

import com.apexstore.contratos.*;
import java.sql.SQLException;
import java.util.Locale;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

/**
 * Contexto del patrón Strategy en Nodo 2 (ProcesadorPagosContexto). Mide tres tramos por pago
 * para RAS-02 y los escribe en el log como "RAS02,orden,medio,repoMs,estrategiaMs,totalMs".
 */
public final class ProcesadorPagosContexto {
    private final RegistroEstrategias estrategias;
    private final RepositorioPagosIce repo;
    private final PoliticasResiliencia resiliencia;
    private final Consumer<String> alConfirmarResultado;

    public ProcesadorPagosContexto(RegistroEstrategias estrategias, RepositorioPagosIce repo,
                                   PoliticasResiliencia resiliencia, Consumer<String> alConfirmarResultado) {
        this.estrategias = estrategias;
        this.repo = repo;
        this.resiliencia = resiliencia;
        this.alConfirmarResultado = alConfirmarResultado;
    }

    public RespuestaPago iniciarPagoOrden(SolicitudPago solicitud) throws Exception {
        long inicio = System.nanoTime();
        var registro = repo.registrarPendiente(solicitud);
        long tRegistro = System.nanoTime() - inicio;
        if (!registro.nueva()) {
            return new RespuestaPago(registro.transaccionExterna(), registro.estado(), registro.instrucciones());
        }
        long inicioEstrategia = System.nanoTime();
        RespuestaPago respuesta;
        try {
            respuesta = resiliencia.ejecutar(solicitud.medio(), () -> estrategias.resolver(solicitud.medio()).iniciarPago(solicitud));
        } catch (RejectedExecutionException e) {
            repo.marcarFallida(solicitud.claveIdempotencia(), "resiliencia", "capacidad o breaker");
            throw e;
        } catch (Exception e) {
            if (!causadoPorTimeout(e)) { repo.marcarFallida(solicitud.claveIdempotencia(), "ice", "no enviado"); }
            throw e;
        }
        long tEstrategia = System.nanoTime() - inicioEstrategia;
        long inicioGuardar = System.nanoTime();
        repo.guardarRespuesta(solicitud.claveIdempotencia(), respuesta);
        long tGuardar = System.nanoTime() - inicioGuardar;
        long tTotal = System.nanoTime() - inicio;
        System.out.println(String.format(Locale.ROOT, "RAS02,%s,%s,%.3f,%.3f,%.3f", solicitud.idOrden(), solicitud.medio(),
                (tRegistro + tGuardar) / 1e6, tEstrategia / 1e6, tTotal / 1e6));
        return respuesta;
    }

    public void procesarResultadoPago(ResultadoPago r) throws SQLException {
        String orden = repo.ordenDeClave(r.claveIdempotencia());
        if (orden != null && repo.aplicarResultado(r)) {
            alConfirmarResultado.accept(orden);
        }
    }

    private static boolean causadoPorTimeout(Throwable e) {
        for (Throwable x = e; x != null; x = x.getCause()) {
            if (x instanceof java.util.concurrent.TimeoutException || x.getClass().getSimpleName().contains("InvocationTimeout")) { return true; }
        }
        return false;
    }
}
