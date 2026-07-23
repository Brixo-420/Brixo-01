@echo off
title Brixo - Iniciando servicios
color 0A

echo ============================================
echo   BRIXO - Iniciando todos los servicios
echo ============================================
echo.

:: ── 1. Iniciar MySQL via XAMPP ──────────────────────────────
echo [1/3] Iniciando MySQL...
set XAMPP_DIR=C:\xampp
if exist "%XAMPP_DIR%\mysql\bin\mysqld.exe" (
    start "" "%XAMPP_DIR%\mysql\bin\mysqld.exe" --defaults-file="%XAMPP_DIR%\mysql\bin\my.ini"
    echo      MySQL iniciado en puerto 3307
    timeout /t 3 /nobreak >nul
) else (
    echo      XAMPP no encontrado en C:\xampp
    echo      Asegurate de que MySQL este corriendo manualmente.
    pause
)

echo.

:: ── 2. Iniciar servicio Python ───────────────────────────────
echo [2/3] Iniciando servicio Python en puerto 8001...
set "PYTHON_EXE=%~dp0python-service\.venv\Scripts\python.exe"
set "PYTHON_DIR=%~dp0python-service"
if exist "%PYTHON_EXE%" (
    start "Brixo Python Service" /d "%PYTHON_DIR%" "%PYTHON_EXE%" -m uvicorn main:app --host 0.0.0.0 --port 8001
    echo      Servicio Python iniciando...
    echo      Esperando 20 segundos para que carguen los modelos IA...
    timeout /t 20 /nobreak >nul
    echo      Python listo.
) else (
    echo      No se encontro el entorno virtual en python-service\.venv
    echo      Ejecuta python-service\start.bat manualmente para crearlo.
)

echo.

:: ── 3. Iniciar Spring Boot ───────────────────────────────────
echo [3/3] Iniciando Spring Boot en puerto 8080...
set "PROJECT_DIR=%~dp0"
if exist "%PROJECT_DIR%mvnw.cmd" (
    start "Brixo Spring Boot" cmd /k "cd /d ""%PROJECT_DIR%"" && mvnw.cmd spring-boot:run"
    echo      Spring Boot iniciando...
) else (
    echo      No se encontro mvnw.cmd
)

echo.
echo ============================================
echo   Servicios en curso:
echo   MySQL  : localhost:3307
echo   Python : http://localhost:8001
echo   App    : http://localhost:8080
echo ============================================
echo.
echo Abre el navegador en http://localhost:8080
echo.
echo Cierra esta ventana cuando quieras detener los servicios.
pause
