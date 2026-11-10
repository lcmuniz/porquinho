---
title: 'Story 6.5: realocar dinheiro entre categorias'
type: 'feature'
created: '2026-11-10'
status: 'done'
baseline_commit: 'a41d07817ef8718e037672f5e96918ce1d33deef'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-6-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Para mudar o orçamento no meio do mês, o usuário precisa editar dois valores à mão na página `/orcamento`. Não fica registro de que o dinheiro saiu de uma categoria e foi para outra, e o histórico da 6.8 vai precisar desse registro.

**Approach:** Um botão "Mover dinheiro" na página `/orcamento` abre um diálogo com origem, destino e valor, e mostra o restante atual de cada categoria. O backend subtrai o valor da alocação da origem, soma na do destino e grava a realocação em `budget_reallocations`, tudo na mesma transação.

## Boundaries & Constraints

**Always:**
- Origem e destino são categorias filhas ativas do usuário e diferentes entre si. O valor é maior que 0, com até 2 casas decimais.
- A origem precisa ter alocação no mês. O valor não pode passar do restante da origem (alocado − gasto), e não se move dinheiro de uma origem com restante negativo. O gasto da validação vem do banco, não do cache.
- Destino sem alocação no mês: a realocação cria a alocação com o valor movido.
- Tabela nova `budget_reallocations` (próxima migration livre: V33), sem soft delete, porque é registro de auditoria. Entra no export e no purge LGPD nesta story.
- Só o mês corrente e os futuros (fuso de São Paulo, como o `build`). Mês fechado: 400 "Não dá para mover dinheiro em um mês que já fechou", e o botão não aparece na tela.
- Mensagem de sucesso: "R$ X,XX movido de [A] para [B]".
- Erros 400 em RFC 7807, com `detail` em português. Categoria de outro usuário ou removida também volta 400, igual ao `save`.
- Cache: nada a invalidar. O Redis guarda só gasto e comprometido (6.4), e as alocações são lidas do banco a cada GET.

**Never:** histórico ou listagem de realocações (fica para a 6.8), desfazer realocação, realocar a partir de grupo ou para grupo, cache do Dashboard.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Feliz | A: alocado 300, gasto 100; B: alocado 50; mover 150 | A=150, B=200, 1 linha em `budget_reallocations` | N/A |
| Restante inteiro | A: restante 200; mover 200 | A fica com restante 0 | N/A |
| Acima do restante | A: restante 200; mover 200,01 | nada gravado | 400 "O valor passa do restante de [A] (R$ 200,00)" |
| Origem estourada | A: restante −30 | nada gravado | 400 "[A] está estourada: não dá para mover dinheiro dela" |
| Origem sem alocação | A sem linha no mês | nada gravado | 400 "[A] não tem orçamento neste mês" |
| Destino sem alocação | B sem linha | B criada com o valor movido | N/A |
| Mesma categoria | origem = destino | nada gravado | 400 "Escolha categorias diferentes" |
| Grupo/removida/de outro usuário | id inválido | nada gravado | 400 (mensagem do `save`) |
| Mês fechado | mês anterior ao corrente | nada gravado | 400 "Não dá para mover dinheiro em um mês que já fechou" |
| Valor ≤ 0 ou 3 casas | 0 / −1 / 1,001 | nada gravado | 400 de Bean Validation |

</frozen-after-approval>

## Code Map

- `porquinho-backend/src/main/resources/db/migration/V33__create_budget_reallocations.sql` -- novo, no padrão da V32 (UUID `DEFAULT gen_random_uuid() PRIMARY KEY`, `TIMESTAMP WITH TIME ZONE` por causa do H2, FKs sem ON DELETE). Colunas: `user_id`, `from_category_id`, `to_category_id`, `month_year` (dia 1), `amount DECIMAL(15,2)`, `created_at`. CHECK `amount > 0` e `from <> to`. Índices `(user_id, month_year)`, `from_category_id`, `to_category_id`. O RLS entra sozinho pelo `afterMigrate.sql`.
- `entity/BudgetReallocation.java`, `repository/BudgetReallocationRepository.java` -- copiar a forma de `BudgetAllocation`/`BudgetAllocationRepository` (`findByUserIdOrdered`, `deleteByUserId` nativo).
- `dto/ReallocateBudgetRequest.java` -- `fromCategoryId`, `toCategoryId` (`@NotNull`), `amount` (`@NotNull`, `@DecimalMin(value="0.01")`, `@Digits(13,2)`), com mensagens no tom de `SaveBudgetRequest`.
- `service/BudgetService.java` -- novo `reallocate(userId, month, request)` `@Transactional`. Trava com `findActiveByUserIdForUpdate`, como o `save`, para serializar com ele. Valida contra `children(...)`. O gasto da origem vem de `transactionRepository.sumSpentByCategory` direto. Atualiza ou cria as duas `BudgetAllocation` e grava a realocação. Devolve `build(...)`. O nome da categoria para as mensagens vem da própria lista travada.
- `controller/BudgetController.java` -- `POST /api/v1/budgets/{month}/reallocations` → 200 `BudgetResponse`.
- `service/DataExportService.java:~504` e a lista de tabelas na `:230` -- bloco `budget_reallocations`, igual ao de alocações. `service/AccountDeletionService.java:381` -- `deleteByUserId` antes de `categoryRepository` (junto da linha de `budgetAllocationRepository`), e atualizar o comentário da `:63`.
- `DataExportServiceTest`, `AccountDeletionServiceTest` -- mocks e asserts da tabela nova.
- `BudgetIntegrationTest` -- os cenários da matriz. Rodar com `REDIS_PORT=6380`.
- Front: `types/budget.ts` (`BudgetReallocationInput`), `services/budgetService.ts` (`reallocateBudget(month, input)`). Novo `components/budget/ReallocateForm.vue`, no padrão do `components/accounts/TransferForm.vue` (select nativo com `SELECT_CLASS`, `Input` de valor, `parseBRL`, erros por campo, 400 do backend). Cada opção mostra o nome e o restante ("Mercado — restante R$ 200,00"). `BudgetView.vue` abre o `Dialog` como em `AccountsView.vue:363`, faz `fill(data)` no sucesso e chama `setSuccess`.
- `BudgetView.vue` -- o botão fica desabilitado quando há valores digitados e não salvos (comparar `values` com o `formatBRLInput` do salvo), com o texto "Salve o orçamento antes de mover dinheiro". Assim o `fill` do sucesso não apaga o que foi digitado. Desabilitado também quando nenhuma categoria tem alocação. Oculto em mês fechado (`month < currentMonth()`, comparando as strings `AAAA-MM`).

