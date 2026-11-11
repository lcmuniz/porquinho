---
title: 'Story 6.8: histórico de alocações da categoria'
type: 'feature'
created: '2026-11-11'
status: 'done'
baseline_commit: 'f6b6f16826b662f52d01ce068ff8715c71551fe1'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-6-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Hoje o usuário vê o orçamento só mês a mês em `/orcamento`. Não há como ver como alocado e gasto de uma categoria evoluíram ao longo dos meses.

**Approach:** Um endpoint novo devolve, em uma chamada, todos os meses da categoria até o mês corrente, com alocado, gasto, restante e status. Uma tela nova mostra filtro de período, estatísticas, gráfico de linhas alocado × gasto, tabela ordenável por mês e export CSV. O filtro, as estatísticas e o CSV são calculados no front, sobre a mesma resposta.

## Boundaries & Constraints

**Always:**
- `GET /api/v1/budgets/categories/{categoryId}/history`: categoria filha ativa do usuário do JWT. Se for grupo, removida, de outro usuário ou inexistente, devolve 404 "Categoria não encontrada". Id malformado devolve 400.
- Gasto do mês com a mesma base de `sumSpentByCategory` (débitos REGULAR confirmados, sem parcela futura, sem removidos). `remaining` e `status` usam `BudgetService.status`, a regra única do épico. Meses futuros ficam de fora.
- Resposta: `{ categoryId, categoryName, months: [{ month: "AAAA-MM", allocated, spent, remaining, status }] }`, do mais antigo para o mais novo. Sem cache: são duas queries por chamada (alocações da categoria e gasto agrupado por mês).
- Filtro: 3 meses, 6 meses, 1 ano e Tudo. "N meses" são os N últimos meses-calendário contando o corrente. O padrão é 1 ano. Trocar o filtro não faz nova chamada.
- Estatísticas sobre os meses filtrados: média mensal alocada, média mensal gasta, meses estourados (status OVERSPENT) e melhor mês (menor gasto; no empate, o mais recente), escrito como "novembro de 2026".
- Tabela com Mês, Alocado, Gasto, Restante e Status (ícone + texto, mesmas cores e rótulos da 6.4/6.7). Ordenada por mês, a mais recente primeiro, e o cabeçalho Mês alterna a ordem com `aria-sort`.
- Gráfico em SVG feito à mão, sem biblioteca, como o `BalanceChart`: duas linhas (Alocado em roxo `#7c3aed`, Gasto em `#DC2626` tracejado), eixo X com os meses, eixo Y em R$ e legenda em texto. A tabela é a alternativa acessível.
- CSV gerado no front (Blob): separador `;`, decimal com vírgula, BOM UTF-8, colunas `Mês;Alocado;Gasto;Restante;Status`, só os meses filtrados, arquivo `historico-<slug-da-categoria>.csv`.
- Decisão (Luiz, 2026-11-11): a entrada é a rota `/orcamento/categorias/:id/historico`, com um link "Histórico" em cada categoria de `/orcamento`.
- Decisão (Luiz, 2026-11-11): entram só os meses com registro em `budget_allocations`. Gasto em mês sem orçamento não aparece.
- Lista vazia no período: "Nenhum mês com orçamento neste período". Estatísticas, gráfico e CSV ficam ocultos.

**Never:** tabela ou migration nova. Mudanças em `GET/PUT /budgets/{month}` ou na regra de status. Histórico de grupos (6.9). Export PDF. Cache Redis do histórico.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Feliz | 14 meses alocados, filtro 1 ano | 12 linhas, a mais nova no topo; estatísticas dos 12 | N/A |
| Gasto em mês não alocado | gasto em março, sem alocação | março não aparece | N/A |
| Mês futuro alocado | alocação em dezembro, hoje é novembro | dezembro não aparece | N/A |
| Grupo / removida / alheia | id de grupo | — | 404 "Categoria não encontrada" |
| Empate no melhor mês | dois meses com gasto 0 | o mais recente | N/A |
| Nada no período | sem meses no filtro de 3 meses | mensagem de vazio, sem CSV | N/A |

</frozen-after-approval>

## Code Map

- `porquinho-backend/.../controller/BudgetController.java` -- adicionar `GET /categories/{categoryId}/history`. Não conflita com `/{month}`.
- `porquinho-backend/.../service/BudgetService.java` -- método `history`. Reaproveitar `status(...)`, `children` e o `SAO_PAULO`/`clock` para o mês corrente. 404 com `CategoryService.NOT_FOUND_MESSAGE` via `ResourceNotFoundException`.
- `porquinho-backend/.../repository/CategoryRepository.java#findActiveByIdAndUserId` -- lookup pronto (falta checar `parentCategoryId != null`).
- `porquinho-backend/.../repository/BudgetAllocationRepository.java` -- nova `findByUserIdAndCategoryIdAndMonthYearLessThanEqualOrderByMonthYear`.
- `porquinho-backend/.../repository/TransactionRepository.java` -- nova `sumSpentByMonth(userId, categoryId, from, to)`, com os mesmos filtros de `sumSpentByCategory` (linha ~231) e `GROUP BY YEAR(t.transactionDate), MONTH(t.transactionDate)`.
- `porquinho-backend/.../dto/` -- novo `CategoryBudgetHistoryResponse` (record).
- `porquinho-backend/src/test/.../BudgetControllerTest.java`, `BudgetIntegrationTest.java` -- estender.
- `porquinho-frontend/src/views/AccountHistoryView.vue` + `components/accounts/BalanceChart.vue` -- modelo de layout, seletor de período, estados de carga/erro/404 e SVG. Não alterar.
- `porquinho-frontend/src/views/BudgetView.vue` -- só o link "Histórico" por categoria. `STATUS`/`statusOf` têm ícones e rótulos: extrair para `src/lib/budgetStatus.ts` se as duas telas usarem.
- `porquinho-frontend/src/lib/money.ts#formatBRL`, `src/services/budgetService.ts`, `src/types/budget.ts`, `src/router/index.ts`.

