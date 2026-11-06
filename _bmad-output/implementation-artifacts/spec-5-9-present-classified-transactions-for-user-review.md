---
title: 'Story 5.9 — Apresentar as transações classificadas para revisão'
type: 'feature'
created: '2026-11-06'
baseline_commit: 'cc417d1be94a92b87d317fd90f3f87168250acc8'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-5-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** a importação chega a `REVIEW`, mas nenhuma tela mostra as transações. O usuário não vê o que a IA classificou, não consegue aprovar o que está certo e não tem como desistir de um arquivo errado.

**Approach:** criar a tela `/importar/:id/revisao`, com as transações agrupadas por confiança, os marcadores de Pix e parcela e a aprovação em lote da alta confiança. A importação pode ser retomada pela `/importar` ou descartada. A edição e a conclusão ficam na 5.10.

## Boundaries & Constraints

**Always:**
- **API:** `GET /api/v1/imports/{id}/transactions`, `POST /api/v1/imports/{id}/approve-high-confidence` e `POST /api/v1/imports/{id}/discard`, atrás do `SubscriptionAccessFilter`. Recurso de outro usuário ou removido responde 404 sem gravar nada. Aprovar ou descartar fora de `REVIEW` responde 409 com mensagem em português.
- **Dados por transação:** id, data, descrição, valor, tipo, `kind`, categoria (id, nome e grupo, resolvidos no backend, inclusive categoria removida depois), origem, confiança, `reviewStatus`, `isPix`, `pixDetails`, `isInstallment`, `installmentNumber`, `installmentTotal`, `isFutureInstallment`.
- **Grupos:** Alta (80+), Média (50–79), Baixa (<50), nessa ordem. `CARD_PAYMENT` fica à parte, em "Pagamento de fatura", sem indicador de confiança.
- **Não classificado:** sem categoria, ou confiança nula numa linha `REGULAR` (dados anteriores à 5.8). Fica no grupo Baixa, com o texto "Não classificado".
- **Indicadores:** Alta = check verde, Média = alerta amarelo, Baixa = bandeira vermelha, sempre com texto. Pix com ícone e texto. Parcela com "Compra parcelada: 3/10 — restam 7 parcelas" (restam = total − número).
- **Contagem:** "X transações de alta confiança, Y precisam de revisão". X = Alta; Y = Média + Baixa. `CARD_PAYMENT` não entra em nenhum dos dois. Singular e plural pelo `plural()`.
- **Aprovar todas:** idempotente: a segunda chamada não muda nada. Não mexe em saldo, `affects_balance` nem status da importação. Só linhas `REGULAR` com confiança ≥ 80.
- **Descartar:** com diálogo de confirmação, advisory lock por destino (`lockTarget`) e update com guarda `REVIEW → DISCARDED`. Soft delete das linhas, para seguir a convenção do projeto. As linhas apagadas deixam de ser candidatas da 5.5 e da 5.7, e reenviar o mesmo arquivo importa de novo.
- **Retomar:** a linha `REVIEW` da `/importar` leva à revisão.
- **Auditoria:** `import_high_confidence_approved` e `import_discarded`, só com `import_id` e contagens.
- Consultas em `transactions` filtram `deleted_at IS NULL`.

**Never:**
- Mexer em saldo, `BalanceHistoryRecorder` ou `COMPLETED` (5.10).
- Salvar a entidade `Transaction` inteira nas ações em lote: usar `UPDATE` só das colunas alteradas.
- Trocar o `ImportStep.vue` do onboarding (AC da 5.10).
- Log com descrição, nome de contraparte ou categoria.

## I/O & Edge-Case Matrix

| Cenário | Entrada / estado | Resultado |
|---|---|---|
| Lista normal | 92, 60, 30, `CARD_PAYMENT` | Alta, Média, Baixa e "Pagamento de fatura"; "1 transação de alta confiança, 2 precisam de revisão" |
| Limites | 80, 79, 50, 49 | Alta, Média, Média, Baixa |
| Confiança nula | `REGULAR`, null | Baixa, "Não classificado" |
| Categoria removida | `category_id` de categoria apagada | mostra o nome gravado da categoria |
| Importação de outro usuário | id alheio | 404 nas três rotas, nada gravado |
| Aprovar em `PROCESSING` | status diferente de `REVIEW` | 409, nada muda |
| Aprovar duas vezes | segunda chamada | 0 linhas alteradas, sem erro |
| Descartar duas vezes | já `DISCARDED` | 409, nada muda |
| Descartar e reenviar | mesmo arquivo | linhas importadas de novo, sem duplicatas |
| Importação sem pendência | só `CARD_PAYMENT` | X = 0, Y = 0; "Aprovar todas" desabilitado |
| Compra 3/10 | 1 parcela e 7 futuras | 1 linha, com "restam 7 parcelas"; X conta 1 |
| Descartar a criadora | A criou a futura, B a confirmou | as demais linhas de A somem; a confirmada passa para B e fica `CONFIRMED` se B estiver `COMPLETED` |
| Descartar a confirmadora | B confirmou futura de A | a linha volta a futura de A; reenviar B confirma de novo |
| Descartar com duplicatas posteriores | C, posterior a A no mesmo destino, tem duplicatas | o diálogo nomeia C e pede o reenvio; sem C, ou com C descartada, não há aviso |

