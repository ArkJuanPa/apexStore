package com.apexstore.nodo2;

import com.apexstore.contratos.*;
import com.apexstore.ice.ConversorIce;
import com.apexstore.ice.pagos.IEstrategiaPagoPrx;

/** Adaptador ICE de una estrategia remota (Nodo 3). Traduce entre el dominio y el Slice. */
public final class ProxyEstrategiaIce implements IEstrategia {
    private final MedioPago medio;
    private final IEstrategiaPagoPrx proxy;

    public ProxyEstrategiaIce(MedioPago medio, IEstrategiaPagoPrx proxy, int timeoutMs) {
        this.medio = medio;
        this.proxy = proxy.ice_invocationTimeout(timeoutMs);
    }

    @Override
    public RespuestaPago iniciarPago(SolicitudPago solicitud) {
        try {
            return ConversorIce.desdeSlice(proxy.iniciarPagoAsync(ConversorIce.aSlice(solicitud)).get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Invocacion Ice interrumpida", e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new IllegalStateException("Fallo de invocacion Ice", e.getCause());
        }
    }

    @Override
    public EstadoPago consultarEstado(String clave) {
        try {
            return ConversorIce.desdeSlice(proxy.consultarEstadoAsync(clave).get());
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo consultar simulador", e);
        }
    }

    @Override
    public boolean soporta(MedioPago m) { return medio == m; }
}
