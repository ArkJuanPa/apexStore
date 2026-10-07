package com.apexstore.nodo4;

import com.apexstore.contratos.EstadoPago;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.sql.DataSource;

/**
 * Ordenes e inventario: productos, creacion y consulta de ordenes, y liberacion de stock al expirar.
 * Separado de las transacciones de pago porque es otra responsabilidad (alta cohesion). Atiende RAS-03:
 * cada operacion es una transaccion local en Nodo 4.
 */
public final class RepositorioOrdenes {
    private final DataSource dataSource;

    public RepositorioOrdenes(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public record Item(long productoId, int cantidad) { }

    public List<Map<String, Object>> productos() throws SQLException {
        var out = new ArrayList<Map<String, Object>>();
        String sql = "SELECT id,nombre,precio_menor,moneda,stock FROM productos ORDER BY id";
        try (Connection c = dataSource.getConnection();
             PreparedStatement p = c.prepareStatement(sql);
             ResultSet r = p.executeQuery()) {
            while (r.next()) {
                out.add(Map.of("productoId", r.getLong(1), "nombre", r.getString(2), "precioMenor", r.getLong(3),
                        "moneda", r.getString(4).trim(), "stock", r.getInt(5)));
            }
        }
        return out;
    }

    public Map<String, Object> crearOrden(List<Item> pedido) throws SQLException {
        if (pedido.isEmpty()) {
            throw new SQLException("La orden requiere productos");
        }
        if (pedido.stream().anyMatch(i -> i.productoId() <= 0 || i.cantidad() <= 0)) {
            throw new SQLException("Producto y cantidad deben ser positivos");
        }
        List<Item> items = consolidar(pedido);
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                UUID id = UUID.randomUUID();
                long total = 0;
                String moneda = null;
                for (Item item : items) {
                    try (PreparedStatement p = c.prepareStatement("SELECT precio_menor,moneda FROM productos WHERE id=?")) {
                        p.setLong(1, item.productoId());
                        try (ResultSet r = p.executeQuery()) {
                            if (!r.next()) {
                                throw new SQLException("Producto inexistente");
                            }
                            long precio = r.getLong(1);
                            String monedaProducto = r.getString(2).trim();
                            if (moneda != null && !moneda.equals(monedaProducto)) {
                                throw new SQLException("Una orden debe usar una sola moneda");
                            }
                            moneda = monedaProducto;
                            total = Math.addExact(total, Math.multiplyExact(precio, item.cantidad()));
                        }
                    }
                }
                insertarOrden(c, id, total, moneda);
                for (Item item : items) {
                    reservarStock(c, id, item);
                }
                c.commit();
                return Map.of("id", id.toString(), "estado", "CREADA", "totalMenor", total, "moneda", moneda);
            } catch (Exception e) {
                c.rollback();
                throw aSqlException(e);
            }
        }
    }

    public Map<String, Object> obtenerOrden(String id) throws SQLException {
        String sql = "SELECT o.id,o.estado,o.total_menor,o.moneda,t.estado,t.medio,t.clave_idempotencia,t.id_transaccion_externa "
                + "FROM ordenes o LEFT JOIN transacciones_pago t ON t.orden_id=o.id "
                + "WHERE o.id=? ORDER BY t.creada_en DESC LIMIT 1";
        try (Connection c = dataSource.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setObject(1, UUID.fromString(id));
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) {
                    return null;
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", r.getString(1));
                m.put("estado", r.getString(2));
                m.put("totalMenor", r.getLong(3));
                m.put("moneda", r.getString(4).trim());
                m.put("pagoEstado", r.getString(5));
                m.put("medio", r.getString(6));
                m.put("idempotencyKey", r.getString(7));
                m.put("referencia", r.getString(8));
                return m;
            }
        }
    }

    /** Expira las transacciones vencidas y libera su stock. Lo llama el reconciliador. */
    public int expirarVencidas() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                int expiradas = expirarEnTransaccion(c);
                c.commit();
                return expiradas;
            } catch (Exception e) {
                c.rollback();
                throw aSqlException(e);
            }
        }
    }

    private static List<Item> consolidar(List<Item> pedido) {
        Map<Long, Integer> cantidades = pedido.stream()
                .collect(Collectors.toMap(Item::productoId, Item::cantidad, Integer::sum, LinkedHashMap::new));
        return cantidades.entrySet().stream()
                .map(e -> new Item(e.getKey(), e.getValue()))
                .toList();
    }

    private static void insertarOrden(Connection c, UUID id, long total, String moneda) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("INSERT INTO ordenes(id,estado,total_menor,moneda) VALUES(?,'CREADA',?,?)")) {
            p.setObject(1, id);
            p.setLong(2, total);
            p.setString(3, moneda);
            p.executeUpdate();
        }
    }

    private static void reservarStock(Connection c, UUID id, Item item) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("UPDATE productos SET stock=stock-? WHERE id=? AND stock>=?")) {
            p.setInt(1, item.cantidad());
            p.setLong(2, item.productoId());
            p.setInt(3, item.cantidad());
            if (p.executeUpdate() != 1) {
                throw new SQLException("Stock insuficiente");
            }
        }
        try (PreparedStatement p = c.prepareStatement("INSERT INTO reservas_stock(orden_id,producto_id,cantidad) VALUES(?,?,?)")) {
            p.setObject(1, id);
            p.setLong(2, item.productoId());
            p.setInt(3, item.cantidad());
            p.executeUpdate();
        }
    }

    private static int expirarEnTransaccion(Connection c) throws SQLException {
        int expiradas = 0;
        String vencidas = "SELECT id,orden_id FROM transacciones_pago "
                + "WHERE estado='PENDIENTE' AND vence_en<=now() FOR UPDATE SKIP LOCKED";
        try (PreparedStatement p = c.prepareStatement(vencidas);
             ResultSet r = p.executeQuery()) {
            while (r.next()) {
                UUID tx = r.getObject(1, UUID.class);
                UUID orden = r.getObject(2, UUID.class);
                expirar(c, tx, orden);
                expiradas++;
            }
        }
        return expiradas;
    }

    private static void expirar(Connection c, UUID tx, UUID orden) throws SQLException {
        ejecutar(c, "UPDATE transacciones_pago SET estado='EXPIRADA',actualizada_en=now() WHERE id=?", tx);
        ejecutar(c, "UPDATE productos p SET stock=p.stock+r.cantidad FROM reservas_stock r "
                + "WHERE r.orden_id=? AND r.producto_id=p.id AND r.liberada=false", orden);
        ejecutar(c, "UPDATE reservas_stock SET liberada=true WHERE orden_id=?", orden);
        ejecutar(c, "UPDATE ordenes SET estado='EXPIRADA',version=version+1 WHERE id=?", orden);
        auditoria(c, tx, EstadoPago.PENDIENTE, EstadoPago.EXPIRADA, "reconciliador");
    }

    private static void ejecutar(Connection c, String sql, UUID id) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setObject(1, id);
            p.executeUpdate();
        }
    }

    private static void auditoria(Connection c, UUID tx, EstadoPago anterior, EstadoPago nuevo, String origen) throws SQLException {
        String sql = "INSERT INTO bitacora_auditoria(transaccion_id,estado_anterior,estado_nuevo,origen,detalle) VALUES (?,?,?,?,?::jsonb)";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setObject(1, tx);
            p.setString(2, anterior.name());
            p.setString(3, nuevo.name());
            p.setString(4, origen);
            p.setString(5, "{}");
            p.executeUpdate();
        }
    }

    private static SQLException aSqlException(Exception e) {
        return e instanceof SQLException sql ? sql : new SQLException(e);
    }
}
