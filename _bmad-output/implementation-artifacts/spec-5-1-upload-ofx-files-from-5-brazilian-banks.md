---
title: 'Enviar extrato OFX para uma conta ou cartão'
type: 'feature'
created: '2026-11-04'
status: 'done'
baseline_commit: 'e6fd7afc3feec0b7712cb9ba3c93685283ce16c3'
route: 'dispatch'
review_loop_iteration: 0
story_key: 5-1-upload-ofx-files-from-5-brazilian-banks
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-5-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** O usuário não tem como trazer o extrato do banco para o porquinho. Sem isso, nada do Epic 5 (leitura, duplicatas, IA) tem por onde começar.

**Approach:** Página `/importar` em que o usuário escolhe a conta ou o cartão de destino (D2) e envia um arquivo `.ofx`. O backend valida o arquivo e o destino e registra a importação na tabela nova `transaction_imports`.

## Boundaries & Constraints

**Always:**
- Destino: exatamente um entre `accountId` e `creditCardId`, e precisa ser conta ou cartão ativo do usuário do JWT. Conta ou cartão de outro usuário, ou removido, → 404, como se não existisse.
- Limite de 10MB no frontend (antes de enviar), no Spring (multipart) e no nginx da raiz (`client_max_body_size`, que hoje é 1MB por padrão).
- Validação pelo conteúdo, não só pela extensão: o arquivo precisa começar (depois de BOM e espaços) com `OFXHEADER:` ou `<?xml` e conter `<OFX>`. O tipo do extrato precisa bater com o destino: `<CCSTMTRS>` é cartão e `<STMTRS>` é conta.
- O conteúdo do arquivo não é gravado no banco nem em log. De `file_name` guarda-se só o nome, sem caminho, com no máximo 255 caracteres.
- Migration nova na próxima versão livre (V25). `transaction_imports` entra no export LGPD (com teste do conjunto de chaves) e no purge, antes de `accounts` e `credit_cards`.
- Audit `import_created` com `import_id`, `file_type` e `file_size_kb` (sem nome de arquivo).
- Mensagens em pt-BR. Erros em `ProblemDetail`.
- **Decisão 1A (Luiz, 2026-11-04):** a importação é registrada com status `PROCESSING` e nada roda em segundo plano ainda. A lista em `/importar` mostra esse status como "Recebido — a leitura das transações chega na próxima etapa". O processamento assíncrono e o armazenamento temporário do arquivo nascem na Story 5.3.
- **Decisão 2A (Luiz, 2026-11-04):** o passo 6 do onboarding (`ImportStep.vue`) não muda. O upload é ligado a ele na Story 5.10, e o texto do épico é ajustado nesta story (retro do Epic 2, P1).

**Never:**
- Ler os lançamentos, detectar o banco, CSV, duplicatas, IA (Stories 5.2–5.8).
- Chamar a IA ou qualquer serviço externo.
- Guardar o arquivo depois da requisição.

## I/O & Edge-Case Matrix

| Cenário | Entrada / Estado | Resultado | Erro |
|---|---|---|---|
| OFX de conta para conta | `nubank-conta.ofx`, conta ativa do usuário | 201, importação `PROCESSING` (`file_type=OFX`, `bank_name` nulo) | — |
| OFX de cartão para cartão | `itau-cartao.ofx`, cartão ativo | 201 | — |
| Cartão para conta (ou o contrário) | `itau-cartao.ofx` para uma conta | 400 "Este arquivo é de cartão de crédito. Escolha um cartão como destino." (e o inverso para conta) | nada gravado |
| Arquivo > 10MB | 10MB + 1 byte | 400 "Arquivo muito grande (máximo 10MB)" | frontend barra antes |
| Extensão errada | `.csv`, `.pdf` | 400 "Formato de arquivo não suportado" | — |
| .ofx que não é OFX | `erro-nao-e-extrato.ofx`, arquivo vazio | 400 "Formato de arquivo não suportado" | — |
| Destino ausente ou duplo | nenhum, ou conta e cartão juntos | 400 | — |
| Destino de outro usuário ou removido | id válido | 404 "Conta não encontrada" / "Cartão não encontrado" | nada gravado |
| Sem assinatura ativa | trial vencido | 402 (filtro existente) | — |

</frozen-after-approval>

## Code Map

