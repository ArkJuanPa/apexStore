package com.apexstore.nodo3;
import com.apexstore.contratos.*;
import com.apexstore.ice.pagos.ConfigSimulacion;
interface IAdaptadorPasarela {
 MedioPago medio();
 RespuestaPago traducir(SolicitudPago solicitud);
 long demoraMs(String token,ConfigSimulacion config);
}
