@echo off
REM Script para rodar o backend em desenvolvimento no Windows
REM Carrega variaveis do .env e inicia o Spring Boot

echo Carregando variaveis de ambiente...

REM Definir variaveis de ambiente
set SUPABASE_DB_URL=jdbc:postgresql://aws-1-us-east-1.pooler.supabase.com:5432/postgres
set SUPABASE_DB_USERNAME=postgres.vhkjyefwpwtlcyuznqgq
set SUPABASE_DB_PASSWORD=Lc@280475
set SUPABASE_JWT_SECRET=aBAyKZ+87900WIQ9sB+WlZwsbzxCuX2BcPmFEMmLVZWcbpnB3LqSKnqWlXyQQndjpx5yxQQQKiVJv2OnsEzaGA==
set SUPABASE_JWT_ISSUER_URI=https://vhkjyefwpwtlcyuznqgq.supabase.co/auth/v1
set REDIS_HOST=localhost
set REDIS_PORT=6379
set REDIS_PASSWORD=

echo Iniciando backend Spring Boot (perfil: dev)...
mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
