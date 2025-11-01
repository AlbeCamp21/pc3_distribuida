@echo off
setlocal EnableDelayedExpansion

echo   Descargando dependencias RABBITMQ
echo.

if not exist lib mkdir lib

echo [1/3] Descargando amqp-client-5.16.0.jar...
curl.exe -L -o lib\amqp-client-5.16.0.jar https://repo1.maven.org/maven2/com/rabbitmq/amqp-client/5.16.0/amqp-client-5.16.0.jar 2>nul
if !errorlevel! neq 0 (
    echo ERROR: No se pudo descargar amqp-client
    echo Verifique su conexion a internet
    pause
    exit /b 1
)

echo [2/3] Descargando slf4j-api-1.7.36.jar...
curl.exe -L -o lib\slf4j-api-1.7.36.jar https://repo1.maven.org/maven2/org/slf4j/slf4j-api/1.7.36/slf4j-api-1.7.36.jar 2>nul
if !errorlevel! neq 0 (
    echo ERROR: No se pudo descargar slf4j-api
    echo Verifique su conexion a internet
    pause
    exit /b 1
)

echo [3/3] Descargando slf4j-simple-1.7.36.jar...
curl.exe -L -o lib\slf4j-simple-1.7.36.jar https://repo1.maven.org/maven2/org/slf4j/slf4j-simple/1.7.36/slf4j-simple-1.7.36.jar 2>nul
if !errorlevel! neq 0 (
    echo ERROR: No se pudo descargar slf4j-simple
    echo Verifique su conexion a internet
    pause
    exit /b 1
)
