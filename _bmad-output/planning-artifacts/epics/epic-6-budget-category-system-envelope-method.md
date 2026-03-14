# Epic 6: Budget & Category System (Envelope Method)

Usuários podem criar categorias personalizadas de gastos organizadas hierarquicamente, definir alocação mensal de orçamento por categoria, visualizar saldo disponível em tempo real, realocar dinheiro entre categorias quando necessário, ver dedução automática ao classificar transações, receber sinais visuais de categorias com saldo baixo/negativo, visualizar histórico de alocações, e comparar planejado vs real.

## Story 6.1: Create Custom Spending Categories

As a logged-in user,
I want to create custom spending categories beyond the onboarding defaults,
So that I can organize my budget according to my specific needs.

**Acceptance Criteria:**

**Given** I am logged in and on the Categories management page
**When** I click "Add Category"
**Then** Create category modal/form is displayed with fields:
  - Category Name (text, required)
  - Color (color picker, optional - default assigned if not selected)
  - Icon (icon picker from Lucide library, optional - default assigned)
  - Parent Category (dropdown, optional - for hierarchy in Story 6.2)
**And** I fill in name and click "Save"
**And** New category is saved to `categories` table (created in Epic 3, Story 3.4) with user_id from JWT
**And** Category appears in my categories list immediately
**And** Category is available in transaction classification dropdowns
**And** I can create unlimited categories (no artificial limit per NFR30)
**And** Form validation: Category Name required, Name must be unique per user
**And** Success message: "Categoria criada"
**And** Default color is assigned from predefined palette if not selected
**And** Default icon is "folder" if not selected

---

## Story 6.2: Organize Categories in Hierarchical Groups

As a logged-in user,
I want to organize my categories in hierarchical groups (parent/child),
So that I can have nested category structures like "Alimentação > Restaurantes".

**Acceptance Criteria:**

**Given** I have multiple categories created
**When** I edit a category or create a new one
**Then** Parent Category dropdown shows all my categories (excluding itself to prevent circular reference)
**And** I can select a parent category to create a hierarchy
**And** Category record is updated with parent_category_id (FK to categories table)
**And** Categories list displays hierarchy with visual indentation
**And** Parent categories can be collapsed/expanded to show/hide children
**And** Budget allocation can be set at both parent and child levels
**And** Parent category total = sum of all child categories + parent's own allocation
**And** Transaction classification can use either parent or child categories
**And** Dashboard displays hierarchical categories with roll-up totals
**And** Validation: Cannot set a category as its own parent
**And** Validation: Cannot create circular hierarchy (A → B → A)
**And** Maximum hierarchy depth: 3 levels (to avoid complexity)

---

## Story 6.3: Allocate Monthly Budget Per Category

As a logged-in user,
I want to allocate how much money to assign to each category at the start of each month,
So that I implement the envelope budgeting method.

**Acceptance Criteria:**

**Given** I am logged in and it's a new month (or I want to set budget for current month)
**When** I navigate to "Budget Allocation" page
**Then** A `budget_allocations` table is created via migration (V19__create_budget_allocations_table.sql) with fields: allocation_id (UUID, PK), user_id (FK), category_id (FK), month_year (date, format: YYYY-MM-01), allocated_amount (decimal), spent_amount (decimal, calculated), remaining_amount (decimal, calculated), created_at, updated_at
**And** Budget allocation interface is displayed showing:
  - Current month/year
  - List of all my categories
  - Input field for "Allocated Amount" next to each category
  - Total allocated vs total available (from accounts)
**And** I enter allocation amounts for each category
**And** System validates: Total allocated <= Total available in accounts
**And** Clicking "Save Monthly Budget" creates/updates budget_allocation records for current month
**And** Each category shows: Allocated, Spent (from transactions), Remaining
**And** Visual progress bar shows: Spent / Allocated ratio
**And** If total allocated > total available, warning: "Você está alocando mais do que tem disponível"
**And** Allocations can be edited throughout the month
**And** Previous month's allocations can be copied: "Repeat Last Month's Budget" button
**And** Success message: "Orçamento mensal definido"

