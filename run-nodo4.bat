@echo off
rem Nodo 4: persistencia (IRepositorioPagos por ICE, puerto 10004). Ejecutar desde cualquier carpeta: lee config.nodo4 y .env de la raiz.
cd /d "%~dp0"
call "%~dp0gradlew.bat" --quiet :nodo4-db:installDist || exit /b 1
java -cp "nodo4-db\build\install\nodo4-db\lib\*" com.apexstore.nodo4.Nodo4 %*
