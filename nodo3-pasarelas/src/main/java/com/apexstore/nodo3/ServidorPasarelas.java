package com.apexstore.nodo3;

import com.apexstore.ice.pagos.IReceptorResultadosPrx;
import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.Util;
import java.util.*;

/** Nodo 3: publica las tres pasarelas simuladas por ICE (estilo helloworld; config.nodo3 fuera del jar). */
public final class ServidorPasarelas {
    private ServidorPasarelas() { }

    public static void main(String[] args) {
        List<String> extra = new ArrayList<>();
        try (Communicator comunicador = Util.initialize(args, "config.nodo3", extra)) {
            IReceptorResultadosPrx receptor = IReceptorResultadosPrx.uncheckedCast(comunicador.propertyToProxy("Receptor.Proxy"));
            int aceptacionMin = comunicador.getProperties().getPropertyAsIntWithDefault("Simulacion.AceptacionMinMs", 30);
            int aceptacionMax = comunicador.getProperties().getPropertyAsIntWithDefault("Simulacion.AceptacionMaxMs", 80);
            ObjectAdapter adaptador = comunicador.createObjectAdapter("Pasarelas");
            adaptador.add(new EstrategiaStripe(receptor, aceptacionMin, aceptacionMax), Util.stringToIdentity("estrategia/stripe"));
            adaptador.add(new EstrategiaPSE(receptor, aceptacionMin, aceptacionMax), Util.stringToIdentity("estrategia/pse"));
            adaptador.add(new EstrategiaCripto(receptor, aceptacionMin, aceptacionMax), Util.stringToIdentity("estrategia/cripto"));
            adaptador.activate();
            System.out.println("Nodo 3 (Pasarelas) iniciado; callback=" + comunicador.getProperties().getProperty("Receptor.Proxy"));
            comunicador.waitForShutdown();
        }
    }
}
