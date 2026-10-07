package com.apexstore.contratos;
/**
 * Estrategia de pago del dominio (patron Strategy). Un medio nuevo implementa esta interfaz (RAS-04).
 */
public interface IEstrategia {
 RespuestaPago iniciarPago(SolicitudPago solicitud);
 EstadoPago consultarEstado(String claveIdempotencia);
 boolean soporta(MedioPago medio);
}
