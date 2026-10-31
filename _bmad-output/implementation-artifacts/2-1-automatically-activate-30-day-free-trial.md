---
status: done
baseline_commit: 7c989a17c3216ae174ff6b90cb7e45fb3487b3a3
story_key: 2-1-automatically-activate-30-day-free-trial
context: []
---

# Story 2.1: Ativar automaticamente trial gratuito de 30 dias

## Story

Como novo usuário,
quero receber automaticamente um trial gratuito de 30 dias ao me cadastrar,
para que eu possa explorar todas as funcionalidades sem compromisso de pagamento.

## Critérios de Aceitação (BDD)

### AC1: Tabela `subscriptions` via Flyway

**Given** o backend inicia
**When** o Flyway roda
**Then** a migration `V9__create_subscriptions_table.sql` cria a tabela `subscriptions` com: `id` (UUID, PK), `user_id` (FK → `users.id`, NOT NULL, UNIQUE), `plan_type` (`TRIAL`/`MONTHLY`), `status` (`ACTIVE`/`CANCELLED`/`EXPIRED`), `trial_start_date`, `trial_end_date`, `next_billing_date`, `payment_method_id`, `created_at`, `updated_at`, `deleted_at`
**And** usuários já existentes sem assinatura recebem um trial de 30 dias a partir da data da migration (backfill)

> ⚠️ O epic cita `V5__create_subscriptions_table.sql`. **V5 a V8 já existem** (V5 = failed login tracking). A próxima versão livre é **V9**. Usar V9.

### AC2: Trial criado automaticamente no cadastro

**Given** concluo o cadastro (Story 1.1 Google ou 1.2 email/senha)
**When** minha conta é criada no backend (`isNewUser == true`)
**Then** um registro em `subscriptions` é criado na **mesma transação** com `plan_type='TRIAL'`, `status='ACTIVE'`
**And** `trial_start_date` = agora (UTC)
**And** `trial_end_date` = `trial_start_date` + 30 dias
**And** um audit log `trial_started` é registrado
**And** chamadas repetidas ao endpoint de registro (idempotentes, `isNewUser == false`) **não** criam nem reiniciam o trial

### AC3: Acesso completo durante o trial

**Given** tenho trial ativo
**When** `agora < trial_end_date`
**Then** tenho acesso a todas as funcionalidades

### AC4: Status de assinatura checado em toda requisição autenticada

**Given** faço uma requisição autenticada em `/api/v1/**`
**When** a rota não está na lista de exceções (ver Dev Notes)
**Then** o backend verifica a assinatura do usuário (`sub` do JWT)
**And** se `hasAccess == false` (trial vencido, sem assinatura ou status ≠ `ACTIVE`), responde **402 Payment Required** com corpo `ProblemDetail`
**And** rotas de auth, assinatura e direitos LGPD (exportação/exclusão) **sempre** passam, mesmo sem acesso

### AC5: Badge no dashboard

**Given** estou logado com trial ativo
**When** abro o dashboard
**Then** vejo o badge "Trial: X dias restantes" (singular: "1 dia restante")
**And** com o trial vencido vejo "Trial expirado"
**And** se a API falhar ou retornar 404, o badge não aparece e o dashboard continua funcionando

### AC6: Integração com LGPD (Stories 1.6 e 1.7)

**Given** existe a tabela `subscriptions`
**Then** a exportação de dados (Story 1.6) inclui a assinatura do usuário
**And** o purge definitivo (Story 1.7) apaga a assinatura **antes** de apagar o usuário (senão a FK quebra o purge)

## Tasks / Subtasks

