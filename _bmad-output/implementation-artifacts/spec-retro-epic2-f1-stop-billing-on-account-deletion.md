---
title: 'Parar a cobrança do Stripe no pedido de exclusão de conta'
type: 'bugfix'
created: '2026-11-01'
status: 'done'
baseline_commit: 8bf8e4756c68f7f84d40b1eb365f9438fbeaf0a2
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-2-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Quem pede a exclusão da conta perde o acesso na hora (sessões revogadas, usuário soft-deletado), mas a assinatura no Stripe só é cancelada no purge, 30 dias depois. Nesse intervalo o Stripe pode renovar, fazer a 1ª cobrança pós-trial ou repetir uma fatura recusada, e o link de pagamento já enviado por e-mail continua aceitando pagamento. É o achado F1 da retro do Epic 2 (`epic-2-retro-2026-11-01.md`).

**Approach:** No pedido de exclusão, cancelar a assinatura no Stripe **imediatamente**, anular a fatura em aberto quando a linha está em `PAYMENT_FAILED` e marcar a linha local `EXPIRED`. O cancelamento no purge continua como rede de segurança.

## Boundaries & Constraints

**Always:**
- A exclusão nunca falha nem atrasa por causa do Stripe. As chamadas são best-effort, depois do commit, e uma falha registra `log.error` pedindo o cancelamento manual.
- Linha local: `EXPIRED`, `cancellation_date` = agora, `payment_update_url` nulo, tudo na mesma transação do pedido. O `customer.subscription.deleted` que chega depois é no-op, como já acontece com uma linha EXPIRED.
- Audit `subscription_cancelled` com `reason: "account_deletion"` e `immediate: true`.
- Fazer só quando existe `stripe_subscription_id` e a linha não está `EXPIRED` nem `SUSPENDED`. TRIAL sem Stripe: nada muda.
- Void só quando o status anterior era `PAYMENT_FAILED`, usando o `latest_invoice` devolvido pelo cancelamento. Uma fatura paga não é anulável.
- A lógica fica no `SubscriptionService`. O `AccountDeletionService` só a chama, para não espalhar regra de assinatura.
- Pedido repetido (idempotência da exclusão): não chama o Stripe de novo.
- **Decisão (Luiz, 2026-11-01) A:** sem estorno. A cobrança para a partir do pedido e o período corrente não é devolvido.

**Never:**
- Enviar e-mail de cancelamento. O usuário acabou de receber o de exclusão, e `findById` não acha usuário soft-deletado.
- Mudar a janela de 30 dias, o purge ou a retenção de `billing_transactions`, que é uma decisão adiada.
- Nova migration.

## I/O & Edge-Case Matrix

| Cenário | Estado | Resultado | Erro |
|---|---|---|---|
| Mensal ativo | MONTHLY/ACTIVE, sub id | Stripe cancela na hora; linha EXPIRED; audit | — |
| Assinou no trial | MONTHLY/ACTIVE, trial no Stripe | idem, e o Stripe não cobra ao fim do trial | — |
| Cancelado com acesso | MONTHLY/CANCELLED | idem | — |
| Pagamento pendente | MONTHLY/PAYMENT_FAILED | idem + void do `latest_invoice` | void falha → log error |
| Só trial | TRIAL sem sub id | nada no Stripe; linha inalterada | — |
| Já encerrada | EXPIRED / SUSPENDED | nada | — |
| Stripe fora | cancelamento lança | exclusão concluída normalmente; linha EXPIRED; o purge tenta de novo | log error "cancele à mão" |
| Pedido repetido | PENDING_PURGE já existe | nada no Stripe | — |
| Webhook posterior | `customer.subscription.deleted` | no-op (linha EXPIRED) | — |

</frozen-after-approval>

## Code Map

- `porquinho-backend/src/main/java/com/porquinho/service/AccountDeletionService.java:218` -- trocar o comentário "billing stops at purge" pela chamada ao novo método do `SubscriptionService` dentro de `executeDeletionTransaction`, depois do audit `account_deletion_requested` e só no caminho `newlyCreated`. Injetar `SubscriptionService` (não há ciclo de dependência: `SubscriptionService` não depende de `AccountDeletionService`).
- `AccountDeletionService.java:313-315` -- o `cancelSubscription` do purge **fica** como rede de segurança. Um "já cancelada" do Stripe ali vira um warn inofensivo.
- `porquinho-backend/src/main/java/com/porquinho/service/SubscriptionService.java` -- novo método `cancelForAccountDeletion(UUID userId)` (`@Transactional`, lock `findByUserIdForUpdate`). Reusar `afterCommit` (`:715`), o padrão do `cancelImmediately(Subscription, ip)` privado (`:441`, da 2.7) e o `voidOpenInvoice` (2.7). Não reutilizar o `cancel()` público: ele lança 409/503 e envia e-mail.
- `porquinho-backend/src/main/java/com/porquinho/service/StripeBillingService.java:237` -- `cancelImmediately(id)` devolve o `latest_invoice` e lança `PaymentProviderUnavailableException`. Capturar a exceção no `afterCommit` e logar em error. `voidInvoice` (`:260`) já é best-effort.
- Testes: `AccountDeletionServiceTest` (construtor `:93`/`:98`: acrescentar `SubscriptionService` mockado; testar que é chamado só em `newlyCreated`) e `SubscriptionServiceTest` (matriz acima, com void depois do commit, como nos testes da 2.7).

