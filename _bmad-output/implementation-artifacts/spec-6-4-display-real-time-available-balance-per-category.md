---
title: 'Story 6.4: mostrar em tempo real o saldo disponível por categoria'
type: 'feature'
created: '2026-11-09'
status: 'done'
baseline_commit: '0fc8fec4767e73b47ab0aa2926e67ef6a679e8b9'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-6-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** A página `/orcamento` (6.3) mostra alocado, gasto e restante, mas não diz se a categoria está saudável, baixa ou estourada. O gasto também é recalculado no banco a cada leitura, sem o cache de 30 s previsto no épico.

**Approach:** O backend devolve `remaining` e `status` por categoria e guarda no Redis, por 30 s, o gasto e o comprometido de cada usuário e mês. O cache é invalidado depois do commit de cada escrita que altera esses valores. A tela pinta a barra e mostra ícone e rótulo do status.

## Boundaries & Constraints

**Always:**
- Regra de status, igual em todo o épico: `OVERSPENT` se restante < 0; `LOW` se 0 ≤ restante ≤ 20% do alocado; `HEALTHY` se restante > 20%. Sem alocação: `status` e `remaining` vêm `null` e a tela continua com "Sem orçamento definido". Alocado 0 e gasto 0 dá `LOW` (aplicação literal da regra).
- Cache só do gasto e do comprometido. Alocações e total disponível continuam lidos do banco a cada GET.
- Invalidação depois do commit (`TransactionSynchronization.afterCommit`; sem transação ativa, na hora). Ela apaga todos os meses do usuário.
- Redis fora do ar não quebra a página: a leitura cai no banco, a invalidação só registra log, e o TTL de 30 s limita o tempo de dado velho.
- Status nunca só por cor: ícone Lucide e rótulo em texto ("Saudável", "Saldo baixo", "Estourada"). Cores Green 600 `#16A34A`, Yellow 600 `#CA8A04`, Red 600 `#DC2626`.
- Saldos aparecem só na página `/orcamento`. O épico diz "lista de categorias ou orçamento", e `/categorias` continua sendo a tela de gestão.

**Never:** mensagens "Saldo baixo - R$ X,XX restantes" e "Categoria estourada!", estouradas no topo, resumo "X saudáveis…", tooltip e notificação de 90% (ficam para a 6.7). Também ficam fora os totais do grupo (6.9) e o cache do Dashboard (Epic 7). Reconciliação, remoção de conta e mudanças de categoria contam só com o TTL.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Saudável | alocado 100, gasto 50 | `remaining=50`, `HEALTHY` | N/A |
| Limite amarelo | alocado 100, gasto 80 / gasto 100 | `LOW` nos dois | N/A |
| Estourada | alocado 100, gasto 130 | `remaining=-30`, `OVERSPENT`, barra cheia vermelha | N/A |
| Sem orçamento | sem alocação | `remaining=null`, `status=null` | N/A |
| Cache quente | 2 GETs seguidos | só o primeiro consulta as somas de transação | N/A |
| Invalidação | GET, criar/editar/remover transação, GET | o 2º GET reflete a mudança | N/A |
| Redis fora | conexão recusada | 200 com valores do banco | log warn |

</frozen-after-approval>

## Code Map

- `service/BudgetService.java#build` -- trocar as chamadas diretas de `sumSpentByCategory`/`sumCommittedByCategory` pelo cache. Calcular `remaining`/`status` aqui. `committed` continua `null` em mês fechado.
- `service/BudgetBalanceCache.java` -- novo `@Component` sobre o `RedisTemplate<String,Object>` de `config/RedisConfig.java`. Usa um hash por usuário, `category:balance:{userId}`, com campo `{YYYY-MM}` → `{spent:{catId:valor}, committed:{...}|null}`. O TTL de 30 s é aplicado só quando o hash é criado (`getExpire < 0`), para nenhum mês passar de 30 s. `evict(userId)` faz `DEL` da chave. Desvio consciente da chave por categoria do épico: um `DEL` invalida todos os meses sem `SCAN`. Valores voltam como `BigDecimal` (cuidado com o Jackson genérico, que devolve `Double`/`Integer`: guardar como string).
- `dto/BudgetResponse.java` -- `CategoryBudget` ganha `remaining` e `status` (enum `BudgetStatus { HEALTHY, LOW, OVERSPENT }` em `dto/` ou `entity/`, como os outros enums).
- Pontos de `evict(userId)`: `TransactionService` `create`/`update`/`delete`/`removeFutureInstallments`; `TransactionImportReviewService` `complete`/`discard` (só esses mexem em `CONFIRMED` ou em futuras confirmadas); `CreditCardService` `delete`/`restore` (comprometido ignora cartão removido).
- `src/test/resources/application-test.yml` -- porta `${REDIS_PORT:6379}`. Na máquina do Luiz, a 6379 é de outro projeto: rodar os testes com `REDIS_PORT=6380`. O CI segue com 6379.
- `service/BudgetIntegrationTest.java` -- usa Redis real e insere transações via `jdbc`, sem passar pela invalidação. Chamar `evict` (ou apagar a chave) depois de inserções diretas e no `@AfterEach`.
- `porquinho-frontend/src/types/budget.ts` -- `remaining: number | null`, `status: 'HEALTHY' | 'LOW' | 'OVERSPENT' | null`.
- `porquinho-frontend/src/views/BudgetView.vue:148-164` -- barra (`bg-gray-600`) passa a usar a cor do status. Ícone (`CircleCheck`/`TriangleAlert`/`CircleAlert` de `lucide-vue-next`, `aria-hidden`) + rótulo ao lado do restante. O restante vem de `remaining` do backend, não de `allocated - spent`. Mas os valores digitados e ainda não salvos não mudam o status (ele reflete o salvo).

