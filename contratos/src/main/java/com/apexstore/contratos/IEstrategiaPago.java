package com.apexstore.contratos;
public interface IEstrategiaPago {
 RespuestaPago iniciarPago(SolicitudPago solicitud);
 EstadoPago consultarEstado(String claveIdempotencia);
 boolean soporta(MedioPago medio);
}
