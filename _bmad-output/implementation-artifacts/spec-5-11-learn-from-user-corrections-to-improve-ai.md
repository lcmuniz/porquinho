---
title: 'Story 5.11 — Aprender com as correções do usuário'
type: 'feature'
created: '2026-11-09'
baseline_commit: 'e71521ade3839ab3e5f44842739cff7f1c8d0132'
status: 'done'
route: 'dispatch'
review_loop_iteration: 1
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-5-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** quando o usuário corrige uma categoria na revisão, o sistema não guarda nada. O `ai_classification_history` continua com `was_corrected = false`, e na importação seguinte a mesma descrição vai de novo para a IA e pode errar de novo.

**Approach:** registrar a correção no histórico e gravar um padrão por descrição normalizada em `user_classification_patterns`. Nas próximas importações, a linha cuja descrição bate com um padrão é classificada sem chamar a IA, com origem `PATTERN`.

## Boundaries & Constraints

**Always:**
- **Migration V30:** `user_classification_patterns` com `id`, `user_id`, `description_pattern`, `preferred_category_id`, `usage_count`, `last_used_at` e `created_at`, único por `(user_id, description_pattern)`. As FKs seguem a V28, sem `ON DELETE`.
- **Normalização:** tira o marcador de parcela (`InstallmentDetector.detect(...).baseDescription()`), passa para minúsculas, remove acentos e troca tudo que não é letra por um espaço, o que já elimina datas, números e documentos. Espaços repetidos viram um só. Um padrão vazio não aprende nem casa com nada.
- **Histórico:** ao gravar a categoria (`writeCategory` da 5.10), as linhas de `ai_classification_history` das transações editadas recebem `was_corrected = (predicted_category_id IS DISTINCT FROM categoria)`, e `corrected_category_id` recebe a categoria quando há correção ou fica nulo quando não há. Salvar de novo recalcula os dois campos.
- **Aplicar:** antes da chamada à IA, as linhas classificáveis cujo padrão existe para o usuário e aponta para uma categoria filha ativa recebem essa categoria, `classification_source = PATTERN` e confiança 100 (Q1). As parcelas futuras do mesmo grupo e da mesma importação herdam o resultado, como na 5.8 (1A). O `last_used_at` do padrão é atualizado. Essas linhas não vão para a IA, não entram em `classification_total` e não geram linha de histórico. Se todas casarem, a IA não é chamada.
- Um padrão cuja categoria foi removida ou virou grupo é ignorado, e a linha vai para a IA.
- **Isolamento:** toda consulta e toda escrita filtra por `user_id`.
- **LGPD:** a tabela entra no export (chave `user_classification_patterns`, com o teste do conjunto de chaves) e no purge, antes de `categories` e de `users`.
- Os logs mostram só ids e contagens, nunca o padrão nem a descrição.

**Never:**
- Chamar a IA para linhas que casaram com um padrão.
- Casamento aproximado (similaridade, prefixo).
- Mudar a tela de revisão: a linha `PATTERN` cai no grupo da sua confiança.
- Editar data, descrição ou valor (5.12).

## I/O & Edge-Case Matrix

| Cenário | Entrada / estado | Resultado |
|---|---|---|
| Correção | IA sugeriu Mercado e o usuário escolhe Restaurante | histórico `was_corrected = true`, `corrected = Restaurante`; padrão gravado com Restaurante |
| Mesma categoria | o usuário salva a sugestão da IA | `was_corrected = false`, `corrected` nulo; `usage_count` + 1 se o padrão já tinha essa categoria (Q2) |
| Linha sem histórico | "Não classificado" (IA fora do ar) recebe uma categoria | nenhum histórico muda; padrão gravado (Q2) |
| Normalização | "UBER *TRIP 12/03 PARC 02/05" e "Uber Trip 15/04" | os dois viram "uber trip" |
| Importação seguinte | descrição casa com o padrão | `PATTERN`, sem chamada à IA; as futuras do grupo herdam |
| Categoria removida | o padrão aponta para uma categoria apagada | a linha vai para a IA |
| Outro usuário | o padrão "uber trip" é do usuário A | a importação do usuário B não o usa |
| Lote com descrições iguais | 3 linhas "uber trip" para Transporte | um padrão só |
| Padrão vazio | descrição "123456" | não aprende, vai para a IA |
| Só termos genéricos | "PIX 98765", "PAGAMENTO BOLETO 3421" | não aprende nem casa, vai para a IA (Q4) |
| Termo genérico com nome | "PIX TRANSF JOAO SILVA" | padrão `pix transf joao silva` (Q4) |

