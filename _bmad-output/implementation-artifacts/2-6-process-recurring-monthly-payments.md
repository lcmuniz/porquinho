---
title: 'Registrar pagamentos mensais recorrentes'
type: 'feature'
created: '2026-10-31'
status: 'done'
baseline_commit: dd9af80f90b04b64401d71c63ba11503e428b2ba
route: 'dispatch'
story_key: 2-6-process-recurring-monthly-payments
review_loop_iteration: 1
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-2-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** O Stripe já cobra a mensalidade todo mês, mas o porquinho não fica sabendo: `next_billing_date` para de ser atualizado depois da primeira cobrança, não fica histórico de pagamentos e o usuário não recebe recibo.

**Approach:** Tratar o webhook `invoice.paid`. Para cada fatura paga com valor > 0 de uma assinatura conhecida:
- grava uma linha na nova tabela `billing_transactions`;
- atualiza `next_billing_date` com o fim do período informado pelo Stripe;
- grava o audit `payment_success`;
- envia o e-mail de recibo "Pagamento confirmado".

Quem cobra continua sendo o Stripe. O porquinho não faz cobrança própria.

## Boundaries & Constraints

**Always:**
- Idempotente pelo id da fatura (`stripe_transaction_id` UNIQUE): um webhook repetido não grava de novo, não reenvia o e-mail nem duplica o audit.
- Valor em `DECIMAL(15,2)`, convertido de centavos (`amount_paid / 100`), e moeda em minúsculas (`brl`).
- E-mail best-effort enviado depois do commit, com o valor, a data do pagamento e a próxima cobrança (datas em SP).
- **Decisão (Luiz, 2026-10-31) A:** se a 1ª fatura de uma assinatura (`subscription_create`) chegar antes do `checkout.session.completed`, o webhook devolve 503 para o Stripe reenviar. Assim esse primeiro pagamento também é registrado.
- Fora esse caso, o webhook sempre devolve 200 para eventos ignorados (assinatura desconhecida, valor 0), com log warn ou debug.
- O purge de conta (Story 1.7) apaga `billing_transactions` do usuário antes de `subscriptions`. O export de dados inclui a tabela.

**Never:**
- Job próprio de cobrança (cron às 2h UTC) ou cobrança off-session com o `payment_method_id`. O Stripe já faz isso, e um job nosso cobraria duas vezes.
- Falha de pagamento, retry e `invoice.payment_failed`: Story 2.7.
- Tela de histórico de pagamentos no frontend (não pedida).

## I/O & Edge-Case Matrix

| Cenário | Estado | Resultado | Erro |
|---|---|---|---|
| Renovação paga | MONTHLY/ACTIVE, `invoice.paid` R$ 19,90 | grava transação `PAID`, `next_billing_date` = fim do novo período, audit, recibo | — |
| 1ª cobrança no fim do trial | assinou durante o trial | idem | — |
| Fatura de R$ 0 (assinatura criada no trial) | `amount_paid` = 0 | ignorada | log debug |
| Evento repetido | mesma fatura já gravada | nada muda | — |
| 1ª fatura chega antes do checkout | `billing_reason=subscription_create`, sub id ainda não gravado | 503 (o Stripe reenvia); nada muda | log info |
| Assinatura desconhecida | outra `billing_reason`, nenhuma linha com esse sub id | 200, nada muda | log warn |
| Fatura sem assinatura | fatura avulsa | 200, ignorada | log debug |
| Stripe fora ao buscar o período | `retrieveSubscription` falha | grava a transação; `next_billing_date` = `period_end` da linha da fatura | log warn |

</frozen-after-approval>

## Code Map

