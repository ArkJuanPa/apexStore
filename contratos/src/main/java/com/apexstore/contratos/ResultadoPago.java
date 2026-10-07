package com.apexstore.contratos;
/**
 * Resultado asincrono que llega por callback. El pago no espera la confirmacion (RAS-01).
 */
public record ResultadoPago(String idEvento, String idTransaccionExterna, String claveIdempotencia, EstadoPago estado, long ocurridoEnEpochMs) { }
