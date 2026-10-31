---
title: 'Cancelamento self-service da assinatura mensal'
type: 'feature'
created: '2026-10-31'
status: 'done'
baseline_commit: 615d2e463bc835d901493e716e9dba61b4e13f66
route: 'dispatch'
story_key: 2-4-self-service-subscription-cancellation
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-2-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Quem assina o plano mensal não tem como parar a cobrança recorrente sozinho.

**Approach:** Botão "Cancelar assinatura" em `/assinatura` com modal de confirmação. O backend pede ao Stripe o cancelamento **no fim do período** (`cancel_at_period_end=true`), marca a assinatura local `CANCELLED` com `cancellation_date`, e o acesso continua até `next_billing_date`. Quando o Stripe encerra a assinatura (`customer.subscription.deleted`), o status vira `EXPIRED`.

## Boundaries & Constraints

**Always:**
- Acesso de `MONTHLY/CANCELLED` vale enquanto `agora < next_billing_date`; depois disso, sem acesso (402) mesmo antes do webhook `deleted` chegar.
- Pedido ao Stripe é **síncrono**: se falhar, nada muda localmente e o usuário vê erro (503). Não marcar CANCELLED sem o Stripe ter aceitado.
- Idempotente: cancelar de novo uma assinatura já `CANCELLED` → 409; webhook repetido não altera nada.
- Evento antigo `checkout.session.completed` com o mesmo `stripe_subscription_id` **nunca** reativa uma assinatura `CANCELLED`/`EXPIRED` (item adiado da 2.2).
- Audit `subscription_cancelled` e `subscription_expired`; e-mail "Assinatura cancelada" best-effort após commit, com a data até quando o acesso vale.

**Never:**
- Desfazer cancelamento / reativar (Story 2.5), reembolso, cancelamento imediato.
- Cancelar trial que ainda não assinou (não há o que cancelar).

## I/O & Edge-Case Matrix

| Cenário | Estado | Resultado | Erro |
|---|---|---|---|
| Cancela mensal ativo | MONTHLY/ACTIVE | Stripe `cancel_at_period_end`, local CANCELLED + `cancellation_date`, 200 | — |
| Cancela já cancelado | MONTHLY/CANCELLED | 409 | — |
| Cancela em trial | TRIAL | 409 | — |
| Stripe fora | erro na API | 503, local inalterado | log warn |
| Acesso antes do fim | CANCELLED, agora < next_billing | `hasAccess` true | — |
| Acesso após o fim | CANCELLED, agora ≥ next_billing | `hasAccess` false | — |
| Stripe encerra | `customer.subscription.deleted` do id local | EXPIRED, audit | — |
| `deleted` de id desconhecido | sem linha com esse id | 200, nada muda | log warn |
| Checkout antigo reentregue | CANCELLED/EXPIRED, mesmo sub id | nada muda | — |

</frozen-after-approval>

## Code Map

- `porquinho-backend/src/main/resources/db/migration/V12__add_cancellation_date_to_subscriptions.sql` -- NOVO: `cancellation_date` TIMESTAMPTZ. V1–V11 aplicadas: não editar
- `.../entity/Subscription.java` -- campo `cancellationDate`; enums `Status{ACTIVE,CANCELLED,EXPIRED}` já existem
- `.../service/SubscriptionService.java:105-125` -- `hasAccess(Subscription)`: hoje exige `ACTIVE`; incluir regra CANCELLED-até-`next_billing_date`. `activateMonthly:183-200`: idempotência só checa ACTIVE — mesmo `stripe_subscription_id` deve retornar sem alterar em qualquer status. `afterCommit` (~236) e `findByUserIdForUpdate` para lock: reutilizar
- `.../service/StripeBillingService.java:172` -- `cancelSubscription` (imediato, best-effort, usado em duplicata/purge): **não reutilizar** aqui; criar `cancelAtPeriodEnd(id)` que **lança** `PaymentProviderUnavailableException` em falha
- `.../controller/SubscriptionController.java` -- adicionar `POST /cancel` (já isento do filtro 402)
- `.../controller/StripeWebhookController.java:34,59` -- hoje só `checkout.session.completed`; rotear também `customer.subscription.deleted` (objeto `id` = sub id)
- `.../repository/SubscriptionRepository.java` -- `findByStripeSubscriptionId` (UNIQUE já existe, V10)
- `.../dto/SubscriptionResponse.java` -- expor `nextBillingDate` e `cancellationDate`
- `.../service/EmailService.java` -- `newMessage` + datas SP; adicionar `sendSubscriptionCancelled(email, accessUntil)`
- `porquinho-frontend/src/views/SubscriptionView.vue` -- tela do plano; adicionar cancelamento com modal (shadcn `Dialog`, como em `AccountDeletionView.vue`)
- `porquinho-frontend/src/components/subscription/TrialBadge.vue:3,14` -- só TRIAL e MONTHLY/ACTIVE; adicionar "Assinatura cancelada (ativa até dd/mm/aaaa)" e "Assinatura expirada"
- `porquinho-frontend/src/views/DashboardView.vue` -- CTA "Assinar" hoje só em TRIAL; mostrar também para EXPIRED
- `porquinho-frontend/src/types/subscription.ts` -- campos novos
- Dev local: `stripe listen` precisa encaminhar `customer.subscription.deleted` também

