# Epic 7: Dashboard GPS - Complete Financial Overview

Usuários visualizam sua saúde financeira completa através de Dashboard GPS de 3 camadas navegável por scroll vertical: Layer 1 (Overview) com 5 métricas essenciais, semáforo de saúde, e resposta às 3 perguntas GPS em 30 segundos; Layer 2 (Tactical) com gráficos por categoria, tendências temporais, e drill-down; Layer 3 (Details) com lista completa de transações, filtros avançados, ordenação, edição, e exportação CSV.

## Story 7.1: Dashboard GPS Container & Vertical Scroll Navigation

As a logged-in user,
I want to navigate through 3 dashboard layers using smooth vertical scrolling,
So that I can explore my financial data naturally without excessive clicks.

**Acceptance Criteria:**

**Given** I am logged in and on the Dashboard GPS page (home/default page)
**When** The page loads
**Then** Dashboard GPS container component is rendered (UX-DR-29)
**And** Page is structured as single-column vertical scroll layout (UX-DR-34)
**And** Three layers are stacked vertically:
  - Layer 1 (Overview) at top
  - Layer 2 (Tactical) in middle
  - Layer 3 (Details) at bottom
**And** Scroll behavior is smooth with momentum (CSS: scroll-behavior: smooth)
**And** Visual separators (py-12 spacing per UX-DR-16) between layers
**And** Navigation between layers occurs in < 500ms (NFR2, UX-DR-36)
**And** No tabs or excessive clicks required (UX-DR-34)
**And** Scroll position is preserved when navigating away and returning
**And** "Jump to Layer" quick navigation buttons available (optional):
  - "Jump to Overview" (Layer 1)
  - "Jump to Tactical" (Layer 2)
  - "Jump to Details" (Layer 3)
**And** Mobile: Single column layout maintained, touch-friendly scrolling
**And** Desktop: Wider viewport uses full width for charts/tables

---

## Story 7.2: Financial Health Semaphore (Sticky Top Component)

As a logged-in user,
I want to see a financial health semaphore (🟢🟡🔴) that stays visible while scrolling,
So that I always know my overall financial status.

**Acceptance Criteria:**

**Given** I am on the Dashboard GPS page
**When** Page loads
**Then** Financial Health Semaphore component is rendered at the very top (UX-DR-28)
**And** Semaphore calculates overall health based on:
  - Category budget status (% of categories healthy/low/overspent from Epic 6)
  - Account balances (positive vs negative)
  - Goal progress (on track vs behind)
  - Recent spending trend (increasing vs decreasing)
**And** Health indicator displays:
  - 🟢 **Green (Saudável):** 80%+ categories healthy, positive balances, goals on track
  - 🟡 **Yellow (Atenção):** 50-79% categories healthy OR some overspent categories
  - 🔴 **Red (Crítico):** < 50% categories healthy OR multiple overspent OR negative balances
**And** Semaphore is sticky (position: sticky, top: 0) and remains visible during scroll (UX-DR-37)
**And** Displays text label: "Saúde Financeira: [Saudável/Atenção/Crítico]"
**And** Clicking semaphore shows tooltip with breakdown:
  - X categorias saudáveis
  - Y categorias com saldo baixo
  - Z categorias estouradas
**And** Uses semantic colors from UX spec (UX-DR-7): Green 600, Yellow 600, Red 600
**And** Component is visually prominent but not overwhelming (follows "UI neutra, dados vibrantes" per UX-DR-39)
**And** Semaphore updates in real-time when underlying data changes

---

## Story 7.3: Layer 1 - 5 Essential Metrics Overview (< 2s Performance)

As a logged-in user,
I want to see 5 essential financial metrics in a single view,
So that I can understand "Onde estou? Para onde vou? Preciso ajustar?" in 30 seconds.

**Acceptance Criteria:**