- [x] **Task 0: Corrigir ID do usuário Google = `sub` do Supabase (pré-requisito do AC4)** (AC: 2, 4)
  - [x] 0.1 Hoje `AuthService.registerOrGetUserFromGoogle` cria `new User(...)` com UUID **gerado**, enquanto o caminho email usa `insertWithId` com o `sub` do JWT. `DataExportController`, `AccountDeletionController` e o novo filtro usam `jwt.getSubject()` como `users.id` → para usuários Google o lookup **não encontra ninguém**, e todo usuário Google levaria 402
  - [x] 0.2 Passar `userId` (do `@AuthenticationPrincipal`) para `registerOrGetUserFromGoogle` e criar o usuário com `insertWithId`
  - [x] 0.3 Estender `UserRepository.insertWithId` com o parâmetro `googleId` (a constraint `users_google_id_check` exige `google_id` NOT NULL para `GOOGLE`). Atualizar a chamada do caminho email passando `null`
  - [x] 0.4 Atualizar `AuthServiceTest` / `AuthControllerTest`
  - [x] 0.5 Usuários Google já existentes com ID divergente: **não migrar IDs**. O banco de dev já foi limpo (ver Decisões confirmadas)
- [x] **Task 1: Migration V9** (AC: 1)
  - [x] 1.1 Criar `porquinho-backend/src/main/resources/db/migration/V9__create_subscriptions_table.sql` (esqueleto em Dev Notes)
  - [x] 1.2 Backfill: `INSERT INTO subscriptions (...) SELECT ... FROM users WHERE deleted_at IS NULL AND id NOT IN (SELECT user_id FROM subscriptions)`
  - [x] 1.3 **Sem índice parcial (`WHERE`)**: H2 não suporta (ver comentário em V2). `UNIQUE (user_id)` já cria o índice
  - [x] 1.4 Adicionar teste da V9 em `MigrationTest` (tabela existe e constraint UNIQUE em `user_id`)
- [x] **Task 2: Entity + Repository** (AC: 1, 2)
  - [x] 2.1 `entity/Subscription.java` com enums internos `PlanType { TRIAL, MONTHLY }` e `Status { ACTIVE, CANCELLED, EXPIRED }`, `@Enumerated(EnumType.STRING)`, datas `OffsetDateTime`, `@PrePersist`/`@PreUpdate` como em `UserSession`
  - [x] 2.2 **Sem `@Where`**: na Story 1.7, `@Where` quebrou o hard-delete
  - [x] 2.3 `repository/SubscriptionRepository.java`: `Optional<Subscription> findByUserId(UUID)`, `existsByUserId(UUID)`, `deleteByUserId(UUID)` (`@Modifying(clearAutomatically = true)` + `@Query` native, mesmo padrão de `DataExportRepository.deleteByUserId`)
- [x] **Task 3: `SubscriptionService`** (AC: 2, 3, 4)
  - [x] 3.1 `startTrial(UUID userId)`: idempotente (se `existsByUserId`, retorna o existente sem alterar). Cria TRIAL/ACTIVE, start = `now(clock)`, end = start + 30 dias. Audit `trial_started` com metadata `{"trial_end_date":"..."}`
  - [x] 3.2 `Optional<Subscription> findByUserId(UUID)`
  - [x] 3.3 `boolean hasAccess(UUID userId)`: `status == ACTIVE && (planType != TRIAL || now < trialEndDate)`. Sem assinatura → `false`
  - [x] 3.4 `long daysRemaining(Subscription)`: `ceil` das horas restantes / 24, mínimo 0 (29d 1h → 30; vencido → 0)
  - [x] 3.5 Injetar `java.time.Clock` (bean `Clock.systemUTC()` em `config/ClockConfig.java`) para testar datas sem `sleep`
  - [x] 3.6 Constante `TRIAL_DAYS = 30` (sem config externa, o valor não muda por ambiente)
- [x] **Task 4: Integrar no cadastro** (AC: 2)
  - [x] 4.1 Em `AuthService`, nos dois ramos de **criação** (Google e email), chamar `subscriptionService.startTrial(id)` logo após salvar o usuário e antes do audit `user_registration`. Fica dentro do `@Transactional` existente: se o trial falhar, o cadastro faz rollback
  - [x] 4.2 **Não** chamar nos ramos idempotentes nem nos de vinculação de conta (`google_account_linked`, `auth_method_linked`)
