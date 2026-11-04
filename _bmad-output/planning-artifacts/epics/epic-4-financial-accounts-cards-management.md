# Epic 4: Financial Accounts & Cards Management

Usuários podem cadastrar e gerenciar múltiplas contas bancárias (corrente/poupança) e cartões de crédito, editar informações, desativar/remover contas, registrar transferências entre contas próprias, reconciliar saldos com valores reais do banco, e visualizar histórico de saldos ao longo do tempo.

> **Como construído (retro do Epic 4, 2026-11-04):** os critérios abaixo foram atualizados para refletir o que foi implementado. Specs com as decisões: `_bmad-output/implementation-artifacts/spec-4-*.md` e `spec-epic-4-retro-fixes.md`. Tudo acontece nas páginas `/contas` e `/cartoes` (não há página de detalhe da conta); o histórico de saldo tem a página própria `/contas/:id/historico`. As tabelas usam `id` como chave primária e as migrations são V20 a V24 (V12 a V14 já existiam). Transações ainda não existem: o que depende delas ficou para o Epic 5.

## Story 4.1: Register Multiple Bank Accounts

As a logged-in user,
I want to register additional bank accounts after onboarding,
So that I can track all my financial accounts in one place.

**Acceptance Criteria:**

**Given** I am logged in and on the Accounts page (`/contas`, linked from the dashboard)
**When** I click "Add Bank Account"
**Then** Modal/form is displayed with fields: Account Name, Bank Name, Account Type (checking/savings), Current Balance
**And** I fill in the details and click "Save"
**And** New account is saved to `accounts` table with user_id from JWT (segregation per NFR15)
**And** Query filters by user_id AND deleted_at IS NULL (ARCH-REQ-9)
**And** Account appears in my accounts list immediately
**And** Account balance is reflected in total balance calculations
**And** I can add unlimited accounts (no artificial limit per NFR30)
**And** Form validation: Account Name required, Balance must be numeric
**And** Success message: "Conta adicionada com sucesso"
**And** If save fails, error message with clear explanation (NFR41)

---

## Story 4.2: Register Multiple Credit Cards

As a logged-in user,
I want to register additional credit cards after onboarding,
So that I can manage credit card tracking separately from bank accounts.

**Acceptance Criteria:**

**Given** I am logged in and on the Cards page (`/cartoes`, linked from the dashboard)
**When** I click "Add Credit Card"
**Then** Modal/form is displayed with fields: Card Name, Bank Name, Credit Limit, Due Day (1-31)
**And** I fill in the details and click "Save"
**And** New card is saved to `credit_cards` table with user_id from JWT
**And** Query filters by user_id AND deleted_at IS NULL (ARCH-REQ-9)
**And** Card appears in my cards list immediately
**And** Card limit is tracked for available credit calculations
**And** I can add unlimited cards (no artificial limit per NFR30)
**And** Form validation: Card Name required, Due Day must be 1-31, Credit Limit must be numeric
**And** Success message: "Cartão adicionado com sucesso"
**And** If save fails, error message with clear explanation

---

## Story 4.3: Edit Account and Card Information

As a logged-in user,
I want to edit information for my existing accounts and cards,
So that I can keep my financial data accurate and up-to-date.

**Acceptance Criteria:**

