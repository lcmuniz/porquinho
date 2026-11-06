---
title: 'Story 5.7 — Detectar compras parceladas e criar as parcelas futuras'
type: 'feature'
created: '2026-11-05'
status: 'done'
baseline_commit: '69328b3d651354b00f29b8c2dd583a973fbd0392'
route: 'dispatch'
review_loop_iteration: 3
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-5-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** uma compra parcelada no cartão entra como um lançamento comum (`is_installment = false`). As parcelas dos meses seguintes não existem, e o orçamento dos próximos meses não sabe que esse dinheiro já está comprometido.

**Approach:** um detector puro (`InstallmentDetector`) lê o marcador de parcela na descrição dos lançamentos de cartão. Ao gravar a importação, cada parcela entra num grupo (`installment_group_id`) e as parcelas que faltam viram parcelas futuras. Quando uma importação posterior traz uma parcela que já existe como futura, essa linha é confirmada em vez de se criar outra (D7).

## Boundaries & Constraints

**Always:**
- **Só cartão:** roda só em importação para cartão, em lançamento `DEBIT` com `kind = REGULAR`. Na conta, nada muda ("PIX TRANSF CARLOS 03/10" continua sem parcela).
- **Marcadores** (sem diferenciar maiúsculas): `Parcela 3/10`, `PARC 03/10`, `PARC 3/10` e `dd/dd` no fim da descrição, depois de espaço. Vale só se `1 ≤ número ≤ total`, `2 ≤ total ≤ 48`.
- **Parcela importada:** `is_installment = true`, `installment_number`, `installment_total`, `installment_group_id`.
- **Parcelas futuras:** uma para cada número de `número+1` até `total`, com o mesmo valor, `transaction_date = data + (k − número)` meses (`plusMonths`), `is_future_installment = true`, `external_id` nulo, `review_status = PENDING`, `import_id` da importação que as criou, mesmo grupo. Descrição e descrição original iguais às da parcela importada, com o número trocado e o mesmo número de dígitos (`MAGAZINELUIZA  03/10` → `MAGAZINELUIZA  04/10`; `Parcela 3/10` → `Parcela 4/10`).
- **Grupo existente:** antes de criar o grupo, procurar parcelas ativas do mesmo cartão com o mesmo total, valor dentro da tolerância da 3A e a mesma descrição base (`original_description` sem o marcador, comparada com `DuplicateDetector.similar`) e data a até 20 dias da data esperada para o número (4A). Se achar, a parcela entra nesse grupo. Se o número já existe como futura, a linha é confirmada (`is_future_installment = false`, `external_id`, `transaction_date`, `amount`, `description` e `original_description` do arquivo). Só se criam as futuras de números que ainda não existem no grupo. Isso cobre também importar um mês antigo depois de um mais novo.
- **Duplicatas (5.5):** parcelas futuras não entram como candidatas do `DuplicateDetector`. Sem isso, "04/10" do mês seguinte seria descartada como duplicata da futura (≈95% de semelhança) em vez de confirmá-la.
- Tudo na mesma transação e sob o mesmo advisory lock da 5.5. A importação só é gravada inteira.
- **NFR6:** regex pré-compiladas e uma consulta só para as parcelas do cartão.

**Never:**
- Migration nova (as colunas existem na V26).
- Mudar a 5.5 além de tirar as futuras da lista de candidatas. Mudar o Pix (5.6), o `kind` ou o saldo (cartão nunca mexe em saldo).
- Gravar descrição no log ou na auditoria (só contagens).
- Contar as futuras como gasto do mês. Ainda não há cálculo de gasto: as Epics 6/7 devem filtrar `is_future_installment` (anotar no épico).

## I/O & Edge-Case Matrix

