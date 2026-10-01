package com.apexstore.nodo2;
import com.apexstore.contratos.MedioPago;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.*;
public final class PoliticasResiliencia {
 private final Map<MedioPago,Semaphore> bulkheads=new EnumMap<>(MedioPago.class);private final Map<MedioPago,AtomicInteger> failures=new EnumMap<>(MedioPago.class);private final Map<MedioPago,Long> openUntil=new EnumMap<>(MedioPago.class);private final Map<MedioPago,Boolean> probe=new EnumMap<>(MedioPago.class);
 public PoliticasResiliencia(){for(var m:MedioPago.values()){bulkheads.put(m,new Semaphore(m==MedioPago.PSE?16:64));failures.put(m,new AtomicInteger());openUntil.put(m,0L);probe.put(m,false);}}
 public <T>T ejecutar(MedioPago medio,Callable<T> call)throws Exception{
  Semaphore s=bulkheads.get(medio);if(s==null||!s.tryAcquire())throw new RejectedExecutionException("Capacidad ocupada; pruebe otro medio");boolean trial=false;
  try{synchronized(openUntil){long now=System.currentTimeMillis();if(openUntil.get(medio)>now)throw new RejectedExecutionException("Circuit breaker abierto; pruebe otro medio");if(failures.get(medio).get()>=3){if(probe.get(medio))throw new RejectedExecutionException("Circuit breaker semiabierto; espere o pruebe otro medio");probe.put(medio,true);trial=true;}}
   try{T result=null;for(int attempt=0;attempt<3;attempt++){try{result=call.call();break;}catch(Exception e){if(attempt==2||!connectionFailure(e))throw e;Thread.sleep(40L*(attempt+1)+ThreadLocalRandom.current().nextLong(40));}}failures.get(medio).set(0);synchronized(openUntil){openUntil.put(medio,0L);}return result;
   }catch(Exception e){if(failures.get(medio).incrementAndGet()>=3)synchronized(openUntil){openUntil.put(medio,System.currentTimeMillis()+10000);}throw e;}
  }finally{if(trial)synchronized(openUntil){probe.put(medio,false);}s.release();}
 }
 public Map<String,String> estado(){Map<String,String> out=new HashMap<>();synchronized(openUntil){openUntil.forEach((m,t)->out.put(m.name(),t>System.currentTimeMillis()?"ABIERTO":probe.get(m)?"SEMIABIERTO":"CERRADO"));}return out;}
 private static boolean connectionFailure(Throwable e){for(Throwable x=e;x!=null;x=x.getCause()){String n=x.getClass().getSimpleName();if(n.equals("ConnectFailedException")||n.equals("ConnectTimeoutException")||n.equals("ConnectionRefusedException"))return true;}return false;}
}
