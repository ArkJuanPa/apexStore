package com.apexstore.nodo3;

import com.apexstore.contratos.*;
import com.apexstore.ice.ConversorIce;
import com.apexstore.ice.pagos.ConfigSimulacion;
import com.apexstore.ice.pagos.MedioPago;
import com.apexstore.ice.pagos.ModoSimulacion;
import com.apexstore.ice.pagos.PanelSimulacion;
import com.apexstore.ice.pagos.ReceptorResultadosPrx;
import com.apexstore.ice.pagos.EstrategiaPago;
import com.apexstore.ice.pagos.PasarelaNoDisponible;
import com.apexstore.ice.pagos.SolicitudInvalida;
import com.zeroc.Ice.*;
import java.util.*;
import java.util.concurrent.*;

public final class ServidorPasarelas {
 private ServidorPasarelas() { }
 public static void main(String[] args) {
  com.zeroc.Ice.Properties properties=Util.createProperties(args);loadIceProperties(properties);
  InitializationData init=new InitializationData(); init.properties=properties;
  try(Communicator communicator=Util.initialize(init)) {
   ObjectAdapter adapter=communicator.createObjectAdapterWithEndpoints("Pasarelas","tcp -h 0.0.0.0 -p "+Entorno.valor("ICE_NODO3_PORT",properties.getProperty("Ice.Pasarelas.Port")));
   String host=Entorno.valor("ICE_CALLBACK_HOST",properties.getProperty("Ice.Callback.Host")); int port=Integer.parseInt(Entorno.valor("ICE_CALLBACK_PORT",properties.getProperty("Ice.Callback.Port")));
   String secret=Entorno.valor("SECRETO_FIRMA_SIMULACION","desarrollo-apexstore-no-usar-en-produccion");
   ReceptorResultadosPrx callback=ReceptorResultadosPrx.uncheckedCast(communicator.stringToProxy("receptor:tcp -h "+host+" -p "+port));
   var configs=new ConcurrentHashMap<MedioPago,ConfigSimulacion>();
   configs.put(MedioPago.STRIPE,defaultConfig()); configs.put(MedioPago.PSE,defaultConfig()); configs.put(MedioPago.CRIPTO,defaultConfig());
   for(IAdaptadorPasarela strategy:List.of(new AdaptadorStripe(),new AdaptadorPSE(),new AdaptadorCripto())){MedioPago medio=MedioPago.valueOf(strategy.medio().name());adapter.add(new Servant(medio,strategy,configs,callback,secret),Util.stringToIdentity("estrategia/"+medio.name().toLowerCase(Locale.ROOT)));}
   adapter.add(new Panel(configs),Util.stringToIdentity("panel")); adapter.activate(); communicator.waitForShutdown();
  }
 }
 private static ConfigSimulacion defaultConfig(){return new ConfigSimulacion(ModoSimulacion.NORMAL,0,0.0,3);}
 private static void loadIceProperties(com.zeroc.Ice.Properties target){java.util.Properties p=new java.util.Properties();try(var in=ServidorPasarelas.class.getResourceAsStream("/config.nodo3")){if(in==null)throw new IllegalStateException("No existe config.nodo3");p.load(in);}catch(java.io.IOException e){throw new IllegalStateException("No se pudo leer config.nodo3",e);}p.forEach((k,v)->target.setProperty(k.toString(),v.toString()));}

