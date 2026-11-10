---
title: 'Story 6.6: deduzir da categoria ao classificar uma transação'
type: 'chore'
created: '2026-11-10'
status: 'done'
route: 'oneshot'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-6-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** A story pede que o gasto da categoria acompanhe sozinho as transações: entra ao classificar, sai da categoria antiga e entra na nova ao trocar, sai ao remover, e créditos e parcelas futuras ficam de fora. A 6.3/6.4 já resolveu isso calculando o gasto a partir das transações (sem `spent_amount` armazenado, decisão do épico) e invalidando o cache em todos os pontos de escrita. Faltam testes que provem a troca de categoria e a troca de mês pelo serviço real.

**Approach:** Nenhum código de produção novo. Um teste de integração em `BudgetIntegrationTest` usa `TransactionService` para criar um débito em Mercado e confirma o gasto. Depois troca a categoria para Transporte (Mercado −, Transporte +), muda a data para o mês seguinte (sai do mês corrente, entra no seguinte) e cria um crédito, que não altera o gasto. Uma categoria sem alocação segue sem linha em `budget_allocations`.

</frozen-after-approval>

## Implementation Notes

- Mapeamento dos ACs da story para o que já existe:
  - Débito classificado entra no gasto: `TransactionRepository.sumSpentByCategory`. Já testado em `onlyConfirmedRegularDebitsOfTheMonthAreSpent`, que também cobre crédito, `CARD_PAYMENT`, `PENDING`, parcela futura e removida.
  - Edição, remoção e criação invalidam o cache: `creatingEditingAndDeletingATransactionInvalidatesTheCache`.
  - Conclusão da importação, que confirma futuras: `completingAnImportInvalidatesTheCache`.
  - Remoção de futuras: `removingFutureInstallmentsInvalidatesTheCache`.
  - Sem alocação, nenhuma linha é criada: `aMonthWithoutAllocationsHasEveryChildWithNullAndTheSpentComputed`.
- "Atômico": o gasto é derivado e o evict roda depois do commit (6.4). Não há o que dessincronizar.
- O cache do Dashboard (`dashboard:layer*`) ainda não existe e fica para o Epic 7, como já decidido na 6.4.
- `BudgetIntegrationTest.theSpentFollowsTheTransactionAcrossCategoriesAndMonths` cobre troca de categoria, troca de mês, desclassificação (categoria `null`) e crédito. O `tearDown` passou a apagar `user_classification_patterns`, porque o `update` aprende um padrão quando a categoria muda.

## Review Triage Log

| # | Finding | Verdict | Evidence / Route |
|---|---------|---------|------------------|
| 1 | Ordem do teste diferente do Approach (crédito no início) | low | patch: crédito movido para o fim |
| 2 | Desclassificação (categoria `null`) sem teste | medium | patch: passo novo no teste |
| 3 | `CategoryService.delete` sem evict | false | `build` lê as categorias ativas do banco a cada GET; a removida some mesmo com cache quente. TTL aceito na 6.4 |
| 4 | Troca de mês checada só pela metade | low | patch: assert de `food` no mês corrente |
| 5 | Linha de cleanup sem comentário | low | patch: comentário de uma linha |
| 6 | Spec sem seção de verificação | low | rejeitado: o template oneshot remove a seção |
| 7 | `applyUserCategoryToFutures` sem evict | false | Atua só em importação em review (não `CONFIRMED`), fora de gasto e comprometido |
