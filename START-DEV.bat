@echo off
REM Script principal para iniciar frontend E backend simultaneamente
REM Executa os dois servidores em janelas separadas

echo.
echo ================================================
echo   INICIANDO PORQUINHO - MODO DESENVOLVIMENTO
echo ================================================
echo.

REM Verificar se Redis está rodando (opcional)
echo [REDIS] Verificando Redis...
docker ps | findstr redis >nul 2>&1
if errorlevel 1 (
    echo [REDIS] Redis nao esta rodando. Iniciando container Redis...
    docker run -d -p 6379:6379 --name porquinho-redis redis:7-alpine
    timeout /t 2 >nul
) else (
    echo [REDIS] Redis ja esta rodando
)

echo.
echo [INFO] Abrindo terminais separados:
echo    - Backend (Spring Boot) na porta 8080
echo    - Frontend (Vue + Vite) na porta 5173
echo.

REM Abrir Backend em nova janela
start "Porquinho Backend" cmd /k "cd porquinho-backend && run-dev.bat"

REM Aguardar 3 segundos
timeout /t 3 >nul

REM Abrir Frontend em nova janela
start "Porquinho Frontend" cmd /k "cd porquinho-frontend && run-dev.bat"

echo.
echo [OK] Aplicacao iniciada!
echo.
echo [URLs] Acesse:
echo    - Frontend: http://localhost:5173
echo    - Backend:  http://localhost:8080
echo    - Health:   http://localhost:8080/api/v1/auth/health
echo.
echo [DICA] Use Ctrl+C nas janelas abertas para parar os servidores
echo.