- [x] **Task 5: Endpoint `GET /api/v1/subscriptions/me`** (AC: 5)
  - [x] 5.1 `controller/SubscriptionController.java` em `@RequestMapping("/api/v1/subscriptions")`, `@AuthenticationPrincipal Jwt jwt` + `UUID.fromString(jwt.getSubject())` (padrão do `DataExportController`)
  - [x] 5.2 `dto/SubscriptionResponse.java`: `planType`, `status`, `trialStartDate`, `trialEndDate`, `daysRemaining`, `hasAccess` (JSON camelCase)
  - [x] 5.3 Sem assinatura → `ResourceNotFoundException` (o `GlobalExceptionHandler` já mapeia para 404)
- [x] **Task 6: Filtro de acesso por assinatura** (AC: 4)
  - [x] 6.1 `config/SubscriptionAccessFilter.java` estende `OncePerRequestFilter`. **NÃO anotar com `@Component`**: o Spring Boot registraria o filtro como servlet filter global, inclusive no perfil `test` (o `SecurityConfig` é `@Profile("!test")`)
  - [x] 6.2 Registrar em `SecurityConfig.securityFilterChain` com `.addFilterAfter(new SubscriptionAccessFilter(subscriptionService), BearerTokenAuthenticationFilter.class)`
  - [x] 6.3 `shouldNotFilter`: true se o path não começa com `/api/v1/` ou começa com um prefixo isento (ver Dev Notes), ou se não há `JwtAuthenticationToken` no `SecurityContextHolder`
  - [x] 6.4 Sem acesso → status 402, `Content-Type: application/problem+json`, corpo `ProblemDetail` (title "Assinatura necessária", detail "Seu período de trial terminou. Assine para continuar.") serializado via `ObjectMapper`
  - [x] 6.5 `sub` inválido (não UUID) → deixa passar. A autenticação já validou o token e o controller trata o caso
- [x] **Task 7: LGPD** (AC: 6)
  - [x] 7.1 `AccountDeletionService.purgeUser`: `subscriptionRepository.deleteByUserId(userId)` **antes** de `userRepository.deleteByIdNative`. Manter o TODO da linha ~205 (`cancelImmediately`), que pertence à Story 2.4 (Stripe)
  - [x] 7.2 `DataExportService.collectUserData`: adicionar a chave `subscriptions` (lista com 0..1 item, campos do DTO) e incluir `"subscriptions"` em `exported_tables`
  - [x] 7.3 Atualizar `AccountDeletionServiceTest` e `DataExportServiceTest` (e o construtor com mocks)
- [x] **Task 8: Frontend: tipos, service, badge** (AC: 5)
  - [x] 8.1 `src/types/subscription.ts`: `interface Subscription { planType: 'TRIAL' | 'MONTHLY'; status: 'ACTIVE' | 'CANCELLED' | 'EXPIRED'; trialStartDate: string | null; trialEndDate: string | null; daysRemaining: number; hasAccess: boolean }`
  - [x] 8.2 `src/services/subscriptionService.ts`: `getMySubscription()` usando `api` de `./api` (padrão de `sessionService.ts`)
  - [x] 8.3 `src/components/subscription/TrialBadge.vue`: recebe `subscription` via prop, renderiza nada se não for TRIAL. Pill `rounded-full` (UX: badges = pills), `role="status"`, contraste WCAG AA
  - [x] 8.4 `DashboardView.vue`: busca no `onMounted`, guarda em `ref`, `try/catch` silencioso (AC5: falha esconde o badge). Badge ao lado do `<h1>`
  - [x] 8.5 `src/components/__tests__/TrialBadge.spec.ts`: 30 dias, 1 dia (singular), expirado e não-trial (não renderiza)
- [x] **Task 9: Testes backend** (AC: todos)
  - [x] 9.1 `SubscriptionServiceTest` (Mockito + `Clock.fixed`): criação, idempotência, `hasAccess` antes/depois do fim, sem assinatura, `daysRemaining` (borda 29d1h → 30, vencido → 0)
  - [x] 9.2 `SubscriptionAccessFilterTest`: 402 para rota protegida sem acesso; passa em rotas isentas; passa com acesso; passa sem autenticação
  - [x] 9.3 `SubscriptionControllerTest`: 200 com corpo, 404 sem assinatura
  - [x] 9.4 `AuthServiceTest`: `startTrial` chamado **só** quando `isNewUser`
  - [x] 9.5 Rodar a suíte completa backend (`./mvnw test`) e frontend (`npm run test:unit`, `npm run type-check`). Zero regressões

