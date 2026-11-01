---
title: 'Falhas de pagamento: avisos, novas tentativas e suspensão'
type: 'feature'
created: '2026-10-31'
status: 'done'
baseline_commit: 45a2006ec511bb24eacae7821bb0b097d7765056
route: 'dispatch'
story_key: 2-7-handle-payment-failures-with-retry-notifications
review_loop_iteration: 1
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-2-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Quando a cobrança mensal falha (cartão vencido, sem saldo), o porquinho não fica sabendo. O usuário não é avisado, não tem como pagar de novo e não há regra de suspensão.

**Approach:** O próprio Stripe repete a cobrança, conforme o agendamento configurado no Dashboard, e cancela a assinatura quando todas as tentativas falham. O porquinho reage aos webhooks:
- `invoice.payment_failed`: status `PAYMENT_FAILED`, contador de tentativas, audit e e-mail "Pagamento falhou" com o link de pagamento da fatura.
- `invoice.paid` (já existe, Story 2.6): volta para `ACTIVE` e zera o contador.
- `customer.subscription.deleted` com status `PAYMENT_FAILED`: vira `SUSPENDED`, sem acesso, com e-mail "Assinatura suspensa".

No frontend: banner "Pagamento falhou" com CTA "Atualizar pagamento", que abre a página de pagamento da fatura no Stripe.

## Boundaries & Constraints

**Always:**
- Link de pagamento = `hosted_invoice_url` da fatura que falhou. Nessa página o usuário paga na hora, com o cartão salvo ou um novo, e isso cobre "tentar de novo" e "trocar cartão". Quando é paga, o Stripe envia `invoice.paid`.
- Idempotência da falha: reprocessar uma `attempt_count` menor ou igual à já gravada não muda nada, não reenvia e-mail nem duplica o audit.
- E-mails best-effort enviados depois do commit. O de falha traz o link da fatura, sem motivo (decisão 3A).
- Audit: `payment_failed` (com `attempt_count` e `invoice_id`), `payment_recovered` (quando o `invoice.paid` tira a assinatura de `PAYMENT_FAILED`) e `subscription_suspended`.
- Usuário `SUSPENDED` volta pelo Checkout ("Reativar assinatura", fluxo da 2.5).
- Configuração no Stripe Dashboard, documentada no `.env.example` e no deferred-work para o deploy: agendamento das tentativas e, depois da última falha, **cancelar a assinatura**.

- **Decisão (Luiz, 2026-10-31) 1A:** agendamento do epic: 2 novas tentativas, 3 e 10 dias depois da falha (Dashboard → Billing → Revenue recovery → Retries, agendamento customizado), e cancelar a assinatura após a última.
- **Decisão (Luiz, 2026-10-31) 2A:** `PAYMENT_FAILED` mantém o acesso, com o banner, até virar `SUSPENDED`.
- **Decisão (Luiz, 2026-10-31, revisão) 1A:** em `PAYMENT_FAILED` o usuário pode cancelar: cancelamento **imediato** no Stripe (síncrono; falha → 503, nada muda), linha `EXPIRED` com `cancellation_date`, audit `subscription_cancelled` (`immediate: true`), e-mail "Assinatura cancelada" com o acesso encerrado hoje. Sem e-mail de suspensão.
- **Decisão (Luiz, 2026-10-31, revisão) 2A:** ao suspender, o porquinho anula (void) no Stripe a fatura em aberto (`latest_invoice` do evento `customer.subscription.deleted`), best-effort: o link do e-mail para de cobrar. Volta só pelo Checkout.
- **Decisão (Luiz, 2026-10-31, revisão) 3A:** sem motivo da falha no e-mail nem no audit.

**Never:**
- Job próprio de retry ou cobrança off-session. O Stripe já faz isso.
- Gravar falhas em `billing_transactions`: a fatura é a chave UNIQUE, e uma linha de falha bloquearia o registro do pagamento que depois dá certo.
- Customer Portal ou endpoint novo para trocar o cartão.

## I/O & Edge-Case Matrix

