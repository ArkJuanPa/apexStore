package com.apexstore.nodo3;

import com.apexstore.contratos.*;
import com.apexstore.ice.pagos.IReceptorResultadosPrx;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Pasarela Stripe simulada (medio STRIPE). */
final class EstrategiaStripe extends EstrategiaSimuladaBase {
    EstrategiaStripe(IReceptorResultadosPrx receptor, int aceptacionMinMs, int aceptacionMaxMs) { super(receptor, aceptacionMinMs, aceptacionMaxMs); }

    @Override protected MedioPago medio() { return MedioPago.STRIPE; }

    @Override protected RespuestaPago traducir(SolicitudPago solicitud) {
        return new RespuestaPago("SIM-STRIPE-" + UUID.randomUUID(), EstadoPago.PENDIENTE, Map.of("entorno", "simulado", "moneda", "USD"));
    }

    @Override protected long demoraMs(String token) {
        return token.equals("tok_sim_lento") ? 15000 : 1000 + ThreadLocalRandom.current().nextLong(2001);
    }
}
