package com.apexstore.contratos;
import java.util.Map;
public record RespuestaPago(String idTransaccionExterna, EstadoPago estado, Map<String,String> instrucciones) { public RespuestaPago { instrucciones = Map.copyOf(instrucciones); } }
