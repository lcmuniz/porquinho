# Epic 5: Transaction Import & AI Classification

Usuários podem fazer upload de arquivos OFX/CSV dos 5 principais bancos brasileiros (Nubank, BB, Itaú, Bradesco, Caixa) com detecção automática de formato, parsing robusto, detecção de duplicatas, identificação de Pix e parcelamento, classificação automática por IA, revisão e ajuste de classificações, aprendizado contínuo com correções, e edição/adição manual de transações.

> **Revisado antes da implementação (2026-11-04, Luiz):** o texto foi alinhado ao código dos Epics 0–4 e às decisões abaixo. Ao implementar, cada story que mudar a abordagem atualiza este texto na mesma story (retro do Epic 2, P1).
>
> **Decisões:**
> - **D1 — transação de conta ou de cartão:** cada transação pertence a exatamente uma conta (`account_id`) ou um cartão (`credit_card_id`).
> - **D2 — destino escolhido antes do upload:** o usuário escolhe a conta ou o cartão antes de enviar o arquivo. O OFX só confirma ou sugere.
> - **D3 — saldo:** só transações de conta com data **posterior à data de referência do saldo** alteram `current_balance`. A data de referência é a do último snapshot `initial`, `edit` ou `reconciliation` da conta. A mudança passa sempre pelo `BalanceHistoryRecorder` (regra 8 da arquitetura). Transações de cartão nunca alteram saldo de conta.
> - **D4 — transferências já registradas:** um lançamento que corresponde a uma transferência da Story 4.5 (mesma conta, data ±1 dia, mesmo valor, sentido certo) não é importado.
> - **D5 — IA:** Claude Haiku 5.5 (`claude-haiku-5-5`) via API da Anthropic. CPF e CNPJ são mascarados antes do envio.
> - **D6 — sem arquivos reais:** os parsers são escritos e testados com amostras fictícias em `porquinho-backend/src/test/resources/import-samples/` (ver o README de lá; CSV com confiança baixa a média). O OFX é o formato principal; o CSV de cada banco é validado quando houver um arquivo real.
> - **D7 — parcelas futuras:** a parcela importada que corresponde a uma parcela futura já criada confirma essa parcela, em vez de inserir outra.
> - **D8 — pagamento de fatura:** o pagamento da fatura do cartão (débito na conta e crédito no cartão) é marcado como `CARD_PAYMENT`. Ele altera o saldo da conta pela D3, mas não conta como gasto nem receita, para as compras do cartão não entrarem em dobro.
>
> - **D9 — reconciliação:** continua sem transação de ajuste (decisão A da Story 4.6). A reconciliação já corrige o saldo e grava o histórico; uma transação de ajuste mudaria o saldo de novo.
>
> **Convenções do código atual:** PK `id` UUID, `user_id` indexado, `deleted_at`, dinheiro em `DECIMAL(15,2)`. Migrations na próxima versão livre (V25 em 2026-11-04; as do texto original, V15–V18, já existem com outro conteúdo). Toda tabela nova entra no export e no purge LGPD (Stories 1.6/1.7) na mesma story que a cria. Categorias já existem (`categories`, Story 3.4); contas, cartões, transferências e histórico de saldo também (Epic 4).
>
> **Fora deste épico:** atualizar o orçamento da categoria (Epic 6, ainda sem alocações); cache e Camadas do Dashboard GPS (Epic 7, o cache ainda não existe); métrica "precisão da IA no mês" (Epic 10).

## Story 5.1: Upload OFX Files from 5 Brazilian Banks

As a logged-in user,
I want to upload OFX files from my bank (Nubank, BB, Itaú, Bradesco, Caixa),
So that I can import my transaction history automatically.

**Acceptance Criteria:**

