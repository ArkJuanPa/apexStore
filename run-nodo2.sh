#!/usr/bin/env bash
# Nodo 2: backend core ICE (puertos 10001 y 10003). Ejecutar desde cualquier carpeta: se sitúa en la raíz, que es donde están config.nodo2 y .env.
set -e
cd "$(dirname "$0")"
./gradlew --quiet :nodo2-backend:installDist
exec java -cp "nodo2-backend/build/install/nodo2-backend/lib/*" com.apexstore.nodo2.ServidorBackend "$@"
