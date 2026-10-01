package com.apexstore.nodo2;

import com.apexstore.contratos.*;
import com.apexstore.ice.ConversorIce;
import com.apexstore.ice.pagos.ReceptorResultados;
import com.apexstore.ice.pagos.SolicitudInvalida;
import com.zeroc.Ice.Current;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.*;

public final class ReceptorCallbacks implements ReceptorResultados {
 private final RepositorioTransacciones repo;private final ProcesadorPagosContexto contexto;private final String secret;
 public ReceptorCallbacks(RepositorioTransacciones r,ProcesadorPagosContexto c,String s){repo=r;contexto=c;secret=s;}
 @Override public CompletionStage<Void> notificarResultadoPagoAsync(com.apexstore.ice.pagos.ResultadoPago wire,String signature,long timestamp,Current current){
  try{ResultadoPago r=ConversorIce.desdeSlice(wire);long now=System.currentTimeMillis();if(timestamp<now-300000||timestamp>now+300000)throw new SolicitudInvalida("Marca temporal fuera de ventana");String expected=sign(r,timestamp,secret);if(signature==null||!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),signature.getBytes(StandardCharsets.US_ASCII)))throw new SolicitudInvalida("Firma inválida");contexto.procesarResultadoPago(r);return CompletableFuture.completedFuture(null);}
  catch(Exception e){return CompletableFuture.failedFuture(e);}
 }
 private static String sign(ResultadoPago r,long timestamp,String secret)throws Exception{String data=r.idEvento()+"|"+r.idTransaccionExterna()+"|"+r.claveIdempotencia()+"|"+r.estado()+"|"+r.ocurridoEnEpochMs()+"|"+timestamp;Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));}
}
