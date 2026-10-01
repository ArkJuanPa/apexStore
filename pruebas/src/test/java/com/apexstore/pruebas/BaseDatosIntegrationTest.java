package com.apexstore.pruebas;

import com.apexstore.nodo2.*;
import com.apexstore.contratos.*;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;

@Testcontainers(disabledWithoutDocker=true)
class BaseDatosIntegrationTest {
 @Container static final PostgreSQLContainer<?> pg=new PostgreSQLContainer<>("postgres:16-alpine");
 @Test void inicializadorYSemillaSonIdempotentes() throws Exception {
  var cfg=new ConfiguracionBaseDatos("contenedor",pg.getHost(),pg.getFirstMappedPort(),pg.getDatabaseName(),pg.getUsername(),pg.getPassword(),"apex_test","disable",5,false,true,true,20);
  try(var first=InicializadorBaseDatos.inicializar(cfg);var second=InicializadorBaseDatos.inicializar(cfg)){
   try(var c=second.getConnection();var p=c.prepareStatement("SELECT count(*) FROM productos");var r=p.executeQuery()){assertTrue(r.next());assertEquals(3,r.getInt(1));}
  }
 }

 @Test void reintentoIdempotenteRecuperaLaRespuestaOriginal() throws Exception {
  var cfg=new ConfiguracionBaseDatos("contenedor",pg.getHost(),pg.getFirstMappedPort(),pg.getDatabaseName(),pg.getUsername(),pg.getPassword(),"apex_retry","disable",5,false,true,true,20);
  try(var ds=InicializadorBaseDatos.inicializar(cfg)) {
   var repo=new RepositorioTransacciones(ds);
   var order=repo.crearOrden(List.of(new RepositorioTransacciones.Item(1,1)));
   var request=new SolicitudPago(String.valueOf(order.get("id")),"misma-clave",new Dinero(((Number)order.get("totalMenor")).longValue(),"COP"),MedioPago.CRIPTO,"tok_sim_ok");
   var first=repo.registrarPendiente(request);
   var original=new RespuestaPago("SIM-BTC-prueba",EstadoPago.PENDIENTE,Map.of("direccion","SIM-BTC-prueba","unidad","satoshis"));
   repo.guardarRespuesta("misma-clave",original);
   var retry=repo.registrarPendiente(request);
   assertFalse(retry.nueva());
   assertEquals(original.idTransaccionExterna(),retry.transaccionExterna());
   assertEquals(original.instrucciones(),retry.instrucciones());
   assertEquals(first.id(),retry.id());
  }
 }
}
