---
title: 'Assinar plano mensal (R$ 19,90) via Stripe Checkout'
type: 'feature'
created: '2026-10-31'
status: 'done'
baseline_commit: 94762ff0d527d091972849ca0a27a70bfd89f3af
route: 'dispatch'
story_key: 2-2-subscribe-to-monthly-plan-after-trial
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-2-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Usuário em trial (ou com trial vencido) não tem como pagar; ao fim dos 30 dias perde o acesso sem saída.

**Approach:** Plano mensal único de **R$ 19,90** via **Stripe Checkout hospedado** (modo `subscription`, cartão). Backend cria a sessão e devolve a URL; frontend só redireciona. Webhook do Stripe confirma o pagamento e muda a assinatura para `MONTHLY`/`ACTIVE`. Cobrança recorrente fica a cargo do Stripe Billing.

## Boundaries & Constraints

**Always:**
- Estado da assinatura só muda pelo **webhook com assinatura verificada** (`Stripe-Signature` + `STRIPE_WEBHOOK_SECRET`), nunca pelo retorno do redirect.
- Webhook idempotente: reprocessar o mesmo evento não altera o resultado.
- Segredos só no backend (`STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`); frontend não usa chave Stripe.
- Preço inline no Checkout (`price_data`: BRL, 1990 centavos, recorrência mensal) — sem setup manual no painel.
- Audit `subscription_activated`; e-mail "Assinatura ativada" best-effort (`EmailService`).
- **Decisão (Luiz, 2026-10-31): cobrança no fim do trial.** Assinar durante o trial passa `subscription_data.trial_end` = `trial_end_date` da assinatura local; o usuário mantém os dias grátis e a 1ª cobrança ocorre nessa data. Com trial já vencido, cobra na hora. Na confirmação o status vira MONTHLY/ACTIVE.

**Never:**
- Cancelamento, reativação, falha de pagamento/retry, lembretes (Stories 2.3–2.7).
- Pix/boleto; Stripe Elements; armazenar dados de cartão.
- Referenciar `VITE_STRIPE_PUBLISHABLE_KEY` (ou qualquer chave Stripe) no frontend: o `.env` local contém uma chave secreta nessa variável e o Vite embute no bundle tudo que o código referencia.

## I/O & Edge-Case Matrix

| Cenário | Entrada / Estado | Resultado esperado | Erro |
|---|---|---|---|
| Checkout em trial | `POST /api/v1/subscriptions/checkout`, TRIAL | 200 `{url}` da sessão Stripe | — |
| Checkout já mensal | MONTHLY/ACTIVE | 409 ProblemDetail | — |
| Pagamento ok | evento `checkout.session.completed` válido | MONTHLY/ACTIVE, ids Stripe salvos, `next_billing_date` = fim do período | — |
| Evento repetido | mesmo evento 2x | estado igual, 1 audit | — |
| Assinatura inválida | header ausente/errado | 400, nada muda | log warn |
| Stripe fora | erro na API ao criar sessão | 503 ProblemDetail | trial continua |
| Usuário desiste | volta pela `cancel_url` | tela de assinatura com aviso, trial intacto | — |
| Trial vencido + rota protegida | 402 | frontend redireciona para `/assinatura` | — |

</frozen-after-approval>

## Code Map

