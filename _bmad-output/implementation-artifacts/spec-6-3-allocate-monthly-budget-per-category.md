---
title: 'Story 6.3: alocar orçamento mensal por categoria'
type: 'feature'
created: '2026-11-09'
status: 'done'
baseline_commit: '82fb41081b548095bceabfab400b01dc0a7bad3b'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-6-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** O usuário não tem como dizer quanto pretende gastar em cada categoria no mês, nem como comparar esse valor com o que já gastou e com o que já está comprometido em parcelas.

**Approach:** Criar a tabela `budget_allocations` e os endpoints `GET`/`PUT /api/v1/budgets/{YYYY-MM}`, além da página `/orcamento`. A página tem navegação por mês, um campo de valor por categoria agrupado por grupo, gasto, comprometido, barra de progresso, total alocado × total disponível, "Repetir orçamento do mês anterior" e "Salvar orçamento".

## Boundaries & Constraints

**Always:**
- Só categorias filhas ativas do usuário recebem alocação. Grupos não recebem (decisão do Luiz).
- O gasto é calculado a cada leitura, sem coluna `spent_amount` nem `remaining_amount` (decisão do Luiz). Entram as transações com `CONFIRMED`, `futureInstallment=false`, `deletedAt IS NULL`, `kind=REGULAR`, `DEBIT` e data no mês. É a mesma base de `findConfirmedBetween`.
- Comprometido: só do mês corrente (America/Sao_Paulo) em diante. Soma as parcelas futuras ativas com data no mês e ignora as de cartão removido. Em mês fechado vem `null`.
- Total disponível é a soma de `currentBalance` das contas ativas. Cartões ficam de fora.
- O `PUT` substitui o conjunto do mês. Categoria sem valor não tem registro e a tela mostra "Sem orçamento definido". O valor 0 é uma alocação válida. Valor negativo, categoria repetida, grupo, categoria de outro usuário ou removida retornam 400 sem gravar nada.
- O usuário vem do JWT, os erros seguem RFC 7807 e o JSON usa camelCase. A tabela nova entra no export LGPD e no purge.
- Mensagem de sucesso: "Orçamento mensal definido". Aviso: "Você está alocando mais do que tem disponível".

**Decisões (Luiz, 2026-11-09):**
- Quando o total alocado passa do disponível, a tela só mostra o aviso e o salvamento continua. O backend não valida esse total.
- Qualquer mês pode ser aberto e editado, inclusive os que já fecharam.

**Never:** cache Redis, cores de status, alertas de saldo baixo ou estourado e totais acumulados no grupo (ficam para 6.4, 6.7 e 6.9). Também ficam fora a realocação (6.5) e transações sem categoria na tela.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Salvar | PUT com 2 filhas | 200 com o mês recalculado | N/A |
| Limpar uma categoria | PUT sem a categoria que tinha valor | registro apagado; `allocated=null` | N/A |
| Mês sem alocação | GET de mês novo | todas as filhas com `allocated=null`, gasto calculado | N/A |
| Gasto | débito confirmado, crédito, `CARD_PAYMENT`, PENDING, parcela futura | só o débito confirmado soma | N/A |
| Comprometido | futura no mês corrente; futura de cartão removido; mês passado | soma só a primeira; mês passado `null` | N/A |
| Mês inválido | `2026-13` | 400 | mensagem na tela |
| Categoria inválida | grupo, removida, de outro usuário, repetida ou valor < 0 | 400 sem gravar nada | modal/tela mantém valores |
| Repetir mês anterior | anterior com 3 alocações | campos preenchidos com esses valores; nada salvo até "Salvar" | anterior vazio: aviso "O mês anterior não tem orçamento" |

</frozen-after-approval>

## Code Map

