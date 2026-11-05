---
title: 'Importar extrato CSV dos 5 bancos'
type: 'feature'
created: '2026-11-05'
status: 'done'
baseline_commit: '59db97e7bf8038bafe66f1aa4200ff3ce155c167'
route: 'dispatch'
review_loop_iteration: 0
story_key: 5-2-upload-csv-files-from-5-brazilian-banks
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-5-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** A importação só aceita OFX. Muitos usuários só conseguem (ou preferem) baixar o extrato em CSV, e cada banco usa um layout próprio.

**Approach:** Aceitar `.csv` no mesmo upload e no mesmo processamento em segundo plano das 5.3/5.4: reconhecer o layout (que já identifica o banco e se o extrato é de conta ou de cartão), ler as linhas com as mesmas regras de linha pulada e gravação tudo-ou-nada, e terminar em `REVIEW` como o OFX.

## Boundaries & Constraints

**Always:**
- Layouts das amostras (`import-samples/README.md`): Nubank conta (`Data,Valor,Identificador,Descrição`), Nubank cartão (`date,title,amount`), BB, Itaú (sem cabeçalho: `DD/MM/AAAA;descrição;valor`), Bradesco, Caixa. Reconhecimento pelo cabeçalho (ignorando aspas, acentos, maiúsculas e linhas de título antes dele); Itaú pelo formato das linhas. Layout reconhecido grava `bank_name` (`NUBANK`, `BANCO_DO_BRASIL`, `ITAU`, `BRADESCO`, `CAIXA`) e `file_type = 'CSV'`.
- Encoding: BOM UTF-8 ou UTF-8 válido → UTF-8; senão cp1252 (cobre ISO-8859-1). Separador: vírgula, ponto e vírgula ou tabulação, com campos entre aspas (aspas duplicadas dentro do campo).
- Datas `DD/MM/AAAA`, `DD/MM/AA` (ano 20AA), `AAAA-MM-DD`, `AAAAMMDD`, conforme o layout. Valores com ponto ou vírgula decimal e separador de milhar; mesmas regras de valor inválido da 5.4 (≤ 2 casas, ≤ 13 dígitos inteiros, sem notação científica).
- Tipo: conta pelo sinal (negativo `DEBIT`); Bradesco pela coluna preenchida (Crédito/Débito); Caixa pela coluna `D`/`C`; Nubank cartão com sinal invertido (positivo = compra `DEBIT`, negativo = pagamento/estorno `CREDIT`).
- Linhas que não são lançamento (BB "Saldo Anterior" e "S A L D O", Bradesco "SALDO ANTERIOR", "Total", título, rodapé e linhas em branco) são ignoradas e **não** contam como puladas. Linhas de lançamento com data ou valor ausente/inválido ou valor zero são puladas e contadas, log só com posição e motivo.
- `external_id`: Nubank `Identificador`; BB `Número do documento`, Bradesco `Docto.`, Caixa `Nr_Doc` quando não vazios e diferentes de "0"; Itaú e Nubank cartão sem.
- Pagamento de fatura (D8/3A da 5.4) vale igual para CSV, e a lista de palavras-chave ganha "PAGAMENTO CARTAO DE CREDITO" (BB "Pagamento Cartão de Crédito - OUROCARD VISA").
- Destino (D2): Nubank cartão só em cartão; os outros layouts só em conta.
- Arquivo, tamanho, auditoria, LGPD, varredura de órfãs, `REVIEW`, contadores e mensagens de "nenhuma transação" e de erro genérico: iguais à 5.4.
- O extrato real do usuário em `docs/` só é usado em teste manual local; nunca entra no repositório nem em fixture.
- **Decisão 1A (Luiz, 2026-11-05):** sem escolha manual do banco. No CSV o layout reconhecido define o banco; no OFX o banco não muda a leitura (`OTHER` continua "Banco não identificado"). O AC "Detectamos [Banco]. Está correto?" sai do épico.
- **Decisão 2A (Luiz, 2026-11-05):** layout e destino do CSV são verificados no envio, como o OFX: lido o cabeçalho, layout desconhecido → 400 "Não reconhecemos o formato deste CSV. Tente exportar o extrato em OFX."; layout de cartão em conta ou de conta em cartão → 400 com as mensagens de destino errado da 5.1. Nada é gravado. Banco gravado e leitura das linhas continuam em segundo plano.

**Never:**
- Layouts além dos 6 das amostras. Duplicatas, Pix, parcelas, IA (5.5–5.8). Nova migration (`file_type` já aceita `CSV`).

## I/O & Edge-Case Matrix

