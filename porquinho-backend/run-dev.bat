@echo off
REM Script para rodar o backend em desenvolvimento no Windows

echo.
echo [BACKEND] Carregando variaveis de .env e iniciando Spring Boot...
echo.

REM Definir variaveis manualmente (conteudo do .env)
set SUPABASE_DB_URL=jdbc:postgresql://aws-1-us-east-1.pooler.supabase.com:5432/postgres
set SUPABASE_DB_USERNAME=postgres.vhkjyefwpwtlcyuznqgq
set SUPABASE_DB_PASSWORD=Lc@280475
set SUPABASE_JWT_SECRET=aBAyKZ+87900WIQ9sB+WlZwsbzxCuX2BcPmFEMmLVZWcbpnB3LqSKnqWlXyQQndjpx5yxQQQKiVJv2OnsEzaGA==
set SUPABASE_JWT_ISSUER_URI=https://vhkjyefwpwtlcyuznqgq.supabase.co/auth/v1
set REDIS_HOST=localhost
set REDIS_PORT=6379
set REDIS_PASSWORD=

echo [ENV] Variaveis de ambiente definidas!
echo.
echo Iniciando Spring Boot...
echo.

REM Iniciar Maven com Spring Boot
mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
