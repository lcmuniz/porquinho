---
title: 'Story 5.5 — Detectar e ignorar transações duplicadas'
type: 'feature'
created: '2026-11-05'
status: 'done'
baseline_commit: '3370abe13aca59ff10d5b96d3aa705c02e4aa606'
route: 'dispatch'
review_loop_iteration: 1
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-5-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** hoje toda importação grava todos os lançamentos lidos. Importar o mesmo extrato duas vezes, ou o OFX e o CSV do mesmo mês, duplica as transações. Um lançamento que já foi registrado como transferência entre contas próprias (Story 4.5) também vira transação.

**Approach:** antes da gravação tudo-ou-nada, `TransactionImportProcessor` compara cada lançamento lido com as transações já gravadas no mesmo destino e com as transferências da conta. As duplicatas e as transferências já registradas não são gravadas. As duas contagens ficam na importação, aparecem na tela e vão para a auditoria.

## Boundaries & Constraints

**Always:**
- **Escopo:** a comparação é feita só com transações do mesmo destino (`account_id` ou `credit_card_id`) e do mesmo usuário, com `deleted_at IS NULL`, em qualquer `review_status`.
- **Pareamento um-para-um:** cada transação existente e cada transferência "absorvem" no máximo um lançamento por importação. Os lançamentos do próprio arquivo não são comparados entre si.
- **Ordem:**
  1. **Id exato:** mesmo `external_id`, não nulo.
  2. **Semelhança**, só com o que sobrou do passo 1:
     - o mesmo `transaction_type`;
     - o mesmo `amount`;
     - data com diferença de até 1 dia;
     - descrição semelhante (ver Design Notes).

     Vale quando um dos `external_id` falta, ou quando os dois são diferentes **e** a transação existente veio de uma importação de outro formato (`file_type` OFX × CSV — decisão 4A). Os bancos usam ids diferentes no OFX e no CSV, e o AC exige que a importação dos dois formatos do mesmo mês dê só duplicatas; no mesmo formato, id diferente é transação diferente.
  3. **Transferência (D4):** só quando o destino é conta. Precisa de transferência ativa do usuário, mesmo valor e data com diferença de até 1 dia. A conta da importação é `from_account_id` com lançamento `DEBIT`, ou `to_account_id` com lançamento `CREDIT`.
- **Corrida entre importações:** a consulta às transações existentes e a gravação ficam na mesma transação, depois de um `pg_advisory_xact_lock` pelo id do destino. Assim, duas importações para o mesmo destino não deixam de se ver.
- **Contadores e coluna nova:**
  - `total_transactions` = linhas lidas.
  - `imported_transactions` = lançamentos gravados.
  - `duplicates_found` = duplicatas.
  - `transfers_found` = transferências já registradas. Coluna nova, criada na V27.
  - A coluna nova entra no DTO, no tipo do front e no export LGPD.
- **Auditoria:** o evento `import_duplicates_checked` é gravado com IP nulo. O metadata leva só `import_id` e as contagens.
- **NFR6:** o arquivo inteiro deve ser processado em menos de 30 s. Uma única consulta busca as transações do destino na janela entre (menor data do arquivo − 1) e (maior data do arquivo + 1). Uma única consulta busca as transferências.

**Never:**
- Alterar migration já aplicada.
- Apagar ou alterar transações existentes.
- Ler `docs/` ou usar o extrato real.
- Gravar descrições no log ou na auditoria.
- Comparar duplicatas entre destinos diferentes.

## I/O & Edge-Case Matrix

| Cenário | Estado / entrada | Resultado |
|---|---|---|
| Mesmo arquivo duas vezes | 2ª importação de `nubank-conta.ofx` na mesma conta | 0 gravadas, todas em `duplicates_found` |
| OFX + CSV do mesmo mês | um formato e depois o outro, nos 5 bancos (BB incluído) | 2ª importação: todos os lançamentos são duplicatas |
| Dois lançamentos iguais no arquivo | 2 cafés de R$ 5,00 no mesmo dia, nada no banco | os 2 são gravados |
| Reimportação parcial | o banco tem 1 café; o arquivo traz 2 iguais | 1 duplicata e 1 gravada |
| Transferência registrada | conta BB, transferência BB→Nubank de R$ 2.000 em 01/10, lançamento DEBIT de 2000 em 02/10 | não é gravado; `transfers_found` = 1 |
| Sentido errado | mesma transferência, lançamento CREDIT na BB | é gravado normalmente |
| Cartão | importação para cartão | não verifica transferências |
| Transação excluída | a igual tem `deleted_at` | o lançamento é gravado |
| Virada de mês, mesmo formato | OFX de outubro gravado com "UBER" R$ 23,70 em 31/10; OFX de novembro traz outra "UBER" R$ 23,70 em 01/11 com outro id | é gravada (não é duplicata) |