| Cenário | Entrada / estado | Resultado |
|---|---|---|
| Nubank CSV | `Magazine Luiza - Parcela 3/10`, 02/10, 189,90 | parcela 3/10 + 7 futuras (02/11 … 02/05/2027) |
| Itaú OFX | `CASASBAHIA     07/12` | 7/12 + 5 futuras |
| Última parcela | `… 12/12` | parcela, sem futuras |
| Inválido | `LOJA 15/10`, `LOJA 0/10`, `LOJA 3/1` | não é parcela |
| Conta | `PIX TRANSF CARLOS 03/10` em conta | não é parcela |
| Mês seguinte | futura 4/10 existe; arquivo traz `… 04/10` | futura confirmada, nada inserido, nenhuma futura nova |
| Reimportar o mesmo arquivo | 3/10 já gravada (mesmo `external_id`) | duplicata (5.5), nenhuma futura nova |
| Mês antigo depois | grupo 3..10 existe; arquivo traz 2/10 | 2/10 entra no grupo; nenhuma futura criada |
| Fevereiro | 31/01, 1/3 | futuras em 28/02 e 31/03 |
| Arredondamento | 1/3 de 333,34 gravada (futuras 333,34); arquivo traz 2/3 de 333,33 | futura 2/3 confirmada com 333,33; nenhum grupo novo |
| Sobra maior | 1/10 de 100,09 gravada; arquivo traz 2/10 de 100,00 | futura 2/10 confirmada com 100,00 (tolerância 0,09) |
| Valor diferente | grupo de 189,90 em 10x; arquivo traz `… 04/10` de 190,00 | outro grupo (diferença 0,10 > 0,09) |
| Compra repetida | grupo `ANUIDADE DIFERENCIADA` 5..12 de 2026 (5/12 em 25/10/2026); arquivo traz `… 01/12` em 25/06/2027 | outro grupo, com as futuras 2..12 |
| Data perto | futura 4/10 em 01/11; arquivo traz `… 04/10` em 15/11 | futura confirmada |

## Decisões

