#!/usr/bin/env bash
# Nodo 1: cliente de consola (MobileApp), menú interactivo. Ejecutar desde cualquier carpeta: se sitúa en la raíz, que es donde están config.nodo1 y .env.
set -e
cd "$(dirname "$0")"
./gradlew --quiet :nodo1-consola:installDist
exec java -Dstdout.encoding=UTF-8 -cp "nodo1-consola/build/install/nodo1-consola/lib/*" com.apexstore.nodo1.ClienteConsola "$@"
