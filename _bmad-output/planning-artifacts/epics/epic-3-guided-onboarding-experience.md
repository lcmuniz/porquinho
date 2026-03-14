# Epic 3: Guided Onboarding Experience

Novos usuários são guiados através de onboarding interativo que explica o conceito de orçamento por envelope, permite configurar contas bancárias e cartões, sugerir e personalizar categorias iniciais, fazer primeira importação de transações, entender o Dashboard GPS de 3 camadas, e criar meta inicial de Reserva de Emergência.

## Story 3.1: Display Welcome & Envelope Budget Concept Tutorial

As a new user who just registered,
I want to see a welcome tutorial explaining the envelope budget concept,
So that I understand how the system works before I start using it.

**Acceptance Criteria:**

**Given** I complete registration (Epic 1, Story 1.1 or 1.2)
**When** I am redirected to the onboarding flow
**Then** An `onboarding_progress` table is created via migration (V7__create_onboarding_progress_table.sql) with fields: progress_id (UUID, PK), user_id (FK), current_step (integer), steps_completed (JSON array), is_completed (boolean), created_at, updated_at
**And** Onboarding progress record is created with current_step=1, is_completed=false
**And** Welcome screen is displayed with: "Bem-vindo ao porquinho!"
**And** Interactive tutorial explains envelope budget concept with visual diagrams
**And** Tutorial content includes:
  - What is envelope budgeting (traditional concept)
  - How it works digitally (money allocated to categories = envelopes)
  - Why it's effective (visual control, no overspending)
  - 3-5 screens with illustrations and simple language (NFR51)
**And** "Next" and "Skip Tutorial" buttons are visible
**And** Progress indicator shows "Step 1 of 8"
**And** Clicking "Next" advances to Story 3.2
**And** Clicking "Skip Tutorial" shows confirmation: "Are you sure? This helps you get started"
**And** If skipped, onboarding_progress is updated but user proceeds to Step 2
**And** Tutorial completion is tracked for analytics (NFR74 metric)

---

## Story 3.2: Add Bank Accounts During Setup

As a new user in onboarding,
I want to add my bank accounts during initial setup,
So that I can track all my finances in one place.

**Acceptance Criteria:**

**Given** I completed Step 1 (Story 3.1) or skipped it
**When** I reach Step 2 of onboarding
**Then** An `accounts` table is created via migration (V8__create_accounts_table.sql) with fields: account_id (UUID, PK), user_id (FK), account_name, account_type (checking/savings), initial_balance (decimal), current_balance (decimal), bank_name, created_at, updated_at, deleted_at
**And** Form is displayed: "Adicione suas contas bancárias"
**And** Form includes fields: Account Name (text), Bank Name (select or text), Account Type (checking/savings), Initial Balance (currency input)
**And** I can add multiple accounts via "Add Another Account" button
**And** Accounts are saved to `accounts` table with user_id from JWT
**And** Initial_balance and current_balance are set to the same value
**And** At least 1 account is required to proceed (validation)
**And** Progress indicator shows "Step 2 of 8"
**And** Clicking "Next" advances to Story 3.3
**And** Clicking "Back" returns to Step 1
**And** Onboarding_progress is updated: current_step=2, steps_completed includes 'accounts_added'
**And** If user skips, generic account "Minha Conta Principal" is created with balance=0

---

## Story 3.3: Add Credit Cards During Setup

As a new user in onboarding,
I want to add my credit cards during initial setup,
So that I can track credit card expenses alongside bank accounts.

**Acceptance Criteria:**

**Given** I completed Step 2 (Story 3.2)
**When** I reach Step 3 of onboarding
**Then** A `credit_cards` table is created via migration (V9__create_credit_cards_table.sql) with fields: card_id (UUID, PK), user_id (FK), card_name, card_limit (decimal), due_day (integer 1-31), bank_name, created_at, updated_at, deleted_at
**And** Form is displayed: "Adicione seus cartões de crédito"
**And** Form includes fields: Card Name (text), Bank Name (text), Credit Limit (currency), Due Day (1-31)
**And** I can add multiple cards via "Add Another Card" button
**And** Cards are saved to `credit_cards` table with user_id from JWT
**And** Validation: due_day must be between 1 and 31
**And** Progress indicator shows "Step 3 of 8"
**And** Clicking "Next" advances to Story 3.4
**And** Clicking "Skip" is allowed (credit cards are optional)
**And** Onboarding_progress is updated: current_step=3, steps_completed includes 'cards_added' (if any added)

---

## Story 3.4: Suggest Initial Spending Categories

As a new user in onboarding,
I want to see suggested spending categories,
So that I don't have to create everything from scratch.

**Acceptance Criteria:**

**Given** I completed Step 3 (Story 3.3)
**When** I reach Step 4 of onboarding
**Then** A `categories` table is created via migration (V10__create_categories_table.sql) with fields: category_id (UUID, PK), user_id (FK), category_name, parent_category_id (FK, nullable for hierarchy), color_hex, icon_name, is_system_default (boolean), created_at, updated_at, deleted_at
**And** Screen is displayed: "Categorias de gastos sugeridas"
**And** System displays pre-defined category suggestions based on common patterns:
  - Essenciais: Moradia, Alimentação, Transporte, Saúde
  - Variáveis: Lazer, Educação, Vestuário, Assinaturas
  - Financeiro: Investimentos, Reserva de Emergência
