package com.apexstore.nodo1;

import com.apexstore.ice.pagos.IObservadorPago;
import com.apexstore.ice.pagos.Orden;
import com.zeroc.Ice.Current;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Implementa IObservadorPago (push desde Nodo 2). Imprime cada notificación al llegar, aunque el usuario esté en el menú,
 * y completa la espera de una orden cuando su pago llega a un estado final (CONFIRMADA o FALLIDA).
 */
final class ObservadorConsola implements IObservadorPago {
    private final Map<String, CompletableFuture<Orden>> pendientes = new ConcurrentHashMap<>();

    CompletableFuture<Orden> esperar(String idOrden) {
        return pendientes.computeIfAbsent(idOrden, k -> new CompletableFuture<>());
    }

    boolean hayPendientes() {
        return !pendientesActuales().isEmpty();
    }

    List<CompletableFuture<Orden>> pendientesActuales() {
        return pendientes.values().stream().filter(f -> !f.isDone()).toList();
    }

    @Override
    public void pagoActualizado(Orden orden, Current current) {
        System.out.println();
        System.out.println("[PUSH] orden " + orden.id + ": estado=" + orden.estado + ", pago=" + orden.estadoPago + " (" + orden.medio + ")");
        if (orden.estadoPago.equals("CONFIRMADA") || orden.estadoPago.equals("FALLIDA")) {
            CompletableFuture<Orden> espera = pendientes.get(orden.id);
            if (espera != null) { espera.complete(orden); }
        }
    }
}
