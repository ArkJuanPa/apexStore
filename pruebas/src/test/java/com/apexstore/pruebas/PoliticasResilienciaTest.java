package com.apexstore.pruebas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.apexstore.contratos.MedioPago;
import com.apexstore.nodo2.PoliticasResiliencia;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;

/** Circuit breaker (RAS-01): tres caidas abren el medio, la ventana expira y una llamada exitosa lo cierra. */
class PoliticasResilienciaTest {
    private static final long VENTANA_MS = 300;

    @Test
    void tresCaidasAbrenElMedioYLaVentanaLoCierra() throws Exception {
        var politicas = new PoliticasResiliencia(VENTANA_MS);
        for (int i = 0; i < 3; i++) {
            assertThrows(IllegalStateException.class,
                    () -> politicas.ejecutar(MedioPago.PSE, () -> { throw new IllegalStateException("caido"); }));
        }
        assertThrows(RejectedExecutionException.class, () -> politicas.ejecutar(MedioPago.PSE, () -> "ok"));
        assertEquals("ok", politicas.ejecutar(MedioPago.STRIPE, () -> "ok"));
        Thread.sleep(VENTANA_MS + 100);
        assertEquals("ok", politicas.ejecutar(MedioPago.PSE, () -> "ok"));
        assertEquals("ok", politicas.ejecutar(MedioPago.PSE, () -> "ok"));
    }
}