- `porquinho-backend/src/main/resources/db/migration/V13__create_billing_transactions_table.sql` -- NOVO. Colunas: `id`, `subscription_id` (FK, indexada), `user_id` (FK, indexada), `amount DECIMAL(15,2)`, `currency`, `status` (CHECK `PAID`; a 2.7 acrescenta `FAILED`), `payment_method`, `stripe_transaction_id` UNIQUE (id da fatura), `paid_at`, `created_at`. **Já existe e já foi aplicada no Supabase de dev (o devtools reiniciou o backend): não editar, porque quebra o checksum do Flyway.** Construir a entidade a partir dela.
- `.../entity/BillingTransaction.java`, `.../repository/BillingTransactionRepository.java` -- NOVOS. `existsByStripeTransactionId`, `deleteByUserId` nativo (padrão do `SubscriptionRepository:63`).
- `.../controller/StripeWebhookController.java:35-67` -- adicionar `INVOICE_PAID = "invoice.paid"`. O id da assinatura vem de `parent.subscription_details.subscription` na API 2026, ou de `subscription` nas versões antigas: ler os dois. Ler também `id`, `amount_paid`, `currency`, `status_transitions.paid_at` e `lines.data[0].period.end`. Reusar o helper `text(...)`.
- `.../service/SubscriptionService.java` -- `recordPayment(...)` `@Transactional`: lock (`findByStripeSubscriptionId` + `findByUserIdForUpdate`), idempotência, `next_billing_date` via `stripeBillingService.retrieveSubscription(id).currentPeriodEnd()` com fallback para o fim do período da linha, audit `payment_success` (`amount`, `currency`, `invoice_id`, `next_billing_date`) e `afterCommit` do e-mail.
- `.../service/EmailService.java` -- `sendPaymentReceipt(email, amount, paidAt, nextBillingDate)`, seguindo `sendSubscriptionActivated`. Assunto "Porquinho - Pagamento confirmado"; valor no formato `R$ 19,90`.
- `.../service/AccountDeletionService.java` -- no purge, apagar `billing_transactions` antes de `subscriptions`. Atualizar o Javadoc (linha 48) com a ordem.
- `.../service/DataExportService.java:158,206` -- incluir `billing_transactions` no export e na lista `exported_tables`.
- Testes: `StripeWebhookControllerTest`, `SubscriptionServiceTest`, `EmailServiceTest`, `AccountDeletionServiceTest`, `DataExportServiceTest`, MigrationTest (V13).
- Dev local: o `stripe listen` também precisa encaminhar `invoice.paid`.

## Tasks & Acceptance

**Execution:**
- [x] V13 + entidade + repository -- tabela `billing_transactions`
- [x] `StripeWebhookController` -- rotear `invoice.paid` e extrair os campos
- [x] `SubscriptionService.recordPayment` -- matriz acima
- [x] `EmailService.sendPaymentReceipt`
- [x] Purge e export -- incluir a tabela nova
- [x] Testes cobrindo toda a matriz, purge, export e migration

**Acceptance Criteria:**
- Dado uma assinatura mensal que renova, quando o Stripe cobra e envia `invoice.paid`, então `billing_transactions` ganha uma linha `PAID` de 19.90 BRL, `next_billing_date` avança para o fim do novo período e chega o e-mail "Pagamento confirmado".
- Dado uma conta excluída e purgada, quando o purge roda, então não sobra nenhuma linha em `billing_transactions` desse usuário.

## Implementation Notes

## Spec Change Log

- **Loop 1 (2026-10-31), intent_gap #1 do triage:** sem tratamento, a 1ª fatura paga fora do trial podia se perder quando chegava antes do checkout. Luiz escolheu a opção A (503 para `subscription_create` com assinatura ainda desconhecida), e a matriz e o Always foram atualizados. O código da 1ª tentativa foi revertido, menos a V13, que já estava aplicada. **KEEP:** a estrutura da 1ª tentativa funcionou: entidade e repository, `recordPayment` com idempotência pelo id da fatura, fallback para o `period_end` da linha, recibo enviado com `afterCommit`, purge e export. **Aplicar também os patches #2–#6 do triage:**
  - buscar a linha com `PESSIMISTIC_WRITE` direto pelo `stripe_subscription_id`, sem carregar a entidade antes, por causa do identity map;
  - `next_billing_date` só pode avançar;
  - teste de integração Testcontainers do `deleteByUserId` de `billing_transactions` na ordem do purge e da ordenação do export;
  - teste da fatura sem `id`;
  - imports em ordem.

## Review Triage Log

Revisão em 3 camadas (Blind Hunter, Edge Case Hunter, Verification Gap), 2026-10-31. A saída do Edge Case Hunter chegou truncada, e só foi possível recuperar 5 achados.

