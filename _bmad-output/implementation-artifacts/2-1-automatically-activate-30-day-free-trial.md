---
status: ready-for-dev
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

- [ ] **Task 0: Corrigir ID do usuário Google = `sub` do Supabase (pré-requisito do AC4)** (AC: 2, 4)
  - [ ] 0.1 Hoje `AuthService.registerOrGetUserFromGoogle` cria `new User(...)` com UUID **gerado**, enquanto o caminho email usa `insertWithId` com o `sub` do JWT. `DataExportController`, `AccountDeletionController` e o novo filtro usam `jwt.getSubject()` como `users.id` → para usuários Google o lookup **não encontra ninguém**, e todo usuário Google levaria 402
  - [ ] 0.2 Passar `userId` (do `@AuthenticationPrincipal`) para `registerOrGetUserFromGoogle` e criar o usuário com `insertWithId`
  - [ ] 0.3 Estender `UserRepository.insertWithId` com o parâmetro `googleId` (a constraint `users_google_id_check` exige `google_id` NOT NULL para `GOOGLE`). Atualizar a chamada do caminho email passando `null`
  - [ ] 0.4 Atualizar `AuthServiceTest` / `AuthControllerTest`
  - [ ] 0.5 Usuários Google já existentes com ID divergente: **não migrar IDs**. O banco de dev já foi limpo (ver Decisões confirmadas)
- [ ] **Task 1: Migration V9** (AC: 1)
  - [ ] 1.1 Criar `porquinho-backend/src/main/resources/db/migration/V9__create_subscriptions_table.sql` (esqueleto em Dev Notes)
  - [ ] 1.2 Backfill: `INSERT INTO subscriptions (...) SELECT ... FROM users WHERE deleted_at IS NULL AND id NOT IN (SELECT user_id FROM subscriptions)`
  - [ ] 1.3 **Sem índice parcial (`WHERE`)**: H2 não suporta (ver comentário em V2). `UNIQUE (user_id)` já cria o índice
  - [ ] 1.4 Adicionar teste da V9 em `MigrationTest` (tabela existe e constraint UNIQUE em `user_id`)
- [ ] **Task 2: Entity + Repository** (AC: 1, 2)
  - [ ] 2.1 `entity/Subscription.java` com enums internos `PlanType { TRIAL, MONTHLY }` e `Status { ACTIVE, CANCELLED, EXPIRED }`, `@Enumerated(EnumType.STRING)`, datas `OffsetDateTime`, `@PrePersist`/`@PreUpdate` como em `UserSession`
  - [ ] 2.2 **Sem `@Where`**: na Story 1.7, `@Where` quebrou o hard-delete
  - [ ] 2.3 `repository/SubscriptionRepository.java`: `Optional<Subscription> findByUserId(UUID)`, `existsByUserId(UUID)`, `deleteByUserId(UUID)` (`@Modifying(clearAutomatically = true)` + `@Query` native, mesmo padrão de `DataExportRepository.deleteByUserId`)
- [ ] **Task 3: `SubscriptionService`** (AC: 2, 3, 4)
  - [ ] 3.1 `startTrial(UUID userId)`: idempotente (se `existsByUserId`, retorna o existente sem alterar). Cria TRIAL/ACTIVE, start = `now(clock)`, end = start + 30 dias. Audit `trial_started` com metadata `{"trial_end_date":"..."}`
  - [ ] 3.2 `Optional<Subscription> findByUserId(UUID)`
  - [ ] 3.3 `boolean hasAccess(UUID userId)`: `status == ACTIVE && (planType != TRIAL || now < trialEndDate)`. Sem assinatura → `false`
  - [ ] 3.4 `long daysRemaining(Subscription)`: `ceil` das horas restantes / 24, mínimo 0 (29d 1h → 30; vencido → 0)
  - [ ] 3.5 Injetar `java.time.Clock` (bean `Clock.systemUTC()` em `config/ClockConfig.java`) para testar datas sem `sleep`
  - [ ] 3.6 Constante `TRIAL_DAYS = 30` (sem config externa, o valor não muda por ambiente)
- [ ] **Task 4: Integrar no cadastro** (AC: 2)
  - [ ] 4.1 Em `AuthService`, nos dois ramos de **criação** (Google e email), chamar `subscriptionService.startTrial(id)` logo após salvar o usuário e antes do audit `user_registration`. Fica dentro do `@Transactional` existente: se o trial falhar, o cadastro faz rollback
  - [ ] 4.2 **Não** chamar nos ramos idempotentes nem nos de vinculação de conta (`google_account_linked`, `auth_method_linked`)