**Given** I am on Dashboard GPS Layer 1 (top of page)
**When** Layer 1 loads
**Then** Layer 1 renders in < 2 seconds (NFR1 - CRITICAL requirement)
**And** 5 essential metrics are displayed (FR55):
  1. **Total Available Balance** (FR56): Sum of all account balances
  2. **Category Status Visual** (FR57): Grid or list showing categories with color-coded status (🟢🟡🔴)
  3. **Main Goal Progress** (FR58): Emergency Fund goal with progress bar and percentage
  4. **Month Spending vs Planned** (FR59): Current month spending vs total budget allocated
  5. **End-of-Month Projection** (FR60): Projected balance at month end based on current spending rate
**And** Answers the 3 GPS questions visually (FR61):
  - **"Onde estou?"** → Total balance + Category status
  - **"Para onde vou?"** → Goal progress + Projection
  - **"Preciso ajustar?"** → Overspent categories highlighted
**And** Each metric is displayed as a card with:
  - Large primary number (typography: text-4xl, weight: 700)
  - Descriptive label (text-sm, Gray 600)
  - Supporting visual (icon, mini chart, or progress bar)
**And** Cards use spacing: p-6, gap-4 (UX-DR-15)
**And** Layout: 2-column grid on desktop, single column on mobile (responsive per UX-DR-18-20)
**And** Data is fetched from Redis cache (key: `dashboard:layer1:{userId}`, TTL: 30s per ARCH-REQ-11)
**And** If cache miss, calculate from database and cache result
**And** Visual emphasis: Gráficos dominam (80% espaço), UI discreta (20%) per UX-DR-38
**And** User can achieve financial clarity in ≤ 30 seconds (UX-DR-35)

---

## Story 7.4: Wealth Growth Chart Component (Animated Curve)

As a logged-in user,
I want to see an animated chart showing my total wealth growth over time,
So that I can visualize my financial progress motivationally.

**Acceptance Criteria:**

**Given** I am viewing Dashboard GPS Layer 1
**When** The Wealth Growth Chart component loads
**Then** Component displays animated line chart (UX-DR-30) showing:
  - X-axis: Time (last 3/6/12 months, selectable)
  - Y-axis: Total wealth (sum of all account balances)
  - Smooth curve line animated on load (CSS animation or library like Chart.js)
