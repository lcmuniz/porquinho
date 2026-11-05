---
title: 'Ler as transações do OFX importado'
type: 'feature'
created: '2026-11-04'
status: 'done'
baseline_commit: '606cdfd14a6afc94ef2f43fad90600d97dd9592a'
route: 'dispatch'
review_loop_iteration: 0
story_key: 5-4-parse-transactions-with-robust-error-handling
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-5-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Depois da 5.3 a importação para em "Banco detectado": nenhuma transação do extrato é lida nem gravada, e sem a tabela `transactions` nada do resto do épico (duplicatas, Pix, parcelas, IA, revisão) tem onde trabalhar.

**Approach:** Criar `transactions` (V26) e estender o processamento em segundo plano da 5.3: depois de detectar o banco, ler cada `<STMTTRN>` do OFX, pular e contar as linhas com problema, gravar as válidas como `PENDING` numa única transação junto com os contadores da importação, e mostrar o resultado na lista de `/importar`.

## Boundaries & Constraints

**Always:**
- Só OFX (SGML com ou sem fechamento de folhas, XML; cp1252/UTF-8 como na 5.3). Data de `DTPOSTED` pelos 8 primeiros dígitos (AAAAMMDD, fuso ignorado). Valor de `TRNAMT` com ponto ou vírgula decimal; o sinal decide o tipo: negativo `DEBIT`, positivo `CREDIT`, `amount` em valor absoluto (vale para conta e cartão). `TRNTYPE` é ignorado.
- Descrição de `MEMO`, senão `NAME`, senão "Sem descrição"; `original_description` guarda o texto como veio (aparado) e `description` o mesmo com espaços repetidos colapsados. `external_id` = `FITID` (opcional).
- Linha pulada (sem data válida, sem valor, valor inválido ou zero): conta em `skipped_rows`, log só com id da importação, posição da linha e motivo — nunca descrição, valor ou conteúdo.
- Gravação tudo-ou-nada: transações + `total_transactions` + `skipped_rows` numa transação de banco. Erro inesperado → nada gravado, `FAILED` com a mensagem genérica da 5.3.
- Nenhuma transação válida no arquivo → `FAILED`, "Não encontramos transações neste arquivo. Exporte o extrato de novo e envie outra vez."
- Transação nova: `review_status = PENDING`, `kind = REGULAR`, `classification_source = NONE`, `affects_balance = false`, flags de Pix/parcela `false`, `import_id`, `user_id` e o destino (conta ou cartão) da importação. Nada aparece em saldo ou lista (D3 é aplicada na 5.10).
- `transactions` com todos os campos do AC do épico, CHECK de exatamente um destino (D1), `amount > 0`, CHECKs dos enums, índices por `user_id`, `import_id` e destino+data; FKs sem `ON DELETE` (como V25).
- LGPD: `transactions` no export e no purge, apagada antes de `transaction_imports`, `accounts`, `credit_cards` e `categories`.
- Parsing de 1 ano (≈3 000 lançamentos) em < 30 s (NFR6).
- **Decisão 1A (Luiz, 2026-11-04):** gravadas as transações, a importação vai para `REVIEW` (só a partir de `PROCESSING`). A lista mostra "Pronta para revisão: X transações (Y linhas com erro) — a tela de revisão chega numa próxima etapa" e a consulta de 2 s para. `PROCESSING` volta a significar processamento ativo: a varredura de órfãs (subida e 15 min) pega toda `PROCESSING` antiga, com ou sem `bank_name`. As 5.5–5.8 entram antes do `REVIEW`.
- **Decisão 2A (Luiz, 2026-11-04):** sem progresso real nesta story. Enquanto `PROCESSING` com banco, a lista mostra "Banco detectado: <banco> — lendo as transações..." e segue consultando; contagens só no fim. A coluna de progresso ("X de Y", NFR8) entra na 5.8.
- **Decisão 3A (Luiz, 2026-11-04):** `kind = CARD_PAYMENT` marcado na leitura por palavra-chave na descrição (sem acento, maiúsculas): "PAGAMENTO DA FATURA", "PAGAMENTO EFETUADO", "PAGAMENTO RECEBIDO", "ITAU UNIBANCO CARTAO", "GASTOS CARTAO DE CREDITO". Na conta só vale para `DEBIT`; no cartão só para `CREDIT`. O resto é `REGULAR`.

**Never:**
- Duplicatas, Pix, parcelas, IA, tela de revisão, saldo (5.5–5.10). CSV (5.2). Chamar serviço externo.
- Editar migration aplicada (V25 inclusa).

## I/O & Edge-Case Matrix

