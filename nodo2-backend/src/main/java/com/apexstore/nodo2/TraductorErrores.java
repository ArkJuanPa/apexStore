package com.apexstore.nodo2;

import com.apexstore.ice.pagos.MedioNoDisponible;
import com.zeroc.Ice.UserException;
import java.sql.SQLException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Convierte las causas internas de un pago en lo que el contrato Slice puede expresar. Atiende RAS-01:
 * un medio caido se presenta como MedioNoDisponible con una alternativa, no como un error generico.
 */
final class TraductorErrores {
    private static final String ALTERNATIVA = "Seleccione otro medio de pago";

    private TraductorErrores() { }

    static boolean esConflictoIdempotencia(Throwable e) {
        for (Throwable causa = e; causa != null; causa = causa.getCause()) {
            if (causa instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    static boolean esTimeout(Throwable e) {
        for (Throwable causa = e; causa != null; causa = causa.getCause()) {
            if (causa instanceof TimeoutException || causa.getClass().getSimpleName().contains("InvocationTimeout")) {
                return true;
            }
        }
        return false;
    }

    static Exception aExcepcionSlice(Throwable e) {
        for (Throwable causa = e; causa != null; causa = causa.getCause()) {
            if (causa instanceof UserException usuario) {
                return usuario;
            }
        }
        for (Throwable causa = e; causa != null; causa = causa.getCause()) {
            if (causa instanceof RejectedExecutionException rechazo) {
                return new MedioNoDisponible(rechazo.getMessage(), ALTERNATIVA);
            }
        }
        return new MedioNoDisponible("El medio no esta disponible; pruebe otro medio", ALTERNATIVA);
    }
}
