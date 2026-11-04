---
title: 'Correções da retro do Epic 4: edição concorrente de conta e estado de /contas'
type: 'bugfix'
created: '2026-11-04'
status: 'done'
baseline_commit: bf1e1ed
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-4-retro-2026-11-04.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-4-3-edit-account-and-card-information.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-4-6-reconcile-account-balance-with-bank.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:**
- **X1:** a edição de conta (Story 4.3) regrava o saldo lido quando o formulário abriu. Uma transferência (4.5) ou reconciliação (4.6) feita em outra aba nesse intervalo é desfeita ao salvar, e o histórico ganha uma linha `edit` falsa.
- **X2:** em `/contas`, `load`, `refreshAccounts` e `refreshAfterNotFound` não têm guarda de sequência, então uma resposta atrasada pode sobrescrever uma edição recente.
- **X3:** o 404 é tratado de quatro jeitos diferentes.

**Approach:** Controle de versão otimista na conta (decisão A do Luiz, 2026-11-04): coluna `version`, `@Version` na entidade, versão enviada no PUT e 409 quando ela diverge. Na tela, guarda de sequência nas recargas de contas e um caminho único para o 404.

## Boundaries & Constraints

**Always:**
- Migration **V24** `ALTER TABLE accounts ADD COLUMN version BIGINT NOT NULL DEFAULT 0`, com comentário em pt-BR.
- `Account` ganha `@Version private Long version`. Toda alteração da conta incrementa a versão, inclusive transferência, reconciliação, remoção e restauração.
- `AccountResponse` inclui `version`. `UpdateAccountRequest` exige `version` (400 "Versão obrigatória" se faltar).
- `PUT /api/v1/accounts/{id}` com uma versão diferente da atual → 409 "Esta conta foi alterada em outra tela. Abra a edição de novo." Nada é gravado. A comparação é feita explicitamente no serviço, depois do lock.
- A colisão no flush (`ObjectOptimisticLockingFailureException`) também vira esse 409 no `GlobalExceptionHandler`.
- O export LGPD inclui `version` (`assertExportsEveryColumn`).
- Frontend:
  - `AccountForm` em modo edição envia a `version` da conta.
  - No 409, o diálogo fecha, as contas são recarregadas e o aviso âmbar (`role="alert"`, mesma região do 404 da 4.3) mostra a mensagem do servidor.
  - O foco volta para o botão "Editar" da conta, ou para um alvo estável se ela não estiver mais na lista.
- `AccountsView.vue`:
  - **Guarda de sequência:** `load`, `refreshAccounts` e `refreshAfterNotFound` usam um contador comum de requisições de contas (padrão `request === accountsRequest` de `loadTransfers` e `loadRemoved`). Uma mudança local (`splice` ou `filter`) feita depois de a requisição começar invalida a resposta, que é descartada.
  - **404 único:** o 404 de editar, remover, restaurar, transferir e reconciliar passa pelo mesmo caminho. Ele recarrega as contas ativas e, se a seção estiver aberta, as removidas. Transferir continua com o diálogo aberto; os demais fecham, como hoje.
- Cartões não mudam: o limite só muda pelo PUT, então não há escrita concorrente do servidor.

**Never:**
- Editar migrations V1 a V23; biblioteca nova; tocar em cartões, transferências ou reconciliação além do incremento automático de versão.

## I/O & Edge-Case Matrix

| Cenário | Estado | Resultado | Erro |
|---|---|---|---|
| Editar com a versão atual | versão 3 | 200; versão 4; histórico como hoje | — |
| Transferência entre abrir e salvar a edição | formulário com versão 3, conta em 4 | 409; saldo da transferência mantido; sem linha `edit`; aviso; lista recarregada | 409 |
| Reconciliação entre abrir e salvar | idem | 409 | 409 |
| PUT sem `version` | — | 400 "Versão obrigatória" | 400 |
| Recarga lenta depois de uma edição | `refreshAccounts` responde depois do `splice` | resposta descartada; a tela mantém a edição | — |
| 404 na edição com "Removidas" aberta | conta removida em outra aba | lista ativa sem a conta; "Removidas" mostra a conta | 404 |

