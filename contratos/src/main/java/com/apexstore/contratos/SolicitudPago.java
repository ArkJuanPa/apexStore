package com.apexstore.contratos;
/**
 * Solicitud de cobro con clave de idempotencia. Evita el doble cobro (RAS-03).
 */
public record SolicitudPago(String idOrden, String claveIdempotencia, Dinero monto, MedioPago medio, String tokenPago) { }
