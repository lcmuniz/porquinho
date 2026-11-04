---
title: 'Processar o extrato em segundo plano e detectar o banco'
type: 'feature'
created: '2026-11-04'
status: 'done'
baseline_commit: 'a8a31c56e666abf1984b79f6a9d8c95e06ae0440'
route: 'dispatch'
review_loop_iteration: 0
story_key: 5-3-auto-detect-file-format-and-bank
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-5-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** A importação enviada na 5.1 fica parada em "Recebido": nada lê o arquivo, e as linhas antigas nunca saem de `PROCESSING`. Sem um processamento em segundo plano, a leitura das transações (5.4) não tem onde rodar.

**Approach:** Depois de gravar a importação, o backend passa o conteúdo (só em memória) para um processamento assíncrono que identifica o banco do OFX ou marca a importação como `FAILED` com uma mensagem clara. A tela `/importar` consulta o status a cada 2 s até o fim da etapa. Importações sem processamento ativo (envio interrompido ou anteriores a esta story) viram `FAILED`.

## Boundaries & Constraints

**Always:**
- Ordem de chamada (Story 5.2 adiada, decisão do Luiz em 2026-11-04): só OFX; a detecção de CSV e o layout de cada banco ficam para a 5.2.
- Banco pelo `BANKID` (zeros à esquerda ignorados); sem ele, `FID`; sem os dois, `ORG` por palavra-chave. Códigos gravados em `bank_name`: `NUBANK` (260), `BANCO_DO_BRASIL` (1), `ITAU` (341), `BRADESCO` (237), `CAIXA` (104). Precisão de 100% nas amostras OFX (NFR56 pede 95%).
- OFX sem `</OFX>` é truncado → `FAILED`, `error_message` "O arquivo está incompleto. Exporte o extrato de novo no site ou app do banco e envie outra vez."
- O conteúdo só existe em memória durante o processamento; nunca vai para disco, banco ou log. Logs só com id da importação e motivo, sem dado pessoal.
- O processamento começa só depois do commit da importação (nunca vê uma linha não gravada) e roda fora da thread da requisição.
- Importação `PROCESSING` sem processamento ativo: na subida do backend, todas as criadas antes da subida; e, a cada 5 min, as com mais de 15 min → `FAILED`, "O processamento foi interrompido. Envie o arquivo de novo."
- `GET /api/v1/imports/{id}` (só do dono; senão 404 "Importação não encontrada") para a consulta de status. A tela consulta a cada 2 s (NFR8) só enquanto houver importação na etapa e para ao sair da página.
- Mensagens em pt-BR; erros em `ProblemDetail`.
- **Decisão 1A (Luiz, 2026-11-04):** depois da detecção, a importação continua `PROCESSING` com `bank_name` gravado. A lista mostra "Banco detectado: <banco> — a leitura das transações chega na próxima etapa", e a consulta de 2 s para quando o banco aparece (ou quando o status sai de `PROCESSING`). A 5.4 estende o mesmo processamento.
- **Decisão 2A (Luiz, 2026-11-04):** OFX de banco fora dos 5 segue com `bank_name = OTHER`, exibido como "Banco não identificado". A escolha manual do banco ("Detectamos X. Está correto?") vai para a 5.2.

**Never:**
- Ler os lançamentos ou criar `transactions` (5.4). Chamar IA ou serviço externo.
- Nova migration (os campos de `transaction_imports` bastam).

## I/O & Edge-Case Matrix

| Cenário | Entrada / Estado | Resultado | Erro |
|---|---|---|---|
| OFX de cada banco | as 5 amostras de conta + `itau-cartao.ofx` | `bank_name` com o código certo; status segue `PROCESSING` (1A) | — |
| Cartão sem BANKID | `itau-cartao.ofx` (só `FID` 341) | `ITAU` | — |
| BANKID e FID divergentes | BANKID 260, FID 341 | `NUBANK` (BANKID vence) | — |
| Banco desconhecido | BANKID 077, sem ORG conhecido | `OTHER`, "Banco não identificado" (2A) | — |
| OFX truncado | `erro-ofx-truncado.ofx` | `FAILED` + mensagem de arquivo incompleto | — |
| Erro inesperado no processamento | exceção qualquer | `FAILED`, "Não foi possível processar o arquivo. Tente de novo." | log sem conteúdo |
| Backend reiniciado | importação `PROCESSING` criada antes da subida | `FAILED` "interrompido" | — |
| Status de importação alheia | `GET /imports/{id}` de outro usuário | 404 | — |

