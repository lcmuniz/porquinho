---
title: 'Checkout duplicado: estornar a cobrança e parar o 503 em loop'
type: 'bugfix'
created: '2026-11-01'
status: 'done'
baseline_commit: 16242c1f7fb17636f6d52fbb6297cbbaf48950b1
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-2-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Fora do trial, dois Checkouts concluídos (duas abas, ou um clique repetido) cobram duas vezes. O porquinho cancela a 2ª assinatura, mas não devolve o dinheiro. O `invoice.paid` dela não acha a linha local e, por ser `subscription_create`, recebe 503 a cada reentrega do Stripe durante dias. O mesmo vale para quem já foi purgado: o checkout é ignorado e a assinatura no Stripe continua viva. É o achado F2 da retro do Epic 2.

**Approach:** Quando uma assinatura chega como duplicata (ou para um usuário que não existe mais), o porquinho cancela no Stripe e trata a cobrança dela. O `invoice.paid` de uma assinatura desconhecida que o Stripe diz estar cancelada recebe 200, não 503.

## Boundaries & Constraints

**Always:**
- Um `invoice.paid` com `subscription_create` e assinatura desconhecida consulta o Stripe. Se ela estiver `canceled`, responde 200 e registra `log.warn`. Se estiver ativa, mantém o 503 de hoje (corrida com o `checkout.session.completed`, decisão A da 2.6). Se o Stripe estiver fora, também 503.
- `checkout.session.completed` de usuário sem linha (purgado): cancelar a assinatura nova no Stripe, best-effort, além do `log.error` de hoje.
- Fatura de R$ 0 (duplicata durante o trial): nada a devolver.
- Nunca gravar a cobrança da duplicata em `billing_transactions` e nunca enviar recibo dela.
- Chamadas ao Stripe que não sejam decisivas para a resposta (cancelamento e devolução) são best-effort: falha gera `log.error` com os ids para ação manual.
- **Decisão (Luiz, 2026-11-01) A:** estorno automático e total da fatura paga da assinatura duplicada (ou do usuário excluído/purgado), via Stripe Refund, depois de cancelá-la.

**Never:**
- Mudar o fluxo de quem assina uma única vez.
- Customer Portal, nova migration ou e-mail novo.

## I/O & Edge-Case Matrix

| Cenário | Estado | Resultado | Erro |
|---|---|---|---|
| Duplicata paga | linha MONTHLY/ACTIVE (ou PAYMENT_FAILED) + checkout com sub nova | sub nova cancelada; cobrança tratada (ver decisão); linha inalterada | Stripe falha → log error |
| Duplicata no trial | idem, fatura R$ 0 | sub nova cancelada; nada a devolver | — |
| `invoice.paid` da duplicata | sub desconhecida, `subscription_create`, Stripe `canceled` | 200, nada gravado | log warn |
| `invoice.paid` antes do checkout | sub desconhecida, `subscription_create`, Stripe ativa | 503 (como hoje) | — |
| Stripe fora no `invoice.paid` | consulta lança | 503 | — |
| Checkout de usuário purgado | sem linha local | sub nova cancelada; 200 | log error |

</frozen-after-approval>

## Code Map

- `porquinho-backend/src/main/java/com/porquinho/service/SubscriptionService.java:281-288` -- ramo de duplicata do `activateMonthly`: hoje só `cancelSubscription` (best-effort). Após `cancelSubscription`, chamar `refundLatestInvoice`. O ramo de usuário excluído (F1, logo acima) e o checkout de purgado fazem o mesmo.
- `porquinho-backend/src/main/java/com/porquinho/controller/StripeWebhookController.java:135-141` -- `UNKNOWN_SUBSCRIPTION` + `subscription_create` → hoje 503. Consultar o status no Stripe antes de decidir. `:229` (catch de `ResourceNotFoundException` no checkout): acrescentar o cancelamento.
- `porquinho-backend/src/main/java/com/porquinho/service/StripeBillingService.java` -- `retrieveSubscription` (`:167`) devolve `SubscriptionDetails(customerId, paymentMethodId, currentPeriodEnd)`: acrescentar `status`. Criar `refundLatestInvoice(subscriptionId)` best-effort: na API `2026-10-28.endive` (stripe-java 34.1.0) a fatura não traz `charge`/`payment_intent` no topo; o pagamento fica em `invoice.payments` (expandir) → `payment.payment_intent` → `refunds.create(payment_intent)`. Conferir no SDK antes de codar. `cancelSubscription` (`:183`) segue best-effort.
- Testes: `SubscriptionServiceTest` (duplicata, com e sem valor), `StripeWebhookControllerTest` (as três variantes de `invoice.paid` desconhecido e o checkout de usuário purgado), `StripeBillingServiceTest` (status no retrieve e refund com sucesso, sem pagamento e com falha).

