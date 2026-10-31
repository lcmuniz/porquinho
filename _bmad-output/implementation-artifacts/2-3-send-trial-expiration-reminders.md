---
title: 'Lembretes por e-mail 7 dias e 1 dia antes do fim do trial'
type: 'feature'
created: '2026-10-31'
status: 'ready-for-dev'
route: 'dispatch'
story_key: 2-3-send-trial-expiration-reminders
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-2-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** O trial acaba sem aviso; o usuário só descobre ao tomar 402, e a conversão trial→pago sofre.

**Approach:** Job diário envia e-mail "Seu trial termina em 7 dias" e "Seu trial termina amanhã" para assinaturas `TRIAL`/`ACTIVE`, com link "Assinar agora" para `{frontend}/assinatura`. Cada lembrete sai no máximo uma vez por usuário; quem assinou (MONTHLY) não recebe mais nada.

## Boundaries & Constraints

**Always:**
- Envio único por lembrete, registrado no banco (colunas novas), garantindo idempotência mesmo se o job rodar duas vezes ou após restart.
- Job tolerante a dia perdido: lembrete de 7 dias vale quando faltam ≤ 7 e > 1 dias; o de 1 dia quando falta ≤ 1 dia e o trial ainda não acabou. Dias contados em datas de calendário de São Paulo.
- Falha de e-mail de um usuário não interrompe os demais; e-mail best-effort (`EmailService`).
- Audit `trial_reminder_sent` com metadata `{"reminder":"7d"|"1d"}`.
- **Decisão (Luiz, 2026-10-31): envio às 9h de São Paulo** (`@Scheduled(cron = "0 0 9 * * *", zone = "America/Sao_Paulo")`), não 9h UTC como sugere o epic: público brasileiro, sem fuso por usuário.

**Never:**
- Cobrança, cancelamento, retry de pagamento (2.4–2.7).
- Fuso por usuário (não há coluna de timezone).
- Lock distribuído (ShedLock etc.): backend roda em instância única.

## I/O & Edge-Case Matrix

| Cenário | Estado | Resultado | Erro |
|---|---|---|---|
| Faltam 7 dias | TRIAL/ACTIVE, nada enviado | e-mail 7d, marca enviado, audit | — |
| Faltam 5 dias (job falhou antes) | 7d não enviado | envia 7d | — |
| Faltam 7 dias, já enviado | 7d marcado | nada | — |
| Falta 1 dia | 1d não enviado | e-mail 1d | — |
| Falta 1 dia, 7d nunca enviado | nenhum enviado | só o 1d (não manda os dois) | — |
| Já assinou | MONTHLY | nada | — |
| Trial vencido | trial_end < agora | nada | — |
| SMTP falha p/ um usuário | exceção no envio (após commit) | segue para os próximos; lembrete fica marcado (não reenvia) | log warn |

</frozen-after-approval>

## Code Map

- `porquinho-backend/src/main/resources/db/migration/V11__add_trial_reminder_tracking.sql` -- NOVO: `trial_reminder_7d_sent_at`, `trial_reminder_1d_sent_at` (TIMESTAMPTZ) em `subscriptions`. V1–V10 aplicadas no Supabase: não editar
- `.../entity/Subscription.java` -- adicionar os dois campos (padrão `OffsetDateTime` + getters/setters)
- `.../repository/SubscriptionRepository.java` -- query das candidatas: `plan_type='TRIAL' AND status='ACTIVE' AND trial_end_date > now AND trial_end_date <= now+8d AND (1d não enviado)`; filtro fino no serviço
- `.../service/SubscriptionService.java` -- `Clock` injetado (`ClockConfig`) e padrão `afterCommit` (linha ~236) para e-mail pós-commit: reutilizar
- `.../service/AccountDeletionService.java:222` -- exemplo de `@Scheduled(cron, zone)` + processamento por item com `@Transactional(REQUIRES_NEW)` via self-proxy `@Lazy`: seguir o padrão
- `.../config/AsyncConfig.java` -- `@EnableScheduling` já ativo
- `.../service/EmailService.java` -- `newMessage(to, subject)` com From; datas em `America/Sao_Paulo`; adicionar `sendTrialReminder(email, daysLeft, trialEndDate, subscribeUrl)`
- `.../service/StripeBillingService.java:51` -- `app.frontend-url` já existe: reutilizar para o link
- `.../repository/UserRepository.java` -- e-mail do usuário (`findById`)

## Tasks & Acceptance

- [ ] `V11__add_trial_reminder_tracking.sql` + `Subscription.java` -- colunas e campos
- [ ] `SubscriptionRepository.java` -- query de candidatas
- [ ] `service/TrialReminderService.java` -- `@Scheduled` diário; para cada candidata, em transação própria: calcula dias restantes (datas SP), decide 7d/1d pelas regras, marca `*_sent_at`, audit, e-mail após commit
- [ ] `EmailService.java` -- `sendTrialReminder`: assunto "Porquinho - Seu trial termina em 7 dias" / "Porquinho - Seu trial termina amanhã", data de fim, benefícios curtos, link `{frontend}/assinatura`
- [ ] Testes: unit do serviço cobrindo toda a matriz (Clock fixo); `EmailServiceTest` dos dois textos; `MigrationTest` das colunas novas

**AC:**
- Dado um trial terminando em 7 dias, quando o job roda, então chega 1 e-mail com link para `/assinatura`, e rodar o job de novo não envia outro.

## Spec Change Log

## Review Triage Log

## Design Notes

"Faltam N dias" = `ChronoUnit.DAYS.between(hoje_SP, data_fim_SP)`. Ex.: trial termina 30/11 10h SP; em 23/11 → 7 (envia 7d); em 29/11 → 1 (envia 1d).

Marcar `*_sent_at` antes do envio (mesma transação) e enviar após commit: evita duplicata; se o SMTP falhar, o lembrete é perdido (aceitável, best-effort) — log warn.

## Verification

**Commands:**
- `cd porquinho-backend && ./mvnw test` -- 0 falhas
