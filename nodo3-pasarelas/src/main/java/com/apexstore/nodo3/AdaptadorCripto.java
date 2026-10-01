package com.apexstore.nodo3;
import com.apexstore.contratos.*;
import com.apexstore.ice.pagos.ConfigSimulacion;
import java.util.*;
final class AdaptadorCripto implements IAdaptadorPasarela {
 public MedioPago medio(){return MedioPago.CRIPTO;}
 public RespuestaPago traducir(SolicitudPago s){String address="SIM-BTC-"+UUID.randomUUID();return new RespuestaPago(address,EstadoPago.PENDIENTE,Map.of("direccion",address,"unidad","satoshis"));}
 public long demoraMs(String token,ConfigSimulacion c){return token.equals("tok_sim_lento")?15000:Math.max(2500L,1000L*Math.max(1,c.confirmacionesRequeridas));}
}
