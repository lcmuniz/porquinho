# Epic 9: Reports & Data Export

Usuários podem exportar relatórios de transações em CSV, relatórios de categorias e orçamento em CSV, e exportar todos os dados da conta em formato portável para compliance LGPD e análise externa.

## Story 9.1: Export Transaction Reports to CSV

As a logged-in user,
I want to export my transaction history to CSV format,
So that I can analyze data in Excel or other tools.

**Acceptance Criteria:**

**Given** I am viewing Dashboard Layer 3 or Transactions page
**When** I click "Export to CSV"
**Then** CSV export dialog is displayed with options:
  - Date Range: Custom range or presets (This month, Last 3 months, Last year, All time)
  - Accounts: Select specific accounts or "All"
  - Categories: Select specific categories or "All"
  - Include: Checkboxes for "Include future installments", "Include soft-deleted"
**And** Clicking "Generate CSV" triggers export
**And** CSV file is generated with columns:
  - Date, Description, Amount, Type (Debit/Credit), Category, Account, Is Pix, Is Installment, Installment Details
**And** File naming: `transactions_YYYY-MM-DD.csv`
**And** CSV uses UTF-8 BOM encoding for Excel compatibility (Brazilian users)
**And** Decimal separator: comma (Brazilian standard: R$ 1.234,56)
**And** Export completes in < 5 seconds for up to 10,000 transactions
**And** Download link is provided or file downloads automatically
**And** Audit log records "transaction_export" event
**And** Success message: "Relatório exportado: X transações"

---

## Story 9.2: Export Budget & Category Reports to CSV

As a logged-in user,
I want to export budget and category reports to CSV,
So that I can analyze my spending patterns externally.

**Acceptance Criteria:**

**Given** I am on the Budget or Categories page
**When** I click "Export Budget Report"
**Then** CSV export dialog is displayed with options:
  - Date Range: Select month or range of months
  - Include: "Include hierarchical structure", "Include historical averages"
**And** Clicking "Generate CSV" triggers export
**And** CSV file is generated with columns:
  - Category Name, Parent Category, Allocated Amount, Spent Amount, Remaining, Percentage Used, Status
**And** If historical data requested, additional columns: Avg Spent (3mo), Avg Spent (6mo), Trend
**And** File naming: `budget_report_YYYY-MM.csv`
**And** UTF-8 BOM encoding, comma decimal separator
**And** Export completes in < 3 seconds
**And** Audit log records "budget_export" event
**And** Success message: "Relatório de orçamento exportado"

---

## Story 9.3: Export Complete Account Data (LGPD Portability)

As a logged-in user,
I want to export all my account data in a portable format,
So that I can comply with LGPD portability rights or migrate to another platform.

**Acceptance Criteria:**

**Given** I am on Account Settings > Data Export
**When** I click "Export All Data" (same as Epic 1, Story 1.9 but specifically for FR84)
**Then** Confirmation dialog is displayed: "This will export ALL your data. Continue?"
**And** After confirmation, background job is triggered
**And** Complete data export includes:
  - User profile and settings
  - All accounts and cards
  - All transactions (including soft-deleted with deleted_at marked)
  - All categories and budgets
  - All goals and progress history
  - All audit logs (LGPD compliance)
**And** Export format: JSON (structured, machine-readable) per LGPD requirements
**And** ZIP file created containing:
  - user_profile.json
  - accounts.json
  - transactions.json
  - categories.json
  - budgets.json
  - goals.json
  - audit_log.json
  - export_metadata.json (export date, data version, schema version)
**And** Download link is generated (valid for 24 hours)
**And** Email notification sent: "Seu export de dados está pronto"
**And** File is password-protected (user's account password or custom password)
**And** Audit log records "complete_data_export" event
**And** Export file is automatically deleted after 24 hours or first download
**And** Complies with LGPD portability requirements (FR84, NFR23)

---
