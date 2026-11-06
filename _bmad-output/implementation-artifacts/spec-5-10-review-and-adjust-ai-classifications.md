---
title: 'Story 5.10 — Ajustar as classificações e concluir a importação'
type: 'feature'
created: '2026-11-06'
baseline_commit: 'a7c8d54d716078184769b831b80ea025f21708f3'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-5-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** a revisão mostra as transações, mas o usuário não corrige uma categoria errada nem conclui a importação: as linhas ficam `PENDING` para sempre e o saldo não muda. O passo 6 do onboarding ainda diz que a importação "chega em breve".

**Approach:** na `/importar/:id/revisao`, permitir editar a categoria (uma linha ou várias) e concluir a importação numa transação só, com o efeito de saldo da D3. O passo 6 do onboarding passa a usar o mesmo upload da `/importar` e registra `first_import_completed` quando a primeira importação termina.

## Boundaries & Constraints

**Always:**
- **API:** `PATCH /api/v1/imports/{id}/transactions/category` com `{transactionIds, categoryId}` serve a edição individual e a em lote. `POST /api/v1/imports/{id}/complete` conclui. Ambas atrás do `SubscriptionAccessFilter`.
- **Editar:** só em `REVIEW` (409 fora dele), com a linha da importação travada (`FOR UPDATE`, como `approveHighConfidence`). Grava `category_id`, `classification_source = USER`, confiança 100 e `APPROVED`. Só linhas `REGULAR` da revisão: id fora disso (outra importação, futura, `CARD_PAYMENT`) responde 404 e nada é gravado. A categoria é filha, ativa e do usuário: removida ou alheia responde 404, grupo (sem pai) responde 400.
- **Parcelas:** a categoria editada vai também para as futuras do mesmo `installment_group_id` e da mesma importação, com a mesma origem e confiança (5.8 – 1A).
- **Concluir:** ordem `lockTarget` → update com guarda `REVIEW → COMPLETED` → lock da conta → saldo. `review_status = CONFIRMED` em todas as linhas da importação, inclusive futuras (5.9 – 2A). Linha sem categoria fica assim e continua "Não classificado".
- **Idempotência:** concluir uma importação já `COMPLETED` responde 200 sem mudar nada e sem auditoria. Qualquer outro status responde 409.
- **Saldo (D3, D8):** só importações de conta. Entram `REGULAR` e `CARD_PAYMENT` com `transaction_date` posterior à data de referência: essas linhas ficam `affects_balance = true`, e `current_balance` recebe a soma de CREDIT menos DEBIT. Um snapshot `transaction` pelo `BalanceHistoryRecorder` quando a soma é diferente de 0.
- **Data de referência:** a maior entre o último snapshot `initial`/`edit`/`reconciliation` e `last_reconciliation_date` (reconciliação sem diferença não gera snapshot), convertida para a data de São Paulo. Transação no próprio dia da referência não altera o saldo.
- **Estouro:** saldo fora de `fits()` responde 400 com mensagem em português, rollback completo, e a importação continua em `REVIEW`.
- **Destino removido:** conta ou cartão apagados depois do upload respondem 409 "restaure ou descarte a importação", e nada muda.
- **Tela:** "Editar" abre um diálogo com a categoria atual, um select agrupado e data, descrição e valor só leitura. Checkbox por linha `REGULAR` e "Alterar categoria" para as selecionadas. "Concluir importação" sempre pede confirmação, porque não tem volta; o diálogo informa quantas linhas seguem sem revisão e sem categoria. Terminada, volta para a `/importar`, que mostra "Concluída" com as contagens.
- **Onboarding:** extrair `ImportUploader` da `ImportView` (destino, zona de envio, progresso, lista, polling) e usá-lo nas duas telas. `POST /onboarding/me/import` aceita corpo opcional `{importId}`: com ele, exige importação `COMPLETED` do usuário (404 alheia, 409 outro status), grava `first_import_completed` e audita `{skipped:false}`. Sem corpo, continua `import_skipped`.
- **Volta ao onboarding:** ao concluir, se o usuário está no passo 6, a revisão faz o POST e vai para `/onboarding`. O passo 6 mostra "Continuar" quando já existe importação `COMPLETED`, o que cobre falha nesse POST e importação sem nada novo (5.5 – 1A). "Importar depois" continua.
- **Auditoria:** `import_category_changed` e `import_completed`, só com `import_id` e contagens.