| Cenário | Estado | Resultado | Erro |
|---|---|---|---|
| 1ª falha | MONTHLY/ACTIVE, `invoice.payment_failed` attempt 1 | PAYMENT_FAILED, attempts=1, link salvo, audit, e-mail | — |
| Nova tentativa falha | PAYMENT_FAILED, attempt 2 | attempts=2, audit, e-mail | — |
| Falha repetida | attempt ≤ gravado | nada muda | — |
| Retry pago | PAYMENT_FAILED + `invoice.paid` | ACTIVE, attempts=0, link limpo, audit `payment_recovered`, recibo (2.6) | — |
| Todas falharam | PAYMENT_FAILED + `customer.subscription.deleted` | SUSPENDED, sem acesso (402), e-mail suspensa | — |
| Cancelamento normal | ACTIVE/CANCELLED + deleted | EXPIRED (como hoje) | — |
| Falha da 1ª fatura no Checkout | sub id ainda desconhecido | 200, ignorada (o Checkout mostra o erro) | log warn |
| Checkout durante PAYMENT_FAILED | POST /checkout | 409 "pague a fatura pendente" | — |
| Cancela durante PAYMENT_FAILED | POST /cancel | Stripe cancela na hora; EXPIRED, `cancellation_date`, sem acesso, audit, e-mail cancelada, 200 | Stripe fora → 503, inalterado |
| Suspensão anula a fatura | PAYMENT_FAILED + deleted com `latest_invoice` | SUSPENDED + void da fatura no Stripe | void falha → log error, segue SUSPENDED |
| Fatura paga após suspensão | SUSPENDED + `invoice.paid` (void falhou) | pagamento gravado (2.6), status não muda | log error (estorno manual) |

</frozen-after-approval>

## Code Map

- `porquinho-backend/src/main/resources/db/migration/V14__payment_failure_tracking.sql` -- NOVO. Trocar o CHECK de `subscriptions.status` para incluir `PAYMENT_FAILED` e `SUSPENDED`: o constraint inline da V9 tem nome gerado, então conferir com `\d subscriptions` / o MigrationTest. Adicionar `failed_payment_attempts INT NOT NULL DEFAULT 0` e `payment_update_url VARCHAR(1024)`. **Não editar V1–V13.** Assim que algum teste rodar, o devtools aplica a migration no Supabase: escrever certo na 1ª vez.
- `.../entity/Subscription.java:20` -- enum `Status` + campos novos.
- `.../controller/StripeWebhookController.java` -- `INVOICE_PAYMENT_FAILED`, com os mesmos campos lidos do `invoice.paid` (sub id: `parent.subscription_details.subscription`/`subscription`), mais `attempt_count` e `hosted_invoice_url` (sem motivo, decisão 3A). Em `handleSubscriptionDeleted`, ler também `latest_invoice` (string id) e repassar ao service.
- `.../service/SubscriptionService.java` -- `recordPaymentFailure(...)` (lock `findByStripeSubscriptionIdForUpdate`, padrão do `recordPayment`). Em `recordPayment`, se o status era `PAYMENT_FAILED`: ACTIVE, zerar o contador e o link, audit `payment_recovered`. Em `expireByStripeSubscriptionId(subId, latestInvoiceId)`, se era `PAYMENT_FAILED`: SUSPENDED, e-mail e, após o commit, `stripeBillingService.voidInvoice(latestInvoiceId)` best-effort. Em `cancel`, aceitar também `PAYMENT_FAILED` → `stripeBillingService.cancelImmediately` (síncrono) → EXPIRED (o `deleted` que chega depois é no-op). Em `recordPayment`, SUSPENDED: grava normalmente e `log.error` pedindo estorno manual. Em `hasAccess`, `PAYMENT_FAILED` tem acesso (decisão 2A) e `SUSPENDED` não tem. Em `createCheckout`, 409 para `PAYMENT_FAILED`.
- `.../service/StripeBillingService.java:183` -- `cancelSubscription` é best-effort (duplicata/purge): **não reutilizar** no `cancel`. Criar `cancelImmediately(id)` no padrão do `cancelAtPeriodEnd` (lança `PaymentProviderUnavailableException`) e `voidInvoice(invoiceId)` best-effort (log error, nunca lança).
- Manter da 1ª implementação (`/private/tmp/claude-501/-Users-lcmuniz-Desenvolvimento-porquinho/524c20e4-30ec-403c-8672-4d680c844a20/scratchpad/story27-iter0.diff`): falha de fatura já em `billing_transactions` → ignorada (entrega fora de ordem); `reactivate` → 409 em PAYMENT_FAILED; Checkout antigo concluído em PAYMENT_FAILED → duplicata cancelada; `payUrl` cai em `{frontend}/assinatura` se `hosted_invoice_url` vier nulo; V14 já aplicada no Supabase — **não editar**.
- `.../dto/SubscriptionResponse.java` -- expor `failedPaymentAttempts` e `paymentUpdateUrl`.
- `.../service/EmailService.java` -- `sendPaymentFailed(email, payUrl)` e `sendSubscriptionSuspended(email, reactivateUrl)` (`{frontend}/assinatura`).
- Frontend: `types/subscription.ts` (status novos e campos), `components/subscription/TrialBadge.vue` ("Pagamento falhou", "Assinatura suspensa"), `views/DashboardView.vue` (banner com CTA "Atualizar pagamento" que abre direto `paymentUpdateUrl` via `window.location.assign`, como na SubscriptionView; CTA "Reativar" para SUSPENDED), `views/SubscriptionView.vue` (bloco de falha com botão "Atualizar pagamento" → `window.location.assign(paymentUpdateUrl)` e botão "Cancelar assinatura" com modal "o acesso termina agora"; SUSPENDED usa o fluxo de ex-assinante; polling/`isFormerSubscriber` cobrindo SUSPENDED).
- `porquinho-backend/.env.example` -- `stripe listen` com `invoice.payment_failed`, e nota do Dashboard (retries + cancelar ao final).
- Testes: controller (inclui `GET /me` com `failedPaymentAttempts`/`paymentUpdateUrl` no JSON), service, StripeBillingService, e-mail, MigrationTest (V14), TrialBadge, Dashboard e SubscriptionView.

