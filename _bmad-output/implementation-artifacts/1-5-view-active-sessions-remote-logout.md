# Story 1.5: Visualizar Sessoes Ativas e Logout Remoto

Status: done
Code Review: ✅ Approved (2026-03-26)

## Story

Como um usuario logado,
Eu quero visualizar todas as minhas sessoes ativas e realizar logout remoto,
Para que eu possa proteger minha conta caso suspeite de acesso nao autorizado.

## Criterios de Aceitacao (BDD)

### AC1: Listar sessoes ativas
**Dado** que sou um usuario autenticado
**Quando** navego para Configuracoes da Conta > aba "Sessoes"
**Entao** vejo uma lista de todas as sessoes ativas mostrando: dispositivo/navegador, endereco IP, horario da ultima atividade
**E** a sessao atual esta marcada com badge "Dispositivo atual"

### AC2: Logout remoto de sessao individual
**Dado** que estou visualizando minhas sessoes ativas
**Quando** clico em "Encerrar" em uma sessao que nao e a atual
**Entao** um dialog de confirmacao e exibido
**E** apos confirmar, a sessao e invalidada (JWT adicionado a blacklist Redis)
**E** o dispositivo remoto e deslogado imediatamente
**E** um registro de auditoria "remote_logout" e criado

### AC3: Logout de todas as outras sessoes
**Dado** que estou visualizando minhas sessoes ativas
**Quando** clico em "Encerrar todas as outras sessoes"
**Entao** um dialog de confirmacao e exibido
**E** apos confirmar, todas as sessoes exceto a atual sao invalidadas
**E** registros de auditoria sao criados para cada sessao encerrada

### AC4: Sessoes expiradas nao aparecem
**Dado** que uma sessao tem mais de 7 dias de inatividade (NFR16)
**Quando** a lista de sessoes e carregada
**Entao** sessoes expiradas nao aparecem na lista
**E** sao marcadas como expiradas no banco de dados

### AC5: Registro de sessao no login
**Dado** que um usuario faz login com sucesso (Google OAuth ou email/senha)
**Quando** o login e completado
**Entao** um registro e criado na tabela `user_sessions` com device_info, ip_address, created_at, last_activity, expires_at

## Tasks / Subtasks