**Given** I am logged in and on the Import page (`/importar`, linked from the dashboard)
**When** I choose the destination account or card (D2) and click "Enviar arquivo" or drag an OFX file into the drop zone (UX-DR-43)
**Then** File upload begins with progress indicator
**And** File size is validated: max 10MB (NFR5), on the frontend and on the backend
**And** File extension is validated: .ofx accepted
**And** Upload completes in < 5 seconds for 10MB file (NFR5)
**And** The file content is only validated during the request and is never stored nor logged (LGPD); temporary storage for background processing arrives with Story 5.3 (decision 1A of Story 5.1)
**And** Content is validated, not only the extension: after BOM and whitespace the file starts with `OFXHEADER:` or `<?xml` and contains `<OFX>`
**And** `file_name` keeps only the name (no path), up to 255 characters; audit `import_created` with `import_id`, `file_type` and `file_size_kb` (no file name)
**And** A `transaction_imports` table is created via migration (next free version) with fields: id (UUID, PK), user_id (FK), account_id (FK, nullable), credit_card_id (FK, nullable; exactly one of the two), file_name, file_type (OFX/CSV), bank_name, file_size_kb, status (PROCESSING/REVIEW/COMPLETED/FAILED/DISCARDED), total_transactions, imported_transactions, duplicates_found, skipped_rows, error_message, created_at, updated_at
**And** Import record is created with status='PROCESSING' and nothing runs in the background yet (decision 1A, Luiz, 2026-11-04): the list on `/importar` shows this status as "Recebido — a leitura das transações chega na próxima etapa". Background processing and status polling every 2 seconds (NFR8) arrive with Story 5.3
**And** Message: "Arquivo enviado, processando..."
**And** If file size > 10MB, error: "Arquivo muito grande (máximo 10MB)"
**And** If file format invalid, error: "Formato de arquivo não suportado"
**And** The OFX account type must match the destination: a credit card OFX (`CCSTMTRS`) into a bank account, or the reverse, is rejected with a clear message
**And** The upload endpoint is `POST /api/v1/imports` (multipart: `file` plus exactly one of `accountId`/`creditCardId`) and `GET /api/v1/imports` lists the 20 most recent imports with the destination name
**And** A destination of another user, or removed, returns 404 ("Conta não encontrada" / "Cartão não encontrado") and nothing is written
**And** Onboarding step 6 (`ImportStep.vue`) is **not** changed here (decision 2A, Luiz, 2026-11-04); it is wired to the upload in Story 5.10
**And** `transaction_imports` is included in data export and purge (LGPD)

---

## Story 5.2: Upload CSV Files from 5 Brazilian Banks

As a logged-in user,
I want to upload CSV files from my bank (Nubank, BB, Itaú, Bradesco, Caixa),
So that I can import transactions in CSV format.

**Acceptance Criteria:**

**Given** I am logged in and on the Import page
**When** I choose the destination and send a CSV file
**Then** File size and processing behave as in Stories 5.1 and 5.3 (10MB, background processing, file discarded afterwards)
**And** File extension is validated: .csv accepted
**And** CSV encoding is detected (UTF-8 with or without BOM, ISO-8859-1, Windows-1252); if it cannot be detected, UTF-8 is assumed
**And** Separator is detected (comma, semicolon, tab), including quoted fields
**And** Import record is created in `transaction_imports` with file_type='CSV'
**And** Layouts supported at first are those of the samples (`import-samples/README.md`): Nubank account and card, BB, Itaú, Bradesco and Caixa, including title/total/balance lines that are not transactions (BB "Saldo Anterior"/"S A L D O", Bradesco header and "Total"), separate credit/debit columns (Bradesco), D/C column (Caixa), and decimal comma with thousands separator
**And** Decision 2A (Luiz, 2026-11-05): the layout (header or, for Itaú, line shape matched against the layouts above) and the destination are checked at upload, like the OFX: an unknown layout is 400 with the suggestion to export in OFX (NFR57): "Não reconhecemos o formato deste CSV. Tente exportar o extrato em OFX."; a card layout sent to an account, or the opposite, is 400 with the wrong-destination messages of Story 5.1. Nothing is written. Recording the bank and reading the rows stay in the background processing of Stories 5.3/5.4
**And** Decision 1A (Luiz, 2026-11-05): no manual choice of bank. In the CSV the recognized layout defines the bank; in the OFX the bank does not change the reading (`OTHER` stays "Banco não identificado"). The former "Detectamos [Banco]. Está correto?" criterion is removed from the epic

---