## Decisões

- **1A — aprovação (Luiz, 2026-11-06):** "Aprovar todas" grava o novo valor `APPROVED` em `review_status` (V29). `CONFIRMED` continua significando importação concluída.
- **2A — parcelas futuras (Luiz, 2026-11-06):** não aparecem na revisão nem contam em X ou Y; "restam N parcelas" as representa. Seguem a parcela do arquivo no descarte e na conclusão. `isFutureInstallment` continua no DTO.
- **3A — descarte com parcela confirmada por outra importação (Luiz, 2026-11-06):** coluna `confirmed_by_import_id` (V29), gravada na confirmação. Descartar a importação que criou a futura transfere a linha para a que a confirmou. Descartar a que confirmou volta a linha a futura (`is_future_installment = true`, `external_id` nulo, marca nula), mantendo descrição, data e valor do arquivo descartado. Confirmações já gravadas não têm a marca.
- **4A — botões da 5.10 (Luiz, 2026-11-06):** "Editar" fica para a 5.10. "Continuar" vira "Continuar depois" e volta para a `/importar`.
- **Tamanho (Luiz, 2026-11-06):** a spec fica inteira, sem dividir.
- **5A — descarte com duplicatas em outra importação (Luiz, 2026-11-06):** descartar A apaga as linhas que um arquivo posterior ignorou como duplicatas delas. O diálogo avisa quando existe importação do mesmo destino, criada depois de A, com `duplicatesFound > 0` e status diferente de `DISCARDED` e `FAILED`, e nomeia esses arquivos: reenviá-los recupera os lançamentos. O confirmar fica desabilitado até a checagem terminar. O frontend usa `listImports`: se A está entre as 20 mais recentes, as posteriores também estão. Sem coluna nova e sem bloquear o descarte.

</frozen-after-approval>

## Code Map

Prefixos: `B` = `porquinho-backend/src/main/java/com/porquinho`, `T` = `porquinho-backend/src/test/java/com/porquinho`, `F` = `porquinho-frontend/src`.

- `B/controller/TransactionImportController.java` -- `list` (:38), `create` (:48), `get` (:61); usuário por `@AuthenticationPrincipal Jwt`.
- `B/service/TransactionImportService.java` -- `get` (:163), 404 por `findByIdAndUserId` + `ResourceNotFoundException(NOT_FOUND_MESSAGE)`; `AUDIT_CREATED` (:41). `ConflictException` para o 409.
- `B/entity/Transaction.java` -- enums (:25-31), `importId` `updatable=false` (:48), sem `@SQLRestriction`.
- `B/repository/TransactionRepository.java` -- modelo `applyAiClassification` (:63); `findClassifiableByImport` (:54) exclui `CARD_PAYMENT` e futuras; falta consulta com todas as linhas da importação.
- `B/repository/TransactionImportRepository.java` -- modelo de guarda `markReadyForReview` (:101-106); `lockTarget` (:129) exige transação.
- `B/service/InstallmentPlanner.java` -- `confirm` (:178), chamado na linha :104; `future` (:190). `B/service/TransactionImportProcessor.java:184-185` conta a confirmada em `duplicatesFound`.
- `B/service/AuditLogService.java:47` -- `log(eventType, userId, ip, metadataJson)`.
- `B/repository/CategoryRepository.java` -- `findActiveByUserId` só traz ativas; a revisão precisa de consulta que inclua removidas.
- `B/service/PixDetector.java` -- `pixDetails` é JSON em String: `direction`, `counterpart_name`, `bank`. O DTO devolve objeto, não String.
- `B/service/DataExportService.java` e `T/service/DataExportServiceTest.java` (`assertExportsEveryColumn`) -- entra `confirmed_by_import_id`.
- `F/router/index.ts` -- modelo `/contas/:id/historico` (:77-82). `F/views/AccountHistoryView.vue` -- modelo de carga e estados `notFound`/`loadFailed`.
- `F/views/ImportView.vue` -- `REVIEW_NEXT_STEP` (:196), rótulo (:365-368), lista (:138-156). `F/views/__tests__/ImportView.spec.ts:317-319` testa o texto que sai.
- `F/services/transactionImportService.ts`, `F/types/transactionImport.ts`.
- `F/views/SessionsView.vue:103-125` -- modelo de diálogo. `F/components/ui/` não tem badge nem checkbox.
- `F/lib/money.ts:27` `formatBRL`. `F/lib/date.ts:9` `formatDateBR` -- **não usar para `LocalDate`**: `new Date('2026-11-03')` é UTC e sai um dia antes em São Paulo.
- Migration nova: V29 (CHECK de `review_status` com `APPROVED`, coluna `confirmed_by_import_id`).

