@echo off
title Brixo Python Service
cd /d "%~dp0"

if not exist ".venv\Scripts\activate.bat" (
    echo Creando entorno virtual...
    python -m venv .venv
)

call .venv\Scripts\activate.bat

echo Instalando/verificando dependencias...
pip install -r requirements.txt --quiet

echo.
echo  Brixo - Servicio de Validacion de Identidad
echo  http://localhost:8001
echo  http://localhost:8001/health
echo.
python -m uvicorn main:app --host 0.0.0.0 --port 8001 --log-level info
pause
