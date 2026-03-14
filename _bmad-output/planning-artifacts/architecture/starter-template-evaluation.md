# Starter Template Evaluation

## Primary Technology Domain

**Full-Stack Separado:** Frontend Vue.js + Backend Java Spring Boot

Baseado nos requisitos do projeto:
- SaaS Web App desktop-first com mobile responsivo
- Fintech com LGPD compliance e security-first
- Real-time dashboard com performance crítica (< 2s)
- IA classification + multi-bank integrations

## Starter Options Considered

### 1. Frontend: `create-vue` (Oficial Vue CLI)

**Versão Atual:** Vue 3.5+ com Vite 6
**Comando:** `npm create vue@latest`

**O que já vem configurado:**
- ✅ Vite (build ultra-rápido com HMR)
- ✅ Vue Router (SPA navigation)
- ✅ Pinia (state management - sucessor do Vuex)
- ✅ TypeScript (opcional, mas recomendado)
- ✅ ESLint + Prettier
- ✅ Vitest (unit testing)
- ✅ E2E testing (Playwright/Cypress)

**Adições Necessárias:**
- Tailwind CSS via `@tailwindcss/vite`
- shadcn-vue components (9.6k stars, port oficial do shadcn-ui)
- Axios para comunicação com backend Spring Boot

**Vantagens:**
- Stack oficial mantida pelo Vue core team
- Vite oferece dev experience excepcional (HMR instantâneo)
- shadcn-vue alinha perfeitamente com requisito UX (Tailwind + Shadcn/UI)
- Estrutura pronta para SPA desktop-first

### 2. Backend: Spring Initializr

**Versão Atual:** Spring Boot 4.0.3 (JDK 25) ou 3.4.x (JDK 21 LTS)
**URL:** https://start.spring.io ou CLI `spring init`

**Configuração Recomendada:**
- Build Tool: Maven
- Java Version: 21 LTS (produção)
- Packaging: JAR (embedded Tomcat)
- Dependencies críticos:
  - Spring Web (REST APIs)
  - Spring Data JPA (ORM para PostgreSQL)
  - PostgreSQL Driver
  - Spring Security + OAuth2 Client (Google OAuth)
  - Spring Boot Actuator (health checks, metrics - NFR85-89)
  - Validation (Bean Validation)
  - Lombok (boilerplate reduction)

**Opcional mas Recomendado:**
- Spring Boot DevTools (hot reload)
- OpenAPI/Swagger (API docs)
- Spring Cache (performance optimization)
- Resilience4j (circuit breaker para integrações bancárias)

**Vantagens:**
- Ecossistema maduro para fintech (transações, security, auditoria)
- Spring Security robusto para LGPD compliance
- Excelente para integrações complexas (multi-bank parsing)
- Comunidade Java enterprise gigante

### 3. Database: Supabase (PostgreSQL Managed)

**Supabase Features Utilizadas:**
- PostgreSQL 16+ completo
- Auth service (Google OAuth built-in)
- Row Level Security (RLS) para segregação de dados (NFR15)
- Realtime subscriptions (opcional, para dashboard updates)
- Storage (futuro: anexos, comprovantes)

**Limitação:**
- Cliente oficial apenas JavaScript
- Backend Spring Boot usa PostgreSQL driver direto (connection string)

**Arquitetura:**
- Frontend Vue → Supabase Auth (Google OAuth)
- Backend Spring Boot → PostgreSQL via JDBC

## Selected Starter: Arquitetura Dual (Vue + Spring Boot)

### Rationale for Selection

**Por que Vue + Spring Boot separados (não monolito):**

1. **Separação de Concerns:** Frontend SPA (Vue) pode escalar/deploy independente do backend (Spring Boot)
2. **Performance:** Vite dev server é significativamente mais rápido que bundlers tradicionais
3. **Expertise:** Stack alinhada com habilidades existentes (Java + Vue)
4. **Fintech Requirements:** Spring Boot é padrão em fintech enterprise (security, transações, auditoria)
5. **Deploy Flexibility:** Frontend pode ir para CDN, backend para VPS Hostinger + Dokploy

**Por que esses starters específicos:**

- **create-vue:** Oficial, mantido pelo Vue core team, zero-config para Vite
- **Spring Initializr:** Padrão da indústria, gera estrutura Maven otimizada
- **shadcn-vue:** Requisito UX explícito (Tailwind + Shadcn/UI), 9.6k stars, copy-paste approach

