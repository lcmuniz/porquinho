# Story 0.6: Configure CI/CD Pipeline & Docker Containers

Status: done

## Story

As a developer,
I want to configure Docker containers and basic CI/CD setup,
So that the application can be built and deployed consistently.

## Acceptance Criteria

1. **Given** Docker is installed locally
   **When** I create Dockerfile for frontend (multi-stage build: Node 22-alpine -> Nginx alpine)
   **Then** Frontend Dockerfile builds successfully and creates production-ready image
   **And** Frontend container exposes port 80 and serves static files via Nginx

2. **Given** Frontend Dockerfile exists
   **When** I create Dockerfile for backend (OpenJDK 21-slim, jar execution)
   **Then** Backend Dockerfile builds successfully with Maven-built jar
   **And** Backend container exposes port 8080

3. **Given** Both Dockerfiles exist
   **When** docker-compose.yml defines all services: frontend, backend, redis, nginx (reverse proxy)
   **Then** Running `docker-compose up` starts all containers successfully
   **And** Services can communicate between containers via Docker network

4. **Given** docker-compose.yml is configured
   **When** Environment variables are configured via .env file
   **Then** No secrets or credentials are hardcoded in any Docker or compose file

5. **Given** GitHub repository exists
   **When** I create GitHub Actions workflows for frontend and backend
   **Then** Push to main/develop triggers appropriate CI pipeline (test -> build -> deploy)
   **And** PRs to main trigger test pipeline only

6. **Given** Nginx reverse proxy is configured
   **When** Requests arrive at the server
   **Then** `/` routes to frontend container (port 80)
   **And** `/api/` routes to backend container (port 8080)
   **And** HTTP redirects to HTTPS

## Tasks / Subtasks

