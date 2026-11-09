---
title: 'Story 5.13 — Adicionar transações manualmente (despesas em dinheiro)'
type: 'feature'
created: '2026-11-09'
status: 'done'
baseline_commit: 'c3badefb4a3d7e66e0be591391dcf10bcb0714a9'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-5-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Gastos em dinheiro e lançamentos que nenhum extrato trouxe não têm como entrar no Porquinho; o registro de gastos fica incompleto.

**Approach:** Botão "Adicionar transação" em `/transacoes` abre um Dialog (data, descrição, valor, tipo, categoria, conta ou cartão); `POST /api/v1/transactions` cria a linha já `CONFIRMED`, com efeito de saldo pela D3, e a lista recarrega com "Transação adicionada".

## Boundaries & Constraints

**Always:**
- Linha criada com `user_id` do JWT, `import_id` e `external_id` NULL, `original_description` = descrição, `kind=REGULAR`, `source=USER`, confiança 100, `review_status=CONFIRMED`, sem Pix e sem parcela.
- Validação (backend e formulário): data obrigatória (padrão hoje em São Paulo; futura só avisa), descrição obrigatória (≤ 500), valor > 0 e ≤ `MAX_BALANCE`, tipo DEBIT/CREDIT, categoria obrigatória (filha ativa do usuário, nunca grupo), exatamente um de `accountId`/`creditCardId` ativo do usuário. Destino ou categoria de outro usuário ou removidos → 404 sem escrever.
- Saldo D3: conta com data posterior à referência (`BalanceHistoryRecorder.referenceDate`) → valor assinado aplicado com conta travada, `fits()`, snapshot `transaction` e `affects_balance=true`; senão, ou cartão, saldo intacto e `affects_balance=false`.
- Advisory lock (`lockTarget`) do destino antes de tocar na conta, como na 5.12.
- Auditoria `transaction_created` só com ids.
- Formulário com rótulos, foco devolvido ao botão "Adicionar transação" ao fechar, sucesso em `role="status"`.

**Decisões (Luiz, 2026-11-09):**
- **Q1 (A):** sem "Forma de pagamento": o destino (conta ou cartão) já diz por onde o dinheiro saiu; nenhuma migration.
- **Q2 (A):** dinheiro vivo vai numa conta comum "Carteira"; quando o usuário não tem conta ativa, o Dialog mostra a dica "Para gastos em dinheiro, cadastre uma conta Carteira em Contas".

**Never:** aprender padrão com a descrição manual (não é texto do banco, como na Q5 da 5.12); criar tipo de conta "Dinheiro"; orçamento por categoria (Epic 6); mudar a tela de revisão ou a edição.

## I/O & Edge-Case Matrix

| Cenário | Entrada / Estado | Resultado | Erro |
|---|---|---|---|
| Despesa em conta | débito 50, hoje > ref | saldo −50, snapshot, `affects_balance=true`, 201 | — |
| Data ≤ referência | débito 50, data ≤ ref (conciliada) | saldo intacto, `affects_balance=false` | — |
| Cartão | crédito 30 no cartão | saldo de conta nenhum muda | — |
| Sem categoria / grupo | `categoryId` null / grupo | nada gravado | 400 |
| Dois destinos ou nenhum | ambos / nenhum | nada gravado | 400 |
| Conta removida ou alheia | `accountId` inativo | nada gravado | 404 |
| Saldo fora do limite | soma estoura `MAX_BALANCE` | nada gravado | 400 |

</frozen-after-approval>

## Code Map

Prefixos: `B` = `porquinho-backend/src/main/java/com/porquinho`, `T` = `porquinho-backend/src/test/java/com/porquinho`, `F` = `porquinho-frontend/src`.

- `B/service/TransactionService.java` -- `lockAccounts`, `applyDeltas`, `signed`, `toResponses`, constantes de mensagem: reaproveitar. Validação de categoria em `update` (`:136-143`): extrair num helper e usar nos dois. Timestamps pelo `clock`.
- `B/entity/Transaction.java` -- sem `@PrePersist`: o processor define `id`, `createdAt`, `updatedAt` (ver como `TransactionImportProcessor` monta a linha e seguir igual).
- `B/repository/TransactionImportRepository.java:161` `lockTarget`, `B/service/TransactionImportProcessor.java` `lockKey`.
- `B/repository/AccountRepository` `findActiveByIdsForUpdate`, `B/repository/CreditCardRepository` `findActiveByIdForUpdate`.
- `B/dto/TransactionUpdateRequest.java` -- mesmo corpo para o create (novo record `TransactionCreateRequest` com `categoryId` `@NotNull`).
- `B/controller/TransactionController.java` -- `POST` → 201 com `TransactionResponse`.
- `B/service/DuplicateDetector.java:77` -- já trata `importId` null (regra 2 por descrição similar). Nada a mudar.
- `F/views/TransactionsView.vue` -- Dialog de edição (`:141-271`), `save()`, `returnFocus`, `setSuccess`, `todaySaoPaulo`: o mesmo Dialog serve ao modo criar (título "Adicionar transação", tipo padrão DEBIT, categoria obrigatória, destino habilitado).
- `F/services/transactionService.ts`, `F/types/transaction.ts` -- `createTransaction`.
- **Não mexer:** importação, revisão, `ClassificationPatternService`.

