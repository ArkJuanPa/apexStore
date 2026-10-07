@echo off
rem Nodo 2: backend core ICE (puertos 10001 y 10003). Ejecutar desde cualquier carpeta: lee config.nodo2 y .env de la raiz.
cd /d "%~dp0"
call "%~dp0gradlew.bat" --quiet :nodo2-backend:installDist || exit /b 1
java -cp "nodo2-backend\build\install\nodo2-backend\lib\*" com.apexstore.nodo2.ServidorBackend %*
