---
title: 'Story 5.6 — Identificar transações Pix e extrair os detalhes'
type: 'feature'
created: '2026-11-05'
status: 'done'
baseline_commit: 'bd3c4f147a34c34394183c523187cfe2f32854b8'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-5-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** toda transação importada sai com `is_pix = false` e `pix_details` vazio. O usuário não sabe quais lançamentos são Pix nem para quem foi ou de quem veio o dinheiro (FR35).

**Approach:** depois da detecção de duplicatas, no momento em que cada lançamento vira `Transaction`, um detector puro (`PixDetector`) reconhece o Pix pela descrição. Ele marca `is_pix` e grava em `pix_details` os dados que a descrição trouxer.

## Boundaries & Constraints

**Always:**
- **Detecção:** padrões de cada banco (tabela abaixo), testados em **todas** as importações, qualquer que seja o banco detectado. Assim, `OTHER` e o cartão também ficam cobertos. Fallback: a palavra "pix" isolada (com limite de palavra, sem diferenciar maiúsculas) também marca Pix, mesmo sem extração.
- **`pix_details`:** JSON válido com `detected: true` e `direction` sempre presentes. `direction` vem de `transaction_type`: DEBIT → `SENT`, CREDIT → `RECEIVED`. Quando presentes, entram também `counterpart_name`, `document` e `counterpart_bank` (`{name, code, agency, account}`, cada um só se presente). Chave ausente é omitida, nunca `null`.
- **Documento:** CPF mascarado e CNPJ são gravados como o banco mostra. CPF completo (11 dígitos à mostra) é mascarado antes de gravar, no formato `•••.XXX.XXX-••`.
- **Texto extraído:** como aparece na descrição (sem mudar maiúsculas nem acentos), só com `trim`. A data/hora que o banco põe junto (`03/10`, `01/10 09:15`) não entra no nome.
- **NFR6:** regex pré-compiladas, sem consulta ao banco de dados.

**Never:**
- Migration nova. As colunas já existem na V26.
- Gravar descrição ou nome no log ou na auditoria.
- Alterar a detecção de duplicatas, o `kind` (CARD_PAYMENT) ou as transações já existentes.
- Ler `docs/` ou usar extrato real.

## I/O & Edge-Case Matrix

| Banco | Descrição (amostra) | `pix_details` |
|---|---|---|
| Nubank | `Transferência recebida pelo Pix - JOANA PEREIRA LIMA - •••.432.198-•• - BCO DO BRASIL S.A. (0001) Agência: 1234 Conta: 98765-4` | RECEIVED, nome, documento, banco `{BCO DO BRASIL S.A., 0001, 1234, 98765-4}` |
| Nubank | `… enviada pelo Pix - MERCADO BOM PRECO LTDA - 12.345.678/0001-90 - CAIXA …` | SENT, CNPJ |
| Nubank | `… - NU PAGAMENTOS - IP (0260) Agência: 1 Conta: 7788990-1` | banco `NU PAGAMENTOS - IP` (o " - " dentro do nome não quebra) |
| Itaú | `PIX TRANSF CARLOS 03/10`, `PIX QRS IFOOD 04/10`, `PIX TRANSF MARIA S11/10` | nome `CARLOS`, `IFOOD`, `MARIA S` |
| BB | CSV `Pix - Enviado - 01/10 09:15 Joana Pereira Lima`; OFX `PIX - ENVIADO 01/10 09:15 JOANA` | nome `Joana Pereira Lima`; `JOANA` |
| Bradesco | `TRANSFERENCIA PIX DES: CARLOS EDUARDO SOUZA 03/10` / `REM: …` | nome |
| Caixa | `ENVIO PIX`, `PIX RECEBIDO` | `{detected, direction}` |
| Outro | `ESTORNO PIX LOJA X` | Pix, `{detected, direction}` |
| Não é Pix | `Compra no débito - UBER *TRIP`, `PIXEL STORE` | `is_pix = false`, `pix_details` nulo |

## Decisões

