# Story 0.3: Setup PostgreSQL Database with Flyway

Status: done

## Story

As a developer,
I want to setup PostgreSQL 16+ via Supabase with Flyway migrations,
So that I have a versioned database schema management system ready for feature development.

## Acceptance Criteria

1. **Given** I have a Supabase project created
   **When** I configure Spring Boot datasource with Supabase connection string
   **Then** Backend connects successfully to PostgreSQL via Supabase

2. **And** Flyway is configured in pom.xml and application.yml

3. **And** Flyway `baseline-on-migrate` is enabled

4. **And** Migrations directory exists at `src/main/resources/db/migration/`

5. **And** Application logs show "Successfully applied" on startup (Flyway migration runs)

6. **And** Database connection pool (HikariCP) is configured with `maximum-pool-size: 20`, `minimum-idle: 5`

7. **And** `./mvnw test` passes (context loads with DB connected)

8. **And** `http://localhost:8080/actuator/health` still returns `{"status":"UP"}`

## Tasks / Subtasks

- [x] Create Supabase project and obtain connection credentials (AC: #1)
  - [x] Create new project at https://supabase.com/dashboard
  - [x] Go to Project Settings → Database → Connection Info
  - [x] Copy: Host, Database, Port (5432), User, Password
  - [x] Note the connection string format: `jdbc:postgresql://db.[PROJECT-REF].supabase.co:5432/postgres`

- [x] Add Flyway dependencies to pom.xml (AC: #2)
  - [x] Add `flyway-core` (no version — managed by Spring Boot BOM)
  - [x] Add `flyway-database-postgresql` (required for Flyway 10.x PostgreSQL support)
  - [x] Add `h2` dependency with `<scope>test</scope>` (for unit tests without real DB)

- [x] Update .gitignore to protect credentials (AC: #1)
  - [x] Add `application-dev.yml` to `porquinho-backend/.gitignore`
  - [x] Add `.env` to `porquinho-backend/.gitignore`

- [x] Create `application-dev.yml.example` as committed template (AC: #1)
  - [x] Create `src/main/resources/application-dev.yml.example` with placeholder values
  - [x] Document all required env vars: `SUPABASE_DB_URL`, `SUPABASE_DB_USERNAME`, `SUPABASE_DB_PASSWORD`

- [x] Create `application-dev.yml` with real Supabase credentials (AC: #1, #6)
  - [x] Create `src/main/resources/application-dev.yml` (gitignored — NOT committed)
  - [x] Add datasource config pointing to Supabase with real credentials
  - [x] Configure HikariCP: `maximum-pool-size: 20`, `minimum-idle: 5`, `connection-timeout: 30000`
  - [x] Add JPA config: `ddl-auto: validate`, `show-sql: true` (dev only), PostgreSQL dialect
  - [x] Add Flyway config: `enabled: true`, `baseline-on-migrate: true`

- [x] Update `application.yml` — remove DataSource/JPA exclusions (AC: #1)
  - [x] **CRITICAL**: Remove `DataSourceAutoConfiguration` from `spring.autoconfigure.exclude`
  - [x] **CRITICAL**: Remove `HibernateJpaAutoConfiguration` from `spring.autoconfigure.exclude`
  - [x] Keep Security/OAuth2 exclusions (still needed — Security configured in Story 1.1)
  - [x] Add datasource config skeleton with env var references (without real values)
  - [x] Add JPA config with `ddl-auto: validate` and PostgreSQL dialect
  - [x] Add Flyway config block

- [x] Create Flyway migrations directory and initial migration (AC: #3, #4, #5)
  - [x] Create directory `src/main/resources/db/migration/`
  - [x] Create `V1__init.sql` with baseline comment (satisfies Flyway migration log requirement)

- [x] Create test configuration to run tests without real DB (AC: #7)
  - [x] Create `src/test/resources/application.yml` with H2 in-memory config
  - [x] Configure H2 with `MODE=PostgreSQL` and `ddl-auto: create-drop`
  - [x] Set `spring.flyway.enabled: false` for tests

- [x] Verify startup and health endpoint (AC: #7, #8)
  - [x] Run `./mvnw clean test` — all tests pass
  - [x] Run `./mvnw spring-boot:run` with `SPRING_PROFILES_ACTIVE=dev`
  - [x] Confirm logs show Flyway migration message
  - [x] Confirm `http://localhost:8080/actuator/health` returns `{"status":"UP","components":{"db":{"status":"UP"},...}}`

## Dev Notes

### CRITICAL: Story 0.2 Left DataSource/JPA Disabled

Story 0.2 added these exclusions to `application.yml` to allow startup without a database:

```yaml
spring:
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration       # ← REMOVE
      - org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration  # ← REMOVE
      - org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
      - org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
      - org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration
      - org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration
```

**This story must remove the first two exclusions** (DataSource and JPA). The Security exclusions MUST REMAIN — they will be addressed in Story 1.1.

---

### pom.xml Changes

Add inside `<dependencies>`:

```xml
<!-- Flyway - Database Migrations -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>

<!-- H2 - In-memory database for unit tests only -->
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>
```

**No version tags needed** — Spring Boot BOM (3.5.0) manages Flyway 10.x and H2 versions automatically.

---

### application.yml — Final State After This Story

```yaml
spring:
  application:
    name: porquinho-backend
  profiles:
    active: dev
  autoconfigure:
    exclude:
      # DataSource and JPA exclusions REMOVED in this story
      - org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
      - org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
      - org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration
      - org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration
  datasource:
    url: ${SUPABASE_DB_URL}
    username: ${SUPABASE_DB_USERNAME}
    password: ${SUPABASE_DB_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
        default_schema: public
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration
    validate-on-migrate: true
server:
  port: 8080
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
```

---

### application-dev.yml — Local Dev (Gitignored)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://db.[PROJECT-REF].supabase.co:5432/postgres
    username: postgres
    password: YOUR-SUPABASE-PASSWORD
  jpa:
    show-sql: true
```

The Spring profile `dev` (set in application.yml `profiles.active: dev`) will automatically merge this file on startup.

**Important:** `application-dev.yml` must be in `.gitignore`. Create `application-dev.yml.example` as a committed template.

---

### application-dev.yml.example — Committed Template

```yaml
# Copy this file to application-dev.yml and fill in real Supabase credentials
# application-dev.yml is gitignored and should NEVER be committed
spring:
  datasource:
    url: jdbc:postgresql://db.[PROJECT-REF].supabase.co:5432/postgres
    username: postgres
    password: YOUR-SUPABASE-PASSWORD-HERE
  jpa:
    show-sql: true
```

---

### Flyway Initial Migration

**Path:** `src/main/resources/db/migration/V1__init.sql`

```sql
-- V1__init.sql
-- Baseline migration for porquinho database
-- Database: PostgreSQL 16+ via Supabase
-- Project: porquinho - Personal Finance Manager
--
-- This baseline establishes the Flyway migration history.
-- Future migrations will create business tables (users, transactions, etc.)
-- following the naming convention: V{N}__{description}.sql
--
-- Naming conventions for all future migrations:
--   Tables:      snake_case plural (users, transactions, budget_categories)
--   Columns:     snake_case (user_id, created_at, transaction_date)
--   Primary Keys: id UUID PRIMARY KEY DEFAULT gen_random_uuid()
--   Indexes:     idx_{table}_{column} (idx_transactions_user_id)
--   Constraints: {table}_{column}_{type} (users_email_unique)

-- Enable pgcrypto extension (if not already enabled by Supabase)
-- Note: gen_random_uuid() is built-in for PostgreSQL 13+ — no extension needed
SELECT 1; -- no-op statement to produce a valid migration
```

**Migration naming for future stories:**
```
V1__init.sql                            ← this story
V2__create_users_table.sql              ← Story 1.x (Auth)
V3__create_transactions_table.sql       ← Story 5.x (Transactions)
V4__create_budget_tables.sql            ← Story 6.x (Budget)
```

---

### Test Configuration — src/test/resources/application.yml

Create this file to allow `./mvnw test` without a real database:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;NON_KEYWORDS=VALUE
    username: sa
    password:
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop
    properties:
      hibernate:
        dialect: org.hibernate.dialect.H2Dialect
  flyway:
    enabled: false
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
      - org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
      - org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration
      - org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration
```

No changes to `PorquinhoBackendApplicationTests.java` are needed — Spring Boot automatically picks up `src/test/resources/application.yml` for tests.

---

### Supabase Connection String — Exact Format

```
jdbc:postgresql://db.[PROJECT-REF].supabase.co:5432/postgres
```

- Port **5432** = direct PostgreSQL (required for JPA/Flyway)
- Port 6543 = PgBouncer (connection pooler) — **DO NOT USE** for JPA/Hibernate
- SSL is required by Supabase — the PostgreSQL JDBC driver enables SSL by default for Supabase hosts

If SSL errors occur, add to datasource URL: `?sslmode=require`

---

### Running with Dev Profile

```bash
# Option 1: Using SPRING_PROFILES_ACTIVE env var (recommended)
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run

# Option 2: Using JVM argument
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Option 3: If credentials are set as OS env vars instead of application-dev.yml
export SUPABASE_DB_URL=jdbc:postgresql://db.[REF].supabase.co:5432/postgres
export SUPABASE_DB_USERNAME=postgres
export SUPABASE_DB_PASSWORD=your-password
./mvnw spring-boot:run
```

**Note from Story 0.2:** JDK 21 is configured via `mise.toml`. If running from a new terminal, the shell may default to JDK 17. Use `mise exec -- ./mvnw spring-boot:run` or set `JAVA_HOME` explicitly:
```bash
export JAVA_HOME=$(mise where java)
```

---

### Expected Startup Logs (Success)

When Flyway runs successfully:
```
Flyway OSS Edition ... by Redgate ...
Database: jdbc:postgresql://db.[REF].supabase.co:5432/postgres (PostgreSQL 16.x)
Schema history table "public"."flyway_schema_history" does not exist yet
Creating Schema History table "public"."flyway_schema_history" ...
Current version of schema "public": << Empty Schema >>
Migrating schema "public" to version "1 - init"
Successfully applied 1 migration to schema "public" ...
```

When Actuator reports DB:
```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP", "details": { "database": "PostgreSQL", "validationQuery": "isValid()" } },
    "diskSpace": { "status": "UP" },
    "ping": { "status": "UP" }
  }
}
```

---

### Architecture Compliance

**From implementation-patterns-consistency-rules.md:**
- All future tables MUST use UUID PKs: `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`
- All tables MUST have audit columns: `created_at`, `updated_at`, `created_by`, `updated_by`, `deleted_at`, `deleted_by`
- Soft deletes only — never hard delete user data (LGPD compliance)
- Index format: `idx_{table}_{column(s)}`
- All queries must filter by `user_id` from JWT (security pattern for future stories)

**From core-architectural-decisions.md:**
- `ddl-auto: validate` in production/main config (Flyway owns schema management)
- `ddl-auto: update` is NEVER used in this project
- HikariCP settings are mandatory: `maximum-pool-size: 20`, `minimum-idle: 5`

---

### Files to Create/Modify

| Action | File |
|--------|------|
| MODIFY | `porquinho-backend/pom.xml` |
| MODIFY | `porquinho-backend/src/main/resources/application.yml` |
| CREATE | `porquinho-backend/src/main/resources/application-dev.yml` (gitignored) |
| CREATE | `porquinho-backend/src/main/resources/application-dev.yml.example` |
| CREATE | `porquinho-backend/src/main/resources/db/migration/V1__init.sql` |
| CREATE | `porquinho-backend/src/test/resources/application.yml` |
| MODIFY | `porquinho-backend/.gitignore` |

### References

- [Source: _bmad-output/planning-artifacts/epics/epic-0-project-foundation-technical-setup.md#Story 0.3]
- [Source: _bmad-output/planning-artifacts/architecture/core-architectural-decisions.md#Data Migrations: Flyway]
- [Source: _bmad-output/planning-artifacts/architecture/core-architectural-decisions.md#Database: PostgreSQL via Supabase]
- [Source: _bmad-output/planning-artifacts/architecture/implementation-patterns-consistency-rules.md#Database Naming Conventions]
- [Source: _bmad-output/implementation-artifacts/0-2-initialize-spring-boot-backend-project.md#Dev Agent Record]

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- Direct connection (`db.vhkjyefwpwtlcyuznqgq.supabase.co`) é IPv6-only — não funciona em redes IPv4.
- Solução: usar Session Pooler do Supabase (`aws-1-us-east-1.pooler.supabase.com:5432`) com username no formato `postgres.[project-ref]`.
- `./mvnw` sem `mise exec` falha com "release version 21 not supported" — sempre usar `mise exec -- ./mvnw`.

### Completion Notes List

- Adicionadas dependências `flyway-core`, `flyway-database-postgresql` e `h2` (test) ao pom.xml — gerenciadas pelo Spring Boot BOM 3.5.0 (Flyway 11.7.2).
- `application.yml` atualizado: removidas exclusões DataSource/JPA, adicionados blocos datasource, jpa, flyway com env vars.
- `application-dev.yml` criado (gitignored) com Session Pooler do Supabase — PostgreSQL 17.6 confirmado.
- `application-dev.yml.example` criado como template commitado.
- `V1__init.sql` criado em `db/migration/` — Flyway baseline estabelecido com sucesso.
- `src/test/resources/application.yml` criado com H2 in-memory (MODE=PostgreSQL, flyway.enabled=false).
- `.gitignore` atualizado para proteger `application-dev.yml` e `.env`.
- `./mvnw clean test` — 1 teste, 0 falhas (BUILD SUCCESS).
- `http://localhost:8080/actuator/health` retorna `{"status":"UP"}`.
- Logs confirmam: "Successfully validated 2 migrations", "Schema up to date".

### Code Review Fixes Applied

- `application-dev.yml.example`: Corrigido formato de conexão de direct connection (IPv6-only) para Session Pooler, com documentação do problema e formato correto do username.
- `application.yml`: Substituído comentário que referenciava a story por comentário contextual sobre as exclusões de Security.

### File List

- porquinho-backend/pom.xml
- porquinho-backend/src/main/resources/application.yml
- porquinho-backend/src/main/resources/application-dev.yml (gitignored)
- porquinho-backend/src/main/resources/application-dev.yml.example
- porquinho-backend/src/main/resources/db/migration/V1__init.sql
- porquinho-backend/src/test/resources/application.yml
- porquinho-backend/.gitignore
