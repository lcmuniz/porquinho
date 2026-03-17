@echo off
REM Script para rodar o backend em desenvolvimento no Windows
REM Carrega variáveis do .env na raiz e inicia o Spring Boot

echo.
echo [BACKEND] Iniciando Spring Boot (perfil: dev)...
echo.

REM Ler e exportar variáveis do .env (se existir)
if exist "..\\.env" (
    echo [ENV] Carregando variaveis de ambiente de ..\.env
    for /f "usebackq tokens=1,* delims==" %%a in ("..\.env") do (
        REM Verificar se a linha é um comentário ou está vazia
        echo %%a | findstr /r "^#" >nul
        if errorlevel 1 (
            REM Não é comentário - definir variável
            if not "%%a"=="" if not "%%b"=="" (
                set "%%a=%%b"
            )
        )
    )
) else (
    echo [AVISO] Arquivo ..\.env nao encontrado. Usando valores padrao.
)

REM Iniciar Maven com Spring Boot
mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev

