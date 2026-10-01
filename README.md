# ApexStore

Prototipo local Java 21 de checkout con cuatro nodos de ejecución. Stripe, PSE y Cripto son simuladores internos; no se conecta ningún procesador real y el navegador solo envía tokens de escenario opacos. La cifra de 8,000 req/s es referencia de diseño, no una capacidad medida.

## Requisitos

- JDK 21 (Gradle usa toolchain 21) y Gradle Wrapper incluido.
- PostgreSQL 14+ si usa base externa.
- Docker Desktop activo para Compose y Testcontainers.

## Preparar la base de datos

1. Copie `.env.example` como `.env` en la raíz. `.env` está ignorado por Git y excluido del contexto de imagen.
2. Para PostgreSQL propio, complete `DB_HOST`, `DB_PORT`, `DB_NOMBRE`, `DB_USUARIO`, `DB_PASSWORD`, `DB_ESQUEMA` y elija `DB_SSLMODE`. El usuario debe poder crear el esquema; si `DB_CREAR_BASE_SI_NO_EXISTE=true`, también necesita CREATEDB cuando la base todavía no existe.
3. Antes de arrancar puede comprobar conexión, versión y privilegios sin crear o modificar objetos:

   ```powershell
   .\gradlew.bat :nodo2-backend:run --args="--verificar-db"
   ```

   En Linux/macOS use `./gradlew` con el mismo argumento. El comando no imprime la contraseña.
4. En modo externo arranque el sistema con `docker compose up --build`. Nodo 2 tiene salida de red para alcanzar la base indicada; Nodo 3 no comparte esa ruta.

Para PostgreSQL local en contenedor, en `.env` ponga `DB_MODO=contenedor` y `DB_HOST=nodo4`, y ejecute:

```powershell
docker compose -f docker-compose.yml -f docker-compose.db-local.yml up --build
```

El PostgreSQL local usa volumen y no publica puertos. Tanto `red_datos` como `red_pagos` son internas. En las dos modalidades, la única publicación al host es `http://localhost:8080` desde Nodo 1.

El backend también puede ejecutarse sin Docker, con el mismo `.env`, iniciando primero Nodo 3 y luego Nodo 2:

```powershell
Start-Process .\gradlew.bat -ArgumentList ':nodo3-pasarelas:run' -NoNewWindow
 .\gradlew.bat :nodo2-backend:run
```

La configuración de Ice activa está empaquetada en `config.nodo2` y `config.nodo3`; variables como `ICE_NODO3_HOST` y `ICE_CALLBACK_PORT` la sobrescriben.

## Prueba manual guiada

Abre `http://localhost:8080`; la página muestra siempre el aviso **“Entorno de simulación: no se realizan cobros reales”**.

1. Agrega uno o más artículos y crea una orden.
2. Selecciona Stripe, PSE y Cripto por separado. Usa `tok_sim_ok`; espera el evento `PagoActualizado`. Las direcciones Cripto comienzan por `SIM-BTC-`.
3. Prueba `tok_sim_rechazado` y `tok_sim_fondos` para ver el resultado de rechazo.
4. Pulsa “Repetir pago con la misma clave”: debe conservar una sola transacción. Si cambias el medio o token antes del reintento, el servidor devuelve 409 por cuerpo distinto.
5. Con `MODO_DESARROLLO=true`, selecciona PSE y modo `LENTO`. La respuesta HTTP se acepta antes del callback, que llega hasta 15 s después; Stripe y Cripto usan cupos separados.
6. Selecciona `CAIDO` para PSE. Después de tres fallos se abre el breaker de PSE y sus respuestas devuelven 503 con alternativa; los otros medios mantienen sus estrategias aisladas.
7. Selecciona `CALLBACK_DUPLICADO` para probar que la repetición del mismo idEvento no duplica el efecto. `CALLBACK_PERDIDO` deja que el reconciliador recupere el estado.

El área de registro muestra solicitudes HTTP/eventos. El estado se actualiza por SSE y con consulta cada 2 s.

## Pruebas y carga

```powershell
.\gradlew.bat build
.\gradlew.bat :pruebas:test
```

La compilación ejecuta `verificarDependencias`, `verificarArquitectura` y ArchUnit. Las pruebas PostgreSQL usan Testcontainers y no leen `.env`. Docker debe estar activo para ejecutarlas; sin Docker, Testcontainers las omite e indica que no se validó la base real.

Driver de carga concurrente con `HttpClient` y virtual threads:

```powershell
.\gradlew.bat :pruebas:carga --args="http://localhost:8081/api 20 PSE tok_sim_ok"
```

Argumentos: URL API, número de solicitudes, medio y token. El resultado informa tasa observada y P50/P95/P99 de `POST .../pago`, además de JDK, SO, arquitectura y CPU. También puede consultar el histograma en `/api/dev/metricas`.

**Medición en este entorno:** no se midió carga. Docker Desktop no tenía daemon activo, así que no se pudo levantar la plataforma completa ni ejecutar Testcontainers o la carga contra servicios. No se presenta una tasa ni percentiles inventados. Repite la carga en la máquina objetivo y registra su CPU/RAM junto al resultado antes de comparar con 8,000 req/s.

La suite automatizada ejecutable en esta revisión cubre reglas de arquitectura/dependencias, validación de esquema y migraciones, registro de una estrategia adicional, y dos casos PostgreSQL con Testcontainers (semilla/migración idempotente y recuperación de la respuesta en un reintento idempotente). En el entorno revisado, Gradle terminó correctamente: 5 pruebas pasaron y las 2 pruebas Testcontainers fueron omitidas al no estar disponible Docker. Los flujos de HTTP/callback, concurrencia de stock, firma, fallos Ice y los escenarios de carga RAS-01/RAS-02 aún no están cubiertos por pruebas automatizadas; no se consideran verificados.

## Arquitectura y límites

- Nodo 1: HTML/CSS/JS sin frameworks, detrás de Nginx.
- Nodo 2: `HttpServer`, contexto Strategy, bulkheads, circuit breaker, repositorio JDBC único, callbacks firmados, reconciliador, SSE y métricas.
- Nodo 3: servants Ice, adapters Stripe/PSE/Cripto y simuladores locales. No incluye controlador JDBC.
- Nodo 4: PostgreSQL solo en el overlay `docker-compose.db-local.yml`; Flyway y semilla son responsabilidad de Nodo 2.
- Nodo 2 y Nodo 3 se comunican solo con Ice en `red_pagos`; reintentos de transporte Ice están desactivados y los reintentos controlados reutilizan la clave de idempotencia.
- El servidor HTTP usa hasta 128 hilos virtuales con cola de 512; los pools Ice usan 8/32 hilos. Una respuesta del simulador llega en 100–200 ms y el resultado tarda aparte; PSE a 15 s no retiene el hilo hasta su callback.
- Las migraciones solo crean objetos. Un cambio posterior debe añadirse como nueva migración versionada.

Las decisiones, supuestos y diferencias del prototipo están en [docs/DECISIONES.md](docs/DECISIONES.md).