## Tasks & Acceptance

**Execution:**
- [x] `BudgetStatus` + `remaining`/`status` em `BudgetResponse` e `BudgetService` -- regra única do épico.
- [x] `BudgetBalanceCache` + uso em `BudgetService` -- TTL 30 s, fallback quando o Redis falha.
- [x] `evict` nos 8 pontos de escrita listados -- após o commit.
- [x] `BudgetBalanceCacheTest` (unit, Redis mockado) -- hit, miss, TTL só na criação, falha de Redis cai no loader, evict adiado até o commit.
- [x] `BudgetIntegrationTest` -- status nas 4 faixas da matriz, cache quente, invalidação por create/update/delete de transação via `TransactionService`, mais o ajuste de `application-test.yml`.
- [x] Front: tipos, `BudgetView.vue` e `views/__tests__/BudgetView.spec.ts` -- cor, ícone e rótulo por status. Sem status, mantém "Sem orçamento definido".

**Acceptance Criteria:**
- Given uma categoria com alocado 100 e gasto 130, when abro `/orcamento`, then vejo "Restante: -R$ 30,00", a barra cheia em vermelho e o rótulo "Estourada" com ícone.
- Given abri `/orcamento` e depois lancei uma despesa manual na categoria, when volto à página, then o gasto e o status já incluem a despesa.
- Given o Redis está fora do ar, when abro `/orcamento`, then a página carrega com os valores do banco.

## Implementation Notes

- A invalidação reaproveita `SubscriptionService.afterCommit`. Na revisão, entrou um 9º ponto de `evict`: `TransactionImportProcessor`, quando o processamento confirma parcelas futuras (`InstallmentPlanner.confirm` altera futuras já `CONFIRMED`). A lista do Code Map estava incompleta.
- O rótulo de status fica em texto escuro e só o ícone é colorido. O ícone amarelo usa Yellow 700 por causa do contraste (WCAG 1.4.11).

## Spec Change Log

## Review Triage Log

| # | Layer | Finding | Verdict | Evidence | Route |
|---|-------|---------|---------|----------|-------|
| 1 | edge, vgap | O processamento de importação confirma futuras `CONFIRMED` sem `evict` | medium | `TransactionImportProcessor.java:201-203` grava `plan.confirmed()`, e `InstallmentPlanner.confirm` muda `futureInstallment`/valor/data | patch |
| 2 | blind, edge | Corrida: leitura grava o cache depois do `evict` de uma escrita concorrente | low | Real, limitada a 30 s; pede contador de geração (guarda nova); o fluxo comum é sequencial | rejeitado (low) |
| 3 | blind, edge | `put` ok e `getExpire`/`expire` falham: hash sem TTL | low | Pede falha parcial do Redis entre dois comandos; correção pede MULTI ou guarda nova | rejeitado (low) |
| 4 | blind | Redis fora: cada leitura e `evict` esperam 2 s de timeout | low | `application.yml:38` `timeout: 2000ms`; só com Redis fora; spec aceita fallback | rejeitado (low) |
| 5 | blind | Cache depende de `SubscriptionService.afterCommit` | low | Reuso de helper existente; nenhum chamador diverge | rejeitado (low) |
| 6 | vgap, blind | `complete`/`discard` sem teste de invalidação | gap | Nenhum teste lê o orçamento após concluir importação | patch |
| 7 | vgap, blind | `CreditCardServiceTest` não verifica `evict` em delete/restore | gap | Mock inline sem `verify` | patch |
| 8 | vgap | `removeFutureInstallments` sem teste de invalidação | gap | Mesmo caminho dos 3 já testados; TTL limita | defer |
| 9 | blind | Sem teste de cache em mês fechado e de virada de mês | low | `BudgetService` ignora `committed` cacheado quando o mês fecha | rejeitado (low) |
| 10 | blind | `status` sem teste unitário de bordas | low | Faixas cobertas no teste de integração | rejeitado (low) |
| 11 | blind | `AccountDeletionService` não chama `evict` | low | Somas do usuário excluído ficam até 30 s no Redis, sem leitor | rejeitado (low) |
| 12 | blind | Ícone amarelo `#CA8A04` com contraste 2,9:1 | low | Abaixo de 3:1 da WCAG 1.4.11; correção direta da classe | patch |
| 13 | blind | Hex fixo em vez de `green-600`/`yellow-600`/`red-600` | low | Mesmos valores do Tailwind; correção direta | patch |
| 14 | edge | Alocado 0 e gasto 0 dá `LOW` | false | Decisão explícita no bloco frozen | rejeitado |

## Verification

**Commands:**
- `cd porquinho-backend && REDIS_PORT=6380 ./mvnw test -Dtest='Budget*,Transaction*,CreditCard*'` -- expected: verde
- `cd porquinho-frontend && npm run lint && npx vue-tsc --build && npx vitest run` -- expected: sem erros