---

## Story 6.4: Display Real-Time Available Balance Per Category

As a logged-in user,
I want to see the real-time available balance for each category,
So that I know how much I can still spend in each envelope.

**Acceptance Criteria:**

**Given** I have budget allocations set for the current month (Story 6.3)
**When** I view my categories list or budget page
**Then** For each category, display:
  - Allocated Amount: R$ X.XX (what I planned)
  - Spent Amount: R$ Y.YY (sum of transactions in this category this month)
  - Remaining Amount: R$ Z.ZZ (Allocated - Spent, calculated in real-time)
**And** Remaining amount calculation:
  - Query all transactions for user_id + category_id + current month
  - Sum transaction amounts (debits only, credits don't reduce budget)
  - remaining_amount = allocated_amount - spent_amount
**And** Calculation happens in real-time (NFR49) when viewing the page
**And** For performance, use Redis cache (ARCH-REQ-10) with TTL 30s for category balances
**And** Cache key: `category:balance:{userId}:{categoryId}:{month}`
**And** Cache is invalidated when:
  - New transaction is added/edited (Story 5.12, 5.13)
  - Transaction category is changed (Story 5.10)
  - Budget allocation is changed (Story 6.3)
**And** Visual indicators:
  - Green: Remaining > 20% of allocated
  - Yellow: Remaining 0-20% of allocated
  - Red: Remaining < 0 (overspent)
**And** Categories with 0 allocated show: "Sem orçamento definido"

---

## Story 6.5: Reallocate Money Between Categories

As a logged-in user,
I want to move money between categories during the month,
So that I can adjust my budget when priorities change.

**Acceptance Criteria:**

**Given** I have budget allocations for the current month
**When** I click "Reallocate" or "Move Money" button
**Then** Reallocation modal is displayed with:
  - From Category (dropdown)
  - To Category (dropdown)
  - Amount to move (currency input)
  - Current balances displayed for context
**And** I select source category, destination category, and amount
**And** System validates: Amount <= Remaining balance in source category
**And** Clicking "Confirm" updates both budget_allocation records:
  - Source category: allocated_amount -= amount
  - Destination category: allocated_amount += amount
**And** A `budget_reallocations` table is created (V20) to track: reallocation_id, user_id, from_category_id, to_category_id, amount, month_year, created_at
**And** Reallocation is recorded for audit/history purposes
**And** Category balances are updated immediately
**And** Redis cache is invalidated for both categories (ARCH-REQ-14)
**And** Success message: "R$ X.XX movido de [Category A] para [Category B]"
**And** Validation: Cannot move money from category with negative balance
**And** Validation: from_category_id ≠ to_category_id

---

## Story 6.6: Auto-Deduct from Category When Transaction Classified

As a system,
I want to automatically deduct transaction amounts from category budgets when classified,
So that category balances stay accurate without manual updates.

**Acceptance Criteria:**

**Given** A transaction is classified to a category (Epic 5, Stories 5.8-5.10, 5.12-5.13)
**When** The transaction is saved with a category_id
**Then** System automatically updates budget_allocation for that category + current month:
  - spent_amount += transaction amount (for debit transactions)
  - remaining_amount is recalculated (allocated - spent)
**And** If transaction is credit (income), it does NOT reduce budget (only debits reduce)
**And** If transaction is edited to different category:
  - Old category: spent_amount -= transaction amount
  - New category: spent_amount += transaction amount
**And** If transaction is deleted (soft delete):
  - Category: spent_amount -= transaction amount
**And** Updates happen atomically in database transaction
**And** Redis cache for category balance is invalidated (ARCH-REQ-14)
**And** No user action required - this is automatic (NFR51)
**And** Dashboard updates immediately to reflect new balances
**And** If category wasn't allocated for current month, no budget record is created (spent tracked but no allocated limit)

---

## Story 6.7: Visual Indicators for Low/Negative Balance Categories

As a logged-in user,
I want to see visual warnings when categories are running low or overspent,
So that I can adjust my spending before it's too late.

**Acceptance Criteria:**

**Given** I have budget allocations with varying remaining balances
**When** I view my budget or categories page
**Then** Each category displays visual indicator based on remaining balance:
  - **Green (Healthy):** Remaining > 20% of allocated
    - Green progress bar or checkmark icon
    - No warning message
  - **Yellow (Low):** Remaining 0-20% of allocated
    - Yellow progress bar or warning icon
    - Warning text: "Saldo baixo - R$ X.XX restantes"
  - **Red (Overspent):** Remaining < 0 (negative)
    - Red progress bar or alert icon
    - Alert text: "Categoria estourada! R$ X.XX acima do orçamento"
**And** Dashboard summary shows count: "X categorias saudáveis, Y baixas, Z estouradas"
**And** Overspent categories appear at top of list (prioritized) with red background
**And** Visual indicators use semantic colors from UX spec (UX-DR-7):
  - Green 600: #16A34A
  - Yellow 600: #CA8A04
  - Red 600: #DC2626
**And** Progress bar shows percentage: spent / allocated * 100%
**And** Hovering over indicator shows tooltip with exact amounts
**And** Notifications can be enabled: "Alert me when category reaches 90% of budget"

---

## Story 6.8: View Category Allocation History

As a logged-in user,
I want to view my budget allocation history over time,
So that I can see how my spending patterns evolve.

**Acceptance Criteria:**

**Given** I am logged in and on a category detail page
**When** I navigate to "Allocation History" tab
**Then** Historical data table is displayed showing:
  - Month/Year
  - Allocated Amount
  - Spent Amount
  - Remaining Amount
  - Status (Healthy/Low/Overspent)
**And** Data is fetched from `budget_allocations` table for all past months for this category
**And** Table is sortable by month (default: most recent first)
**And** Chart visualization shows allocation vs spending trend over time:
  - X-axis: Months
  - Y-axis: Amount (R$)
  - Two lines: Allocated (planned) and Spent (actual)
**And** Time range selector: Last 3 months, 6 months, 1 year, All time
**And** Export button allows downloading history as CSV
**And** Summary statistics displayed:
  - Average monthly allocation: R$ X.XX
  - Average monthly spending: R$ Y.YY
  - Months overspent: Z
  - Best month (lowest spending): Month Year
**And** History query filters by user_id for security (NFR15)
**And** Page loads in < 1 second even with 12+ months of data (NFR10)

---

## Story 6.9: Compare Planned vs Actual Spending by Category

As a logged-in user,
I want to compare my planned budget with actual spending across all categories,
So that I can identify where I'm over/under budget.

**Acceptance Criteria:**

**Given** I have budget allocations and transactions for the current month
**When** I view the "Budget Overview" page
**Then** Comparison table is displayed with columns:
  - Category Name
  - Allocated (Planned)
  - Spent (Actual)
  - Difference (Allocated - Spent)
  - Percentage (Spent / Allocated * 100%)
  - Status Icon (Green/Yellow/Red)
**And** Categories are sortable by any column
**And** Default sort: Categories with highest overspend at top
**And** Visual comparison chart (bar chart):
  - X-axis: Categories
  - Y-axis: Amount (R$)
  - Two bars per category: Allocated (light color) and Spent (dark color)
**And** Summary totals at bottom:
  - Total Allocated: R$ X.XX
  - Total Spent: R$ Y.YY
  - Total Remaining: R$ Z.ZZ
  - Overall Budget Status: "X% do orçamento utilizado"
**And** Filter options:
  - Show only: All / Healthy / Low / Overspent
  - Time period: Current month / Last month / Custom range
**And** Hierarchical categories show parent totals with child breakdown (collapsible)
**And** Export comparison report as PDF or CSV
**And** Data refreshes in real-time when transactions are added/edited (cache invalidation)

---