- [x] Task 1: Migracao Flyway V6 - Tabela user_sessions (AC: #1, #5)
  - [x] 1.1 Criar `V6__create_user_sessions_table.sql` com campos: id (UUID PK), user_id (FK), jwt_token_hash (VARCHAR 64), device_info (VARCHAR 500), ip_address (VARCHAR 45), created_at, last_activity, expires_at, revoked_at
  - [x] 1.2 Criar indices: `idx_user_sessions_user_id`, `idx_user_sessions_jwt_token_hash`, `idx_user_sessions_expires_at`

- [x] Task 2: Backend - Entity e Repository (AC: #1, #5)
  - [x]2.1 Criar `entity/UserSession.java` com JPA annotations, @Where(clause="revoked_at IS NULL")
  - [x]2.2 Criar `repository/UserSessionRepository.java` com queries: findByUserIdAndRevokedAtIsNull, findByJwtTokenHash, revokeAllByUserIdExceptSessionId

- [x] Task 3: Backend - DTOs (AC: #1)
  - [x]3.1 Criar `dto/response/SessionResponse.java` com: sessionId, deviceInfo, ipAddress, createdAt, lastActivity, isCurrent (boolean)
  - [x]3.2 Metodo estatico `fromEntity(UserSession, String currentSessionId)` para converter

- [x] Task 4: Backend - SessionService (AC: #1, #2, #3, #4, #5)
  - [x]4.1 Criar `service/SessionService.java`
  - [x]4.2 `createSession(userId, jwtTokenHash, deviceInfo, ipAddress)` - registra nova sessao
  - [x]4.3 `getActiveSessions(userId)` - retorna sessoes nao-revogadas e nao-expiradas
  - [x]4.4 `revokeSession(sessionId, userId)` - revoga sessao individual + adiciona JWT a blacklist Redis
  - [x]4.5 `revokeAllOtherSessions(userId, currentSessionId)` - revoga todas exceto atual
  - [x]4.6 `cleanExpiredSessions()` - marca sessoes expiradas (>7 dias inatividade)
  - [x]4.7 `updateLastActivity(jwtTokenHash)` - atualiza last_activity da sessao

- [x] Task 5: Backend - Redis JWT Blacklist (AC: #2, #3)
  - [x]5.1 Adicionar metodo em `RedisConfig` ou criar `JwtBlacklistService` para armazenar tokens revogados
  - [x]5.2 Key pattern: `jwt:blacklist:{tokenHash}` com TTL = tempo restante do JWT
  - [x]5.3 Integrar verificacao de blacklist no filtro JWT (SecurityConfig/JwtAuthenticationConverter)

- [x] Task 6: Backend - Endpoints no AuthController (AC: #1, #2, #3)
  - [x]6.1 `GET /api/v1/auth/sessions` - lista sessoes ativas do usuario autenticado
  - [x]6.2 `DELETE /api/v1/auth/sessions/{sessionId}` - revoga sessao individual (204 No Content)
  - [x]6.3 `DELETE /api/v1/auth/sessions` - revoga todas exceto atual (204 No Content)
  - [x]6.4 Rate limiting nos endpoints via RateLimitService
  - [x]6.5 Audit logging via AuditLogService para cada revogacao

- [x] Task 7: Backend - Integrar registro de sessao no login existente (AC: #5)
  - [x]7.1 Modificar AuthController/AuthService: apos login bem-sucedido, chamar SessionService.createSession()
  - [x]7.2 Extrair device_info do header User-Agent
  - [x]7.3 Extrair ip_address de X-Forwarded-For (fallback getRemoteAddr)
  - [x]7.4 Gerar jwt_token_hash com SHA-256 do token

- [x] Task 8: Frontend - Tipos e Servico (AC: #1)
  - [x]8.1 Criar `types/session.ts` com interface Session
  - [x]8.2 Criar `services/sessionService.ts` com: getSessions(), revokeSession(id), revokeAllOther()

- [x] Task 9: Frontend - Componente SessionList (AC: #1, #2, #3)
  - [x]9.1 Criar `views/SessionsView.vue` usando Card, Button, Badge, Dialog (shadcn-vue)
  - [x]9.2 Listar sessoes com device info, IP, ultima atividade
  - [x]9.3 Badge "Dispositivo atual" na sessao corrente
  - [x]9.4 Botao "Encerrar" por sessao com Dialog de confirmacao
  - [x]9.5 Botao "Encerrar todas as outras" com Dialog de confirmacao
  - [x]9.6 Estados: loading spinner, lista vazia, erro
  - [x]9.7 Responsivo: desktop (cards grid), tablet/mobile (cards stack)

- [x] Task 10: Frontend - Rota e Navegacao (AC: #1)
  - [x]10.1 Adicionar rota `/auth/sessions` no router (requer autenticacao)
  - [x]10.2 Adicionar link "Sessoes" no menu/configuracoes do usuario

- [x] Task 11: Testes Backend (AC: todos)
  - [x]11.1 `SessionServiceTest.java` - testes unitarios com Mockito
  - [x]11.2 `AuthControllerSessionTest.java` - @WebMvcTest para endpoints de sessao
  - [x]11.3 Testar rate limiting e audit logging

- [x] Task 12: Testes Frontend (AC: #1, #2, #3)
  - [x]12.1 Testes unitarios Vitest para SessionsView
  - [x]12.2 Testar renderizacao da lista, badge "atual", dialogs de confirmacao

## Dev Notes

### Arquitetura Hibrida Supabase + Backend

**CRITICO**: O projeto usa Supabase Auth como autoridade de autenticacao. O backend Spring Boot valida JWTs via JWKS endpoint e fornece funcionalidades adicionais (auditoria, rate limiting, locking). Para gerenciamento de sessoes:

- Supabase NAO oferece API para listar/revogar sessoes individuais nativamente
- Portanto, o backend DEVE manter sua propria tabela `user_sessions` para rastrear sessoes
- A revogacao funciona via **blacklist Redis** do JWT hash (o token em si continua valido no Supabase, mas o backend rejeita requests com token na blacklist)
- O filtro JWT existente DEVE ser modificado para verificar a blacklist antes de aceitar o token

### Padroes Estabelecidos nas Stories Anteriores

**Story 1.3 (Login):**
- 3 endpoints de login: `/login/check` (pre-auth), `/login/failed` (falha), `/login` (sucesso)
- RateLimitService com sliding window Redis: `allowRequest(key, maxAttempts, windowSeconds)`
- AccountLockService: rastreia falhas na tabela users, bloqueia 15min apos 5 falhas
- Delay de 1s em erros para prevenir timing attacks

**Story 1.4 (Recuperacao de Senha):**
- Supabase gerencia tokens de reset, emails, validacao
- Backend fornece endpoints audit-only
- `AuditLogService.logAnonymous()` para operacoes sem user_id
- Correcao OAuth callback: accessToken passado diretamente (race condition localStorage)

### Padroes de Codigo a Seguir

**Backend:**
- Controller: constructor injection, `@AuthenticationPrincipal String userId`, `HttpServletRequest` para IP
- Service: `@Service`, `@Transactional`, logica de negocio separada do controller
- Repository: `JpaRepository<Entity, UUID>`, queries filtram por userId e revokedAt/deletedAt IS NULL
- Entity: `@Entity`, `@Table(name="...")`, `@Where(clause="revoked_at IS NULL")`, `@PrePersist`/`@PreUpdate`
- DTO Response: `SessionResponse.fromEntity()` static factory
- Excecoes: customs tratadas por GlobalExceptionHandler (RFC 7807 ProblemDetail)
- Auditoria: `AuditLogService.log(eventType, userId, ipAddress)` no controller
- IP: extrair de `X-Forwarded-For` header, fallback `request.getRemoteAddr()`

**Frontend:**
- Composition API: `<script setup lang="ts">` exclusivamente
- Store Pinia: `defineStore('name', () => { ... })` com `ref`, `computed`
- Loading states: `isLoading`, `isSaving` booleans
- API calls: via `api` (autenticado) de `services/api.ts`
- Tratamento de erros: try/catch/finally com mensagens visuais (SEM alert())
- Componentes shadcn-vue: Card, Button, Badge, Dialog

### Estrutura do Projeto

**Backend - Novos arquivos:**
- `porquinho-backend/src/main/resources/db/migration/V6__create_user_sessions_table.sql`
- `porquinho-backend/src/main/java/com/porquinho/entity/UserSession.java`
- `porquinho-backend/src/main/java/com/porquinho/repository/UserSessionRepository.java`
- `porquinho-backend/src/main/java/com/porquinho/service/SessionService.java`
- `porquinho-backend/src/main/java/com/porquinho/service/JwtBlacklistService.java`
- `porquinho-backend/src/main/java/com/porquinho/dto/SessionResponse.java`

**Backend - Arquivos a modificar:**
- `porquinho-backend/src/main/java/com/porquinho/controller/AuthController.java` (adicionar endpoints de sessao)
- `porquinho-backend/src/main/java/com/porquinho/config/SecurityConfig.java` (permitir novos endpoints, adicionar blacklist check)
- `porquinho-backend/src/main/java/com/porquinho/config/JwtAuthenticationConverter.java` (verificar blacklist)

**Frontend - Novos arquivos:**
- `porquinho-frontend/src/types/session.ts`
- `porquinho-frontend/src/services/sessionService.ts`
- `porquinho-frontend/src/views/SessionsView.vue`

**Frontend - Arquivos a modificar:**
- `porquinho-frontend/src/router/index.ts` (adicionar rota /auth/sessions)
- `porquinho-frontend/src/services/api.ts` (interceptor deve lidar com 401 de token blacklisted)

### Convencoes de Nomes

- **Tabela DB**: `user_sessions` (snake_case, plural)
- **Colunas**: `jwt_token_hash`, `device_info`, `ip_address`, `last_activity`, `revoked_at` (snake_case)
- **Indices**: `idx_user_sessions_user_id`, `idx_user_sessions_jwt_token_hash`
- **Endpoint API**: `/api/v1/auth/sessions` (plural, sob auth prefix existente)
- **JSON response**: camelCase (sessionId, deviceInfo, ipAddress, lastActivity, isCurrent)
- **Java**: PascalCase classes (UserSession, SessionService, SessionResponse)
- **Vue**: PascalCase componentes (SessionsView.vue)
- **Data format**: ISO 8601 `"2026-03-13T10:30:00.000Z"`

### Requisitos de Seguranca

- **Todas as queries DEVEM filtrar por user_id do JWT** - nunca expor sessoes de outros usuarios
- **Rate limiting** nos endpoints de sessao via RateLimitService (sugestao: 10 requests/minuto)
- **Audit logging** para todas as operacoes de revogacao
- **CSRF**: estado muda via DELETE (protecao CSRF aplicada automaticamente pelo Spring Security)
- **Token hash**: armazenar SHA-256 do JWT, NUNCA o token raw
- **Redis blacklist TTL**: tempo restante ate expiracao do JWT (evita acumulo infinito)

### Requisitos de UX/Design

- **Cores**: Purple 600 (#9333EA) primario, Gray 50 (#FAFAFA) fundo de cards
- **Tipografia**: Inter, semibold para titulos, regular para corpo
- **Espacamento**: p-6 para cards, gap-4 entre cards, rounded-lg (12px) bordas
- **Badge**: cor verde/destaque para "Dispositivo atual"
- **Dialog de confirmacao**: Dialog shadcn-vue com titulo, descricao, botoes Cancelar/Confirmar
- **Acessibilidade**: WCAG 2.1 AA, contraste 4.5:1, teclado navegavel, ARIA roles, touch targets 44x44px
- **Responsivo**: Desktop (>=1280px) grid, Tablet (768-1279px) stack com p-6, Mobile (<768px) stack com p-4
- **Mensagens em portugues simples** sem jargao tecnico (NFR51)

### Migracao SQL Referencia

```sql
-- V6__create_user_sessions_table.sql
CREATE TABLE user_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    jwt_token_hash VARCHAR(64) NOT NULL,
    device_info VARCHAR(500),
    ip_address VARCHAR(45),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_activity TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_user_sessions_user_id ON user_sessions(user_id);
CREATE INDEX idx_user_sessions_jwt_token_hash ON user_sessions(jwt_token_hash);
CREATE INDEX idx_user_sessions_expires_at ON user_sessions(expires_at) WHERE revoked_at IS NULL;
```

### API Endpoints Referencia

| Metodo | Path | Descricao | Auth | Response |
|--------|------|-----------|------|----------|
| GET | `/api/v1/auth/sessions` | Lista sessoes ativas | JWT | 200 + SessionResponse[] |
| DELETE | `/api/v1/auth/sessions/{sessionId}` | Revoga sessao individual | JWT | 204 No Content |
| DELETE | `/api/v1/auth/sessions` | Revoga todas exceto atual | JWT | 204 No Content |

Header necessario para identificar sessao atual: `Authorization: Bearer {jwt}` - extrair hash do JWT para encontrar a sessao corrente.

### Testes Existentes (16 testes passando)

Testes existentes usam `@WebMvcTest` com mocked services. SecurityConfig tem `@Profile("!test")` para testes usarem config separada. Seguir o mesmo padrao para novos testes.

### Decisoes Arquiteturais Importantes

1. **Soft delete**: Sessoes revogadas NAO sao deletadas - `revoked_at` timestamp e preenchido (LGPD compliance, Story 1.6 precisa exportar dados de sessao)
2. **Expiracao 7 dias**: NFR16 - `expires_at = created_at + 7 days`, sessoes expiradas filtradas na query
3. **Blacklist Redis**: JWTs revogados adicionados ao Redis com TTL ate expiracao natural - filtro JWT verifica blacklist antes de aceitar token
4. **Device info via User-Agent**: Extrair browser e OS do header User-Agent (usar biblioteca ou regex simples)
5. **Sem localizacao por IP**: Epic menciona "localizacao aproximada" mas isso requer servico de GeoIP externo - implementar apenas se existir servico ja configurado, caso contrario mostrar apenas o IP

### References

- [Source: _bmad-output/planning-artifacts/epics/epic-1-user-authentication-account-management.md#Story-1.5]
- [Source: _bmad-output/planning-artifacts/prd/functional-requirements.md#FR5]
- [Source: _bmad-output/planning-artifacts/prd/non-functional-requirements.md#NFR14,NFR16,NFR18]
- [Source: _bmad-output/planning-artifacts/architecture/core-architectural-decisions.md]
- [Source: _bmad-output/planning-artifacts/architecture/implementation-patterns-consistency-rules.md]
- [Source: _bmad-output/planning-artifacts/architecture/project-structure-boundaries.md]
- [Source: _bmad-output/planning-artifacts/ux-design-specification/design-system-foundation.md]
- [Source: _bmad-output/implementation-artifacts/1-4-password-recovery-via-email.md]
- [Source: _bmad-output/implementation-artifacts/1-3-user-login-with-any-configured-method.md]

## Dev Agent Record

### Agent Model Used
Claude Opus 4.6 (1M context)

### Debug Log References
- Backend tests: 33 passed (SessionServiceTest 9, JwtBlacklistServiceTest 4, AuthControllerSessionTest 4, AuthControllerTest 16)
- Frontend tests: 36 passed (SessionsView.spec.ts 8, + 28 existing)
- No regressions detected

### Completion Notes List
- Task 1: Criada migracao V6 com tabela user_sessions, 3 indices (user_id, jwt_token_hash, expires_at parcial)
- Task 2: Criados UserSession entity (JPA) e UserSessionRepository com queries customizadas
- Task 3: Criado SessionResponse DTO com factory method fromEntity
- Task 4: Criado SessionService com createSession, getActiveSessions, revokeSession, revokeAllOtherSessions, updateLastActivity, hashToken
- Task 5: Criado JwtBlacklistService (Redis key pattern jwt:blacklist:{hash} com TTL) e integrado no JwtAuthenticationConverter
- Task 6: Adicionados 3 endpoints REST: GET/DELETE /sessions, DELETE /sessions/{id} com rate limiting e audit logging
- Task 7: Integrado registro de sessao no endpoint /login existente (extrai JWT do header, User-Agent, IP)
- Task 8: Criados types/session.ts e services/sessionService.ts no frontend
- Task 9: Criado SessionsView.vue com lista de sessoes, badges, dialogs de confirmacao, estados loading/erro/vazio, responsivo
- Task 10: Adicionada rota /auth/sessions no router (protegida)
- Task 11: Criados SessionServiceTest (9 testes), JwtBlacklistServiceTest (4 testes), AuthControllerSessionTest (4 testes)
- Task 12: Criados testes Vitest para SessionsView (8 testes)

### Change Log
- 2026-03-24: Implementacao completa da Story 1.5 - sessoes ativas e logout remoto
- 2026-03-24: Code review fixes - cleanExpiredSessions implementado, @Where adicionado, testes reescritos, parseDeviceInfo corrigido, link de navegacao adicionado, api.ts atualizado para 401 de token revogado
- 2026-03-26: Code review pass 2 - SecurityConfig explicit matcher para /auth/sessions/**, removido api.ts da File List (interceptor 401 ja existia da Story 1.4)

### File List
**Novos arquivos:**
- porquinho-backend/src/main/resources/db/migration/V6__create_user_sessions_table.sql
- porquinho-backend/src/main/java/com/porquinho/entity/UserSession.java
- porquinho-backend/src/main/java/com/porquinho/repository/UserSessionRepository.java
- porquinho-backend/src/main/java/com/porquinho/service/SessionService.java
- porquinho-backend/src/main/java/com/porquinho/service/JwtBlacklistService.java
- porquinho-backend/src/main/java/com/porquinho/dto/SessionResponse.java
- porquinho-backend/src/test/java/com/porquinho/service/SessionServiceTest.java
- porquinho-backend/src/test/java/com/porquinho/service/JwtBlacklistServiceTest.java
- porquinho-backend/src/test/java/com/porquinho/controller/AuthControllerSessionTest.java
- porquinho-frontend/src/types/session.ts
- porquinho-frontend/src/services/sessionService.ts
- porquinho-frontend/src/views/SessionsView.vue
- porquinho-frontend/src/views/__tests__/SessionsView.spec.ts

**Arquivos modificados:**
- porquinho-backend/src/main/java/com/porquinho/controller/AuthController.java
- porquinho-backend/src/main/java/com/porquinho/config/SecurityConfig.java
- porquinho-backend/src/main/java/com/porquinho/config/JwtAuthenticationConverter.java
- porquinho-backend/src/test/java/com/porquinho/controller/AuthControllerTest.java
- porquinho-frontend/src/router/index.ts
- porquinho-frontend/src/views/DashboardView.vue
