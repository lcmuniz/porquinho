---
title: 'Story 6.9: comparar planejado × real por categoria'
type: 'feature'
created: '2026-11-11'
status: 'done'
baseline_commit: '36227afa1fef3140535f7cf6db207dd66a4e349e'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-6-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `/orcamento` mostra o mês categoria por categoria, mas não dá uma visão comparativa de todas: onde o usuário está acima ou abaixo do planejado, com totais, gráfico e export.

**Approach:** Uma tela "Planejado × real" monta a comparação no front a partir do `GET /api/v1/budgets/{month}` que já existe (mais `listCategories` para nomes e grupos): tabela ordenável agrupada por grupo, gráfico de barras alocado × gasto, totais, filtros de status e período e export CSV.

## Boundaries & Constraints

**Always:**
- Linha por categoria filha: Categoria, Alocado, Gasto, Diferença (alocado − gasto), % (gasto ÷ alocado × 100, arredondado) e Status (ícone + texto via `src/lib/budgetStatus.ts`). Sem alocação: Alocado "—", % e Status "Sem orçamento".
- Grupos: linha do grupo com a soma das filhas visíveis, abre e fecha (`aria-expanded`), aberto por padrão. Ordenação vale dentro de cada grupo; grupos ordenados pelo mesmo critério sobre o total.
- Ordenação por qualquer coluna, `aria-sort` no cabeçalho; padrão: maior estouro (menor Diferença) no topo.
- Status de linha agregada e de grupo: mesma regra do épico (`OVERSPENT` < 0; `LOW` ≤ 20% do alocado; senão `HEALTHY`), recalculada no front sobre os somatórios só quando houver alocação.
- Filtro de status: Todas / Saudáveis / Baixas / Estouradas. "Sem orçamento" só aparece em Todas.
- Totais no rodapé (sobre as linhas visíveis): Total alocado, Total gasto, Total restante e "X% do orçamento utilizado" (gasto ÷ alocado; oculto se alocado = 0).
- Gráfico de barras em SVG feito à mão (padrão do `AllocationHistoryChart`), duas barras por categoria: Alocado em roxo claro e Gasto em roxo escuro, legenda em texto; a tabela é a alternativa acessível. Só categorias com alocação.
- CSV no front (mesmo formato da 6.8: `;`, vírgula decimal, BOM): `Grupo;Categoria;Alocado;Gasto;Diferença;Percentual;Status`, só as linhas filtradas, `planejado-x-real-<AAAA-MM>[_<AAAA-MM>].csv`.
- Tempo real: cada abertura e cada troca de filtro de período recarrega do servidor; o cache de 30 s da 6.4/6.6 já é invalidado nas escritas de transação.
- Estados: carregando, erro com "Tentar de novo", vazio "Nenhuma categoria para comparar neste período".
- Decisão (Luiz, 2026-11-11): período Mês atual / Mês passado / Personalizado (mês inicial e final, no máximo 12 meses, final ≥ inicial). O personalizado faz uma chamada `getBudget` por mês em paralelo e soma alocado e gasto por categoria no front; uma categoria conta como "com orçamento" se tiver alocação em ao menos um mês. Intervalo inválido mostra a mensagem "Escolha até 12 meses, com o mês final depois do inicial" e não chama o servidor. Padrão: mês atual.
- Decisão (Luiz, 2026-11-11): PDF via botão "Imprimir / salvar PDF" que chama `window.print()`, com CSS `print:` que esconde navegação, filtros e botões. Sem dependência nova.
- Decisão (Luiz, 2026-11-11): tela na rota `/orcamento/comparativo`, com link "Planejado × real" no topo de `/orcamento`.

**Never:** endpoint, tabela ou migration novos. Mudança na regra de status ou em `GET/PUT /budgets/{month}`. Biblioteca de gráfico.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Feliz | 5 filhas, 1 estourada | estourada no topo do seu grupo; totais e % corretos | N/A |
| Sem orçamento com gasto | filha com gasto 80 e sem alocação | Alocado "—", status "Sem orçamento"; some em "Estouradas" | N/A |
| Alocado 0, gasto 0 | alocação 0 | % "—"; status LOW (regra da 6.4) | N/A |
| Grupo fechado | clique no grupo | filhas somem, total do grupo fica | N/A |
| Filtro vazio | "Estouradas" sem nenhuma | mensagem de vazio, sem gráfico e sem CSV | N/A |
| Falha de rede | GET falha | mensagem + "Tentar de novo" | N/A |

</frozen-after-approval>

## Code Map

- `porquinho-frontend/src/services/budgetService.ts#getBudget`, `src/types/budget.ts#Budget/CategoryBudget` -- fonte de dados; não alterar.
- `porquinho-frontend/src/services/categoryService.ts#listCategories`, `src/types/category.ts` -- nomes e `parentCategoryId` para agrupar; `BudgetView.vue` (linhas ~150-200: `byCategory`, `groups`, `byName`) mostra como montar grupos.
- `porquinho-frontend/src/lib/budgetStatus.ts` -- `STATUS`/`statusOf`; adicionar aqui um `computeStatus(allocated, remaining)` (regra do épico) se as linhas agregadas precisarem. Não mudar rótulos.
- `porquinho-frontend/src/lib/date.ts` (`currentMonth`, `shiftMonth`, `formatMonthBR`), `src/lib/money.ts#formatBRL`.
- `porquinho-frontend/src/views/CategoryBudgetHistoryView.vue` -- modelo de estados, ordenação com `aria-sort` e geração do CSV (Blob, BOM, `revokeObjectURL` em `setTimeout`). Copiar o padrão, não alterar.
- `porquinho-frontend/src/components/budget/AllocationHistoryChart.vue` -- modelo do SVG (escala Y, ticks, legenda).
- `porquinho-frontend/src/views/BudgetView.vue` -- só o link para a tela nova.
- `porquinho-frontend/src/router/index.ts` -- rota nova (padrão da linha 127).

## Tasks & Acceptance

**Execution:**
- [x] `src/lib/budgetStatus.ts` -- `computeStatus` com a regra do épico -- status de linhas agregadas e grupos.
- [x] `src/components/budget/PlannedVsActualChart.vue` -- barras SVG alocado × gasto.
- [x] `src/views/BudgetComparisonView.vue` -- carga, agregação, grupos, ordenação, filtros, totais, CSV e impressão.
- [x] `src/router/index.ts`, `src/views/BudgetView.vue` -- rota e link.
- [x] `src/views/__tests__/BudgetComparisonView.spec.ts`, `src/components/budget/__tests__/PlannedVsActualChart.spec.ts`, `src/lib/__tests__/budgetStatus.spec.ts` -- matriz acima, ordenação padrão, colapso, filtro, totais e conteúdo do CSV.

**Acceptance Criteria:**
- Given um mês com alocações e gastos, when abro a comparação, then a categoria com maior estouro aparece no topo, o gráfico tem duas barras por categoria alocada e o rodapé mostra os quatro totais.
- Given a tabela filtrada em "Baixas", when exporto CSV, then o arquivo tem só as linhas baixas com valores no formato `1.234,56`.

## Implementation Notes

## Spec Change Log

## Review Triage Log

| # | Finding | Verdict | Evidence / Route |
|---|---------|---------|------------------|
| 1 | Cor "Alocado" `#c4b5fd` com contraste ~1,6:1 (Blind) | low | patch: tons roxos com ≥ 3:1 |
| 2 | Categoria removida some das linhas e totais (Blind + Edge) | false | `GET /budgets/{mês}` também só devolve filhas ativas (`children(findActiveByUserId)`); as duas fontes batem |
| 3 | Filha cujo pai não é grupo de topo some (Edge) | false | profundidade 1 garantida pela 6.2: pai sempre é grupo ativo |
| 4 | `input type="month"` vira texto no Safari/Firefox sem dica (Blind) | low | patch: `placeholder="AAAA-MM"` |
| 5 | Ordenar por % joga alocado 0 + gasto > 0 para o fim (Blind + Edge) | low | rejeitado: ordem padrão é Diferença, onde a linha fica no topo; fix exige ramo novo |
| 6 | Guarda de resposta atrasada sem teste (Blind + VG) | medium | patch: teste no padrão do `AccountsView.spec.ts` |
| 7 | Link "Planejado × real" sem asserção (Blind + VG) | low | patch: asserção no `BudgetView.spec.ts` |
| 8 | BOM literal invisível no CSV (Blind) | low | patch: `'\uFEFF'` |
| 9 | CSV sem neutralizar fórmula `=+-@` nem quebra de linha (Blind + Edge) | low | rejeitado: o usuário exporta os próprios nomes; mesmo comportamento do CSV da 6.8 |
| 10 | Impressão: header do App, grupo fechado sem indicador, `overflow-x-auto` (Blind) | low | rejeitado: header vazio hoje; grupo fechado é escolha do usuário; tabela cabe em `max-w-3xl` |
| 11 | Nome cortado sem `<title>`; ticks com valores quebrados (Blind) | low | rejeitado: cosmético, a tabela é a alternativa acessível; mesmo esquema da 6.8 |
| 12 | `percent` redundante nos totais; casts em `load` (Blind) | low | patch só da redundância; casts são necessários (spread no `Promise.all` perde a tupla) |
| 13 | "Mês atual" não muda se a página atravessar a virada do mês (Edge) | low | rejeitado: mesmo caso já rejeitado na 6.8; recarregar resolve |
| 14 | Personalizado soma gasto de meses sem orçamento contra a alocação (Edge) | low | rejeitado: é a decisão congelada ("soma alocado e gasto por categoria") |
| 15 | `loading` fica `true` se o intervalo ficar inválido durante uma carga (Edge) | false | com `months === null` o template mostra o erro antes do skeleton; voltar a um intervalo válido dispara `load` de novo |
| 16 | Mês futuro no personalizado quebra o `Promise.all` (Edge) | false | `GET /budgets/{mês}` aceita mês futuro (mês aberto) |
| 17 | `shortName` parte emoji no corte (Edge) | low | patch: `Array.from` |
| 18 | Estourada de um grupo com saldo positivo não fica no topo da tela (Edge, claim) | low | rejeitado: a regra congelada ordena grupos pelo total; a linha estourada fica no topo do seu grupo, com ícone vermelho |
| 19 | Quarto total some quando alocado = 0 (Edge, claim) | false | a spec manda ocultar o % quando o alocado é 0 |
| 20 | Rota nova sem teste de `requiresAuth` (VG) | low | patch: caso no `router/__tests__/index.spec.ts` |
| 21 | Ordenação por Status e decrescente sem teste (VG) | low | patch: teste curto |

## Verification

**Commands:**
- `cd porquinho-frontend && npx vitest run src/views/__tests__/BudgetComparisonView.spec.ts src/components/budget src/lib src/views/__tests__/BudgetView.spec.ts` -- passa
- `cd porquinho-frontend && npm run lint && npx vue-tsc --build` -- sem erros
