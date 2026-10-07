package com.apexstore.pruebas;

import com.apexstore.ice.pagos.IGestionComprasPrx;
import com.apexstore.ice.pagos.Item;
import com.apexstore.ice.pagos.Orden;
import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.InitializationData;
import com.zeroc.Ice.Util;
import java.io.PrintWriter;
import java.nio.file.*;
import java.util.*;

/**
 * Mide RAS-02 por tramos. Hace 10 pagos de calentamiento (no se cuentan) y 50 medidas por ICE.
 * Los tramos del servidor salen de las líneas "RAS02,orden,medio,repoMs,estrategiaMs,totalMs" del log de Nodo 2.
 * Uso: MedidorRAS02 "<proxy checkout>" "<log de nodo2>" "<csv de salida>"
 */
public final class MedidorRAS02 {
    private static final int CALENTAMIENTO = 10;
    private static final int MEDICIONES = 50;

    private MedidorRAS02() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) { throw new IllegalArgumentException("Uso: MedidorRAS02 <proxy checkout> <log de nodo2> <csv de salida>"); }
        var init = new InitializationData();
        init.properties = Util.createProperties();
        init.properties.setProperty("Ice.Default.Package", "com.apexstore.ice");
        try (Communicator comunicador = Util.initialize(init)) {
            IGestionComprasPrx compras = IGestionComprasPrx.uncheckedCast(comunicador.stringToProxy(args[0]));
            List<Double> cliente = new ArrayList<>();
            for (int i = 0; i < CALENTAMIENTO + MEDICIONES; i++) {
                Orden orden = compras.crearOrden(new Item[]{new Item(1, 1)});
                long inicio = System.nanoTime();
                compras.pagarOrden(orden.id, "STRIPE", "tok_sim_ok", "medicion-" + i + "-" + System.nanoTime());
                double ms = (System.nanoTime() - inicio) / 1e6;
                if (i >= CALENTAMIENTO) { cliente.add(ms); }
            }
            List<String[]> servidor = lineasRas02(Path.of(args[1]));
            if (servidor.size() < CALENTAMIENTO + MEDICIONES) {
                throw new IllegalStateException("El log tiene " + servidor.size() + " lineas RAS02; se esperaban al menos " + (CALENTAMIENTO + MEDICIONES));
            }
            List<String[]> medidas = servidor.subList(servidor.size() - (CALENTAMIENTO + MEDICIONES), servidor.size()).subList(CALENTAMIENTO, CALENTAMIENTO + MEDICIONES);
            escribir(Path.of(args[2]), medidas, cliente);
        }
    }

    private static List<String[]> lineasRas02(Path log) throws Exception {
        var out = new ArrayList<String[]>();
        for (String linea : Files.readAllLines(log)) {
            if (linea.startsWith("RAS02,")) { out.add(linea.split(",")); }
        }
        return out;
    }

    private static void escribir(Path csv, List<String[]> medidas, List<Double> cliente) throws Exception {
        var tramos = new LinkedHashMap<String, List<Double>>();
        tramos.put("a_nodo4_registro_y_guardado", columna(medidas, 3));
        tramos.put("b_invocacion_estrategia", columna(medidas, 4));
        tramos.put("c_orquestacion_total", columna(medidas, 5));
        tramos.put("d_cliente_pagarOrden", cliente);
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(csv))) {
            out.println("tramo,n,p50_ms,p95_ms");
            for (var e : tramos.entrySet()) {
                out.printf(Locale.ROOT, "%s,%d,%.1f,%.1f%n", e.getKey(), e.getValue().size(), percentil(e.getValue(), 0.50), percentil(e.getValue(), 0.95));
            }
        }
        for (var e : tramos.entrySet()) {
            System.out.printf(Locale.ROOT, "%-30s n=%d  P50=%.1f ms  P95=%.1f ms%n", e.getKey(), e.getValue().size(), percentil(e.getValue(), 0.50), percentil(e.getValue(), 0.95));
        }
        Path crudo = Path.of(csv.toString().replace(".csv", "-crudo.csv"));
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(crudo))) {
            out.println("n,repo_ms,estrategia_ms,total_ms,cliente_ms");
            for (int i = 0; i < medidas.size(); i++) {
                out.printf(Locale.ROOT, "%d,%s,%s,%s,%.3f%n", i + 1, medidas.get(i)[3], medidas.get(i)[4], medidas.get(i)[5], cliente.get(i));
            }
        }
    }

    private static List<Double> columna(List<String[]> filas, int indice) {
        var out = new ArrayList<Double>();
        for (String[] f : filas) { out.add(Double.parseDouble(f[indice])); }
        return out;
    }

    /** Percentil por rango más cercano (nearest-rank). */
    static double percentil(List<Double> valores, double p) {
        var ordenados = new ArrayList<>(valores);
        Collections.sort(ordenados);
        int indice = (int) Math.ceil(p * ordenados.size()) - 1;
        return ordenados.get(Math.max(0, Math.min(indice, ordenados.size() - 1)));
    }
}