**And** Each category is displayed as a card with checkbox, name, icon, and color
**And** All categories are checked by default
**And** I can uncheck categories I don't want
**And** Progress indicator shows "Step 4 of 8"
**And** Clicking "Next" creates checked categories in `categories` table with user_id
**And** Onboarding_progress is updated: current_step=4, steps_completed includes 'categories_suggested'
**And** At least 1 category must remain checked (validation)

---

## Story 3.5: Customize Suggested Categories

As a new user in onboarding,
I want to customize the suggested categories (rename, add new, change colors),
So that they match my personal financial structure.

**Acceptance Criteria:**

**Given** I completed Step 4 (Story 3.4) and categories were created
**When** I reach Step 5 of onboarding
**Then** Screen is displayed: "Personalize suas categorias"
**And** All selected categories from Step 4 are listed
**And** For each category, I can:
  - Edit name (inline text edit)
  - Change color (color picker with palette)
  - Change icon (icon picker with common financial icons from Lucide)
  - Delete category (with confirmation)
**And** "Add New Category" button allows creating custom categories
**And** Changes are saved to `categories` table immediately (auto-save)
**And** Progress indicator shows "Step 5 of 8"
**And** Clicking "Next" advances to Story 3.6
**And** Clicking "Skip Customization" proceeds with default settings
**And** Onboarding_progress is updated: current_step=5, steps_completed includes 'categories_customized'

---

## Story 3.6: Guided First Transaction Import

As a new user in onboarding,
I want guidance on importing my first bank transaction file,
So that I can see how the system processes and classifies transactions.

**Acceptance Criteria:**

**Given** I completed Step 5 (Story 3.5)
**When** I reach Step 6 of onboarding
**Then** Screen is displayed: "Importe suas transações"
**And** Instructional text explains: "Baixe o extrato do seu banco em formato OFX ou CSV e faça upload aqui"
**And** Links to help docs for each of 5 supported banks (Nubank, BB, Itaú, Bradesco, Caixa) are provided
**And** Drag-and-drop upload area is displayed (UX-DR-43)
**And** I can click "Upload File" or drag file into the zone
**And** File upload triggers Epic 5 Story 5.1-5.3 logic (format detection, parsing, duplicate detection)
**And** Progress bar shows file processing status (updated every 2 seconds per NFR8)
**And** After processing, I see: "X transações importadas com sucesso"
**And** Preview of first 5-10 transactions is shown with AI classifications
**And** Progress indicator shows "Step 6 of 8"
**And** Clicking "Next" advances to Story 3.7
**And** Clicking "Skip for Now" allows proceeding without importing (can import later)
**And** Onboarding_progress is updated: current_step=6, steps_completed includes 'first_import_completed' (if uploaded)

---

## Story 3.7: Explain Dashboard GPS 3-Layer Navigation

As a new user in onboarding,
I want to understand the 3-layer Dashboard GPS navigation,
So that I know how to use the main feature of the product.

**Acceptance Criteria:**

**Given** I completed Step 6 (Story 3.6)
**When** I reach Step 7 of onboarding
**Then** Screen is displayed: "Conheça o Dashboard GPS"
**And** Interactive tutorial explains the 3 layers:
  - **Layer 1 (Overview):** "Onde estou?" - 5 métricas essenciais, 30 segundos para clareza total
  - **Layer 2 (Tactical):** "Para onde vou?" - Gráficos por categoria, tendências
  - **Layer 3 (Details):** "Preciso ajustar?" - Todas as transações, filtros, edição
**And** Visual diagram shows scroll vertical navigation between layers
**And** Animated demo or screenshots show each layer
**And** Tooltip explains: "Role verticalmente para navegar entre camadas"
**And** Emphasis on: "Seu check-in semanal leva apenas 5 minutos"
**And** Progress indicator shows "Step 7 of 8"
**And** Clicking "Next" advances to Story 3.8
**And** Onboarding_progress is updated: current_step=7, steps_completed includes 'dashboard_gps_explained'

---

## Story 3.8: Create Initial Emergency Fund Goal

As a new user in onboarding,
I want to create my first financial goal (Emergency Fund),
So that I start building healthy financial habits immediately.

**Acceptance Criteria:**

**Given** I completed Step 7 (Story 3.7)
**When** I reach Step 8 of onboarding (final step)
**Then** A `goals` table is created via migration (V11__create_goals_table.sql) with fields: goal_id (UUID, PK), user_id (FK), goal_name, target_amount (decimal), current_amount (decimal), target_date, monthly_allocation (decimal), is_completed (boolean), created_at, updated_at, deleted_at
**And** Screen is displayed: "Crie sua primeira meta: Reserva de Emergência"
**And** Form includes fields:
  - Goal Name (pre-filled: "Reserva de Emergência")
  - Target Amount (suggested: R$ 25.000 based on primary user persona, editable)
  - Target Date (date picker, suggested: 12 months from now)
  - Monthly Allocation (auto-calculated: target_amount / months_until_target_date, editable)
**And** Visual preview shows goal progress bar (currently 0%) and projected completion date
**And** Clicking "Create Goal" saves to `goals` table with user_id
**And** Progress indicator shows "Step 8 of 8"
**And** Clicking "Finish Onboarding" completes onboarding flow
**And** Onboarding_progress is updated: current_step=8, steps_completed includes 'emergency_fund_created', is_completed=true
**And** User is redirected to Dashboard GPS (Layer 1)
**And** Confetti animation or success message: "Parabéns! Sua conta está configurada"
**And** Onboarding completion is tracked for analytics (NFR74: 70%+ target)
**And** Clicking "Skip for Now" allows proceeding without creating goal

---