### Initialization Commands

**Frontend (Vue + Tailwind + shadcn-vue):**

```bash
# 1. Criar projeto Vue com Vite
npm create vue@latest porquinho-frontend

# Opções recomendadas no prompt interativo:
# ✔ Add TypeScript? → Yes (type safety no frontend também)
# ✔ Add JSX Support? → No (não necessário para Vue SFC)
# ✔ Add Vue Router? → Yes (SPA navigation)
# ✔ Add Pinia? → Yes (state management)
# ✔ Add Vitest for Unit Testing? → Yes
# ✔ Add E2E Testing? → Playwright (mais moderno que Cypress)
# ✔ Add ESLint? → Yes
# ✔ Add Prettier? → Yes

cd porquinho-frontend
npm install

# 2. Instalar Tailwind CSS
npm install -D tailwindcss postcss autoprefixer @tailwindcss/vite
npx tailwindcss init -p

# 3. Configurar Tailwind no vite.config.ts
# (adicionar plugin @tailwindcss/vite)

# 4. Instalar shadcn-vue
npx shadcn-vue@latest init

# Opções shadcn-vue:
# - Style: Default
# - Base color: Purple (alinhado com UX spec: #9333EA)
# - CSS variables: Yes

# 5. Instalar Axios para backend communication
npm install axios

# 6. Run dev server
npm run dev
```

**Backend (Spring Boot + PostgreSQL):**

```bash
# Opção 1: Via Spring Initializr Web (https://start.spring.io)
# Configurações:
# - Project: Maven
# - Language: Java
# - Spring Boot: 3.4.x (Java 21 LTS)
# - Packaging: Jar
# - Java: 21
# - Dependencies:
#   * Spring Web
#   * Spring Data JPA
#   * PostgreSQL Driver
#   * Spring Security
#   * OAuth2 Client
#   * Validation
#   * Lombok
#   * Spring Boot Actuator
#   * Spring Boot DevTools

# Opção 2: Via CLI (se tiver spring-boot-cli instalado)
spring init \
  --dependencies=web,data-jpa,postgresql,security,oauth2-client,validation,lombok,actuator,devtools \
  --build=maven \
  --java-version=21 \
  --packaging=jar \
  --name=porquinho-backend \
  --artifact-id=porquinho-backend \
  --group-id=com.porquinho \
  porquinho-backend

cd porquinho-backend
./mvnw spring-boot:run
```

**Database (Supabase):**

```bash
# 1. Criar projeto no Supabase Dashboard (https://supabase.com)
# 2. Configurar Google OAuth provider no Supabase Auth
# 3. Obter connection string PostgreSQL:
#   postgresql://postgres:[PASSWORD]@db.[PROJECT-REF].supabase.co:5432/postgres

# 4. Configurar no Spring Boot (application.yml):
# spring:
#   datasource:
#     url: jdbc:postgresql://db.[PROJECT-REF].supabase.co:5432/postgres
#     username: postgres
#     password: [YOUR-PASSWORD]
```

### Architectural Decisions Provided by Starters

**Frontend Stack (create-vue + Tailwind + shadcn-vue):**

**Language & Runtime:**
- Vue 3.5+ com Composition API (`<script setup>` syntax)
- TypeScript 5.x para type safety
- Node.js 22.12+ (requisito mínimo)
- Vite 6 como build tool e dev server

