package com.apexstore.nodo3;

import com.apexstore.contratos.*;
import com.apexstore.ice.pagos.IReceptorResultadosPrx;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Pasarela PSE simulada (medio PSE). */
final class EstrategiaPSE extends EstrategiaSimuladaBase {
    EstrategiaPSE(IReceptorResultadosPrx receptor, int aceptacionMinMs, int aceptacionMaxMs) { super(receptor, aceptacionMinMs, aceptacionMaxMs); }

    @Override protected MedioPago medio() { return MedioPago.PSE; }

    @Override protected RespuestaPago traducir(SolicitudPago solicitud) {
        return new RespuestaPago("SIM-PSE-" + UUID.randomUUID(), EstadoPago.PENDIENTE, Map.of("banco", "Banco Simulado", "referencia", UUID.randomUUID().toString()));
    }

    @Override protected long demoraMs(String token) {
        return token.equals("tok_sim_lento") ? 15000 : 2000 + ThreadLocalRandom.current().nextLong(13001);
    }
}