**And** Chart uses vibrant color from UX spec: Chart 1 (Purple #9333EA) per UX-DR-8
**And** Data points show tooltips on hover: Date + Balance value
**And** Chart highlights key milestones:
  - Positive trend: Green upward arrow
  - Negative trend: Red downward arrow
  - Flat: Yellow neutral indicator
**And** Chart renders in < 1 second (NFR3)
**And** Wealth calculation:
  - Sum of all account balances (from `accounts` table)
  - Historical snapshots from `account_balance_history` (Epic 4, Story 4.7)
**And** Chart is responsive: full width on mobile, 2/3 width on desktop
**And** Animation plays once on component mount (not on every scroll)
**And** Chart library: Use Chart.js or similar for performance + animation support
**And** Data is cached with Layer 1 (Redis, 30s TTL)

---

## Story 7.5: Layer 2 - Tactical View with Category Breakdown

As a logged-in user,
I want to access Layer 2 for detailed tactical analysis of my spending by category,
So that I can drill down into specific areas of concern.

**Acceptance Criteria:**

**Given** I scroll down from Layer 1 to Layer 2 (middle section)
**When** Layer 2 becomes visible
**Then** Layer 2 tactical view is displayed (FR62)
**And** Charts of spending by category are shown (FR63):
  - Pie chart or donut chart: Current month spending breakdown by category
  - Bar chart: Top 5 spending categories
**And** Planned vs Actual comparison per category (FR64):
  - Table or stacked bar chart showing allocated vs spent for each category
  - Visual indicators: Green (under budget), Red (over budget)
**And** Temporal trends displayed (FR65):
  - Line chart: Last 3/6/12 months spending by top categories
  - Time range selector: 3 months, 6 months, 12 months
**And** Drill-down capability (FR66):
  - Clicking on a category opens detail modal or expands section
  - Shows all transactions for that category in selected period
**And** Layer 2 renders from Redis cache (key: `dashboard:layer2:{userId}`, TTL: 5 min per ARCH-REQ-12)
**And** Charts render in < 1 second (NFR3)
**And** Layout: 2-column grid on desktop (1280px+), single column on tablet/mobile (UX-DR-18-20)
**And** Charts use vibrant colors from UX spec (Chart 2-6) per UX-DR-8
**And** Transitions between Layer 1 and 2 are smooth (< 500ms per NFR2)

---

## Story 7.6: Layer 3 - Complete Transaction List with Filters

As a logged-in user,
I want to access Layer 3 to see all my transactions with advanced filtering,
So that I can find specific transactions and make detailed edits.

**Acceptance Criteria:**

**Given** I scroll down from Layer 2 to Layer 3 (bottom section)
**When** Layer 3 becomes visible
**Then** Complete transaction list is displayed (FR67, FR68)
**And** Transaction table shows columns:
  - Date
  - Description
  - Amount (with currency formatting)
  - Category
  - Account
  - Status (icons for Pix, Installment, etc.)
  - Actions (Edit button)
**And** Filters are available (FR69):
  - Period filter: Date range picker or presets (This month, Last month, Last 3 months, Custom)
  - Category filter: Multi-select dropdown
  - Account filter: Multi-select dropdown
  - Text search: Search in transaction descriptions
**And** Sorting options (FR70):
  - Sort by: Date (default: newest first), Amount (high to low), Category (A-Z)
  - Clickable column headers for sorting
**And** Individual edit capability (FR71):
  - Clicking "Edit" on a transaction opens inline editor or modal
  - Uses Epic 5, Story 5.12 edit logic
**And** Export to CSV (FR72):
  - "Export to CSV" button above table
  - Exports filtered/sorted results
  - CSV includes all transaction fields
**And** Pagination: 50 transactions per page (for performance)
**And** Table query returns in < 1 second for up to 10,000 records (NFR10)
**And** Layer 3 data is cached in Redis (key: `dashboard:layer3:{userId}:{filters_hash}`, TTL: 15 min per ARCH-REQ-13)
**And** Layout: Full-width table on desktop, stacked cards on mobile (responsive per UX-DR-18-20)
**And** Filters persist when navigating away and returning (localStorage)

---

## Story 7.7: Optimize Dashboard Performance with Redis Caching

As a system,
I want to cache dashboard data in Redis with appropriate TTLs,
So that dashboard loads meet performance requirements (< 2s for Layer 1).

**Acceptance Criteria:**

**Given** Redis is configured (Epic 0, Story 0.5)
**When** Dashboard data is requested
**Then** Layer 1 data is cached with key: `dashboard:layer1:{userId}`, TTL: 30 seconds (ARCH-REQ-11)
**And** Layer 2 data is cached with key: `dashboard:layer2:{userId}`, TTL: 5 minutes (ARCH-REQ-12)
**And** Layer 3 data is cached with key: `dashboard:layer3:{userId}:{filters_hash}`, TTL: 15 minutes (ARCH-REQ-13)
**And** Cache invalidation occurs when (ARCH-REQ-14):
  - Transaction is added, edited, or deleted (Epic 5)
  - Budget allocation is changed (Epic 6)
  - Category is classified (Epic 5)
  - Account balance is updated (Epic 4)
**And** Cache invalidation is selective:
  - Transaction change: Invalidate all 3 layers for that user
  - Budget change: Invalidate Layer 1 and 2 only
**And** Cache warming: Background job pre-calculates Layer 1 data every 30 seconds for active users (optional optimization)
**And** Cache key naming follows pattern: `dashboard:{layer}:{userId}:{optional_params}`
**And** Dashboard performance meets requirements:
  - Layer 1: < 2 seconds (NFR1 - CRITICAL)
  - Layer 2: < 3 seconds
  - Layer 3: < 4 seconds (with pagination)
  - Navigation between layers: < 500ms (NFR2)
  - Charts render: < 1 second (NFR3)
**And** Performance is maintained even with 5 years of transaction history (NFR9)
**And** Monitoring tracks cache hit rate (target: > 90%)

---
