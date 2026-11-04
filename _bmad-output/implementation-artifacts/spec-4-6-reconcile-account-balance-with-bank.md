---
title: 'Reconciliar o saldo da conta com o banco'
type: 'feature'
created: '2026-11-03'
status: 'done'
baseline_commit: 7b683ed273d9dd92ae4d90fcf7222dd676238603
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/spec-4-5-record-internal-transfers-between-own-accounts.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-4-3-edit-account-and-card-information.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** O saldo registrado no porquinho se afasta do saldo real do banco (tarifa esquecida, lançamento não registrado). Hoje a única forma de corrigir é editar o saldo, que não guarda a diferença, o motivo nem a data em que o usuário conferiu com o banco.

**Approach:** Cada conta em `/contas` ganha o botão "Reconciliar". Ele abre um diálogo com o saldo atual, um campo para o saldo do banco e a diferença calculada na hora. Confirmar chama `POST /api/v1/accounts/{id}/reconciliations`, que ajusta `current_balance`, grava o evento em `reconciliation_history`, uma linha `reconciliation` no histórico de saldo e `last_reconciliation_date` na conta.

## Boundaries & Constraints

**Always:**
- Migration **V23**:
  - Tabela `reconciliation_history`: `id` UUID PK; `account_id` FK → `accounts(id)` ON DELETE CASCADE; `user_id` FK; `previous_balance`, `bank_balance` e `difference` DECIMAL(15,2) NOT NULL; `reason` VARCHAR(200) NULL; `reconciled_at` TIMESTAMPTZ NOT NULL; `created_at`.
  - Índice composto (`account_id`, `reconciled_at` DESC) e índice em `user_id`.
  - `ALTER TABLE accounts ADD last_reconciliation_date TIMESTAMPTZ NULL`.
  - Comentários em pt-BR. Os números V13 do épico já existem.
- `POST /api/v1/accounts/{id}/reconciliations` com `{bankBalance, reason}` → 201 com a conta atualizada e o evento.
  - Validação em pt-BR: `bankBalance` obrigatório, até 2 casas, dentro de DECIMAL(15,2), negativo permitido. `reason` opcional, com strip/limpeza única e até 200 caracteres.
  - Conta de outro usuário, inexistente ou removida → 404 "Conta não encontrada". A linha da conta é travada (`PESSIMISTIC_WRITE`, `findActiveByIdForUpdate`).
  - Tudo na mesma transação:
    - `difference = bankBalance − current_balance`, calculado no servidor;
    - `current_balance = bankBalance`;
    - `last_reconciliation_date = now`;
    - linha em `reconciliation_history`;
    - audit `account_reconciled` `{id, difference}`.
  - Se a diferença ≠ 0, grava também a linha `reconciliation` no `account_balance_history`.
  - Diferença 0 também é aceita: registra o evento e a data (o usuário conferiu e bateu), mas não grava linha no histórico de saldo.
- `GET /api/v1/accounts/{id}/reconciliations` lista as 20 reconciliações mais recentes da conta (`reconciled_at` DESC). A resposta de conta passa a incluir `lastReconciliationDate`.
- LGPD: `reconciliation_history` entra no `purgeUser` (antes de `accounts`) e no export. A coluna nova também entra no export (`assertExportsEveryColumn`).
- Frontend, em `/contas`:
  - Cada conta ativa ganha o botão "Reconciliar" (rótulo acessível com o nome, 44px) e a linha "Reconciliada em dd/mm/aaaa" quando houver data.
  - O botão abre um `Dialog` "Reconciliar <nome>" com:
    - "Saldo no porquinho: R$ X";
    - o campo "Saldo no banco" no formato brasileiro;
    - "Diferença: R$ Y", atualizada enquanto o usuário digita, em vermelho se negativa e com sinal;
    - o campo "Motivo" (opcional).
  - Diferença ≠ 0: o diálogo pergunta "Deseja ajustar o saldo?" e mostra os botões "Ajustar saldo" e "Investigar depois" (este fecha sem mudar nada). Diferença 0: o botão é "Confirmar reconciliação".
  - Sucesso: a conta é recarregada do servidor, o total é recalculado, o diálogo fecha e aparece "Saldo reconciliado" na região de status.
  - Erros, travas durante o envio, foco e acessibilidade no padrão das Stories 4.1–4.5. O 404 recarrega as contas.
