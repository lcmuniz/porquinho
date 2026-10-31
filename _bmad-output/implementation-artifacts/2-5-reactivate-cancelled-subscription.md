---
title: 'Reativar assinatura cancelada'
type: 'feature'
created: '2026-10-31'
status: 'done'
baseline_commit: cd75ce96a710a4856312f8e6069c933b6614f088
route: 'dispatch'
story_key: 2-5-reactivate-cancelled-subscription
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-2-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Quem cancelou a assinatura (Story 2.4) não tem como voltar atrás pela tela. Quem já expirou só vê o "Assinar" genérico, e o sistema não registra nem avisa que é uma reativação.

**Approach:** Há dois caminhos:
- **Cancelada que ainda tem acesso:** o botão "Reativar assinatura" desfaz o cancelamento no Stripe (`cancel_at_period_end=false`), e a assinatura local volta a `ACTIVE`.
- **Expirada, ou cancelada já sem acesso:** o botão "Reativar assinatura" abre o Checkout do Stripe que já existe. Quando o webhook confirma, o evento é registrado como reativação.

Nos dois caminhos: audit `subscription_reactivated`, e-mail "Assinatura reativada" e badge "Assinatura ativa".

## Boundaries & Constraints

**Always:**
- O pedido ao Stripe para desfazer o cancelamento é síncrono. Se ele falhar, nada muda localmente e o usuário vê o erro (503), como no `cancel`.
- Lock na linha (`findByUserIdForUpdate`). A reativação é idempotente: reativar o que já está `MONTHLY/ACTIVE` → 409.
- E-mail best-effort após o commit, com a data da próxima cobrança.
- Checkout de quem já foi `MONTHLY` (EXPIRED ou CANCELLED sem acesso): o audit é `subscription_reactivated` (não `subscription_activated`) e o e-mail é "Assinatura reativada".

- **Decisão (Luiz, 2026-10-31) 1A:** reativar cancelada com acesso só desfaz o cancelamento — sem cobrança agora; próxima cobrança na data já prevista.
- **Decisão (Luiz, 2026-10-31) 2A:** reativação de expirada via Stripe Checkout (confirma/troca cartão, cobra na hora).

**Never:**
- Cobrar com o cartão salvo fora do Checkout (off-session).
- Reembolso, troca de plano, `customer.subscription.updated` (adiados).
- Nova migration: as colunas existentes bastam.

## I/O & Edge-Case Matrix

| Cenário | Estado | Resultado | Erro |
|---|---|---|---|
| Reativa cancelada com acesso | MONTHLY/CANCELLED, agora < next_billing | Stripe `cancel_at_period_end=false`; ACTIVE, `cancellation_date` null, `next_billing_date` do Stripe, audit, e-mail, 200 | — |
| Reativa já ativa | MONTHLY/ACTIVE | 409 | — |
| Reativa em trial | TRIAL | 409 | — |
| Reativa cancelada sem acesso / expirada | CANCELLED (agora ≥ next_billing) ou EXPIRED | `POST /reactivate` → 409 (a tela usa o Checkout) | — |
| Stripe fora | erro de rede/API | 503, local inalterado | log warn |
| Stripe diz que a assinatura já terminou | `InvalidRequestException` | 409 "assine novamente", local inalterado | log warn |
| Checkout de ex-assinante | EXPIRED + webhook novo sub id | ACTIVE, audit `subscription_reactivated`, e-mail reativada | — |
| Checkout de quem vem do trial | TRIAL + webhook | como hoje (`subscription_activated`) | — |

</frozen-after-approval>

## Code Map

- `porquinho-backend/src/main/java/com/porquinho/service/StripeBillingService.java` -- `cancelAtPeriodEnd` (padrão: valida id, `client == null` → `PaymentProviderUnavailableException`, devolve `periodEnd(updated)`). Criar `resumeSubscription(id)` análogo com `setCancelAtPeriodEnd(false)`. `InvalidRequestException` → nova `SubscriptionNotResumableException` (409) ou `ConflictException`.
- `.../service/SubscriptionService.java` -- `cancel(:250)` é o molde para `reactivate(userId, ip)` (lock, guards, Stripe síncrono, audit, `afterCommit`). `hasAccess(:114)` define "com acesso". `activateMonthly(:196)`: escolher o evento e o e-mail pelo `planType` anterior (MONTHLY → reativação). Não mexer na idempotência nem no tratamento de duplicata.
- `.../controller/SubscriptionController.java` -- adicionar `POST /reactivate` como o `/cancel` (já isento do 402).
- `.../service/EmailService.java` -- `sendSubscriptionReactivated(email, nextBillingDate)` seguindo `sendSubscriptionActivated` (data em SP).
- `.../exception/GlobalExceptionHandler.java` -- mapear a nova exceção para 409, se for criada.
- `porquinho-frontend/src/services/subscriptionService.ts` -- `reactivateSubscription()`, como o `cancelSubscription`.
- `porquinho-frontend/src/views/SubscriptionView.vue` -- botão "Reativar assinatura" em `isCancelledWithAccess` (sem modal), que chama `reactivateSubscription`. Para EXPIRED/CANCELLED sem acesso, o botão do Checkout ganha o rótulo "Reativar assinatura". Tratar 409/503 como o `confirmCancel`.
- `TrialBadge.vue`, `DashboardView.vue` -- já mostram "Assinatura ativa" para MONTHLY/ACTIVE. CTA do dashboard para EXPIRED: rótulo "Reativar".
- Testes: `SubscriptionServiceTest`, `StripeBillingServiceTest` (construtor package-private com `StripeClient` mockado), `SubscriptionControllerTest`, `EmailServiceTest`, `SubscriptionView.spec.ts`, `DashboardView.spec.ts`.

