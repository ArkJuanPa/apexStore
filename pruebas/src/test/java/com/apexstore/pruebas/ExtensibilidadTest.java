package com.apexstore.pruebas;

import static org.junit.jupiter.api.Assertions.assertSame;

import com.apexstore.contratos.EstadoPago;
import com.apexstore.contratos.IEstrategia;
import com.apexstore.contratos.MedioPago;
import com.apexstore.contratos.RespuestaPago;
import com.apexstore.contratos.SolicitudPago;
import com.apexstore.nodo2.RegistroEstrategias;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Prueba RAS-04: una estrategia nueva se registra sin tocar el contexto ni el repositorio. */
class ExtensibilidadTest {
    @Test
    void estrategiaCuatroSeRegistraSinCambiarContextoCheckoutNiRepositorio() {
        var registro = new RegistroEstrategias();
        IEstrategia extra = new IEstrategia() {
            @Override
            public RespuestaPago iniciarPago(SolicitudPago s) {
                return new RespuestaPago("SIM-WALLET", EstadoPago.PENDIENTE, Map.of("estado", "simulado"));
            }

            @Override
            public EstadoPago consultarEstado(String clave) {
                return EstadoPago.PENDIENTE;
            }

            @Override
            public boolean soporta(MedioPago medio) {
                return medio == MedioPago.BILLETERA_DIGITAL;
            }
        };
        registro.registrar(extra);
        assertSame(extra, registro.resolver(MedioPago.BILLETERA_DIGITAL));
    }
}
