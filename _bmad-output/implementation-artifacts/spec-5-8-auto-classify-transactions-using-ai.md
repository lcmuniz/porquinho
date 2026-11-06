---
title: 'Story 5.8 — Classificar automaticamente as transações importadas com IA'
type: 'feature'
created: '2026-11-06'
baseline_commit: '80fb0027135fe6f2ddb4d8a89f44aebb8cb75e61'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-5-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** toda transação importada chega à revisão sem categoria, e o usuário teria de classificar uma a uma. Isso quebra o ritual semanal, em que a IA resolve a maior parte e o usuário só revisa as exceções.

**Approach:** depois de gravar as linhas, ainda em `PROCESSING` e fora do advisory lock, o backend manda as transações em lotes de até 100 para o Claude Haiku 5.5, grava categoria e confiança, mostra o progresso "X de Y" e só então passa a importação para `REVIEW`. Qualquer falha da IA deixa as linhas "Não classificado" e a importação chega à revisão do mesmo jeito.

## Boundaries & Constraints

**Always:**
- **Modelo e cliente:** `claude-haiku-5-5` pelo SDK oficial `com.anthropic:anthropic-java` (3.6.0), com structured outputs, thinking desligado e effort baixo. A chave fica só no backend (`ANTHROPIC_API_KEY`). Sem chave, tudo se comporta como IA fora do ar.
- **Quem vai para a IA:** linhas da importação com `kind = REGULAR` e `is_future_installment = false`. `CARD_PAYMENT` não vai e fica sem categoria e sem confiança.
- **Conteúdo enviado:** descrição com CPF e CNPJ mascarados (pontuados ou só dígitos), valor, tipo, destino (conta ou cartão) e as categorias ativas filhas do usuário (id, nome, grupo). Grupos não são categoria de transação. Sem categoria filha, não há chamada e as linhas ficam "Não classificado".
- **Resposta:** por transação, uma categoria da lista (ou nenhuma) e confiança de 0 a 100. Categoria fora da lista é descartada. Índice desconhecido ou repetido, ou confiança fora de 0–100, descarta aquele item.
- **Aplicação:** confiança ≥ 80 grava `category_id`, `classification_source = AI` e a confiança. Abaixo de 80 grava o mesmo, e a sugestão fica em `category_id`; "sinalizada para revisão" é `classification_confidence < 80`, sem coluna nova.
- **Não classificado:** `category_id` nulo, `classification_source = NONE`, confiança 0. Vale para lote com erro, timeout, 429, `refusal`, `max_tokens` ou resposta inválida, e para item sem categoria. Um lote com falha não interrompe os seguintes.
- **Histórico:** tabela `ai_classification_history` (V28) com id, user_id, transaction_id, predicted_category_id, confidence, was_corrected, corrected_category_id, created_at. Uma linha por transação que a IA respondeu. Entra no export e no purge, antes de `transactions`.
- **Progresso:** colunas `classification_total` e `classified_count` em `transaction_imports` (V28), expostas em `TransactionImportResponse`. Y = linhas enviadas à IA. X sobe a cada lote concluído, com sucesso ou não. Enquanto `PROCESSING` com Y > 0, a lista mostra "Classificando transações com IA... X de Y".
- **Fluxo:** a transação com lock grava linhas e contadores e mantém `PROCESSING`. A classificação roda depois do commit. No fim, update com guarda `PROCESSING → REVIEW`. Sem linhas novas continua indo direto para `COMPLETED` (5.5 – 1A).
- **Órfãs:** a varredura e a subida passam a usar `updated_at`, atualizado a cada lote. Uma órfã com `imported_transactions > 0` já tem linhas gravadas e vai para `REVIEW`, não para `FAILED`; sem linhas continua `FAILED`.
- **Privacidade:** `PrivacyView` cita a Anthropic como operadora das descrições das transações.
- Log e auditoria só com ids e contagens.