## Dev Notes

### Arquitetura híbrida (CRÍTICO)

- Supabase Auth é a autoridade de login. O backend guarda dados de negócio (incluindo **assinaturas**, ver comentário no `AuthController.registerWithEmail`)
- O identificador do usuário em **todas** as rotas é `jwt.getSubject()`, que **precisa** ser igual a `users.id`. Por isso existe a Task 0
- [Source: ARQUITETURA-SIMPLIFICADA.md], [Source: porquinho-backend/.../controller/AuthController.java]

### Estado atual dos arquivos que serão modificados

| Arquivo | Hoje | Muda | Preservar |
|---|---|---|---|
| `service/AuthService.java` | Ramo Google cria `User` com UUID gerado. Ramo email usa `insertWithId(sub)`. Ambos `@Transactional`, com audit `user_registration` | Google passa a usar `sub`. Os dois ramos de criação chamam `startTrial` | Idempotência, vinculação de contas, audit logs, `UserRegistrationResult` |
| `controller/AuthController.java` | `registerWithGoogle` recebe `userId` mas não repassa | Repassar `userId` ao service | Criação de sessão, 201/200 |
| `repository/UserRepository.java` | `insertWithId(id, email, authProvider, lgpdConsentAt, createdAt, updatedAt)` | + `googleId` | `deleteByIdNative`, `findByIdIncludingDeleted` |
| `config/SecurityConfig.java` | Chain stateless, `permitAll` públicos, `/api/v1/**` authenticated, blacklist via converter | + `addFilterAfter(SubscriptionAccessFilter)` e injeção de `SubscriptionService` | CORS, matchers, converter |
| `service/AccountDeletionService.java` | `purgeUser` (REQUIRES_NEW, native SQL): data_exports → user_sessions → audit_logs → users → Supabase | + `subscriptions` antes de `users` | Ordem, best-effort Supabase, self-injection AOP |
| `service/DataExportService.java` | Exporta `users`, `user_sessions`, `audit_logs` | + `subscriptions` | Formato JSON, `data_version` |
| `views/DashboardView.vue` | Título + seções "Configuracoes da conta" e "Zona de perigo" | + badge no header | Links e a11y (`min-h-[44px]`, `aria-labelledby`) |

### Rotas isentas do filtro (AC4)

```
/api/v1/auth/**            # registro, login, sessões, reset de senha
/api/v1/subscriptions/**   # o usuário precisa ver o próprio status (e assinar na 2.2)
/api/v1/data-export/**     # direito LGPD: independe de pagamento
/api/v1/account-deletion/**# direito LGPD: independe de pagamento
```

Hoje **todas** as rotas existentes estão isentas, então o filtro só passa a ter efeito com os endpoints dos Epics 3+. É esperado. Os testes do filtro garantem o comportamento.

### Esqueleto da migration V9

```sql
-- V9: Create subscriptions table (Story 2.1 - trial gratuito de 30 dias)
CREATE TABLE subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    plan_type VARCHAR(20) NOT NULL CHECK (plan_type IN ('TRIAL', 'MONTHLY')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'CANCELLED', 'EXPIRED')),
    trial_start_date TIMESTAMPTZ,
    trial_end_date TIMESTAMPTZ,
    next_billing_date TIMESTAMPTZ,
    payment_method_id VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT subscriptions_user_id_unique UNIQUE (user_id)
);

-- Backfill: usuários existentes ganham trial a partir de agora
INSERT INTO subscriptions (user_id, plan_type, status, trial_start_date, trial_end_date)
SELECT id, 'TRIAL', 'ACTIVE', NOW(), NOW() + INTERVAL '30' DAY
FROM users WHERE deleted_at IS NULL;
```