</frozen-after-approval>

## Code Map

- `porquinho-backend/.../entity/Account.java`, `dto/AccountResponse.java`, `dto/UpdateAccountRequest.java`, `service/AccountService.java`, `exception/GlobalExceptionHandler.java`, `service/DataExportService.java`.
- `porquinho-frontend/src/types/account.ts`, `services/accountService.ts`, `components/accounts/AccountForm.vue`, `views/AccountsView.vue`.
- Testes: `AccountServiceTest`, `AccountControllerTest`, um teste de integração novo com PostgreSQL (transferência e reconciliação entre a leitura e o PUT → 409), `DataExportServiceTest`, `AccountForm.spec.ts`, `AccountsView.spec.ts` (409, resposta atrasada descartada, 404 com "Removidas" aberta), fixtures com `version`.

## Tasks & Acceptance

**Execution:**
- [x] `V24__add_version_to_accounts.sql`; `@Version`, DTOs, 409 no serviço e no handler, export.
- [x] Frontend: `version` no tipo e no PUT; 409 na tela; guarda de sequência; 404 único.
- [x] Testes da matriz.

**Acceptance Criteria:**
- Dado o formulário de edição aberto no Nubank e uma transferência de R$ 200,00 feita em outra aba, quando o usuário salva a edição, então aparece "Esta conta foi alterada em outra tela. Abra a edição de novo.", o saldo continua com a transferência aplicada e o histórico não tem linha `edit` nova.

## Spec Change Log

## Review Triage Log

Revisão em 3 camadas (Blind Hunter, Edge Case Hunter, Verification Gap) — 2026-11-04.

| # | Origem | Achado | Veredito | Evidência | Rota |
|---|---|---|---|---|---|
| 1 | Blind, Edge | Descartar a recarga em andamento sem buscar de novo perde saldos e versões novas (409 falso na próxima edição); a primeira carga descartada deixa a página vazia | high | `discardPendingAccountLoads` só incrementa o contador | patch (buscar de novo depois do descarte) |
| 2 | Blind, Edge | Falha da recarga no 409 troca a mensagem do conflito pelo aviso genérico | low | `refreshAccounts` sobrescreve `notice` | patch |
| 3 | Blind | O 404 filtra "Removidas" sem invalidar o `loadRemoved` em andamento | low | `removedRequest` intocado | patch |
| 4 | Blind, Edge | O handler global usa a mensagem da edição de conta para qualquer entidade; `exception` importa `service`; sem log | medium | `GlobalExceptionHandler` | patch |
| 5 | Blind | `version` sem `@PositiveOrZero` | low | Só `@NotNull` | patch |
| 6 | Blind | Sem teste de `version` no POST e no GET, nem de dois PUTs simultâneos | medium | Testes sequenciais | patch |
| 7 | VG | Corrida da recarga só testada na edição; 404 de transferência e reconciliação sem teste com "Removidas" aberta | medium | `AccountsView.spec.ts:749, 1416, 1742` | patch |
| 8 | Blind | 409 causado pela própria aba logo depois de uma transferência | low | Coberto pelo item 1: a recarga não é mais perdida | reject (duplicado) |
| 9 | Blind | `lenient()` no helper do teste | low | Estilo de teste | reject |
| 10 | Blind | Mensagem do 409 copiada no front como fallback | low | Fallback quando o servidor não manda `detail` | reject |

## Verification

**Commands:**
- `cd porquinho-backend && ./mvnw test` -- 0 falhas. Antes, parar o backend de dev e conferir que a porta 8080 está livre.
- `cd porquinho-frontend && npx vitest run && npx oxlint . && npx eslint . && npx vue-tsc --build` -- 0 falhas e 0 erros.

**Manual checks:**
- Abrir "Editar" no Nubank, transferir em outra aba e salvar a edição: deve aparecer o 409.