| Cenário | Entrada / Estado | Resultado | Erro |
|---|---|---|---|
| Cada layout | as 6 amostras CSV no destino certo | `bank_name` do layout, `REVIEW`, nº de lançamentos igual ao OFX do mesmo banco (Nubank cartão: 9) | — |
| Linhas de saldo/total | `bb-conta.csv`, `bradesco-conta.csv` | saldo, total, título e rodapé ignorados; `skipped_rows` 0 | — |
| Sinais e colunas | Bradesco crédito/débito, Caixa D/C, Nubank cartão invertido | tipos certos; "Pagamento recebido" e "Estorno" do cartão = `CREDIT` | — |
| Encoding | BB ISO-8859-1, Itaú/Bradesco cp1252, Nubank UTF-8 com e sem BOM | acentos corretos | — |
| Linhas inválidas | `erro-csv-linhas-invalidas.csv` | 2 gravadas, `skipped_rows` 4 | log sem conteúdo |
| Layout desconhecido | `erro-csv-banco-desconhecido.csv` | 400 no envio, nada gravado (2A) | "Não reconhecemos o formato deste CSV. Tente exportar o extrato em OFX." |
| Destino errado | `nubank-cartao.csv` em conta; `nubank-conta.csv` em cartão | 400 no envio, nada gravado (2A) | mensagens de destino errado da 5.1 |
| Vazio | `erro-arquivo-vazio.csv` | 400 no upload (como 5.1) | — |

</frozen-after-approval>

## Code Map

- `service/TransactionImportService.java` -- `create` (l.86-120): extensão `.ofx` (l.97) → aceitar `.csv`; `statementKind` (l.187) só OFX; `record` grava `FileType.OFX` fixo (l.138). Mensagens `UNSUPPORTED_FORMAT_MESSAGE`, `CARD_FILE_TO_ACCOUNT_MESSAGE`, `ACCOUNT_FILE_TO_CARD_MESSAGE` (l.50-55) reaproveitadas.
- `service/TransactionImportProcessor.java` `process` (l.78) -- hoje sempre OFX (`isTruncated`, `detectBank`, `parseAndSave` → `OfxTransactionParser`); ramificar por `file_type`, mantendo o `catch` genérico, a guarda `markReadyForReview` e o tudo-ou-nada.
- `service/OfxTransactionParser.java` -- reusar `Entry`/`Result`/`SkippedRow`/`SkipReason`, `parseAmount`, `isCardPayment` e `CARD_PAYMENT_KEYWORDS` (acrescentar a palavra-chave); não duplicar regras de valor.
- `service/OfxBankDetector.java` `decode` (l.107) -- UTF-8 ou cp1252; acrescentar o BOM.
- `service/CsvStatementReader.java` (novo, nome livre) -- regra pura: bytes → layout (banco + conta/cartão) ou desconhecido; linhas → `Result`.
- `porquinho-frontend/src/views/ImportView.vue` -- `accept=".ofx"` (l.95), validação no cliente (l.268-277), textos "OFX" (l.8, 81, 104).
- `src/test/resources/import-samples/` -- amostras CSV já existem; o caso "UTF-8 com BOM" é montado no teste. Gerador `scripts/gerar-amostras-extrato.py` não muda.

## Tasks & Acceptance

**Execution:**
- [x] `CsvStatementReader` -- reconhecimento de layout, encoding, separador e leitura das linhas
- [x] `TransactionImportService` -- aceitar `.csv`, `file_type = CSV`, layout e destino no envio (400, decisão 2A)
- [x] `TransactionImportProcessor`, `OfxTransactionParser` -- ramo CSV e a palavra-chave nova
- [x] Frontend -- aceitar `.csv` e textos "OFX ou CSV"
- [x] Épico e `epic-5-context.md` -- registrar 1A (remover o AC da escolha manual do banco) e 2A
- [x] Testes -- leitor (cada amostra, matriz, BOM, aspas), service (extensão, destino, layout), integração (upload CSV → `REVIEW` com transações), `ImportView.spec.ts`

**Acceptance Criteria:**
- Given `nubank-conta.csv` enviado para uma conta, when o processamento termina, then `transactions` tem os mesmos 10 lançamentos (data, valor, tipo, `CARD_PAYMENT`) que o `nubank-conta.ofx` gera.
- Given o extrato real do Nubank em `docs/` enviado no ambiente local, when termina, then a importação fica `REVIEW` com todos os lançamentos e nenhuma linha pulada.

## Implementation Notes

- `TransactionImportProcessor.process` passou a receber o `FileType` (`process(id, fileType, content)`); o CSV pega o banco do layout e não passa pela checagem de truncamento do OFX. Layout desconhecido em segundo plano (não deve acontecer, pois é barrado no envio) vira `FAILED` com a mensagem de CSV não reconhecido.
- Posição da linha pulada no CSV = número da linha no arquivo (1-based), contando quebras dentro de campos entre aspas.
- Linha com menos colunas que o layout usa: com data válida na coluna de data é pulada (`MISSING_AMOUNT`); sem data válida é título/rodapé (ignorada, não contada). "Total" na primeira coluna e linhas de saldo ("SALDO ANTERIOR", "S A L D O", "SALDO", "SALDO DO DIA") são ignorados em todos os layouts.
- Bradesco: valor zero numa coluna conta como vazio; as duas colunas com valor não-zero → `INVALID_AMOUNT`; as duas zero → `ZERO_AMOUNT`. Caixa com `Deb_Cred` diferente de D/C → `INVALID_AMOUNT`.
- `external_id` só com zeros ("0", "000000") vira nulo.
- A palavra-chave nova também marca `CARD_PAYMENT` no `bb-conta.ofx` ("PAGAMENTO CARTÃO DE CRÉDITO OURO"); o teste do parser OFX foi atualizado.
- `OfxBankDetector.decode` remove o BOM UTF-8 e decide UTF-8 ou cp1252 pelo restante (BOM seguido de corpo cp1252 é lido como cp1252).

