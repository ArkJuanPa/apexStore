package com.apexstore.ice;

import com.apexstore.contratos.*;
import java.util.Map;

/**
 * Traduce entre el dominio y los tipos generados del Slice. Mantiene el dominio libre de ICE (separacion interfaz/implementacion).
 */
public final class ConversorIce {
    private ConversorIce() { }
    public static com.apexstore.ice.pagos.SolicitudPago aSlice(SolicitudPago s) {
        return new com.apexstore.ice.pagos.SolicitudPago(s.idOrden(), s.claveIdempotencia(), new com.apexstore.ice.pagos.Dinero(s.monto().valorMenor(), s.monto().moneda()), s.medio().name(), s.tokenPago());
    }
    public static RespuestaPago desdeSlice(com.apexstore.ice.pagos.RespuestaPago r) {
        return new RespuestaPago(r.idTransaccionExterna, EstadoPago.valueOf(r.estado), Map.copyOf(r.instrucciones));
    }
    public static String aSlice(EstadoPago s) { return s.name(); }
    public static EstadoPago desdeSlice(String s) { return EstadoPago.valueOf(s); }
    public static com.apexstore.ice.pagos.ResultadoPago aSlice(ResultadoPago r) { return new com.apexstore.ice.pagos.ResultadoPago(r.idEvento(), r.idTransaccionExterna(), r.claveIdempotencia(), aSlice(r.estado()), r.ocurridoEnEpochMs()); }
    public static ResultadoPago desdeSlice(com.apexstore.ice.pagos.ResultadoPago r) { return new ResultadoPago(r.idEvento, r.idTransaccionExterna, r.claveIdempotencia, desdeSlice(r.estado), r.ocurridoEnEpochMs); }
}