- **1A — texto "Compra parcelada: 3/10 — restam 7 parcelas" (Luiz, 2026-11-05):** como o ícone de Pix (1A da 5.6), fica para a 5.9 (revisão) e a 5.12 (`/transacoes`). A 5.7 entrega só os dados e registra a regra no épico dessas duas stories (com a exposição de `isInstallment`, `installmentNumber`, `installmentTotal` e `isFutureInstallment` na API). Nenhuma mudança no frontend.
- **2A — parcela futura confirmada (Luiz, 2026-11-05):** conta como duplicata (`duplicates_found`). A linha fica com a importação que a criou, com a mesma categoria e o mesmo status. O rótulo segue "X duplicatas detectadas e ignoradas" e a soma dos contadores continua fechando. Sem migration. Se o arquivo só trouxer confirmações e duplicatas, a importação vai para `COMPLETED` ("Nenhuma transação nova"), mas as confirmações são gravadas do mesmo jeito.
- **3A — centavos de arredondamento (Luiz, 2026-11-05, revista no loop 3):** ao procurar o grupo, o valor pode diferir até o maior entre R$ 0,05 e R$ 0,01 × (total − 1), inclusive. Total, número livre ou futuro, descrição base e data continuam exigidos. A futura confirmada recebe o valor do arquivo.
- **4A — data do grupo (Luiz, 2026-11-05):** a parcela só entra num grupo se a data dela estiver a até 20 dias (inclusive) da data esperada para aquele número: a data de um membro do grupo mais a diferença de números em meses (`plusMonths`). Evita que uma compra repetida (anuidade do ano seguinte) entre no grupo da anterior pelo número livre.
- **Matriz (Luiz, 2026-11-05):** a última futura da linha "Nubank CSV" corrigida de 02/06/2027 para 02/05/2027 (achado #31).
- **5A — fim das parcelas futuras (Luiz, 2026-11-05):** a 5.7 não detecta estorno, antecipação nem cartão apagado. As Epics 6 e 7 ignoram futuras de cartão apagado, que voltam a contar se o cartão for restaurado. A 5.12 ganha a ação "remover parcelas futuras desta compra", para o usuário tratar estorno e antecipação.
- **Tamanho:** a spec fica inteira, sem dividir (como na 5.6).

</frozen-after-approval>

## Code Map

- `porquinho-backend/src/main/java/com/porquinho/service/TransactionImportProcessor.java:162-187` -- transação com o lock (`lockTarget`), `detect`, update com guarda e `saveAll` via `toTransaction` (260-283, hoje `setInstallment(false)`). As parcelas entram aqui, depois das duplicatas, e só quando `creditCard`.
- `.../TransactionImportProcessor.java:205-244` -- `detect`: no cartão, filtrar `isFutureInstallment()` da lista `existing` antes de chamar `DuplicateDetector.detect`.
- `.../service/DuplicateDetector.java:187` -- `similar(a, b)` (estático, normaliza e compara). Reusar para a descrição base.
- `.../service/PixDetector.java` -- modelo de detector puro: classe final, `Pattern` estático, testado sem Spring.
- `.../repository/TransactionRepository.java:32-38` -- modelo de query. Nova: parcelas ativas (`is_installment = true`, `deleted_at IS NULL`) de um cartão do usuário, em ordem fixa.
- `.../entity/Transaction.java:97-110` -- campos de parcela já mapeados.
- `.../service/DataExportService.java:436-441` -- o export já leva as colunas de parcela. O purge apaga `transactions` inteira, então não muda.
- Amostras: `porquinho-backend/src/test/resources/import-samples/nubank-cartao.csv` (Parcela 3/10, 2/12) e `itau-cartao.ofx` (03/10, 07/12, 01/03, 05/12).
- Testes: `TransactionImportIntegrationTest` (`sample`, `awaitDone`, `transactionsOf`, `newUser`, `card`) e `TransactionImportProcessorTest`.

## Tasks & Acceptance

**Execution:**
- [x] `porquinho-backend/src/main/java/com/porquinho/service/InstallmentDetector.java` -- `static Optional<Installment> detect(String description)` com `record Installment(int number, int total, String baseDescription)` e `static String withNumber(String description, int number)` -- lógica pura.
- [x] `.../repository/TransactionRepository.java` -- query das parcelas ativas do cartão.
- [x] `.../service/TransactionImportProcessor.java` -- tirar as futuras das candidatas a duplicata; montar parcelas, grupos, confirmações e futuras antes do update com guarda, porque as confirmações saem de `toSave` e entram em `duplicates` (2A), o que pode levar a `COMPLETED`; confirmações via `save` da entidade carregada; auditoria com `installments_found`, `future_installments_created` e `installments_confirmed`.
- [x] `porquinho-backend/src/test/java/com/porquinho/service/InstallmentDetectorTest.java` -- uma linha da matriz por caso, mais descrição nula ou vazia e preservação de zeros (`03/10` → `04/10`).
- [x] `.../service/TransactionImportIntegrationTest.java` -- Nubank cartão: 2 parcelas e 7 + 10 futuras com datas certas e grupo comum; Itaú cartão: 4 parcelas; conta: nenhuma; mês seguinte confirma a futura; reimportação não cria futuras.
- [x] `.../service/InstallmentPlanner.java` (novo, puro) -- grupos, confirmações e futuras; testado em `InstallmentPlannerTest` (matriz inteira, incluindo arredondamento e valor diferente).
- [x] `.../service/TransactionImportIntegrationTest.java` -- também: cartão que só traz confirmações vai para `COMPLETED` (`imported_transactions = 0`, `duplicates_found = 1`, futura confirmada).
- [x] `_bmad-output/implementation-artifacts/epic-5-context.md` -- nota para a 5.8/5.9: as futuras da 5.7 têm `import_id` e `PENDING`; decidir lá se a IA as classifica ou se herdam a categoria da parcela de origem, e se entram na lista e no "Aprovar todas".
- [x] `.../service/InstallmentPlanner.java` e `InstallmentDetector.java` -- (loop 2) janela de data da 4A; número, base e `withNumber` lidos da mesma descrição; `String.format(Locale.ROOT, …)`.
- [x] `.../service/TransactionImportIntegrationTest.java` -- (loop 2) parcela do mês seguinte em outro cartão não confirma a futura do primeiro; futura com `deleted_at` não é confirmada.
- [x] Epics 6 e 7 -- (loop 2) comprometido do mês = futuras com data no mês; projeção usa só futuras com data a partir de hoje.
- [x] `.../service/InstallmentPlanner.java` -- (loop 3) tolerância proporcional da 3A, com testes da matriz; a confirmação também copia `is_pix` e `pix_details` da linha do arquivo.
- [x] `.../service/InstallmentDetector.java` e teste -- (loop 3) `(?![\d/])` nos marcadores `Parcela` e `PARC`; `PARC 03/10/26` e `Parcela 3/10/2026` não são parcela.
- [x] `.../service/TransactionImportProcessor.java` -- (loop 3) comentário do rollback: o flush ocorre e o `setRollbackOnly` desfaz.
- [x] Testes -- (loop 3) confirmação grava `description` e `original_description` do arquivo, quando diferem das da futura; `InOrder` com `findActiveInstallmentsByCreditCard` depois de `lockTarget` em `cardChecksOnlyTheCardAndNoTransfers`; integração: confirmação com `external_id` de OFX e reimportação sem id depois da confirmação.
- [x] Epics 5, 6 e 7 -- (loop 3) corrigir a frase da 5.7; 3A proporcional; comprometido e projeção cortam no primeiro dia do mês corrente e ignoram futuras de cartão apagado (5A); na 5.12, a ação "remover parcelas futuras desta compra".
- [x] Épico -- registrar na 5.7 as decisões; nas 5.9 e 5.12, o AC do texto de parcela (1A); nas Epics 6/7, que o gasto do mês ignora `is_future_installment`.

**Acceptance Criteria:**
- Given uma importação de cartão com parcelas, when o processamento termina, then cada grupo tem exatamente uma linha por número de 1..total que veio de arquivo ou foi criada como futura, sem números repetidos.
- Given um grupo cuja parcela mais alta foi apagada, when chega uma parcela menor do mesmo grupo, then as futuras criadas são as de número+1 até total que faltam no grupo (mesma regra do bloco congelado).
- Given uma importação de conta, when termina, then nenhuma transação tem `is_installment = true`.

## Implementation Notes

- Loops 1 e 2: implementação feita direto pelo agente principal, por engano. A ferramenta de subagente estava disponível; o agente concluiu errado que não estava. A partir do loop 3, implementação e revisão usam subagentes.
- A lógica de grupos ficou numa classe pura à parte, `InstallmentPlanner` (`plan(rows, existing)`), testada sem Spring em `InstallmentPlannerTest`. O processador só monta as linhas, chama o planner e grava.
- (Loop 1) Ao entrar num grupo existente, só se criavam as futuras depois do maior número do grupo. Revisão #2: desviava do bloco congelado; no loop 2 passa a criar as de número+1 até total que faltam.
- Várias parcelas da mesma compra no mesmo arquivo (extrato de um ano): a parcela do arquivo toma o lugar da futura criada pela mesma importação e conta como importada, não como duplicata.
- Se a importação sair de `PROCESSING` antes do update com guarda, a transação é marcada `setRollbackOnly`, porque o planner já alterou as futuras confirmadas (entidades gerenciadas).
- O CSV de cartão do Nubank não tem identificador, então a futura confirmada fica com `external_id` nulo, como veio do arquivo.
- Testes de integração: `transactionsOf` passa a trazer só as linhas do arquivo; `futuresOf` traz as futuras. A contagem do purge passou de 18 para 39 (21 futuras do Itaú).
- Épicos: 5.7 reescrita com as decisões, AC do texto de parcela na 5.9 e na 5.12, regra de gasto sem futuras nas Epics 6 e 7. `epic-5-context.md` atualizado.
- (Loop 2) Reaplicado `spec-5-7-impl-loop1.patch`, com estes ajustes: tolerância `AMOUNT_TOLERANCE` = 0,05 (3A); a confirmação grava `amount`; base do grupo pela `original_description` dos dois lados; futuras de número+1 até total que faltam no grupo (`highestNumber` removido); teste de integração `cardFileWithOnlyConfirmationsCompletes`; nota da 5.8/5.9/5.10 no `epic-5-context.md`; Javadoc e log quebrados em 120 colunas.
- (Loop 3) Implementação por subagente, interrompida pelo usuário no fim do código; os documentos e a verificação foram concluídos pelo agente principal. `nearExpectedDate` usa o membro de número mais próximo (o menor no empate). Número, base e `withNumber` vêm da `original_description`.
- Auditoria da matriz (loop 3): Nubank CSV, Itaú, fevereiro, última parcela, mês seguinte, mês antigo, arredondamento, valor diferente, compra repetida (`repeatedPurchaseOfAnotherYearOpensItsOwnGroup`) e data perto (`aDateNearTheExpectedOneConfirmsTheFuture`) em `InstallmentPlannerTest`; inválidos em `InstallmentDetectorTest`; conta, mês seguinte, só confirmações, outro cartão, futura apagada e reimportação em `TransactionImportIntegrationTest`. Suíte: 1134 testes, 0 falhas.

## Spec Change Log

- **Loop 3 (2026-11-05):** achados #35/#36 (futuras ativas depois de estorno, antecipação ou cartão apagado) → intent_gap, resolvidos pela 5A. Achado #34 → o humano reviu a 3A para tolerância proporcional. Emendado: regra do grupo, duas linhas da matriz, tarefas abaixo. Estado ruim evitado: valor comprometido dobrado por sobra maior e futuras de cartão apagado somando. KEEP: tudo do loop 2 mais a janela da 4A, `Locale.ROOT`, número e base da mesma descrição, testes de outro cartão e futura apagada; reaplicar `spec-5-7-impl-loop3.patch` e somar os patches #37–#41, #43, #44, #46 e #47.

- **Loop 2 (2026-11-05):** achado #19 (compra repetida entra no grupo da anterior pelo número livre) → intent_gap, resolvido pela 4A (data a até 20 dias da esperada). Emendado: regra do grupo existente, duas linhas na matriz, tarefas abaixo. Estado ruim evitado: `ANUIDADE DIFERENCIADA 01/12` do ano seguinte misturada ao grupo de 2026, com futuras faltando. KEEP: tudo do loop 1 mais 3A, base pela `original_description`, futuras de número+1 até total que faltam, teste `COMPLETED`; reaplicar `spec-5-7-impl-loop2.patch` e somar os patches #20/#25, #23, #24 e #32.

- **Loop 1 (2026-11-05):** achado #3 (centavos de arredondamento abrem outro grupo e duplicam futuras) → intent_gap, resolvido pela decisão 3A (tolerância de R$ 0,05). Emendado: regra do grupo existente (valor ±0,05, base pela `original_description`, confirmação grava o valor do arquivo), duas linhas na matriz, tarefas do planner, do teste `COMPLETED` e da nota 5.8/5.9, AC das futuras. Estado ruim evitado: parcela 2/3 de 333,33 abrindo outro grupo e criando a 3/3 de novo. KEEP: detector e planner puros, filtro das futuras nas candidatas da 5.5, `setRollbackOnly` quando o update com guarda dá 0, auditoria com três contadores novos, `transactionsOf`/`futuresOf` nos testes; reaplicar `spec-5-7-impl-loop1.patch` e ajustar.

## Review Triage Log

### Revisão 1 (2026-11-05)

| # | Origem | Achado | Veredito | Evidência / rota |
|---|---|---|---|---|
| 1 | blind | Descartar a importação de origem apaga a parcela confirmada por outra; descartar a nova não desfaz a confirmação | medium | Ainda não há descarte no código (só o enum `DISCARDED`). É restrição de projeto para a 5.10, consequência da 2A → defer |
| 2 | blind, edge (claim) | Futuras criadas só acima do maior número do grupo: recria a 10/10 apagada no fim e não cria lacunas abaixo, o que desvia de "futuras de número+1 até total que ainda não existem" | low | `InstallmentPlanner.java:106` usa `Math.max(number, highest) + 1` → patch: iterar de número+1 até total e criar só o que falta no grupo |
| 3 | blind, edge | Valor exigido igual: centavos de arredondamento numa parcela (333,34 + 2 × 333,33) abrem outro grupo e duplicam futuras | medium | `InstallmentPlanner.java:128` `compareTo != 0`. A regra "mesmo valor" está no bloco congelado e no AC do épico → intent_gap |
| 4 | blind | Base do grupo vem de `description`, que será editável (5.10/5.12) | low | `baseOf(transaction.getDescription())` → patch: usar `original_description` |
| 5 | blind | Consulta das parcelas do cartão sem limite | low | O filtro por total e valor vem antes do Levenshtein; uns poucos milhares de linhas cabem na NFR6 → rejeitado |
| 6 | blind | Formatos "PARCELA 03 DE 10" e "3/10" de um dígito no fim não são reconhecidos | maybe-false | Sem extrato real (D6) não dá para saber se algum banco usa; seria medium → defer |
| 7 | blind | Data dd/mm no fim de descrição de cartão vira parcela | false | O AC do épico define o "dd/dd" no fim de descrição de cartão como marcador → rejeitado |
| 8 | blind | A confirmação troca a data projetada pela do arquivo; um emissor que repete a data da compra tira a parcela do mês | maybe-false | Depende do formato real (D6); seria medium → defer |
| 9 | blind, vgap | Sem teste do cartão que só traz confirmações e vai para `COMPLETED` | low | Pré-verificado (vgap) → patch (teste de integração) |
| 10 | blind | `setRollbackOnly` só testado com mock | false | `markReadyForReview` tem `flushAutomatically`: o flush acontece na mesma transação, e o `TransactionTemplate` faz rollback quando a flag está ligada → rejeitado |
| 11 | blind | Confirmação aparece como "duplicata ignorada" e mantém o `review_status` da importação de origem | false | É exatamente a decisão 2A → rejeitado |
| 12 | blind | Nada orienta a 5.8/5.9 sobre as futuras (IA, categoria, "Aprovar todas") | low | Patch: nota no contexto do épico para a 5.8/5.9 |
| 13 | blind | Consumidores atuais de `transactions` passariam a somar as futuras | false | Só o processador, o export e o purge usam `TransactionRepository`; não há soma nem lista → rejeitado |
| 14 | blind | Linha longa no Javadoc do processador | low | Patch (correção de texto) |
| 15 | blind | Nomes totalmente qualificados nos testes | false | O arquivo de teste já usava `org.assertj.core.groups.Tuple.tuple` assim; nenhum dano nomeado → rejeitado |
| 16 | blind | `clip()` pode cortar um par surrogate no caractere 500 | low | Exige descrição de 500 caracteres com o marcador e dígito a mais; improvável → rejeitado |
| 17 | edge | Futura antiga de uma compra idêntica de um ano antes é confirmada pela compra nova | low | Exige compra igual (loja, valor, total) com futuras nunca importadas; o guarda por data acrescenta ramos → rejeitado |
| 18 | edge | Descrição só com o marcador deixa a base vazia e funde compras | low | Improvável ("Parcela 3/10" sozinho); o guarda é um ramo novo → rejeitado |

**Loop 1:** o #3 é intent_gap. O código do backend foi revertido; o que foi feito está em `spec-5-7-impl-loop1.patch` (KEEP: detector, planner, filtro das futuras nas duplicatas, `setRollbackOnly`, testes), para reaplicar com os patches #2, #4, #9, #12 e #14.

### Revisão 2 (2026-11-05)

| # | Origem | Achado | Veredito | Evidência / rota |
|---|---|---|---|---|
| 19 | blind | `findGroup` não olha data: compra nova (ex.: `ANUIDADE DIFERENCIADA 01/12` do ano seguinte) entra no grupo da compra anterior pelo número livre; as futuras 5..12 do ano novo só surgem quando a 5/12 abre outro grupo | medium | A anuidade está na amostra `itau-cartao.ofx` e se repete todo ano com o mesmo valor. O critério do grupo está no bloco congelado → intent_gap |
| 20 | blind | Futuras com data já passada (fatura antiga ou meses pulados) ficam `is_future_installment = true` para sempre e entrariam na projeção da Epic 7 | low | Real, mas a soma ainda não existe → patch: nota na Epic 7 (projeção só com data ≥ hoje) |
| 21 | blind | A confirmação entre CSV e OFX depende de `similar` aceitar `Magazine Luiza` × `MAGAZINELUIZA` | false | Normalizados: "magazine luiza" × "magazineluiza", distância 1 em 14 → 0,93 ≥ 0,90 → rejeitado |
| 22 | blind | A confirmação troca a descrição editada pelo usuário pela do arquivo | false | O bloco congelado manda gravar `description` do arquivo na confirmação → rejeitado |
| 23 | blind, edge | Número lido de `description`; base e `withNumber` da `original_description`: se divergirem, a base fica com o marcador e a futura sai com o número antigo | low | Fix direto (detectar pela `original_description` também) → patch |
| 24 | blind, edge | `String.format` sem locale em `withNumber` | low | Fix direto (`Locale.ROOT`) → patch |
| 25 | blind | Nenhum épico faz o orçamento (Epic 6) mostrar as futuras do mês como valor comprometido, nem a projeção de fim de mês (Epic 7) incluí-las | low | O Problem da story fala do orçamento dos próximos meses e as Epics 6/7 só tiram as futuras do gasto → patch junto com o #20: nota nas Epics 6 e 7 (comprometido do mês = futuras com data no mês; projeção só com data ≥ hoje) |
| 26 | blind | Sprint em `in-progress` com a spec em `in-review`; Code Map com linhas antigas | false | O sprint vai para `review` no passo 5; corrigir o Code Map é editar esta spec → rejeitado |
| 27 | blind | Ordem entre grupos criados na mesma importação depende do UUID | low | Só com duas compras idênticas no mesmo arquivo; as duas são equivalentes e a confirmação grava a data do arquivo → rejeitado |
| 28 | blind | Arredondamento e mês antigo só testados no planner | low | O caminho do processador com a consulta real já é coberto por `nextMonthConfirms…` e `cardFileWithOnlyConfirmations…` → rejeitado |
| 29 | edge | Bases longas que diferem em 1–2 caracteres (`MERCADOLIVRE*LOJA1`/`LOJA2`) se fundem | low | `DuplicateDetector.similar` é o critério do bloco congelado → rejeitado |
| 30 | edge (claim) | O ramo de confirmação não cria as futuras que faltam no grupo | low | A matriz congelada ("Mês seguinte: … nenhuma futura nova") pede isso; o AC solto seria acertado editando esta spec → rejeitado |
| 31 | edge (claim) | A matriz diz que a última futura da 3/10 de 02/10 cai em 02/06/2027; o certo é 02/05/2027 | low | `plusMonths(7)` dá 2027-05-02 e os testes afirmam isso. O erro está no bloco congelado → rejeitado, levar ao humano |
| 32 | vgap | A consulta das parcelas não é testada no banco por cartão nem por `deleted_at` | low | Pré-verificado → patch (teste de integração com dois cartões e com a futura apagada) |

**Loop 2:** o #19 é intent_gap. Código do backend revertido; o que foi feito está em `spec-5-7-impl-loop2.patch` (KEEP: tudo do loop 1 mais 3A, base pela `original_description`, futuras que faltam, teste `COMPLETED`), para reaplicar com os patches #20/#25, #23, #24 e #32.

### Revisão 3 (2026-11-05, por subagentes)

Correção da Revisão 2: o achado "nada cancela as futuras em estorno ou antecipação" ficou sem linha naquela tabela, por engano. Ele volta aqui como #35. A nota do #25 de que o texto "chegou cortado" também estava errada; o item veio inteiro.

| # | Origem | Achado | Veredito | Evidência / rota |
|---|---|---|---|---|
| 33 | blind, edge | Banco que repete a data da compra em cada parcela: com a 4A a parcela fica fora da janela e abre um grupo por mês; no CSV sem id, a 5.5 a descarta como duplicata | maybe-false | Depende do formato real (D6). Seria medium ou high → defer, com o impacto novo |
| 34 | blind, edge | Tolerância fixa de R$ 0,05 não cobre sobra maior (R$ 1.000,09 em 10x: 100,09 + 9 × 100,00) | medium | Real. Primeiro rejeitado pela 3A fixa; o humano reviu a decisão (3A proporcional) → patch no loop 3 |
| 35 | blind, edge | Estorno ou antecipação deixam as futuras ativas, somando ao comprometido | medium | O planner ignora CREDIT; o bloco congelado não trata o fim das futuras → intent_gap |
| 36 | blind | Cartão apagado mantém as futuras ativas, e as regras das Epics 6/7 não filtram por cartão ativo | medium | `CreditCardService.delete` só marca o cartão → intent_gap, mesma pergunta do #35 |
| 37 | blind | Epic 6: comprometido inclui meses encerrados | low | Nota escrita sem corte → patch de documento |
| 38 | blind | Epic 7: "data a partir de hoje" faz sumir a parcela do mês corrente ainda não importada | medium | Nota do loop 3 errada: não é gasto nem projeção → patch de documento (corte no primeiro dia do mês corrente) |
| 39 | blind, edge | `PARC 03/10/26` e `Parcela 3/10/2026` viram parcela e criam futuras falsas | medium | `(?!\\d)` não recusa a barra → patch: `(?![\\d/])` e caso no teste |
| 40 | blind | Falta integração: confirmação com `external_id` de OFX e reimportação sem id depois da confirmação | low | Patch (teste) |
| 41 | blind | A confirmação não atualiza `is_pix` e `pix_details` | low | Correção direta → patch |
| 42 | blind | Lançamentos anteriores à 5.7 ficam sem grupo | low | Não há workflow de deploy no repositório; sem evidência de dados em produção → rejeitado |
| 43 | blind | Comentário "never flush them" não descreve o que acontece | low | `flushAutomatically = true`: o flush ocorre e o rollback desfaz. Vale também para a evidência do #10 → patch |
| 44 | blind | Frase quebrada na 5.7 do épico; contador do loop e Change Log atrasados | low | A frase é real → patch; o contador e o log são atualizados neste loop |
| 45 | edge (claim) | O ramo de confirmação não cria as futuras que faltam | low | carried #30 → rejeitado |
| 46 | vgap | Nenhum teste observa `description` e `original_description` depois da confirmação | low | Pré-verificado → patch (teste) |
| 47 | vgap | Nada fixa que a consulta das parcelas roda depois do advisory lock | low | Pré-verificado → patch (`InOrder`) |
| 48 | vgap | `clip()` sem teste | low | Disposição do revisor → defer |

**Loop 3:** #35 e #36 são intent_gap. Código do backend revertido; o que foi feito está em `spec-5-7-impl-loop3.patch`, para reaplicar com os patches #37–#41, #43, #44, #46 e #47.

### Revisão 4 (2026-11-06, por subagentes)

| # | Origem | Achado | Veredito | Evidência / rota |
|---|---|---|---|---|
| 49 | blind, edge | Futuras removidas pelo usuário voltam quando uma parcela anterior entra no grupo, o que desfaz a ação da 5A | medium | O planner só vê parcelas ativas. Ainda não há remoção no código (a ação é da 5.12), como no #1 → defer, restrição de projeto para a 5.12 |
| 50 | blind | Parcela fora do grupo (valor, data ou nome) abre grupo novo e dobra o comprometido | low | A matriz congelada manda "outro grupo" para valor diferente; a data repetida é o #33 → rejeitado |
| 51 | blind, edge | Data no fim de descrição de cartão vira parcela | false | carried #7: o AC define `dd/dd` no fim como marcador → rejeitado |
| 52 | blind | A confirmação troca a descrição editada | false | carried #22: o bloco congelado manda gravar a do arquivo → rejeitado |
| 53 | blind | Parcela confirmada não volta à classificação nem à revisão; se a 5.8 não classificar futuras, fica sem categoria | low | A nota do contexto fala só das futuras → patch de documento |
| 54 | blind | A Story 6.6 soma em `spent_amount` toda transação classificada, sem excluir as futuras | low | Contradiz a regra de Spent da Epic 6; a 9.1 já tem a opção "Include future installments" → patch de documento |
| 55 | blind | A 5.5 compara a descrição com o marcador e pode descartar parcela de número diferente | low | Exige compras iguais a até 1 dia, com números diferentes; a data repetida é o #33; a guarda é um ramo novo → rejeitado |
| 56 | blind | Confirmação mantém o `import_id` de origem, e a 5.5 deduz o formato errado no OFX seguinte | low | Exige CSV e OFX no mesmo cartão e duas compras iguais a até 1 dia; a guarda acrescenta estado → rejeitado |
| 57 | blind | Confirmações aparecem como duplicatas ignoradas | false | carried #11: é a decisão 2A → rejeitado |
| 58 | blind | Consulta das parcelas do cartão sem limite | low | carried #5 → rejeitado |
| 59 | blind | Sem índice único por grupo e número; o `merge` esconde a violação | false | O advisory lock serializa as importações e nenhum caminho grava confirmada e futura de mesmo número → rejeitado |
| 60 | blind, edge | Descrição só com o marcador funde compras | low | carried #18 → rejeitado |
| 61 | blind | Rollback testado só com mock; "managed entities" errado; `saveAll` redundante | false | carried #10 para o teste. As entidades estão gerenciadas até o flush, que é o que o comentário descreve; o merge é inofensivo → rejeitado |
| 62 | edge | 1/10 no grupo 5..10 cria as futuras 2, 3 e 4 no passado | low | O bloco congelado manda criar os números que faltam até o total; o corte das Epics 6 e 7 ignora meses encerrados → rejeitado |
| 63 | edge | Compras iguais no mesmo mês se fundem pela janela de 20 dias | low | Risco aceito nas Design Notes → rejeitado |
| 64 | vgap | Nenhum teste fixa a exigência do mesmo total | low | Pré-verificado por mutação → patch (teste) |
| 65 | vgap | Nada fixa que a confirmação mantém status e categoria (2A) | low | Pré-verificado por mutação → patch (teste) |
| 66 | vgap | A fronteira à esquerda de `Parcela` e `PARC` não tem teste | low | Pré-verificado por mutação → patch (teste) |

Sem intent_gap nem bad_spec: nenhum loop novo.

## Design Notes

- Descrição base: tirar o marcador e um separador ` - ` que sobrar no fim, depois `trim`. `Magazine Luiza - Parcela 3/10` → `Magazine Luiza`.
- Ao procurar o grupo, o empate (duas compras iguais, mesmo valor e total) fica com o grupo que ainda tem o número livre ou futuro. Se houver mais de um, vale o de `created_at` mais antigo. Duas compras idênticas no mesmo cartão podem se fundir; o risco é aceito.

## Verification

**Commands:**
- `cd porquinho-backend && JAVA_HOME=~/.sdkman/candidates/java/21.0.11-tem ./mvnw test` -- esperado: todos verdes.
- `cd porquinho-frontend && npm run lint && npx vue-tsc --build && npx vitest run` -- esperado: sem regressão.
