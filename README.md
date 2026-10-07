# ApexStore

Prototipo de checkout con cuatro nodos que se comunican por ZeroC ICE 3.7.10. Stripe, PSE y Cripto son simuladores internos: no se conecta ningún procesador real. El cliente es una consola (Nodo 1). La cifra de 8,000 req/s es referencia de diseño; la medición de RAS-02 se documenta en el informe.

## Requisitos

- JDK 21. Gradle Wrapper incluido (no hace falta instalar Gradle).
- ZeroC Ice 3.7.10. Verifique con `slice2java --version`; debe mostrar `3.7.10`. En Windows, instale el MSI. En Linux, instálelo según la documentación de ZeroC para su distribución.
- Docker (Docker Desktop en Windows, Docker Engine en Linux), solo para PostgreSQL.

## Configuración

1. Copie `.env.example` como `.env` en la raíz. `.env` no se versiona.
   - Windows: `copy .env.example .env`
   - Linux o macOS: `cp .env.example .env`
2. Complete `DB_PASSWORD` y, si lo necesita, `DB_PORT` (`5433` por defecto, para no chocar con un PostgreSQL nativo en el `5432`).
3. Cada nodo lee su configuración de la raíz, fuera del jar:

| Archivo | Nodo | Qué contiene |
|---|---|---|
| `config.nodo4` | 4 (persistencia) | `Repositorio.Endpoints`: puerto 10004 |
| `config.nodo3` | 3 (pasarelas) | `Pasarelas.Endpoints` (10000), `Receptor.Proxy` (dónde está Nodo 2), aceptación simulada |
| `config.nodo2` | 2 (backend) | `Checkout.Endpoints` (10003), `Callbacks.Endpoints` (10001), `Repositorio.Proxy`, `Estrategia.*.Proxy`, ventana del breaker |
| `config.nodo1` | 1 (consola) | `Checkout.Proxy`: dónde está Nodo 2 |

## Base de datos

```
docker compose up -d
```

Igual en Windows y Linux. Levanta PostgreSQL 16 en el puerto `DB_PORT` (5433) con el volumen `apex_pgdata`. Al arrancar, Nodo 4 crea el esquema y la semilla de productos con `schema.sql`; no hay migraciones.

Para comprobar la conexión sin modificar nada:
- Windows: `.\gradlew.bat :nodo4-db:run --args="--verificar-db"`
- Linux o macOS: `./gradlew :nodo4-db:run --args="--verificar-db"`

## Arranque

Cada nodo tiene un script en la raíz: `.bat` para Windows y `.sh` para Linux, macOS o Git Bash. Cada script compila su módulo, se sitúa en la raíz y arranca el nodo con su `config`. Abra una terminal por nodo y respete el orden **4 → 3 → 2 → 1**.

Windows:

```
.\run-nodo4.bat     # terminal 1
.\run-nodo3.bat     # terminal 2
.\run-nodo2.bat     # terminal 3
.\run-nodo1.bat     # terminal 4: menú de consola
```

Linux o macOS:

```
./run-nodo4.sh      # terminal 1
./run-nodo3.sh      # terminal 2
./run-nodo2.sh      # terminal 3
./run-nodo1.sh      # terminal 4: menú de consola
```

Si un nodo no está arriba, la consola muestra el error de conexión.

El menú del nodo 1: `1` catálogo, `2` crear orden, `3` pagar (medio y escenario), `4` repetir el último pago con la misma clave, `5` consultar orden, `0` salir (espera hasta 20 s los resultados pendientes). Las notificaciones de pago se imprimen como `[PUSH]` en cuanto llegan.

Escenarios por token: `tok_sim_ok`, `tok_sim_rechazado`, `tok_sim_fondos`, `tok_sim_lento` (15 s en el callback) y `tok_sim_caido`. Tres `tok_sim_caido` seguidos abren el breaker del medio durante `Resiliencia.BreakerAbiertoSeg` segundos (30 en `config.nodo2`). Con el breaker abierto, el menú muestra el motivo y la sugerencia de `MedioNoDisponible`. Repetir con la misma clave y el mismo medio y escenario devuelve la misma respuesta; cambiar el medio o el escenario lanza `ConflictoIdempotencia`.

## Demo completa (sin escribir a mano)

Con los cuatro nodos arriba, la entrada del menú está en `demo/demo-entrada.txt`.

- Windows: `.\run-nodo1.bat < demo\demo-entrada.txt`
- Linux o macOS: `./run-nodo1.sh < demo/demo-entrada.txt`

La demo paga con los tres medios, abre el breaker de PSE con tres `tok_sim_caido`, muestra que Stripe sigue funcionando, repite un pago con la misma clave y muestra `ConflictoIdempotencia` al cambiar el medio.

Gradle no reenvía la entrada estándar a la aplicación; por eso el script del nodo 1 lanza `java` directo y no `gradlew run`.

## Varias PCs

