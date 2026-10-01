# Decisiones de arquitectura

## Supuestos y restricciones

- Este directorio estaba vacío y no contenía Git; se inicializó un repositorio local para materializar los commits por fase.
- El Gradle Wrapper es 8.12.1 y el toolchain es Java 21. Gradle aprovisionó Temurin 21.0.8; el JVM que inicia Gradle puede ser otro JDK compatible.
- Se fija ZeroC Ice 3.7.10 y el plugin Gradle Slice 1.5.2. Ice 3.7.10 mantiene la línea solicitada 3.7.x; el plugin compila los contratos Slice.
- Slice 3.7 no admite `_` en identificadores enum. Los nombres de wire `REEMBOLSOPENDIENTE`, `ERRORESINTERMITENTES`, `CALLBACKDUPLICADO` y `CALLBACKPERDIDO` se convierten a los nombres Java del dominio en `ConversorIce`; es una diferencia sintáctica del contrato generado.
- Los modos Stripe, PSE y Cripto son nombres funcionales simulados. No se usa un SDK de proveedor ni se envía información financiera real.
- El .env se lee localmente sin incorporar una dependencia adicional. Las variables de entorno del proceso prevalecen sobre el archivo.
- DB_NOMBRE y DB_ESQUEMA solo aceptan identificadores ASCII minúsculos y guion bajo antes de formar SQL. La base no se crea en modo de verificación.
- Producción debe ubicar PostgreSQL en una red privada; el modo externo local usa la ruta de salida de Nodo 2 al host, nunca la de Nodo 3.
- TCP de Ice solo se contempla dentro de las redes internas del prototipo. En producción se activaría IceSSL (TLS), rotación de secretos y gestión externa de secretos.
- Se elige resiliencia propia (Semaphore por estrategia, circuit breaker y timeout) para mantener transparente el comportamiento de simulación y evitar acoplar contratos a una librería.
- HTTP tendrá pool acotado y las solicitudes lentas de pasarela se ejecutarán de forma asíncrona; el pool cliente Ice también es acotado. Un retardo PSE de 15 s no debe retener el hilo HTTP ni el cupo de Stripe/Cripto. Los tamaños concretos y su validación de carga se documentarán con resultados medidos.
- La migración V2 agrega las instrucciones simuladas persistidas en JSONB para que una repetición idempotente recupere la misma referencia y las mismas instrucciones. V1 se conserva inmutable.
- El histograma RAS-02 empieza justo antes de invocar la estrategia y se registra al volver de ella, incluyendo las políticas de timeout/reintento, pero excluye el acceso previo de idempotencia y la persistencia posterior de la respuesta.

## Desviaciones conocidas durante el prototipo

- La creación de una base PostgreSQL requiere CREATEDB; si no está disponible debe crearse manualmente.
- El inicializador --verificar-db es de solo lectura y comprueba versión y privilegio CREATE del esquema existente; si aún no existe, comprueba CREATE sobre la base. No crea la base ni el esquema.
- Para conservar RAS-02 se dejó la aceptación inicial del simulador entre 100 y 200 ms; el rango de referencia 100–400 ms para Stripe se recorta en su extremo superior, porque una distribución con respuestas de 400 ms haría incompatible el P95 síncrono de 250 ms. La finalización del simulador sigue ocurriendo por callback tardío.
- El prototipo elige reembolso simulado inmediato después de registrar REEMBOLSO_PENDIENTE; ambas transiciones se auditan en la misma transacción local.
- La prueba Testcontainers está implementada, pero quedó omitida en esta ejecución porque Docker Desktop no tenía daemon activo. Por la misma razón no se midieron tasa, P50/P95/P99 ni se levantó Compose.
- La suite presente en el prototipo cubre reglas estáticas de arquitectura, dependencias, esquema, semilla idempotente y registro de una estrategia adicional. Los flujos HTTP completos, concurrencia de stock, callbacks/firma, resiliencia y carga aún requieren pruebas de integración adicionales; no se consideran demostrados por el build.
- No se incluye telemetría externa ni un servicio financiero. Las métricas viven en memoria.
