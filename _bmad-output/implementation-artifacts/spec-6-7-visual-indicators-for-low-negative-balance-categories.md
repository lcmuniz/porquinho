---
title: 'Story 6.7: indicadores visuais de categorias com saldo baixo ou estourado'
type: 'feature'
created: '2026-11-11'
status: 'done'
baseline_commit: '47a4deadd6c2051cca8799b68dffe66493fd32b4'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-6-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** A 6.4 já devolve `remaining` e `status` por categoria e a página `/orcamento` mostra cor, ícone e rótulo. Ainda faltam as partes que a 6.4 deixou para a 6.7: as mensagens "Saldo baixo - R$ X,XX restantes" e "Categoria estourada! R$ X,XX acima do orçamento", as estouradas no topo com fundo vermelho, o resumo "X categorias saudáveis, Y baixas, Z estouradas", o percentual da barra e os valores exatos ao passar o mouse.

**Approach:** Só frontend, em `BudgetView.vue`. O backend já calcula o status com a regra única do épico e não muda.

## Boundaries & Constraints

**Always:**
- O status vem do backend (`status`, `remaining`), reflete o valor salvo e não é recalculado no front.
- O texto do chip de status: HEALTHY "Saudável" (sem aviso); LOW "Saldo baixo - R$ X,XX restantes"; OVERSPENT "Categoria estourada! R$ X,XX acima do orçamento", com o valor absoluto de `remaining`. O ícone e as cores da 6.4 continuam.
- Dentro de cada grupo, as filhas OVERSPENT vêm primeiro e as demais seguem por nome. Grupos com alguma estourada vêm antes dos outros. O `li` da estourada ganha fundo vermelho claro (`bg-red-50`) e o texto mantém o contraste AA.
- Ao lado da barra aparece o percentual real `round(gasto ÷ alocado × 100)%`, que pode passar de 100 (a barra continua limitada a 100%). Com alocado 0, o percentual não aparece.
- Os valores exatos ("R$ gasto de R$ alocado") ficam no `title` da barra e do chip. Eles já aparecem em texto, então nada fica disponível só no hover. Não entra componente de tooltip novo.
- A ordem e o resumo usam o status salvo. Digitar um valor sem salvar não reordena a lista.
- Decisão (Luiz, 2026-11-11): o resumo aparece só em `/orcamento`, no card de totais. O Dashboard fica com o Epic 7 (7.2).
- Decisão (Luiz, 2026-11-11): o alerta de 90% fica adiado e registrado em `deferred-work.md`.

**Never:** mudanças no backend ou na regra de status. Saldos na tela `/categorias`. Totais por grupo (6.9). Cache do Dashboard (Epic 7). Resumo no `DashboardView`. Alerta ou notificação de 90%.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Saudável | alocado 100, gasto 50 | "Saudável", "50%" | N/A |
| Baixa | alocado 100, gasto 85 | "Saldo baixo - R$ 15,00 restantes", "85%" | N/A |
| Estourada | alocado 100, gasto 130 | "Categoria estourada! R$ 30,00 acima do orçamento", "130%", barra cheia, fundo vermelho, primeira do grupo | N/A |
| Alocado 0 com gasto | alocado 0, gasto 10 | estourada "R$ 10,00 acima", sem percentual | N/A |
| Sem orçamento | allocated null | "Sem orçamento definido", fora do resumo | N/A |
| Resumo | 2 HEALTHY, 1 LOW, 1 OVERSPENT, 1 sem orçamento | "2 categorias saudáveis, 1 baixa, 1 estourada" | N/A |
| Nada orçado | todas sem orçamento | resumo não aparece | N/A |

</frozen-after-approval>

## Code Map

- `porquinho-frontend/src/views/BudgetView.vue` -- `STATUS`/`statusOf` (texto do chip passa a depender de `remaining`), `groups` computed (ordenação por status), `progress()` (a barra continua limitada; criar `percent()` sem limite), card `budget-totals` (resumo). Reaproveitar `formatBRL` de `@/lib/money`.
- `porquinho-frontend/src/types/budget.ts` -- sem mudança.
- `porquinho-frontend/src/views/__tests__/BudgetView.spec.ts` -- os testes da 6.4 (linha 108, "categoria estourada…") checam o rótulo "Estourada". Atualizar para o texto novo.
- `porquinho-backend/.../service/BudgetService.java#status` -- regra já pronta. Não mexer.

## Tasks & Acceptance

**Execution:**
- [x] `porquinho-frontend/src/views/BudgetView.vue` -- mensagens por status, ordenação de estouradas, fundo vermelho, percentual, `title` com valores exatos e resumo -- ACs da 6.7.
- [x] `porquinho-frontend/src/views/__tests__/BudgetView.spec.ts` -- cobrir cada linha da matriz e ajustar os testes da 6.4 que mudam de texto.

**Acceptance Criteria:**
- Given um grupo com Alimentação (HEALTHY) e Mercado (OVERSPENT), when abro `/orcamento`, then Mercado aparece antes de Alimentação, com fundo vermelho e a mensagem de estouro.
- Given o status é mostrado, then sempre há ícone e texto junto da cor.

## Implementation Notes

## Spec Change Log

## Review Triage Log

| # | Finding | Verdict | Evidence / Route |
|---|---------|---------|------------------|
| 1 | `aria-valuenow` travado em 100 enquanto o texto mostra 130% | low | patch: `aria-valuetext` com o percentual real |
| 2 | A lista reordena depois de salvar ou realocar e o foco fica num input que mudou de lugar | false | É o status salvo, como pede a spec. No save o foco fica no botão Salvar, fora da lista. Na realocação volta para "Mover dinheiro" |
| 3 | Alocado 0 e gasto 0 aparece como "Saldo baixo - R$ 0,00 restantes" | false | Regra literal decidida na 6.4 ("Alocado 0 e gasto 0 dá LOW") |
| 4 | Sem teste de `title`, fundo e barra com alocado 0 | low | rejeitado: `exact()` usa `allocated ?? 0` sem dividir, e o fundo já é coberto pelo status |
| 5 | Pluralização do resumo com contagem 0 e singular sem teste (Blind + Verification Gap) | low | patch: teste com "1 categoria saudável, 0 baixas, 0 estouradas" |
| 6 | Sem teste de desempate entre dois grupos com estourada | low | rejeitado: o sort é estável sobre a ordem por nome, e o teste atual já falharia sem a ordenação |
| 7 | Entrada do alerta de 90% fora do diff | false | Registrada em `deferred-work.md` no planejamento; o diff da revisão cobre só o frontend |
| 8 | Contraste e margem do `bg-red-50` | false | `text-gray-700` sobre `#FEF2F2` ≈ 9:1; o ícone `red-600` ≈ 4,4:1 (> 3:1); só OVERSPENT ganha o fundo |
| 9 | Gasto entre 100,00 e 100,49 de 100 mostra "100%" numa linha estourada | low | rejeitado: caso raro, a mensagem diz "estourada" e o fix exigiria um ramo novo |
| 10 | Gasto negativo gera percentual negativo | false | `sumSpentByCategory` soma só débitos, então o gasto nunca é negativo |

## Verification

**Commands:**
- `cd porquinho-frontend && npx vitest run src/views/__tests__/BudgetView.spec.ts` -- passa
- `cd porquinho-frontend && npm run lint && npx vue-tsc --build` -- sem erros
