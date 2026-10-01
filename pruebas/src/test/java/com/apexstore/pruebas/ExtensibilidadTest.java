package com.apexstore.pruebas;
import com.apexstore.contratos.*;
import com.apexstore.nodo2.RegistroEstrategias;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class ExtensibilidadTest {
 @Test void estrategiaCuatroSeRegistraSinCambiarContextoCheckoutNiRepositorio(){
  RegistroEstrategias registry=new RegistroEstrategias();IEstrategiaPago extra=new IEstrategiaPago(){
   public RespuestaPago iniciarPago(SolicitudPago s){return new RespuestaPago("SIM-WALLET",EstadoPago.PENDIENTE,Map.of("estado","simulado"));}
   public EstadoPago consultarEstado(String k){return EstadoPago.PENDIENTE;}
   public boolean soporta(MedioPago m){return m==MedioPago.BILLETERA_DIGITAL;}
  };
  registry.registrar(extra);assertSame(extra,registry.resolver(MedioPago.BILLETERA_DIGITAL));
 }
}
