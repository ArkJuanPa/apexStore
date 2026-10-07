#!/usr/bin/env bash
# Nodo 4: persistencia (IRepositorioPagos por ICE, puerto 10004). Ejecutar desde cualquier carpeta: se sitúa en la raíz, que es donde están config.nodo4 y .env.
set -e
cd "$(dirname "$0")"
./gradlew --quiet :nodo4-db:installDist
exec java -cp "nodo4-db/build/install/nodo4-db/lib/*" com.apexstore.nodo4.Nodo4 "$@"
