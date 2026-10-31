
## Deferred from: code review of 1-7-request-permanent-account-deletion-lgpd-compliance (2026-05-15)

- @Transactional(propagation=NEVER) em requestDeletion — defesa futura caso codigo externo tente envolver a chamada em outra transacao. Atualmente o controller nao envolve, entao nao ha risco real.
- JWT blacklist TTL pode ser 0 ou negativo se token esta no ultimo segundo de validade — janela de exposicao <1min, atacante precisaria de timing perfeito.
- DataExport.findAllByUserId sem stream/paginacao — risco teorico de OOM se retencao for aumentada para anos. Atualmente 24h de retencao mantem lista pequena.
- Rate limit so por userId permite que um atacante com credencials valida bloqueie o usuario legitimo por 24h. Mitigacao real exige rate limit adicional por IP — fora do escopo do MVP.

## Deferred from: code review of 2-1-automatically-activate-30-day-free-trial (2026-10-31)

- source_spec: `_bmad-output/implementation-artifacts/2-1-automatically-activate-30-day-free-trial.md`
  summary: Testes de contexto Spring quebrados no H2 — V6/V7/V8 usam `PRIMARY KEY DEFAULT`, `TIMESTAMPTZ` e índice parcial; `data_exports` TIMESTAMPTZ vs `LocalDateTime`; perfil test sem `JavaMailSender`.
  evidence: 19 erros já no baseline 7c989a1 (MigrationTest, UserRepositoryTest, SecurityConfigTest, PorquinhoBackendApplicationTests). Bloqueia testes de integração da 2.1 (filtro 402 na cadeia real, `insertWithId` com google_id, backfill V9). Corrigir sem alterar checksum de migrations aplicadas (ex.: migrations H2-específicas via `flyway.locations` no perfil test, ou Testcontainers PostgreSQL).
- source_spec: `_bmad-output/implementation-artifacts/2-1-automatically-activate-30-day-free-trial.md`
  summary: Frontend não trata 402 (interceptor em `services/api.ts` só trata 401) nem oferece CTA no badge "Trial expirado".
  evidence: Natural na Story 2.2 (tela de assinatura); hoje nenhuma rota retorna 402.
- source_spec: `_bmad-output/implementation-artifacts/2-1-automatically-activate-30-day-free-trial.md`
  summary: Login Google de usuário que já existe por email com id diferente do sub Google mantém o id do usuário email.
  evidence: maybe-false — se o Supabase vincula identidades do mesmo email no mesmo auth user, o sub é igual e não há problema. Verificar configuração de identity linking no projeto Supabase.
- source_spec: `_bmad-output/implementation-artifacts/2-1-automatically-activate-30-day-free-trial.md`
  summary: Usuário soft-deletado (aguardando purge) que re-registra com o mesmo sub recebe 500 (violação de PK em `insertWithId`) em vez de erro claro.
  evidence: Pré-existente no caminho email; `findById` filtra `deleted_at` via `@Where`. Retornar 409/410 usando `findByIdIncludingDeleted`.
- source_spec: `_bmad-output/implementation-artifacts/2-1-automatically-activate-30-day-free-trial.md`
  summary: [RESOLVIDO 2026-10-31] `/login` e `/password-reset/completed` ainda usam `@AuthenticationPrincipal String`, que resolve null com `JwtAuthenticationToken`.
  evidence: Em produção o audit `password_reset_completed` é silenciosamente pulado. Trocar por `Authentication.getName()` como feito nos endpoints de registro.
