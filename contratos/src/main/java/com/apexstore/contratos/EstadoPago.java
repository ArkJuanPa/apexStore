package com.apexstore.contratos;
/**
 * Estados de una transaccion de pago. Son el vocabulario de la auditoria (RAS-03).
 */
public enum EstadoPago { PENDIENTE, CONFIRMADA, FALLIDA, EXPIRADA, REEMBOLSO_PENDIENTE, REEMBOLSADA }