## Tasks & Acceptance

**Execution:**
- [x] `StripeBillingService.java` -- `resumeSubscription` -- desfazer o cancelamento agendado e devolver o fim do período
- [x] `SubscriptionService.java` -- `reactivate` + evento e e-mail de reativação no `activateMonthly` -- matriz acima
- [x] `SubscriptionController.java` (+ handler) -- `POST /api/v1/subscriptions/reactivate` → `SubscriptionResponse`
- [x] `EmailService.java` -- `sendSubscriptionReactivated`
- [x] Frontend (service, view, dashboard) -- botões e rótulos "Reativar assinatura", erros
- [x] Testes cobrindo toda a matriz (backend) + view/dashboard (frontend)

**Acceptance Criteria:**
- Dado uma assinatura cancelada ainda ativa, quando clico "Reativar assinatura", então a tela volta a "Assinatura ativa", o Stripe deixa de mostrar o cancelamento agendado e chega o e-mail "Assinatura reativada".
- Dado uma assinatura expirada, quando clico "Reativar assinatura" e pago no Checkout, então a assinatura fica ativa e o audit registra `subscription_reactivated`.

## Implementation Notes

## Spec Change Log

## Review Triage Log

Revisão em 3 camadas (Blind Hunter, Edge Case Hunter, Verification Gap) — 2026-10-31.

| # | Origem | Achado | Veredito | Evidência | Rota |
|---|---|---|---|---|---|
| 1 | Blind, Edge, VG | Toda `InvalidRequestException` vira "assinatura já terminou" (id inválido, chave errada) | medium | catch único em `resumeSubscription` | patch (confere status `canceled` no Stripe) |
| 2 | Blind | Audit não guarda o cancelamento desfeito | low | metadata sem `cancellation_date` anterior; correção direta | patch |
| 3 | VG | Ramo timeout de `reactivate()` no front sem teste | medium | VG pré-verificado | patch |
| 4 | VG | E-mail reativada com data nula só testado como "não lança" | medium | VG pré-verificado | patch |
| 5 | Blind | Falta teste de `"via":"checkout"` no metadata | low | nenhuma asserção cobre | patch |
| 6 | Blind, Edge | Checkout antigo concluído com linha CANCELLED com acesso → duas assinaturas pagas sobrepostas | medium | Guard de duplicata só cobre ACTIVE; existia desde a 2.4 | defer |
| 7 | Blind, Edge | Stripe aceitou e commit falhou → divergência | low | Mesmo item #9 da 2.4, já adiado | reject (duplicado) |
| 8 | Blind, Edge | Stripe recusa (cancelada lá) e linha local segue CANCELLED com acesso: botão falha e Checkout dá 409 | low | Stripe só fica `canceled` emitindo `customer.subscription.deleted`, que marca EXPIRED em segundos; só preso se o webhook se perder | reject |
| 9 | Blind | Status `past_due`/`unpaid` no resume não conferido | false | Com `cancel_at_period_end` não há renovação no período; cancelada com acesso não chega a past_due | reject |
| 10 | Blind, Edge | id Stripe vazio → 503 permanente | false | CANCELLED só nasce pelo `cancel`, que exige id | reject |
| 11 | Blind | E-mail não diferencia cobrança hoje (checkout) | low | Texto genérico correto nos dois casos | reject |
| 12 | Blind | `isFormerSubscriber` definido em dois lugares | low | Regras equivalentes hoje | reject |
| 13 | Blind | Copy com `nextBillingDate` nulo | false | `hasAccess` de CANCELLED exige data | reject |
| 14 | Blind | Testes de mensagens 409 / IP repassado | low | Comportamento coberto por status; cosmético | reject |
| 15 | Edge | MONTHLY/ACTIVE sem stripe id tratado como reativação | false | ACTIVE mensal sempre recebe id na ativação | reject |
| 16 | Edge | 409 + reload falho mostra "assine novamente" falso | low | Duas falhas seguidas, raro | reject |
| 17 | Edge | Dashboard "Reativar" vs "Reativar assinatura" | false | Code Map pede "Reativar" no dashboard | reject |

## Verification

**Commands:**
- `cd porquinho-backend && ./mvnw test` -- 0 falhas
- `cd porquinho-frontend && npx vitest run && npx vue-tsc --build && npm run lint` -- verde

**Manual checks:**
- Com o `stripe listen` rodando: assinar → cancelar → reativar na UI → no painel do Stripe, a assinatura sem "cancela em…"; a linha local fica ACTIVE.