## Tasks & Acceptance

**Execution:**
- [x] V14 + entidade + DTO
- [x] Webhook `invoice.payment_failed`
- [x] `SubscriptionService`: falha, recuperação, suspensão, acesso e checkout -- matriz acima
- [x] `EmailService`: falha e suspensão
- [x] Frontend: banner, badges, botão "Atualizar pagamento", SUSPENDED → Reativar
- [x] Testes cobrindo toda a matriz (backend) + view/badge/dashboard (frontend)

**Acceptance Criteria:**
- Dado um assinante cuja renovação falha, quando chega `invoice.payment_failed`, então ele recebe o e-mail "Pagamento falhou" com o link, o dashboard mostra o banner, e "Atualizar pagamento" abre a fatura no Stripe. Ao pagar, a assinatura volta a "Assinatura ativa".

## Spec Change Log

- **Iteração 1 (intent_gap, triage #1–#3):** cancelar em PAYMENT_FAILED dava 409; fatura paga após suspensão gravava pagamento sem acesso; motivo lido de campo que o Stripe não preenche em recusa. Luiz decidiu 1A/2A/3A (bloco congelado). Code Map: `cancelImmediately`, `voidInvoice`, `latest_invoice`, CTA do dashboard direto para a fatura, teste do `GET /me`. Estado ruim evitado: usuário preso sem poder cancelar e pagando sem acesso. KEEP: V14 como está; padrões da 1ª implementação listados no Code Map.

## Review Triage Log

Revisão em 3 camadas (Blind Hunter, Edge Case Hunter, Verification Gap) — 2026-10-31, iteração 0.

| # | Origem | Achado | Veredito | Evidência | Rota |
|---|---|---|---|---|---|
| 1 | Blind, Edge, VG | Motivo lido de `last_finalization_error.message`, que trata de erro de finalização da fatura, não de recusa de cartão: o e-mail nunca mostra o motivo | low | Na API 2026 a fatura não traz `payment_intent`/`charge`; o motivo fica em `last_payment_error` do PaymentIntent (exige chamada ao Stripe) e vem em inglês | intent_gap |
| 2 | Blind, Edge, VG | Em PAYMENT_FAILED o usuário não consegue cancelar: botão oculto e `cancel()` → 409 "Não há assinatura mensal ativa"; o Stripe segue tentando cobrar | medium | `cancel` exige MONTHLY/ACTIVE; antes da 2.7 uma falha deixava ACTIVE e o cancelamento funcionava | intent_gap |
| 3 | Edge | Fatura antiga paga pelo link do e-mail depois de SUSPENDED: pagamento e recibo gravados, assinatura continua SUSPENDED, sem acesso | medium | `recordPayment` só recupera PAYMENT_FAILED; após o cancelamento a fatura fica aberta/uncollectible e ainda é pagável | intent_gap |
| 4 | Edge | CTA do dashboard vai para `/assinatura`, não abre a fatura no Stripe como diz o AC | low | `DashboardView.vue`: `RouterLink to="/assinatura"` | patch (moot: loopback) |
| 5 | VG | `GET /me` sem teste dos campos `failedPaymentAttempts`/`paymentUpdateUrl` | medium | VG pré-verificado | patch (moot: loopback) |
| 6 | Blind, Edge | `attempt_count` ausente → 0 → falha ignorada | false | `attempt_count` é inteiro sempre presente na Invoice do Stripe | reject |
| 7 | Blind, Edge | `deleted` em PAYMENT_FAILED por cancelamento manual/purge vira SUSPENDED com e-mail errado | low | Purge apaga a linha na mesma transação (webhook acha id desconhecido); cancelamento manual no Dashboard é raro; fix adiciona ramo | reject |
| 8 | Blind, Edge | Acesso indefinido em PAYMENT_FAILED se o Dashboard não cancelar ao final | low | Configuração de deploy já registrada no deferred-work (decisão 2A) | reject (duplicado) |
| 9 | Blind, Edge | `paymentUpdateUrl` nulo deixa a tela sem ação | false | Fatura finalizada (pré-requisito de `payment_failed`) sempre tem `hosted_invoice_url` | reject |
| 10 | Blind, Edge | Idempotência por assinatura, não por fatura: falha de outra fatura com o contador alto é ignorada | low | Com retries em 10 dias a assinatura é suspensa antes da próxima fatura; o app não gera faturas avulsas; fix exige coluna nova | reject |
| 11 | Blind | UI não mostra data da próxima tentativa nem "tentativa N de 3" | low | Não pedido pela intenção; melhoria | reject |
| 12 | Blind | Lacunas: TRIAL, CHECK no banco, filtro LIKE do MigrationTest, exceção → 500 | low | V14 aplicada com sucesso no Supabase; 500 faz o Stripe reentregar, o que é aceitável | reject |
| 13 | Blind | `window.location.assign(paymentUpdateUrl)` sem validar o host | false | Valor vem de webhook com assinatura verificada | reject |
| 14 | Edge | Sem polling ao voltar da página da fatura | low | A página hospedada não redireciona de volta; recarregar mostra o estado real | reject |


Iteração 1 — 2026-10-31.

| # | Origem | Achado | Veredito | Evidência | Rota |
|---|---|---|---|---|---|
| 15 | Blind, Edge, VG | Cancelamento imediato não anula a fatura em aberto: o link do e-mail ainda cobra e o pagamento entra numa linha EXPIRED sem alerta | medium | `cancelImmediately` só cancela; `deleted` posterior é no-op para EXPIRED; teste `deletedAfterImmediateCancelIsANoOp` confirma | patch |
| 16 | Edge | `invoice.paid` em EXPIRED sem `log.error` de estorno manual | medium | `recordPayment` só alerta em SUSPENDED | patch (com #15) |
| 17 | Edge | Suspensão com `latest_invoice` nulo: void vira no-op silencioso | low | `voidInvoice` retorna sem log em id vazio; correção direta (log error) | patch |
| 18 | Blind | E-mail da última falha diz "vamos tentar de novo" segundos antes do "Assinatura suspensa" | medium | Na tentativa final `next_payment_attempt` vem nulo; o texto é fixo | patch |
| 19 | Edge | Recibo "Pagamento confirmado" enviado a SUSPENDED que pagou | low | Cobrança realmente ocorreu; recibo é verdadeiro; o `log.error` cobre o estorno | reject |
| 20 | Blind, Edge | `deleted` decidido sem `cancellation_details.reason` (evento perdido / commit falho após cancel no Stripe) | low | Exige perda de webhook ou falha de commit; mesma classe do item #9 adiado na 2.4 | reject |
| 21 | Blind, Edge | carried — idempotência por assinatura, não por fatura | low | carried: igual ao #10 | reject |
| 22 | Blind, Edge | carried — `attempt_count` ausente | false | carried: igual ao #6 | reject |
| 23 | Blind, Edge | carried — acesso indefinido sem a configuração do Dashboard | low | carried: igual ao #8 | reject |
| 24 | Blind, Edge | carried — `paymentUpdateUrl` nulo → beco sem saída (inclui claim do CTA) | false | carried: igual ao #9 | reject |
| 25 | Blind | carried — URL sem validação de host | false | carried: igual ao #13 | reject |
| 26 | Blind | carried — UI sem data da próxima tentativa/contador | low | carried: igual ao #11 | reject |
| 27 | Blind | Contador não zerado em EXPIRED/SUSPENDED | low | Nenhuma UI lê o campo nesses status | reject |
| 28 | Blind | Badge + banner anunciados duas vezes a leitores de tela; `updatePayment` duplicado | low | Cosmético; duas linhas | reject |
| 29 | Edge | `hostedInvoiceUrl` nulo mantém link antigo | false | Fatura finalizada sempre tem `hosted_invoice_url` | reject |
| 30 | Edge | 409 no cancelamento imediato sem mensagem | low | Exige suspensão concorrente; reload mostra o estado | reject |
| 31 | Edge | `app.frontend-url` vazio gera link relativo | false | Default `http://localhost:5173` e `.env` definem a URL | reject |
| 32 | Blind | Testes: CHECK só por `contains`; audit `payment_failed` sem `stripe_subscription_id` | low | V14 aplicada com sucesso; audit já tem `user_id` e `invoice_id` | reject |

## Verification

**Commands:**
- `cd porquinho-backend && ./mvnw test` -- 0 falhas
- `cd porquinho-frontend && npx vitest run && npx vue-tsc --build && npm run lint` -- verde

**Manual checks:**
- No Stripe (teste): trocar o cartão da assinatura pelo `4000 0000 0000 0341` (é aceito ao salvar e recusado na cobrança) e forçar a renovação com um Test Clock → banner + e-mail; pagar pela fatura com `4242…` → ativa.