## Tasks & Acceptance

- [ ] V12 + `Subscription.java` -- coluna/campo
- [ ] `StripeBillingService.cancelAtPeriodEnd` -- update `cancel_at_period_end=true`; erro → `PaymentProviderUnavailableException`
- [ ] `SubscriptionService` -- `cancel(userId, ip)` (lock, 409 se não MONTHLY/ACTIVE, Stripe síncrono, CANCELLED + data, audit, e-mail pós-commit); `expireByStripeSubscriptionId(id)`; `hasAccess` com CANCELLED; idempotência do `activateMonthly`
- [ ] `SubscriptionController` `POST /cancel` + `StripeWebhookController` `customer.subscription.deleted` + DTO
- [ ] `EmailService.sendSubscriptionCancelled`
- [ ] Frontend: botão + modal "Tem certeza? Você perde o acesso ao fim do período atual (dd/mm/aaaa)" em `/assinatura` (só MONTHLY/ACTIVE); badges cancelada/expirada; CTA Assinar para EXPIRED
- [ ] Testes cobrindo toda a matriz (backend) + view/badge (frontend)

**AC:**
- Dado assinatura mensal ativa, quando cancelo e confirmo no modal, então a tela mostra "Assinatura cancelada — ativa até dd/mm/aaaa", o Stripe mostra a assinatura com cancelamento agendado e continuo com acesso até essa data.

## Spec Change Log

## Review Triage Log

Revisão em 3 camadas (Blind Hunter, Edge Case Hunter, Verification Gap) — 2026-10-31.

| # | Origem | Achado | Veredito | Evidência | Rota |
|---|---|---|---|---|---|
| 1 | Blind, Edge | `next_billing_date` desatualizado após renovações → cancelamento corta acesso cedo; null corta na hora | high | Só `activateMonthly` grava o campo; `hasAccess` de CANCELLED depende dele | patch (data do Stripe no cancel) |
| 2 | Blind, Edge, VG | Checkout aceito para CANCELLED com acesso → cobrança imediata sobreposta | high | Guard só bloqueia MONTHLY/ACTIVE | patch |
| 3 | Blind, Edge, VG | `cancellationDate` não limpo ao reativar | medium | `activateMonthly` não zera | patch |
| 4 | VG, Edge | Polling para na hora ao reassinar a partir de EXPIRED | medium | Condição `planType==='MONTHLY'` | patch |
| 5 | Blind, Edge | Erro de rede no cancelamento diz "nada foi alterado"; falha no reload pós-409 some com a tela | medium | catch genérico | patch |
| 6 | Blind | JSDoc deslocado em subscriptionService.ts | low | Correção direta | patch |
| 7 | Blind, VG | Testes faltando: cancelAtPeriodEnd, valores do DTO, 404 do /cancel, CANCELLED sem acesso (dashboard/view) | medium | VG pré-verificado | patch |
| 8 | Blind, Edge | `customer.subscription.updated` não tratado (desfazer cancelamento fora do app; datas de renovação) | medium | Só deleted/checkout roteados | defer (2.5/2.6) |
| 9 | Blind, Edge | Stripe aceitou e commit falhou → divergência | low | Janela pequena; sem reconciliação | defer |
| 10 | Blind, Edge | IP do audit via `getRemoteAddr` atrás de proxy | low | Mesmo padrão do DataExportController | defer |
| 11 | Blind | Webhook de produção precisa assinar `customer.subscription.deleted` | low | Acesso termina pelo `hasAccess` mesmo assim | defer (deploy) |
| 12 | Blind | ACTIVE→EXPIRED por cobrança falha sem aviso | low | Escopo da 2.7 | defer |
| 13 | Blind | Comentário da coluna na V12 impreciso | low | V12 já aplicada; editar quebra checksum Flyway | reject |

## Design Notes

Assinatura que está em `trialing` no Stripe (assinou durante o trial — decisão A da 2.2): `cancel_at_period_end` encerra no fim do trial sem nenhuma cobrança; `next_billing_date` já é essa data, então a regra de acesso vale igual.

## Verification

**Commands:**
- `cd porquinho-backend && ./mvnw test` -- 0 falhas
- `cd porquinho-frontend && npx vitest run && npx vue-tsc --build && npm run lint` -- verde

**Manual checks:**
- Com `stripe listen --events checkout.session.completed,customer.subscription.deleted`: cancelar na UI → linha CANCELLED; no painel Stripe, "Cancelar imediatamente" a assinatura → webhook → linha EXPIRED.
