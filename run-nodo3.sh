#!/usr/bin/env bash
# Nodo 3: pasarelas simuladas (puerto 10000). Ejecutar desde cualquier carpeta: se sitúa en la raíz, que es donde están config.nodo3 y .env.
set -e
cd "$(dirname "$0")"
./gradlew --quiet :nodo3-pasarelas:installDist
exec java -cp "nodo3-pasarelas/build/install/nodo3-pasarelas/lib/*" com.apexstore.nodo3.ServidorPasarelas "$@"
