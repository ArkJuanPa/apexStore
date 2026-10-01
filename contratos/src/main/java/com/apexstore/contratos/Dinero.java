package com.apexstore.contratos;
public record Dinero(long valorMenor, String moneda) { public Dinero { if (valorMenor < 0 || moneda == null || !moneda.matches("[A-Z]{3}")) throw new IllegalArgumentException("Dinero inválido"); } }
