# Epic 5: Transaction Import & AI Classification

Usuários podem fazer upload de arquivos OFX/CSV dos 5 principais bancos brasileiros (Nubank, BB, Itaú, Bradesco, Caixa) com detecção automática de formato, parsing robusto, detecção de duplicatas, identificação de Pix e parcelamento, classificação automática por IA, revisão e ajuste de classificações, aprendizado contínuo com correções, e edição/adição manual de transações.

## Story 5.1: Upload OFX Files from 5 Brazilian Banks

As a logged-in user,
I want to upload OFX files from my bank (Nubank, BB, Itaú, Bradesco, Caixa),
So that I can import my transaction history automatically.

**Acceptance Criteria:**

**Given** I am logged in and on the Import Transactions page
**When** I click "Upload File" or drag an OFX file into the drop zone (UX-DR-43)
**Then** File upload begins with progress indicator
**And** File size is validated: max 10MB (NFR5)
**And** File extension is validated: .ofx accepted
**And** Upload completes in < 5 seconds for 10MB file (NFR5)
**And** File is temporarily stored on server with unique identifier
**And** A `transaction_imports` table is created via migration (V15__create_transaction_imports_table.sql) with fields: import_id (UUID, PK), user_id (FK), file_name, file_type (ofx/csv), bank_name, file_size_kb, status (uploading/processing/completed/failed), total_transactions, imported_transactions, duplicates_found, error_message, created_at
**And** Import record is created with status='uploading'
**And** After upload, status changes to 'processing'
**And** Success message: "Arquivo enviado, processando..."
**And** If file size > 10MB, error: "Arquivo muito grande (máximo 10MB)"
**And** If file format invalid, error: "Formato de arquivo não suportado"
**And** Story 5.3 (auto-detection) is triggered after upload

---

## Story 5.2: Upload CSV Files from 5 Brazilian Banks

As a logged-in user,
I want to upload CSV files from my bank (Nubank, BB, Itaú, Bradesco, Caixa),
So that I can import transactions in CSV format.

**Acceptance Criteria:**

**Given** I am logged in and on the Import Transactions page
**When** I click "Upload File" or drag a CSV file into the drop zone
**Then** File upload begins with progress indicator
**And** File size is validated: max 10MB (NFR5)
**And** File extension is validated: .csv accepted
**And** CSV encoding is detected (UTF-8, ISO-8859-1, Windows-1252)
**And** Upload completes in < 5 seconds for 10MB file (NFR5)
**And** File is temporarily stored on server
**And** Import record is created in `transaction_imports` table with file_type='csv', status='uploading'
**And** After upload, status changes to 'processing'
**And** CSV column headers are read to identify bank format
**And** Success message: "Arquivo enviado, processando..."
**And** If encoding cannot be detected, UTF-8 is assumed as fallback
**And** Story 5.3 (auto-detection) is triggered after upload

---

## Story 5.3: Auto-Detect File Format and Bank

As a system,
I want to automatically detect the file format (OFX/CSV) and originating bank,
So that users don't have to manually specify the source.

**Acceptance Criteria:**

**Given** A file has been uploaded (Story 5.1 or 5.2)
**When** Processing begins
**Then** File format is detected based on extension and content signature
**And** For OFX files: XML structure is validated, bank identifier tags are read
**And** For CSV files: Column headers are analyzed to match bank patterns
**And** Bank detection algorithm checks patterns for 5 supported banks:
  - Nubank: Specific column names and date formats
  - Banco do Brasil: BB-specific transaction codes
  - Itaú: Itaú CSV structure
  - Bradesco: Bradesco transaction patterns
  - Caixa: Caixa-specific identifiers
**And** Detection accuracy is 95%+ (NFR56)
**And** Import record is updated with detected bank_name
**And** If detection confidence is < 95%, user is prompted: "Detectamos [Bank]. Está correto?"
**And** User can confirm or manually select correct bank
**And** If detection fails completely, error message (NFR57): "Não foi possível identificar o banco. Por favor, selecione manualmente"
**And** Malformed files show clear error message with suggestions (NFR57)
**And** After successful detection, Story 5.4 (parsing) is triggered

