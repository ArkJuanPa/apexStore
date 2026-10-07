package com.apexstore.nodo4;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Prepara PostgreSQL: espera, crea la base si se permite, valida la version y ejecuta schema.sql con la semilla.
 * Atiende RAS-03: el esquema se crea de forma idempotente y sin operaciones destructivas.
 */
public final class InicializadorBaseDatos {
    private static final int VERSION_MINIMA = 140000;

    private InicializadorBaseDatos() { }

    public static HikariDataSource inicializar(ConfiguracionBaseDatos c) throws Exception {
        c.validar();
        esperarBase(c);
        validarVersionYEsquema(c);
        var ds = new HikariDataSource(configurarPool(c));
        try (Connection conn = ds.getConnection(); Statement st = conn.createStatement()) {
            st.execute(leerEsquema());
        }
        return ds;
    }

    /** Solo lectura: comprueba version y permiso CREATE sin cambiar nada. */
    public static void verificar(ConfiguracionBaseDatos c) throws Exception {
        try (Connection con = DriverManager.getConnection(c.jdbcUrl(c.base()), c.usuario(), c.password());
             Statement st = con.createStatement()) {
            comprobarVersion(st);
            boolean existe = existeEsquema(con, c.esquema());
            String sql = existe
                    ? "SELECT has_schema_privilege(current_user, ?, 'CREATE')"
                    : "SELECT has_database_privilege(current_user, current_database(), 'CREATE')";
            if (!tienePermisoCreate(con, sql, existe ? c.esquema() : null)) {
                throw new SQLException(existe
                        ? "El usuario no tiene permiso CREATE en el esquema configurado"
                        : "El usuario no puede crear el esquema configurado en esta base");
            }
            System.out.println("PostgreSQL verificado: " + c.host() + ":" + c.puerto() + "/" + c.base()
                    + ", esquema=" + c.esquema() + ", version adecuada; sin cambios realizados.");
        }
    }

    private static void esperarBase(ConfiguracionBaseDatos c) throws Exception {
        long fin = System.nanoTime() + c.esperaMax() * 1_000_000_000L;
        Exception ultima = null;
        while (System.nanoTime() < fin) {
            try {
                prepararBase(c);
                return;
            } catch (Exception e) {
                ultima = e;
                Thread.sleep(1000);
            }
        }
        throw new SQLException("Tiempo agotado esperando PostgreSQL", ultima);
    }

    private static void validarVersionYEsquema(ConfiguracionBaseDatos c) throws SQLException {
        try (Connection conn = DriverManager.getConnection(c.jdbcUrl(c.base()), c.usuario(), c.password());
             Statement st = conn.createStatement()) {
            comprobarVersion(st);
            st.execute("CREATE SCHEMA IF NOT EXISTS \"" + c.esquema() + "\"");
        }
    }

    private static HikariConfig configurarPool(ConfiguracionBaseDatos c) {
        var hc = new HikariConfig();
        hc.setJdbcUrl(c.jdbcUrl(c.base()));
        hc.setUsername(c.usuario());
        hc.setPassword(c.password());
        hc.setMaximumPoolSize(c.poolMax());
        hc.setConnectionInitSql("SET search_path TO \"" + c.esquema() + "\"");
        return hc;
    }

    private static void comprobarVersion(Statement st) throws SQLException {
        try (var rs = st.executeQuery("SHOW server_version_num")) {
            rs.next();
            if (Integer.parseInt(rs.getString(1)) < VERSION_MINIMA) {
                throw new SQLException("Se requiere PostgreSQL 14 o superior");
            }
        }
    }

    private static String leerEsquema() throws IOException {
        try (var in = InicializadorBaseDatos.class.getResourceAsStream("/schema.sql")) {
            if (in == null) {
                throw new FileNotFoundException("No existe /schema.sql en el classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void prepararBase(ConfiguracionBaseDatos c) throws SQLException {
        try (Connection con = DriverManager.getConnection(c.jdbcUrl(c.base()), c.usuario(), c.password())) {
            return;
        } catch (SQLException falta) {
            if (!c.crearBase()) {
                throw falta;
            }
            crearBase(c);
        }
    }

    private static void crearBase(ConfiguracionBaseDatos c) throws SQLException {
        try (Connection con = DriverManager.getConnection(c.jdbcUrl("postgres"), c.usuario(), c.password());
             Statement st = con.createStatement()) {
            if (existeBase(st, c.base())) {
                return;
            }
            st.execute("CREATE DATABASE \"" + c.base() + "\"");
        } catch (SQLException e) {
            throw new SQLException("No se pudo crear la base; cree DB_NOMBRE manualmente o conceda CREATEDB", e);
        }
    }

    private static boolean existeBase(Statement st, String base) throws SQLException {
        try (var rs = st.executeQuery("SELECT 1 FROM pg_database WHERE datname = '" + base + "'")) {
            return rs.next();
        }
    }

    private static boolean existeEsquema(Connection con, String esquema) throws SQLException {
        String sql = "SELECT EXISTS(SELECT 1 FROM information_schema.schemata WHERE schema_name=?)";
        try (var p = con.prepareStatement(sql)) {
            p.setString(1, esquema);
            try (var rs = p.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }

    private static boolean tienePermisoCreate(Connection con, String sql, String esquema) throws SQLException {
        try (var p = con.prepareStatement(sql)) {
            if (esquema != null) {
                p.setString(1, esquema);
            }
            try (var rs = p.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }
}
