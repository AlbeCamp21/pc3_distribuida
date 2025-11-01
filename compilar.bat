@echo off
echo   Compilando sistema del banco
echo.

set CLASSPATH=lib\*

echo [1/3] Compilando Sistema RENIEC...
javac -cp %CLASSPATH% sistema-reniec\SistemaRENIEC.java
if %errorlevel% neq 0 (
    echo ERROR: No se pudo compilar Sistema RENIEC
    pause
    exit /b 1
)

echo [2/3] Compilando Sistema Banco...
javac -cp %CLASSPATH% sistema-banco\SistemaBanco.java
if %errorlevel% neq 0 (
    echo ERROR: No se pudo compilar Sistema Banco
    pause
    exit /b 1
)

echo [3/3] Compilando Cliente Desktop...
javac -cp %CLASSPATH% cliente-desktop\ClienteDesktop.java
if %errorlevel% neq 0 (
    echo ERROR: No se pudo compilar Cliente Desktop
    pause
    exit /b 1
)