## Tasks & Acceptance

**Execution:**
- [x] `porquinho-backend/src/main/resources/db/migration/V29__add_import_review_support.sql` -- `APPROVED` no CHECK de `review_status`; coluna `confirmed_by_import_id` com FK e índice.
- [x] `B/entity/Transaction.java`, `B/service/InstallmentPlanner.java`, `B/service/TransactionImportProcessor.java` -- `APPROVED`, `confirmedByImportId` e a marca gravada em `confirm` (3A).
- [x] `B/repository/TransactionRepository.java` -- listar por importação sem futuras, aprovar em lote, soft delete em lote, transferir e reverter confirmadas (3A; `import_id` tem `updatable=false`, então a transferência usa SQL nativo).
- [x] `B/repository/TransactionImportRepository.java` -- `markDiscarded` com guarda `REVIEW`.
- [x] `B/repository/CategoryRepository.java` -- buscar por ids, com removidas.
- [x] `B/dto/ImportTransactionResponse.java`, `B/dto/ImportReviewActionResponse.java` -- novos DTOs.
- [x] `B/service/TransactionImportReviewService.java` -- listar, aprovar e descartar, com auditoria.
- [x] `B/controller/TransactionImportController.java` -- as três rotas.
- [x] `B/service/DataExportService.java` -- coluna `confirmed_by_import_id`.
- [x] `F/lib/date.ts` -- formatador de data sem hora.
- [x] `F/types/transactionImport.ts`, `F/services/transactionImportService.ts` -- tipos e chamadas.
- [x] `F/views/ImportReviewView.vue`, `F/router/index.ts` -- tela e rota.
- [x] `F/views/ImportView.vue` -- link "Revisar", novo rótulo de `REVIEW` e rótulo de `DISCARDED`.
- [x] Testes: `TransactionImportReviewServiceTest` (matriz), integração com reenvio depois do descarte, controller (404 e 409), export, `ImportReviewView.spec.ts`, `ImportView.spec.ts`, `date`.
- [x] `epic-5-context.md`, épico e `deferred-work.md` -- registrar as decisões; corrigir "5.10" para "5.9" no descarte.

**Acceptance Criteria:**
- Given uma importação em `REVIEW`, when abro `/importar/:id/revisao`, then vejo data, descrição, valor em BRL, categoria sugerida, indicador de confiança e marcadores, com a data igual à do extrato.
- Given os indicadores, when a cor não é percebida, then ícone e texto ainda informam o nível.
- Given "Aprovar todas de alta confiança", when confirmo, then as linhas com 80+ ficam aprovadas, e sair e voltar mantém o estado.
- Given uma importação em `REVIEW`, when descarto, then ela fica `DISCARDED`, nenhuma linha dela aparece, e a `/importar` mostra o novo status.
- Given uma importação em `REVIEW` deixada pela metade, when volto à `/importar`, then consigo reabrir a revisão.

## Implementation Notes

- "Aprovar todas" exclui linha sem categoria, mesmo com confiança ≥ 80: ela aparece como "Não classificado" e conta em Y. A aprovação age no clique, sem diálogo, e trava a linha da importação (`FOR UPDATE`) para não correr com um descarte.
- Aprovar sem nada a alterar responde 200 com 0 linhas e não grava auditoria.
- Reverter uma confirmação (3A) volta `APPROVED` a `PENDING`, porque a linha pode receber dados de outro arquivo; `CONFIRMED` fica como está.
- `TransactionImportProcessor.java` não precisou mudar: a marca de 3A é gravada em `InstallmentPlanner.confirm`.
- A última parcela mostra "Compra parcelada: 10/10 — última parcela".

## Spec Change Log

- **Revisão 1 (Luiz, 2026-11-06):** o achado 1 da triagem (perda de lançamentos ignorados como duplicatas) foi levado ao humano, que escolheu a decisão 5A e manter o código, sem reverter. Entraram a decisão 5A e uma linha na matriz. Estado evitado: descarte que apaga lançamentos sem aviso. KEEP: todo o código da implementação, que passou em 1249 testes do backend e 652 do frontend.

## Review Triage Log

