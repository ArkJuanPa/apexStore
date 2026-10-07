package com.apexstore.nodo1;

import com.apexstore.ice.pagos.IGestionComprasPrx;
import com.apexstore.ice.pagos.IObservadorPagoPrx;
import com.apexstore.ice.pagos.Orden;
import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.LocalException;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.Util;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * MobileApp del diagrama (Nodo 1): menu de consola sobre IGestionCompras. Una WebApp consumiria la misma
 * interfaz; la separacion interfaz/implementacion permite agregarla sin tocar el backend.
 */
public final class ClienteConsola {
    private static final long ESPERA_SALIDA_SEG = 20;

    private final Consola consola = new Consola();
    private final ObservadorConsola observador;
    private final AccionesConsola acciones;
    private final PagosConsola pagos;

    private ClienteConsola(IGestionComprasPrx compras, ObservadorConsola observador, IObservadorPagoPrx prxObservador) {
        this.observador = observador;
        this.acciones = new AccionesConsola(compras, consola);
        this.pagos = new PagosConsola(compras, consola, observador, prxObservador);
    }

    public static void main(String[] args) throws java.lang.Exception {
        List<String> extra = new ArrayList<>();
        try (Communicator comunicador = Util.initialize(args, "config.nodo1", extra)) {
            IGestionComprasPrx compras = IGestionComprasPrx.checkedCast(comunicador.propertyToProxy("Checkout.Proxy"));
            ObjectAdapter callback = comunicador.createObjectAdapter("");
            callback.activate();
            var observador = new ObservadorConsola();
            // Sin endpoints: Nodo 2 fija el proxy a la conexion de suscribir (conexion bidireccional).
            IObservadorPagoPrx prxObservador = IObservadorPagoPrx.uncheckedCast(callback.addWithUUID(observador));
            compras.listarProductos();
            compras.ice_getCachedConnection().setAdapter(callback);
            new ClienteConsola(compras, observador, prxObservador).menu();
        }
    }

    private void menu() {
        System.out.println("ApexStore - cliente de consola (Nodo 1)");
        while (true) {
            imprimirMenu();
            if (!consola.hayLinea()) {
                break;
            }
            String opcion = consola.linea();
            if (opcion.equals("0")) {
                break;
            }
            ejecutar(opcion);
        }
        salir();
    }

    private static void imprimirMenu() {
        System.out.println();
        System.out.println("1) Ver catalogo  2) Crear orden  3) Pagar  4) Repetir ultimo pago  5) Consultar orden  0) Salir");
        System.out.print("> ");
    }

    private void ejecutar(String opcion) {
        try {
            switch (opcion) {
                case "1" -> acciones.catalogo();
                case "2" -> acciones.crearOrden();
                case "3" -> pagos.pagar();
                case "4" -> pagos.repetir();
                case "5" -> acciones.consultar();
                default -> System.out.println("Opcion no valida.");
            }
        } catch (LocalException e) {
            System.out.println("No hay conexion con Nodo 2: " + e.getMessage());
        }
    }

    private void salir() {
        if (observador.hayPendientes()) {
            System.out.println("Esperando resultados pendientes (hasta " + ESPERA_SALIDA_SEG + " s)...");
            for (CompletableFuture<Orden> espera : observador.pendientesActuales()) {
                esperar(espera);
            }
        }
        System.out.println("Hasta luego.");
    }

    private static void esperar(CompletableFuture<Orden> espera) {
        try {
            espera.get(ESPERA_SALIDA_SEG, TimeUnit.SECONDS);
        } catch (Exception e) {
            System.out.println("Sin resultado final para una orden.");
        }
    }
}