- `porquinho-backend/src/main/resources/db/migration/` -- última é `V24__add_version_to_accounts.sql`; criar `V25__create_transaction_imports_table.sql` no padrão da V22 (`id UUID DEFAULT gen_random_uuid() PRIMARY KEY` nessa ordem por causa do H2, `COMMENT ON`, índice `(user_id, created_at DESC)`, CHECK de status `PROCESSING/REVIEW/COMPLETED/FAILED/DISCARDED`, CHECK `file_type IN ('OFX','CSV')`, CHECK de exatamente um destino, FKs para `users`, `accounts`, `credit_cards`)
- `porquinho-backend/src/main/java/com/porquinho/controller/TransferController.java` + `service/TransferService.java` -- molde de controller/service: usuário pelo `jwt.getSubject()`, IP por `getRemoteAddr()`, `TransactionTemplate`, audit, mensagens como constantes públicas
- `repository/AccountRepository.java:29` (`findByIdAndUserIdAndDeletedAtIsNull`) e o equivalente em `CreditCardRepository` -- checagem de destino ativo do usuário
- `exception/GlobalExceptionHandler.java` -- adicionar handler de `MaxUploadSizeExceededException` → 400 com a mensagem de tamanho; `BadRequestException`/`ResourceNotFoundException` já mapeados
- `src/main/resources/application.yml` -- `spring.servlet.multipart.max-file-size: 10MB` e `max-request-size: 11MB` (hoje padrão 1MB)
- `nginx.conf` (raiz) -- `location /api/` sem `client_max_body_size`; definir `11m`
- `service/DataExportService.java:202` (`exported_tables`) e `service/AccountDeletionService.java:345-364` (ordem do purge) -- incluir a tabela; `DataExportServiceTest` testa cada tabela por `contains`; acrescentar teste de chaves da nova seção
- `porquinho-backend/src/test/java/com/porquinho/MigrationTest.java` -- incluir V25; `service/TransferIntegrationTest.java` -- molde de teste com Testcontainers
- `porquinho-backend/src/test/resources/import-samples/` -- amostras fictícias para os testes (OFX de conta, de cartão, e os `erro-*`)
- `porquinho-frontend/src/router/index.ts` -- rotas `/contas` e `/cartoes` como molde (`requiresAuth`); criar `/importar`
- `porquinho-frontend/src/views/DashboardView.vue:94-108` -- nav com links `/contas` e `/cartoes`; acrescentar "Importar extrato"
- `porquinho-frontend/src/services/transferService.ts` -- molde de serviço; `api.ts` tem `Content-Type: application/json` padrão, então o upload envia `FormData` com cabeçalho multipart e usa `onUploadProgress`
- `porquinho-frontend/src/services/accountService.ts`, `cardService.ts` -- listas de contas e cartões ativos para o seletor de destino
- `porquinho-frontend/src/components/onboarding/ImportStep.vue` -- não mexer nesta story (decisão 2A)
- `_bmad-output/planning-artifacts/epics/epic-5-transaction-import-ai-classification.md` -- Story 5.1: tirar o AC do passo 6 do onboarding e o "Story 5.3 runs after upload" em segundo plano; Story 5.10: acrescentar o AC do passo 6 (`first_import_completed`)

## Tasks & Acceptance

**Execution:**
- [x] `V25__create_transaction_imports_table.sql` + `entity/TransactionImport.java` + `repository/TransactionImportRepository.java` -- tabela, entidade e `deleteByUserId` nativo
- [x] `service/TransactionImportService.java` -- validar destino e arquivo (matriz), registrar a importação e o audit; listar as 20 mais recentes do usuário
- [x] `controller/TransactionImportController.java` -- `POST /api/v1/imports` (multipart: `file`, `accountId` ou `creditCardId`) → 201; `GET /api/v1/imports` → lista com nome do destino
- [x] `dto/TransactionImportResponse.java`, `GlobalExceptionHandler`, `application.yml`, `nginx.conf` -- resposta, erro de tamanho, limites
- [x] `DataExportService`, `AccountDeletionService` -- export e purge
- [x] Frontend: `types/transactionImport.ts`, `services/transactionImportService.ts`, `views/ImportView.vue` (seletor de destino com contas e cartões agrupados, área de arrastar e soltar com botão "Enviar arquivo", barra de progresso, mensagem "Arquivo enviado, processando...", erros do backend, lista das importações recentes com status), rota `/importar`, link no dashboard
- [x] `epics/epic-5-transaction-import-ai-classification.md` -- refletir as decisões 1A e 2A (Stories 5.1, 5.3 e 5.10)
- [x] Testes: unit do service (matriz toda, com as amostras), controller (multipart, 400/404/201), integração com Testcontainers (gravação, purge), `MigrationTest` (V25), export; `ImportView.spec.ts` (validação de tamanho e extensão no cliente, envio, erros, lista)

**Acceptance Criteria:**
- Given uma conta ativa, when envio `nubank-conta.ofx` em `/importar` escolhendo essa conta, then vejo "Arquivo enviado, processando..." e a importação aparece na lista com o nome do arquivo e o destino.
- Given um usuário com importações, when a conta é purgada, then não sobra linha em `transaction_imports` desse usuário e o export anterior incluía a seção `transaction_imports`.

