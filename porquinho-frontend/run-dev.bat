@echo off
REM Script para rodar o frontend em desenvolvimento no Windows

echo.
echo [FRONTEND] Iniciando Vue + Vite (modo desenvolvimento)...
echo.

REM Verificar se .env existe
if not exist ".env" (
    echo [AVISO] Arquivo .env nao encontrado!
    echo.
    echo [INFO] Crie o arquivo .env a partir do .env.example:
    echo    copy .env.example .env
    echo.
    echo Depois edite o arquivo .env com suas credenciais do Supabase.
    echo.
    pause
    exit /b 1
)

REM Verificar se node_modules existe
if not exist "node_modules" (
    echo [NPM] Instalando dependencias...
    call npm install
)

REM Iniciar servidor de desenvolvimento
call npm run dev