| # | Origem | Achado | Veredito | Evidência | Rota |
|---|---|---|---|---|---|
| 1 | Blind, Edge, VG, impl. | Na 1ª cobrança sem trial, o `invoice.paid` pode chegar antes do `checkout.session.completed`. A assinatura ainda é desconhecida, o webhook responde 200 e o pagamento e o recibo se perdem | high | O Stripe não garante a ordem dos eventos, e a matriz congelada manda responder 200 para id desconhecido | intent_gap |
| 2 | Blind | O lock não relê a linha: `findByStripeSubscriptionId` já carregou a entidade, e o `FOR UPDATE` devolve a mesma instância desatualizada. O filtro não serve para nada e uma atualização pode se perder | medium | Identity map do Hibernate; o teste usa dois objetos diferentes e por isso não pega | patch (buscar com lock direto pelo sub id) |
| 3 | Blind, Edge | `next_billing_date` pode voltar no tempo se uma fatura antiga chegar atrasada | medium | A data é atribuída sem comparar com a atual | patch (só avança) |
| 4 | VG | O `deleteByUserId` nativo de `billing_transactions` nunca roda contra um banco de verdade | medium | VG pré-verificado | patch (teste de integração) |
| 5 | VG | O caso da fatura sem `id`, que é ignorada, não tem teste | medium | VG pré-verificado | patch |
| 6 | Blind | Imports fora de ordem e dois imports na mesma linha | low | Correção direta | patch |
| 7 | Blind | O webhook de produção precisa assinar `invoice.paid` | low | Só o `.env.example` foi atualizado | defer (deploy) |
| 8 | Blind | A chamada ao Stripe acontece com o lock aberto | low | Mesmo padrão de cancel/reactivate | reject |
| 9 | Blind | Não trata `billing_reason`, proration nem outras linhas além de `lines.data[0]` | low | Plano único, sem troca de plano | reject |
| 10 | Blind, Edge | Moeda diferente de BRL | low | O preço é único, em BRL, criado pelo próprio app | reject |
| 11 | Blind | `payment_method` guarda o método padrão da assinatura, não o da fatura | low | Sem uso funcional hoje | reject |
| 12 | Blind | `data_version` do export não mudou | low | O campo novo é só adicionado, nada muda no que existia | reject |
| 13 | Edge | Um `invoice.paid` atrasado numa linha EXPIRED/CANCELLED reescreve a data | low | Caso raro; o patch do #3 já impede que a data recue | reject |
| 14 | Edge | `amount_paid` fracionário seria truncado | false | O Stripe manda valores inteiros, em centavos | reject |
| 15 | Blind | O ramo `stripeSubscriptionId == null` nunca é alcançado | low | É defensivo e não causa dano | reject |

Loop 2 (2026-10-31): re-revisão em 3 camadas da 2ª implementação.

| # | Origem | Achado | Veredito | Evidência | Rota |
|---|---|---|---|---|---|
| 16 | Blind, Edge | `currency` nulo → violação NOT NULL → 500 → Stripe reenvia por dias | low | Coluna NOT NULL e nenhum default; correção de uma linha | patch |
| 17 | Edge | Resultado DUPLICATE não deixa log | low | Correção direta | patch |
| 18 | Edge | Stripe fora e fatura sem period end: data não avança e nada é logado | low | Correção direta | patch |
| 19 | Blind, Edge | Status EXPIRED/CANCELLED não é olhado | low | carried #13 | reject |
| 20 | Blind, Edge | Chamada ao Stripe com o lock aberto | low | carried #8 | reject |
| 21 | Blind, Edge | Moeda diferente de BRL / `lines.data[0]` | low | carried #9, #10 | reject |
| 22 | Blind | `payment_method` vem da linha local | low | carried #11 | reject |
| 23 | Blind | `data_version` do export | low | carried #12 | reject |
| 24 | Blind | Webhook de produção sem `invoice.paid` | low | carried #7 | defer (já registrado) |
| 25 | Blind, Edge | 503 sem limite se o checkout nunca for processado | low | Só ocorre com endpoint mal configurado ou sem linha de assinatura; o teto exige ramo novo | reject |
| 26 | Edge | Purge concorrente com `invoice.paid` → violação de FK | low | No purge a assinatura Stripe já foi cancelada; a próxima execução refaz | reject |
| 27 | Edge | Mesma fatura por outro caminho → UNIQUE → 500 | false | Só existe um caminho, serializado pelo lock da linha | reject |
| 28 | Blind | Purge apaga o histórico financeiro; a lei fiscal pode exigir retenção (LGPD art. 16, I) | medium | Decisão jurídica/produto; a regra de purge está congelada | defer |
| 29 | Blind | Recibo sem número da fatura ou link | low | Exige parâmetro novo; ganho cosmético | reject |
| 30 | Blind | Índice `(user_id, paid_at)` | low | Volume pequeno por usuário | reject |
| 31 | Blind | Falta teste de integração do `recordPayment` | low | VG: sem lacunas | reject |

## Design Notes

O epic pede um cron às 2h UTC que cobra o `payment_method_id` e soma 30 dias ao `next_billing_date`. Ele foi escrito antes da decisão da Story 2.2 de usar Stripe Subscriptions, que já cobram sozinhas no ciclo. Um cron nosso cobraria duas vezes. Por isso esta story só observa: o webhook `invoice.paid` é a confirmação do pagamento (NFR59), e o período passa a vir do Stripe, em vez de somar 30 dias fixos.

## Verification

**Commands:**
- `cd porquinho-backend && ./mvnw test` -- 0 falhas

**Manual checks:**
- Com `stripe listen --events checkout.session.completed,customer.subscription.deleted,invoice.paid`: assinar sem trial (conta expirada → Reativar) → a fatura paga grava uma linha em `billing_transactions` e chega o recibo. Renovação: usar um Test Clock do Stripe para avançar o tempo.