- **Decisão (Luiz, 2026-11-03) A:** sem transação de ajuste. O evento em `reconciliation_history` (saldo anterior, saldo do banco, diferença, motivo, data) é o registro do ajuste. O Epic 5 decide se gera transações a partir desses eventos (anotado em `deferred-work.md`).

**Never:**
- Tabela `transactions` nem categoria "Ajuste de Reconciliação" (Epic 5), página de detalhe da conta, desfazer uma reconciliação, cartões.
- Biblioteca nova; editar migrations V1 a V22.

## I/O & Edge-Case Matrix

| Cenário | Estado | Resultado | Erro |
|---|---|---|---|
| Banco mostra mais | porquinho 1.000,00, banco 1.050,00 | Diferença +50,00; "Ajustar saldo" → 201; saldo 1.050,00; linha em `reconciliation_history` (diferença 50,00); linha `reconciliation` no histórico; data gravada; "Saldo reconciliado" | — |
| Banco mostra menos | 1.000,00 → 980,00 | Diferença −20,00 em vermelho; ajuste → 980,00 | — |
| Saldos iguais | 1.000,00 → 1.000,00 | "Confirmar reconciliação" → evento com diferença 0 e data gravada; sem linha no histórico de saldo | — |
| Investigar depois | diferença ≠ 0 | diálogo fecha; nada muda | — |
| Saldo do banco vazio ou com 3 casas | — | erro no campo | 400 |
| Saldo do banco negativo | banco −30,00 | permitido | — |
| Conta de outro usuário ou removida | — | 404; nada muda; lista recarregada | 404 |
| Saldo mudou em outra aba | servidor 900,00, tela 1.000,00 | diferença calculada no servidor a partir de 900,00; tela recarregada | — |

</frozen-after-approval>

## Code Map

- `porquinho-backend/.../service/AccountService.java`, `repository/AccountRepository.java`, `entity/Account.java`, `entity/AccountBalanceHistory.java` -- lock, gravação no histórico, audit.
- `porquinho-backend/.../controller/AccountController.java`, `dto/AccountResponse.java` -- novas rotas e o campo `lastReconciliationDate`.
- `porquinho-backend/.../service/TransferService.java` -- modelo de operação atômica com histórico e audit.
- `porquinho-backend/.../service/AccountDeletionService.java`, `DataExportService.java` -- acrescentar a tabela e a coluna.
- Criar `entity/Reconciliation.java`, `repository/ReconciliationRepository.java`, `dto/CreateReconciliationRequest.java`, `dto/ReconciliationResponse.java` (serviço em `AccountService` ou `ReconciliationService`, o que for menor).
- `porquinho-frontend/src/views/AccountsView.vue` -- botão, linha da data, diálogo. Criar `components/accounts/ReconcileForm.vue`. `services/accountService.ts`, `types/account.ts`, `lib/money.ts`.
- Testes modelo: `TransferServiceTest`, `TransferControllerTest`, `TransferIntegrationTest`, `AccountDeletionServiceTest`, `DataExportServiceTest`, `AccountsView.spec.ts`, `TransferForm.spec.ts`.

## Tasks & Acceptance

**Execution:**
- [ ] `V23__create_reconciliation_history_table.sql` -- tabela, índices, coluna nova e comentários.
- [ ] Backend -- entidade, repositório, serviço (lock, saldo, evento, histórico, data, audit), rotas, DTOs; purge e export.
- [ ] Frontend -- serviço, `ReconcileForm.vue`, botão, linha da data e diálogo em `/contas`.
- [ ] Testes -- a matriz no backend (serviço, controller, integração com PostgreSQL incluindo atomicidade e diferença 0) e Vitest do formulário e da página.