- **1A — ícone de Pix (Luiz, 2026-11-05):** fica para a 5.9 (revisão) e a 5.12 (`/transacoes`), que ainda não existem. A 5.6 entrega só os dados e registra a regra do ícone no texto do épico dessas duas stories. Nenhuma mudança no frontend.
- **2A — nome cortado no OFX do BB (Luiz, 2026-11-05):** o nome é gravado como vier (`JOANA`, `ROBER`, `CONDOM`), igual ao que aparece na descrição.
- **Tamanho:** a spec fica inteira, sem dividir.

</frozen-after-approval>

## Code Map

- `porquinho-backend/src/main/java/com/porquinho/service/TransactionImportProcessor.java:260-280` -- `toTransaction` (estático): troca `setPix(false)` pelo resultado do detector. É chamado no `.map` do `saveAll` (cerca da linha 181), já depois das duplicatas.
- `.../service/OfxTransactionParser.java:71` -- `Entry(date, description, originalDescription, amount, type, externalId, cardPayment)`. Usar `description` (espaços colapsados, acentos preservados). O CSV devolve o mesmo record.
- `.../entity/Transaction.java:93-95` -- `pixDetails` é `String` com `@JdbcTypeCode(SqlTypes.JSON)`. Gravar JSON válido.
- `.../entity/OnboardingProgress.java:24,89` -- exemplo de `ObjectMapper` estático com `writeValueAsString`. Montar o JSON com `LinkedHashMap` + `ObjectMapper`, nunca por concatenação (nomes podem ter aspas).
- `.../service/DataExportService.java:433` -- `pix_details` vai como string escapada. Passar a exportar o JSON como objeto (`objectMapper.readTree`), sem mudar o conjunto de chaves.
- Testes: `TransactionImportProcessorTest`; `TransactionImportIntegrationTest` (`sample`, `awaitDone`, `transactionsOf`, `newUser`, `account`, `card`).
- `_bmad-output/planning-artifacts/epics/epic-5-transaction-import-ai-classification.md:146-164` -- AC da 5.6.

## Tasks & Acceptance

**Execution:**
- [x] `porquinho-backend/src/main/java/com/porquinho/service/PixDetector.java` -- classe final com `static Optional<String> detect(String description, Transaction.Type type)` (JSON ou vazio), seguindo a tabela e as regras acima -- lógica pura, testável sem Spring.
- [x] `.../service/TransactionImportProcessor.java` -- `toTransaction` chama o detector e preenche `pix` e `pixDetails`.
- [x] `.../service/DataExportService.java` -- exportar `pix_details` como objeto JSON.
- [x] `porquinho-backend/src/test/java/com/porquinho/service/PixDetectorTest.java` -- uma linha da matriz por caso, mais CPF completo mascarado, aspas no nome e descrição nula ou vazia.
- [x] `.../service/TransactionImportIntegrationTest.java` -- importar `{nubank,bb,itau,bradesco,caixa}-conta` (OFX e CSV, cada um em conta própria) e conferir a quantidade de Pix (Nubank 4, BB 3, Itaú 3, Bradesco 2, Caixa 3) e um `pix_details` lido do banco de dados.
- [x] `.../service/DataExportServiceTest` (ou o teste de export que já existe) -- `pix_details` sai como objeto.
- [x] Épico -- na Story 5.6, registrar a forma do JSON (`detected` sempre presente, `direction` pelo tipo), o fallback "pix" e que o ícone saiu dela (1A). Nas Stories 5.9 e 5.12, acrescentar o AC do ícone de Pix (`is_pix`) e a exposição de `isPix`/`pixDetails` na API.

**Acceptance Criteria:**
- Given uma importação com lançamentos Pix e não Pix, when o processamento termina, then só os Pix têm `is_pix = true`, e todo `pix_details` não nulo é JSON válido com `detected` e `direction`.
- Given um lançamento descartado como duplicata ou transferência, when a importação termina, then nenhuma transação é criada para ele (o detector não altera a contagem).

## Spec Change Log

## Review Triage Log

### Revisão 1 (2026-11-05)

