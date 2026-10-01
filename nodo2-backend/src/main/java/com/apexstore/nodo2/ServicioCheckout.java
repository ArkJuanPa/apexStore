package com.apexstore.nodo2;

import com.apexstore.contratos.*;
import com.apexstore.ice.pagos.PanelSimulacionPrx;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.InitializationData;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.Util;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;

public final class ServicioCheckout {
 private static final ObjectMapper JSON=new ObjectMapper();
 private ServicioCheckout(){}
 public static void iniciar(ConfiguracionBaseDatos db) throws Exception {
  var ds=InicializadorBaseDatos.inicializar(db);
  com.zeroc.Ice.Properties properties=Util.createProperties();loadIceProperties(properties,"/config.nodo2");
  InitializationData init=new InitializationData();init.properties=properties;Communicator communicator=Util.initialize(init);
  RepositorioTransacciones repo=new RepositorioTransacciones(ds);RegistroEstrategias registry=new RegistroEstrategias(communicator,Entorno.valor("ICE_NODO3_HOST",properties.getProperty("Ice.Nodo3.Host")),Integer.parseInt(Entorno.valor("ICE_NODO3_PORT",properties.getProperty("Ice.Nodo3.Port"))));
  ProcesadorPagosContexto context=new ProcesadorPagosContexto(registry,repo);String secret=Entorno.valor("SECRETO_FIRMA_SIMULACION","desarrollo-apexstore-no-usar-en-produccion");
  ReconciliadorPagos reconciliador=new ReconciliadorPagos(repo,registry,context,Integer.parseInt(Entorno.valor("RECONCILIAR_CADA_SEG","10")));
  ObjectAdapter callbacks=communicator.createObjectAdapterWithEndpoints("Callbacks","tcp -h 0.0.0.0 -p "+Entorno.valor("ICE_CALLBACK_PORT",properties.getProperty("Ice.Callback.Port")));callbacks.add(new ReceptorCallbacks(repo,context,secret),Util.stringToIdentity("receptor"));callbacks.activate();
  HttpServer server=HttpServer.create(new InetSocketAddress("0.0.0.0",Integer.parseInt(Entorno.valor("HTTP_PORT","8081"))),128);
  int httpThreads=Integer.parseInt(Entorno.valor("HTTP_HILOS_MAX","128"));
  server.setExecutor(new ThreadPoolExecutor(httpThreads,httpThreads,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(512),Thread.ofVirtual().name("http-apex-",0).factory(),new ThreadPoolExecutor.AbortPolicy()));server.createContext("/api/",x->route(x,repo,context,communicator,ds,db));server.start();
  Runtime.getRuntime().addShutdownHook(new Thread(()->{server.stop(1);reconciliador.close();communicator.destroy();ds.close();}));
  System.out.println("Nodo 2 HTTP iniciado en 8081; PostgreSQL="+db.host()+":"+db.puerto()+"/"+db.base());
 }
 private static void route(HttpExchange x,RepositorioTransacciones repo,ProcesadorPagosContexto context,Communicator comm,com.zaxxer.hikari.HikariDataSource ds,ConfiguracionBaseDatos db)throws IOException{
  String path=x.getRequestURI().getPath();String method=x.getRequestMethod();
  try {
   if(path.equals("/api/salud")){try(var c=ds.getConnection()){send(x,200,Map.of("estado","ok","postgres","conectado","esquema",db.esquema(),"desarrollo",Boolean.parseBoolean(Entorno.valor("MODO_DESARROLLO","false"))));}return;}
   if(path.equals("/api/productos")&&method.equals("GET")){send(x,200,repo.productos());return;}
   if(path.equals("/api/ordenes")&&method.equals("POST")){Map<?,?> body=JSON.readValue(x.getRequestBody(),Map.class);List<RepositorioTransacciones.Item> items=new ArrayList<>();for(Object it:(List<?>)body.get("items")){Map<?,?> row=(Map<?,?>)it;items.add(new RepositorioTransacciones.Item(((Number)row.get("productoId")).longValue(),((Number)row.get("cantidad")).intValue()));}send(x,201,repo.crearOrden(items));return;}
   if(path.equals("/api/dev/metricas")){if(!Boolean.parseBoolean(Entorno.valor("MODO_DESARROLLO","false"))){send(x,404,Map.of("codigo","NO_DISPONIBLE","mensaje","Métricas deshabilitadas"));return;}send(x,200,context.metricas());return;}
  if(path.startsWith("/api/dev/simulacion/")&&method.equals("PUT")){if(!Boolean.parseBoolean(Entorno.valor("MODO_DESARROLLO","false"))){send(x,404,Map.of("codigo","NO_DISPONIBLE","mensaje","Panel deshabilitado"));return;}configurar(comm,path.substring(path.lastIndexOf('/')+1),JSON.readValue(x.getRequestBody(),Map.class));send(x,200,Map.of("estado","configurado"));return;}
   if(path.matches("/api/ordenes/[^/]+/pago")&&method.equals("POST")){pagar(x,path,repo,context);return;}
   if(path.matches("/api/ordenes/[^/]+/eventos")&&method.equals("GET")){sse(x,path,repo,context);return;}
   if(path.matches("/api/ordenes/[^/]+")&&method.equals("GET")){Map<String,Object> o=repo.obtenerOrden(path.substring(path.lastIndexOf('/')+1));send(x,o==null?404:200,o==null?Map.of("codigo","NO_ENCONTRADA","mensaje","Orden desconocida"):o);return;}
   send(x,404,Map.of("codigo","NO_ENCONTRADA","mensaje","Ruta desconocida"));
  }catch(IllegalArgumentException e){send(x,400,Map.of("codigo","SOLICITUD_INVALIDA","mensaje",safe(e.getMessage())));}catch(Exception e){send(x,500,Map.of("codigo","ERROR_INTERNO","mensaje",safe(e.getMessage())));}
 }
 private static void pagar(HttpExchange x,String path,RepositorioTransacciones repo,ProcesadorPagosContexto context)throws Exception{
  String key=x.getRequestHeaders().getFirst("Idempotency-Key");if(key==null||key.isBlank()){send(x,400,Map.of("codigo","FALTA_IDEMPOTENCY_KEY","mensaje","Envíe Idempotency-Key"));return;}
  String id=path.split("/")[3];Map<String,Object> order=repo.obtenerOrden(id);if(order==null){send(x,404,Map.of("codigo","NO_ENCONTRADA","mensaje","Orden desconocida"));return;}
  Map<?,?> body=JSON.readValue(x.getRequestBody(),Map.class);MedioPago medio=MedioPago.valueOf(String.valueOf(body.get("medio")));String token=String.valueOf(body.get("tokenPago"));if(!Set.of("tok_sim_ok","tok_sim_rechazado","tok_sim_fondos","tok_sim_lento").contains(token)){send(x,400,Map.of("codigo","TOKEN_SIMULADO_INVALIDO","mensaje","Use uno de los escenarios de simulación disponibles"));return;}
  SolicitudPago req=new SolicitudPago(id,key,new Dinero(((Number)order.get("totalMenor")).longValue(),String.valueOf(order.get("moneda"))),medio,token);
  try{RespuestaPago r=context.iniciarPagoOrden(req);send(x,202,Map.of("idOrden",id,"estado",r.estado().name(),"idTransaccionExterna",Objects.toString(r.idTransaccionExterna(),""),"instrucciones",r.instrucciones(),"idempotencyKey",key));}
  catch(Exception e){if(hasSqlState(e,"23505")){send(x,409,Map.of("codigo","IDEMPOTENCIA_CONFLICTO","mensaje","La clave ya se usó con un cuerpo distinto"));return;}boolean timeout=hasTimeout(e);send(x,timeout?202:503,Map.of("idOrden",id,"estado",timeout?"PENDIENTE":"FALLIDA","codigo",timeout?"RESULTADO_DESCONOCIDO":"MEDIO_NO_DISPONIBLE","mensaje",timeout?"El resultado se está verificando":"El medio no está disponible; pruebe otro medio","sugerencia","Seleccione otro medio de pago","idempotencyKey",key));}
 }
 private static void sse(HttpExchange x,String path,RepositorioTransacciones repo,ProcesadorPagosContexto context)throws Exception{
  String id=path.split("/")[3];if(repo.obtenerOrden(id)==null){send(x,404,Map.of("codigo","NO_ENCONTRADA","mensaje","Orden desconocida"));return;}
  x.getResponseHeaders().set("Content-Type","text/event-stream; charset=utf-8");x.getResponseHeaders().set("Cache-Control","no-cache");x.sendResponseHeaders(200,0);var q=context.suscribir(id);
  try(var out=x.getResponseBody()){out.write(": conectado\n\n".getBytes(StandardCharsets.UTF_8));while(!Thread.currentThread().isInterrupted()){ResultadoPago r=q.poll(20,TimeUnit.SECONDS);if(r==null)out.write(": espera\n\n".getBytes(StandardCharsets.UTF_8));else out.write(("event: PagoActualizado\ndata: "+JSON.writeValueAsString(r)+"\n\n").getBytes(StandardCharsets.UTF_8));out.flush();}}catch(InterruptedException e){Thread.currentThread().interrupt();}finally{context.cancelarSuscripcion(id,q);x.close();}
 }
 private static void configurar(Communicator c,String medio,Map<?,?> b){var prx=PanelSimulacionPrx.uncheckedCast(c.stringToProxy("panel:tcp -h "+Entorno.valor("ICE_NODO3_HOST","localhost")+" -p "+Entorno.valor("ICE_NODO3_PORT","10000")));var mode=com.apexstore.ice.pagos.ModoSimulacion.valueOf(String.valueOf(b.get("modo")).replace("_",""));int latency=((Number)(b.containsKey("latenciaMs")?b.get("latenciaMs"):0)).intValue();double probability=((Number)(b.containsKey("probFallo")?b.get("probFallo"):0)).doubleValue();if(latency<0||latency>15000||probability<0||probability>1)throw new IllegalArgumentException("Parámetros de simulación fuera de rango");var conf=new com.apexstore.ice.pagos.ConfigSimulacion(mode,latency,probability,3);prx.configurar(com.apexstore.ice.pagos.MedioPago.valueOf(medio.replace("_","")),conf);}
 private static boolean hasTimeout(Throwable e){for(Throwable x=e;x!=null;x=x.getCause())if(x instanceof java.util.concurrent.TimeoutException||x.getClass().getSimpleName().contains("InvocationTimeout"))return true;return false;}
 private static boolean hasSqlState(Throwable e,String state){for(Throwable x=e;x!=null;x=x.getCause())if(x instanceof SQLException s&&state.equals(s.getSQLState()))return true;return false;}
 private static String safe(String m){return m==null?"Solicitud no válida":m.replaceAll("(?i)(password|passwd)=([^\\s&]+)","$1=****");}
 private static void loadIceProperties(com.zeroc.Ice.Properties target,String resource)throws IOException{java.util.Properties p=new java.util.Properties();try(var in=ServicioCheckout.class.getResourceAsStream(resource)){if(in==null)throw new FileNotFoundException(resource);p.load(in);}p.forEach((k,v)->target.setProperty(k.toString(),v.toString()));}
 private static void send(HttpExchange x,int status,Object body)throws IOException{byte[] data=JSON.writeValueAsBytes(body);x.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");x.sendResponseHeaders(status,data.length);try(var out=x.getResponseBody()){out.write(data);}}
}