## Decisões

- **Q1 — confiança (Luiz, 2026-11-09):** linha `PATTERN` recebe confiança 100 e cai em Alta.
- **Q2 — o que ensina (Luiz, 2026-11-09):** toda categoria salva pelo usuário, seja correção, confirmação ou linha sem histórico. Se o padrão já tem a mesma categoria, `usage_count` + 1; se não existe ou tinha outra, a categoria nova é gravada com contador 1.
- **Q3 — quando (Luiz, 2026-11-09):** ao salvar a categoria; descartar a importação não desfaz o padrão.
- **Q4 — termos genéricos (Luiz, 2026-11-09):** um padrão formado só por termos bancários genéricos (pix, ted, doc, transf, transferencia, pagamento, pagto, boleto, compra, debito, credito, enviado, enviada, recebido, recebida, tarifa, saque, deposito e afins, já sem acento) é tratado como vazio: não aprende nem casa, e a linha vai para a IA. O padrão guardado continua sendo a descrição normalizada inteira.

</frozen-after-approval>

## Code Map

Prefixos: `B` = `porquinho-backend/src/main/java/com/porquinho`, `T` = `porquinho-backend/src/test/java/com/porquinho`, `R` = `porquinho-backend/src/main/resources/db/migration`.

- `R/V28__create_ai_classification_history.sql` -- modelo da V30 (FKs, `TIMESTAMP WITH TIME ZONE`, comentários). Os testes usam Postgres via Testcontainers, então `ON CONFLICT` funciona.
- `B/service/TransactionImportReviewService.java:172` `writeCategory` -- o único ponto que grava a categoria do usuário. O aprendizado e o histórico entram aqui; a 5.12 vai chamar o mesmo serviço.
- `B/service/TransactionClassificationService.java:93` `classifyBatches` -- aplicar os padrões antes de `startClassification`. Usar `addClassified(importId, 0, now)` como guarda de `PROCESSING` dentro da transação dos padrões. `categoryChoices` (:135) já carrega as categorias filhas ativas.
- `B/repository/TransactionRepository.java:69,81` `applyAiClassification(ToFutures)` -- receber a origem como parâmetro em vez de duplicar as queries.
- `B/repository/AiClassificationHistoryRepository.java` -- incluir o update da correção por `transaction_id IN :ids AND user_id`.
- `B/service/InstallmentDetector.java:46` `detect` -- a `baseDescription` já vem sem o marcador.
- `B/service/DataExportService.java:220,460` e `B/service/AccountDeletionService.java:371` -- modelos de export e purge.
- Novos: `B/entity/UserClassificationPattern.java`, `B/repository/UserClassificationPatternRepository.java` (upsert nativo e busca dos padrões ativos do usuário, com join em categoria filha ativa), `B/service/ClassificationPatternService.java` (`normalize`, `learn`, `match`).
- **Não mexer:** o frontend, `InstallmentPlanner`, `TransactionImportProcessor`.

## Tasks & Acceptance

**Execution:**
- [x] `R/V30__create_user_classification_patterns.sql` -- tabela, índice único e comentários.
- [x] `B/entity/UserClassificationPattern.java`, `B/repository/UserClassificationPatternRepository.java` -- entidade, upsert, busca e `deleteByUserId`.
- [x] `B/service/ClassificationPatternService.java` -- normalizar, aprender (histórico e padrão) e montar o mapa padrão → categoria.
- [x] `B/repository/AiClassificationHistoryRepository.java`, `TransactionRepository.java` -- marcar a correção e receber a origem como parâmetro.
- [x] `B/service/TransactionImportReviewService.java` -- `writeCategory` chama o aprendizado.
- [x] `B/service/TransactionClassificationService.java` -- aplicar os padrões antes da IA.
- [x] `B/service/DataExportService.java`, `AccountDeletionService.java` -- export e purge.
- [x] Testes: normalização (tabela da matriz), `ClassificationPatternServiceTest`, matriz no `TransactionClassificationServiceTest` (sem chamada à IA quando tudo casa, categoria removida, outro usuário), upsert em integração, `TransactionImportReviewServiceTest` (histórico), export e purge.
- [x] `epic-5-context.md` e o épico -- registrar as decisões Q1–Q3.