## Tasks & Acceptance

**Execution:**
- [x] `SubscriptionService.java` -- `cancelForAccountDeletion` -- matriz acima
- [x] `AccountDeletionService.java` -- chamar no pedido novo; manter o cancelamento do purge
- [x] Testes -- toda a matriz + chamada só em `newlyCreated`

**Acceptance Criteria:**
- Dado um assinante mensal ativo, quando ele pede a exclusão da conta, então a assinatura aparece cancelada no Stripe na hora e nenhuma cobrança acontece nos 30 dias até o purge.

## Spec Change Log

## Review Triage Log

Revisão em 3 camadas (Blind Hunter, Edge Case Hunter, Verification Gap) — 2026-11-01. A saída do Edge Case Hunter chegou truncada pela compressão e o hash expirou: recuperei 5 achados completos e 2 parciais.

| # | Origem | Achado | Veredito | Evidência | Rota |
|---|---|---|---|---|---|
| 1 | Edge | Checkout aberto antes do pedido e concluído depois (ou `checkout.session.completed` antigo reentregue com sub id novo): `activateMonthly` reativa a linha EXPIRED de um usuário soft-deletado e o Stripe volta a cobrar | medium | `activateMonthly` (`SubscriptionService.java:268-290`) não olha se o usuário foi excluído; `findById` respeita `@Where(deleted_at IS NULL)` | patch |
| 2 | Blind, Edge | Falha no cancelamento do Stripe: a única nova tentativa é no purge, 30 dias depois, e uma renovação pode cair no intervalo | low | Matriz congelada aprovada: "Stripe fora → exclusão concluída; o purge tenta de novo; log error" | reject (intenção aprovada) |
| 3 | Blind | O cancelamento do purge vai registrar um warn de "já cancelada" para todo assinante | low | Previsto no Code Map ("warn inofensivo") | reject |
| 4 | Blind, Edge | O purge não refaz o void quando o void do pedido falhou | low | `voidOpenInvoice` já registra `log.error` pedindo void manual | reject |
| 5 | Blind | Void só em PAYMENT_FAILED; ACTIVE pode ter fatura finalizada e ainda não paga | low | Janela entre finalização e cobrança automática é curta; a intenção congelada limita o void a PAYMENT_FAILED | reject |
| 6 | Blind | O audit `subscription_cancelled` é gravado antes da confirmação do Stripe | low | Falha registrada em `log.error`; fix adiciona outro evento | reject |
| 7 | Blind | Purge ainda chama o Stripe pelo `AccountDeletionService` | low | Pré-existente; já é o achado A8 da retro | reject (duplicado) |
| 8 | Blind, VG | Nenhum teste com transação Spring real prova que o Stripe só é chamado após o commit externo | medium | VG pré-verificado; mesmo padrão dos outros fluxos com `afterCommit` | defer |
| 9 | Blind | Testes faltando: PAYMENT_FAILED com `latest_invoice` nulo, id em branco, trial real no Stripe | low | Caminho nulo do `voidOpenInvoice` já testado na 2.7 | reject |
| 10 | Blind | Teste de corrida assume que a transação segue após `DataIntegrityViolationException` | low | Caminho pré-existente (Story 1.7); o teste novo só garante que o Stripe não é chamado | reject |
| 11 | Blind | Nomes totalmente qualificados no meio dos testes, fora do padrão de imports | low | Correção direta | patch |
| 12 | Edge | Achado sobre linha CANCELLED, saída truncada | maybe-false | Texto perdido na compressão; o caso CANCELLED está na matriz e coberto por teste | reject |
| 13 | Edge | Claims sobre a abordagem e o AC, saída truncada | maybe-false | Texto perdido; a AC é coberta pelos testes do caso ACTIVE | reject |

## Verification

**Commands:**
- `cd porquinho-backend && ./mvnw test` -- 0 falhas

**Manual checks:**
- Sandbox Stripe: pedir a exclusão de uma conta de teste com assinatura ativa → no Stripe a assinatura fica `canceled`; a linha local fica EXPIRED.
