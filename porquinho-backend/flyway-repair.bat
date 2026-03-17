@echo off
REM Script para reparar o histórico do Flyway após migration com erro

echo.
echo ================================================
echo   FLYWAY REPAIR - Atualizar Checksums
echo ================================================
echo.

REM Carregar variáveis do .env
if exist "..\\.env" (
    echo [ENV] Carregando variaveis de ambiente...
    for /f "usebackq tokens=1,* delims==" %%a in ("..\.env") do (
        echo %%a | findstr /r "^#" >nul
        if errorlevel 1 (
            if not "%%a"=="" if not "%%b"=="" (
                set "%%a=%%b"
            )
        )
    )
)

echo.
echo [FLYWAY] Executando Flyway Repair...
echo.
echo    Isso vai atualizar os checksums das migrations no banco
echo    para match com os arquivos locais.
echo.

REM Executar flyway repair
call mvnw flyway:repair -Dflyway.user=%SUPABASE_DB_USERNAME% -Dflyway.password=%SUPABASE_DB_PASSWORD% -Dflyway.url=%SUPABASE_DB_URL%

echo.
echo [OK] Repair concluido!
echo.
echo [INFO] Proximos passos:
echo    1. As migrations agora estao sincronizadas
echo    2. Execute: run-dev.bat
echo.

pause
