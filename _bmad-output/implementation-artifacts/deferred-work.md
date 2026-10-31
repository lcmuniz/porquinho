
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

## Deferred from: code review of 2-2-subscribe-to-monthly-plan-after-trial (2026-10-31)

- source_spec: `_bmad-output/implementation-artifacts/2-2-subscribe-to-monthly-plan-after-trial.md`
  summary: `checkout.session.completed` reentregue após cancelamento/expiração pode reativar a assinatura.
  evidence: maybe-false hoje (CANCELLED/EXPIRED inalcançáveis); tratar na 2.4 com dedupe por `event.id` ou checagem de status.
- source_spec: `_bmad-output/implementation-artifacts/2-2-subscribe-to-monthly-plan-after-trial.md`
  summary: Reaproveitar `stripe_customer_id` (`setCustomer`) em vez de `customer_email` ao reassinar.
  evidence: Cada reassinatura cria Customer novo; relevante na 2.5 (reativação).
- source_spec: `_bmad-output/implementation-artifacts/2-2-subscribe-to-monthly-plan-after-trial.md`
  summary: Badge e CTA "Assinar" para MONTHLY CANCELLED/EXPIRED.
  evidence: Estados só passam a existir nas Stories 2.4–2.7.
- source_spec: `_bmad-output/implementation-artifacts/2-2-subscribe-to-monthly-plan-after-trial.md`
  summary: Teste concorrente (Testcontainers) do lock pessimista em `findByUserIdForUpdate`.
  evidence: Sem teste, remover `@Lock` passa; consequência limitada a audit/e-mail duplicados.

## Deferred from: code review of 2-3-send-trial-expiration-reminders (2026-10-31)

- source_spec: `_bmad-output/implementation-artifacts/2-3-send-trial-expiration-reminders.md`
  summary: Job de lembretes sem execução de recuperação no startup; backend fora às 9h pode perder o lembrete de 1 dia.
  evidence: Só cron 09:00 SP; query exige trial_end > now. Opção: rodar também em ApplicationReadyEvent (idempotente).
- source_spec: `_bmad-output/implementation-artifacts/2-3-send-trial-expiration-reminders.md`
  summary: Nenhum job vira status TRIAL→EXPIRED após o fim do trial.
  evidence: Funcionalmente ok (hasAccess calcula na leitura); relevante para relatórios/2.6–2.7.
- source_spec: `_bmad-output/implementation-artifacts/2-3-send-trial-expiration-reminders.md`
  summary: EmailService loga e-mail completo do destinatário (dado pessoal, LGPD).
  evidence: Padrão em todos os métodos de envio; trocar por userId ou e-mail mascarado.