| Cenário | Entrada / Estado | Resultado | Erro |
|---|---|---|---|
| Conta de cada banco | as 5 amostras de conta | todas as `STMTTRN` gravadas, `total_transactions` = nº de blocos, `skipped_rows` 0 | — |
| Cartão | `itau-cartao.ofx` | compras `DEBIT`, pagamento `CREDIT`, `credit_card_id` preenchido | — |
| Acentos cp1252 | `nubank-conta.ofx` | descrição com "ê", "•" corretos | — |
| Linhas com problema | fixture nova com data inválida, sem `TRNAMT`, `TRNAMT` "abc" e "0.00" | válidas gravadas, `skipped_rows` 4 | log sem conteúdo |
| Sem lançamentos | OFX completo sem `STMTTRN` | `FAILED` + mensagem de "não encontramos transações" | — |
| Falha no meio da gravação | exceção ao salvar | nenhuma transação gravada, `FAILED` genérico | log só com tipo da exceção |
| Ano inteiro | OFX gerado com 3 000 lançamentos | concluído em < 30 s | — |
| Pagamento de fatura | débitos de fatura em `nubank-conta`, `itau-conta`, `bradesco-conta` e o crédito "PAGAMENTO EFETUADO" de `itau-cartao` | `kind = CARD_PAYMENT`; demais `REGULAR` | — |
| Concluída | qualquer amostra válida | status `REVIEW`, consulta para | — |
| Órfã detectada | `PROCESSING` com `bank_name`, criada antes da subida ou há > 15 min | `FAILED` "interrompido" | — |
| Purge/export | usuário com transações | exportadas; purge apaga antes de imports/contas/cartões | — |

</frozen-after-approval>

## Code Map

- `db/migration/V26__create_transactions_table.sql` (novo) -- seguir V25: `id UUID DEFAULT gen_random_uuid() PRIMARY KEY` (H2), CHECKs nomeados, `COMMENT ON` em pt-BR, sem índice parcial (H2), `pix_details JSONB`.
- `entity/TransactionImport.java` / `repository/TransactionImportRepository.java` -- contadores `total_transactions`, `skipped_rows` já existem (V25, `int`). `failProcessingCreatedBefore` (l.72) filtra `bankName IS NULL`: remover o filtro (1A) e ajustar os testes da 5.3 que fixam o contrário (`TransactionImportIntegrationTest` "detected ones stay"). `recordBank`/`markFailed` só mudam linha `PROCESSING` — manter esse guarda em toda atualização nova.
- `service/TransactionImportProcessor.java` `process` (l.61-80) -- depois de `recordBank`, chamar o novo parser e gravar; mesmo `catch` genérico. Reusar `OfxBankDetector.tagValue`/`decode` (tornar acessíveis no pacote) para não duplicar a leitura dos bytes.
- `service/OfxTransactionParser.java` (novo) -- regra pura: bytes → lista de lançamentos + nº de pulados; testável sem Spring.
- `entity/Transaction.java`, `repository/TransactionRepository.java` (novos) -- `@Enumerated(STRING)`, `BigDecimal` `precision 15, scale 2` (como `Transfer.java` l.38); `deleteByUserId` nativo; consulta ordenada por usuário para o export. Não há classe `Transaction` existente (só `BillingTransaction`).
- `service/AccountDeletionService.java` l.351-369 -- `transactionRepository.deleteByUserId` antes da l.361; atualizar javadoc l.59 e l.312.
- `service/DataExportService.java` -- campo/construtor (l.80/110/128), `exported_tables` (l.208), bloco após `transaction_imports` (l.373-397), mesmo estilo `LinkedHashMap`.
- `dto/TransactionImportResponse.java` -- expor `totalTransactions`, `skippedRows`.
- `porquinho-frontend/src/views/ImportView.vue` (`isPending` l.335, rótulos l.196-210), `types/transactionImport.ts` -- novos rótulos e quando parar a consulta.
- `src/test/resources/import-samples/` -- criar `erro-ofx-linhas-invalidas.ofx` (cp1252, SGML) e registrar no `README.md`; o OFX de 1 ano é gerado no teste.

## Tasks & Acceptance

**Execution:**
- [x] `V26__create_transactions_table.sql`, `Transaction`, `TransactionRepository` -- tabela e acesso
- [x] `OfxTransactionParser` -- leitura das `STMTTRN` com as regras de Always
- [x] `TransactionImportProcessor` / `TransactionImportRepository` -- gravar tudo-ou-nada, contadores, status `REVIEW`, `CARD_PAYMENT` e varredura sem filtro de banco
- [x] `AccountDeletionService`, `DataExportService` -- LGPD
- [x] `TransactionImportResponse`, frontend -- contagens e rótulos
- [x] Épico e `epic-5-context.md` -- registrar as decisões 1A–3A; trocar no épico "Progress is updated every 2 seconds" da 5.4 para a 5.8
- [x] Testes -- parser (amostras, matriz, desempenho), processador, integração Testcontainers (upload → transações no banco, falha não grava nada, purge/export), `MigrationTest`, `ImportView.spec.ts`