**Never:**
- Gravar em `ai_classification_history` ou criar `user_classification_patterns` (5.11).
- Editar data, descrição ou valor (5.12).
- Salvar a entidade `Transaction` inteira nas ações em lote.
- Log com descrição, categoria ou nome de contraparte.
- Criar migration: `APPROVED`, `USER` e o snapshot `transaction` já estão nos CHECKs.

## I/O & Edge-Case Matrix

| Cenário | Entrada / estado | Resultado |
|---|---|---|
| Editar uma linha Média | categoria diferente ou a mesma | `USER`, 100, `APPROVED`; vai para "Revisadas por você"; Y diminui 1 |
| Lote com id inválido | 3 válidos e 1 de outra importação | 404, nada gravado |
| Editar parcela 3/10 | 7 futuras na importação | as 7 recebem a categoria |
| Editar fora de `REVIEW` | `COMPLETED` ou `DISCARDED` | 409 |
| Categoria é grupo | sem pai | 400 |
| Concluir conta | referência 03/11; linhas 02, 03 e 04/11 | só a de 04/11 altera o saldo; 1 snapshot |
| Soma zero | +100 e −100 depois da referência | `affects_balance = true`, sem snapshot |
| Pagamento de fatura | `CARD_PAYMENT` depois da referência | altera o saldo da conta (D8) |
| Concluir cartão | qualquer data | `CONFIRMED`, sem saldo, sem snapshot |
| Reconciliação sem diferença | `last_reconciliation_date` 05/11, snapshot 01/11 | referência 05/11 |
| Concluir duas vezes ou em paralelo | segunda chamada | 200, saldo e snapshots iguais |
| Concluir em `DISCARDED` | status fechado | 409 |
| Estouro de saldo | soma passa de `MAX_BALANCE` | 400, importação em `REVIEW`, nada gravado |
| Conta removida | destino apagado | 409, nada gravado |
| Onboarding sem conta nem cartão | passo 6 | avisa e mantém "Importar depois" |
| Onboarding no passo 5 | conclui pela `/importar` | sem POST, volta para `/importar` |
| Onboarding `{importId}` em `REVIEW` | passo 6 | 409, passo não muda |

## Decisões

- **1A — linha editada (Luiz, 2026-11-06):** a linha com origem `USER` vai para o novo grupo "Revisadas por você", depois de Baixa, com ícone e cor próprios. Não conta em X nem em Y.
- **2A — salvar a mesma categoria (Luiz, 2026-11-06):** conta como revisada: `APPROVED`, `USER`, 100. A 5.11 distingue correção por `was_corrected`, que compara as categorias.
- **Tamanho (Luiz, 2026-11-06):** a spec fica inteira, com edição, conclusão e onboarding.

</frozen-after-approval>

## Code Map

Prefixos: `B` = `porquinho-backend/src/main/java/com/porquinho`, `T` = `porquinho-backend/src/test/java/com/porquinho`, `F` = `porquinho-frontend/src`.

- `B/service/TransactionImportReviewService.java` -- `approveHighConfidence` (:79) é o modelo de edição; `discard` (:101) é o modelo de conclusão. Concentrar a escrita da categoria num método só: é onde a 5.11 vai se ligar.
- `B/controller/TransactionImportController.java` -- rotas da revisão (:73-89).
- `B/repository/TransactionRepository.java` -- modelos `approveHighConfidence` (:96), `softDeleteByImport` (:133, inclui futuras), `applyAiClassificationToFutures` (:74).
- `B/repository/TransactionImportRepository.java` -- `findByIdAndUserIdForUpdate` (:55), `markDiscarded` (:119), `lockTarget` (:147). Chave em `TransactionImportProcessor.lockKey` (:297, package-private).
- `B/repository/CategoryRepository.java` -- falta finder de categoria ativa por id. Grupos não são categorias de transação (`TransactionClassificationService.java:134`).
- `B/service/TransferService.java:104-125` -- modelo de trava, `fits()`, set, save e record. `AccountRepository.findActiveByIdForUpdate` (:42).
- `B/entity/AccountBalanceHistory.java:20-23` -- falta `EVENT_TRANSACTION`. `B/repository/AccountBalanceHistoryRepository.java` -- falta o último snapshot por eventos. `snapshot_date` é `OffsetDateTime`, `transaction_date` é `LocalDate`, o `Clock` é UTC.
- `B/service/OnboardingService.java:292-322`, `B/controller/OnboardingController.java:118` -- `STEP_IMPORT_SKIPPED` (:44); nova constante `first_import_completed`.
- `F/views/ImportReviewView.vue` -- `groupOf` (:271), `approveAll` (:373), `handleActionError` (:363), diálogo de descarte como modelo, barra de ações (:63-80).
- `F/views/ImportView.vue` -- upload embutido: destino (:52-70), zona (:73-105), progresso (:107-119), lista (:133-165), `statusLabel` (:361), polling (:385-414).
- `F/components/onboarding/ImportStep.vue` -- sai `import-coming-soon`; ficam os guias dos bancos e `finish()`. `F/services/onboardingService.ts:63`.
- `F/services/categoryService.ts:11` `listCategories`; grupos por `parentCategoryId`, como em `CustomizeCategoriesStep.vue:263`. Select nativo com `optgroup` e `SELECT_CLASS` (`ImportView.vue:184`). Checkbox: `CategoriesStep.vue:76`.
- `F/lib/balanceEvents.ts:9` -- o evento `transaction` já tem rótulo e marcador no gráfico; não precisa mudar.
- **Não mexer:** `InstallmentPlanner`, `TransactionImportProcessor`, `DataExportService` (sem coluna nova).