**Never:**
- Chamada de rede dentro da transação com lock.
- Descrição, categoria sugerida ou resposta do modelo em log ou auditoria.
- Mudar `description` ou `original_description` gravadas: a máscara vale só para o envio.
- Reclassificar a parcela futura confirmada por esta importação (ela continua com a importação que a criou, 5.7 – 2A).
- Padrões do usuário: `user_classification_patterns` não existe e a 5.11 está em backlog. A 5.11 passa a aplicá-los antes da chamada à IA.
- Tela de revisão (5.9), `first_import_completed` (5.10), métrica de precisão (Epic 10).

## I/O & Edge-Case Matrix

| Cenário | Entrada / estado | Resultado |
|---|---|---|
| Alta confiança | IA devolve Mercado, 92 | categoria aplicada, `AI`, 92, histórico |
| Baixa confiança | IA devolve Lazer, 60 | sugestão em `category_id`, `AI`, 60, histórico |
| Sem categoria | IA devolve nenhuma, 30 | `NONE`, confiança 0, histórico com categoria nula |
| Categoria fora da lista | id que não foi enviado | item descartado: `NONE`, 0, sem histórico |
| Item faltando | IA responde 99 de 100 | a linha faltante fica `NONE`, 0 |
| 250 transações | 3 lotes | 10 chamadas (4 + 4 + 2); X vai a 100, 200, 250 |
| Segundo lote falha | timeout no lote 2 de 3 | lotes 1 e 3 aplicados; lote 2 `NONE`, 0; `REVIEW` |
| Sem chave | `ANTHROPIC_API_KEY` vazia | nenhuma chamada; todas `NONE`, 0; `REVIEW` |
| Só pagamento de fatura | todas `CARD_PAYMENT` | nenhuma chamada, Y = 0, `REVIEW` |
| CPF na descrição | `PIX 123.456.789-09` ou `12345678909` | o envio leva `***`; a linha gravada não muda |
| Backend cai na classificação | `PROCESSING`, `imported_transactions > 0` | na subida vai para `REVIEW`, restantes sem classificação |
| Importação longa | lotes atualizam `updated_at` | a varredura não a derruba |
| Parcela com futuras | `… 03/10` classificada como Casa, 88; 7 futuras na importação | as 7 herdam Casa, `AI`, 88, sem chamada e sem histórico |
| Parcela sem resposta | lote da `… 03/10` falhou | as futuras ficam `NONE`, 0 |
| Sem nada novo | só duplicatas | `COMPLETED`, nenhuma chamada |

## Decisões

- **1A — parcelas futuras (Luiz, 2026-11-06):** as futuras criadas pela importação herdam categoria, origem e confiança da parcela do arquivo no mesmo grupo e na mesma importação. Não vão para a IA, não contam em Y e não geram histórico.
- **2A — NFR7 (Luiz, 2026-11-06, revista depois da medição):** com a chave real, 100 transações numa chamada levaram de 5,5 a 6,7 s. Cada lote de 100 passa a sair em 4 chamadas simultâneas de até 25. Os lotes continuam sequenciais, e o progresso e a transação continuam por lote. Uma chamada com falha deixa só as suas linhas "Não classificado".
- **Tamanho (Luiz, 2026-11-06):** a spec fica inteira, sem dividir (como na 5.6 e na 5.7).

</frozen-after-approval>

## Code Map

Prefixos: `B` = `porquinho-backend/src/main/java/com/porquinho`, `T` = `porquinho-backend/src/test/java/com/porquinho`, `F` = `porquinho-frontend/src`.