## Decisões

- **1A — nada novo:** se todos os lançamentos forem duplicatas ou transferências (0 gravados), a importação vai para `COMPLETED` (mesma guarda `PROCESSING`), com o rótulo "Nenhuma transação nova — X duplicatas detectadas e ignoradas" (e "· N transferências já registradas" quando N > 0).
- **2A — rótulo de `REVIEW`:** uma linha só, com sufixos que aparecem quando a contagem passa de 0: "Pronta para revisão: 7 transações (1 linha com erro) · 3 duplicatas detectadas e ignoradas · 1 transferência já registrada — …". A forma singular segue o `plural()` que já existe.
- **Tamanho:** a spec fica inteira, sem dividir.
- **4A — ids diferentes:** a semelhança com ids diferentes só vale entre formatos diferentes (a existente veio de OFX e o arquivo é CSV, ou o contrário). Transação existente sem importação (manual, futura 5.13) só passa pela semelhança quando falta id de um dos lados.

</frozen-after-approval>

## Code Map

- `porquinho-backend/src/main/java/com/porquinho/service/TransactionImportProcessor.java` -- `parseAndSave` (cerca da linha 120): o filtro entra dentro do `transactionTemplate.execute`, depois do lock e antes de `markReadyForReview` e `saveAll`. `toTransaction` é reutilizado.
- `.../repository/TransactionImportRepository.java:60-71` -- `markReadyForReview`: acrescentar os parâmetros `duplicates` e `transfers`. Para a decisão 1A, criar uma variante para `COMPLETED` com a mesma guarda `PROCESSING`.
- `.../repository/TransactionRepository.java` -- nova consulta de ativas por destino, usuário e janela de datas.
- `.../repository/TransferRepository.java` -- nova consulta das ativas da conta (`from` ou `to`) na janela de datas.
- `.../entity/Transfer.java` -- `fromAccountId`, `toAccountId`, `amount`, `transferDate`, `deletedAt`.
- `.../entity/TransactionImport.java:60` -- `duplicatesFound` já existe. Acrescentar `transfersFound`.
- `.../dto/TransactionImportResponse.java:24,42` -- acrescentar `transfersFound`.
- `.../service/DataExportService.java:395` -- chave `transfers_found` (e no teste das chaves).
- `.../service/AuditLogService.java:47` -- `log(eventType, userId, ip, metadata)`.
- `porquinho-backend/src/main/resources/db/migration/V27__add_transfers_found_to_transaction_imports.sql` -- novo: `INT NOT NULL DEFAULT 0` com CHECK `>= 0`.
- `porquinho-frontend/src/types/transactionImport.ts:25` e `src/views/ImportView.vue:~340` -- campo novo e rótulo.
- `porquinho-backend/src/test/resources/import-samples/` -- amostras OFX/CSV do mesmo mês (README, linhas 23-24). Usar só essas, sem criar fixture com dado real.

## Tasks & Acceptance