## Tasks & Acceptance

**Execution:**
- [x] `StripeBillingService.java` -- status no retrieve + `refundLatestInvoice`
- [x] `SubscriptionService.java` -- duplicata trata a cobrança
- [x] `StripeWebhookController.java` -- `invoice.paid` desconhecido consulta o Stripe; checkout de purgado cancela
- [x] Testes -- toda a matriz

**Acceptance Criteria:**
- Dado um assinante ativo que conclui um 2º Checkout, quando os webhooks chegam, então a 2ª assinatura aparece cancelada no Stripe, a cobrança dela é estornada e o endpoint responde 200 ao `invoice.paid` dela.

## Spec Change Log

## Review Triage Log

Revisão em 3 camadas (Blind Hunter, Edge Case Hunter, Verification Gap) — 2026-11-01. A saída do Edge Case Hunter veio parcialmente comprimida; os achados legíveis estão abaixo.

| # | Origem | Achado | Veredito | Evidência | Rota |
|---|---|---|---|---|---|
| 1 | Blind, Edge | Reembolso roda mesmo quando o cancelamento falhou: a duplicata segue ativa, a 1ª cobrança é devolvida e as renovações continuam | medium | `cancelSubscription` é `void` best-effort e `refundLatestInvoice` vem logo depois sem checar | patch |
| 2 | Blind | Motivo `DUPLICATE` fixo também no caminho de usuário excluído/purgado | low | `refundLatestInvoice` fixa `Reason.DUPLICATE`; correção direta (parâmetro) | patch |
| 3 | Blind | Nomes totalmente qualificados e campos no meio de `StripeBillingServiceTest` | low | Correção direta | patch |
| 4 | Blind, Edge, VG | Chamadas ao Stripe com a linha travada (`findByUserIdForUpdate`) | low | Só no ramo raro de duplicata; mesmo padrão do `cancelSubscription` que já estava ali | reject |
| 5 | Blind, Edge | Reentrega do checkout duplicado gera log "faça à mão" falso (já cancelada / já reembolsada; chave de idempotência dura 24h) | low | `checkout.session.completed` só é reentregue após resposta não-2xx; falso alarme, sem dinheiro duplicado | reject |
| 6 | Blind, Edge | Só `canceled` é tratado como terminal; `incomplete_expired`/404 caem no 503 | false | Sub `incomplete_expired` não emite `invoice.paid`; assinaturas não somem do Stripe | reject |
| 7 | Blind | Reembolso sem audit nem e-mail | low | Metadata do refund no Stripe registra sub e fatura; intenção não pede e-mail | reject |
| 8 | Blind, Edge | Fatura paga parcialmente com crédito/saldo | low | Só cartão no Checkout; sem saldo de cliente no fluxo | reject |
| 9 | Edge | Pagamento assíncrono liquidado depois do cancelamento | false | Checkout só aceita cartão (Story 2.2) | reject |
| 10 | Blind | Teste best-effort sem asserções e faltando casos (vários payments, nulos) | low | O contrato do método é "não lança"; casos principais cobertos (VG) | reject |
| 11 | Edge | Chamada extra ao Stripe na corrida de quem assina uma vez | low | Resposta inalterada (503); uma consulta a mais por reentrega | reject |

## Verification

**Commands:**
- `cd porquinho-backend && ./mvnw test` -- 0 falhas

**Manual checks:**
- Sandbox: com a assinatura de teste ativa, concluir outro Checkout (4242) → no Stripe, a 2ª assinatura `canceled` e a fatura reembolsada; log do `stripe listen` mostra 200 no `invoice.paid` dela.
