package com.apexstore.nodo2;

import com.apexstore.contratos.*;
import com.apexstore.ice.ConversorIce;
import com.apexstore.ice.pagos.EstrategiaPagoPrx;
import com.zeroc.Ice.Communicator;
import java.util.concurrent.*;

public final class ProxyEstrategiaIce implements IEstrategiaPago {
 private final MedioPago medio; private final EstrategiaPagoPrx proxy;
 public ProxyEstrategiaIce(Communicator communicator,MedioPago medio,String host,int port,int timeoutMs){
  this.medio=medio; String name="estrategia/"+medio.name().toLowerCase(java.util.Locale.ROOT)+":tcp -h "+host+" -p "+port;
  this.proxy=EstrategiaPagoPrx.uncheckedCast(communicator.stringToProxy(name)).ice_invocationTimeout(timeoutMs);
 }
 @Override public RespuestaPago iniciarPago(SolicitudPago solicitud){
  try{return ConversorIce.desdeSlice(proxy.iniciarPagoAsync(ConversorIce.aSlice(solicitud)).get());}
  catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Invocación Ice interrumpida",e);}
  catch(ExecutionException e){throw new IllegalStateException("Fallo de invocación Ice",e.getCause());}
 }
 @Override public EstadoPago consultarEstado(String clave){
  try{return ConversorIce.desdeSlice(proxy.consultarEstadoAsync(clave).get());}
  catch(Exception e){throw new IllegalStateException("No se pudo consultar simulador",e);}
 }
 @Override public boolean soporta(MedioPago m){return medio==m;}
}