**Acceptance Criteria:**
- Dado Nubank com R$ 1.000,00, quando o usuário reconcilia com R$ 1.050,00 e escolhe "Ajustar saldo", então a lista mostra R$ 1.050,00 e "Reconciliada em <hoje>", aparece "Saldo reconciliado", e no banco existem o evento com diferença 50,00, a linha `reconciliation` no histórico, `last_reconciliation_date` preenchida e o audit `account_reconciled`.
- Dado a diferença diferente de zero, quando o usuário escolhe "Investigar depois", então nada muda.

## Spec Change Log

## Review Triage Log

Revisão em 3 camadas (Blind Hunter, Edge Case Hunter, Verification Gap) — 2026-11-03.

| # | Origem | Achado | Veredito | Evidência | Rota |
|---|---|---|---|---|---|
| 1 | VG | Atualização local da conta depois do POST sem teste (o refresh mockado sempre sobrescreve) | medium | Apagar o `splice` ou o `filter` do 404 passa nos testes | patch |
| 2 | Blind | Diferença fora de DECIMAL(15,2) volta como erro geral, não no campo; sem teste HTTP | low | `DIFFERENCE_OUT_OF_RANGE_MESSAGE` sem `errors` | patch |
| 3 | Blind | GET sem teste de 404 no controller | low | Só o caminho feliz | patch |
| 4 | Edge | `request` ou `bankBalance` nulo vira 500 sem `@Valid` | low | Mesmo guarda da 4.5 | patch |
| 5 | Blind | "Investigar depois" e "Cancelar" lado a lado fazem a mesma coisa | low | Os dois só fecham | patch (sem Cancelar quando diferença ≠ 0) |
| 6 | Blind | Front não valida o limite de 13 dígitos | low | Só formato | patch |
| 7 | Blind | `lastReconciliationDate` opcional no tipo, mas sempre enviado | low | `AccountResponse` | patch |
| 8 | Blind | Desempate por `createdAt` inútil (mesmo `now` de `reconciledAt`) | low | Repositório | patch |
| 9 | Blind | Comentário "V13 do épico" na V23 e Javadoc longo | low | V23 ainda não aplicada | patch |
| 10 | Blind, Edge | Saldo mudou em outra aba: o servidor grava diferença diferente da vista | low | A matriz manda calcular no servidor; o saldo final é o do banco, que é o objetivo | reject |
| 11 | Blind | Dois envios de abas diferentes geram dois eventos | low | O segundo tem diferença 0 e só registra a conferência | reject |
| 12 | Blind | Audit só com `{id, difference}` | low | Formato pedido na spec; o evento completo está em `reconciliation_history` | reject |
| 13 | Blind | DTO usa `CategoryService.cleanName` | low | Mesmo veredito da 4.5 | reject |
| 14 | Blind | GET e `listReconciliations` sem tela | low | A spec pede só a API; a tela de histórico é da 4.7 | reject |
| 15 | Blind | Botão "Reconciliar" com texto pode apertar a linha no mobile | low | Editar e Remover também têm texto | reject |
| 16 | Blind | Sem teste de 401 | low | Segurança global | reject |
| 17 | Edge | Diferença 0 não grava linha no histórico de saldo | false | Decisão explícita da spec (o saldo não muda) | reject |

## Verification

**Commands:**
- `cd porquinho-backend && ./mvnw test` -- 0 falhas. Antes, parar o backend de dev e conferir que a porta 8080 está livre.
- `cd porquinho-frontend && npx vitest run && npx oxlint . && npx eslint . && npx vue-tsc --build` -- 0 falhas e 0 erros.

**Manual checks:**
- Reconciliar uma conta para mais, outra para menos e uma com saldo igual; conferir lista, data, mensagem e as linhas no Supabase de dev.