- `B/service/TransactionImportProcessor.java` -- `process` (:102), `parseAndSave` (:145), transação com lock (:164-205), guarda (:184-193), `catch` (:134), varreduras `failImportsInterruptedByRestart` (:317) e `failStaleImports` (:327), `ORPHAN_AGE` (:64). A classificação entra depois de :205.
- `B/repository/TransactionImportRepository.java` -- `markReadyForReview` (grava contadores e muda status; separar), `markCompletedWithNothingNew`, `failProcessingCreatedBefore` (:112; trocar por `updated_at` e dividir entre `REVIEW`/`FAILED`).
- `B/entity/Transaction.java` -- `category_id` (:72), `classification_source` (:76), `classification_confidence` (:79), `kind` (:69), `is_future_installment` (:109). Colunas já existem (V26).
- `B/entity/TransactionImport.java` -- contadores; somar as duas colunas novas. `B/dto/TransactionImportResponse.java` (:12-29) tem `new` posicional em `T/controller/TransactionImportControllerTest.java:71,156`.
- `B/repository/CategoryRepository.java` -- `findActiveByUserId` (:25); filhas têm `parentCategoryId`, o nome do grupo sai em memória. Não usar `findActiveByUserIdForUpdate`.
- `B/service/PixDetector.java` -- modelo de detector puro; `maskFullCpf` (:137) só cobre CPF pontuado, por isso a máscara nova.
- `B/service/SupabaseAdminClient.java` -- modelo de cliente externo com `@Value("${...:}")` e chave opcional. Nenhum `@ConfigurationProperties` no projeto.
- `B/service/DataExportService.java` -- `exported_tables` (:214), bloco depois de `transactions` (:447). `B/service/AccountDeletionService.java` -- `purgeUser`, antes de :367; Javadoc :59-62.
- Testes que mudam: `T/service/TransactionImportProcessorTest.java:488-498`, `T/service/TransactionImportIntegrationTest.java:687+`, `T/service/DataExportServiceTest.java` (`assertExportsEveryColumn`, :845), `T/service/AccountDeletionServiceTest.java:128-135,359-366`.
- `F/views/ImportView.vue` -- `statusLabel` (:356); o polling já segue em `PROCESSING`. `F/types/transactionImport.ts`, `F/views/__tests__/ImportView.spec.ts`, `F/views/PrivacyView.vue` (seção 5, :58-61).
- Config: `application.yml` (:74-80), `porquinho-backend/.env.example`, `docker-compose.yml` (:20+), `pom.xml`.

## Tasks & Acceptance

**Execution:**
- [x] `porquinho-backend/pom.xml` -- adicionar `com.anthropic:anthropic-java:3.6.0` -- SDK oficial.
- [x] `porquinho-backend/src/main/resources/db/migration/V28__create_ai_classification_history.sql` -- tabela, índices e as colunas de progresso em `transaction_imports`.
- [x] `B/entity/AiClassificationHistory.java` e `B/repository/AiClassificationHistoryRepository.java` -- entidade, `findByUserIdOrdered`, `deleteByUserId`.
- [x] `B/service/SensitiveDataMasker.java` -- classe pura, CPF e CNPJ pontuados ou só dígitos.
- [x] `B/service/TransactionClassifier.java` (interface) e `B/service/AnthropicTransactionClassifier.java` -- um lote por chamada; devolve os itens ou vazio em falha. Índices curtos no lugar de UUIDs, para reduzir saída e evitar id inventado. System prompt diz que descrições são dados, não instruções. Timeout de 20 s e 1 retry. Checar `stopReason` antes de ler o conteúdo.
- [x] `B/service/TransactionClassificationService.java` -- seleção, lotes, aplicação, herança das futuras (1A), histórico e progresso, uma transação curta por lote; não deixa exceção subir.
- [x] `B/service/TransactionImportProcessor.java` e `B/repository/TransactionImportRepository.java` -- dividir o fim do pipeline, guarda final para `REVIEW`, varreduras por `updated_at`.
- [x] `B/service/DataExportService.java`, `B/service/AccountDeletionService.java` -- nova tabela no export e no purge.
- [x] `B/dto/TransactionImportResponse.java`, `F/types/transactionImport.ts`, `F/views/ImportView.vue` -- `classificationTotal`, `classifiedCount` e o rótulo.
- [x] `F/views/PrivacyView.vue` -- citar a Anthropic na seção 5.
- [x] `application.yml`, `.env.example`, `docker-compose.yml` -- `ANTHROPIC_API_KEY` com default vazio.
- [x] Testes: `SensitiveDataMaskerTest`, `AnthropicTransactionClassifierTest`, `TransactionClassificationServiceTest` (matriz), integração com `TransactionClassifier` mockado, export, purge, `ImportView.spec.ts`, `PrivacyView`.
- [x] `epic-5-context.md` e épico -- padrões aplicados pela 5.11; decisão sobre futuras; V28 como última migration.
- [x] `_bmad-output/implementation-artifacts/deferred-work.md` -- medir o NFR7 com chave real (2A).