- `porquinho-backend/src/main/resources/db/migration/V32__create_budget_allocations.sql` -- nova. Seguir o estilo da V30 (comentários em PT, `DEFAULT gen_random_uuid()` antes de `PRIMARY KEY` por causa do H2, `TIMESTAMP WITH TIME ZONE`, FKs sem ON DELETE). Colunas `id, user_id, category_id, month_year DATE, allocated_amount DECIMAL(15,2) CHECK >= 0, created_at, updated_at`, com UNIQUE (`user_id, category_id, month_year`). Sem `deleted_at`: limpar apaga a linha, porque é configuração e não lançamento financeiro (como `user_classification_patterns`).
- `entity/BudgetAllocation.java`, `repository/BudgetAllocationRepository.java` -- novos. No repositório: `findByUserIdAndMonthYear`, bulk `@Modifying` delete do mês (executa antes dos inserts, evitando o problema de ordem do flush do Hibernate com a UNIQUE), `findByUserIdOrdered` e `deleteByUserId` nativo (padrão de `UserClassificationPatternRepository`).
- `repository/TransactionRepository.java` -- duas queries agregadas `SELECT t.categoryId, SUM(t.amount) ... GROUP BY t.categoryId`: gasto (filtros da regra acima) e comprometido (`futureInstallment=true`, `deletedAt IS NULL`, `LEFT JOIN CreditCard c ON c.id = t.creditCardId` com `t.creditCardId IS NULL OR c.deletedAt IS NULL`).
- `repository/CategoryRepository.java#findActiveByUserIdForUpdate` -- usar como trava do PUT para serializar saves simultâneos. A lista de ativas também valida as categorias.
- `service/BudgetService.java` -- novo. `get(userId, month)` e `save(userId, month, items)`. Parse do mês com `YearMonth.parse` (400 via `BadRequestException`, como em `TransactionService.list`). O "mês corrente" usa `ZoneId.of("America/Sao_Paulo")` com `Clock` injetado.
- `controller/BudgetController.java`, `dto/BudgetResponse.java`, `dto/SaveBudgetRequest.java` -- novos. A resposta tem `month, totalAvailable, totalAllocated, categories[{categoryId, allocated|null, spent, committed|null}]` para cada filha ativa. O request é `{allocations:[{categoryId, amount}]}` com Bean Validation (`@NotNull`, `@DecimalMin("0")`, `@Digits(13,2)`).
- `service/DataExportService.java` (lista na linha ~225 e bloco ~485) e `service/AccountDeletionService.java` (~376, apagar antes de `categories`) -- incluir `budget_allocations`. Ajustar `DataExportServiceTest`/`AccountDeletionServiceTest`.
- `porquinho-frontend/src/types/budget.ts`, `services/budgetService.ts` -- novos, no padrão de `categoryService.ts`.
- `porquinho-frontend/src/views/BudgetView.vue` -- nova. Reaproveitar a navegação de mês de `TransactionsView.vue:45-52,387-412,505-507`, o agrupamento de `CategoriesView.vue` (`listCategories`), `parseBRL`/`formatBRL`/`formatBRLInput` de `lib/money.ts` e `Button`/`Input`. A barra de progresso é uma `div role="progressbar"` neutra, com o texto "R$ gasto de R$ alocado" visível.
- `porquinho-frontend/src/router/index.ts` -- rota `/orcamento` (`requiresAuth`). `views/DashboardView.vue:122` -- link "Orçamento" ao lado de "Categorias".

## Tasks & Acceptance

**Execution:**
- [x] Migration V32, entidade, repositório e queries agregadas.
- [x] `BudgetService` + `BudgetController` + DTOs conforme a matriz.
- [x] Export e purge LGPD com testes ajustados.
- [x] `BudgetIntegrationTest` -- matriz completa: gasto, comprometido (cartão removido, mês passado), PUT substitui, isolamento entre usuários e 400s sem gravar.
- [x] Front: tipos, serviço, `BudgetView.vue`, rota e link no dashboard.
- [x] `views/__tests__/BudgetView.spec.ts` -- carregar, salvar (mensagem), aviso de excesso, repetir mês anterior (com e sem dados), "Sem orçamento definido", troca de mês e erro 400.