## Tasks & Acceptance

**Execution:**
- [x] `B/entity/AccountBalanceHistory.java`, `B/repository/AccountBalanceHistoryRepository.java` -- `EVENT_TRANSACTION` e consulta da referência.
- [x] `B/repository/TransactionRepository.java`, `TransactionImportRepository.java`, `CategoryRepository.java` -- contar editáveis, aplicar categoria, propagar às futuras, confirmar todas, marcar `affects_balance`, somar o efeito, `markCompleted` com guarda, ler status.
- [x] `B/dto/UpdateImportCategoryRequest.java` -- ids não vazios e categoria obrigatória.
- [x] `B/service/TransactionImportReviewService.java`, `B/controller/TransactionImportController.java` -- editar e concluir.
- [x] `B/service/OnboardingService.java`, `B/controller/OnboardingController.java` -- corpo opcional `{importId}`.
- [x] `F/types/transactionImport.ts`, `F/services/transactionImportService.ts`, `F/services/onboardingService.ts` -- chamadas novas.
- [x] `F/components/import/ImportUploader.vue`, `F/views/ImportView.vue` -- extrair sem mudar o comportamento; rótulo de `COMPLETED` com contagens.
- [x] `F/components/onboarding/ImportStep.vue` -- usar o uploader; "Continuar".
- [x] `F/views/ImportReviewView.vue` -- editar, selecionar, alterar em lote, concluir, voltar ao onboarding.
- [x] Testes: matriz no `TransactionImportReviewServiceTest`, integração de saldo e de conclusão simultânea, controller, onboarding, `ImportReviewView.spec.ts`, `ImportUploader`, `ImportStep.spec.ts`, service do frontend.
- [x] `epic-5-context.md`, épico e `deferred-work.md` -- registrar as decisões. Fechar `deferred-work.md:200`: parcelas futuras só existem em cartão, e a D3 não mexe em saldo de cartão.

**Acceptance Criteria:**
- Given a revisão, when edito uma categoria, then vejo data, descrição e valor sem poder alterá-los, e a linha aparece como revisada por mim, com ícone e texto além da cor.
- Given várias linhas marcadas, when uso "Alterar categoria", then todas mudam e a seleção é limpa.
- Given uma importação de conta em `REVIEW`, when concluo, then o saldo e o gráfico de histórico mostram o efeito das transações posteriores à referência.
- Given um erro ao concluir, when a API responde 400 ou 409, then vejo a mensagem em português e a revisão continua disponível se a importação seguir em `REVIEW`.
- Given o passo 6 do onboarding, when envio um arquivo, reviso e concluo, then volto ao onboarding no passo 7 com `first_import_completed` gravado.
- Given a `/importar`, when a extração termina, then upload, polling e lista funcionam como antes.

## Implementation Notes

- `completeImport` do `OnboardingService` passou a receber `importId` (nulo = "Importar depois"), com um só ponto de entrada; os testes existentes foram ajustados.
- O 404 do `PATCH` de categoria não usa `handleActionError`: nesse caso quem sumiu foi a linha ou a categoria, não a importação, e a tela mostra a mensagem e recarrega.
- A conclusão de cartão trava o cartão com `findActiveByIdForUpdate`, para a remoção não correr junto com a conclusão.
- O `ImportUploader` avisa a lista por `imports-change`; é assim que o passo 6 sabe quando mostrar "Continuar", inclusive depois do polling.
- Sem nenhum snapshot de referência (não deveria ocorrer, a V20 fez o backfill), a data de referência é `accounts.created_at`.

