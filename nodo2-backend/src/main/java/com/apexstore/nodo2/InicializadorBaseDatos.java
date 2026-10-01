package com.apexstore.nodo2;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import java.sql.*;
import java.time.Instant;

public final class InicializadorBaseDatos {
    private InicializadorBaseDatos() { }
    public static void main(String[] args) {
        try {
            var config = ConfiguracionBaseDatos.cargar();
            if (args.length > 0 && args[0].equals("--verificar-db")) { verificar(config); return; }
            ServicioCheckout.iniciar(config);
        } catch (Exception e) {
            System.err.println("No se pudo inicializar PostgreSQL. Revise host/puerto, credenciales, pg_hba.conf, host.docker.internal y firewall. Causa: " + safeCause(e));
            System.exit(1);
        }
    }

    public static HikariDataSource inicializar(ConfiguracionBaseDatos c) throws Exception {
        c.validar();
        long fin = System.nanoTime() + c.esperaMax() * 1_000_000_000L;
        Exception ultima = null;
        while (System.nanoTime() < fin) {
            try { prepararBase(c); break; } catch (Exception e) { ultima = e; Thread.sleep(1000); }
        }
        if (ultima != null && System.nanoTime() >= fin) throw new SQLException("Tiempo agotado esperando PostgreSQL", ultima);
        var hc = new HikariConfig(); hc.setJdbcUrl(c.jdbcUrl(c.base())); hc.setUsername(c.usuario()); hc.setPassword(c.password());
        try (var conn = DriverManager.getConnection(c.jdbcUrl(c.base()), c.usuario(), c.password()); var st = conn.createStatement()) {
            try (var rs = st.executeQuery("SHOW server_version_num")) { rs.next(); if (Integer.parseInt(rs.getString(1)) < 140000) throw new SQLException("Se requiere PostgreSQL 14 o superior"); }
            st.execute("CREATE SCHEMA IF NOT EXISTS \"" + c.esquema() + "\"");
        }
        hc.setMaximumPoolSize(c.poolMax()); hc.setConnectionInitSql("SET search_path TO \"" + c.esquema() + "\"");
        var ds = new HikariDataSource(hc);
        if (c.migrar()) Flyway.configure().dataSource(ds).schemas(c.esquema()).defaultSchema(c.esquema()).locations("classpath:db/migration").load().migrate();
        if (c.sembrar()) sembrar(ds);
        return ds;
    }

    private static void prepararBase(ConfiguracionBaseDatos c) throws SQLException {
        try (var con = DriverManager.getConnection(c.jdbcUrl(c.base()), c.usuario(), c.password())) { return; }
        catch (SQLException missing) {
            if (!c.crearBase()) throw missing;
            try (var con = DriverManager.getConnection(c.jdbcUrl("postgres"), c.usuario(), c.password()); var st = con.createStatement()) {
                try (var rs = st.executeQuery("SELECT 1 FROM pg_database WHERE datname = '" + c.base() + "'")) { if (rs.next()) return; }
                st.execute("CREATE DATABASE \"" + c.base() + "\"");
            } catch (SQLException e) { throw new SQLException("No se pudo crear la base; cree DB_NOMBRE manualmente o conceda CREATEDB", e); }
        }
    }

    private static void sembrar(HikariDataSource ds) throws SQLException {
        try (var c = ds.getConnection(); var p = c.prepareStatement("INSERT INTO productos(nombre,precio_menor,moneda,stock) VALUES (?,?,?,?) ON CONFLICT DO NOTHING")) {
            Object[][] rows = {{"Auriculares",129900L,"COP",50},{"Teclado",189900L,"COP",30},{"Camiseta",79900L,"COP",100}};
            for (Object[] r : rows) { p.setString(1,(String)r[0]); p.setLong(2,(Long)r[1]); p.setString(3,(String)r[2]); p.setInt(4,(Integer)r[3]); p.addBatch(); } p.executeBatch();
        }
    }

    private static void verificar(ConfiguracionBaseDatos c) throws Exception {
        try (var con = DriverManager.getConnection(c.jdbcUrl(c.base()), c.usuario(), c.password()); var st = con.createStatement()) {
            try (var rs = st.executeQuery("SHOW server_version_num")) { rs.next(); if (Integer.parseInt(rs.getString(1)) < 140000) throw new SQLException("Se requiere PostgreSQL 14 o superior"); }
            boolean exists;
            try (var p=con.prepareStatement("SELECT EXISTS(SELECT 1 FROM information_schema.schemata WHERE schema_name=?)")){p.setString(1,c.esquema());try(var rs=p.executeQuery()){rs.next();exists=rs.getBoolean(1);}}
            String privilegeSql=exists?"SELECT has_schema_privilege(current_user, ?, 'CREATE')":"SELECT has_database_privilege(current_user, current_database(), 'CREATE')";
            try (var p = con.prepareStatement(privilegeSql)) { if(exists)p.setString(1, c.esquema()); try (var rs=p.executeQuery()) { rs.next(); if (!rs.getBoolean(1)) throw new SQLException(exists?"El usuario no tiene permiso CREATE en el esquema configurado":"El usuario no puede crear el esquema configurado en esta base"); } }
            System.out.println("PostgreSQL verificado: " + c.host()+":"+c.puerto()+"/"+c.base()+", esquema="+c.esquema()+", versión adecuada; sin cambios realizados.");
        }
    }
    private static String safeCause(Throwable t) { Throwable x=t; while(x.getCause()!=null) x=x.getCause(); String s=x.getMessage(); return s==null?x.getClass().getSimpleName():s.replaceAll("(?i)(password|passwd)=([^\\s&]+)", "$1=****"); }
}
