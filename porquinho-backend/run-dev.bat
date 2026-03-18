@echo off
REM Script para rodar o backend em desenvolvimento no Windows

echo Iniciando backend Spring Boot com variaveis de ambiente...

mvnw.cmd spring-boot:run ^
  -Dspring-boot.run.profiles=dev ^
  -Dspring-boot.run.arguments="--SUPABASE_DB_URL=jdbc:postgresql://aws-1-us-east-1.pooler.supabase.com:5432/postgres --SUPABASE_DB_USERNAME=postgres.vhkjyefwpwtlcyuznqgq --SUPABASE_DB_PASSWORD=Lc@280475 --SUPABASE_JWT_SECRET=aBAyKZ+87900WIQ9sB+WlZwsbzxCuX2BcPmFEMmLVZWcbpnB3LqSKnqWlXyQQndjpx5yxQQQKiVJv2OnsEzaGA== --SUPABASE_JWT_ISSUER_URI=https://vhkjyefwpwtlcyuznqgq.supabase.co/auth/v1 --REDIS_HOST=localhost --REDIS_PORT=6379"
