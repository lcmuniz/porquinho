---
title: 'Story 5.12 — Editar manualmente qualquer transação'
type: 'feature'
created: '2026-11-09'
status: 'done'
baseline_commit: '0930efb4e3ceec86801e73f1fbd154679c8cd9fb'
route: 'dispatch'
review_loop_iteration: 1
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-5-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Depois de concluir uma importação, o usuário não tem onde ver nem corrigir suas transações confirmadas: não existe página, nem endpoint de listagem, edição ou remoção, e as parcelas futuras de uma compra estornada ou quitada ficam para sempre.

**Approach:** Nova página `/transacoes` (link no dashboard) que lista as transações confirmadas com Pix e parcela, e permite editar (data, descrição, valor, tipo, categoria, conta ou cartão), remover (soft delete) e "Remover parcelas futuras desta compra". Toda mudança de saldo reaplica a D3 pelo `BalanceHistoryRecorder`; a troca de categoria alimenta a 5.11.

## Boundaries & Constraints

**Always:**
- Só linhas do usuário do JWT, `CONFIRMED`, não removidas e não futuras são listadas, editadas ou removidas; qualquer outra → 404 sem escrever.
- Saldo: efeito antigo = valor assinado se `affects_balance` e a data antiga ainda é posterior à referência D3 da conta antiga; efeito novo = valor assinado se o destino novo é conta e a data nova é posterior à referência D3 dela. Aplicar a diferença por conta (travadas em ordem de id, `fits()`, um snapshot `transaction` por conta com efeito ≠ 0) e regravar `affects_balance`. Remoção = efeito novo zero. Cartão nunca mexe em saldo.
- Advisory lock (`lockTarget`) do destino antigo e do novo, em ordem de chave, antes de tocar nas linhas, como `complete`/`discard`.
- Categoria nova (filha ativa, nunca grupo) diferente da atual → `source=USER`, confiança 100 e `ClassificationPatternService.learn` com a descrição anterior à edição (Q5). Categoria vazia é permitida e não ensina.
- Validação: valor > 0 e ≤ `MAX_BALANCE`, descrição obrigatória (≤ 500), exatamente um de `accountId`/`creditCardId` ativo do usuário. Data futura só mostra aviso no formulário; não bloqueia.
- `original_description`, `import_id` e os campos de parcela não mudam na edição.
- Auditoria: `transaction_edited`, `transaction_deleted`, `installment_futures_removed`, com ids e contagens apenas.
- Sucesso exibe "Transação atualizada" (`role="status"`); Pix e parcela sempre com texto, nunca só ícone.

**Decisões (Luiz, 2026-11-09):**
- **Q1 (A):** a lista mostra um mês por vez, padrão o mês atual (São Paulo), com "‹ mês anterior / próximo ›"; `GET /api/v1/transactions?month=YYYY-MM`, por data desc. Parcelas futuras não aparecem (como na revisão).
- **Q2 (A):** remoção durável. Migration `V31` cria `transactions.installment_futures_removed_at`, gravada nas parcelas ativas do grupo ao remover as futuras; o `InstallmentPlanner` não cria futuras para grupo marcado e propaga a marca à parcela nova; a coluna entra no export.
- **Q3 (A):** remover uma parcela confirmada que deixa a compra sem nenhuma outra parcela confirmada ativa também remove as futuras ativas do grupo e marca o grupo (como "Remover parcelas futuras desta compra"); o diálogo de confirmação avisa quantas futuras saem junto.
- **Q4 (A):** numa parcela (`is_installment=true`), cartão/conta e tipo não mudam: o backend responde 400 se mudarem e o formulário mostra os campos desabilitados; valor, data, descrição e categoria mudam só na linha.
- **Q5 (A):** o aprendizado da 5.11 usa o texto do banco, ou seja, a descrição de antes da edição, para casar com o próximo extrato.

**Never:** adicionar transação (5.13), orçamento por categoria (Epic 6), filtros/busca do Dashboard camada 3 (Epic 7), editar parcela futura, mudar a tela de revisão da importação.

## I/O & Edge-Case Matrix

| Cenário | Entrada / Estado | Resultado | Erro |
|---|---|---|---|
| Valor na mesma conta | débito 100 → 150, data > ref | saldo −50, snapshot, `affects_balance=true` | — |
| Troca de conta | débito 100 de A (com efeito) → B, data > ref(B) | A +100, B −100 | conta B inativa → 404 |
| Data antes da referência | débito com efeito, nova data ≤ ref | saldo +100, `affects_balance=false` | — |
| Conciliada depois | `affects_balance=true`, data ≤ ref atual; muda valor | saldo intacto | — |
| Conta → cartão | débito 100 com efeito → cartão | saldo +100, `affects_balance=false` | — |
| Remover | crédito 200 com efeito | saldo −200, `deleted_at` | já removida → 404 |
| Valor inválido | 0 ou negativo | — | 400 RFC 7807 |
| Saldo fora do limite | diferença estoura `MAX_BALANCE` | nada gravado | 400 |
| Linha de importação em revisão | `review_status=PENDING` | — | 404 |
| Remover futuras | parcela 3/10 com 4..10 futuras | 7 removidas, 3/10 intacta, label sem "restam" | sem futuras → 200 com 0 |

