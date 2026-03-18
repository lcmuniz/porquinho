@echo off
REM Script para rodar o backend em desenvolvimento no Windows
REM Le variaveis do arquivo .env e inicia o Spring Boot

echo.
echo [BACKEND] Iniciando Spring Boot (perfil: dev)...
echo.

REM Verificar se .env existe
if not exist ".env" (
    echo [ERRO] Arquivo .env nao encontrado em %CD%
    echo.
    exit /b 1
)

echo [ENV] Lendo variaveis de .env
echo.

REM Ler arquivo .env linha por linha (pular linhas vazias e comentarios)
for /f "usebackq eol=# tokens=1,* delims==" %%a in (".env") do (
    if not "%%b"=="" (
        set "%%a=%%b"
        echo [ENV] Carregado: %%a
    )
)

echo.
echo [INFO] Variaveis de ambiente carregadas do .env
echo.

REM Iniciar Maven com Spring Boot
mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