- `INTERVAL '30' DAY` (sintaxe SQL padrão) funciona em PostgreSQL e H2. **Não** usar `INTERVAL '30 days'`, que falha no H2
- `UNIQUE (user_id)`: um registro por usuário durante toda a vida. Upgrade/cancelamento (2.2+) **atualizam** a linha. Isso também impede que alguém reinicie o trial
- Valores de enum em MAIÚSCULAS, consistente com `auth_provider` e `account_deletions.status`. O epic usa minúsculas (`trial`/`active`), e aqui prevalece a convenção do código
- `payment_method_id` e `next_billing_date` ficam NULL no trial. São preenchidos pela Story 2.2 (Stripe)
- Status `payment_failed`/`suspended` (Story 2.7) **não** entram agora. A 2.7 altera o CHECK
- `deleted_at` existe porque o AC pede, mas nada o preenche nesta story (o purge apaga a linha). Por isso não há `@Where`

### Decisões técnicas

- **Expiração computada na leitura, sem job.** `hasAccess` compara `now` com `trial_end_date`. O job diário que marca `EXPIRED` e envia lembretes é da Story 2.3. Não criar `@Scheduled` aqui
- **Sem cache no filtro.** É uma query indexada por `user_id` por requisição. `// ponytail:` comentar o teto: se a latência pesar, usar `@Cacheable` no Redis (já configurado, `RedisConfig`) com TTL curto e evict em `startTrial`/updates
- **402 Payment Required** (não 403): o frontend da 2.2 vai usar esse status para redirecionar à tela de assinatura sem confundir com falta de permissão
- **Datas em UTC com `OffsetDateTime`/`TIMESTAMPTZ`**, padrão adotado no fix de review da Story 1.7 (V6/V8). `User` ainda usa `LocalDateTime` (legado), não copiar
- **Sem dependência nova** (Stripe entra na 2.2)

### Estrutura do projeto

O código real usa **camadas planas** (`controller/`, `service/`, `entity/`, `repository/`, `dto/`, `config/`) em `com.porquinho`, e não pacotes por feature (`subscription/`) como sugere `architecture/project-structure-boundaries.md`. **Seguir o código existente.**

Arquivos novos:

```
porquinho-backend/src/main/resources/db/migration/V9__create_subscriptions_table.sql
porquinho-backend/src/main/java/com/porquinho/entity/Subscription.java
porquinho-backend/src/main/java/com/porquinho/repository/SubscriptionRepository.java
porquinho-backend/src/main/java/com/porquinho/service/SubscriptionService.java
porquinho-backend/src/main/java/com/porquinho/controller/SubscriptionController.java
porquinho-backend/src/main/java/com/porquinho/dto/SubscriptionResponse.java
porquinho-backend/src/main/java/com/porquinho/config/SubscriptionAccessFilter.java
porquinho-backend/src/main/java/com/porquinho/config/ClockConfig.java
porquinho-backend/src/test/java/com/porquinho/service/SubscriptionServiceTest.java
porquinho-backend/src/test/java/com/porquinho/config/SubscriptionAccessFilterTest.java
porquinho-backend/src/test/java/com/porquinho/controller/SubscriptionControllerTest.java
porquinho-frontend/src/types/subscription.ts
porquinho-frontend/src/services/subscriptionService.ts
porquinho-frontend/src/components/subscription/TrialBadge.vue
porquinho-frontend/src/components/__tests__/TrialBadge.spec.ts
```

### Convenções

