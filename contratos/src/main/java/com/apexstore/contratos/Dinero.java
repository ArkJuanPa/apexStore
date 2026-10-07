package com.apexstore.contratos;
/**
 * Monto en unidad menor y moneda ISO. Tipo de dominio sin dependencia de ICE (separacion interfaz/implementacion).
 */
public record Dinero(long valorMenor, String moneda) { public Dinero { if (valorMenor < 0 || moneda == null || !moneda.matches("[A-Z]{3}")) throw new IllegalArgumentException("Dinero invalido"); } }