**Execution:**
- [x] `V27__...sql`, entidade, DTO, export e `MigrationTest` -- coluna `transfers_found`.
- [x] Novo `service/DuplicateDetector.java` (puro, sem Spring) -- recebe os lançamentos, as transações existentes, as transferências e a conta, e devolve `Result(toSave, duplicates, transfers)`. Também guarda a normalização e a semelhança.
- [x] `TransactionImportProcessor` e os repositórios -- lock, consultas, filtro, contadores e auditoria.
- [x] Testes: unitários do `DuplicateDetector` cobrindo a matriz e o par BB truncado; integração (Testcontainers) com OFX+CSV dos 5 bancos dando só duplicatas, mais o caso da transferência BB→Nubank.
- [x] Frontend: tipo, rótulo e teste do `ImportView`.
- [x] Rótulo `COMPLETED` (decisão 1A): a parte das duplicatas só aparece quando passa de 0, e as linhas com erro aparecem como no rótulo de `REVIEW` ("(N linhas com erro)"); testes no `ImportView.spec.ts` para só transferências e com linhas com erro.
- [x] Consultas de candidatos (transações e transferências) com `ORDER BY` fixo (data, `created_at`, `id`), para o empate ser determinístico.
- [x] Testes extras: integração de duplicata em cartão (`itau-cartao.ofx` duas vezes no mesmo cartão → 2ª `COMPLETED` com todas duplicatas; outro cartão → 0); transferência com `deleted_at` não absorve o lançamento; guarda de `markCompletedWithNothingNew` retornando 0 (nada gravado nem auditado).
- [x] Épico, Story 5.5: atualizar o AC do `external_id`, que agora é exato e, quando o id falta ou os ids são diferentes entre formatos (OFX × CSV), recorre à semelhança (regra P1).

**Acceptance Criteria:**
- Given um arquivo com duplicatas, when ele é processado, then nenhuma duplicata é gravada e os contadores somam: imported + duplicates + transfers = número de lançamentos lidos.
- Given que a importação saiu de `PROCESSING` durante o processamento, when chega a gravação, then nada é gravado e nenhum contador muda.
- Given um processamento concluído, when a auditoria é gravada, then o metadata contém só ids e contagens.

## Design Notes

**Normalização:** minúsculas, sem acento (NFD), cada caractere que não é letra nem dígito vira espaço, espaços seguidos viram um só, `trim`.

**Semelhante** quando vale qualquer uma destas:
- similaridade de Levenshtein de pelo menos 0,90 (`1 − dist / max(len)`);
- o texto menor tem 10 caracteres ou mais e é prefixo do maior.

O OFX do BB corta o MEMO em cerca de 32 caracteres. Só com Levenshtein, o par "pix enviado 01 10 09 15 joana" e "pix enviado 01 10 09 15 joana pereira lima" dá entre 0,60 e 0,79, e as duplicatas do BB passariam. O risco de um falso positivo é baixo, porque o valor, o tipo e a data (±1 dia) também precisam coincidir. Usar a descrição original (`original_description`).

## Verification

**Commands:**
- `cd porquinho-backend && JAVA_HOME=~/.sdkman/candidates/java/21.0.11-tem ./mvnw test` -- expected: tudo verde (eram 967).
- `cd porquinho-frontend && npm run lint && npx vue-tsc --build && npx vitest run` -- expected: tudo verde.

## Implementation Notes

- Repositórios: `TransactionRepository.findActiveByAccountBetween` / `findActiveByCreditCardBetween`, `TransferRepository.findActiveByAccountBetween`, `TransactionImportRepository.markReadyForReview` (com `duplicates`/`transfers`), `markCompletedWithNothingNew` (decisão 1A) e `lockTarget` (`pg_advisory_xact_lock`, chave = bits altos XOR baixos do UUID do destino; colisão só serializa duas importações sem relação).
- Lock, consultas, update com guarda `PROCESSING`, `saveAll` e auditoria ficam no mesmo `transactionTemplate.execute`. Com a guarda falhando, nada é gravado nem auditado.
- No passo 2 (semelhança), entre os candidatos válidos fica o de menor distância em dias. No passo 3 também.
- O teste de integração da 5.2 (`uploadedCsvGetsItsBankAndTheSameTransactionsAsTheOfx`) passou a usar duas contas, pois na mesma conta o segundo arquivo agora vira só duplicatas.
- Decisão 4A: `DuplicateDetector.detect(entries, fileType, existing, importFileTypes, transfers, accountId)`. O formato de origem das transações existentes vem de `TransactionImportRepository.findByUserIdAndIdIn` (uma consulta só, e só quando há transação de importação na janela). Transação sem importação, ou com importação fora do mapa, conta como "sem formato".
- Verificação da 2ª implementação: backend 1016 testes verdes; frontend lint (0 erros, 15 avisos que já existiam), `vue-tsc --build` e vitest 626 verdes.
- (1ª implementação, revertida na revisão 1 — ver Spec Change Log.) Verificação: backend 1008 testes verdes (eram 967); frontend lint (0 erros, 15 avisos que já existiam), `vue-tsc --build` e vitest 625 verdes.