**Acceptance Criteria:**
- Given uma correção salva na revisão, when envio na semana seguinte um extrato com a mesma descrição (outra data, outro valor), then a linha chega à revisão com a categoria corrigida, e o classificador não recebeu essa linha.
- Given a exclusão da conta, when o purge roda, then nenhum padrão do usuário sobra; e o export traz a chave `user_classification_patterns`.

## Implementation Notes

- `applyAiClassification(ToFutures)` recebe a origem (`AI` ou `PATTERN`); o nome ficou por compatibilidade.
- Se a gravação dos padrões falhar, todas as linhas vão para a IA, como antes da 5.11; uma importação que saiu de `PROCESSING` não recebe padrão nem IA.
- O upsert ordena os padrões, para que dois salvamentos simultâneos travem as linhas na mesma ordem.

## Spec Change Log

- **Revisão 1 (Luiz, 2026-11-09):** o achado 1 (padrão genérico como `pix` classifica toda linha parecida com confiança 100) foi levado ao humano, que escolheu a lista de termos genéricos (Q4, no bloco congelado). O código não foi revertido, porque a regra só acrescenta uma checagem ao `normalize`/`learn`/`match`. KEEP: toda a implementação da primeira passada.

## Review Triage Log

| # | Camada | Achado | Veredito | Rota | Evidência |
|---|---|---|---|---|---|
| 1 | blind | Descrição genérica ("PIX 98765", "TED 001", "PAGAMENTO BOLETO 3421") vira padrão curto ("pix", "ted", "pagamento boleto") e classifica toda linha parecida com confiança 100 | medium | intent_gap, resolvido: Q4 (opção B) | `normalize` tira todos os dígitos, como a spec manda; não existe piso de tamanho, e a Q1 põe a linha em Alta. |
| 2 | blind | Edição em lote ensina todas as descrições | false | rejeitado | É a decisão Q2: toda categoria salva ensina. |
| 3 | blind | `usage_count` nunca é lido | false | rejeitado | A coluna e o incremento são pedidos pela AC; usar o contador não está na intent. |
| 4 | blind | `last_used_at` sem uso | false | rejeitado | Coluna pedida pela AC; expiração não está na intent. |
| 5 | blind | Falha ao aprender desfaz a troca de categoria | low | rejeitado | Só uma falha de banco chega lá, e ela também derrubaria a própria troca; a correção é um `REQUIRES_NEW` com try/catch. |
| 6 | blind, edge | Deadlock entre `touch` e `learn` | low | rejeitado | Exige o mesmo usuário revisando uma importação enquanto outra é classificada com os mesmos padrões; quem perde recebe 500 e pode tentar de novo. A correção é uma trava nova. |
| 7 | blind | A correção de uma linha `PATTERN` não vai para o histórico | false | rejeitado | O histórico guarda só respostas da IA (V28); o padrão é atualizado pelo upsert. |
| 8 | blind | Não há tela para ver ou apagar padrões | false | rejeitado | A intent proíbe mudar a tela; é funcionalidade nova. |
| 9 | blind, gap | Falta teste da volta para a IA quando a gravação dos padrões falha | gap | patch | Nenhum teste faz `touch` ou a escrita lançar exceção. |
| 10 | edge, gap | `match()` fica fora do try: se a leitura falhar, a importação inteira fica sem classificação | medium | patch | `applyPatterns` chama `patternService.match` antes do `try`, e `classify` engole a exceção. |
| 11 | blind | O nome `applyAiClassification` engana, porque também grava `PATTERN` | low | patch | Renomear é direto, e todos os chamadores já estão no diff. |
| 12 | blind | Teste troca o campo `userId` no meio | low | patch | Uma variável local resolve. |
| 13 | edge | Categoria removida entre `match` e a escrita | low | rejeitado | Janela de milissegundos; a correção é uma guarda nova na escrita. |
| 14 | gap | `last_used_at` só é verificado por mock | gap | patch | Uma asserção no teste de integração fecha. |

## Verification

**Commands:**
- `cd porquinho-backend && ./mvnw test` -- expected: 0 falhas (JDK 21, Docker no ar)
