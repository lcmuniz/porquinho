@echo off
REM Script para LIMPAR COMPLETAMENTE o banco de dados (USE COM CUIDADO!)

echo.
echo ================================================
echo   ⚠️  FLYWAY CLEAN - APAGAR TUDO DO BANCO
echo ================================================
echo.

echo ⚠️  ATENÇÃO: Isso vai DELETAR TODAS as tabelas do banco!
echo.
echo    Use apenas em desenvolvimento local.
echo    NUNCA execute isso em produção!
echo.

set /p confirm="Tem certeza? Digite 'SIM' para continuar: "
if not "%confirm%"=="SIM" (
    echo.
    echo ❌ Operação cancelada.
    echo.
    pause
    exit /b 0
)

REM Carregar variáveis do .env
if exist "..\\.env" (
    echo.
    echo 📦 Carregando variáveis de ambiente...
    for /f "usebackq tokens=* delims=" %%a in ("..\.env") do (
        set "line=%%a"
        if not "!line!"=="" if not "!line:~0,1!"=="#" (
            set "%%a"
        )
    )
)

echo.
echo 🗑️  Limpando banco de dados...
echo.

REM Executar flyway clean
call mvnw flyway:clean -Dflyway.user=%SUPABASE_DB_USERNAME% -Dflyway.password=%SUPABASE_DB_PASSWORD% -Dflyway.url=%SUPABASE_DB_URL%

echo.
echo ✅ Banco de dados limpo!
echo.
echo 📝 Próximos passos:
echo    1. Execute run-dev.bat para recriar as tabelas
echo    2. As migrations serão executadas do zero
echo.

pause