</frozen-after-approval>

## Code Map

- `service/TransactionImportService.java` -- `create` valida fora da transação e grava via `TransactionTemplate` (5.1); disparar o processamento depois do `execute` retornar (já commitado), passando os bytes lidos na validação.
- `config/AsyncConfig.java` -- `@EnableAsync`/`@EnableScheduling` com virtual threads. Usar `@Async` num bean **separado** (`service/TransactionImportProcessor.java`): `DataExportService.processExport` chama o próprio `@Async` por dentro e por isso roda síncrono — não copiar esse padrão.
- `entity/TransactionImport.java`, `repository/TransactionImportRepository.java` -- `status`, `bank_name` (VARCHAR 100), `error_message` (500) já existem; acrescentar consulta por id+usuário e o update em massa das órfãs.
- `controller/TransactionImportController.java`, `dto/TransactionImportResponse.java` -- `GET /{id}`.
- `service/DataExportService.java` `cleanupExpiredExports` -- molde de `@Scheduled`; subida via `ApplicationReadyEvent`.
- `porquinho-backend/src/test/resources/import-samples/` -- `erro-ofx-truncado.ofx` e as amostras OFX; BANKIDs: 0260, 001, 0341, 0237, 104; `itau-cartao.ofx` só tem `FID` 341.
- `porquinho-frontend/src/views/ImportView.vue`, `services/transactionImportService.ts`, `types/transactionImport.ts` -- rótulo do banco, consulta de 2 s com parada no `onUnmounted`, mensagem do `FAILED`.
- `_bmad-output/planning-artifacts/epics/epic-5-transaction-import-ai-classification.md` -- Story 5.3: CSV e escolha manual do banco vão para a 5.2 (2A); registrar a decisão 1A e a ordem 5.3 antes da 5.2.

## Tasks & Acceptance

**Execution:**
- [x] `service/OfxBankDetector.java` -- banco e truncamento a partir dos bytes (sem parser completo) -- regra isolada e testável com as amostras
- [x] `service/TransactionImportProcessor.java` -- `@Async`: detectar, gravar `bank_name` ou `FAILED`; varredura das órfãs na subida e a cada 5 min
- [x] `TransactionImportService`, `TransactionImportRepository` -- disparar após o commit; `findByIdAndUserId`; update das órfãs
- [x] `TransactionImportController` -- `GET /api/v1/imports/{id}`
- [x] Frontend -- `getImport(id)`, consulta de 2 s, rótulos de banco e de falha
- [x] Épico -- texto da 5.3 conforme 1A/2A e a ordem 5.3 antes da 5.2
- [x] Testes -- detector (todas as amostras e as linhas da matriz), processador (sucesso, truncado, exceção, órfãs), controller (`GET /{id}` 200/404), integração Testcontainers (upload → banco gravado), `ImportView.spec.ts` (consulta para ao detectar, ao falhar e ao desmontar)

**Acceptance Criteria:**
- Given um `nubank-conta.ofx` enviado, when o processamento termina, then a lista mostra o banco detectado sem recarregar a página e a consulta de 2 s para.
- Given importações `PROCESSING` antigas no banco de dev, when o backend sobe, then todas viram `FAILED` com a mensagem de interrupção.

## Implementation Notes

- A varredura das órfãs (subida e a cada 5 min) só pega importações `PROCESSING` **sem** `bank_name`. Com banco gravado, a etapa terminou e a importação aguarda a leitura (1A). **Para a 5.4:** quando a leitura rodar depois da detecção, a varredura precisa de outro critério de "processamento ativo" (por exemplo `updated_at` ou um estado próprio), senão uma leitura interrompida depois da detecção nunca vira `FAILED`.
- `BANKID` presente decide sozinho: fora dos 5 vira `OTHER`, mesmo com `FID` ou `ORG` conhecido ("sem ele, FID").
- Premissa de instância única do backend. Com várias réplicas ou deploy rolling, a varredura da subida pode derrubar uma detecção em andamento em outra instância. Hoje a janela é de milissegundos. Rever na 5.4, quando a leitura for longa.

## Spec Change Log

## Review Triage Log

