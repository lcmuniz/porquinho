#!/bin/bash
# Script para rodar o backend em desenvolvimento
# Carrega variáveis do .env na raiz e inicia o Spring Boot

# Carregar variáveis do .env (se existir)
if [ -f "../.env" ]; then
  echo "📦 Carregando variáveis de ambiente de ../.env"
  export $(cat ../.env | grep -v '^#' | xargs)
else
  echo "⚠️  Arquivo ../.env não encontrado. Usando valores padrão."
fi

# Iniciar Spring Boot em modo dev
echo "🚀 Iniciando backend Spring Boot (perfil: dev)..."
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

