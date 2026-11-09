---
title: 'Retro Epic 5, item 25: núcleo único de transação'
type: 'refactor'
created: '2026-11-09'
status: 'done'
baseline_commit: '3788e71957dddc677b6a76fe7e792e296e39505a'
route: 'dispatch'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Três regras de escrita de transação estão espalhadas: o alvo da trava (`cardId ?: accountId` + `lockTarget(lockKey)`) em 5 lugares, a regra D3 (entra no saldo se tem conta e data depois da referência da conta, fora parcelas futuras) em JPQL e em Java, e a validação de categoria filha em dois serviços. Além disso, `TransactionService` depende de estáticos da revisão e do processador e trava pelo `TransactionImportRepository` (achados D1, D2, D3, A1). O Epic 6 vai adicionar novos pontos de escrita.

**Approach:** Criar `TransactionCore`, um `@Component` com a derivação e a trava do alvo, a regra D3 em Java, a validação de categoria filha e os helpers de resposta compartilhados. Revisão, edição/criação/remoção e processador passam a usá-lo. Comportamento observável idêntico.

## Boundaries & Constraints

**Always:** refactor sem mudança de comportamento: mesmas respostas, mensagens, códigos HTTP, eventos de auditoria, efeito no saldo e snapshots. A ordem das travas continua a de hoje: advisory locks em ordem de chave antes da linha/import/conta. Todos os testes existentes passam, mudando só referências a símbolos movidos.

**Never:** sem migration, sem mudar API ou frontend, sem mexer em duplicatas, planner ou conciliação além de trocar a chamada de trava. Não mudar o construtor de `TransactionImportProcessor` (ele usa só os estáticos do núcleo).

</frozen-after-approval>

## Code Map

- `porquinho-backend/src/main/java/com/porquinho/service/TransactionService.java` -- `create` (alvo e trava em ~131-132, D3 em ~143), `update` (D3 em ~219-221 junto com `absorbed`), `lockRow` (~300-314, trava ordenada por TreeSet), `requireChildCategory` (~317), `undoOldEffect` (D3), `signed`, `toResponses` (usa `categoriesOf`, `category`, `pixDetails`, `USER_CONFIDENCE` da revisão). `absorbed` (X2) fica aqui.
- `porquinho-backend/src/main/java/com/porquinho/service/TransactionImportReviewService.java` -- `USER_CONFIDENCE`, `GROUP_NOT_ALLOWED_MESSAGE`, validação inline em `updateCategory` (~141-145), alvo e trava em `complete` (~185) e `discard` (~238-240), `applyBalanceEffect` (D3 via JPQL, ~212-226), estáticos `categoriesOf`, `category`, `pixDetails`.
- `porquinho-backend/src/main/java/com/porquinho/service/TransactionImportProcessor.java` -- alvo em ~172, `lockKey` em ~297.
- `porquinho-backend/src/main/java/com/porquinho/repository/TransactionRepository.java` -- `sumBalanceEffect` e `markAffectsBalance` (~161-178): saem, usados só em `applyBalanceEffect`.
- `porquinho-backend/src/main/java/com/porquinho/repository/TransactionImportRepository.java` -- `lockTarget` (MANDATORY, advisory xact lock): reutilizar, não mudar.
- Testes que citam símbolos movidos: `TransactionImportProcessorTest` (347, 356, 392, 518), `TransactionImportIntegrationTest` (1565, 1613), `controller/TransactionImportControllerTest` (337). `TransactionServiceTest` e `TransactionImportReviewServiceTest` são `@SpringBootTest` (sem mudança de construtor nos testes).

## Tasks & Acceptance

**Execution:**
- [x] `porquinho-backend/src/main/java/com/porquinho/service/TransactionCore.java` -- criar `@Component` com: `USER_CONFIDENCE`, `GROUP_NOT_ALLOWED_MESSAGE`; estáticos `target(cardId, accountId)`, `lockKey(UUID)`, `signed(type, amount)`, `category(...)`, `pixDetails(row)`; instância `lockTargets(UUID...)` (ignora nulos, chaves distintas em ordem crescente), `requireChildCategory(userId, categoryId)` retornando a `Category`, `inBalance(Account, LocalDate)` (D3: conta não nula e data depois de `referenceDate`), `categoriesOf(userId, rows)` -- um lugar só para as regras.
- [x] `porquinho-backend/src/main/java/com/porquinho/repository/TransactionRepository.java` -- trocar `sumBalanceEffect`/`markAffectsBalance` por `findBalanceCandidates(userId, importId)` (linhas da import, não futuras, ativas) -- D3 sai do JPQL.
- [x] `porquinho-backend/src/main/java/com/porquinho/service/TransactionImportReviewService.java` -- usar o núcleo para trava, categoria e `applyBalanceEffect` (filtra os candidatos por `inBalance`, soma `signed`, marca `affectsBalance=true` e `updatedAt=now`, retorna a contagem); remover os estáticos movidos e a dependência direta de `CategoryRepository` se sobrar sem uso.
- [x] `porquinho-backend/src/main/java/com/porquinho/service/TransactionService.java` -- injetar `TransactionCore` no lugar de `TransactionImportRepository`; `create` e `lockRow` usam `target` e `lockTargets`; D3 de `create`, `update` e `undoOldEffect` usa `inBalance`; remover `requireChildCategory` e `signed` locais.
- [x] `porquinho-backend/src/main/java/com/porquinho/service/TransactionImportProcessor.java` -- usar `TransactionCore.target` e `TransactionCore.lockKey`; remover `lockKey` local.
- [x] testes listados no Code Map -- atualizar referências; criar `TransactionCoreTest` (unitário, mocks) para `lockTargets` (ordem por chave, nulos e repetidos ignorados) e `inBalance` (conta nula, data igual à referência, data depois).