- DB `snake_case` plural, JSON `camelCase` [Source: architecture/implementation-patterns-consistency-rules.md]
- Erros via `ProblemDetail` [Source: architecture/implementation-patterns-consistency-rules.md#error-handling]
- Audit logs via `AuditLogService.log(eventType, userId, ip, metadataJson)`. No `startTrial` o IP pode ser `null` (o service não tem request) ou ser passado como parâmetro. **Preferir passar `ipAddress`**, que o `AuthService` já tem
- Construtor explícito para injeção (sem `@Autowired` em campo), como todo o código existente
- Textos de UI em pt-BR

### Testes

- Backend: JUnit 5 + Mockito (`@Mock`/`@InjectMocks`), como `AuthServiceTest`. Controllers com `TestWebSecurityConfig`. H2 `MODE=PostgreSQL` com Flyway habilitado no perfil `test`, então a V9 **precisa** rodar no H2
- Frontend: Vitest + `@vue/test-utils`. Mockar o service com `vi.mock('@/services/subscriptionService')`

### Aprendizados das stories anteriores (1.6 / 1.7)

- `@Where` em entity impede native/JPQL delete no purge (bug HIGH da 1.7) → `Subscription` sem `@Where`, delete nativo
- Usar `@Modifying(clearAutomatically = true)` em deletes
- Mudanças em LGPD (export/purge) precisam acompanhar **toda** tabela nova ligada a `user_id`. A 1.7 deixou o comentário pedindo isso
- Review 3 camadas da 1.7 achou bugs de idempotência vs. rate limit: atenção à idempotência de `startTrial`

### Git (últimos commits)

`4aaac21` Story 1.7, `337b3cc` Story 1.6, `cfb97b3` Story 1.5. Padrão de commit: `feat(<escopo>): ... (Story X.Y)`. Escopo sugerido: `feat(billing)`.

### Pesquisa técnica

Nenhuma biblioteca nova. Spring Boot 3.5 / Spring Security 6: `OncePerRequestFilter` + `addFilterAfter(..., BearerTokenAuthenticationFilter.class)` é o ponto certo para ler o `JwtAuthenticationToken` já autenticado. `ProblemDetail` é nativo do Spring 6.

### Decisões confirmadas (Luiz, 2026-10-31)

1. **Usuários Google com `users.id ≠ sub`**: o banco de dev (Supabase) foi limpo em 2026-10-31. Os dados de `users`, `audit_logs`, `user_sessions` e `data_exports` foram apagados, enquanto schema, `flyway_schema_history` (V7) e `auth.users` foram mantidos. **Não** criar migration de remapeamento de IDs. Ao relogar, o lazy sync recria o usuário. Depois da Task 0, o ID passa a ser o `sub`
2. **Backfill**: confirmado. Usuários existentes ganham 30 dias a partir da data da migration

### References

- [Source: _bmad-output/planning-artifacts/epics/epic-2-subscription-billing-system.md#Story 2.1]
- [Source: _bmad-output/planning-artifacts/prd/functional-requirements.md#FR8]
- [Source: _bmad-output/planning-artifacts/prd/saas-web-app-specific-requirements.md#6. Subscription Model & Monetization]
- [Source: _bmad-output/planning-artifacts/architecture/project-structure-boundaries.md]
- [Source: _bmad-output/planning-artifacts/architecture/implementation-patterns-consistency-rules.md]
- [Source: _bmad-output/implementation-artifacts/1-7-request-permanent-account-deletion-lgpd-compliance.md#Review Findings]
- [Source: porquinho-backend/src/main/java/com/porquinho/service/AccountDeletionService.java:205]

## Dev Agent Record

### Agent Model Used

Claude Opus 5.5 (claude-opus-5-5)

### Debug Log References

- Backend: `./mvnw test` com JDK 21 (`JAVA_HOME=~/.sdkman/candidates/java/21.0.11-tem`; o JDK padrão da máquina é 17 e não roda o projeto). 135 testes, 114 passam, 21 erros, todos em `MigrationTest`, `PorquinhoBackendApplicationTests`, `UserRepositoryTest` e `SecurityConfigTest`. Esses 4 já falhavam no baseline (19 erros antes da story): o Flyway quebra em `V6` no H2 (`PRIMARY KEY DEFAULT` e índice parcial). Os 2 erros novos são os testes da V9 em `MigrationTest`, que herdam a mesma falha.
- V9 validada à parte no H2 2.3.232 (script de scratchpad, com cópias de V6-V8 corrigidas só para o teste): a migration roda, o backfill cria 1 trial de 30 dias só para o usuário não deletado e a UNIQUE rejeita a segunda linha. Com essas cópias, o Hibernate `ddl-auto=validate` passa pela entity `Subscription`. O contexto completo ainda cai depois por problemas anteriores (`data_exports` com `TIMESTAMPTZ` x `LocalDateTime` e falta de bean `JavaMailSender` no perfil test).
- Frontend: `vitest run` 58/58, `vue-tsc --build` limpo, `eslint` limpo nos arquivos alterados.

### Completion Notes List

- V9 usa `id UUID DEFAULT gen_random_uuid() PRIMARY KEY` e `TIMESTAMP WITH TIME ZONE` no lugar do esqueleto (`PRIMARY KEY DEFAULT`, `TIMESTAMPTZ`). O H2 não aceita nenhuma das duas formas do esqueleto, e a story exige que a V9 rode no H2. No PostgreSQL o tipo é o mesmo (`timestamptz`).
- Task 0: `AuthController.registerWithGoogle` e `registerWithEmail` passaram a ler o `sub` por `Authentication.getName()`. Com `JwtAuthenticationToken` o principal é o objeto `Jwt`, então `@AuthenticationPrincipal String userId` resolvia `null` em produção (o caminho email chegaria em `UUID.fromString(null)`). `registerOrGetUserFromGoogle(userId, email, googleId, ip)` cria o usuário via `insertWithId` com `google_id`; sem `sub` lança `IllegalArgumentException`.
- `startTrial(userId, ipAddress)` recebe o IP (preferência das Dev Notes). Idempotente via `findByUserId`: retorna o registro existente sem alterar.
- `daysRemaining` arredonda para cima pelos segundos restantes (29d 1h → 30, vencido → 0).
- Export LGPD: chave `subscriptions` com campos em snake_case (`plan_type`, `status`, `trial_start_date`, `trial_end_date`, `days_remaining`, `has_access`), igual ao resto do arquivo exportado.
- O `ProblemDetail` do 402 é serializado com `ProblemDetailJacksonMixin`, o mesmo formato JSON do Spring MVC.
- Adicionado `DashboardView.spec.ts` (badge aparece com a API ok; some quando a API falha, e o dashboard continua).
- Fora do escopo, não corrigido: `@AuthenticationPrincipal String userId` também aparece em `/login` e `/password-reset/completed`. No segundo, o audit `password_reset_completed` é pulado em silêncio em produção.

### File List

Novos:
- porquinho-backend/src/main/resources/db/migration/V9__create_subscriptions_table.sql
- porquinho-backend/src/main/java/com/porquinho/entity/Subscription.java
- porquinho-backend/src/main/java/com/porquinho/repository/SubscriptionRepository.java
- porquinho-backend/src/main/java/com/porquinho/service/SubscriptionService.java
- porquinho-backend/src/main/java/com/porquinho/controller/SubscriptionController.java
- porquinho-backend/src/main/java/com/porquinho/dto/SubscriptionResponse.java
- porquinho-backend/src/main/java/com/porquinho/config/SubscriptionAccessFilter.java
- porquinho-backend/src/main/java/com/porquinho/config/ClockConfig.java
- porquinho-backend/src/test/java/com/porquinho/service/SubscriptionServiceTest.java
- porquinho-backend/src/test/java/com/porquinho/config/SubscriptionAccessFilterTest.java
- porquinho-backend/src/test/java/com/porquinho/controller/SubscriptionControllerTest.java
- porquinho-frontend/src/types/subscription.ts
- porquinho-frontend/src/services/subscriptionService.ts
- porquinho-frontend/src/components/subscription/TrialBadge.vue
- porquinho-frontend/src/components/__tests__/TrialBadge.spec.ts
- porquinho-frontend/src/views/__tests__/DashboardView.spec.ts

Modificados:
- porquinho-backend/src/main/java/com/porquinho/config/SecurityConfig.java
- porquinho-backend/src/main/java/com/porquinho/controller/AuthController.java
- porquinho-backend/src/main/java/com/porquinho/repository/UserRepository.java
- porquinho-backend/src/main/java/com/porquinho/service/AuthService.java
- porquinho-backend/src/main/java/com/porquinho/service/AccountDeletionService.java
- porquinho-backend/src/main/java/com/porquinho/service/DataExportService.java
- porquinho-backend/src/test/java/com/porquinho/MigrationTest.java
- porquinho-backend/src/test/java/com/porquinho/controller/AuthControllerTest.java
- porquinho-backend/src/test/java/com/porquinho/service/AuthServiceTest.java
- porquinho-backend/src/test/java/com/porquinho/service/AccountDeletionServiceTest.java
- porquinho-backend/src/test/java/com/porquinho/service/DataExportServiceTest.java
- porquinho-frontend/src/views/DashboardView.vue
- _bmad-output/implementation-artifacts/sprint-status.yaml

## Change Log

- 2026-10-31: Story 2.1 implementada (trial de 30 dias, filtro 402, badge, integração LGPD, ID Google = `sub`). Status → review.

## Review Triage Log

Revisão em 3 camadas (Blind Hunter, Edge Case Hunter, Verification Gap) — 2026-10-31.

| # | Origem | Achado | Veredito | Evidência | Rota |
|---|---|---|---|---|---|
| 1 | Blind, VG | Filtro 402 sem teste pela cadeia real do `SecurityConfig`; remover/inverter `addFilterAfter` não quebra nada | medium | VG pré-verificado: `SecurityConfigTest` usa `TestWebSecurityConfig`; testes de contexto já quebrados no baseline (V6–V8 no H2) | defer |
| 2 | Blind, Edge, VG | Usuários Google legados com `users.id ≠ sub` ficam sem assinatura | false | Banco de dev limpo em 2026-10-31 (users=0); não há produção | reject |
| 3 | Blind | Backfill ignora soft-deletados que cancelem a exclusão | false | Story 1.7 não tem cancelamento; soft-deletado só é purgado | reject |
| 4 | Blind | `NOT IN (SELECT ...)` redundante na V9 | low | Cosmético, pedido pela spec | reject |
| 5 | Blind | Export LGPD omite `next_billing_date`, `payment_method_id`, `created_at`, `updated_at` | medium | `DataExportService` exporta só campos do DTO; 2.2 preenche essas colunas | patch |
| 6 | Blind | `deleted_at` ignorado por `hasAccess` | low | Nada preenche a coluna (purge é hard delete) | reject |
| 7 | Blind, Edge | `daysRemaining` calcula para plano não-TRIAL | low | Correção direta de 1 linha; dado contraditório na API/export | patch |
| 8 | Blind | Mensagem do 402 fixa ("trial terminou") | low | Só TRIAL existe na 2.1 | reject |
| 9 | Blind | Frontend não trata 402 | medium | Interceptor só trata 401; sem rotas protegidas hoje | defer (Story 2.2) |
| 10 | Blind, Edge, VG | `sub` nulo/inválido → 500 no registro | low | Rota exige JWT do Supabase (sub sempre UUID) | reject |
| 11 | Blind, Edge | Corrida em `startTrial` (find + save) | false | Registro concorrente colide antes no PK de `users` (`insertWithId`) na mesma tx | reject |
| 12 | Blind | `@PrePersist` usa relógio da JVM; `@GenericGenerator` obsoleto | low | Mesmo instante em TIMESTAMPTZ; padrão do código existente | reject |
| 13 | Blind | Dashboard engole erros silenciosamente | false | AC5 exige: falha esconde o badge | reject |
| 14 | Blind | `role="status"` no badge anuncia no mount | low | Pedido pela spec (Task 8.3) | reject |
| 15 | Edge | Ramo `google_account_linked` com id de usuário email ≠ sub Google | maybe-false | Depende de o Supabase vincular identidades no mesmo auth user (mesmo sub). Checar config "link identities" | defer |
| 16 | Edge | Soft-deletado re-registra com mesmo sub → PK violation 500 | medium | Pré-existente (caminho email já colidia; email UNIQUE também) | defer |
| 17 | Edge | Badge mostra "Trial expirado" para trial CANCELLED | low | CANCELLED inalcançável para TRIAL na 2.1 | reject |
| 18 | Edge | Falha de BD no filtro → 500 cru sem ProblemDetail | low | Raro; padrão de erro genérico do Spring | reject |
| 19 | VG | `registerWithEmail` sem teste verificando sub repassado | medium | VG pré-verificado: stubs com `nullable(String.class)` | patch |
| 20 | VG | SQL nativo `insertWithId` (google_id) sem teste contra BD | medium | VG pré-verificado; `UserRepositoryTest` quebrado no baseline (V6 no H2) | defer |
| 21 | VG | Backfill da V9 nunca exercitado com dados | medium | VG pré-verificado; verificado manualmente pelo dev em H2 com cópias corrigidas; mesma infra quebrada | defer |
