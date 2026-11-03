---
title: 'Editar contas bancárias e cartões de crédito'
type: 'feature'
created: '2026-11-03'
status: 'done'
baseline_commit: 8fced8439d748cfc23ca00ccfc26da21f6352413
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/spec-4-1-register-multiple-bank-accounts.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-4-2-register-multiple-credit-cards.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-3-2-add-bank-accounts-during-setup.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Depois de cadastrada, uma conta ou um cartão não pode ser corrigido: um nome digitado errado, um banco trocado, um saldo ou limite desatualizado ficam assim para sempre.

**Approach:** Acrescentar `PUT /api/v1/accounts/{id}` e `PUT /api/v1/credit-cards/{id}`, com as mesmas validações da criação, e um botão "Editar" em cada item das páginas `/contas` e `/cartoes`, que abre o mesmo diálogo da criação com os valores atuais preenchidos.

## Boundaries & Constraints

**Always:**
- `PUT /api/v1/accounts/{id}` `{accountName, bankName, accountType, currentBalance}` → 200 com a conta atualizada. Mesmas regras de validação e mensagens da criação (nome com strip/limpeza única, até 100; banco opcional; tipo obrigatório; saldo com até 2 casas, dentro de DECIMAL(15,2), negativo permitido). Atualiza `current_balance`; `initial_balance` não muda.
- `PUT /api/v1/credit-cards/{id}` `{cardName, bankName, cardLimit, dueDay}` → 200 com o cartão atualizado. Mesmas regras da criação (limite ≥ 0, dia 1–31).
- Conta ou cartão de outro usuário, inexistente ou removido → 404. `user_id` sempre do JWT. `updated_at` é atualizado (o `@PreUpdate` existente já faz isso).
- Frontend: cada item da lista em `/contas` e `/cartoes` ganha o botão "Editar" (rótulo acessível com o nome do item, 44px). Ele abre o diálogo com título "Editar conta"/"Editar cartão", o formulário (`AccountForm`/`CardForm`) preenchido com os valores atuais e o botão "Salvar alterações". Ao salvar, o item é substituído na lista, o total é recalculado, o diálogo fecha e aparece "Informações atualizadas" na região de status existente.
- O mesmo formulário serve para criar e editar (modo definido por uma prop com o item a editar). Erros, travas durante o envio, foco (ao fechar, volta para o botão "Editar" do item) e acessibilidade iguais aos da criação. Um 404 ao salvar fecha o diálogo, remove o item da lista e mostra "Esta conta não existe mais" / "Este cartão não existe mais".
- O passo 2 e o passo 3 do onboarding continuam só criando.
- **Decisão (Luiz, 2026-11-03) A:** criar agora a migration **V20** `account_balance_history`: `id` UUID PK, `account_id` FK → `accounts(id)` indexada, `user_id` FK indexada, `balance_snapshot` DECIMAL(15,2) NOT NULL, `snapshot_date` TIMESTAMP WITH TIME ZONE NOT NULL, `created_by_event` VARCHAR(20) NOT NULL CHECK (`transaction`, `reconciliation`, `edit`, `transfer`, `initial`), `created_at`. Na edição, quando o saldo muda, grava uma linha com o novo saldo e `edit`, na mesma transação; sem mudança de saldo, nada é gravado. Criar uma conta (onboarding, `/contas` ou "Minha Conta Principal") grava uma linha `initial` com o saldo inicial, para o histórico começar na criação. Sem endpoint de leitura nem gráfico (Story 4.7). LGPD: a tabela entra no `purgeUser` (antes de `accounts` e do usuário) e no export (`assertExportsEveryColumn`).

**Never:**
- Remover ou desativar (Story 4.4), transferências (4.5), reconciliação (4.6), gráfico de histórico (4.7).
- Editar `initial_balance` ou campos fora da lista acima; biblioteca nova.

## I/O & Edge-Case Matrix

| Cenário | Estado | Resultado | Erro |
|---|---|---|---|
| Editar nome e banco da conta | conta própria | 200; lista atualizada; "Informações atualizadas" | — |
| Editar saldo da conta | 100,00 → 250,00 | `current_balance`=250,00; `initial_balance` igual; total recalculado | — |
| Editar limite e vencimento do cartão | cartão próprio | 200; "vence dia X" e total atualizados | — |
| Valores inválidos | dia 0, nome vazio, saldo com 3 casas | 400 no campo; diálogo aberto | 400 |
| Item de outro usuário ou removido | — | 404; item sai da lista com aviso | 404 |
| Abrir "Editar" | — | formulário preenchido com os valores atuais (saldo e limite no formato brasileiro) | — |
| Editar saldo | 100,00 → 250,00 | 1 linha `edit` com 250,00 em `account_balance_history` | — |
| Editar só o nome | saldo igual | nenhuma linha nova no histórico | — |
| Criar conta | — | 1 linha `initial` com o saldo inicial | — |

</frozen-after-approval>

## Code Map

