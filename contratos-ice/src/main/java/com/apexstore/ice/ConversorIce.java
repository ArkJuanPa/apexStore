package com.apexstore.ice;

import com.apexstore.contratos.*;
import java.util.Map;

public final class ConversorIce {
    private ConversorIce() { }
    public static com.apexstore.ice.pagos.SolicitudPago aSlice(SolicitudPago s) {
        return new com.apexstore.ice.pagos.SolicitudPago(s.idOrden(), s.claveIdempotencia(), new com.apexstore.ice.pagos.Dinero(s.monto().valorMenor(), s.monto().moneda()), com.apexstore.ice.pagos.MedioPago.valueOf(s.medio().name()), s.tokenPago());
    }
    public static RespuestaPago desdeSlice(com.apexstore.ice.pagos.RespuestaPago r) {
        return new RespuestaPago(r.idTransaccionExterna, EstadoPago.valueOf(r.estado.name().replace("REEMBOLSOPENDIENTE", "REEMBOLSO_PENDIENTE")), Map.copyOf(r.instrucciones));
    }
    public static com.apexstore.ice.pagos.EstadoPago aSlice(EstadoPago s) { return com.apexstore.ice.pagos.EstadoPago.valueOf(s.name().replace("REEMBOLSO_PENDIENTE", "REEMBOLSOPENDIENTE")); }
    public static EstadoPago desdeSlice(com.apexstore.ice.pagos.EstadoPago s) { return EstadoPago.valueOf(s.name().replace("REEMBOLSOPENDIENTE", "REEMBOLSO_PENDIENTE")); }
    public static com.apexstore.ice.pagos.ResultadoPago aSlice(ResultadoPago r) { return new com.apexstore.ice.pagos.ResultadoPago(r.idEvento(), r.idTransaccionExterna(), r.claveIdempotencia(), aSlice(r.estado()), r.ocurridoEnEpochMs()); }
    public static ResultadoPago desdeSlice(com.apexstore.ice.pagos.ResultadoPago r) { return new ResultadoPago(r.idEvento, r.idTransaccionExterna, r.claveIdempotencia, desdeSlice(r.estado), r.ocurridoEnEpochMs); }
}
