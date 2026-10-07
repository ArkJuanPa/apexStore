@echo off
rem Nodo 3: pasarelas simuladas (puerto 10000). Ejecutar desde cualquier carpeta: lee config.nodo3 de la raiz.
cd /d "%~dp0"
call "%~dp0gradlew.bat" --quiet :nodo3-pasarelas:installDist || exit /b 1
java -cp "nodo3-pasarelas\build\install\nodo3-pasarelas\lib\*" com.apexstore.nodo3.ServidorPasarelas %*
