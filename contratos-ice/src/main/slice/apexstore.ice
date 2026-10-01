[["java:package:com.apexstore.ice"]]
module pagos {
 enum MedioPago { STRIPE, PSE, CRIPTO, BILLETERADIGITAL };
 enum EstadoPago { PENDIENTE, CONFIRMADA, FALLIDA, EXPIRADA, REEMBOLSOPENDIENTE, REEMBOLSADA };
 enum ModoSimulacion { NORMAL, LENTO, CAIDO, ERRORESINTERMITENTES, CALLBACKDUPLICADO, CALLBACKPERDIDO, RECHAZO };
 struct Dinero { long valorMenor; string moneda; };
 dictionary<string, string> Instrucciones;
 struct SolicitudPago { string idOrden; string claveIdempotencia; Dinero monto; MedioPago medio; string tokenPago; };
 struct RespuestaPago { string idTransaccionExterna; EstadoPago estado; Instrucciones instrucciones; };
 struct ResultadoPago { string idEvento; string idTransaccionExterna; string claveIdempotencia; EstadoPago estado; long ocurridoEnEpochMs; };
 struct ConfigSimulacion { ModoSimulacion modo; int latenciaMs; double probFallo; int confirmacionesRequeridas; };
 exception PasarelaNoDisponible { string motivo; };
 exception SolicitudInvalida { string motivo; };
 interface EstrategiaPago {
  ["amd"] RespuestaPago iniciarPago(SolicitudPago s) throws PasarelaNoDisponible, SolicitudInvalida;
  ["amd"] idempotent EstadoPago consultarEstado(string claveIdempotencia) throws PasarelaNoDisponible;
  idempotent bool soporta(MedioPago m);
 };
 interface ReceptorResultados { ["amd"] void notificarResultadoPago(ResultadoPago r, string firma, long marcaTiempoEpochMs); };
 interface PanelSimulacion { void configurar(MedioPago m, ConfigSimulacion c); idempotent ConfigSimulacion obtener(MedioPago m); };
};