- [x] Task 1: Criar Dockerfile do Frontend (AC: #1)
  - [x] 1.1 Criar `porquinho-frontend/Dockerfile` com multi-stage build (Node 22-alpine -> Nginx alpine)
  - [x] 1.2 Criar `porquinho-frontend/nginx.conf` para servir SPA (fallback para index.html)
  - [x] 1.3 Criar `porquinho-frontend/.dockerignore`
  - [x] 1.4 Testar build local: `docker build -t porquinho-frontend ./porquinho-frontend`

- [x] Task 2: Criar Dockerfile do Backend (AC: #2)
  - [x] 2.1 Criar `porquinho-backend/Dockerfile` com eclipse-temurin:21-jre-alpine (corrigido de openjdk:21-jdk-slim que nao existe mais)
  - [x] 2.2 Criar `porquinho-backend/.dockerignore`
  - [x] 2.3 Testar build local: `./mvnw clean package -DskipTests` + `docker build -t porquinho-backend ./porquinho-backend`

- [x] Task 3: Atualizar docker-compose.yml com todos os servicos (AC: #3, #4)
  - [x] 3.1 Expandir docker-compose.yml existente (atualmente so tem Redis)
  - [x] 3.2 Adicionar servico frontend (build context, port 3000:80, env vars)
  - [x] 3.3 Adicionar servico backend (build context, port 8080:8080, env vars, depends_on redis)
  - [x] 3.4 Adicionar servico nginx reverse proxy (ports 80/443, depends_on frontend+backend)
  - [x] 3.5 Criar arquivo `.env.example` com todas as variaveis necessarias
  - [x] 3.6 Garantir `.env` esta no `.gitignore`

- [x] Task 4: Configurar Nginx Reverse Proxy (AC: #6)
  - [x] 4.1 Criar `nginx.conf` na raiz do projeto para o reverse proxy
  - [x] 4.2 Configurar roteamento: `/` -> frontend:80, `/api/` -> backend:8080
  - [x] 4.3 Configurar headers de proxy (X-Real-IP, X-Forwarded-For, X-Forwarded-Proto)
  - [x] 4.4 Configurar redirect HTTP -> HTTPS (preparado para SSL)

- [x] Task 5: Criar GitHub Actions Workflows (AC: #5)
  - [x] 5.1 Criar `.github/workflows/frontend.yml` (test -> build -> deploy)
  - [x] 5.2 Criar `.github/workflows/backend.yml` (test -> build -> deploy)
  - [x] 5.3 Configurar path filters para executar apenas pipeline relevante
  - [x] 5.4 Documentar GitHub Secrets necessarios no README ou .env.example

- [x] Task 6: Testar integracao completa
  - [x] 6.1 `docker-compose up --build` e verificar todos os servicos
  - [x] 6.2 Verificar conectividade entre containers (frontend HTTP 200, Redis PONG, backend inicia na rede compose)
  - [x] 6.3 Verificar health check do backend via `/actuator/health` (retorna {"status":"DOWN"} - endpoint funcional, status DOWN por falta de DB)

## Dev Notes

### Estado Atual do Projeto

O docker-compose.yml na raiz **ja existe** mas so contem o servico Redis (criado na story 0-5). Voce DEVE expandir este arquivo, NAO criar um novo. O arquivo atual:

```yaml
services:
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    restart: unless-stopped

volumes:
  redis-data:
```

### Versoes Reais do Projeto (ATENCAO - diferem da arquitetura)

As versoes reais instaladas no projeto diferem do documento de arquitetura. USE ESTAS VERSOES:

| Componente | Versao Real | Versao na Arquitetura |
|------------|-------------|----------------------|
| Spring Boot | **3.5.0** | 3.4.x |
| Vite | **7.3.1** | 6.x |
| Node.js | **^20.19.0 \|\| >=22.12.0** | 22 |
| Pinia | **3.0.4** | 2.x |
| Vue Router | **5.0.3** | - |
| Vitest | **4.0.18** | latest |
| Playwright | **1.58.2** | latest |

Para o Dockerfile do frontend, use `node:22-alpine` (compativel com >=22.12.0).
Para GitHub Actions, use `node-version: '22'` e `java-version: '21'`.

### Dockerfile Frontend - Especificacao

```dockerfile
# Build stage
FROM node:22-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# Production stage
FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

**IMPORTANTE:** O `nginx.conf` dentro de `porquinho-frontend/` e para servir a SPA (fallback para index.html). E DIFERENTE do `nginx.conf` da raiz que e o reverse proxy.

### Dockerfile Backend - Especificacao

```dockerfile
FROM openjdk:21-jdk-slim
WORKDIR /app
COPY target/porquinho-backend-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx512m", "-jar", "app.jar"]
```

**IMPORTANTE:** O backend precisa do JAR pre-compilado. O dev deve rodar `./mvnw clean package -DskipTests` antes de `docker build`. O artifact name e `porquinho-backend-0.0.1-SNAPSHOT.jar` (verificar em pom.xml).

### docker-compose.yml - Especificacao Completa

Expandir o arquivo existente para incluir 4 servicos: frontend, backend, redis (ja existe), nginx.

Variaveis de ambiente do backend via docker-compose:
- `SPRING_PROFILES_ACTIVE=prod`
- `SUPABASE_DB_URL=${SUPABASE_DB_URL}`
- `SUPABASE_DB_USERNAME=${SUPABASE_DB_USERNAME}`
- `SUPABASE_DB_PASSWORD=${SUPABASE_DB_PASSWORD}`
- `REDIS_HOST=redis` (hostname do container, NAO localhost)
- `JWT_ISSUER_URI=${SUPABASE_URL}/auth/v1`

Variaveis de ambiente do frontend via docker-compose:
- `VITE_API_BASE_URL=${VITE_API_BASE_URL}`
- `VITE_SUPABASE_URL=${SUPABASE_URL}`
- `VITE_SUPABASE_ANON_KEY=${SUPABASE_ANON_KEY}`

**NOTA:** Variaveis VITE_ precisam estar disponiveis no build time (nao runtime). No Dockerfile do frontend, considerar usar ARG para injetar em build time.

### Nginx Reverse Proxy - Especificacao

O `nginx.conf` na raiz (para o servico nginx do docker-compose) deve:
- Redirecionar HTTP (80) para HTTPS (443)
- Rotear `/` para `frontend:80`
- Rotear `/api/` para `backend:8080`
- Incluir headers de proxy adequados
- SSL sera configurado com certificados montados via volume `./ssl:/etc/nginx/ssl`

### GitHub Actions - Especificacao

Dois workflows separados com path filters:

**Frontend (.github/workflows/frontend.yml):**
- Trigger: push main/develop (paths: porquinho-frontend/**), PR main
- Job test: checkout -> setup-node 22 -> npm ci -> npm run lint -> npm run test:unit -> npm run test:e2e
- Job build: (only main) checkout -> setup-node -> npm ci -> npm run build -> upload artifact
- Job deploy: (only main) download artifact -> SSH to VPS -> docker-compose up -d frontend

**Backend (.github/workflows/backend.yml):**
- Trigger: push main/develop (paths: porquinho-backend/**), PR main
- Job test: checkout -> setup-java temurin 21 -> ./mvnw test
- Job build: (only main) checkout -> setup-java -> ./mvnw clean package -DskipTests -> upload artifact
- Job deploy: (only main) download artifact -> SSH to VPS -> docker-compose up -d backend

**GitHub Secrets necessarios:**
- `VPS_HOST` - IP ou domain do VPS Hostinger
- `VPS_USER` - SSH username
- `VPS_SSH_KEY` - Private key para SSH
- `SUPABASE_URL` - https://[project-ref].supabase.co
- `SUPABASE_ANON_KEY` - Supabase anon key
- `SUPABASE_DB_URL` - Connection string PostgreSQL
- `SUPABASE_DB_PASSWORD` - DB password

### Hosting & Deploy Target

- **VPS Hostinger** com Ubuntu 22.04 LTS
- **Dokploy** para Docker orchestration
- Deploy path no VPS: `/opt/dokploy/porquinho`
- Deploy via SSH action: `appleboy/ssh-action@v1.0.0`

### Aprendizados da Story 0-5 (Redis)

- docker-compose.yml ja existe na raiz com servico Redis
- Backend application.yml ja usa variaveis de ambiente para Redis: `${REDIS_HOST:localhost}`, `${REDIS_PORT:6379}`, `${REDIS_PASSWORD:}`
- O padrao de variaveis de ambiente com defaults funciona bem para dev local vs container
- Testes rodam sem Redis (configuracao de teste desabilita cache)

### Project Structure Notes

Arquivos a serem criados/modificados:
```
porquinho/
├── docker-compose.yml              # MODIFICAR (expandir servicos)
├── nginx.conf                      # CRIAR (reverse proxy config)
├── .env.example                    # CRIAR (template de variaveis)
├── .github/
│   └── workflows/
│       ├── frontend.yml            # CRIAR
│       └── backend.yml             # CRIAR
├── porquinho-frontend/
│   ├── Dockerfile                  # CRIAR
│   ├── .dockerignore               # CRIAR
│   └── nginx.conf                  # CRIAR (SPA serving config)
└── porquinho-backend/
    ├── Dockerfile                  # CRIAR
    └── .dockerignore               # CRIAR
```

### Anti-Patterns a Evitar

- NAO hardcodar credenciais em Dockerfiles ou docker-compose
- NAO usar `latest` como tag de imagem Docker em producao
- NAO expor portas desnecessarias (Redis 6379 nao deve ser exposto em prod)
- NAO esquecer .dockerignore (node_modules, target/, .git)
- NAO usar `docker-compose version: '3.8'` - a propriedade version e obsoleta nas versoes atuais
- NAO criar application-prod.yml separado - o application.yml atual ja usa env vars, basta definir SPRING_PROFILES_ACTIVE
- NAO duplicar o servico Redis - ele JA EXISTE no docker-compose.yml

### References

- [Source: architecture/core-architectural-decisions.md#Infrastructure & Deployment] - Dockerfiles, docker-compose, Nginx, GitHub Actions completos
- [Source: architecture/core-architectural-decisions.md#CI/CD Pipeline: GitHub Actions] - Workflows completos
- [Source: architecture/core-architectural-decisions.md#Environment Configuration] - .env files e profiles
- [Source: architecture/project-structure-boundaries.md#Deployment] - Port mapping e servicos
- [Source: architecture/technical-preferences.md#Deployment] - VPS Hostinger + Dokploy
- [Source: epics/epic-0-project-foundation-technical-setup.md#Story 0.6] - Acceptance criteria originais

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6 (1M context)

### Debug Log References

- Frontend Docker build: sucesso, imagem criada e testada (HTTP 200 na porta 80)
- Backend Docker build: sucesso com eclipse-temurin:21-jre-alpine (openjdk:21-jdk-slim descontinuado)
- Backend container: inicia corretamente na rede compose, erro esperado de DB connection (sem credenciais)
- docker compose config: validado com sucesso, todos os 4 servicos configurados corretamente
- nginx.conf syntax: validada (erro de DNS upstream esperado fora da rede compose)
- Redis: PONG confirmado dentro da rede compose
- Health check /actuator/health: endpoint funcional, retorna {"status":"DOWN"} (esperado sem DB)
- Frontend unit tests: 1/1 passando (sem regressoes)

### Completion Notes List

- Task 1: Dockerfile frontend com multi-stage build (Node 22-alpine -> Nginx alpine), nginx.conf para SPA com fallback, .dockerignore. Build e container testados com sucesso.
- Task 2: Dockerfile backend com eclipse-temurin:21-jre-alpine (corrigido de openjdk:21-jdk-slim que foi descontinuado). JAR compilado e imagem Docker buildada com sucesso.
- Task 3: docker-compose.yml expandido com 4 servicos (frontend, backend, redis, nginx). Porta Redis removida (seguranca). .env.example criado. .env adicionado ao .gitignore.
- Task 4: nginx.conf reverse proxy criado na raiz com roteamento /, /api/, /actuator/, redirect HTTP->HTTPS, headers de proxy.
- Task 5: GitHub Actions workflows criados para frontend e backend com path filters, jobs test/build/deploy, deploy via SSH para VPS Hostinger.
- Task 6: Integracao validada - docker compose config OK, frontend (HTTP 200), backend (inicia, health check funcional), Redis (PONG), conectividade na rede compose confirmada.

### Change Log

- 2026-03-14: Story implementada - CI/CD pipeline e Docker containers configurados
- 2026-03-14: Code review - 5 issues corrigidos (H1: /actuator/ bloqueado publicamente, H2: .dockerignore simplificado, M1: VITE_ ENV no Dockerfile, M3: GitHub Secrets documentados, L1: http2 adicionado)

### File List

- porquinho-frontend/Dockerfile (novo)
- porquinho-frontend/nginx.conf (novo)
- porquinho-frontend/.dockerignore (novo)
- porquinho-backend/Dockerfile (novo)
- porquinho-backend/.dockerignore (novo)
- docker-compose.yml (modificado - expandido com 4 servicos)
- nginx.conf (novo - reverse proxy)
- .env.example (novo)
- .gitignore (modificado - adicionado .env e ssl/)
- .github/workflows/frontend.yml (novo)
- .github/workflows/backend.yml (novo)