## Spec Change Log

## Review Triage Log

| # | Origem | Achado | Veredito | Evidência | Destino |
|---|---|---|---|---|---|
| 1 | blind, edge (+claim) | Linha de lançamento com menos colunas some sem contar como pulada | medium | `readRow` l.187: `fields.size() < minColumns` → `return` sem `skipped`; contraria "linha com valor ausente é pulada e contada" | patch: data válida na coluna de data → `MISSING_AMOUNT` |
| 2 | blind, edge | Linhas de saldo só filtradas em BB/Bradesco; "SALDO DO DIA" não está no conjunto | low | `hasBalanceRows=false` para Itaú/Caixa; export real dessas pode trazer saldo e gravá-lo como lançamento | patch: filtro em todos os layouts + "SALDO DO DIA" |
| 3 | edge | Bradesco com "0,00" na coluna oposta vira `INVALID_AMOUNT` | low | `!credit.isEmpty() && !debit.isEmpty()` não distingue zero | patch: coluna zero = vazia |
| 4 | blind, edge | `external_id` "000000" compartilhado entre lançamentos | low | Só `"0".equals` anula; vira falsa duplicata na 5.5 | patch: só zeros → nulo |
| 5 | edge, verification-gap | BOM + corpo cp1252 sai com U+FFFD (regressão) | low | `decode` com BOM usa `new String(UTF_8)` sem recuo; antes caía em cp1252 | patch: decodificação estrita após o BOM |
| 6 | verification-gap, blind | Regras Caixa D/C inválido e Bradesco crédito+débito sem teste | medium | Pré-verificado pela camada | patch: testes inline |
| 7 | blind | Javadoc de `MAX_HEADER_SEARCH` errado | low | O limite inclui o cabeçalho | patch: comentário |
| 8 | blind, edge | Aspa sem fechamento engole o resto do arquivo | low | Exports de banco citam campos corretamente; correção exige reabrir/abortar o tokenizer | rejeitado |
| 9 | blind | CSV cortado na última linha grava valor errado | low | Upload multipart é completo ou falha; export cortado é improvável | rejeitado |
| 10 | blind, edge | Heurística do Itaú aceita qualquer `data;texto;valor` | low | Reconhecimento pelo formato das linhas é o que a spec congelada define | rejeitado |
| 11 | edge | Itaú com `;` sem aspas na descrição | low | Não acontece nas amostras; correção muda a leitura do layout | rejeitado |
| 12 | edge | Caixa com valor negativo e `C` | low | Layout da Caixa é sem sinal; caso inexistente nas amostras | rejeitado |
| 13 | edge | CSV em UTF-16 (Excel "Texto Unicode") | low | Fora dos encodings da spec | rejeitado |
| 14 | blind | Extração de `entry()` mudou o OFX (MEMO/FITID vazios, trim) | false | `value()` já devolve null para vazio e apara; `CARD_PAYMENT` no BB OFX é pedido pela spec | rejeitado |
| 15 | blind | Paridade CSV×OFX ignora descrição | false | Descrições das amostras diferem por banco entre OFX e CSV; compará-las não é invariante | rejeitado |
| 16 | blind | Teste de integração depende da ordem | false | `transactionsOf` ordena por data e `external_id` (iguais no CSV e OFX do Nubank) | rejeitado |
| 17 | blind | Arquivo decodificado/varrido 3 vezes | low | Arquivos de KB; custo desprezível | rejeitado |
| 18 | blind | Spec × sprint-status, linhas do Code Map, colunas > 120, mensagem de formato no frontend | false | Fluxo do step-05; mensagem é genérica ("Formato de arquivo não suportado") | rejeitado |

## Verification

**Commands:**
- `cd porquinho-backend && ./mvnw test` -- 0 falhas (`JAVA_HOME` do JDK 21; não subir o backend de dev)
- `cd porquinho-frontend && npx vitest run && npx vue-tsc --build && npm run lint` -- tudo verde

**Manual checks:**
- Ambiente local: enviar `nubank-conta.csv`, `nubank-cartao.csv`, `bradesco-conta.csv` e o CSV real de `docs/`; conferir contagens na lista e linhas em `transactions`.
