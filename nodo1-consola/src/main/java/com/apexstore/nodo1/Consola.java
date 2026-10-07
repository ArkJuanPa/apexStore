package com.apexstore.nodo1;

import java.util.Scanner;

/**
 * Entrada de texto del menu y estado de la sesion (ultima orden). Cohesion: todo lo que pide datos al usuario.
 */
final class Consola {
    private final Scanner entrada = new Scanner(System.in);
    private String ultimaOrden;

    void recordarOrden(String orden) {
        ultimaOrden = orden;
    }

    boolean hayLinea() {
        return entrada.hasNextLine();
    }

    String linea() {
        return entrada.nextLine().trim();
    }

    String leerOrden() {
        System.out.print("Orden (Enter = ultima" + (ultimaOrden == null ? "" : ": " + ultimaOrden) + ") > ");
        String texto = linea();
        return texto.isEmpty() ? ultimaOrden : texto;
    }

    long leerNumero(String etiqueta, long porDefecto) {
        System.out.print(etiqueta + " (Enter = " + porDefecto + ") > ");
        String texto = linea();
        return texto.isEmpty() ? porDefecto : Long.parseLong(texto);
    }

    int elegir(String etiqueta, String[] opciones) {
        System.out.println(etiqueta + ":");
        mostrarOpciones(opciones);
        System.out.print("> ");
        return Integer.parseInt(linea()) - 1;
    }

    int elegirOpcional(String etiqueta, String[] opciones, int actual) {
        System.out.println(etiqueta + " actual: " + opciones[actual] + ". Enter para mantener, o elija:");
        mostrarOpciones(opciones);
        System.out.print("> ");
        String texto = linea();
        return texto.isEmpty() ? actual : Integer.parseInt(texto) - 1;
    }

    private static void mostrarOpciones(String[] opciones) {
        for (int i = 0; i < opciones.length; i++) {
            System.out.println("  " + (i + 1) + ") " + opciones[i]);
        }
    }
}
