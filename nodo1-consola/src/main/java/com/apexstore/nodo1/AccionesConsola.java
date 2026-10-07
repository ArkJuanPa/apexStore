package com.apexstore.nodo1;

import com.apexstore.ice.pagos.IGestionComprasPrx;
import com.apexstore.ice.pagos.Item;
import com.apexstore.ice.pagos.Orden;
import com.apexstore.ice.pagos.OrdenNoEncontrada;
import com.apexstore.ice.pagos.Producto;
import com.apexstore.ice.pagos.SolicitudInvalida;
import java.util.Locale;

/**
 * Opciones del menu que no pagan: catalogo, crear y consultar orden. Cohesion: solo creacion y consulta.
 */
final class AccionesConsola {
    private final IGestionComprasPrx compras;
    private final Consola consola;

    AccionesConsola(IGestionComprasPrx compras, Consola consola) {
        this.compras = compras;
        this.consola = consola;
    }

    void catalogo() {
        for (Producto producto : compras.listarProductos()) {
            System.out.printf(Locale.ROOT, "  %d. %s  %d %s  (stock %d)%n",
                    producto.id, producto.nombre, producto.precio.valorMenor, producto.precio.moneda, producto.stock);
        }
    }

    void crearOrden() {
        long producto = consola.leerNumero("Producto (id)", 1);
        int cantidad = (int) consola.leerNumero("Cantidad", 1);
        try {
            Orden orden = compras.crearOrden(new Item[] {new Item(producto, cantidad)});
            consola.recordarOrden(orden.id);
            System.out.println("Orden creada: " + orden.id
                    + " (total " + orden.total.valorMenor + " " + orden.total.moneda + ")");
        } catch (SolicitudInvalida e) {
            System.out.println("Orden no creada: " + e.motivo);
        }
    }

    void consultar() {
        String id = consola.leerOrden();
        try {
            Orden orden = compras.consultarOrden(id);
            System.out.println("Orden " + orden.id + ": estado=" + orden.estado
                    + ", pago=" + orden.estadoPago + ", medio=" + orden.medio);
        } catch (OrdenNoEncontrada e) {
            System.out.println("Orden no encontrada: " + e.idOrden);
        }
    }
}
