package com.apexstore.nodo2;

import com.apexstore.contratos.*;
import com.apexstore.ice.pagos.IEstrategiaPagoPrx;
import com.zeroc.Ice.Communicator;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Registro de estrategias: cada medio se resuelve a su proxy de config.nodo2 (Estrategia.<MEDIO>.Proxy). */
public final class RegistroEstrategias {
    private final Map<MedioPago, IEstrategia> estrategias = new ConcurrentHashMap<>();

    public RegistroEstrategias() { }

    public RegistroEstrategias(Communicator comunicador, int timeoutMs) {
        for (MedioPago medio : MedioPago.values()) {
            if (medio == MedioPago.BILLETERA_DIGITAL) { continue; }
            String propiedad = "Estrategia." + medio.name() + ".Proxy";
            IEstrategiaPagoPrx proxy = IEstrategiaPagoPrx.uncheckedCast(comunicador.propertyToProxy(propiedad));
            if (proxy == null) { throw new IllegalStateException("Falta " + propiedad + " en config.nodo2"); }
            estrategias.put(medio, new ProxyEstrategiaIce(medio, proxy, timeoutMs));
        }
    }

    public IEstrategia resolver(MedioPago medio) {
        var estrategia = estrategias.get(medio);
        if (estrategia == null) { throw new IllegalArgumentException("Medio no disponible"); }
        return estrategia;
    }

    public void registrar(IEstrategia estrategia) {
        for (MedioPago medio : MedioPago.values()) {
            if (estrategia.soporta(medio)) { estrategias.put(medio, estrategia); }
        }
    }
}
