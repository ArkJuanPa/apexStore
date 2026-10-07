package com.apexstore.nodo3;

import com.apexstore.contratos.*;
import com.apexstore.ice.pagos.IReceptorResultadosPrx;
import java.util.*;

/** Pasarela cripto simulada (medio CRIPTO). Notifica por callback; no escribe en la BD (RAS-03). */
final class EstrategiaCripto extends EstrategiaSimuladaBase {
    EstrategiaCripto(IReceptorResultadosPrx receptor, int aceptacionMinMs, int aceptacionMaxMs) { super(receptor, aceptacionMinMs, aceptacionMaxMs); }

    @Override protected MedioPago medio() { return MedioPago.CRIPTO; }

    @Override protected RespuestaPago traducir(SolicitudPago solicitud) {
        String direccion = "SIM-BTC-" + UUID.randomUUID();
        return new RespuestaPago(direccion, EstadoPago.PENDIENTE, Map.of("direccion", direccion, "unidad", "satoshis"));
    }

    @Override protected long demoraMs(String token) {
        return token.equals("tok_sim_lento") ? 15000 : 3000L;
    }
}