## Story 5.3: Auto-Detect File Format and Bank

As a system,
I want to automatically detect the file format (OFX/CSV) and originating bank,
So that users don't have to manually specify the source.

**Implementation order (Luiz, 2026-11-04):** Story 5.3 is built before Story 5.2 and covers OFX only. CSV detection (header or line shape against the bank layouts) moves to Story 5.2. The manual choice of bank was dropped (Story 5.2, decision 1A, Luiz, 2026-11-05).

**Acceptance Criteria:**

**Given** An OFX file has been uploaded (Story 5.1)
**When** The import row is committed
**Then** Processing runs in the background, outside the request thread: the file content is kept only in memory during processing and is never written to disk, database or log (LGPD); logs carry only the import id and the reason
**And** The bank is read from `BANKID` (leading zeros ignored; when present it decides alone), else `FID`, else `ORG` by keyword, and recorded in `bank_name` as `NUBANK` (260), `BANCO_DO_BRASIL` (001), `ITAU` (341), `BRADESCO` (237) or `CAIXA` (104); accuracy is 100% on the OFX samples (NFR56 asks 95%+)
**And** An OFX from a bank outside these five is recorded as `OTHER` and shown as "Banco não identificado" (decision 2A, Luiz, 2026-11-04)
**And** After detection the import stays `PROCESSING` with `bank_name` set (decision 1A, Luiz, 2026-11-04): the list on `/importar` shows "Banco detectado: <banco> — a leitura das transações chega na próxima etapa"; Story 5.4 extends the same processing with the parsing
**And** A truncated OFX (no `</OFX>`) becomes `FAILED` with "O arquivo está incompleto. Exporte o extrato de novo no site ou app do banco e envie outra vez." (NFR57); any unexpected error becomes `FAILED` with "Não foi possível processar o arquivo. Tente de novo."
**And** Imports left `PROCESSING` with no bank yet and no processing running become `FAILED` with "O processamento foi interrompido. Envie o arquivo de novo.": at backend startup all those created before it came up, and every 5 minutes those older than 15 minutes
**And** An empty file or one whose content is not OFX is already rejected at upload (Story 5.1)
**And** `GET /api/v1/imports/{id}` returns one of the user's imports (404 "Importação não encontrada" otherwise); `/importar` polls it every 2 seconds (NFR8) only while an import has no bank yet and is `PROCESSING`, and stops when leaving the page

---

## Story 5.4: Parse Transactions with Robust Error Handling

As a system,
I want to parse transaction data from uploaded files with robust error handling,
So that users can import transactions even if files have minor issues.

**Acceptance Criteria:**