**Acceptance Criteria:**
- Given `nubank-conta.ofx` enviado para a conta Nubank, when o processamento termina, then `transactions` tem os 10 lançamentos `PENDING` dessa conta e a lista de `/importar` mostra a contagem sem recarregar a página.
- Given o usuário pede o purge, when ele roda, then nenhuma linha de `transactions` do usuário sobra e as FKs não bloqueiam.

## Implementation Notes

## Spec Change Log

## Review Triage Log

| # | Origem | Achado | Veredito | Evidência | Destino |
|---|---|---|---|---|---|
| 1 | edge, blind | `TRNAMT` em notação científica ("1E200000000") trava a thread; mais de 2 casas ("1.234") arredonda em silêncio; U+00A0 invalida valor bom | high | Reproduzido: `setScale` em `1E200000000` não termina em 15 s; `parseAmount` aceitava qualquer `BigDecimal` | patch: só sinal/dígitos/separadores, ≤ 2 casas e ≤ 13 dígitos inteiros antes do `setScale` |
| 2 | blind | `imported_transactions` fica 0 em importação `REVIEW`; tela calcula `total − skipped` | low | `markReadyForReview` só grava `total`/`skipped`; API e export mostram 0 | patch: gravar `imported_transactions` e a tela usar o campo |
| 3 | verification-gap | Corte de MEMO 500 / FITID 255 sem teste | medium | Pré-verificado pela camada | patch: teste no parser |
| 4 | verification-gap | Guarda `status = PROCESSING` de `markReadyForReview` só testada com mock | medium | Pré-verificado pela camada | patch: teste de integração com importação `FAILED` |
| 5 | blind | `epic-5-context.md` recompilado perdeu regras (migration imutável, 402, claude-api, F3 do export) | medium | `git show 606cdfd:` mostra as regras; a versão nova não as tem — a 5.8 perderia a orientação da `claude-api` | patch: seção "Regras do projeto" no contexto |
| 6 | blind | AC do épico pede tentar formatos de data do banco; parser só lê AAAAMMDD | low | Spec congelada fixa AAAAMMDD; todas as amostras OFX usam esse formato | patch: nota no épico (formatos alternativos são do CSV/5.2) |
| 7 | blind | Varredura por `created_at` sem heartbeat derruba processamento longo | medium (futuro) | Hoje a leitura de 1 ano leva < 2 s; vira real com a IA (5.8) | defer (`deferred-work.md` + contexto do épico) |
| 8 | edge | `Error` (OOM, StackOverflow) escapa do `catch (Exception)` | low | Com o patch 1 não há caminho conhecido para OOM; varredura cobre em 15 min | rejeitado |
| 9 | edge | OFX com vários `STMTRS` grava lançamentos de outras contas no destino | low | Bancos exportam uma conta por arquivo; nenhuma amostra tem mais de uma | rejeitado |
| 10 | edge, blind | Destino removido entre upload e gravação deixa transações presas a ele | low | Janela de milissegundos; correção acrescenta consulta e ramo | rejeitado |
| 11 | edge | Purge concorrente com processamento falha por FK | low | Exige purge no mesmo segundo do upload; o purge é reexecutado | rejeitado |
| 12 | edge, blind | Entidades numéricas XML (`&#231;`) não decodificadas | low | Bancos brasileiros exportam SGML cp1252; correção acrescenta regex | rejeitado |
| 13 | edge | Corte em 500 pode partir um par surrogate | low | Exige emoji exatamente na posição 500 | rejeitado |
| 14 | blind | Varredura de subida com várias instâncias | low | Carregado da 5.3 (#4): instância única | rejeitado |
| 15 | blind | Todas as linhas inválidas → mensagem "não encontramos transações" | low | Mensagem fixada na spec congelada; mudá-la é editar a spec | rejeitado |
| 16 | blind | `saveAll` sem batch JDBC; teste de NFR6 frágil | false | 3 000 lançamentos de ponta a ponta em ~1,7 s; folga de > 15× | rejeitado |
| 17 | blind | Log por linha pulada sem limite | low | Spec exige posição por linha; arquivo com milhares de linhas ruins é improvável | rejeitado |
| 18 | blind | Spec `in-review` × sprint `in-progress`; Javadoc "5 min" × "15 min" | false | Fluxo normal do step-05; 5 min é a frequência e 15 min a idade, ambos corretos | rejeitado |
| 19 | blind | Export grava `pix_details` como string | low | Coluna sempre nula até a 5.6, que deve exportá-la como JSON | rejeitado (nota para a 5.6) |

## Verification

**Commands:**
- `cd porquinho-backend && ./mvnw test` -- 0 falhas (com `JAVA_HOME` do JDK 21)
- `cd porquinho-frontend && npx vitest run && npx vue-tsc --build && npm run lint` -- tudo verde

**Manual checks:**
- Backend e frontend locais: enviar `nubank-conta.ofx` e `itau-cartao.ofx`; ver as contagens na lista e as linhas em `transactions` no banco de dev.
