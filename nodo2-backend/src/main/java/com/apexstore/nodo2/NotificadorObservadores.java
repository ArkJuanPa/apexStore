package com.apexstore.nodo2;

import com.apexstore.ice.pagos.IObservadorPagoPrx;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;

/** Avisa por IObservadorPago (conexión bidireccional del cliente) cuando cambia el estado de una orden. */
public final class NotificadorObservadores {
    private final RepositorioPagosIce repo;
    private final Map<String, List<IObservadorPagoPrx>> observadores = new ConcurrentHashMap<>();

    public NotificadorObservadores(RepositorioPagosIce repo) {
        this.repo = repo;
    }

    public void suscribir(String idOrden, IObservadorPagoPrx observador) {
        observadores.computeIfAbsent(idOrden, k -> new CopyOnWriteArrayList<>()).add(observador);
    }

    public void notificar(String idOrden) {
        List<IObservadorPagoPrx> lista = observadores.get(idOrden);
        if (lista == null || lista.isEmpty()) { return; }
        try {
            var orden = ServicioCheckout.aOrden(repo.obtenerOrden(idOrden));
            for (IObservadorPagoPrx observador : lista) {
                observador.pagoActualizadoAsync(orden).exceptionally(e -> {
                    System.err.println("No se pudo notificar la orden " + idOrden + " al observador: " + e);
                    lista.remove(observador);
                    return null;
                });
            }
        } catch (SQLException e) {
            System.err.println("No se pudo notificar la orden " + idOrden + ": " + e.getMessage());
        }
    }
}