</frozen-after-approval>

## Code Map

Prefixos: `B` = `porquinho-backend/src/main/java/com/porquinho`, `R` = `porquinho-backend/src/main/resources/db/migration`, `T` = `porquinho-backend/src/test/java/com/porquinho`, `F` = `porquinho-frontend/src`.

- `B/entity/Transaction.java:89,116,122,125` -- `affectsBalance`, `updatedAt`, `deletedAt`; `importId` não atualizável. CHECK D1 em V26.
- `B/repository/TransactionRepository.java:49,66,199` -- modelos: `findActiveInstallmentsByCreditCard`, `@Modifying` parcial, `softDeleteByImport`. Faltam: listar confirmadas por usuário/mês, contar futuras ativas por grupo, soft delete das futuras do grupo.
- `B/service/TransactionImportReviewService.java:223-252,291-335` -- `applyBalanceEffect`, `referenceDate`/`REFERENCE_EVENTS` (privados: extrair para um ponto compartilhado, ex. `BalanceHistoryRecorder` ou componente novo, sem duplicar) e `toResponse`/`categoriesOf`/`pixDetails` (reaproveitar). `:154-156` bloqueio de categoria-grupo.
- `B/service/ClassificationPatternService.java:80` `learn(userId, ids, categoryId)` -- lê `description` do banco por JPQL (`TransactionRepository.findDescriptions:91`). Q5: chamar `learn` antes de alterar a entidade gerenciada, porque o flush automático do JPQL gravaria a descrição nova antes da leitura.
- `B/service/ReconciliationService.java:64-88`, `TransferService.java:100-127` -- padrão de trava + `fits` + `record` + audit; duas contas em ordem de id (`findActiveByIdsForUpdate`).
- `B/service/AuditLogService.java:47` -- `log(event, userId, ip, metadataJson)`.
- `B/repository/TransactionImportRepository.java:161`, `B/service/TransactionImportProcessor.java:297` -- `lockTarget`, `lockKey`.
- `B/service/InstallmentPlanner.java:64` -- Q2: não criar futuras para grupo marcado e propagar a marca.
- `B/service/DataExportService.java:461` -- export de transações (coluna nova, Q2).
- `F/views/ImportReviewView.vue:166-176,195-273,493-541` -- Pix/parcela, Dialog, select com optgroup, `pixLabel`/`installmentLabel` (mover para `F/lib/` e reusar nas duas telas).
- `F/views/AccountsView.vue:20-31,363-410,628-638` -- mensagem `role="status"`, modal de edição e devolução de foco.
- `F/router/index.ts:91-104`, `F/views/DashboardView.vue:95-115` -- rota autenticada e link.
- `F/lib/money.ts`, `F/lib/date.ts`, `F/services/transactionImportService.ts` (modelo de service).
- **Não mexer:** `TransactionImportProcessor` (salvo o planner, Q2), fluxo de revisão.

## Tasks & Acceptance

**Execution:**
- [x] `B/service/...` -- extrair `referenceDate` para uso comum; `complete` passa a usar a versão extraída.
- [x] `B/repository/TransactionRepository.java` -- queries de lista, contagem de futuras por grupo e soft delete das futuras.
- [x] `B/dto/TransactionResponse.java`, `TransactionUpdateRequest.java` -- resposta com os campos de `ImportTransactionResponse` + `accountId`, `creditCardId`, `installmentGroupId`, `remainingInstallments` (futuras ativas).
- [x] `B/service/TransactionService.java` -- listar, editar, remover, remover futuras (regras de saldo, trava, aprendizado Q5, auditoria). Q3 na remoção, Q4 na edição. Destino inalterado de conta ou cartão já removido é aceito, sem efeito de saldo nessa conta. Timestamps pelo `Clock` injetado. Valor fora do limite com mensagem de limite.
- [x] `B/controller/TransactionController.java` -- `GET /api/v1/transactions`, `PUT /{id}`, `DELETE /{id}`, `POST /{id}/remove-future-installments`.
- [x] `R/V31__add_installment_futures_removed_at.sql`, entidade, `InstallmentPlanner`, `DataExportService` -- remoção durável (Q2).
- [x] `F/types`, `F/services/transactionService.ts`, `F/lib/` (rótulos), `F/views/TransactionsView.vue` (lista, Dialog de edição com aviso de data futura, destino e tipo desabilitados numa parcela (Q4), confirmação de remoção que avisa as futuras que saem junto (Q3); 409 mostra o `detail` e recarrega como o 404; todo botão de linha com `aria-label` que cita a descrição), rota e link no dashboard.
- [x] Testes: `TransactionServiceTest` (Testcontainers: toda a matriz, isolamento, aprendizado com descrição editada (Q5), Q3, Q4, débito → crédito, linha fora do saldo que entra nele (data e cartão → conta), cartão/categoria de outro usuário ou removidos → 404 sem escrita, edição de linha em conta removida), `TransactionControllerTest` (`@WebMvcTest`, validação 400/404), planner (grupo marcado não recria futuras), `TransactionsView.spec.ts` (inclui falhas 400/404/409 e de remoção).

