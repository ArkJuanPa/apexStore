[["java:package:com.apexstore.ice"]]
module pagos {
 struct Dinero { long valorMenor; string moneda; };
 dictionary<string, string> Instrucciones;
 struct Producto { long id; string nombre; Dinero precio; int stock; };
 sequence<Producto> ProductoSeq;
 struct Item { long productoId; int cantidad; };
 sequence<Item> ItemSeq;
 struct Orden { string id; string estado; Dinero total; string estadoPago; string medio; string referencia; };
 struct SolicitudPago { string idOrden; string claveIdempotencia; Dinero monto; string medio; string tokenPago; };
 struct RespuestaPago { string idTransaccionExterna; string estado; Instrucciones instrucciones; };
 struct ResultadoPago { string idEvento; string idTransaccionExterna; string claveIdempotencia; string estado; long ocurridoEnEpochMs; };

 exception SolicitudInvalida { string motivo; };
 exception MedioNoDisponible { string motivo; string sugerencia; };
 exception OrdenNoEncontrada { string idOrden; };
 exception ConflictoIdempotencia { string clave; };

 // Nodo 2 -> Nodo 3. El medio es string: agregar uno no obliga a tocar el contrato (RAS-04).
 interface IEstrategiaPago {
  ["amd"] RespuestaPago iniciarPago(SolicitudPago s) throws MedioNoDisponible, SolicitudInvalida;
  ["amd"] idempotent string consultarEstado(string claveIdempotencia);
 };

 // Nodo 3 -> Nodo 2. Validación: la clave de idempotencia existe y el idEvento no se procesa dos veces.
 interface IReceptorResultados {
  ["amd"] void notificarResultadoPago(ResultadoPago r) throws SolicitudInvalida;
 };

 struct PendienteTx { string claveIdempotencia; string medio; };
 sequence<PendienteTx> PendienteSeq;

 // Nodo 2 -> Nodo 4 (persistencia). Cada operación es una transacción completa dentro de Nodo 4 (RAS-03).
 interface IRepositorioPagos {
  idempotent ProductoSeq productos();
  Orden crearOrden(ItemSeq items) throws SolicitudInvalida;
  idempotent Orden obtenerOrden(string idOrden) throws OrdenNoEncontrada;
  RespuestaPago registrarPendiente(SolicitudPago s, out bool nueva) throws ConflictoIdempotencia, SolicitudInvalida;
  void guardarRespuesta(string clave, RespuestaPago r);
  bool aplicarResultado(ResultadoPago r);
  void marcarFallida(string clave, string origen);
  idempotent PendienteSeq pendientes();
  int expirarVencidas();
  idempotent string ordenDeClave(string clave);
 };

 // Navegador -> Nodo 2 (push por la misma conexión).
 interface IObservadorPago { void pagoActualizado(Orden orden); };

 // Navegador -> Nodo 2.
 interface IGestionCompras {
  idempotent ProductoSeq listarProductos();
  Orden crearOrden(ItemSeq items) throws SolicitudInvalida;
  ["amd"] RespuestaPago pagarOrden(string idOrden, string medio, string tokenPago, string claveIdempotencia)
   throws SolicitudInvalida, MedioNoDisponible, OrdenNoEncontrada, ConflictoIdempotencia;
  idempotent Orden consultarOrden(string idOrden) throws OrdenNoEncontrada;
  void suscribir(string idOrden, IObservadorPago* observador) throws OrdenNoEncontrada;
 };
};
