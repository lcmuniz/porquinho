@echo off
REM Script para rodar o backend em desenvolvimento no Windows
REM Carrega variaveis do .env e inicia o Spring Boot

echo.
echo [BACKEND] Iniciando Spring Boot (perfil: dev)...
echo.

REM Ler e exportar variaveis do .env (se existir)
if exist ".env" (
    echo [ENV] Carregando variaveis de ambiente de .env
    echo.
    for /f "usebackq tokens=1,* delims==" %%a in (".env") do (
        REM Pular comentarios (linhas que comecam com #)
        echo %%a | findstr /r "^#" >/dev/null
        if errorlevel 1 (
            REM Nao e comentario - definir variavel
            if not "%%a"=="" if not "%%b"=="" (
                set "%%a=%%b"
                echo [ENV] %%a definido
            )
        )
    )
    echo.
) else (
    echo [AVISO] Arquivo .env nao encontrado.
    echo.
)

REM Iniciar Maven com Spring Boot
echo Iniciando Spring Boot...
echo.
mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
