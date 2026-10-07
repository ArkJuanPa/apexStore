package com.apexstore.nodo4;

import java.util.*;

/** Datos de PostgreSQL leídos de .env (o del entorno). Nunca se imprime la contraseña. */
public record ConfiguracionBaseDatos(String modo, String host, int puerto, String base, String usuario,
        String password, String esquema, String sslmode, int poolMax, boolean crearBase, int esperaMax) {
    private static final java.util.regex.Pattern IDENT = java.util.regex.Pattern.compile("[a-z_][a-z0-9_]{0,62}");

    public static ConfiguracionBaseDatos cargar() {
        var c = new ConfiguracionBaseDatos(env("DB_MODO", "externo"), env("DB_HOST", "localhost"),
                integer("DB_PORT", 5432), env("DB_NOMBRE", "apexstore"), env("DB_USUARIO", "apexstore"),
                env("DB_PASSWORD", ""), env("DB_ESQUEMA", "apexstore"), env("DB_SSLMODE", "disable"),
                integer("DB_POOL_MAX", 20), bool("DB_CREAR_BASE_SI_NO_EXISTE", false),
                integer("DB_ESPERA_MAX_SEG", 60));
        c.validar();
        return c;
    }

    public void validar() {
        if (!IDENT.matcher(base).matches() || !IDENT.matcher(esquema).matches()) throw new IllegalArgumentException("DB_NOMBRE y DB_ESQUEMA deben cumplir [a-z_][a-z0-9_]{0,62}");
        if (!modo.equals("externo") && !modo.equals("contenedor")) throw new IllegalArgumentException("DB_MODO debe ser externo o contenedor");
        if (puerto < 1 || puerto > 65535 || poolMax < 1 || esperaMax < 1) throw new IllegalArgumentException("Puerto, pool o espera fuera de rango");
        if (!Set.of("disable", "require", "verify-full").contains(sslmode)) throw new IllegalArgumentException("DB_SSLMODE invalido");
        if (host.isBlank() || usuario.isBlank()) throw new IllegalArgumentException("DB_HOST y DB_USUARIO son obligatorios");
    }

    public String jdbcUrl(String database) { return "jdbc:postgresql://" + host + ":" + puerto + "/" + database + "?sslmode=" + sslmode; }

    private static String env(String k, String d) { return com.apexstore.contratos.Entorno.valor(k,d); }
    private static int integer(String k, int d) { return Integer.parseInt(env(k, Integer.toString(d))); }
    private static boolean bool(String k, boolean d) { return Boolean.parseBoolean(env(k, Boolean.toString(d))); }
}