## Spec Change Log

- **Revisão 1 (2026-11-05) — intent_gap resolvido (4A).** Achado: a semelhança com ids diferentes descartava compras reais na virada de mês do mesmo formato (ex.: "UBER" R$ 23,70 em 31/10 e 01/11). Emendado: passo 2 só com ids diferentes entre formatos diferentes (bloco congelado, decisão 4A, nova linha da matriz); tarefas dos patches da revisão 1 (rótulo `COMPLETED`, `ORDER BY`, testes de cartão, transferência excluída e guarda do `COMPLETED`). Estado ruim evitado: perda silenciosa de lançamentos legítimos. Código revertido para ser refeito. **KEEP:** a estrutura da 1ª implementação funcionou e deve ser mantida — `DuplicateDetector` puro com `Result(toSave, duplicates, transfers)`, passos em ordem com `Candidate.used`, escolha do candidato de menor distância em dias, atalho de comprimento antes do Levenshtein; V27 com CHECK e COMMENT; `markReadyForReview` com `duplicates`/`transfers`, `markCompletedWithNothingNew`, `lockTarget` via `SELECT 1 FROM (SELECT pg_advisory_xact_lock(:key))` com chave XOR dos bits do UUID; lock + consultas + update com guarda + `saveAll` + auditoria no mesmo `transactionTemplate.execute`; metadata da auditoria só com `import_id` e contagens; o teste da 5.2 `uploadedCsvGetsItsBankAndTheSameTransactionsAsTheOfx` com duas contas; 25 testes unitários do detector e 6 de integração (mesmo arquivo 2×, OFX+CSV dos 5 bancos, transferência BB→Nubank, excluída/outra conta, simultâneas, export e CHECK). A 1ª implementação inteira está em `/tmp/s51/diff-5-5-impl1.patch` como referência.

## Review Triage Log

### Revisão 1 (2026-11-05)

| # | Origem | Achado | Veredito | Evidência / rota |
|---|---|---|---|---|
| 1 | blind | Semelhança com ids diferentes do mesmo formato descarta compra real (ex.: "UBER" R$ 23,70 em 31/10 no extrato de outubro e outra em 01/11 no de novembro) | high | A janela (mín−1) traz a linha de 31/10, que não é absorvida no passo 1; passo 2 casa por valor, tipo, ±1 dia e descrição. Regra está no bloco congelado → **intent_gap** |
| 2 | vgap | Sem teste de integração de duplicata em cartão | medium | Pré-verificado; só mock confere `findActiveByCreditCardBetween` → patch |
| 3 | vgap | Sem teste de transferência excluída no filtro D4 | medium | Pré-verificado → patch |
| 4 | vgap | Teste de concorrência não força sobreposição | medium | Pré-verificado; exige sincronização entre threads → defer |
| 5 | vgap, blind, edge | Rótulo COMPLETED mostra "0 duplicatas" quando só há transferências | low | `ImportView.vue:362-368` monta a parte das duplicatas sem condição → patch |
| 6 | blind, edge | Rótulo COMPLETED omite linhas com erro | low | Ramo COMPLETED não usa `skippedRows` → patch |
| 7 | blind | Sem teste da guarda em `markCompletedWithNothingNew` | low | Só o caminho `markReadyForReview` = 0 é testado → patch (teste) |
| 8 | edge | Consultas de candidatos sem ORDER BY: escolha não determinística em empate | low | Correção direta (ORDER BY) → patch |
| 9 | blind | Mesmo `external_id` fora da janela de datas não é visto | low | Só ocorre com data editada (Story 5.12 ainda não existe); correção exige nova consulta → rejeitado |
| 10 | edge | Mesmo caso do 9 (data editada) | low | Idem → rejeitado |
| 11 | blind | Passo 1 não confere valor/tipo; banco que reutiliza FITID | maybe-false | Precisaria de extrato real com FITID repetido na mesma janela; se real, medium → defer |
| 12 | blind | Transferência ignora descrição (aluguel de mesmo valor no dia) | low | Critérios definidos pela D4; caso raro e correção acrescenta heurística → rejeitado |
| 13 | blind | Nada registra quais lançamentos foram descartados | false | A intenção diz "não são gravados" e auditoria só com contagens |
| 14 | blind, edge | Pareamento guloso pode tirar o candidato de um lançamento posterior | low | Exige dois lançamentos semelhantes de mesmo valor em dias vizinhos com candidatos cruzados; correção é matching global → rejeitado |
| 15 | blind, edge | Descrições vazias sempre "semelhantes" | low | Exige dois lançamentos sem descrição, mesmo valor, ±1 dia; correção acrescenta ramo → rejeitado |
| 16 | blind | Sem teste de integração transferência + reimportação | low | Lógica coberta no unitário; regressão improvável → rejeitado |
| 17 | blind | Status da spec e do sprint divergem; triage log vazio | false | O sprint vai para review no step 5; o log é preenchido nesta triagem |
| 18 | blind | Transações de importação DISCARDED viram candidatas | maybe-false | O fluxo de descarte ainda não existe (5.9); se descartar não apagar as transações, medium → defer |
| 19 | edge | Lock não cobre transação manual nem transferência criada durante a importação | low | Transação manual não existe (5.13); transferência simultânea é rara → defer para 5.13 |