 private static final class Servant implements EstrategiaPago {
  private final MedioPago medio; private final IAdaptadorPasarela strategy; private final Map<MedioPago,ConfigSimulacion> configs; private final ReceptorResultadosPrx callback; private final String secret;
  private final Map<String,RespuestaPago> known=new ConcurrentHashMap<>();
  Servant(MedioPago m,IAdaptadorPasarela strategy,Map<MedioPago,ConfigSimulacion> c,ReceptorResultadosPrx r,String s){medio=m;this.strategy=strategy;configs=c;callback=r;secret=s;}
  public CompletionStage<com.apexstore.ice.pagos.RespuestaPago> iniciarPagoAsync(com.apexstore.ice.pagos.SolicitudPago s,Current current) {
   RespuestaPago prior=known.get(s.claveIdempotencia);if(prior!=null)return CompletableFuture.completedFuture(ice(prior));
   ConfigSimulacion config=configs.get(medio);
   if(config.modo==ModoSimulacion.CAIDO) return CompletableFuture.failedFuture(new PasarelaNoDisponible("Simulador no disponible"));
   if(s.medio!=medio) return CompletableFuture.failedFuture(new SolicitudInvalida("Medio no soportado"));
   if(config.modo==ModoSimulacion.ERRORESINTERMITENTES&&ThreadLocalRandom.current().nextDouble()<config.probFallo)return CompletableFuture.failedFuture(new PasarelaNoDisponible("Fallo simulado"));
   RespuestaPago r=strategy.traducir(new SolicitudPago(s.idOrden,s.claveIdempotencia,new Dinero(s.monto.valorMenor,s.monto.moneda),com.apexstore.contratos.MedioPago.valueOf(medio.name()),s.tokenPago));
   known.put(s.claveIdempotencia,r);
   long wait=strategy.demoraMs(s.tokenPago,config);if(config.latenciaMs>0)wait=Math.max(wait,config.latenciaMs);if(config.modo==ModoSimulacion.LENTO)wait=Math.max(wait,15000);
   schedule(s,r,wait,config); long responseMs=100+ThreadLocalRandom.current().nextInt(101);final RespuestaPago response=r;
   return CompletableFuture.supplyAsync(()->ice(response),CompletableFuture.delayedExecutor(responseMs,TimeUnit.MILLISECONDS));
  }
  private void schedule(com.apexstore.ice.pagos.SolicitudPago s,RespuestaPago r,long delay,ConfigSimulacion c){
   CompletableFuture.delayedExecutor(delay,TimeUnit.MILLISECONDS).execute(()->{
    long when=System.currentTimeMillis(); boolean rejected=s.tokenPago.equals("tok_sim_rechazado")||s.tokenPago.equals("tok_sim_fondos")||c.modo==ModoSimulacion.RECHAZO;EstadoPago state=rejected?EstadoPago.FALLIDA:EstadoPago.CONFIRMADA;
    known.put(s.claveIdempotencia,new RespuestaPago(r.idTransaccionExterna(),state,r.instrucciones()));
    if(c.modo==ModoSimulacion.CALLBACKPERDIDO)return;
    var result=new ResultadoPago(UUID.randomUUID().toString(),r.idTransaccionExterna(),s.claveIdempotencia,state,when);
    long marked=System.currentTimeMillis(); var wire=ConversorIce.aSlice(result); String sig=Firmas.firmar(result,marked,secret);
    int retries=Integer.parseInt(Entorno.valor("CALLBACK_REINTENTOS","3"));send(wire,sig,marked,retries); if(c.modo==ModoSimulacion.CALLBACKDUPLICADO) send(wire,sig,marked,retries);
   });
  }
  private void send(com.apexstore.ice.pagos.ResultadoPago r,String sig,long ts,int left){ callback.notificarResultadoPagoAsync(r,sig,ts).whenComplete((v,e)->{if(e!=null&&left>1)CompletableFuture.delayedExecutor((4-left)*250L,TimeUnit.MILLISECONDS).execute(()->send(r,sig,ts,left-1));}); }
  private static com.apexstore.ice.pagos.RespuestaPago ice(RespuestaPago r){return new com.apexstore.ice.pagos.RespuestaPago(r.idTransaccionExterna(),ConversorIce.aSlice(r.estado()),r.instrucciones());}
  public CompletionStage<com.apexstore.ice.pagos.EstadoPago> consultarEstadoAsync(String key,Current current){var r=known.get(key);return CompletableFuture.completedFuture(r==null?com.apexstore.ice.pagos.EstadoPago.FALLIDA:ConversorIce.aSlice(r.estado()));}
  public boolean soporta(com.apexstore.ice.pagos.MedioPago m,Current current){return m.name().equals(medio.name());}
 }

 private static final class Panel implements PanelSimulacion {
  private final Map<MedioPago,ConfigSimulacion> configs; Panel(Map<MedioPago,ConfigSimulacion> c){configs=c;}
  public void configurar(MedioPago m,ConfigSimulacion c,Current current){configs.put(m,c);}
  public ConfigSimulacion obtener(MedioPago m,Current current){return configs.get(m);}
 }
}