## Tasks & Acceptance

**Execution:**
- [x] `V33__create_budget_reallocations.sql` + entity + repository -- tabela de auditoria.
- [x] `ReallocateBudgetRequest` + `BudgetService.reallocate` + rota no `BudgetController` -- regras da matriz, atômico.
- [x] `DataExportService`, `AccountDeletionService` e os testes deles -- LGPD na mesma story.
- [x] `BudgetIntegrationTest` -- todas as linhas da matriz, incluindo "nada gravado" nos erros.
- [x] Front: tipo, serviço, `ReallocateForm.vue` + spec, `BudgetView.vue` + spec (abrir o diálogo, sucesso com mensagem e valores atualizados, botão bloqueado com edição pendente).

**Acceptance Criteria:**
- Given tenho alocações no mês, when clico em "Mover dinheiro", movo R$ 50,00 de Lazer para Mercado e confirmo, then o diálogo fecha, vejo "R$ 50,00 movido de Lazer para Mercado", e os valores alocados e o restante das duas categorias já aparecem atualizados.
- Given o backend recusa com 400, when confirmo, then o diálogo continua aberto com a mensagem do `detail` e o que digitei.
- Given a realocação foi gravada, when exporto meus dados, then o JSON tem `budget_reallocations` com a linha.

## Implementation Notes

- A edição pendente compara o valor numérico (`parseBRL`), não o texto: "400" num campo salvo como "400,00" não bloqueia o botão.
- A linha "Valor ≤ 0 ou 3 casas" da matriz é coberta no `BudgetControllerTest` (Bean Validation via HTTP). O resto da matriz está no `BudgetIntegrationTest`.

## Spec Change Log

## Review Triage Log

| # | Layer | Finding | Verdict | Evidence | Route |
|---|-------|---------|---------|----------|-------|
| 1 | blind | Origem lista categorias estouradas ou zeradas, que o backend sempre recusa (e pode vir pré-selecionada) | medium | `ReallocateForm.vue` `sources` filtra só `remaining !== null` | patch |
| 2 | blind | Botão habilitado sem nada a mover (1 filha só, ou nenhuma origem com restante > 0) | low | `BudgetView.vue` `hasAllocation`; correção direta do computed | patch |
| 3 | vgap | `BudgetReallocationRepository.deleteByUserId`/`findByUserIdOrdered` só testados com mock | gap | A irmã `budget_allocations` tem teste real em `BudgetIntegrationTest:245` | patch |
| 4 | vgap | Mapeamento `reallocateOptions` da view não é testado | gap | Nenhum teste da view lê os `<option>` | patch |
| 5 | blind | Sem validação local do teto do restante | low | Backend é a autoridade; adiciona ramo | rejeitado (low) |
| 6 | blind | Teto ignora o comprometido | false | Bloco frozen define restante = alocado − gasto | rejeitado |
| 7 | blind, edge | Soma no destino passa de DECIMAL(15,2) → 500 | low | Exige alocações perto de 10^13; correção é guarda nova | rejeitado (low) |
| 8 | blind, edge | Despesa concorrente não é serializada pela trava das categorias | low | Mesma corrida aceita na 6.4 (#2); fluxo comum é sequencial | rejeitado (low) |
| 9 | edge | Resposta da realocação usa o gasto do cache (até 30 s) | low | TTL de 30 s aceito na 6.4; o servidor valida pelo banco | rejeitado (low) |
| 10 | edge | Erro de campo do servidor fica após editar o campo | low | Mesmo comportamento do `TransferForm`; some no próximo envio | rejeitado (low) |
| 11 | blind | Testes extras de backend (mês inválido, detail 400, UUID ruim, mês futuro isolado) | low | Caminhos genéricos já cobertos por outros controllers e pelo `GlobalExceptionHandler` | rejeitado (low) |
| 12 | blind | Testes extras de front (Esc, Cancelar, foco, falha de rede) | low | Mesmo padrão do diálogo de contas, já testado lá | rejeitado (low) |
| 13 | blind | Índices em `from/to_category_id` sem uso | low | Convenção de índice em FK, como a V32 | rejeitado (low) |
| 14 | blind | Entidade de auditoria com setters | low | Mesmo padrão de `BudgetAllocation`; colunas `updatable=false` | rejeitado (low) |
| 15 | blind | `closedMonth` não reage à virada do mês | low | Backend recusa com 400 e mensagem clara | rejeitado (low) |
| 16 | blind | Trava duplicada `inFlight` + `submitting` | low | Cópia do padrão do `TransferForm` | rejeitado (low) |

## Verification

**Commands:**
- `cd porquinho-backend && REDIS_PORT=6380 ./mvnw test -Dtest='Budget*,DataExport*,AccountDeletion*'` -- expected: verde
- `cd porquinho-frontend && npm run lint && npx vue-tsc --build && npx vitest run` -- expected: sem erros