### Revisão 2 (2026-11-05)

| # | Origem | Achado | Veredito | Evidência / rota |
|---|---|---|---|---|
| 20 | blind | `lockTarget` não exige transação ativa; chamado fora dela, o lock é liberado na hora | low | Uso futuro (5.13, já no deferred-work) ficaria sem serialização em silêncio; correção direta (`MANDATORY`) → patch |
| 21 | blind | Espera do advisory lock sem limite | low | Seção travada dura segundos; esgotar pool exige muitas importações simultâneas no mesmo destino → rejeitado |
| 22 | blind | Chave do lock sem namespace | low | Colisão com o lock do Flyway (só na subida) é improvável; correção muda a chamada → rejeitado |
| 23 | blind | Nenhum teste fixa a ordem lock → consultas → update | low | Só `verify(lockTarget)`; `InOrder` barato → patch |
| 24 | blind | Auditoria sem `skipped_rows` | low | A soma não fecha com `total_transactions` quando há linhas puladas; acrescentar a contagem → patch |
| 25 | blind | Javadoc da classe ambíguo (FAILED parece alternativa ao COMPLETED) | low | Correção de texto → patch |
| 26 | blind | Spec aponta para `/tmp` | low | Correção seria editar a spec (Change Log é append-only) → rejeitado |
| 27 | blind, edge | Regra de prefixo casa rótulos genéricos ("pix enviado" ⊂ "pix enviado joao") | low | Só vale entre formatos diferentes ou sem id (4A) e exige banco cujo formato traga memo genérico, mesmo valor e ±1 dia; nenhuma amostra tem esse padrão; mudar o mínimo contraria a Design Note → rejeitado |
| 28 | blind | `findByUserIdAndIdIn` hidrata entidades inteiras | low | Poucas importações por janela; ganho desprezível → rejeitado |
| 29 | blind | CHECK só em `transfers_found` | low | As demais colunas são anteriores à story → rejeitado |
| 30 | blind | Integração OFX+CSV nas duas ordens só para Nubank e BB | low | Ordem define `otherFormat`; acrescentar linhas ao `@CsvSource` → patch |
| 31 | vgap | Janela de datas não é testada com arquivo fora de ordem crescente | medium | Pré-verificado; parsers não ordenam → patch (teste) |
| 32 | vgap | Regra do candidato de menor distância (passos 2 e 3) sem teste | medium | Pré-verificado → patch (testes) |
| 33 | vgap | Teste de concorrência não força sobreposição | medium | carried (#4) → defer já registrado |
| 34 | edge | Descrições vazias sempre semelhantes | low | carried (#15) → rejeitado |
| 35 | edge | Passo 1 sem conferir valor/tipo (FITID reutilizado) | maybe-false | carried (#11) → defer já registrado |
| 36 | edge | Transferência casada por ordem do arquivo, sem descrição | low | carried (#12/#14) → rejeitado |
| 37 | edge | Semelhança gulosa por ordem do arquivo | low | carried (#14) → rejeitado |