- `porquinho-backend/pom.xml` -- adicionar `com.stripe:stripe-java` (não existe hoje)
- `porquinho-backend/src/main/resources/application.yml` -- padrão `${ENV:default}`; criar `stripe.secret-key`, `stripe.webhook-secret`, `app.frontend-url` (`${FRONTEND_URL:http://localhost:5173}`) — não há propriedade de URL do frontend hoje
- `.../db/migration/V10__add_stripe_ids_to_subscriptions.sql` -- NOVO: `stripe_customer_id`, `stripe_subscription_id` (UNIQUE). V9 já aplicada no Supabase: **não editar V1–V9**
- `.../entity/Subscription.java` -- enums `PlanType{TRIAL,MONTHLY}`, `Status{ACTIVE,CANCELLED,EXPIRED}`; setters prontos; `payment_method_id` já existe
- `.../service/SubscriptionService.java` -- `hasAccess`/`daysRemaining` (0 para não-TRIAL) — reutilizar
- `.../controller/SubscriptionController.java` -- só `GET /me`; adicionar `POST /checkout` (já isento do filtro 402 por `/api/v1/subscriptions/`)
- `.../config/SecurityConfig.java:48-55` -- adicionar `permitAll` para `/api/v1/webhooks/stripe` **antes** de `/api/v1/**`; CSRF já desabilitado
- `.../config/SubscriptionAccessFilter.java:37-42` -- `EXEMPT_PREFIXES`: webhook é anônimo (filtro já pula), não precisa entrar
- `.../service/EmailService.java` -- padrão best-effort (`mailSender == null` → log e retorna); `AuditLogService.log(event, userId, ip, metadata)`
- `.../repository/UserRepository.java` -- e-mail do usuário para `customer_email`
- `porquinho-frontend/src/services/api.ts:57-63` -- interceptor só trata 401; adicionar 402 → `/assinatura` (item adiado da 2.1)
- `porquinho-frontend/src/components/subscription/TrialBadge.vue` -- só renderiza TRIAL; "Trial expirado" sem CTA
- `porquinho-frontend/src/views/DashboardView.vue` -- header com badge + botão Sair
- `porquinho-frontend/src/router/index.ts` -- sem rota de assinatura

## Tasks & Acceptance

- [x] `pom.xml` + `application.yml` -- stripe-java, propriedades Stripe e `app.frontend-url`; `.env.example`/docs com `STRIPE_WEBHOOK_SECRET`
- [x] `V10__add_stripe_ids_to_subscriptions.sql` + `Subscription.java` -- colunas e campos novos
- [x] `service/StripeBillingService.java` -- criar sessão Checkout (`mode=subscription`, `client_reference_id`=userId, `customer_email`, `price_data` 1990 BRL mensal, success `{frontend}/assinatura?status=sucesso`, cancel `{frontend}/assinatura?status=cancelado`); construir/verificar evento do webhook. Isola o SDK para mock nos testes
- [x] `SubscriptionService.java` -- `activateMonthly(userId, stripeCustomerId, stripeSubscriptionId, paymentMethodId, nextBillingDate)` idempotente + audit + e-mail
- [x] `SubscriptionController.java` -- `POST /checkout` (409 se já MONTHLY/ACTIVE, 503 se Stripe falhar)
- [x] `controller/StripeWebhookController.java` + `SecurityConfig.java` -- `POST /api/v1/webhooks/stripe`, público, trata `checkout.session.completed`; outros eventos → 200 ignorado
- [x] `EmailService.java` -- `sendSubscriptionActivated(email, nextBillingDate)`
- [x] Frontend: `views/SubscriptionView.vue` (rota `/assinatura`, requiresAuth): plano R$ 19,90/mês, botão "Assinar" → `window.location = url`; mensagens para `?status=sucesso|cancelado`; `subscriptionService.createCheckout()`; badge "Assinatura ativa" para MONTHLY; CTA "Assinar" no badge/dashboard em trial; `api.ts` 402 → `/assinatura`
- [x] Testes: unit `SubscriptionService.activateMonthly` (idempotência), controller checkout (200/409/503), webhook (assinatura inválida 400, evento válido ativa, evento ignorado 200), frontend SubscriptionView + interceptor 402

**AC:**
- Dado trial ativo, quando clico "Assinar" e pago com `4242 4242 4242 4242` no Checkout de teste, então volto para `/assinatura?status=sucesso` e, após o webhook, o dashboard mostra "Assinatura ativa".
- Dado webhook com assinatura inválida, então 400 e nenhuma linha muda.

## Spec Change Log

## Review Triage Log

Revisão em 3 camadas (Blind Hunter, Edge Case Hunter, Verification Gap) — 2026-10-31.

