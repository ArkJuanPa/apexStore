package com.apexstore.contratos;
import java.util.Map;
/**
 * Respuesta sincrona de una pasarela: referencia e instrucciones. Dominio sin ICE.
 */
public record RespuestaPago(String idTransaccionExterna, EstadoPago estado, Map<String,String> instrucciones) { public RespuestaPago { instrucciones = Map.copyOf(instrucciones); } }
