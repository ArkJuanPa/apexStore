package com.apexstore.nodo1;

import com.apexstore.ice.pagos.ConflictoIdempotencia;
import com.apexstore.ice.pagos.IGestionComprasPrx;
import com.apexstore.ice.pagos.MedioNoDisponible;
import com.apexstore.ice.pagos.IObservadorPagoPrx;
import com.apexstore.ice.pagos.OrdenNoEncontrada;
import com.apexstore.ice.pagos.RespuestaPago;
import com.apexstore.ice.pagos.SolicitudInvalida;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Opciones de pago del menu: pagar y repetir con la misma clave. Muestra los errores del contrato con su
 * motivo y su alternativa (RAS-01: el usuario puede elegir otro medio).
 */
final class PagosConsola {
    static final String[] MEDIOS = {"STRIPE", "PSE", "CRIPTO"};
    static final String[] ESCENARIOS = {"tok_sim_ok", "tok_sim_rechazado", "tok_sim_fondos", "tok_sim_lento", "tok_sim_caido"};

    private record Pago(String orden, String medio, String token, String clave) { }

    private final IGestionComprasPrx compras;
    private final Consola consola;
    private final ObservadorConsola observador;
    private final IObservadorPagoPrx prxObservador;
    private final Set<String> suscritas = new HashSet<>();
    private Pago ultimoPago;

    PagosConsola(IGestionComprasPrx compras, Consola consola, ObservadorConsola observador, IObservadorPagoPrx prxObservador) {
        this.compras = compras;
        this.consola = consola;
        this.observador = observador;
        this.prxObservador = prxObservador;
    }

    void pagar() {
        String orden = consola.leerOrden();
        int medio = consola.elegir("Medio", MEDIOS);
        int escenario = consola.elegir("Escenario", ESCENARIOS);
        Pago pago = new Pago(orden, MEDIOS[medio], ESCENARIOS[escenario], UUID.randomUUID().toString());
        ultimoPago = pago;
        enviar(pago);
    }

    void repetir() {
        if (ultimoPago == null) {
            System.out.println("Todavia no hay un pago para repetir.");
            return;
        }
        System.out.println("Se repite con la misma clave: " + ultimoPago.clave());
        int medio = consola.elegirOpcional("Medio", MEDIOS, Arrays.asList(MEDIOS).indexOf(ultimoPago.medio()));
        int escenario = consola.elegirOpcional("Escenario", ESCENARIOS, Arrays.asList(ESCENARIOS).indexOf(ultimoPago.token()));
        enviar(new Pago(ultimoPago.orden(), MEDIOS[medio], ESCENARIOS[escenario], ultimoPago.clave()));
    }

    private void enviar(Pago pago) {
        try {
            suscribirUnaVez(pago.orden());
            RespuestaPago respuesta = compras.pagarOrden(pago.orden(), pago.medio(), pago.token(), pago.clave());
            System.out.println("Pago " + pago.medio() + " (" + pago.token() + "): " + respuesta.estado + referencia(respuesta));
            if (respuesta.estado.equals("PENDIENTE")) {
                observador.esperar(pago.orden());
            }
        } catch (MedioNoDisponible e) {
            System.out.println("Medio no disponible: " + e.motivo);
            System.out.println("Sugerencia: " + e.sugerencia);
        } catch (ConflictoIdempotencia e) {
            System.out.println("ConflictoIdempotencia: la clave " + e.clave
                    + " ya se uso con otro cuerpo (medio o escenario distintos).");
        } catch (SolicitudInvalida e) {
            System.out.println("Solicitud no valida: " + e.motivo);
        } catch (OrdenNoEncontrada e) {
            System.out.println("Orden no encontrada: " + e.idOrden);
        }
    }

    private void suscribirUnaVez(String orden) throws OrdenNoEncontrada {
        if (suscritas.add(orden)) {
            compras.suscribir(orden, prxObservador);
        }
    }

    private static String referencia(RespuestaPago respuesta) {
        return respuesta.idTransaccionExterna.isEmpty() ? "" : " ref " + respuesta.idTransaccionExterna;
    }
}