| # | Origem | Achado | Veredito | Evidência | Rota |
|---|---|---|---|---|---|
| 1 | Blind, Edge | 2º Checkout concluído sobrescreve `stripeSubscriptionId`; 1ª assinatura Stripe segue cobrando | high | `activateMonthly` só checa mesmo id; nada impede 2 sessões | patch (cancelar duplicata) — escolhido sobre bad_spec por proporcionalidade: guard local + 1 método best-effort |
| 2 | Blind, Edge | Purge LGPD não cancela assinatura no Stripe | high | `AccountDeletionService:209` TODO; com 2.2 há cobrança real | patch (reusa cancelSubscription do #1) |
| 3 | Edge | Evento antigo reativa CANCELLED/EXPIRED | maybe-false | Estados inalcançáveis até 2.4 | defer |
| 4 | Blind | Customer Stripe não reaproveitado | low | Só afeta reassinatura (2.5) | defer |
| 5 | Blind, VG | Checkout com trial vencido sem teste pela cadeia real | medium | Isenção `/api/v1/subscriptions/` existe; sem teste | patch (teste) |
| 6 | Blind, Edge | Data do e-mail em UTC | low | `toOffsetDateTime` UTC; correção direta | patch |
| 7 | Blind, Edge | Tela de sucesso: texto "Pagamento recebido", botão Assinar visível, sem refresh | medium | Trial não paga agora; risco de 2º checkout (#1) | patch |
| 8 | Blind | Erro inesperado no webhook → 500 e Stripe reenvia | false | Retry é o comportamento desejado para erro transitório | reject |
| 9 | Blind | Mensagens de config expostas no webhook público | low | `ex.getMessage()` no ProblemDetail; correção direta | patch |
| 10 | Blind, Edge | Audit JSON grava "null" e não escapa | low | `String.format`; correção direta | patch |
| 11 | Blind | Import circular api.ts ↔ router | maybe-false | Funciona em dev/testes; views lazy | reject (low se real) |
| 12 | Blind, Edge | Badge/CTA ausentes para MONTHLY CANCELLED/EXPIRED | low | Estados inalcançáveis até 2.4 | defer |
| 13 | VG | Caminho afterCommit do e-mail sem teste | medium | VG pré-verificado | patch |
| 14 | VG | Parâmetros do Checkout e mapeamento da subscription sem teste | medium | VG pré-verificado | patch |
| 15 | VG, Blind | Guardas do webhook sem teste | medium | VG pré-verificado | patch |
| 16 | VG, Blind | Lock pessimista sem teste concorrente | medium | VG pré-verificado; afeta só audit/e-mail duplicado | defer |
| 17 | VG | 409 na view e MONTHLY/CANCELLED no backend sem teste | medium | VG pré-verificado | patch |
| 18 | Blind | `stripeWebhookIsPublicWithoutJwt` espera 404 (frágil) | low | Teste novo #15 cobre o controller real | reject |

## Design Notes

Teste local do webhook: `stripe listen --forward-to localhost:8080/api/v1/webhooks/stripe` imprime o `whsec_...` para `STRIPE_WEBHOOK_SECRET`. Sem Stripe CLI, o webhook não chega em localhost.

`checkout.session.completed` traz `client_reference_id` (userId), `customer`, `subscription`; buscar a Subscription do Stripe para `current_period_end` (→ `next_billing_date`) e `default_payment_method` (→ `payment_method_id`).

## Verification

**Commands:**
- `cd porquinho-backend && ./mvnw test` -- 0 falhas
- `cd porquinho-frontend && npx vitest run && npx vue-tsc --build && npm run lint` -- tudo verde

**Manual checks:**
- Fluxo AC 1 com `stripe listen` rodando; conferir linha em `subscriptions` (MONTHLY/ACTIVE, ids Stripe preenchidos)

### Verificação manual (2026-10-31)

Fluxo AC 1 executado no modo de teste com `stripe listen`: `checkout.session.completed` → webhook 200 → linha `MONTHLY/ACTIVE` com `stripe_customer_id`, `stripe_subscription_id` e `payment_method_id` preenchidos; `next_billing_date` = `trial_end_date` (2026-11-30, decisão A). No Stripe: 1 assinatura `trialing`, 1990 BRL. Audit `subscription_activated` = 1.
