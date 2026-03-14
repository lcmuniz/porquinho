# Story 0.2: Initialize Spring Boot Backend Project

Status: done

## Story

As a developer,
I want to initialize the Spring Boot 3.4.x backend project with JDK 21 LTS,
So that I have a working REST API server with all required dependencies.

## Acceptance Criteria

1. **Given** I have JDK 21 LTS installed
   **When** I run Spring Initializr with dependencies: Spring Web, Spring Data JPA, PostgreSQL Driver, Spring Security, OAuth2 Client, Validation, Lombok, Actuator, DevTools
   **Then** A complete Spring Boot 3.5.x Maven project is created in `porquinho-backend` directory

2. **And** Running `./mvnw spring-boot:run` starts the server on http://localhost:8080

3. **And** Accessing http://localhost:8080/actuator/health returns {"status":"UP"}

4. **And** All required dependencies are configured in pom.xml

5. **And** Application uses JDK 21 LTS as target version

## Tasks / Subtasks

- [x] Generate Spring Boot project via Spring Initializr (AC: #1, #4, #5)
  - [x] Use https://start.spring.io or CLI command
  - [x] Configure: Maven, Java 21, Spring Boot 3.4.x, Jar packaging
  - [x] Add dependencies: Spring Web, Spring Data JPA, PostgreSQL Driver, Spring Security, OAuth2 Client, Validation, Lombok, Actuator, DevTools
  - [x] Set groupId: com.porquinho
  - [x] Set artifactId: porquinho-backend
  - [x] Set name: porquinho-backend
  - [x] Download and extract to `porquinho-backend/` directory

- [x] Verify project structure (AC: #1)
  - [x] Confirm Maven wrapper files exist (mvnw, mvnw.cmd)
  - [x] Verify pom.xml contains all selected dependencies
  - [x] Verify src/main/java/com/porquinho/ structure exists
  - [x] Verify src/main/resources/application.properties exists
  - [x] Verify src/test/java structure exists

- [x] Convert application.properties to application.yml (AC: #1)
  - [x] Delete src/main/resources/application.properties
  - [x] Create src/main/resources/application.yml
  - [x] Add basic Spring Boot configuration

- [x] Install dependencies and start server (AC: #2)
  - [x] Run `./mvnw clean install` to download dependencies
  - [x] Run `./mvnw spring-boot:run` and verify server starts
  - [x] Verify console shows "Started PorquinhoBackendApplication"
  - [x] Verify server runs on port 8080

- [x] Verify Actuator health endpoint (AC: #3)
  - [x] Access http://localhost:8080/actuator/health
  - [x] Verify response: {"status":"UP"}
  - [x] Verify console logs show no errors

- [x] Create basic package structure (AC: #1)
  - [x] Create com.porquinho.controller package
  - [x] Create com.porquinho.service package
  - [x] Create com.porquinho.repository package
  - [x] Create com.porquinho.entity package
  - [x] Create com.porquinho.dto package
  - [x] Create com.porquinho.config package
  - [x] Create com.porquinho.exception package

## Dev Notes

### Critical Technical Requirements

**Versions (DO NOT DEVIATE):**
- Java: 21 LTS (Oracle JDK, OpenJDK, or Temurin)
- Spring Boot: 3.4.x (latest stable)
- Maven: 3.9+ (included via wrapper)
- Packaging: JAR (embedded Tomcat)

**Command to Execute:**

**Option 1: Via Spring Initializr Web (Recommended)**
1. Go to https://start.spring.io
2. Configure:
   - Project: Maven
   - Language: Java
   - Spring Boot: 3.4.x (use latest 3.4.x version available)
   - Project Metadata:
     - Group: com.porquinho
     - Artifact: porquinho-backend
     - Name: porquinho-backend
     - Package name: com.porquinho
     - Packaging: Jar
     - Java: 21
3. Dependencies:
   - Spring Web
   - Spring Data JPA
   - PostgreSQL Driver
   - Spring Security
   - OAuth2 Client
   - Validation
   - Lombok
   - Spring Boot Actuator
   - Spring Boot DevTools
4. Click "Generate" and extract ZIP to project root

**Option 2: Via Spring Boot CLI**
```bash
spring init \
  --dependencies=web,data-jpa,postgresql,security,oauth2-client,validation,lombok,actuator,devtools \
  --build=maven \
  --java-version=21 \
  --packaging=jar \
  --name=porquinho-backend \
  --artifact-id=porquinho-backend \
  --group-id=com.porquinho \
  --type=maven-project \
  porquinho-backend
```

**Option 3: Via curl (if no CLI available)**
```bash
curl https://start.spring.io/starter.zip \
  -d dependencies=web,data-jpa,postgresql,security,oauth2-client,validation,lombok,actuator,devtools \
  -d type=maven-project \
  -d language=java \
  -d bootVersion=3.4.2 \
  -d baseDir=porquinho-backend \
  -d groupId=com.porquinho \
  -d artifactId=porquinho-backend \
  -d name=porquinho-backend \
  -d packageName=com.porquinho \
  -d packaging=jar \
  -d javaVersion=21 \
  -o porquinho-backend.zip

unzip porquinho-backend.zip
```

### Expected Project Structure

After initialization, the `porquinho-backend/` directory should contain:

```
porquinho-backend/
├── src/
│   ├── main/
│   │   ├── java/com/porquinho/
│   │   │   ├── controller/       # REST Controllers (criar manualmente)
│   │   │   ├── service/          # Business Logic (criar manualmente)
│   │   │   ├── repository/       # Data Access (criar manualmente)
│   │   │   ├── entity/           # JPA Entities (criar manualmente)
│   │   │   ├── dto/              # Request/Response DTOs (criar manualmente)
│   │   │   ├── config/           # Configuration classes (criar manualmente)
│   │   │   ├── exception/        # Custom exceptions (criar manualmente)
│   │   │   └── PorquinhoBackendApplication.java  # Main class (gerado)
│   │   └── resources/
│   │       ├── application.yml   # Main config (criar manualmente, substituir .properties)
│   │       ├── application-dev.yml    # Dev profile (criar depois)
│   │       ├── application-prod.yml   # Prod profile (criar depois)
│   │       └── static/           # Static files (gerado, vazio)
│   └── test/
│       └── java/com/porquinho/
│           └── PorquinhoBackendApplicationTests.java  # Test class (gerado)
├── .gitignore                    # Git ignore (gerado)
├── mvnw                          # Maven wrapper Unix (gerado)
├── mvnw.cmd                      # Maven wrapper Windows (gerado)
├── pom.xml                       # Maven dependencies (gerado)
└── README.md                     # Project readme (gerado, opcional)
```

### Project Context Alignment

**Location:** This creates the backend project in a SEPARATE directory from the frontend (dual architecture approach).

**Directory Naming:** Must be `porquinho-backend` (NOT `backend/`, NOT `server/`). This aligns with the dual-stack architecture decision where frontend and backend are independent projects.

**Workspace Setup:** After this story, the project root should have:
```
porquinho/
├── porquinho-frontend/    # Story 0.1 created this
├── porquinho-backend/     # This story creates this
└── _bmad-output/          # Planning artifacts
```

### Technology Rationale (From Architecture)

**Why Spring Boot 3.4.x:**
- Latest stable LTS version with JDK 21 support
- Excellent for fintech (security, transactions, auditing)
- Mature ecosystem for complex integrations (multi-bank parsing, AI APIs)
- Production-ready defaults (embedded Tomcat, Actuator, security)

**Why JDK 21 LTS:**
- Long-term support until September 2028
- Virtual threads (Project Loom) for high concurrency
- Pattern matching and records for cleaner code
- Security updates and performance improvements

**Why Maven (not Gradle):**
- Industry standard for Java enterprise projects
- Better IDE integration (IntelliJ, Eclipse, VS Code)
- Simpler dependency management for team collaboration
- Spring Boot Initializr default

**Why These Dependencies:**

| Dependency | Purpose | Story Where Used |
|------------|---------|------------------|
| **Spring Web** | REST API Controllers | All feature stories (1.x, 2.x, etc.) |
| **Spring Data JPA** | ORM for PostgreSQL | Story 0.3 (database setup) |
| **PostgreSQL Driver** | JDBC connection to Supabase | Story 0.3 (database setup) |
| **Spring Security** | Authentication & Authorization | Epic 1 (Auth), all protected endpoints |
| **OAuth2 Client** | Google OAuth integration | Story 1.1 (Google OAuth) |
| **Validation** | Bean Validation (@Valid) | All POST/PUT endpoints |
| **Lombok** | Reduce boilerplate (@Data, @Builder) | All entities, DTOs, services |
| **Actuator** | Health checks, metrics | Epic 10 (Admin Operations) |
| **DevTools** | Hot reload, live reload | Development only |

**What Comes Next:**
- Story 0.3: Setup PostgreSQL Database with Flyway
- Story 0.5: Setup Redis for Caching
- Story 0.6: Configure CI/CD Pipeline & Docker Containers

### Development Experience Notes

**After Initialization:**
- Spring Boot starts in ~3-5 seconds (empty project)
- DevTools enables automatic restart on code changes
- Actuator endpoints available at /actuator/* (health, info, metrics)
- Default server port: 8080
- Default context path: / (no prefix)

**Performance:**
- Embedded Tomcat starts in < 5 seconds
- JVM warm-up takes ~30s for optimal performance
- DevTools restart is faster than full restart (~2s)

**DO NOT:**
- Connect to database yet (Story 0.3 will configure Supabase)
- Implement any endpoints yet (feature stories will add them)
- Configure Spring Security yet (Story 1.1 will add auth)
- Change the default port 8080 (frontend expects this)
- Delete application.properties without creating application.yml first

**DO:**
- Create empty package structure (controller, service, repository, etc.)
- Convert application.properties to application.yml
- Verify all dependencies are in pom.xml
- Test that server starts successfully
- Verify Actuator health endpoint works

### Verification Checklist

Before marking this story as complete:

1. ✅ `porquinho-backend/` directory exists
2. ✅ `pom.xml` contains all required dependencies (web, data-jpa, postgresql, security, oauth2-client, validation, lombok, actuator, devtools)
3. ✅ `./mvnw spring-boot:run` starts server successfully on port 8080
4. ✅ Console logs show "Started PorquinhoBackendApplication in X seconds"
5. ✅ http://localhost:8080/actuator/health returns {"status":"UP"}
6. ✅ Java version is 21 (check pom.xml: <java.version>21</java.version>)
7. ✅ Spring Boot version is 3.4.x (check pom.xml parent version)
8. ✅ Empty package structure created (controller, service, repository, entity, dto, config, exception)
9. ✅ application.yml exists (converted from application.properties)
10. ✅ No compilation errors or warnings

### Context from Previous Story (0.1)

**Story 0.1 (Vue Frontend) Created:**
- porquinho-frontend/ directory with Vue 3.5+ and Vite 6
- Dev server running on http://localhost:5173
- TypeScript, Vue Router, Pinia, Vitest, Playwright configured
- ESLint and Prettier for code quality

**Integration Notes:**
- Backend (this story) will run on http://localhost:8080
- Frontend will call backend APIs via Axios
- CORS configuration needed (Story 0.3 or 1.1)
- Both projects are independent (can run separately)

**Dual Architecture Confirmed:**
- Two separate directories: porquinho-frontend/ and porquinho-backend/
- Two separate git repositories (optional, can be monorepo)
- Independent deployment (frontend → Nginx, backend → JAR)

### Architecture Compliance

**Layered Architecture Pattern (from architecture.md):**
```
controller/    → REST Controllers (@RestController)
service/       → Business Logic (@Service)
repository/    → Data Access (@Repository, JPA)
entity/        → JPA Entities (@Entity)
dto/           → Request/Response DTOs
config/        → Configuration classes (@Configuration)
exception/     → Custom exceptions
```

**Naming Conventions (from implementation-patterns.md):**
- Classes: PascalCase with suffixes (TransactionController, BudgetService, UserRepository)
- Files: Match class name (TransactionController.java)
- Methods: camelCase (getTransactions, createBudget)
- Packages: lowercase (com.porquinho.controller)

**API Conventions (for future stories):**
- Base path: /api/v1/*
- Plural nouns: /api/v1/transactions, /api/v1/categories
- REST verbs: GET (list), GET/{id} (single), POST (create), PUT (update), DELETE (delete)
- Error handling: RFC 7807 Problem Details (via @ControllerAdvice)

**Configuration Pattern (application.yml):**
```yaml
spring:
  application:
    name: porquinho-backend
  profiles:
    active: dev
server:
  port: 8080
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
```

### Critical Implementation Rules

**DO:**
- Use JDK 21 LTS (not 17, not 22, not 25)
- Use Spring Boot 3.4.x (not 3.3.x, not 4.x preview)
- Use Maven (pom.xml, not build.gradle)
- Create application.yml (not .properties)
- Create empty package structure following architecture pattern
- Verify all dependencies are present in pom.xml
- Test server startup and Actuator health endpoint

**DO NOT:**
- Add any database configuration yet (Story 0.3)
- Add any security configuration yet (Story 1.1)
- Implement any REST endpoints yet (feature stories)
- Add custom dependencies beyond Spring Initializr selection
- Change server port from 8080
- Modify PorquinhoBackendApplication.java (keep as generated)

### References

- [Source: _bmad-output/planning-artifacts/epics/epic-0-project-foundation-technical-setup.md#Story 0.2]
- [Source: _bmad-output/planning-artifacts/architecture/starter-template-evaluation.md#Backend: Spring Initializr]
- [Source: _bmad-output/planning-artifacts/architecture/starter-template-evaluation.md#Initialization Commands]
- [Source: _bmad-output/planning-artifacts/architecture/implementation-patterns-consistency-rules.md#Code Naming Conventions]
- [Source: _bmad-output/planning-artifacts/architecture/implementation-patterns-consistency-rules.md#Project Organization]
- [Source: _bmad-output/planning-artifacts/architecture/core-architectural-decisions.md#Data Architecture]

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6

### Implementation Notes

- Projeto gerado via curl no Spring Initializr (start.spring.io)
- Spring Boot 3.4.x nao estava mais disponivel no Initializr (minimo 3.5.0). Aprovado pelo usuario usar Spring Boot 3.5.0
- JDK 21.0.2 configurado via mise (JAVA_HOME explicito necessario pois shell padrao usa JDK 17)
- Auto-configuracao de DataSource, JPA, Security e OAuth2 desabilitada via spring.autoconfigure.exclude no application.yml (nao deve haver config de banco/auth nesta story)
- ManagementWebSecurityAutoConfiguration tambem excluida para evitar conflito entre Actuator e Security sem HttpSecurity bean
- Pacotes criados com .gitkeep para persistir diretorios vazios no Git
- Teste contextLoads() do Spring Boot passa com sucesso
- Servidor inicia em ~2 segundos na porta 8080
- Health endpoint retorna {"status":"UP"}

### Completion Notes List

- Projeto Spring Boot 3.5.0 com JDK 21 inicializado com sucesso
- Todas as 9 dependencias configuradas no pom.xml (web, data-jpa, postgresql, security, oauth2-client, validation, lombok, actuator, devtools)
- application.properties convertido para application.yml com configuracao base
- Estrutura de pacotes criada (controller, service, repository, entity, dto, config, exception)
- Servidor testado: startup OK, health endpoint OK, testes unitarios OK
- Nenhum endpoint, banco ou security configurado (conforme instrucoes da story)

### Change Log

- 2026-03-14: Story implementada - projeto Spring Boot inicializado com todas dependencias e estrutura de pacotes

### Files Created/Modified

- porquinho-backend/pom.xml (gerado pelo Initializr)
- porquinho-backend/mvnw (gerado)
- porquinho-backend/mvnw.cmd (gerado)
- porquinho-backend/.gitignore (gerado)
- porquinho-backend/.gitattributes (gerado)
- porquinho-backend/.mvn/wrapper/maven-wrapper.properties (gerado)
- porquinho-backend/HELP.md (gerado)
- porquinho-backend/src/main/java/com/porquinho/PorquinhoBackendApplication.java (gerado)
- porquinho-backend/src/main/resources/application.yml (criado, substituiu application.properties)
- porquinho-backend/src/test/java/com/porquinho/PorquinhoBackendApplicationTests.java (gerado)
- porquinho-backend/src/main/java/com/porquinho/controller/.gitkeep (criado)
- porquinho-backend/src/main/java/com/porquinho/service/.gitkeep (criado)
- porquinho-backend/src/main/java/com/porquinho/repository/.gitkeep (criado)
- porquinho-backend/src/main/java/com/porquinho/entity/.gitkeep (criado)
- porquinho-backend/src/main/java/com/porquinho/dto/.gitkeep (criado)
- porquinho-backend/src/main/java/com/porquinho/config/.gitkeep (criado)
- porquinho-backend/src/main/java/com/porquinho/exception/.gitkeep (criado)
- mise.toml (modificado - java@21.0.2 adicionado ao projeto)