**Acceptance Criteria:**
- Given salvei o orçamento de novembro, when recarrego `/orcamento`, then os valores salvos aparecem com o gasto e o restante (alocado − gasto) de cada categoria.
- Given o total alocado passa do disponível, when edito um campo, then o aviso aparece na hora, antes de salvar.
- Given uma categoria sem alocação, when a página carrega, then ela mostra "Sem orçamento definido" e o gasto do mês, sem barra de progresso.
- Given estou num mês fechado, when a página carrega, then o comprometido não aparece.

## Implementation Notes

## Spec Change Log

## Review Triage Log

| # | Layer | Finding | Verdict | Evidence | Route |
|---|-------|---------|---------|----------|-------|
| 1 | blind, edge | Comprometido soma parcela futura CREDIT ou não REGULAR | false | `InstallmentPlanner.java:91-92` só gera futuras de REGULAR DEBIT | rejeitado |
| 2 | edge | Comprometido conta futuras de importação ainda em revisão (PENDING/APPROVED) | medium | `confirmByImport` só confirma as futuras ao concluir; descartar a importação apaga o valor | patch |
| 3 | blind, edge | Salvar apaga a alocação de categoria removida depois | low | O bulk delete pega o mês inteiro; perde o histórico da 6.8. Correção direta no filtro | patch |
| 4 | blind | Gasto e comprometido tratam contas removidas de forma diferente | false | Gasto segue a base de `findConfirmedBetween` (decisão da spec); futuras só existem em cartão | rejeitado |
| 5 | blind | `month_year` sem CHECK de dia 1 | low | Só escrita fora do service quebraria; ninguém escreve fora | rejeitado (low) |
| 6 | edge | Ano gigante (`+999999999-12`) dá 500 | low | Só por chamada manual da API; correção pede guarda nova | rejeitado (low) |
| 7 | edge | Aviso de excesso com 0 alocado e saldo negativo | low | Cheque especial é caso real (`parseBRL` aceita negativo); correção direta | patch |
| 8 | blind, edge | "Salvar" ativo durante "Repetir" | low | Botão só desabilita com `saving`; correção direta | patch |
| 9 | blind, edge | Sucesso aparece no mês errado e persiste ao trocar de mês | low | `setSuccess` fora do `if`; `load()` não limpa | patch |
| 10 | edge | Repetir com A→B→A aplica valores sobre a recarga | low | Corrida rara; pede mais guarda | rejeitado (low) |
| 11 | blind, edge | Filha sem pai visível conta no total e vai no save | low | Corrida entre duas requisições; pede reestruturar a resposta | rejeitado (low) |
| 12 | blind | Sem estado visual de estouro; `aria-valuenow` limitado a 100 | low | Cores e estouro são da 6.7 (Never da spec) | rejeitado |
| 13 | blind | Salvar bloqueado por campo inválido sem feedback | low | O erro aparece inline no campo enquanto digita | rejeitado (low) |
| 14 | blind | Trocar de mês descarta o que foi digitado | low | Pede checagem de alterações pendentes | rejeitado (low) |
| 15 | blind | Aviso de excesso com `role=alert` reanuncia | low | Inofensivo; correção pede região viva fixa | rejeitado (low) |
| 16 | blind | Teste de integração usa relógio do sistema | low | Só falha na virada do mês | rejeitado (low) |
| 17 | blind | Faltam testes de borda do gasto e de caminhos de erro do front | low | Comportamento existe | rejeitado (low) |
| 18 | vgap | Purge e export nativos nunca rodam no Postgres | gap | Repositório só mockado nos testes de serviço | patch |
| 19 | vgap | Rota `/orcamento` e link do dashboard sem teste | gap | Grep sem ocorrência nos specs do router e do dashboard | patch |
| 20 | vgap, blind | Teste do PUT usa `any()` | gap | Não confere usuário, mês nem itens | patch |

## Verification

**Commands:**
- `cd porquinho-backend && ./mvnw test -Dtest='Budget*,DataExportServiceTest,AccountDeletionServiceTest,Category*'` -- expected: verde
- `cd porquinho-frontend && npm run lint && npx vue-tsc --build && npx vitest run` -- expected: sem erros
