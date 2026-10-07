package com.apexstore.nodo2;

import com.apexstore.contratos.Entorno;
import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.Util;
import java.util.*;

/** Nodo 2: arranca el backend core por ICE (ServicioCheckout y ReceptorCallbacks). config.nodo2 vive fuera del jar. */
public final class ServidorBackend {
    private ServidorBackend() { }

    public static void main(String[] args) {
        List<String> extra = new ArrayList<>();
        try (Communicator comunicador = Util.initialize(args, "config.nodo2", extra)) {
            long ventanaMs = comunicador.getProperties().getPropertyAsIntWithDefault("Resiliencia.BreakerAbiertoSeg", 10) * 1000L;
            var repo = new RepositorioPagosIce(comunicador);
            var registro = new RegistroEstrategias(comunicador, 500);
            var notificador = new NotificadorObservadores(repo);
            var contexto = new ProcesadorPagosContexto(registro, repo, new PoliticasResiliencia(ventanaMs), notificador::notificar);
            var reconciliador = new ReconciliadorPagos(repo, registro, contexto, Integer.parseInt(Entorno.valor("RECONCILIAR_CADA_SEG", "10")));
            ObjectAdapter checkout = comunicador.createObjectAdapter("Checkout");
            checkout.add(new ServicioCheckout(repo, contexto, notificador), Util.stringToIdentity("servicioCheckout"));
            ObjectAdapter callbacks = comunicador.createObjectAdapter("Callbacks");
            callbacks.add(new ReceptorCallbacks(repo, contexto), Util.stringToIdentity("receptor"));
            checkout.activate();
            callbacks.activate();
            Runtime.getRuntime().addShutdownHook(new Thread(reconciliador::close));
            System.out.println("Nodo 2 (ServicioCheckout) iniciado; ventana del breaker=" + ventanaMs / 1000 + " s");
            comunicador.waitForShutdown();
        }
    }
}
