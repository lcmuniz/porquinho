@echo off
setlocal enabledelayedexpansion

REM Script para rodar o backend em desenvolvimento no Windows
REM Carrega variaveis do .env e inicia o Spring Boot

echo.
echo [BACKEND] Iniciando Spring Boot (perfil: dev)...
echo.

REM Ler e exportar variaveis do .env (se existir)
if exist ".env" (
    echo [ENV] Carregando variaveis de ambiente de .env
    for /f "usebackq tokens=1,* delims==" %%a in (".env") do (
        REM Pular linhas vazias e comentarios
        set "line=%%a"
        if not "!line:~0,1!"=="#" (
            if not "%%a"=="" if not "%%b"=="" (
                set "%%a=%%b"
                echo [ENV] Definido: %%a
            )
        )
    )
    echo.
) else (
    echo [AVISO] Arquivo .env nao encontrado.
    echo.
)

REM Iniciar Maven com Spring Boot
mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev

endlocal