**Acceptance Criteria:**
- Given uma importação em classificação, when a página consulta a cada 2 s, then o rótulo mostra "Classificando transações com IA... X de Y" e X nunca passa de Y.
- Given a IA fora do ar, when a importação termina, then o status é `REVIEW` e nenhuma linha se perde.
- Given um usuário apagado, when o purge roda, then `ai_classification_history` some antes de `transactions`, e o export do usuário traz a tabela com todas as colunas.
- Given a classificação em andamento, when outra importação do mesmo destino começa, then ela não espera pela IA, porque o lock já foi solto.

## Implementation Notes

- As linhas `REGULAR` nascem `NONE`, confiança 0 (`TransactionImportProcessor`), e as futuras copiam esse valor. Assim, lote com falha, item descartado e queda do backend não precisam de escrita. `CARD_PAYMENT` nasce com confiança nula.
- Índice repetido descarta todos os itens daquele índice, porque não dá para saber qual deles vale.
- Sem chave, Y é o total de linhas elegíveis e X sobe sem chamada, como em lotes com falha. Sem categoria filha, Y = 0, porque nada é enviado.
- No histórico do item sem categoria fica a confiança devolvida pela IA (30, por exemplo). Na transação fica 0.
- A categoria segue para a IA com número curto a partir de 1; 0 significa "nenhuma". A tradução para UUID fica no serviço.
- Com duas parcelas do mesmo grupo no arquivo, as futuras ficam com a classificação da última respondida (as linhas vão por data).
- A escrita usa `UPDATE` só das colunas da classificação: uma importação paralela pode confirmar a futura, e salvar a entidade regravaria a linha inteira.
- O `application-test.yml` desliga o Flyway. A V28 foi conferida rodando `TransactionImportIntegrationTest` com `-Dspring.flyway.enabled=true -Dspring.jpa.hibernate.ddl-auto=validate`.

- Depois da revisão 1: um lote que falha no banco não avança X (o `addClassified` sofre rollback), então a importação pode chegar a `REVIEW` com X menor que Y. O índice UNIQUE de `transaction_id` só existe com Flyway, porque o perfil de teste padrão usa `create-drop`. O `@DynamicUpdate` de `Transaction` não tem teste próprio de corrida.
- Verificação final: backend 1224 testes, 0 falhas (JDK 21); frontend 629 testes, lint sem erros, `vue-tsc` limpo. Nenhuma chamada real à API da Anthropic (2A).

## Spec Change Log

- **Depois da entrega (2026-11-06):** medição com chave real reprovou o NFR7 (5,5–6,7 s por lote de 100) → o humano reviu a 2A para 4 chamadas simultâneas de 25. Emendado: decisão 2A e a linha "250 transações" da matriz. Medido depois da mudança, pelo `TransactionClassificationService` com o classificador real e repositórios simulados: 3,3 s na primeira rodada e 2,0–2,2 s nas três seguintes, 100 de 100 aplicadas. KEEP: lotes sequenciais, progresso e transação por lote.

## Review Triage Log

### Revisão 1 (2026-11-06, por subagentes)

