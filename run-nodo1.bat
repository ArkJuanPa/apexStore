@echo off
rem Nodo 1: cliente de consola (MobileApp), menu interactivo. Ejecutar desde cualquier carpeta: lee config.nodo1 de la raiz.
cd /d "%~dp0"
call "%~dp0gradlew.bat" --quiet :nodo1-consola:installDist || exit /b 1
java -Dstdout.encoding=UTF-8 -cp "nodo1-consola\build\install\nodo1-consola\lib\*" com.apexstore.nodo1.ClienteConsola %*