---

## Story 5.4: Parse Transactions with Robust Error Handling

As a system,
I want to parse transaction data from uploaded files with robust error handling,
So that users can import transactions even if files have minor issues.

**Acceptance Criteria:**

**Given** File format and bank have been detected (Story 5.3)
**When** Parsing begins
**Then** A `transactions` table is created via migration (V16__create_transactions_table.sql) with fields: transaction_id (UUID, PK), user_id (FK), account_id (FK), import_id (FK), transaction_date, description, amount (decimal), transaction_type (debit/credit), category_id (FK, nullable), classification_confidence (0-100), is_duplicate (boolean), is_pix (boolean), pix_details (JSONB), is_installment (boolean), installment_number, installment_total, installment_group_id (UUID), is_future_installment (boolean), original_description, created_at, updated_at, deleted_at
**And** Parser reads transactions one by one from file
**And** For each transaction, extracts: date, description, amount, type (debit/credit)
**And** Parsing completes in < 30 seconds for 1 year of transactions (NFR6)
**And** Progress is updated every 2 seconds: "X de Y transações processadas" (NFR8)
**And** Error handling for malformed rows:
  - Skip row with error, log error, continue parsing (graceful degradation)
  - Missing required fields: skip row, log warning
  - Invalid date format: attempt multiple formats, fallback to skipping
  - Invalid amount: skip row, log error
**And** Parsing statistics are tracked: total_rows, successful_imports, skipped_rows, error_count
**And** Import record is updated with total_transactions count
**And** After parsing completes, Stories 5.5-5.7 (duplicate detection, Pix, installments) are triggered in sequence
**And** If parsing fails completely, status='failed', error_message is saved
**And** User sees: "Arquivo processado: X transações importadas, Y duplicadas ignoradas, Z erros"

---

## Story 5.5: Detect and Flag Duplicate Transactions

As a system,
I want to detect duplicate transactions during import,
So that users don't have duplicate entries inflating their data.

**Acceptance Criteria:**

**Given** Transactions have been parsed from file (Story 5.4)
**When** Duplicate detection runs
**Then** For each parsed transaction, system checks existing transactions for duplicates
**And** Duplicate detection logic compares:
  - Same user_id
  - Same account_id
  - Same transaction_date (within ±1 day tolerance)
  - Same amount (exact match)
  - Similar description (fuzzy match 90%+ similarity)
**And** If all criteria match, transaction is flagged as is_duplicate=true
**And** Duplicate transactions are NOT saved to database (skipped)
**And** Import record is updated with duplicates_found count
**And** User notification shows: "X duplicatas detectadas e ignoradas"
**And** Duplicate detection completes within overall parsing time budget (NFR6: < 30s total)
**And** Edge case: If user wants to force import duplicates, "Import Anyway" option is provided with warning
**And** Audit log records duplicate detection statistics

---

## Story 5.6: Identify Pix Transactions and Extract Details

As a system,
I want to identify Pix transactions and extract origin/destination details,
So that users can see who sent or received money via Pix.

**Acceptance Criteria:**

**Given** Transactions have been parsed (Story 5.4) and duplicates filtered (Story 5.5)
**When** Pix identification runs
**Then** For each transaction, system checks description for Pix indicators:
  - Keywords: "PIX", "Pix Recebido", "Pix Enviado", "Transferência Pix"
  - Bank-specific Pix transaction codes
**And** If Pix is detected, is_pix flag is set to true
**And** System extracts Pix details from description using regex patterns:
  - Sender/recipient name
  - CPF/CNPJ (if available)
  - Pix key type (email, phone, CPF, random key)
**And** Extracted details are stored in pix_details JSONB field: {sender_name, recipient_name, pix_key_type, key_value}
**And** If details cannot be extracted, pix_details stores minimal info: {detected: true}
**And** Pix transactions are visually marked in transaction list with Pix icon
**And** Pix identification completes within overall parsing time budget (NFR6)

---

## Story 5.7: Detect Installment Purchases and Split Across Months