**Styling Solution:**
- Tailwind CSS 4.x (utility-first, alinhado com UX spec)
- PostCSS para processamento
- shadcn-vue components (copy-paste, não external library)
- Purple (#9333EA) como primary color (UX spec)
- Inter font family (UX spec)
- WCAG 2.1 AA compliance built-in nos componentes shadcn-vue

**Build Tooling:**
- Vite 6: ESBuild para dev (HMR instantâneo), Rollup para prod
- Tree-shaking automático
- Code splitting por route (lazy loading)
- Asset optimization (images, fonts)
- TypeScript transpilation zero-config

**Testing Framework:**
- Vitest (unit tests) - API compatível com Jest, mas mais rápido
- Playwright (E2E tests) - suporta Chromium, Firefox, WebKit
- Vue Test Utils (component testing)

**Code Organization:**
```
porquinho-frontend/
├── src/
│   ├── assets/          # Static assets (images, fonts)
│   ├── components/      # Vue components (including shadcn-vue)
│   │   └── ui/         # shadcn-vue components copiados aqui
│   ├── composables/     # Composition API reusables
│   ├── router/          # Vue Router config
│   ├── stores/          # Pinia stores (state management)
│   ├── views/           # Page-level components
│   ├── services/        # API calls (Axios)
│   ├── utils/           # Helper functions
│   ├── App.vue
│   └── main.ts
├── public/              # Static files (servidos na raiz)
└── vite.config.ts       # Vite configuration
```

**Development Experience:**
- HMR (Hot Module Replacement) instantâneo via Vite
- TypeScript LSP integration (VS Code)
- ESLint + Prettier auto-formatting
- Vue DevTools extension suportada
- Component playground para desenvolvimento isolado

**State Management:**
- Pinia stores para estado global (substituiu Vuex)
- Composition API para estado local
- Vue Router para navegação SPA

**Performance Patterns (Critical para NFRs):**
- Lazy loading de routes (code splitting)
- Virtual scrolling para grandes listas (transações)
- Debouncing em inputs (classificação IA)
- Memoization de computed properties
- Keep-alive para cache de componentes navegados

---

**Backend Stack (Spring Boot + PostgreSQL):**

**Language & Runtime:**
- Java 21 LTS
- Spring Boot 3.4.x
- Embedded Tomcat (JAR packaging)
- Maven para build e dependency management

**API Layer:**
- Spring Web (REST Controllers)
- Jackson para JSON serialization/deserialization
- CORS configuration para Vue frontend
- Exception handling global (@ControllerAdvice)
- Request/Response DTOs (separados de Entities)

**Database & Persistence:**
- Spring Data JPA (ORM)
- Hibernate como JPA provider
- PostgreSQL 16+ (via Supabase)
- Flyway/Liquibase para migrations (adicionar depois)
- Connection pooling (HikariCP - default do Spring Boot)

**Security Architecture:**
- Spring Security 6.x
- OAuth2 Client (Google OAuth integration)
- JWT ou Session-based authentication (definir depois)
- CORS configuration para Vue frontend
- CSRF protection configurável
- Password encoding com BCrypt (NFR13)
- Rate limiting via Bucket4j ou custom filter (NFR17: 5 tentativas/minuto)

**Audit & Compliance (LGPD):**
- Spring Data JPA Auditing (@CreatedDate, @LastModifiedDate)
- Custom audit logging (operações sensíveis - NFR20)
- IP address capture via HttpServletRequest
- User consent management entities

**Build Tooling:**
- Maven 3.9+ (pom.xml)
- spring-boot-maven-plugin (cria fat JAR)
- maven-compiler-plugin (Java 21 target)
- Profiles para dev/prod (application-dev.yml, application-prod.yml)

**Testing Framework:**
- JUnit 5 (unit tests)
- Mockito (mocking)
- Spring Boot Test (@SpringBootTest, @WebMvcTest)
- Testcontainers (integration tests com PostgreSQL real)
- RestAssured (API testing)

**Code Organization (Layered Architecture):**
```
porquinho-backend/
├── src/main/java/com/porquinho/
│   ├── controller/       # REST Controllers (@RestController)
│   ├── service/          # Business Logic (@Service)
│   ├── repository/       # Data Access (@Repository, JPA)
│   ├── entity/           # JPA Entities (@Entity)
│   ├── dto/              # Request/Response DTOs
│   ├── config/           # Configuration classes (@Configuration)
│   │   ├── SecurityConfig.java
│   │   ├── CorsConfig.java
│   │   └── JpaConfig.java
│   ├── exception/        # Custom exceptions
│   ├── security/         # Security utilities
│   ├── audit/            # Audit logging
│   └── PorquinhoApplication.java
├── src/main/resources/
│   ├── application.yml           # Main config
│   ├── application-dev.yml       # Dev profile
│   ├── application-prod.yml      # Prod profile
│   └── db/migration/            # Flyway migrations (adicionar)
└── pom.xml
```

**Development Experience:**
- Spring Boot DevTools (auto-restart)
- Live reload (quando código muda)
- Actuator endpoints (/actuator/health, /metrics - NFR85-89)
- Swagger/OpenAPI auto-docs (adicionar springdoc-openapi)
- H2 Console para debug (dev only)

**Integration Patterns:**
- RestTemplate ou WebClient para chamadas HTTP (banks, IA API)
- Circuit Breaker (Resilience4j) para integrações externas
- Retry policies com exponential backoff
- Async processing (@Async) para IA classification

**Performance & Scalability (NFRs críticos):**
- Connection pooling (HikariCP)
- JPA query optimization (fetch strategies)
- Cache abstraction (Spring Cache) para dashboard queries
- Pagination para listas grandes (transações)
- Database indexing strategy (definir no schema)

---

**Database Schema (PostgreSQL via Supabase):**

**Supabase Features Utilizadas:**
- PostgreSQL 16+ completo
- Auth service (Google OAuth)
- Row Level Security (RLS) para segregação de dados (NFR15)
- Realtime (opcional, para dashboard live updates)
- Storage (futuro: anexos, comprovantes)

**Spring Boot Configuration:**
```yaml
spring:
  datasource:
    url: jdbc:postgresql://db.[PROJECT-REF].supabase.co:5432/postgres
    username: postgres
    password: ${SUPABASE_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate  # Prod: validate, Dev: update
    show-sql: true        # Dev only
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
```

**Schema Design Principles (a definir depois):**
- Multi-tenant via `user_id` foreign key (não schema separado)
- Audit columns em todas as tabelas (created_at, updated_at, created_by)
- Soft deletes (`deleted_at`) para LGPD compliance
- Indexes em foreign keys e queries frequentes
- JSONB para dados flexíveis (classificação IA metadata)

---

### Integration Architecture

**Frontend (Vue) ↔ Backend (Spring Boot):**
- REST APIs (JSON)
- Base URL configurável via `.env` (Vite)
- Axios interceptors para auth token injection
- Error handling global no frontend
- CORS liberado no Spring Boot para domínio Vue

**Backend (Spring Boot) ↔ Database (Supabase PostgreSQL):**
- JDBC via PostgreSQL driver
- Spring Data JPA repositories
- Connection string via environment variable
- HikariCP connection pool (10-20 connections para MVP)

**Authentication Flow (Supabase Auth + Spring Boot):**

**Opção Recomendada para MVP:**
- Frontend: Vue → Supabase Auth (Google OAuth)
- Supabase retorna JWT token
- Frontend envia JWT no header `Authorization: Bearer {token}`
- Backend: Spring Boot valida JWT via Supabase public key

**External Integrations:**
- IA Classification API (OpenAI/Claude): Spring Boot → HTTP Client
- Multi-Bank Parsing: Spring Boot services (OFX/CSV parsing)
- Payment Gateway (Stripe): Spring Boot → Stripe Java SDK

---

### Deployment Architecture (VPS Hostinger + Dokploy)

**Frontend Deploy:**
```bash
# Build production
npm run build  # Gera ./dist

# Opções:
# 1. Servir dist/ via Nginx no VPS
# 2. Deploy dist/ para CDN (Cloudflare Pages, Netlify)
# 3. Dokploy com Dockerfile para servir estático
```

**Backend Deploy:**
```bash
# Build JAR
./mvnw clean package -DskipTests

# Gera target/porquinho-backend-0.0.1-SNAPSHOT.jar

# Dokploy deployment:
# - Dockerfile com openjdk:21
# - COPY target/*.jar app.jar
# - ENTRYPOINT java -jar app.jar
# - Environment variables (DB, secrets)
```

**Database:**
- Supabase managed (nada a fazer no deploy)

---

### Next Steps After Initialization

**Frontend:**
1. Configurar Tailwind theme (purple primary, Inter font)
2. Instalar primeiros componentes shadcn-vue (Button, Input, Card)
3. Setup Axios com base URL e interceptors
4. Criar estrutura de pastas para Dashboard GPS (3 camadas)

**Backend:**
1. Configurar Supabase connection string
2. Setup Spring Security CORS para Vue dev server (localhost:5173)
3. Criar primeiro endpoint de health check
4. Setup JPA entities base (User, Transaction, Category)

**Database:**
1. Criar projeto Supabase
2. Configurar Google OAuth provider
3. Design schema inicial (users, transactions, categories, budgets)
4. Setup Row Level Security policies

**Note:** Project initialization usando estes comandos deve ser a **primeira implementation story** (Story 0: Project Setup).

---
