package com.apexstore.nodo2;
import com.apexstore.contratos.*;
import com.zeroc.Ice.Communicator;
import java.util.*;
public final class RegistroEstrategias {
 private final Map<MedioPago,IEstrategiaPago> estrategias=new java.util.concurrent.ConcurrentHashMap<>();
 public RegistroEstrategias() { }
 public RegistroEstrategias(Communicator c,String host,int port){for(MedioPago medio:MedioPago.values())if(medio!=MedioPago.BILLETERA_DIGITAL)estrategias.put(medio,new ProxyEstrategiaIce(c,medio,host,port,500));}
 public IEstrategiaPago resolver(MedioPago medio){var e=estrategias.get(medio);if(e==null)throw new IllegalArgumentException("Medio no disponible");return e;}
 public void registrar(IEstrategiaPago estrategia){for(MedioPago medio:MedioPago.values())if(estrategia.soporta(medio))estrategias.put(medio,estrategia);}
}