**Acceptance Criteria:**
- Given uma transação editada com nova categoria, when importo outro extrato com a mesma descrição, then a linha chega com a categoria via padrão.
- Given uma transação removida, when abro `/transacoes`, then ela não aparece na lista e o saldo voltou ao valor anterior.

## Implementation Notes

- Referência da 1ª passada (revertida pelo loopback de revisão): `/private/tmp/claude-501/-Users-lcmuniz-Desenvolvimento-porquinho/2793d70b-81e9-4c42-bf05-754703af6696/scratchpad/impl-5-12-pass1.patch`. Testes e verificação passaram nela; pode reaproveitar o que não conflita com Q3–Q5 e com as tarefas acima.

## Spec Change Log

- **Revisão 1 (Luiz, 2026-11-09):** os achados 2, 3 e 15 da triagem (parcela removida deixa futuras órfãs; editar parcela divide o grupo; aprendizado com a descrição editada) foram levados ao humano, que decidiu Q3–Q5 (bloco congelado). As tarefas passaram a incluir os patches 4, 6, 7, 10, 11, 16–19. KEEP: extração de `referenceDate` para `BalanceHistoryRecorder`; ordem de travas (advisory por chave, linha, contas por id); `fits()` em todas as contas antes de gravar; `remainingInstallments` por contagem de futuras ativas; `F/lib/transactionLabels.ts` compartilhado com a revisão; mensagem `role="status"`.

## Verification

**Commands:**
- `cd porquinho-backend && ./mvnw test` -- verde.
- `cd porquinho-frontend && npm run lint && npx vue-tsc --build && npm run test:unit` -- verde.

## Review Triage Log

