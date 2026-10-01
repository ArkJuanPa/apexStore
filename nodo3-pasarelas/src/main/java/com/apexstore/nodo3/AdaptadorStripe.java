package com.apexstore.nodo3;
import com.apexstore.contratos.*;
import com.apexstore.ice.pagos.ConfigSimulacion;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
final class AdaptadorStripe implements IAdaptadorPasarela {
 public MedioPago medio(){return MedioPago.STRIPE;}
 public RespuestaPago traducir(SolicitudPago s){return new RespuestaPago("SIM-STRIPE-"+UUID.randomUUID(),EstadoPago.PENDIENTE,Map.of("entorno","simulado","moneda","USD"));}
 public long demoraMs(String token,ConfigSimulacion c){return token.equals("tok_sim_lento")?15000:1000+ThreadLocalRandom.current().nextLong(2001);}
}
