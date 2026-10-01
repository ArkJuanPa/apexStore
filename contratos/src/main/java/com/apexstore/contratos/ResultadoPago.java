package com.apexstore.contratos;
public record ResultadoPago(String idEvento, String idTransaccionExterna, String claveIdempotencia, EstadoPago estado, long ocurridoEnEpochMs) { }