## Implementation Notes

## Spec Change Log

## Review Triage Log

Revisão de 2026-11-04 (iteração 0): Blind Hunter (B), Edge Case Hunter (E), Verification Gap (V).

| # | Achado | Veredito | Evidência | Destino |
|---|---|---|---|---|
| B1 | Upload gravado em disco temporário (`file-size-threshold` padrão 0B) | medium | O Spring grava cada parte acima do threshold em `java.io.tmpdir`; um crash no meio da requisição deixa o extrato no disco, contra "não guardar o arquivo" | patch: `file-size-threshold: 11MB` |
| B2/E9 | Validação de até 10MB dentro de `@Transactional` e com duas cópias do conteúdo | medium | A conexão do pool ficava presa durante a leitura; `new String` + `toUpperCase` somavam ~20MB por envio | patch: validação antes do `TransactionTemplate`, busca das tags nos bytes, sem cópia |
| V1 | Limites do multipart sem teste (o `application.yml` de teste esconde o principal) | medium | Pré-verificado pela camada; remover o bloco não quebrava nenhum teste | patch: `MultipartLimitsConfigTest` lê o yml principal; o nginx continua no check manual |
| B4 | Importações em `PROCESSING` ficam órfãs até a 5.3 | medium | Consequência da decisão 1A: sem arquivo guardado, a 5.3 não tem o que processar nas linhas antigas | defer (deferred-work) |
| B3 | Upload sem rate limit | low | Rota autenticada e paga, já atrás do filtro de assinatura; a correção acrescenta um guard novo | rejeitado |
| B5/E3 | `max-swallow-size` do Tomcat reseta a conexão | false | O nginx barra acima de 11m antes do Tomcat; entre 10MB e 11MB o excesso fica abaixo dos 2MB de swallow | rejeitado |
| B6/E4 | 413 do nginx em HTML vira mensagem genérica | low | Inalcançável pela tela: o frontend barra acima de 10MB antes de enviar | rejeitado |
| B7/E5 | UUID inválido e multipart malformado sem `detail` em pt-BR | low | Só por cliente fora da tela, que manda ids das listas; a correção pede handlers novos | rejeitado |
| B8/E7 | Extrato do tipo errado para destino alheio dá 400 em vez de 404 | low | O 400 é igual exista ou não o destino, então não vaza nada e nada é gravado; a matriz trata o 404 com arquivo compatível | rejeitado |
| B9 | AC de purge sem teste de ponta a ponta | low | A ordem do `purgeUser` está no `AccountDeletionServiceTest` e as FKs no teste de integração, no mesmo padrão das Stories 4.5/4.6 | rejeitado |
| B10/E10 | `sanitizeFileName` deixa caracteres Unicode de formatação e pode cortar um par surrogate | low | É o nome do próprio arquivo do usuário, e nomes com mais de 255 caracteres e emoji no corte são raros | rejeitado |
| B11/E1/E2 | Arquivo solto durante o envio se perde; o destaque pisca no dragleave | low | Os botões ficam desabilitados durante o envio, que leva segundos; a correção acrescenta guards | rejeitado |
| B12/E11 | Um banco só no OFX de cartão; OFX truncado aceito; sem teste do NFR5 | false | O design note da spec define que não há parser completo nesta story; o OFX truncado é tratado na 5.3 ("Malformed files") | rejeitado |
| B13 | `listRecent` carrega todas as contas e cartões | low | O usuário tem poucas contas e cartões | rejeitado |
| B14 | Spec `in-review` e sprint `in-progress` | false | O workflow sincroniza o sprint-status no fechamento (step-05) | rejeitado |
| E6 | Conta removida em concorrência com o upload | low | Janela de milissegundos, e a importação fica numa conta removida sem efeito em saldo | rejeitado |
| E8 | OFX em UTF-16 é recusado | low | Os bancos brasileiros exportam em cp1252/UTF-8 (amostras e README) | rejeitado |

## Design Notes

Detecção do tipo de extrato: basta procurar `<CCSTMTRS>` ou `<STMTRS>` no conteúdo (cp1252 ou UTF-8, sem parser completo); a leitura real do OFX é da 5.3/5.4. Um OFX com os dois blocos é aceito só se o destino bater com o primeiro encontrado.

## Verification

**Commands:**
- `cd porquinho-backend && ./mvnw test` -- 0 falhas
- `cd porquinho-frontend && npx vitest run && npx vue-tsc --build && npm run lint` -- tudo verde

**Manual checks:**
- Enviar `nubank-conta.ofx` e `itau-cartao.ofx` pela tela, com o backend local; conferir as linhas em `transaction_imports`. Enviar um arquivo de 2MB para confirmar que o limite de 1MB foi removido.
