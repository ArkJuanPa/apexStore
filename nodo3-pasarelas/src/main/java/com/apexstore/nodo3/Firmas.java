package com.apexstore.nodo3;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
final class Firmas {
 static String firmar(com.apexstore.contratos.ResultadoPago r,long marca,String secreto) {
  try { String dato=r.idEvento()+"|"+r.idTransaccionExterna()+"|"+r.claveIdempotencia()+"|"+r.estado()+"|"+r.ocurridoEnEpochMs()+"|"+marca;
   Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8),"HmacSHA256")); return HexFormat.of().formatHex(mac.doFinal(dato.getBytes(StandardCharsets.UTF_8)));
  } catch(Exception e) { throw new IllegalStateException(e); }
 }
}
