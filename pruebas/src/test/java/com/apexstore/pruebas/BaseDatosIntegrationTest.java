package com.apexstore.pruebas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apexstore.contratos.Dinero;
import com.apexstore.contratos.EstadoPago;
import com.apexstore.contratos.MedioPago;
import com.apexstore.contratos.RespuestaPago;
import com.apexstore.contratos.ResultadoPago;
import com.apexstore.contratos.SolicitudPago;
import com.apexstore.nodo4.ConfiguracionBaseDatos;
import com.apexstore.nodo4.InicializadorBaseDatos;
import com.apexstore.nodo4.RepositorioOrdenes;
import com.apexstore.nodo4.RepositorioTransacciones;
import com.zaxxer.hikari.HikariDataSource;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Prueba RAS-03 con PostgreSQL real (Testcontainers): la semilla es idempotente y un reintento con la misma
 * clave de idempotencia devuelve la respuesta original.
 */
@Testcontainers(disabledWithoutDocker = true)
class BaseDatosIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void inicializadorYSemillaSonIdempotentes() throws Exception {
        var cfg = configuracion("apex_test");
        try (HikariDataSource primera = InicializadorBaseDatos.inicializar(cfg);
             HikariDataSource segunda = InicializadorBaseDatos.inicializar(cfg)) {
            assertEquals(3, cantidadProductos(segunda));
        }
    }

    @Test
    void reintentoIdempotenteRecuperaLaRespuestaOriginal() throws Exception {
        var cfg = configuracion("apex_retry");
        try (HikariDataSource ds = InicializadorBaseDatos.inicializar(cfg)) {
            var ordenes = new RepositorioOrdenes(ds);
            var repo = new RepositorioTransacciones(ds);
            var orden = ordenes.crearOrden(List.of(new RepositorioOrdenes.Item(1, 1)));
            var solicitud = new SolicitudPago(String.valueOf(orden.get("id")), "misma-clave",
                    new Dinero(((Number) orden.get("totalMenor")).longValue(), "COP"), MedioPago.CRIPTO, "tok_sim_ok");
            var primero = repo.registrarPendiente(solicitud);
            var original = new RespuestaPago("SIM-BTC-prueba", EstadoPago.PENDIENTE,
                    Map.of("direccion", "SIM-BTC-prueba", "unidad", "satoshis"));
            repo.guardarRespuesta("misma-clave", original);
            var reintento = repo.registrarPendiente(solicitud);
            assertFalse(reintento.nueva());
            assertEquals(original.idTransaccionExterna(), reintento.transaccionExterna());
            assertEquals(original.instrucciones(), reintento.instrucciones());
            assertEquals(primero.id(), reintento.id());
        }
    }

    @Test
    void mismoResultadoAplicadoDosVecesProduceUnSoloEfecto() throws Exception {
        var cfg = configuracion("apex_callback");
        try (HikariDataSource ds = InicializadorBaseDatos.inicializar(cfg)) {
            var orden = new RepositorioOrdenes(ds).crearOrden(List.of(new RepositorioOrdenes.Item(1, 1)));
            var repo = new RepositorioTransacciones(ds);
            var solicitud = new SolicitudPago(String.valueOf(orden.get("id")), "clave-callback",
                    new Dinero(((Number) orden.get("totalMenor")).longValue(), "COP"), MedioPago.STRIPE, "tok_sim_ok");
            repo.registrarPendiente(solicitud);
            var resultado = new ResultadoPago("evento-unico", "SIM-STRIPE-callback", "clave-callback",
                    EstadoPago.CONFIRMADA, 1L);
            assertTrue(repo.aplicarResultado(resultado));
            assertFalse(repo.aplicarResultado(resultado));
            assertEquals("1", valor(ds, "SELECT count(*) FROM bitacora_auditoria a JOIN transacciones_pago t "
                    + "ON t.id = a.transaccion_id WHERE t.clave_idempotencia = ? AND a.origen = 'callback'", "clave-callback"));
            assertEquals("PAGADA", valor(ds, "SELECT o.estado FROM ordenes o JOIN transacciones_pago t "
                    + "ON t.orden_id = o.id WHERE t.clave_idempotencia = ?", "clave-callback"));
        }
    }

    private static String valor(HikariDataSource ds, String sql, String parametro) throws Exception {
        try (var c = ds.getConnection();
             var p = c.prepareStatement(sql)) {
            p.setString(1, parametro);
            try (var r = p.executeQuery()) {
                assertTrue(r.next());
                return r.getString(1);
            }
        }
    }

    private static ConfiguracionBaseDatos configuracion(String esquema) {
        return new ConfiguracionBaseDatos("contenedor", pg.getHost(), pg.getFirstMappedPort(), pg.getDatabaseName(),
                pg.getUsername(), pg.getPassword(), esquema, "disable", 5, false, 20);
    }

    private static int cantidadProductos(HikariDataSource ds) throws Exception {
        try (var c = ds.getConnection();
             var p = c.prepareStatement("SELECT count(*) FROM productos");
             var r = p.executeQuery()) {
            assertTrue(r.next());
            return r.getInt(1);
        }
    }
}
