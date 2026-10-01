package com.apexstore.contratos;
public record SolicitudPago(String idOrden, String claveIdempotencia, Dinero monto, MedioPago medio, String tokenPago) { }