| # | Camada | Achado | Veredito | Rota | Evidência |
|---|---|---|---|---|---|
| 1 | blind, edge, gap | A marca Q2 some se todas as linhas ativas do grupo forem removidas ou movidas, e a próxima importação recria as futuras | low | rejeitado | O planner só lê linhas ativas (`findActiveInstallmentsByCreditCard`); exige remover a compra inteira e o banco ainda cobrar parcela depois. A correção precisa ler linhas removidas sem confirmá-las. |
| 2 | blind, edge | Remover a única parcela confirmada deixa as futuras ativas, ocultas e sem botão para removê-las | medium | intent_gap | `delete` não toca no grupo; a lista esconde futuras (Q1) e a ação fica na linha removida. |
| 3 | blind, edge | Editar uma parcela (outro cartão, conta, valor, tipo) não alcança o grupo: futuras ficam no cartão antigo e com o valor antigo | medium | intent_gap (mesma causa do 2) | `update` grava só a linha; a spec não diz o que acontece com o grupo. |
| 4 | blind, edge | O 409 de edição concorrente mostra a mensagem genérica e não recarrega | low | patch | `save()` só trata 400/404. |
| 5 | blind, edge | Falha em `loadOptions` rotula toda linha como "Conta removida" | low | rejeitado | Só com falha de rede nas três listas; a correção é um estado novo. |
| 6 | blind, edge | Transação de conta ou cartão removido nunca pode ser editada (404), nem a descrição | medium | patch | `lockAccounts` só traz contas ativas e o cartão novo é exigido ativo, mesmo quando o destino não muda. |
| 7 | blind, edge, gap | `delete` e `removeFutureInstallments` usam `OffsetDateTime.now()` em vez do `clock` | low | patch | `TransactionService.java` :686, :705. |
| 8 | blind | Marcar o grupo com 0 futuras removidas o bloqueia para sempre | false | rejeitado | Sem futuras ativas, todos os números já existem; não há o que criar. |
| 9 | blind | Futuras criadas por importação em revisão entram no "restam" e são removidas | low | rejeitado | Exige a mesma compra numa importação ainda em revisão; ao concluí-la nada se perde. |
| 10 | blind | Valor fora do limite gera "Informe um valor maior que zero" | low | patch | Mensagem errada no ramo `fits()` de `update`. |
| 11 | blind | "Remover parcelas futuras desta compra" sem `aria-label` com a descrição | low | patch | "Editar" e "Remover" têm; a spec pede texto acessível. |
| 12 | blind | Editar a data para outro mês tira a linha da tela sem explicação | low | rejeitado | Comportamento esperado da lista mensal (Q1). |
| 13 | edge | Linha confirmada por importação em revisão pode ser editada e voltar a futura ao descartar | low | rejeitado | Só parcelas de cartão (sem saldo); exige editar durante a revisão e depois descartar. |
| 14 | edge | Remoção concorrente mostra "0 parcelas futuras removidas" | low | rejeitado | O botão só aparece com futuras; exige duas abas. |
| 15 | edge | Com descrição e categoria editadas juntas, o padrão aprende o texto do usuário e não casa com o extrato seguinte | medium | intent_gap | `findDescriptions` lê `description`; a importação casa `row.getDescription()` do banco; o bloco congelado manda aprender depois de gravar a descrição. |
| 16 | gap | Troca de tipo (débito ↔ crédito) sem teste | gap | patch | Todo `update` do teste usa DEBIT. |
| 17 | gap | Linha fora do saldo que entra nele (data ou cartão → conta) sem teste | gap | patch | Todo `update` parte de `affects=true`. |
| 18 | gap | Cartão/categoria de outro usuário ou removidos na edição sem teste | gap | patch | Só conta inativa é testada. |
| 19 | gap | `TransactionsView` sem teste de falha (400/404/409, remoção) | gap | patch | Nenhum mock rejeita. |
| 20 | gap | Nenhum teste de importação depois de remover as futuras | low | rejeitado | O planner cobre a regra; o campo vem da entidade. |
| 21 | blind, edge (2ª rodada) | Remover a última parcela (Q3) grava a marca só em linhas que logo são removidas; a próxima importação recria as futuras | low | rejeitado | carried #1: mesma alegação (o planner só lê linhas ativas). O diálogo do Q3 não promete durabilidade; só o de "Remover parcelas futuras" promete, e lá a parcela confirmada continua ativa com a marca. |
| 22 | blind | Editar o valor da parcela mais antiga tira as próximas da tolerância do grupo e duplica as futuras | medium | patch | `findGroup` compara só com `group.amount`, tirado do primeiro membro (`InstallmentPlanner.java:154`). |
| 23 | blind | Reimportar o mesmo extrato depois de editar data/valor cria duplicata | low | rejeitado | Consequência de editar; exige reimportar o mesmo arquivo sem id (CSV). |
| 24 | blind | Mudar a data através da referência D3 mexe no saldo sem avisar | false | rejeitado | É a regra D3 do bloco congelado ("D3 recomputed for the new date"). |
| 25 | blind | Data futura entra no saldo na hora, sem limite superior | false | rejeitado | Igual à importação (D3); a spec só pede aviso. |
| 26 | blind, edge | `removeFutureInstallments` com 0 marca o grupo | false | rejeitado | carried #8. |
| 27 | blind | "restam N" conta as futuras da compra, não as depois da linha | false | rejeitado | O texto descreve a compra: N parcelas ainda por vir. |
| 28 | blind, edge | `CARD_PAYMENT` pode trocar tipo, destino e categoria | low | rejeitado | Escolha do usuário; padrões e IA não processam pagamentos de fatura (D8). |
| 29 | edge | Limpar a categoria grava USER/100 | low | patch | Ramo `categoryChanged` não distingue null. |
| 30 | edge | `loadOptions` com falha rotula tudo como removido | low | rejeitado | carried #5. |
| 31 | edge | Timer de sucesso dispara depois de desmontar a tela | low | rejeitado | Escrever num ref de componente desmontado não tem efeito visível. |
| 32 | edge | "saldo voltou ao anterior" falha para linha antes da referência | false | rejeitado | Regra D3 da matriz ("Conciliada depois"). |
| 33 | blind | Validação de valor duplicada entre DTO e service | low | rejeitado | Inofensiva; protege chamadas fora do HTTP. |
| 34 | blind | Mês não fica na URL; "Próximo ›" sem limite | low | rejeitado | Conveniência fora da intent. |
| 35 | blind | Trava do cartão fora da ordem documentada | false | rejeitado | Advisory lock vem antes, como no `complete`; a importação do mesmo cartão espera nele. |
| 36 | gap | Nenhum teste mantém a mesma categoria (removida) numa edição | gap | patch | Todos os testes partem de categoria null ou trocam. |
| 37 | gap | Valor de `installment_futures_removed_at` no export não é verificado | gap | patch | `assertExportsEveryColumn` só checa a chave. |
| 38 | gap, blind | 409 de `lockRow` sem teste no backend | gap | defer | Reproduzir a corrida exige duas transações coordenadas. |
