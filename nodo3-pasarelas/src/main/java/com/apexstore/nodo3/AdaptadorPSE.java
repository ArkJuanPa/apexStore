package com.apexstore.nodo3;
import com.apexstore.contratos.*;
import com.apexstore.ice.pagos.ConfigSimulacion;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
final class AdaptadorPSE implements IAdaptadorPasarela {
 public MedioPago medio(){return MedioPago.PSE;}
 public RespuestaPago traducir(SolicitudPago s){return new RespuestaPago("SIM-PSE-"+UUID.randomUUID(),EstadoPago.PENDIENTE,Map.of("banco","Banco Simulado","referencia",UUID.randomUUID().toString()));}
 public long demoraMs(String token,ConfigSimulacion c){return c.modo==com.apexstore.ice.pagos.ModoSimulacion.LENTO||token.equals("tok_sim_lento")?15000:2000+ThreadLocalRandom.current().nextLong(13001);}
}
