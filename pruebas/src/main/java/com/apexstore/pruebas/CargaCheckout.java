package com.apexstore.pruebas;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;

public final class CargaCheckout {
 private static final ObjectMapper JSON=new ObjectMapper();
 private CargaCheckout(){}
 public static void main(String[] args)throws Exception{
  String base=args.length>0?args[0]:"http://localhost:8081/api";int count=args.length>1?Integer.parseInt(args[1]):20;String medium=args.length>2?args[2]:"STRIPE";String token=args.length>3?args[3]:"tok_sim_ok";
  var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();var products=JSON.readValue(client.send(HttpRequest.newBuilder(URI.create(base+"/productos")).GET().build(),HttpResponse.BodyHandlers.ofString()).body(),new TypeReference<List<Map<String,Object>>>(){});long productId=((Number)products.get(0).get("productoId")).longValue();
  var times=new ConcurrentLinkedQueue<Long>();var ok=new LongAdder();long begin=System.nanoTime();
  try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
   List<Future<?>> jobs=new ArrayList<>();for(int i=0;i<count;i++){int n=i;jobs.add(executor.submit(()->{try{var create=HttpRequest.newBuilder(URI.create(base+"/ordenes")).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{\"items\":[{\"productoId\":"+productId+",\"cantidad\":1}]}")).build();var created=client.send(create,HttpResponse.BodyHandlers.ofString());if(created.statusCode()!=201)return;String id=JSON.readValue(created.body(),new TypeReference<Map<String,Object>>(){}).get("id").toString();var pay=HttpRequest.newBuilder(URI.create(base+"/ordenes/"+id+"/pago")).header("Content-Type","application/json").header("Idempotency-Key","load-"+UUID.randomUUID()+"-"+n).POST(HttpRequest.BodyPublishers.ofString("{\"medio\":\""+medium+"\",\"tokenPago\":\""+token+"\"}")).build();long at=System.nanoTime();var response=client.send(pay,HttpResponse.BodyHandlers.ofString());times.add((System.nanoTime()-at)/1_000_000);if(response.statusCode()==202)ok.increment();}catch(Exception e){System.err.println("Solicitud fallida: "+e.getClass().getSimpleName());}}));}
   for(Future<?> job:jobs)job.get();
  }
  long elapsed=System.nanoTime()-begin;long[] sorted=times.stream().mapToLong(Long::longValue).sorted().toArray();double rps=sorted.length/(elapsed/1_000_000_000.0);
  System.out.printf(Locale.ROOT,"JDK=%s OS=%s/%s CPUs=%d solicitudes=%d aceptadas=%d tasa=%.2f req/s P50=%dms P95=%dms P99=%dms%n",System.getProperty("java.version"),System.getProperty("os.name"),System.getProperty("os.arch"),Runtime.getRuntime().availableProcessors(),count,ok.sum(),rps,pct(sorted,.50),pct(sorted,.95),pct(sorted,.99));
 }
 private static long pct(long[] a,double p){return a.length==0?0:a[Math.min(a.length-1,(int)Math.ceil(p*a.length)-1)];}
}