- [ ] **Task 5: Endpoint `GET /api/v1/subscriptions/me`** (AC: 5)
  - [ ] 5.1 `controller/SubscriptionController.java` em `@RequestMapping("/api/v1/subscriptions")`, `@AuthenticationPrincipal Jwt jwt` + `UUID.fromString(jwt.getSubject())` (padrão do `DataExportController`)
  - [ ] 5.2 `dto/SubscriptionResponse.java`: `planType`, `status`, `trialStartDate`, `trialEndDate`, `daysRemaining`, `hasAccess` (JSON camelCase)
  - [ ] 5.3 Sem assinatura → `ResourceNotFoundException` (o `GlobalExceptionHandler` já mapeia para 404)
- [ ] **Task 6: Filtro de acesso por assinatura** (AC: 4)
  - [ ] 6.1 `config/SubscriptionAccessFilter.java` estende `OncePerRequestFilter`. **NÃO anotar com `@Component`**: o Spring Boot registraria o filtro como servlet filter global, inclusive no perfil `test` (o `SecurityConfig` é `@Profile("!test")`)
  - [ ] 6.2 Registrar em `SecurityConfig.securityFilterChain` com `.addFilterAfter(new SubscriptionAccessFilter(subscriptionService), BearerTokenAuthenticationFilter.class)`
  - [ ] 6.3 `shouldNotFilter`: true se o path não começa com `/api/v1/` ou começa com um prefixo isento (ver Dev Notes), ou se não há `JwtAuthenticationToken` no `SecurityContextHolder`
  - [ ] 6.4 Sem acesso → status 402, `Content-Type: application/problem+json`, corpo `ProblemDetail` (title "Assinatura necessária", detail "Seu período de trial terminou. Assine para continuar.") serializado via `ObjectMapper`
  - [ ] 6.5 `sub` inválido (não UUID) → deixa passar. A autenticação já validou o token e o controller trata o caso
- [ ] **Task 7: LGPD** (AC: 6)
  - [ ] 7.1 `AccountDeletionService.purgeUser`: `subscriptionRepository.deleteByUserId(userId)` **antes** de `userRepository.deleteByIdNative`. Manter o TODO da linha ~205 (`cancelImmediately`), que pertence à Story 2.4 (Stripe)
  - [ ] 7.2 `DataExportService.collectUserData`: adicionar a chave `subscriptions` (lista com 0..1 item, campos do DTO) e incluir `"subscriptions"` em `exported_tables`
  - [ ] 7.3 Atualizar `AccountDeletionServiceTest` e `DataExportServiceTest` (e o construtor com mocks)
- [ ] **Task 8: Frontend: tipos, service, badge** (AC: 5)
  - [ ] 8.1 `src/types/subscription.ts`: `interface Subscription { planType: 'TRIAL' | 'MONTHLY'; status: 'ACTIVE' | 'CANCELLED' | 'EXPIRED'; trialStartDate: string | null; trialEndDate: string | null; daysRemaining: number; hasAccess: boolean }`
  - [ ] 8.2 `src/services/subscriptionService.ts`: `getMySubscription()` usando `api` de `./api` (padrão de `sessionService.ts`)
  - [ ] 8.3 `src/components/subscription/TrialBadge.vue`: recebe `subscription` via prop, renderiza nada se não for TRIAL. Pill `rounded-full` (UX: badges = pills), `role="status"`, contraste WCAG AA
  - [ ] 8.4 `DashboardView.vue`: busca no `onMounted`, guarda em `ref`, `try/catch` silencioso (AC5: falha esconde o badge). Badge ao lado do `<h1>`
  - [ ] 8.5 `src/components/__tests__/TrialBadge.spec.ts`: 30 dias, 1 dia (singular), expirado e não-trial (não renderiza)
- [ ] **Task 9: Testes backend** (AC: todos)
  - [ ] 9.1 `SubscriptionServiceTest` (Mockito + `Clock.fixed`): criação, idempotência, `hasAccess` antes/depois do fim, sem assinatura, `daysRemaining` (borda 29d1h → 30, vencido → 0)
  - [ ] 9.2 `SubscriptionAccessFilterTest`: 402 para rota protegida sem acesso; passa em rotas isentas; passa com acesso; passa sem autenticação
  - [ ] 9.3 `SubscriptionControllerTest`: 200 com corpo, 404 sem assinatura
  - [ ] 9.4 `AuthServiceTest`: `startTrial` chamado **só** quando `isNewUser`
  - [ ] 9.5 Rodar a suíte completa backend (`./mvnw test`) e frontend (`npm run test:unit`, `npm run type-check`). Zero regressões

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

### Debug Log References

### Completion Notes List

- Ultimate context engine analysis completed - comprehensive developer guide created

### File List
