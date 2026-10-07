package com.apexstore.contratos;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Lee la configuracion de variables de entorno y, si no existen, del archivo .env local (sin dependencias extra).
 * Atiende la regla de secretos: .env nunca se versiona.
 */
public final class Entorno {
    private Entorno() { }

    public static String valor(String clave, String porDefecto) {
        String valor = System.getenv(clave);
        if (valor != null) {
            return valor;
        }
        Path archivo = Path.of(".env");
        if (Files.isRegularFile(archivo)) {
            return leerDeArchivo(archivo, clave, porDefecto);
        }
        return porDefecto;
    }

    private static String leerDeArchivo(Path archivo, String clave, String porDefecto) {
        try {
            for (String linea : Files.readAllLines(archivo)) {
                String s = linea.trim();
                if (s.startsWith("#") || !s.startsWith(clave + "=")) {
                    continue;
                }
                return sinComillas(s.substring(s.indexOf('=') + 1).trim());
            }
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo leer .env", e);
        }
        return porDefecto;
    }

    private static String sinComillas(String valor) {
        boolean entreComillas = valor.length() > 1
                && ((valor.startsWith("\"") && valor.endsWith("\"")) || (valor.startsWith("'") && valor.endsWith("'")));
        return entreComillas ? valor.substring(1, valor.length() - 1) : valor;
    }
}