Cada nodo puede correr en una PC distinta. Solo hay que editar la IP en la configuración de quien depende de otro nodo. Los adaptadores escuchan en `0.0.0.0`, así que sus endpoints no cambian.

| Nodo | PC de ejemplo | Archivo y clave | Valor |
|---|---|---|---|
| 3 (pasarelas) | IP-3 | `config.nodo3`: `Receptor.Proxy` | `receptor:tcp -h <IP-2> -p 10001` |
| 2 (backend) | IP-2 | `config.nodo2`: `Repositorio.Proxy` | `repositorio:tcp -h <IP-4> -p 10004` |
| 2 (backend) | IP-2 | `config.nodo2`: `Estrategia.STRIPE.Proxy`, `Estrategia.PSE.Proxy`, `Estrategia.CRIPTO.Proxy` | `estrategia/<medio>:tcp -h <IP-3> -p 10000` |
| 1 (consola) | IP-1 | `config.nodo1`: `Checkout.Proxy` | `servicioCheckout:tcp -h <IP-2> -p 10003` |
| 4 (persistencia) | IP-4 | nada | |

El `.env` solo lo usa Nodo 4 (la base de datos). Si PostgreSQL corre en otra PC, cambie `DB_HOST` en ese `.env`.

**Firewall.** En cada PC que aloja un nodo, abra los puertos de entrada TCP 10000, 10001, 10003 y 10004. Basta con los puertos del nodo que aloja cada PC.

- Windows (PowerShell como administrador):

  ```powershell
  New-NetFirewallRule -DisplayName "ApexStore ICE" -Direction Inbound -Protocol TCP -LocalPort 10000,10001,10003,10004 -Action Allow
  ```

  Para probar la conexión antes de la demo: `Test-NetConnection <IP> -Port 10003`.

- Linux con ufw:

  ```
  sudo ufw allow 10000,10001,10003,10004/tcp
  ```

  Para probar la conexión: `nc -zv <IP> 10003`.

## Pruebas

- Windows: `.\gradlew.bat build` y `.\gradlew.bat :pruebas:test`
- Linux o macOS: `./gradlew build` y `./gradlew :pruebas:test`

La compilación ejecuta cuatro grupos de pruebas: `ArquitecturaTest` (Nodo 3 no accede a la base y el contexto de pagos no depende de ICE), `ExtensibilidadTest` (una estrategia nueva se registra sin tocar el contexto), `PoliticasResilienciaTest` (el breaker abre, espera y cierra) y `BaseDatosIntegrationTest` (idempotencia con PostgreSQL real). Esta última usa Testcontainers y necesita Docker activo; si Docker no está, se omite.

La suite automatizada no cubre la carga ni la concurrencia de stock.

## Medición de RAS-02 por tramos

Nodo 2 escribe una línea `RAS02,orden,medio,repoMs,estrategiaMs,totalMs` por pago en su salida. El medidor necesita ese log en un archivo. Haga 10 pagos de calentamiento (no se cuentan) y 50 medidas, y calcula P50 y P95 por tramo. Por eso el log tiene que ser nuevo y tener al menos 60 líneas `RAS02`.

1. Con Nodo 4 y Nodo 3 arriba, arranque Nodo 2 enviando su salida a un archivo:
   - Windows (cmd): `.\run-nodo2.bat > nodo2.log 2>&1`
   - Linux o macOS: `./run-nodo2.sh > nodo2.log 2>&1`
2. En otra terminal, ejecute el medidor:
   - Windows: `.\gradlew.bat :pruebas:medirRas02 --args='"servicioCheckout:tcp -h localhost -p 10003" nodo2.log medicion-ras02.csv'`
   - Linux o macOS: `./gradlew :pruebas:medirRas02 --args='"servicioCheckout:tcp -h localhost -p 10003" nodo2.log medicion-ras02.csv'`
3. El resultado queda en `medicion-ras02.csv` (P50 y P95 por tramo) y en `medicion-ras02-crudo.csv` (las 50 medidas).

## Arquitectura

- **Nodo 1**: `nodo1-consola`, la MobileApp del diagrama. Consume `IGestionCompras`. Una WebApp consumiría la misma interfaz; la separación interfaz/implementación permite agregarla sin tocar el backend.
- **Nodo 2**: `ServidorBackend` arranca el componente ServicioCheckout (`ServicioCheckout`, interfaz `IGestionCompras`), el contexto Strategy (`ProcesadorPagosContexto`), bulkheads y circuit breaker (`PoliticasResiliencia`), el receptor de callbacks y el reconciliador. Accede a la persistencia solo por ICE (`RepositorioPagosIce`).
- **Nodo 3**: `EstrategiaStripe`, `EstrategiaPSE` y `EstrategiaCripto` sobre `EstrategiaSimuladaBase`. No tiene acceso a la base de datos.
- **Nodo 4**: servant `IRepositorioPagos` por ICE. Es el único que accede a PostgreSQL. Cada operación es una transacción local.
- Las decisiones, supuestos y la bitácora por fase son documentos del informe y no se versionan en este repositorio.