## Tasks & Acceptance

**Execution:**
- [x] `TransactionRepository.java`, `BudgetAllocationRepository.java` -- queries novas -- gasto por mês e alocações da categoria.
- [x] `dto/CategoryBudgetHistoryResponse.java`, `BudgetService.java`, `BudgetController.java` -- endpoint e regra de 404 -- fonte única dos dados.
- [x] `BudgetIntegrationTest.java` / `BudgetControllerTest.java` -- matriz no backend: feliz, mês futuro, gasto fora da base (crédito, pendente, parcela futura), 404 de grupo, removida e alheia, e 400 de id malformado.
- [x] `src/types/budget.ts`, `src/services/budgetService.ts`, `src/router/index.ts` -- tipo, `getCategoryBudgetHistory` e rota.
- [x] `src/lib/budgetStatus.ts` (extraído de `BudgetView.vue`), `src/components/budget/AllocationHistoryChart.vue`, `src/views/CategoryBudgetHistoryView.vue` -- tela, gráfico, filtro, estatísticas, tabela e CSV.
- [x] `BudgetView.vue` -- link "Histórico" por categoria.
- [x] `src/views/__tests__/CategoryBudgetHistoryView.spec.ts`, `src/components/budget/__tests__/AllocationHistoryChart.spec.ts` -- filtro, estatísticas, empate, ordenação, vazio, 404, erro e conteúdo do CSV.

**Acceptance Criteria:**
- Given a categoria Mercado com 14 meses alocados, when abro o histórico, then vejo 12 meses (filtro 1 ano), o gráfico com duas linhas e as quatro estatísticas. When escolho "Tudo", vejo os 14 sem nova requisição.
- Given o histórico aberto, when clico em "Exportar CSV", then baixo um arquivo com os meses filtrados e valores no formato `1.234,56`.

## Implementation Notes

## Spec Change Log

## Review Triage Log

| # | Finding | Verdict | Evidence / Route |
|---|---------|---------|------------------|
| 1 | `revokeObjectURL` logo após `click()` pode abortar o download (Blind + Edge) | low | patch: revoke em `setTimeout(…, 0)` |
| 2 | Teste do histórico não semeia `CARD_PAYMENT` nem débito removido, então os filtros duplicados de `sumSpentByMonth` ficam sem cobertura (Verification Gap + Blind) | medium | patch: duas linhas a mais no teste de integração, gasto esperado continua 120,00 |
| 3 | "Voltar ao orçamento" volta ao mês corrente, não ao mês de onde o usuário veio | low | rejeitado: `/orcamento` nunca guardou o mês na URL; o fix exige estado novo de rota |
| 4 | Mês com alocado 0 e gasto 0 aparece como LOW e entra nas médias e no melhor mês | false | É a regra da 6.4 ("Alocado 0 e gasto 0 dá LOW"); é um mês alocado de verdade |
| 5 | Ticks do eixo Y com valores quebrados; `PAD.left` pode cortar rótulos grandes | low | rejeitado: cosmético, mesmo esquema do `BalanceChart`; "R$ 100.000,00" cabe em 96 px |
| 6 | Sem teste do gráfico com um mês só ou tudo zero | low | rejeitado: `xOf`/`yMax` tratam os dois casos de forma direta |
| 7 | `watch(route.params.id)` sem teste; filtro e ordem não voltam ao padrão (Blind + Verification Gap) | low | rejeitado: nenhum link da tela troca de categoria; só alcançável por URL manual |
| 8 | CSV sai em ordem cronológica, não na ordem da tabela; mês em minúscula | low | rejeitado: a spec não fixa ordem; cronológica é a leitura natural de um export |
| 9 | Dois links "Voltar ao orçamento" no estado 404 | low | rejeitado: mesmo padrão do `AccountHistoryView` (4.7) |
| 10 | Skeleton com `aria-busy` sem `role="status"` | low | rejeitado: padrão das views existentes, fora do escopo |
| 11 | Teste dos rótulos do eixo X aceita 0 rótulo | low | rejeitado: o teste também exige o último rótulo "nov" |
| 12 | Janela do filtro não muda se a página atravessar a virada do mês | low | rejeitado: caso raro; recarregar resolve |
| 13 | Relógio do navegador diferente do servidor desloca a janela do filtro | low | rejeitado: só perto da meia-noite da virada; o mesmo vale para `/orcamento` |

## Verification

**Commands:**
- `cd porquinho-backend && ./mvnw test -Dtest='Budget*'` -- passa
- `cd porquinho-frontend && npx vitest run src/views/__tests__/CategoryBudgetHistoryView.spec.ts src/components/budget src/views/__tests__/BudgetView.spec.ts` -- passa
- `cd porquinho-frontend && npm run lint && npx vue-tsc --build` -- sem erros
