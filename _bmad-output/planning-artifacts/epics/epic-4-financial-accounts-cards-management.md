# Epic 4: Financial Accounts & Cards Management

Usuários podem cadastrar e gerenciar múltiplas contas bancárias (corrente/poupança) e cartões de crédito, editar informações, desativar/remover contas, registrar transferências entre contas próprias, reconciliar saldos com valores reais do banco, e visualizar histórico de saldos ao longo do tempo.

## Story 4.1: Register Multiple Bank Accounts

As a logged-in user,
I want to register additional bank accounts after onboarding,
So that I can track all my financial accounts in one place.

**Acceptance Criteria:**

**Given** I am logged in and on the Accounts management page
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

**Given** I am logged in and on the Cards management page
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
**And** I can modify: Account/Card Name, Bank Name, Account Type, Current Balance (for accounts), Credit Limit and Due Day (for cards)
**And** Clicking "Save" updates the record in database
**And** Updated_at timestamp is set to NOW()
**And** Query validates user_id matches JWT (security: cannot edit other user's accounts per NFR15)
**And** Account/card list is refreshed with updated information
**And** If balance is changed, a balance history entry is created (Story 4.7 table)
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
**Then** Confirmation dialog is shown: "Are you sure? This action will soft-delete this account/card"
**And** Dialog explains: "Transactions will be preserved but account will be hidden"
**And** Clicking "Confirm" performs soft delete: deleted_at = NOW(), deleted_by = user_id (ARCH-REQ-8)
**And** Account/card is immediately hidden from list (query filters deleted_at IS NULL per ARCH-REQ-9)
**And** Associated transactions are NOT deleted (preserved for history)
**And** Success message: "Conta/Cartão removido"
**And** Audit log entry is created for "account_deleted" or "card_deleted" event
**And** Cannot delete last remaining account (validation: at least 1 active account required)
**And** Option to "Show Deleted Accounts" toggle for recovery purposes
**And** Clicking "Restore" on deleted account sets deleted_at = NULL, deleted_by = NULL

---

## Story 4.5: Record Internal Transfers Between Own Accounts

As a logged-in user,
I want to record transfers between my own accounts,
So that money movement between accounts doesn't affect my budget categories.

**Acceptance Criteria:**

**Given** I am logged in and have at least 2 active accounts
**When** I navigate to "Record Transfer" or click "Transfer" button
**Then** Transfer form is displayed with fields: From Account (select), To Account (select), Amount (currency), Transfer Date, Description (optional)
**And** A `transfers` table is created via migration (V12__create_transfers_table.sql) with fields: transfer_id (UUID, PK), user_id (FK), from_account_id (FK), to_account_id (FK), amount (decimal), transfer_date, description, created_at, deleted_at
**And** I select source and destination accounts, enter amount and date
**And** Clicking "Save" creates transfer record with user_id from JWT
**And** Source account current_balance is decreased by amount
**And** Destination account current_balance is increased by amount
**And** Both balance changes are atomic (database transaction)
**And** Transfer appears in both accounts' transaction history with special "Transfer" type
**And** Validation: from_account_id ≠ to_account_id
**And** Validation: amount > 0
**And** Validation: user owns both accounts (security check via user_id)
**And** Success message: "Transferência registrada"

---

## Story 4.6: Reconcile Account Balance with Bank

As a logged-in user,
I want to reconcile my registered balance with my real bank balance,
So that I can correct any discrepancies and maintain accuracy.

**Acceptance Criteria:**

**Given** I am logged in and viewing an account detail page
**When** I click "Reconcile Balance"
**Then** Reconciliation modal is displayed showing:
  - Current balance in porquinho: R$ X.XX
  - Actual bank balance: [input field]
  - Difference: R$ Y.YY (calculated automatically)
**And** I enter the actual balance from my bank statement
**And** System calculates difference automatically
**And** If difference ≠ 0, I am prompted: "Do you want to adjust the balance?"
**And** Adjustment options:
  - Create adjustment transaction (adds/removes difference to match bank)
  - Manually investigate discrepancy (cancel, no changes)
**And** If "Create adjustment transaction" is selected:
  - Transaction is created with category "Ajuste de Reconciliação"
  - Account current_balance is updated to match bank balance
  - Adjustment is recorded with timestamp and reason
**And** A `reconciliation_history` table stores all reconciliation events (new migration V13)
**And** Success message: "Saldo reconciliado"
**And** Reconciliation date is saved to account record (last_reconciliation_date field)

---

## Story 4.7: View Account Balance History Over Time

As a logged-in user,
I want to view historical balance changes for each account over time,
So that I can understand my account balance evolution.

**Acceptance Criteria:**

**Given** I am logged in and viewing an account detail page
**When** I navigate to "Balance History" tab
**Then** A `account_balance_history` table is created via migration (V14__create_account_balance_history_table.sql) with fields: history_id (UUID, PK), account_id (FK), user_id (FK), balance_snapshot (decimal), snapshot_date, created_by_event (transaction/reconciliation/edit/transfer), created_at
**And** Balance history chart is displayed showing balance over time (line chart)
**And** X-axis: Date (timeline from account creation to today)
**And** Y-axis: Balance (R$)
**And** Chart shows key events: deposits, withdrawals, transfers, reconciliations
**And** Hovering over data points shows: Date, Balance, Event type
**And** Time range selector: Last 30 days, 3 months, 6 months, 1 year, All time
**And** Balance snapshot is automatically recorded whenever:
  - Transaction is added/edited (Epic 5)
  - Transfer is recorded (Story 4.5)
  - Reconciliation occurs (Story 4.6)
  - Balance is manually edited (Story 4.3)
**And** Query filters by user_id for security (NFR15)
**And** Chart renders in < 1 second even with 1000+ data points (NFR10)

---