| # | Camada | Achado | Veredito | Rota | Evidência |
|---|---|---|---|---|---|
| 1 | blind, edge | Descartar A apaga lançamentos que outra importação pulou como duplicatas de A | high | intent_gap, resolvido pela decisão 5A | `findActiveByAccountBetween` e `findActiveByCreditCardBetween` não filtram `review_status`, então linhas em `REVIEW` são candidatas. Nada liga a entrada pulada à linha de A, e a decisão 3A só cobre parcelas. |
| 2 | blind, edge | Parcela transferida para importação `COMPLETED` fica `CONFIRMED` com `affects_balance = false` | medium | defer | Real quando a confirmadora concluiu por `markCompletedWithNothingNew`. A intent proíbe mexer em saldo na 5.9, então a 5.10 precisa tratar. |
| 3 | blind, edge, gap | Reverter a confirmação mantém `APPROVED` na futura | medium | patch | A linha confirmada por B aparece na revisão de A e pode ser aprovada; `revertConfirmedInstallments` não toca em `review_status`. |
| 4 | blind | "Aprovar todas" troca a tela pelo skeleton e a mensagem nasce com `v-if` | medium | patch | `load()` põe `loading = true`, e o `v-else-if` desmonta a lista e o botão: foco e scroll se perdem. |
| 5 | blind, edge | Cancelar ou fechar o diálogo com o descarte em andamento | low | patch | "Cancelar" e `update:open` seguem ativos com `isDiscarding`; o POST continua. |
| 6 | blind | Aprovar e descartar não se desabilitam entre si | false | rejeitado | O backend serializa as duas ações, e as duas ordens dão resultado correto. |
| 7 | blind | `watch` da rota não fecha o diálogo | low | patch | Com o diálogo aberto, voltar no histórico para outra importação faria a confirmação valer para ela. Correção de uma linha. |
| 8 | blind | Falta guarda para `id` ausente | false | rejeitado | `:id` é obrigatório na rota. |
| 9 | blind | Tela fechada usa a mesma frase para todo status e mantém "Continuar depois" | low | rejeitado | Só se chega lá por URL direta ou 409; a `/importar` só liga linhas `REVIEW`. |
| 10 | blind | Última parcela sai como "restam 0 parcelas" | low | patch | `left = 0` em toda compra na última parcela, caso comum. |
| 11 | blind | Número de parcela acima do total | false | rejeitado | `InstallmentDetector` só aceita `1 ≤ number ≤ total`. |
| 12 | blind | `pixDetails` expõe o JSON inteiro em snake_case | low | rejeitado | São dados do próprio usuário, e a spec pede o objeto. Um DTO explícito é mais que correção direta. |
| 13 | blind | JSON sem `direction` aparece como "Pix recebido" | false | rejeitado | `PixDetector` sempre grava `direction`. |
| 14 | blind | Limite de 80 duplicado entre backend e Vue | low | rejeitado | Sem divergência hoje; devolver o grupo pelo backend adiciona superfície. |
| 15 | blind, gap | Locks do descarte e da aprovação sem teste | gap | patch | Nenhum teste executa transações simultâneas no fluxo de revisão; remover os locks deixa a suíte verde. |
| 16 | blind, gap | Falhas do descarte, 404 em ação e 400 na carga sem teste | gap | patch | `mockDiscard` só é configurado com sucesso. |
| 17 | blind | O `detail` do 409 não aparece | false | rejeitado | `review-closed` substitui a seção e mostra o mesmo texto. |
| 18 | blind | Registro da spec não bate com o diff | low | rejeitado | A correção é editar a spec deste build. |
| 19 | blind | Service ignora o `Clock` do pipeline | low | rejeitado | `TransactionImportService` também usa `OffsetDateTime.now()`; não há dano nomeado. |
| 20 | gap | Funções novas do service do frontend sem teste de URL e verbo | gap | patch | O módulo é mockado em `ImportReviewView.spec.ts`. |
| 21 | gap | Rota `/importar/:id/revisao` sem teste | gap | patch | `useRoute` é mockado e `RouterLink` é stub. |

## Design Notes

A categoria vem resolvida do backend porque `GET /categories` só traz ativas: uma categoria removida depois da classificação não seria encontrada no cliente, e uma segunda chamada não traria ganho.

O soft delete deixa as linhas descartadas no export LGPD, como já acontece com os demais registros removidos. O purge da conta as apaga, e a FK de `ai_classification_history` não precisa de tratamento.

## Verification

**Commands:**
- `cd porquinho-backend && ./mvnw test` -- expected: 0 falhas (JDK 21)
- `cd porquinho-frontend && npm run lint && npx vue-tsc --build && npm run test:unit -- --run` -- expected: sem erros
