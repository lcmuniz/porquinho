#!/bin/bash
# Script para rodar o backend em desenvolvimento
# Carrega variáveis do .env e inicia o Spring Boot

# Carregar variáveis do .env (se existir - procura na pasta atual primeiro)
if [ -f ".env" ]; then
  echo "📦 Carregando variáveis de ambiente de .env"
  set -a; . ./.env; set +a  # respeita aspas (xargs quebrava valores com espaço)
elif [ -f "../.env" ]; then
  echo "📦 Carregando variáveis de ambiente de ../.env"
  set -a; . ../.env; set +a
else
  echo "⚠️  Arquivo .env não encontrado. Usando valores padrão."
fi

# O projeto exige Java 21; o padrão da máquina pode ser outro (ex.: 17)
if ! "${JAVA_HOME:-/nonexistent}/bin/java" -version 2>&1 | grep -q 'version "21'; then
  JDK21=$(ls -d "$HOME"/.sdkman/candidates/java/21* 2>/dev/null | head -1)
  [ -z "$JDK21" ] && JDK21=$(/usr/libexec/java_home -v 21 2>/dev/null)
  if [ -z "$JDK21" ]; then
    echo "❌ JDK 21 não encontrado. Instale com: sdk install java 21.0.11-tem"
    exit 1
  fi
  export JAVA_HOME="$JDK21"
fi
echo "☕ Usando Java em $JAVA_HOME"

# Iniciar Spring Boot em modo dev
echo "🚀 Iniciando backend Spring Boot (perfil: dev)..."
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