As a system,
I want to detect installment purchases (parcelamento) and split them correctly across future months,
So that users' monthly budgets reflect accurate installment payments.

**Acceptance Criteria:**

**Given** Transactions have been parsed and Pix identified (Stories 5.4-5.6)
**When** Installment detection runs
**Then** For each transaction, system checks description for installment indicators:
  - Keywords: "PARC", "parcela", "X/Y" pattern (e.g., "1/12", "3/10")
  - Regex patterns: "\d+/\d+" (installment number/total)
**And** If installment is detected:
  - is_installment flag is set to true
  - installment_number is extracted (e.g., 3 from "3/12")
  - installment_total is extracted (e.g., 12 from "3/12")
**And** System calculates future installment dates:
  - If current transaction is installment 3/12, 9 future installments are created
  - Each future installment: same amount, same description, date = current_date + N months
**And** Future installments are marked with is_future_installment=true
**And** All installments are linked via installment_group_id (UUID, same for all in group)
**And** User sees in transaction list: "Compra parcelada: 3/12 - Restantes: 9 parcelas"
**And** Dashboard projections include future installments in budget calculations
**And** Installment detection completes within overall parsing time budget (NFR6)

---

## Story 5.8: Auto-Classify Transactions Using AI

As a system,
I want to automatically classify imported transactions using AI,
So that users save time and don't have to manually categorize everything.

**Acceptance Criteria:**

**Given** Transactions have been imported and processed (Stories 5.4-5.7)
**When** AI classification runs
**Then** Transactions are batched into groups of 100 for classification (NFR7)
**And** For each transaction, AI model analyzes:
  - Transaction description (original text)
  - Transaction amount
  - Transaction type (debit/credit)
  - Merchant name (if extractable)
  - User's historical classifications (if any exist)
**And** AI model predicts category_id from user's categories
**And** Classification confidence score (0-100) is calculated and stored
**And** If confidence >= 80%, transaction is auto-classified with that category
**And** If confidence < 80%, transaction is flagged for manual review
**And** Batch of 100 transactions is classified in < 3 seconds (NFR7)
**And** An `ai_classification_history` table is created (V17) to track: transaction_id, predicted_category_id, confidence, was_corrected (boolean), corrected_category_id
**And** Progress indicator shows: "Classificando transações com IA... X de Y"
**And** After classification, Story 5.9 (present for review) is triggered
**And** If AI service is unavailable, fallback: transactions marked as "Não classificado" with confidence=0

---

## Story 5.9: Present Classified Transactions for User Review

As a logged-in user,
I want to see all imported transactions with their AI classifications,
So that I can review and confirm the categorizations.

**Acceptance Criteria:**

**Given** Transactions have been classified by AI (Story 5.8)
**When** Import completes and user navigates to Review page
**Then** Transaction review screen is displayed with list of all imported transactions
**And** For each transaction, display:
  - Date
  - Description
  - Amount (with currency formatting)
  - Suggested Category (from AI)
  - Confidence indicator (High 80%+, Medium 50-79%, Low <50%)
  - Edit button
**And** Transactions are grouped by confidence: High confidence first, Low confidence last
**And** Visual indicators:
  - Green checkmark: High confidence (80%+)
  - Yellow warning: Medium confidence (50-79%)
  - Red flag: Low confidence (<50%) - needs review
**And** User can quickly review and proceed
**And** "Approve All High Confidence" button allows batch approval of 80%+ confidence transactions
**And** Count shows: "X transações de alta confiança, Y precisam de revisão"
**And** Clicking "Next" or "Continue" moves to Story 5.10 (manual review/adjustment)

---

## Story 5.10: Review and Adjust AI Classifications

As a logged-in user,
I want to review and adjust AI classifications that are incorrect,
So that my transactions are accurately categorized.

**Acceptance Criteria:**

**Given** I am on the transaction review page (Story 5.9)
**When** I click "Edit" on a transaction
**Then** Inline editor or modal appears with:
  - Current category (AI suggestion)
  - Category dropdown (all my categories)
  - Transaction details (read-only: date, description, amount)