| # | Origem | Achado | Veredito | Evidência | Destino |
|---|---|---|---|---|---|
| 1 | edge, blind | Varredura de 15 min e da subida derruba importação já detectada (1A) como "interrompido" | high | `failProcessingCreatedBefore` filtrava só `status` e `createdAt`; toda detecção virava `FAILED` em ≤ 20 min ou no reinício | patch: `AND bankName IS NULL` + testes de integração com linha detectada antiga |
| 2 | edge, blind | Tela não percebe quando importação detectada vira `FAILED` depois | false | Com o patch 1 nenhuma importação com banco sai de `PROCESSING` nesta story | rejeitado |
| 3 | blind | `BANKID` desconhecido não decide; `FID`/`ORG` de outro banco vence | low | `detectBank` encadeava `or()`: BANKID 077 + FID 341 dava `ITAU`, contra "sem ele, FID" | patch: BANKID presente decide sozinho + teste |
| 4 | edge, blind | Varredura da subida com várias instâncias derruba processamento de outra | low | Deploy atual é instância única (`docker-compose.yml`); após o patch 1 a janela é só a detecção (ms) | rejeitado; nota para a 5.4 nas Implementation Notes |
| 5 | edge, blind | Em `create`, se `markFailed`/`get` lançam após falha ao disparar, o upload commitado devolve 500 | low | Exige executor rejeitando (virtual threads não rejeitam) e banco caindo em seguida | rejeitado |
| 6 | edge, blind | Polling sem limite quando `getImport` falha com erro ≠ 404 | low | Só enquanto a página está aberta com importação na etapa; correção acrescenta contador/backoff | rejeitado |
| 7 | edge | `</OFX>` dentro de um MEMO faz arquivo cortado passar como completo | low | Exige o texto literal `</OFX>` no meio de um extrato cortado; raro no uso real | rejeitado |
| 8 | blind | Teste das órfãs fraco: sem asserção na 1ª chamada, varredura periódica não testada no positivo | medium | `processingImportsFromBeforeStartupAndStaleOnesFail` só via a linha de 1 dia já derrubada pela subida | patch: teste reescrito (linha de 20 min inserida após a subida vira `FAILED`; 1 min e detectada ficam) |
| 9 | blind | `TaskRejectedException` é código morto com virtual threads; sem teto de memória para tarefas | low | O catch é defensivo e barato; o `byte[]` já estava na memória da requisição | rejeitado |
| 10 | blind | Épico perdeu os ACs de assinatura de conteúdo e arquivo vazio | low | 5.1 já rejeita arquivo vazio e conteúdo não-OFX (`TransactionImportService` linhas 90 e `statementKind`), mas o épico não dizia | patch: linha no épico apontando a 5.1 |
| 11 | blind | Spec `in-review` e sprint-status `in-progress` | false | Fluxo normal: o step-05 atualiza o sprint-status | rejeitado |
| 12 | blind | `GET /{id}` com id não-UUID não devolve `ProblemDetail` | low | Mesmo comportamento de todos os `/{id}` existentes (nenhum handler de `MethodArgumentTypeMismatchException`); só via chamada manual | rejeitado |
| 13 | blind | `get` usa `findById` para o nome do destino e a lista usa outra consulta | false | As duas mostram o nome do destino da importação do próprio usuário; destino excluído aparece nas duas | rejeitado |
| 14 | verification-gap | Nenhum teste fixa processamento após o commit e fora da thread da requisição | medium | Pré-verificado pela camada | patch: `InOrder` commit → `process` no unitário; integração confere proxy AOP e `@Async` em `process` |
| 15 | verification-gap | Varredura não testada pelo gatilho real (`ApplicationReadyEvent`, `@Scheduled`) | medium | Pré-verificado pela camada | patch: integração publica `ApplicationReadyEvent` e confere `failStaleImports` entre as tarefas agendadas |

## Verification

**Commands:**
- `cd porquinho-backend && ./mvnw test` -- 0 falhas
- `cd porquinho-frontend && npx vitest run && npx vue-tsc --build && npm run lint` -- tudo verde

**Manual checks:**
- Com backend e frontend locais: enviar `nubank-conta.ofx` e `itau-cartao.ofx`; ver o banco aparecer na lista em até 2 s; enviar `erro-ofx-truncado.ofx` e ver a falha com a mensagem.
