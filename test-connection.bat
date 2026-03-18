@echo off
REM Script para diagnosticar problemas de conexão com Supabase

echo.
echo ================================================
echo   🔍 Diagnóstico de Conexão - Supabase
echo ================================================
echo.

REM Ler projeto ID do .env
for /f "tokens=2 delims==" %%a in ('findstr "SUPABASE_URL" .env') do set SUPABASE_URL=%%a
echo 📋 Projeto Supabase: %SUPABASE_URL%
echo.

REM Teste 1: Ping direct connection hostname
echo 🔌 Teste 1: Verificando hostname direto (db.xxx.supabase.co)...
ping -n 1 db.vhkjyefwpwtlcyuznqgq.supabase.co >nul 2>&1
if errorlevel 1 (
    echo ❌ FALHOU: Hostname direto não responde
    echo.
    echo    Possíveis causas:
    echo    - Projeto Supabase está PAUSADO
    echo    - Hostname incorreto
    echo    - Problema de DNS/rede
    echo.
    echo    💡 Solução: Use Connection Pooling (veja abaixo^)
    echo.
) else (
    echo ✅ OK: Hostname direto responde
    echo.
)

REM Teste 2: Ping connection pooler
echo 🔌 Teste 2: Verificando Connection Pooler...
ping -n 1 aws-0-us-east-1.pooler.supabase.com >nul 2>&1
if errorlevel 1 (
    echo ❌ FALHOU: Pooler não responde
    echo.
) else (
    echo ✅ OK: Connection Pooler responde
    echo    💡 Use este método! Edite .env com connection pooling
    echo.
)

REM Teste 3: Verificar se projeto está ativo
echo 🌐 Teste 3: Verificando se projeto está ativo...
echo.
echo    Acesse: https://supabase.com/dashboard/project/vhkjyefwpwtlcyuznqgq
echo    Verifique se está ATIVO ou PAUSADO
echo.

REM Teste 4: Verificar credenciais no .env
echo 📄 Teste 4: Verificando .env...
if not exist ".env" (
    echo ❌ FALHOU: Arquivo .env não encontrado!
    echo.
) else (
    echo ✅ OK: Arquivo .env existe
    echo.
    findstr "SUPABASE_DB_PASSWORD=PREENCHA" .env >nul 2>&1
    if not errorlevel 1 (
        echo ⚠️  AVISO: Senha ainda não foi preenchida!
        echo    Edite .env e preencha SUPABASE_DB_PASSWORD
        echo.
    ) else (
        echo ✅ OK: Senha parece estar preenchida
        echo.
    )
)

echo.
echo ================================================
echo   📋 Resumo e Recomendações
echo ================================================
echo.

echo 🔧 SOLUÇÃO RECOMENDADA: Use Connection Pooling
echo.
echo    1. Acesse: https://supabase.com/dashboard/project/vhkjyefwpwtlcyuznqgq/settings/database
echo    2. Vá em "Connection Pooling"
echo    3. Selecione modo "Transaction"
echo    4. Copie a connection string
echo    5. Edite .env com o formato correto
echo.
echo    Veja exemplo em: .env.pooling-example
echo.

echo 📖 Mais detalhes: CHECK-SUPABASE.md
echo.

pause