**And** I can select a different category from dropdown
**And** Clicking "Save" updates transaction's category_id
**And** Classification confidence is reset to 100% (user override)
**And** Story 5.11 (learning) is triggered: AI records this correction
**And** Transaction is marked as "Reviewed by user"
**And** Visual indicator changes from AI-suggested to user-confirmed (different icon/color)
**And** I can bulk edit multiple transactions:
  - Select multiple transactions (checkbox)
  - Click "Change Category" button
  - Select new category
  - All selected transactions are updated
**And** After all reviews are complete, "Finish Import" button saves all changes
**And** Dashboard cache is invalidated (ARCH-REQ-14) to reflect new transactions

---

## Story 5.11: Learn from User Corrections to Improve AI

As a system,
I want to learn from user corrections to AI classifications,
So that future classifications become more accurate for each user.

**Acceptance Criteria:**

**Given** A user corrects an AI classification (Story 5.10)
**When** The correction is saved
**Then** AI classification history is updated: was_corrected=true, corrected_category_id=[user's choice]
**And** User's classification preferences are stored for future learning
**And** A `user_classification_patterns` table is created (V18) with fields: pattern_id, user_id, description_pattern (text/regex), preferred_category_id, usage_count, last_used_at
**And** System extracts key words from corrected transaction description
**And** Pattern record is created or updated: description_pattern, preferred_category_id
**And** usage_count is incremented each time this pattern is confirmed
**And** In future imports, if transaction description matches stored pattern:
  - AI uses user's preferred_category_id with boosted confidence (+20%)
  - Classification is more likely to be correct
**And** Learning is user-specific (isolated per user_id)
**And** Over time, AI accuracy improves for each individual user
**And** Analytics track: "AI accuracy: X% this month (improved from Y% last month)"

---

## Story 5.12: Manually Edit Any Transaction

As a logged-in user,
I want to edit any transaction (date, amount, description, category) manually,
So that I can correct errors or update transaction details.

**Acceptance Criteria:**

**Given** I am viewing my transaction list (in Dashboard Layer 3 or dedicated Transaction page)
**When** I click "Edit" on any transaction
**Then** Edit form/modal is displayed with all fields editable:
  - Date (date picker)
  - Description (text input)
  - Amount (currency input)
  - Category (dropdown)
  - Account (dropdown - which account this transaction belongs to)
**And** Current values are pre-filled
**And** I can modify any field and click "Save"
**And** Transaction record is updated in database
**And** updated_at timestamp is set to NOW()
**And** Audit log records "transaction_edited" event with user_id
**And** If amount is changed, account balance is recalculated
**And** If date is changed to different month, budget calculations are updated
**And** Dashboard cache is invalidated (ARCH-REQ-14)
**And** Success message: "Transação atualizada"
**And** Validation: Amount cannot be zero or negative, Date cannot be future date (warning)

---

## Story 5.13: Manually Add Transactions (Cash Expenses)

As a logged-in user,
I want to manually add transactions (for cash expenses or missed imports),
So that I have a complete record of all my spending.

**Acceptance Criteria:**

**Given** I am on the Transactions page
**When** I click "Add Transaction" button
**Then** Add transaction form/modal is displayed with fields:
  - Date (date picker, default: today)
  - Description (text input, required)
  - Amount (currency input, required)
  - Type (debit/credit radio buttons)
  - Category (dropdown, required)
  - Account (dropdown, required)
  - Payment Method (optional: Cash, Debit Card, Credit Card, Pix, Transfer)
**And** I fill in details and click "Save"
**And** New transaction is created in `transactions` table with:
  - user_id from JWT
  - import_id = NULL (manually added, not imported)
  - classification_confidence = 100 (user-defined)
  - is_duplicate = false
**And** Account balance (current_balance) is updated: -amount for debit, +amount for credit
**And** Category budget is updated: allocated amount is decreased by transaction amount
**And** Transaction appears immediately in transaction list
**And** Dashboard cache is invalidated (ARCH-REQ-14)
**And** Success message: "Transação adicionada"
**And** Form validation: Description required, Amount > 0, Category required, Account required

---