## Tasks & Acceptance

**Execution:**
- [x] `B/dto/TransactionCreateRequest.java` -- record com validações do update + categoria obrigatória.
- [x] `B/service/TransactionService.java` -- `create`: validar, advisory lock do destino, cartão/conta ativos, categoria filha, D3, salvar, auditar `transaction_created`, devolver via `toResponses`.
- [x] `B/controller/TransactionController.java` -- `POST /api/v1/transactions` → 201.
- [x] `F/types`, `F/services/transactionService.ts`, `F/views/TransactionsView.vue` -- botão no topo, Dialog em modo criar, validação local, 400/404 mostram `detail`, "Transação adicionada", lista recarrega (no mês da data criada).
- [x] Testes: `TransactionServiceTest` (matriz inteira, isolamento, nenhum padrão aprendido), `TransactionControllerTest` (201, 400 de validação), `TransactionsView.spec.ts` (criar com sucesso, validação local, falha 400/404).

**Acceptance Criteria:**
- Given estou em `/transacoes`, when adiciono "Feira" 50,00 débito em Alimentação na conta Carteira com data de hoje, then a linha aparece na lista do mês, o saldo da conta cai 50 e vejo "Transação adicionada".
- Given um formulário sem categoria ou com valor 0, when tento salvar, then vejo o erro do campo e nada é enviado.

## Spec Change Log

## Review Triage Log

| # | Camada | Achado | Veredito | Rota | Evidência |
|---|---|---|---|---|---|
| 1 | blind | Editar a categoria de uma linha manual chama `learn` com o texto do usuário | low | rejeitado | Regra da edição (5.12); o padrão só casa se o extrato tiver o mesmo texto normalizado, e aí aplicar a categoria do usuário é o desejado. |
| 2 | blind, edge | `create` sem validar `type`/`date` dá 500 fora do controller; checagens repetidas | false | rejeitado | O único chamador é o controller com `@Valid`; repetição igual ao `update` (5.12 #33). |
| 3 | blind | Sem teste de crédito em conta | low | rejeitado | `signed()` é compartilhado e o débito → crédito já é testado no `update`. |
| 4 | blind | Sem teste de data futura em conta | low | rejeitado | Mesma regra D3 da importação e da edição; o aviso no formulário já é testado na 5.12. |
| 5 | blind, gap | Foco devolvido ao "Adicionar transação" sem teste | low | defer | `Button` renderiza o `<button>` como raiz (`$el`), então o comportamento está certo; nenhum teste de foco existe na view, nem na edição. |
| 6 | blind, edge | Mensagem de sucesso lê `creating` depois do `await load()` | low | patch | `openEdit`/`openConfirm` zeram `creating` durante o recarregamento. Corrigido com `isCreate`. |
| 7 | blind | Sem teste de duplicata entre manual e importação posterior | low | rejeitado | `DuplicateDetector` não mudou e já trata `importId` null (regra 2). |
| 8 | blind | Controller sem teste de 404/400 vindos do serviço | low | rejeitado | O mapeamento é do `GlobalExceptionHandler`, já coberto pelos testes do PUT. |
| 9 | blind | Ordem de validação dá 400 ou 404 conforme conta ou cartão | low | rejeitado | Só com dois erros no mesmo pedido; ambas as respostas são corretas. |
| 10 | blind | Javadoc da classe sugere que a regra de 404 de linhas vale para criar | low | patch | Texto reescrito. |
| 11 | edge | Falha em `loadOptions` deixa selects vazios e mostra a dica da Carteira | low | rejeitado | carried da 5.12 #5: só com falha de rede nas três listas; a correção é um estado novo. |
| 12 | gap | Normalização da descrição no POST sem teste | gap | patch | Teste do 201 agora envia `"  Feira   livre "` e confere `"Feira livre"`. |

## Verification

**Commands:**
- `cd porquinho-backend && ./mvnw test` -- verde.
- `cd porquinho-frontend && npm run lint && npx vue-tsc --build && npm run test:unit` -- verde.