**Acceptance Criteria:**
- Given o código depois da mudança, when se busca `lockKey(`, `GROUP_NOT_ALLOWED_MESSAGE`, `USER_CONFIDENCE` e `transactionDate > :reference`, then a definição de cada um existe só em `TransactionCore` e o JPQL de D3 não existe mais.
- Given `TransactionService`, when compilado, then não referencia `TransactionImportReviewService`, `TransactionImportProcessor` nem `TransactionImportRepository`.
- Given a suíte do backend, when executada, then passa sem mudar asserções de comportamento.

## Implementation Notes

- A conclusão da importação filtra os candidatos com `TransactionCore.inBalance(account)` (um `Predicate` que lê a referência uma vez) e marca com `TransactionRepository.markInBalance` (UPDATE em massa por ids), e não alterando as entidades.
- `TransactionService` perdeu também `CategoryRepository` do construtor, que ficou sem uso.
- Dois testes de trava em `TransactionServiceTest` (create no cartão e update de conta para cartão), no padrão `holding` do teste de integração.
- Suíte do backend: 1371 testes, 0 falhas.

## Spec Change Log

## Review Triage Log

| # | Origem | Achado | Veredito | Evidência | Rota |
|---|--------|--------|----------|-----------|------|
| 1 | blind | D3 ainda dividida: `futureInstallment=false` fica no JPQL | low | Parcelas futuras só existem em cartão (o planner roda só para cartão), então `inBalance` de conta nunca as vê; corrigir exigiria novo parâmetro | rejeitado |
| 2 | blind | Alvo duplicado no `COALESCE` de `findEditableTarget` | low | Real; correção direta é um comentário apontando o JPQL | patch |
| 3 | blind | `applyBalanceEffect` grava um UPDATE por linha sob a trava | medium | Sem batching JDBC e com `@DynamicUpdate`; antes era um UPDATE em massa | patch |
| 4 | blind | `affectsBalance` só persiste pelo `flushAutomatically` de `confirmByImport` | medium | Mesma causa do #3; resolvido pelo UPDATE em massa por ids | patch (com #3) |
| 5 | blind | `setUpdatedAt(now)` sobrescrito por `@PreUpdate` | low | Mesma causa do #3 | patch (com #3) |
| 6 | blind | `referenceDate` consultado mais vezes em `update` | false | Antes: undo + absorbed + data (até 3); agora: undo + inBalance + absorbed (até 3) | rejeitado |
| 7 | blind | `TransactionCoreTest` não cobre categoria, `categoriesOf`, `signed`, `target` | low | Cobertos pelos `@SpringBootTest` de revisão e transações | rejeitado |
| 8 | blind | Falta teste de efeito que soma zero | low | Com o UPDATE em massa, marcar não depende mais do snapshot | rejeitado |
| 9 | blind | Helpers de resposta misturados no núcleo | low | Escolha da spec (A1); sem dano nomeado | rejeitado |
| 10 | blind | Processador contorna `lockTargets` sem aviso | low | Correção direta: comentário de uma linha | patch |
| 11 | blind | Linhas acima de 120 colunas | low | Correção direta | patch |
| 12 | verification-gap | Nenhum teste prova que create/update do `TransactionService` tomam a trava | medium | Pré-verificado; a diff reescreveu as três chamadas | patch |

## Design Notes

D3 em Java na conclusão carrega as linhas não futuras da import (centenas no máximo) em vez de duas queries agregadas, trocando uma ida ao banco por um laço em memória. Com isso a regra fica em um lugar só. `confirmByImport` tem `flushAutomatically`, então as linhas marcadas são gravadas antes do update em massa. O processador não entra em `lockTargets` para manter o construtor e os mocks dos testes, mas usa os mesmos estáticos.

## Verification

**Commands:**
- `cd porquinho-backend && ./mvnw -q test` -- expected: BUILD SUCCESS, zero falhas
- `grep -rn "lockKey(\|sumBalanceEffect\|markAffectsBalance(" porquinho-backend/src/main` -- expected: só `TransactionCore` define `lockKey`; as queries removidas não aparecem