**Given** File format and bank have been detected (Story 5.3)
**When** Parsing begins
**Then** A `transactions` table is created via migration (next free version) with fields: id (UUID, PK), user_id (FK), account_id (FK, nullable), credit_card_id (FK, nullable), CHECK exactly one of the two (D1), import_id (FK, nullable), external_id (FITID or bank identifier, nullable), transaction_date, description, original_description, amount DECIMAL(15,2) > 0, transaction_type (DEBIT/CREDIT), kind (REGULAR/CARD_PAYMENT, D8), category_id (FK, nullable), classification_source (AI/PATTERN/USER/NONE), classification_confidence (0-100), review_status (PENDING/CONFIRMED), affects_balance (boolean, D3), is_pix, pix_details (JSONB), is_installment, installment_number, installment_total, installment_group_id (UUID), is_future_installment, created_at, updated_at, deleted_at
**And** For each transaction, extracts: date, description, amount (absolute value) and type (DEBIT/CREDIT from sign, D/C column or credit/debit columns); for cards, purchases are DEBIT and payments/refunds CREDIT
**And** Parsing completes in < 30 seconds for 1 year of transactions (NFR6)
**And** Error handling for malformed rows: skip the row and log it (no personal data in logs), keep parsing; invalid date tries the bank's known formats before skipping (OFX dates are always AAAAMMDD; the other formats apply to CSV, Story 5.2); missing or invalid amount skips the row
**And** Import record is updated with total_transactions and skipped_rows
**And** Transactions are saved with review_status='PENDING' and do not appear in lists, totals or balances until the import is finished (Story 5.10)
**And** After parsing, Stories 5.5–5.7 (duplicates, Pix, installments) run in sequence
**And** If parsing fails completely, status='FAILED' and error_message is saved
**And** User sees: "Arquivo processado: X transações importadas, Y duplicadas ignoradas, Z linhas com erro"
**And** `transactions` is included in data export and purge (LGPD), deleted before `accounts`/`credit_cards`
**And** Decision 1A (Luiz, 2026-11-04): once the transactions are saved, the import goes to `REVIEW` (only from `PROCESSING`); the list on `/importar` shows "Pronta para revisão: X transações (Y linhas com erro) — a tela de revisão chega numa próxima etapa" and the 2-second polling stops. `PROCESSING` means processing is running again: the orphan sweep (startup and 15 min) fails every old `PROCESSING` import, with or without `bank_name`. Stories 5.5–5.8 run before `REVIEW`
**And** Decision 2A (Luiz, 2026-11-04): no real progress in this story. While `PROCESSING` with a bank, the list shows "Banco detectado: <banco> — lendo as transações..." and keeps polling; counts only at the end. The progress column ("X de Y", NFR8) comes in Story 5.8
**And** Decision 3A (Luiz, 2026-11-04): `kind = CARD_PAYMENT` is set while parsing by keyword in the description (without accents, upper case): "PAGAMENTO DA FATURA", "PAGAMENTO EFETUADO", "PAGAMENTO RECEBIDO", "ITAU UNIBANCO CARTAO", "GASTOS CARTAO DE CREDITO" (and "PAGAMENTO CARTAO DE CREDITO", added by Story 5.2); on an account only for `DEBIT`, on a card only for `CREDIT`. Everything else is `REGULAR`

---

## Story 5.5: Detect and Flag Duplicate Transactions

As a system,
I want to detect duplicate transactions during import,
So that users don't have duplicate entries inflating their data.

**Acceptance Criteria:**

**Given** Transactions have been parsed from file (Story 5.4)
**When** Duplicate detection runs
**Then** A transaction with exactly the same external_id (FITID/identifier, not null) on the same account or card is a duplicate
**And** When either external_id is missing, or both exist but differ and the existing transaction came from an import of the other format (OFX × CSV: banks use different ids in each format; within the same format a different id is a different transaction, decision 4A), it is a duplicate when it matches an existing transaction of the same account or card on: date ±1 day, same amount and type, similar description (Levenshtein similarity 90%+ after normalization, or the shorter text with 10+ characters is a prefix of the longer one — the BB OFX truncates the MEMO) (rule P1)
**And** Each existing transaction or transfer absorbs at most one entry per import; entries of the same file are not compared with each other
**And** Duplicates are NOT saved
**And** A debit/credit that matches a transfer already registered in Story 4.5 (same account, date ±1 day, same amount, matching direction) is not saved either and is counted apart: "N transferências já registradas" (D4)
**And** Import record is updated with duplicates_found
**And** User notification shows: "X duplicatas detectadas e ignoradas"
**And** Importing the OFX and the CSV of the same month (samples) into the same account flags every transaction of the second file as duplicate
**And** Duplicate detection completes within overall parsing time budget (NFR6: < 30s total)
**And** Audit log records duplicate detection statistics (counts only)

---

## Story 5.6: Identify Pix Transactions and Extract Details

As a system,
I want to identify Pix transactions and extract origin/destination details,
So that users can see who sent or received money via Pix.

**Acceptance Criteria:**