- `porquinho-backend/.../controller/AccountController.java`, `service/AccountService.java`, `dto/CreateAccountRequest.java` -- novo `PUT /{id}` e `UpdateAccountRequest` (mesmas anotações, `currentBalance` no lugar de `initialBalance`).
- `porquinho-backend/.../controller/CreditCardController.java`, `service/CreditCardService.java`, `dto/CreateCreditCardRequest.java` -- novo `PUT /{id}` reaproveitando o DTO de criação.
- `porquinho-backend/.../service/AccountDeletionService.java`, `DataExportService.java` -- acrescentar `account_balance_history` (o histórico referencia `accounts`, então é apagado antes). `OnboardingService.applyAccounts` cria "Minha Conta Principal" pelo `AccountService`.
- `porquinho-frontend/src/components/accounts/AccountForm.vue`, `components/cards/CardForm.vue` -- modo edição (prop com o item, valores iniciais, chamada `update*`).
- `porquinho-frontend/src/views/AccountsView.vue`, `views/CardsView.vue` -- botão "Editar", diálogo em modo edição, substituição na lista.
- `services/accountService.ts`, `services/cardService.ts` -- `updateAccount`, `updateCard`. `lib/money.ts` -- `formatBRLInput` para preencher.
- Testes modelo: `AccountControllerTest`, `AccountServiceTest`, `CreditCardControllerTest`, `CreditCardServiceTest`, `AccountOnboardingIntegrationTest`, `AccountsView.spec.ts`, `CardsView.spec.ts`, `AccountForm.spec.ts`, `CardForm.spec.ts`.

## Tasks & Acceptance

**Execution:**
- [x] `V20__create_account_balance_history_table.sql`, entidade e repositório; gravação `initial` na criação e `edit` na mudança de saldo; purge e export.
- [x] Backend contas -- `UpdateAccountRequest`, `AccountService.update`, `PUT /accounts/{id}`.
- [x] Backend cartões -- `CreditCardService.update`, `PUT /credit-cards/{id}`.
- [x] Frontend -- `updateAccount`/`updateCard`, modo edição nos formulários, botão "Editar" e diálogo nas duas páginas.
- [x] Testes -- matriz no backend (serviço, controller, integração com PostgreSQL incluindo 404 de outro usuário) e Vitest dos formulários e das páginas.

**Acceptance Criteria:**
- Dado uma conta "Nubank" com R$ 100,00, quando o usuário a edita para "Nubank PJ" com R$ 250,00, então a lista mostra "Nubank PJ" com R$ 250,00, o total muda em R$ 150,00, aparece "Informações atualizadas", e no banco `current_balance`=250,00, `initial_balance`=100,00, `updated_at` mudou e existe uma linha `edit` com 250,00 em `account_balance_history`.
- Dado um cartão com vencimento dia 10, quando o usuário o edita para dia 20 e limite R$ 6.000,00, então a lista mostra "vence dia 20" e o novo limite, e o limite total é recalculado.

## Spec Change Log

## Review Triage Log

Revisão em 3 camadas (Blind Hunter, Edge Case Hunter, Verification Gap) — 2026-11-03. O Verification Gap não encontrou lacunas.

| # | Origem | Achado | Veredito | Evidência | Rota |
|---|---|---|---|---|---|
| 1 | Blind, Edge, VG | Contas anteriores à V20 ficam sem a linha `initial` | medium | V20 só cria a tabela; ainda não aplicada, então dá para preencher nela | patch |
| 2 | Edge | Snapshot gravado durante o purge bloqueia o DELETE das contas pela FK | low | Purge apaga o histórico e depois as contas | patch (`ON DELETE CASCADE`) |
| 3 | Blind | Índice só em `account_id`; o gráfico da 4.7 consulta por conta e data | low | Correção direta antes de aplicar a V20 | patch (índice composto) |
| 4 | Blind | 404 da edição aparece na região de sucesso (verde) | low | `onNotFound` chama `setSuccess` | patch |
| 5 | Blind, Edge | Sem controle de concorrência na edição (duas abas: a última vence) | low | Sem `@Version`; mesmo cenário rejeitado nos passos do onboarding | defer |
| 6 | Blind | Ordem do histórico para eventos no mesmo instante depende de UUID | low | Só o export lê hoje; a 4.7 define a ordenação do gráfico | defer (Story 4.7) |
| 7 | Blind | Qualquer 404 tira o item da lista só no front | low | Endpoints devolvem 404 só para conta/cartão inexistente | reject |
| 8 | Blind | Edição de cartão reusa o DTO de criação | low | Mesmos campos e regras; separar quando divergirem | reject |
| 9 | Blind | `UpdateAccountRequest` copia as regras de `CreateAccountRequest` | low | Testes de controller cobrem as duas | reject |
| 10 | Blind | Edição de saldo sem audit log | low | Mesmo veredito de contas e cartões; o histórico registra o evento | reject |
| 11 | Blind | Reabrir o mesmo item depende de o `DialogContent` desmontar | low | Teste de reabrir depois de Cancelar existe | reject |
| 12 | Blind | `data_version` do export não mudou | low | Mesmo veredito da 3.2 | reject |
| 13 | Blind | Entidade "append-only" com setters públicos | low | Só o serviço grava | reject |

## Verification

**Commands:**
- `cd porquinho-backend && ./mvnw test` -- 0 falhas. Antes, parar o backend de dev e conferir que a porta 8080 ficou livre, para o devtools não aplicar uma V20 intermediária no Supabase.
- `cd porquinho-frontend && npx vitest run && npx oxlint . && npx eslint . && npx vue-tsc --build` -- 0 falhas e 0 erros.

**Manual checks:**
- Editar uma conta (nome e saldo) e um cartão (limite e vencimento) e conferir lista, total, mensagem e as linhas no Supabase de dev.