| # | Origem | Achado | Veredito | Evidência / rota |
|---|---|---|---|---|
| 1 | blind, edge | CPF completo dentro do nome (Itaú/BB/Bradesco, ou Nubank sem âncora) fica sem máscara | low | `maskFullCpf` só roda no grupo `document`; a regra "CPF completo é mascarado" vale para qualquer texto extraído → patch |
| 2 | blind | CPF/CNPJ sem pontuação não vira `document` | low | Nenhuma amostra tem esse formato; reconhecer 11 dígitos soltos confunde com número de conta → rejeitado |
| 3 | blind | Descrição continua com o CPF completo | low | A descrição é gravada como veio (5.4); a D5 mascara antes da IA → rejeitado |
| 4 | blind | O fallback marca "TARIFA PIX", "IOF PIX" e pagamento de fatura via Pix | low | A regra do fallback está no bloco congelado; esses lançamentos são de fato ligados a Pix → rejeitado |
| 5 | blind, edge | O padrão BB aceita "EnviadoXYZ" e a data com ano vai para o nome ("/2026 09:15") | low | Falta `\b` depois de Enviado/Recebido e o ano é opcional na data → patch |
| 6 | edge | Data com ano no fim (Itaú/Bradesco: "CARLOS 03/10/26") deixa "03/" no nome | low | `TRAILING_DATE` só aceita dd/mm → patch |
| 7 | blind | O padrão `CAIXA` é redundante com o fallback | low | Gera o mesmo JSON; nenhum dano nomeado → rejeitado |
| 8 | blind | `requireNonNull(type)` vem depois do retorno para descrição vazia | false | Os parsers sempre preenchem `type` (`Entry.type`); não há caminho com `type` nulo |
| 9 | blind | Status da spec (`in-review`) diverge do sprint (`in-progress`) | false | O sprint vai para `review` no step 5 |
| 10 | blind | O AC do ícone na 5.12 entrou no cenário do modal de edição | low | Linha dentro do bloco "When I click Editar" → patch (cenário próprio da lista) |
| 11 | blind, vgap | O fallback de `pixDetailsJson` (JSON inválido vira texto) não tem teste | low | Pré-verificado; nenhum teste chega ao `catch` → patch (teste) |
| 12 | blind | A consulta do teste de integração filtra só pela data 2026-10-15 | false | Em `nubank-conta` só a transação da Ana tem essa data |
| 13 | blind | Faltam testes: Nubank com " - " no nome antes do documento e banco sem `(código)` | low | Os ramos existem e não têm teste → patch |
| 14 | blind | Linha longa no Javadoc de `TransactionImportProcessor` | low | Correção de texto → patch |
| 15 | edge | "Agência:" vazia guarda "Conta:" como agência | low | `\S+` casa com a palavra seguinte → patch |
| 16 | edge | Banco com outro número entre parênteses antes do código pega o código errado | low | `.*?` para no primeiro parêntese → patch (o último `(dígitos)` é o código) |
| 17 | edge | Descrição em NFD não casa "Transferência" | low | Normalizar para NFC é uma linha → patch |
| 18 | edge | Descrição cortada em 500 caracteres corta a conta | low | As descrições do Nubank têm cerca de 140 caracteres; caso improvável → rejeitado |

## Design Notes

- Direção pelo tipo, não pela palavra (Enviado/DES/REM). Um estorno de Pix vira crédito e conta como recebido, e os bancos que não dizem a direção (Itaú) também ficam cobertos.
- Nubank: depois do prefixo, separar pelo documento. O documento (CPF mascarado `[•*\d]{3}\.\d{3}\.\d{3}-[•*\d]{2}` ou CNPJ `\d{2}\.\d{3}\.\d{3}/\d{4}-\d{2}`) é a âncora entre nome e banco. O banco vai até `(código)`, seguido de `Agência: X Conta: Y`. Se a âncora faltar, grava só o nome (o texto até o primeiro ` - `).
- Ordem: padrões dos bancos primeiro e fallback "pix" por último. O primeiro padrão que casar define a extração.

## Verification

**Commands:**
- `cd porquinho-backend && JAVA_HOME=~/.sdkman/candidates/java/21.0.11-tem ./mvnw test` -- esperado: todos verdes (eram 1016).
- `cd porquinho-frontend && npm run lint && npx vue-tsc --build && npx vitest run` -- esperado: sem regressão (o frontend não muda, decisão 1A).