**Given** Transactions have been parsed (Story 5.4) and duplicates filtered (Story 5.5)
**When** Pix identification runs
**Then** For each transaction, system checks description for Pix indicators per bank (samples: Nubank "Transferência enviada/recebida pelo Pix", Itaú "PIX TRANSF"/"PIX QRS", BB "Pix - Enviado/Recebido", Bradesco "TRANSFERENCIA PIX DES:/REM:", Caixa "ENVIO PIX"/"PIX RECEBIDO"); every bank pattern is tried on every import, whatever the detected bank (OTHER and cards included)
**And** As a fallback, the word "pix" alone (word boundary, case-insensitive, not "PIXEL") also marks Pix, without extraction
**And** If Pix is detected, is_pix flag is set to true
**And** System extracts, when present: counterpart name, masked document (CPF as the bank shows it, CNPJ; a full CPF is masked as `•••.XXX.XXX-••`), counterpart bank/agency/account
**And** pix_details JSONB always has `detected: true` and `direction` (from transaction_type: DEBIT → SENT, CREDIT → RECEIVED, never from the words of the description), plus `counterpart_name`, `document` and `counterpart_bank` ({name, code, agency, account}) only when present; a missing key is omitted, never null
**And** Non-Pix transactions keep is_pix=false and pix_details null
**And** The Pix icon is not part of this story (decision 1A of Story 5.6): it moved to Stories 5.9 and 5.12
**And** Pix identification completes within overall parsing time budget (NFR6): precompiled patterns, no database query

---

## Story 5.7: Detect Installment Purchases and Split Across Months

As a system,
I want to detect installment purchases (parcelamento) and split them correctly across future months,
So that users' monthly budgets reflect accurate installment payments.

**Acceptance Criteria:**