## Spec Change Log

- **Revisão 1 (Luiz, 2026-11-06):** o achado 6 da triagem (transferência registrada durante a revisão entra em dobro no saldo) foi levado ao humano, que escolheu manter o código, sem reverter, e adiar o caso para `deferred-work.md`. A spec não mudou. Estado aceito: o saldo pode ficar errado até a reconciliação. KEEP: todo o código da implementação.

## Review Triage Log

| # | Camada | Achado | Veredito | Rota | Evidência |
|---|---|---|---|---|---|
| 1 | blind, edge | "Continuar depois" e descarte no passo 6 deixam o usuário sem caminho de volta ao onboarding | medium | patch | `/onboarding` só é alcançado por login, registro e callback; a `/importar` só liga ao `/dashboard`. |
| 2 | blind | "Importar depois" com importação em andamento perde `first_import_completed` | low | rejeitado | O usuário escolhe pular, e a spec mantém o botão. Nada lê o passo gravado hoje; a correção pede um estado novo. |
| 3 | blind, edge | "Salvar" habilitado com categorias carregando ou com falha | medium | patch | `chosenCategoryId` é preenchido antes da carga, e o `disabled` não olha a carga. |
| 4 | blind | A propagação às futuras não é informada | low | patch | O texto diz "só a categoria desta transação"; parcela é caso comum e a correção é de texto. A contagem na resposta foi rejeitada: é superfície nova. |
| 5 | blind | O diálogo de conclusão não diz o que acontece com o saldo | low | patch | O texto é impreciso pela D3 e em cartão. A contagem de linhas no saldo foi rejeitada: é superfície nova. |
| 6 | blind | Transferência registrada durante a revisão entra em dobro no saldo | medium | intent_gap, resolvido: adiar | A D4 só roda no processamento, e `REFERENCE_EVENTS` exclui `transfer`. O humano manteve o código e adiou. |
| 7 | blind, edge | `transactionIds` sem tamanho máximo | low | patch | Acima do limite de binds vira 500. A correção é uma anotação. |
| 8 | blind, edge | `{}` ou `{"importId":null}` grava `import_skipped` | low | rejeitado | Só um bug de cliente chega aqui: o frontend sempre envia o id. A correção é uma guarda nova. |
| 9 | blind | Três botões sem quebra no passo 6 | low | patch | `Button` tem `whitespace-nowrap`; o container não tem `flex-wrap`. |
| 10 | blind | Falta "selecionar todas" | low | rejeitado | A spec pede checkbox por linha. É funcionalidade nova, não defeito. |
| 11 | blind, gap | Travas de `complete` e `updateCategory` sem teste | gap | patch | Nenhum teste segura o advisory lock nem a linha; os irmãos da 5.9 têm esse teste. |
| 12 | blind | Travas dos diálogos sem teste | low | patch | Nenhum spec exercita `update:open` com a requisição pendente. |
| 13 | edge | Lote grava parcialmente se a linha vira futura entre a contagem e o update | low | patch | Janela curta, mas a correção é uma comparação, e o contrato é 404 sem escrita. |
| 14 | edge | Falha em `getMyOnboarding` depois de concluir leva à `/importar` sem o POST | low | rejeitado | Exige falha de rede logo depois de um POST bem-sucedido. Ir para `/onboarding` às cegas atrapalharia quem já terminou. |
| 15 | gap | Referência sem teste com `edit` mais recente e reconciliação mais antiga | gap | patch | Tirar `EVENT_EDIT` ou trocar a condição de `isAfter` não derruba nenhum teste. |
| 16 | gap | Limpeza da seleção no `load()` sem teste | gap | patch | Apagar a limpeza não derruba nenhum teste. |

## Design Notes

A conclusão toma o advisory lock antes do update com guarda porque o descarte lê o status da importação que confirmou (5.9 – 3A). A ordem advisory → linha da importação → conta é a mesma do descarte, e a transferência só usa a conta, então não há ciclo.

`first_import_completed` fica no POST, e não dentro da conclusão, porque o épico pede esse caminho e a importação não deve falhar por causa do onboarding. O botão "Continuar" do passo 6 cobre a falha desse POST.

## Verification

**Commands:**
- `cd porquinho-backend && ./mvnw test` -- expected: 0 falhas (JDK 21)
- `cd porquinho-frontend && npm run lint && npx vue-tsc --build && npm run test:unit -- --run` -- expected: sem erros