**Given** I am logged in and viewing my accounts or cards list
**When** I click "Edit" button on an account or card
**Then** Edit modal/form is displayed with current values pre-filled
**And** I can modify: Account/Card Name, Bank Name, Account Type, Current Balance (for accounts), Credit Limit and Due Day (for cards); initial_balance never changes
**And** Clicking "Save" updates the record in database
**And** Updated_at timestamp is set to NOW()
**And** Query validates user_id matches JWT (security: cannot edit other user's accounts per NFR15)
**And** Account/card list is refreshed with updated information
**And** An `account_balance_history` table is created via migration (V20__create_account_balance_history_table.sql), here and not in Story 4.7 (decision A, Luiz, 2026-11-03)
**And** If balance is changed (compared by value), one `edit` snapshot is written in the same transaction; an unchanged balance writes none
**And** Creating an account (onboarding, `/contas` or "Minha Conta Principal") writes an `initial` snapshot
**And** Concurrent edit (retro of Epic 4, X1): accounts carry a `version` (V24__add_version_to_accounts.sql, `@Version`); a PUT with a stale version returns 409 "Esta conta foi alterada em outra tela. Abra a edição de novo." and nothing is written
**And** Editing an item that no longer exists (other user, removed) returns 404 and the item leaves the list with a notice
**And** Success message: "Informações atualizadas"
**And** Form validation same as create stories (4.1, 4.2)

---

## Story 4.4: Deactivate or Remove Accounts and Cards

As a logged-in user,
I want to deactivate or permanently remove accounts and cards I no longer use,
So that my financial overview stays clean and relevant.

**Acceptance Criteria:**

**Given** I am logged in and viewing my accounts or cards list
**When** I click "Remove" button on an account or card
**Then** Confirmation dialog "Remover <nome>?" is shown
**And** Dialog explains: "A conta some da lista, mas o histórico é preservado. Você pode restaurá-la depois." (and the equivalent for cards)
**And** Clicking "Remover" performs soft delete: deleted_at = NOW(), deleted_by = user_id (ARCH-REQ-8; `deleted_by` added by V21__add_deleted_by_to_accounts_and_credit_cards.sql)
**And** Account/card is immediately hidden from list (query filters deleted_at IS NULL per ARCH-REQ-9)
**And** Balance history is NOT touched by removing or restoring; transactions (Epic 5) will not be deleted either
**And** Success message: "Conta removida" / "Cartão removido"
**And** Audit log entry is created for "account_deleted" or "card_deleted" event
**And** Cannot delete the last active account once onboarding is completed: 409 "Mantenha ao menos uma conta ativa" (decision 1B, Luiz, 2026-11-03: during onboarding step 2 every account may still be removed); cards have no minimum
**And** A "Mostrar removidas" / "Mostrar removidos" checkbox shows a "Removidas" section for recovery
**And** Clicking "Restaurar" sets deleted_at = NULL, deleted_by = NULL and logs "account_restored" or "card_restored"; removed items do not count in totals and cannot be edited

---

## Story 4.5: Record Internal Transfers Between Own Accounts

As a logged-in user,
I want to record transfers between my own accounts,
So that money movement between accounts doesn't affect my budget categories.

**Acceptance Criteria:**

**Given** I am logged in and have at least 2 active accounts
**When** I click the "Transferir" button on `/contas`
**Then** Transfer form is displayed with fields: From Account (select), To Account (select), Amount (currency), Transfer Date, Description (optional)
**And** A `transfers` table is created via migration (V22__create_transfers_table.sql) with fields: id (UUID, PK), user_id (FK), from_account_id (FK), to_account_id (FK), amount (decimal), transfer_date, description, created_at, deleted_at
**And** I select source and destination accounts, enter amount and date
**And** Clicking "Save" creates transfer record with user_id from JWT
**And** Source account current_balance is decreased by amount
**And** Destination account current_balance is increased by amount
**And** Both balance changes are atomic (database transaction), with both accounts locked in id order; each account gets a `transfer` snapshot in `account_balance_history` linked to the transfer, and the "transfer_created" audit is logged
**And** The snapshot is dated when the transfer is recorded, not by transfer_date: a back-dated transfer shows on the balance chart on the day it was registered (retro X4)
**And** Transfer appears in a "Transferências recentes" section on `/contas` (last 20); the transaction history comes with Epic 5
**And** The request is idempotent: the client sends the transfer id, and repeating it returns the existing transfer with nothing changed
**And** A negative resulting balance is allowed (decision 1A of Story 3.2); transfers do not affect categories or budgets
**And** Validation: from_account_id ≠ to_account_id
**And** Validation: amount > 0 with at most 2 decimals; transfer_date cannot be in the future (America/Sao_Paulo)
**And** Validation: user owns both accounts (security check via user_id)
**And** Success message: "Transferência registrada"

---

## Story 4.6: Reconcile Account Balance with Bank

As a logged-in user,
I want to reconcile my registered balance with my real bank balance,
So that I can correct any discrepancies and maintain accuracy.

**Acceptance Criteria:**

**Given** I am logged in and on `/contas`
**When** I click "Reconciliar" on an account
**Then** Reconciliation modal is displayed showing:
  - Current balance in porquinho: R$ X.XX
  - Actual bank balance: [input field]
  - Difference: R$ Y.YY (calculated automatically)
**And** I enter the actual balance from my bank statement
**And** System calculates difference automatically
**And** The difference is computed again by the server, never trusted from the client
**And** If difference ≠ 0, I am prompted: "Deseja ajustar o saldo?" with "Ajustar saldo" and "Investigar depois" (closes with no changes); if difference = 0, the button is "Confirmar reconciliação"
**And** When adjusting: account current_balance becomes the bank balance, and the event (previous balance, bank balance, difference, optional reason, timestamp) is recorded
**And** Decision A (Luiz, 2026-11-03): no adjustment transaction and no "Ajuste de Reconciliação" category; the event is the record of the adjustment. Epic 5 decides whether to derive transactions from these events (`deferred-work.md`)
**And** A `reconciliation_history` table stores all reconciliation events (V23__create_reconciliation_history_table.sql, which also adds `accounts.last_reconciliation_date`)
**And** A `reconciliation` snapshot goes to `account_balance_history` only when the balance changed; the "account_reconciled" audit is logged
**And** Success message: "Saldo reconciliado"
**And** Reconciliation date is saved to account record (last_reconciliation_date field) and shown as "Reconciliada em dd/mm/aaaa"

---

## Story 4.7: View Account Balance History Over Time

As a logged-in user,
I want to view historical balance changes for each account over time,
So that I can understand my account balance evolution.

**Acceptance Criteria:**

**Given** I am logged in and on `/contas`
**When** I click "Histórico" on an active account
**Then** The page `/contas/:id/historico` ("Histórico de saldo — <nome>") opens, reading `account_balance_history` (created in Story 4.3 by V20, with fields: id (UUID, PK), account_id (FK), user_id (FK), balance_snapshot (decimal), snapshot_date, created_by_event (initial/edit/transfer/reconciliation/transaction), created_at, plus transfer_id added by V22)
**And** Balance history chart is displayed showing balance over time (line chart)
**And** X-axis: Date (timeline from account creation to today)
**And** Y-axis: Balance (R$)
**And** Chart shows key events with a marker per type: initial balance, edits, transfers, reconciliations; deposits and withdrawals come with the transactions of Epic 5
**And** A table "Data | Evento | Saldo" below the chart gives the same data as text (NFR49)
**And** Decision A (Luiz, 2026-11-04): hand-made SVG chart (`BalanceChart.vue`), no chart library; Epic 7 decides whether one is needed
**And** Hovering over data points shows: Date, Balance, Event type
**And** Time range selector: Last 30 days, 3 months, 6 months, 1 year, All time
**And** Balance snapshot is automatically recorded, always through `BalanceHistoryRecorder` (the only writer of the table, retro X5), whenever:
  - Account is created (Story 4.3, `initial`)
  - Transaction is added/edited (Epic 5)
  - Transfer is recorded (Story 4.5)
  - Reconciliation occurs (Story 4.6)
  - Balance is manually edited (Story 4.3)
**And** Query filters by user_id for security (NFR15); another user's account is 404, and a removed account stays readable
**And** Chart renders in < 1 second even with 1000+ data points (NFR10)

---
