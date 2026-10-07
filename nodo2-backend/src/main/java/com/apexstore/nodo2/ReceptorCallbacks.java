package com.apexstore.nodo2;

import com.apexstore.contratos.ResultadoPago;
import com.apexstore.ice.ConversorIce;
import com.apexstore.ice.pagos.IReceptorResultados;
import com.apexstore.ice.pagos.SolicitudInvalida;
import com.zeroc.Ice.Current;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Recibe el resultado asincrono de Nodo 3 (IReceptorResultados). Valida que la clave exista; la deduplicacion
 * por idEvento la hace el repositorio. Atiende RAS-01 y RAS-03.
 */
public final class ReceptorCallbacks implements IReceptorResultados {
    private final RepositorioPagosIce repo;
    private final ProcesadorPagosContexto contexto;

    public ReceptorCallbacks(RepositorioPagosIce repo, ProcesadorPagosContexto contexto) {
        this.repo = repo;
        this.contexto = contexto;
    }

    @Override
    public CompletionStage<Void> notificarResultadoPagoAsync(com.apexstore.ice.pagos.ResultadoPago wire, Current current) {
        try {
            ResultadoPago resultado = ConversorIce.desdeSlice(wire);
            if (repo.ordenDeClave(resultado.claveIdempotencia()) == null) {
                throw new SolicitudInvalida("Clave de idempotencia desconocida");
            }
            contexto.procesarResultadoPago(resultado);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
