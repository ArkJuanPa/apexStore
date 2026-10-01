package com.apexstore.nodo2;

import com.apexstore.contratos.*;
import java.util.UUID;
import java.util.concurrent.*;

public final class ReconciliadorPagos implements AutoCloseable {
 private final ScheduledExecutorService executor=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"reconciliador-pagos");t.setDaemon(true);return t;});
 public ReconciliadorPagos(RepositorioTransacciones repo,RegistroEstrategias registry,ProcesadorPagosContexto context,int periodSeconds){executor.scheduleWithFixedDelay(()->{try{for(var tx:repo.pendientes()){try{EstadoPago state=registry.resolver(tx.medio()).consultarEstado(tx.claveIdempotencia());if(state!=EstadoPago.PENDIENTE)context.procesarResultadoPago(new ResultadoPago("reconciliacion-"+UUID.randomUUID(),"SIM-REC-"+UUID.randomUUID(),tx.claveIdempotencia(),state,System.currentTimeMillis()));}catch(Exception ignored){}}repo.expirarVencidas();}catch(Exception ignored){}},periodSeconds,periodSeconds,TimeUnit.SECONDS);}
 @Override public void close(){executor.shutdownNow();}
}
