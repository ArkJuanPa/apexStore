package com.apexstore.nodo2;

import com.apexstore.contratos.*;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;

public final class ProcesadorPagosContexto {
 private final RegistroEstrategias estrategias;private final RepositorioTransacciones repo;private final PoliticasResiliencia resiliencia=new PoliticasResiliencia();
 private final ConcurrentHashMap<String,CopyOnWriteArrayList<BlockingQueue<ResultadoPago>>> listeners=new ConcurrentHashMap<>();private final ConcurrentHashMap<String,Object> idempotencyLocks=new ConcurrentHashMap<>();private final ArrayDeque<Double> latencias=new ArrayDeque<>();
 public ProcesadorPagosContexto(RegistroEstrategias e,RepositorioTransacciones r){estrategias=e;repo=r;}
 public RespuestaPago iniciarPagoOrden(SolicitudPago solicitud)throws Exception {
  synchronized(idempotencyLocks.computeIfAbsent(solicitud.claveIdempotencia(),k->new Object())) {
  var registro=repo.registrarPendiente(solicitud);
  if(!registro.nueva())return new RespuestaPago(registro.transaccionExterna(),registro.estado(),registro.instrucciones());
  long phaseStart=System.nanoTime(); RespuestaPago response;
  try {response=resiliencia.ejecutar(solicitud.medio(),()->estrategias.resolver(solicitud.medio()).iniciarPago(solicitud));}
  catch(RejectedExecutionException e){repo.marcarFallida(solicitud.claveIdempotencia(),"resiliencia","capacidad o breaker");throw e;}
  catch(Exception e){if(!causedByTimeout(e)){repo.marcarFallida(solicitud.claveIdempotencia(),"ice","no enviado");}throw e;}
  finally{record(System.nanoTime()-phaseStart);}
  repo.guardarRespuesta(solicitud.claveIdempotencia(),response);return response;
  }
 }
 public void procesarResultadoPago(ResultadoPago r)throws SQLException {String order=repo.ordenDeClave(r.claveIdempotencia());if(order!=null&&repo.aplicarResultado(r)){for(var q:listeners.getOrDefault(order,new CopyOnWriteArrayList<>()))q.offer(r);}}
 public BlockingQueue<ResultadoPago> suscribir(String order){var q=new LinkedBlockingQueue<ResultadoPago>(32);listeners.computeIfAbsent(order,k->new CopyOnWriteArrayList<>()).add(q);return q;}
 public void cancelarSuscripcion(String order,BlockingQueue<ResultadoPago> queue){var list=listeners.get(order);if(list!=null){list.remove(queue);if(list.isEmpty())listeners.remove(order,list);}}
 public synchronized Map<String,Object> metricas(){double[] values=latencias.stream().mapToDouble(Double::doubleValue).sorted().toArray();return Map.of("muestras",values.length,"p50Ms",pct(values,.50),"p95Ms",pct(values,.95),"p99Ms",pct(values,.99),"breakers",resiliencia.estado());}
 private synchronized void record(long nanos){if(latencias.size()>=20000)latencias.removeFirst();latencias.addLast(nanos/1_000_000.0);}
 private static double pct(double[] a,double p){return a.length==0?0:a[Math.min(a.length-1,(int)Math.ceil(p*a.length)-1)];}
 private static boolean causedByTimeout(Throwable e){for(Throwable x=e;x!=null;x=x.getCause())if(x instanceof java.util.concurrent.TimeoutException||x.getClass().getSimpleName().contains("InvocationTimeout"))return true;return false;}
}
