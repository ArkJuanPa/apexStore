package com.apexstore.nodo4;

import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.Util;
import java.util.ArrayList;
import java.util.List;

/**
 * Nodo 4: arranca PostgreSQL (esquema y semilla) y expone IRepositorioPagos por ICE, al estilo helloworld.
 * Atiende RAS-03: es el unico nodo que accede a la base.
 */
public final class Nodo4 {
    private Nodo4() { }

    public static void main(String[] args) throws Exception {
        var config = ConfiguracionBaseDatos.cargar();
        if (args.length > 0 && args[0].equals("--verificar-db")) {
            InicializadorBaseDatos.verificar(config);
            return;
        }
        var ds = InicializadorBaseDatos.inicializar(config);
        List<String> extra = new ArrayList<>();
        try (Communicator comunicador = Util.initialize(args, "config.nodo4", extra)) {
            ObjectAdapter adaptador = comunicador.createObjectAdapter("Repositorio");
            adaptador.add(new RepositorioPagos(new RepositorioOrdenes(ds), new RepositorioTransacciones(ds)), Util.stringToIdentity("repositorio"));
            adaptador.activate();
            System.out.println("Nodo 4 iniciado; PostgreSQL=" + config.host() + ":" + config.puerto()
                    + "/" + config.base() + ", esquema=" + config.esquema());
            comunicador.waitForShutdown();
        } finally {
            ds.close();
        }
    }
}