| # | Camada | Achado | Veredito | Evidência | Rota |
|---|---|---|---|---|---|
| 1 | blind, edge | CNPJ alfanumérico e CPF com espaços não são mascarados | medium | Regex só aceita `\d`; o formato alfanumérico vale desde 2026-07 e a `PrivacyView` promete a máscara. | patch |
| 2 | edge, gap | CPF parcial `•••.432.198-••` vai à Anthropic | medium | Formato real de `nubank-conta.csv` (3 linhas), não casa com o regex. | patch |
| 3 | blind | Nome, agência e conta de terceiros vão na descrição do Pix | low | Real, mas o AC e a D5 só pedem CPF/CNPJ; apresentado ao humano no checkpoint, que aprovou. | rejeitado |
| 4 | blind | `PrivacyView` sem transferência internacional, retenção e aviso da seção 10; data da política é `new Date()` | medium | O AC foi cumprido; a data e a promessa da seção 10 já existiam. Decisão jurídica. | defer |
| 5 | blind | Nada mostra que a classificação falhou | low | A spec define X subindo com ou sem sucesso; a 5.9 mostra "Não classificado". Exige coluna nova, e IA fora do ar não é o dia a dia. | rejeitado |
| 6 | blind | Sem caminho de reclassificação | low | A intenção é ficar "Não classificado" e o usuário ajustar na revisão (5.10). | rejeitado |
| 7 | blind, edge | Confirmar futura em paralelo regrava a categoria herdada | low | `Transaction` sem `@DynamicUpdate`; `saveAll` regrava todas as colunas. Corrige com uma anotação. | patch |
| 8 | blind, edge | Futura confirmada depois do snapshot fica sem herança | low | Janela de segundos, duas importações do mesmo cartão; o resultado é o estado seguro "Não classificado". Corrigir pede ramo novo. | rejeitado |
| 9 | blind, edge | Erro de banco num lote encerra os seguintes | low | `transactionTemplate.execute` sem try/catch no laço; `nothingEscapes` só cobre a primeira consulta. | patch |
| 10 | blind, edge | Sem limite de linhas ou de custo; IA fora deixa arquivo grande muito tempo em `PROCESSING` | low | Pior caso cerca de 40 s por lote, com progresso andando; custo próximo de US$ 0,0015 por 100 linhas. Parar antes contraria "um lote com falha não interrompe os seguintes". | rejeitado |
| 11 | blind | Sem evento de auditoria do envio à Anthropic | low | A spec não pede evento, e o `log.info` traz as contagens. Nenhum dano nomeado. | rejeitado |
| 12 | blind | "Uma linha por transação" sem `UNIQUE` | low | Hoje não há reprocessamento, mas a V28 ainda não foi aplicada e a troca é direta; depois exigiria outra migration. | patch |
| 13 | edge | Categoria removida durante os lotes | low | Janela de segundos; categoria com soft delete em transação já é estado normal. Corrigir pede releitura por lote. | rejeitado |
| 14 | edge | Futura antiga com confiança nula, confirmada, nunca vai à IA | low | Não ir à IA é a 5.7 – 2A. A confiança nula de dados anteriores à 5.8 cabe à 5.9 tratar. | defer |
| 15 | gap | `TransactionImportResponse.of` sem teste dos campos novos | medium | Inverter as linhas compila e nenhum teste falha. | patch |
| 16 | gap | Guarda de `addClassified`/`startClassification` só com mock | medium | Apagar a cláusula não quebra nenhum teste. | patch |
| 17 | gap | Limites de `applyAiClassificationToFutures` não exercitados | medium | Só há um teste real, com uma importação e uma resposta. | patch |
| 18 | gap | Ligação de `anthropic.api-key` sem teste | medium | Erro de digitação deixa tudo sem classificar, só com WARN. A primeira execução com chave cobre. | defer |
| 19 | gap | `nothingNewIsNotClassified` testa outro caminho | low | O arranjo usa `recordParsed` devolvendo 0. Correção direta no teste. | patch |

## Design Notes

Dividir o commit é o custo de manter a rede fora do lock. Classificar antes de gravar manteria tudo atômico, mas pagaria IA por duplicatas e reenvios. Por isso as linhas são gravadas primeiro, e a órfã com linhas vai para `REVIEW`: se fosse `FAILED`, as linhas ficariam ativas e um reenvio do arquivo as veria como duplicatas.

O AC do épico traz dois textos de progresso. A tela usa só "Classificando transações com IA... X de Y", que é o da seção de UX.

## Verification

**Commands:**
- `cd porquinho-backend && ./mvnw test` -- expected: 0 falhas
- `cd porquinho-frontend && npm run lint && npx vue-tsc --build && npm run test:unit -- --run` -- expected: sem erros
