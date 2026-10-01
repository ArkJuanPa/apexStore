package com.apexstore.pruebas;

import com.apexstore.nodo2.ConfiguracionBaseDatos;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class ReglasBaseTest {
    @Test void rechazaIdentificadorDeEsquemaConSql() {
        var c = new ConfiguracionBaseDatos("externo", "localhost", 5432, "apexstore", "user", "", "x; DROP TABLE y", "disable", 5, false, true, false, 1);
        assertThrows(IllegalArgumentException.class, c::validar);
    }

    @Test void migracionInicialNoContieneOperacionesDestructivas() throws Exception {
        Path root = Path.of("..").toAbsolutePath().normalize();
        Path migrations = root.resolve("nodo2-backend/src/main/resources/db/migration");
        assertTrue(Files.isDirectory(migrations));
        try (var files = Files.list(migrations)) {
            for (Path f : files.filter(x -> x.toString().endsWith(".sql")).toList()) {
                String sql = Files.readString(f).toUpperCase();
                assertFalse(sql.matches("(?s).*\\b(DROP|TRUNCATE)\\b.*"), f.toString());
            }
        }
    }
}