**Given** Card transactions have been parsed (Stories 5.4–5.6)
**When** Installment detection runs
**Then** Installment indicators are recognized ("Parcela 3/10", "PARC 03/10", trailing "03/10"/"07/12" after a space in card descriptions), without mistaking dates such as "05/10" in account descriptions (detection only runs on card imports, on purchases: DEBIT with kind REGULAR)
**And** An indicator is valid only when 1 ≤ number ≤ total and 2 ≤ total ≤ 48
**And** If installment is detected: is_installment=true, installment_number and installment_total are extracted
**And** Future installments are created for the remaining months (installment 3/10 → 7 future ones): same amount, description with the number replaced keeping the bank's digits ("03/10" → "04/10"), date = transaction date + N months, is_future_installment=true, external_id null, review_status follows the import, import_id of the import that created them
**And** All installments of a purchase share installment_group_id
**And** An installment joins an existing group of the same card with the same total, an amount at most the highest of R$ 0,05 and R$ 0,01 × (total − 1) apart, inclusive (rounding cents, decision 3A of Story 5.7), a similar base description (original description without the marker) and a date at most 20 days from the date expected for its number (decision 4A of Story 5.7: a repeated purchase, such as next year's annual fee, opens its own group), whose number is free or still future; the future installments created are those after its number up to the total that the group does not have yet (an older month imported later creates none)
**And** When a later import brings an installment that matches a future one already created, it confirms that future installment (is_future_installment=false; external_id, date, amount, description, original description, is_pix and pix_details from the file) instead of inserting another (D7); the confirmed row keeps its original import and counts as a duplicate of the new import (decision 2A of Story 5.7)
**And** Future installments are not candidates of the duplicate check (Story 5.5)
**And** The text "Compra parcelada: 3/10 — restam 7 parcelas" is not part of this story (decision 1A of Story 5.7): it moved to Stories 5.9 and 5.12
**And** Future installments are not counted as spending of the current month (projections are Epic 7)
**And** This story does not detect refunds, early payoff or a deleted card (decision 5A of Story 5.7): Epics 6 and 7 ignore the future installments of a deleted card, which count again if the card is restored, and Story 5.12 lets the user remove the future installments of a purchase
**And** Installment detection completes within overall parsing time budget (NFR6): precompiled patterns, one query for the card's installments

---

## Story 5.8: Auto-Classify Transactions Using AI

As a system,
I want to automatically classify imported transactions using AI,
So that users save time and don't have to manually categorize everything.

**Acceptance Criteria:**

**Given** Transactions have been imported and processed (Stories 5.4–5.7)
**When** Classification runs
**Then** Transfers are not imported (D4) and CARD_PAYMENT transactions (D8) are not sent to the AI
**And** The remaining transactions are sent in batches of up to 100 (NFR7) to Claude Haiku 5.5 (`claude-haiku-5-5`) with: description with CPF/CNPJ masked, amount, type, account or card, and the user's active categories (id, name, group); the API key stays on the backend
**And** The model returns, per transaction, a category from the user's list (or none) and a confidence 0–100; any category outside the list is discarded
**And** If confidence >= 80, the category is applied with classification_source='AI'; below 80, the transaction is flagged for review with the suggestion kept
**And** Batch of 100 transactions is classified in < 3 seconds (NFR7)
**And** An `ai_classification_history` table is created via migration (next free version): id, user_id, transaction_id, predicted_category_id, confidence, was_corrected, corrected_category_id, created_at; included in export and purge (LGPD)
**And** Progress indicator shows: "Classificando transações com IA... X de Y"
**And** Progress is updated every 2 seconds: "X de Y transações processadas" (NFR8), moved from Story 5.4 (decision 2A of 5.4), with the progress column it needs
**And** If the AI service is unavailable or times out, the transactions remain "Não classificado" (confidence 0) and the import still reaches review
**And** The privacy policy (`PrivacyView`) names the AI provider as a data processor of transaction descriptions
**And** After classification, the import status becomes 'REVIEW' and Story 5.9 is shown
**And** User patterns are not applied here: `user_classification_patterns` does not exist yet, and Story 5.11 starts applying them before the AI call
**And** Decision 1A (Luiz, 2026-11-06): the future installments created by the import inherit category, source and confidence from the installment in the file (same group, same import). They do not go to the AI, do not count in Y and get no history row
**And** Decision 2A (Luiz, 2026-11-06): this story delivers sequential batches of up to 100, with no real-time test. NFR7 stays pending until there is an `ANTHROPIC_API_KEY` (see deferred-work)

---

## Story 5.9: Present Classified Transactions for User Review

As a logged-in user,
I want to see all imported transactions with their AI classifications,
So that I can review and confirm the categorizations.

**Acceptance Criteria:**

**Given** An import is in status 'REVIEW' (Story 5.8)
**When** I open the review screen (`/importar/:id/revisao`, or the result of onboarding step 6)
**Then** For each transaction, display: date, description, amount (BRL), suggested category, confidence indicator (High 80%+, Medium 50–79%, Low <50%), Pix and installment markers, edit button
**And** Transactions with is_pix=true show a Pix icon (with text alternative, never icon alone); the API exposes `isPix` and `pixDetails` (moved from Story 5.6, decision 1A)
**And** Installments show "Compra parcelada: 3/10 — restam 7 parcelas"; the API exposes `isInstallment`, `installmentNumber`, `installmentTotal` and `isFutureInstallment` (moved from Story 5.7, decision 1A)
**And** Transactions are grouped by confidence: High first, Low last; CARD_PAYMENT shown apart as "Pagamento de fatura"
**And** Visual indicators: green check (High), yellow warning (Medium), red flag (Low, needs review); never color alone (WCAG)
**And** "Aprovar todas de alta confiança" confirms all 80%+ transactions at once
**And** Count shows: "X transações de alta confiança, Y precisam de revisão"
**And** An unfinished import stays in REVIEW and can be resumed from `/importar`, or discarded ("Descartar importação"), which deletes its pending transactions and future installments
**And** "Continuar" moves to Story 5.10

---

## Story 5.10: Review and Adjust AI Classifications

As a logged-in user,
I want to review and adjust AI classifications that are incorrect,
So that my transactions are accurately categorized.

**Acceptance Criteria:**

**Given** I am on the review screen (Story 5.9)
**When** I click "Editar" on a transaction
**Then** Inline editor or modal shows the current category, a dropdown of my categories and read-only date, description and amount
**And** Saving a different category updates category_id, sets classification_source='USER', confidence 100, and triggers Story 5.11
**And** The transaction is marked as reviewed by the user, with a different icon/color
**And** Bulk edit: select several (checkbox), "Alterar categoria", choose, all selected are updated (each correction feeds Story 5.11)
**And** "Concluir importação" confirms the import in one transaction: review_status='CONFIRMED' for all its transactions, import status='COMPLETED', and the balance effect of D3 (affects_balance and one `transaction` snapshot per changed account through `BalanceHistoryRecorder`)
**And** Transactions left without category are confirmed as "Não classificado"
**And** Finishing an import twice, or concurrently, changes nothing the second time
**And** Onboarding step 6 (`ImportStep.vue`) uses the same upload component and endpoint as `/importar`; finishing that first import records `first_import_completed` through `POST /api/v1/onboarding/me/import` (deferred from Story 3.6; moved here by decision 2A of Story 5.1)

---

## Story 5.11: Learn from User Corrections to Improve AI

As a system,
I want to learn from user corrections to AI classifications,
So that future classifications become more accurate for each user.

**Acceptance Criteria:**

**Given** A user corrects a classification (Story 5.10 or 5.12)
**When** The correction is saved
**Then** `ai_classification_history` is updated: was_corrected=true, corrected_category_id
**And** A `user_classification_patterns` table is created via migration (next free version): id, user_id, description_pattern (normalized text: without dates, numbers, document and installment markers), preferred_category_id, usage_count, last_used_at, created_at; included in export and purge (LGPD)
**And** Pattern record is created or updated with the corrected category; usage_count is incremented each time the pattern is confirmed
**And** In future imports, a matching pattern classifies the transaction without the AI (Story 5.8), with classification_source='PATTERN'
**And** A pattern whose category was deleted is ignored
**And** Learning is user-specific (isolated per user_id)

---

## Story 5.12: Manually Edit Any Transaction

As a logged-in user,
I want to edit any transaction (date, amount, description, category) manually,
So that I can correct errors or update transaction details.

**Acceptance Criteria:**

**Given** I am on the Transactions page (`/transacoes`, linked from the dashboard; Dashboard Layer 3 is Epic 7)
**When** I click "Editar" on a confirmed transaction
**Then** Edit form/modal is displayed with: date, description, amount, type, category, account or card
**And** Current values are pre-filled
**And** Saving updates the transaction and updated_at
**And** Audit log records "transaction_edited" with user_id
**And** If amount, type, date or account changes on a transaction with balance effect, the balance is adjusted by the difference through `BalanceHistoryRecorder` (D3 recomputed for the new date and account)
**And** Changing the category feeds Story 5.11
**And** Validation: amount > 0; future date shows a warning
**And** Success message: "Transação atualizada"
**And** Removing a transaction (soft delete) reverses its balance effect

**Given** I am on the Transactions page (`/transacoes`)
**When** The transaction list is displayed
**Then** Transactions with is_pix=true show a Pix icon (with text alternative, never icon alone)
**And** The API exposes `isPix` and `pixDetails` (moved from Story 5.6, decision 1A)
**And** Installments show "Compra parcelada: 3/10 — restam 7 parcelas"; the API exposes `isInstallment`, `installmentNumber`, `installmentTotal` and `isFutureInstallment` (moved from Story 5.7, decision 1A)

**Given** A purchase has future installments (is_future_installment=true)
**When** I use the action "Remover parcelas futuras desta compra" on any installment of that purchase
**Then** The future installments of its installment_group_id are removed (soft delete) and the confirmed installments stay as they are, so I can handle a refund or an early payoff (decision 5A of Story 5.7)

---

## Story 5.13: Manually Add Transactions (Cash Expenses)

As a logged-in user,
I want to manually add transactions (for cash expenses or missed imports),
So that I have a complete record of all my spending.

**Acceptance Criteria:**

**Given** I am on the Transactions page
**When** I click "Adicionar transação"
**Then** Add transaction form/modal is displayed with fields:
  - Date (default: today)
  - Description (required)
  - Amount (required, > 0)
  - Type (debit/credit)
  - Category (required)
  - Account or card (required)
  - Payment method (optional: Dinheiro, Débito, Crédito, Pix, Transferência)
**And** New transaction is created with user_id from JWT, import_id NULL, classification_source='USER', confidence 100, review_status='CONFIRMED'
**And** The balance effect follows D3: an account transaction dated after the balance reference date changes `current_balance` through `BalanceHistoryRecorder`; card transactions do not
**And** Transaction appears immediately in the list
**And** Success message: "Transação adicionada"
**And** Form validation: description required, amount > 0, category required, account or card required
**And** Category budget update is Epic 6
