# Epic 8: Financial Goals Management

Usuários podem criar múltiplas metas financeiras simultâneas com nome, valor alvo e prazo, alocar recursos mensais específicos por meta, visualizar progresso através de barras de preenchimento e curvas temporais, ver projeção automática de data de cumprimento baseada em alocação atual, ajustar alocação entre metas, usar metas pré-configuradas sugeridas, marcar metas como concluídas, e visualizar histórico de progresso.

## Story 8.1: Create Multiple Financial Goals

As a logged-in user,
I want to create multiple financial goals simultaneously,
So that I can save for different objectives at the same time.

**Acceptance Criteria:**

**Given** I am logged in and on the Goals management page
**When** I click "Create Goal"
**Then** Create goal form/modal is displayed with fields:
  - Goal Name (text, required)
  - Target Amount (currency, required)
  - Target Date (date picker, optional)
  - Monthly Allocation (currency, optional - auto-calculated if target date provided)
  - Description (text, optional)
**And** I fill in details and click "Save"
**And** New goal is saved to `goals` table (created in Epic 3, Story 3.8) with user_id from JWT
**And** Goal appears in my goals list immediately
**And** I can create unlimited goals (no artificial limit per NFR30)
**And** Form validation: Goal Name required, Target Amount > 0, Target Date cannot be past
**And** Success message: "Meta criada"
**And** If target date and amount provided, monthly allocation is auto-calculated: target_amount / months_remaining
**And** Current amount starts at 0

---

## Story 8.2: Allocate Monthly Resources to Goals

As a logged-in user,
I want to allocate specific monthly amounts to each of my goals,
So that I can systematically save toward multiple objectives.

**Acceptance Criteria:**

**Given** I have one or more goals created
**When** I view my goals list
**Then** Each goal shows current monthly allocation amount
**And** I can click "Edit Allocation" to change the monthly amount
**And** A `goal_allocations` table is created via migration (V21__create_goal_allocations_table.sql) with fields: allocation_id (UUID, PK), goal_id (FK), user_id (FK), month_year (date), allocated_amount (decimal), contributed_amount (decimal), created_at, updated_at
**And** Changing allocation updates goal record's monthly_allocation field
**And** When I make a contribution to a goal:
  - goal.current_amount += contribution
  - goal_allocations record for current month: contributed_amount += contribution
**And** Total monthly goal allocations are displayed with warning if > available budget
**And** Allocations can be adjusted any time during the month
**And** Success message: "Alocação atualizada para [Goal Name]"

---

## Story 8.3: Visualize Goal Progress with Charts

As a logged-in user,
I want to see my goal progress visualized with progress bars and temporal curves,
So that I can stay motivated and track my savings.

**Acceptance Criteria:**

**Given** I am viewing my goals list or a specific goal detail page
**When** Goal progress is displayed
**Then** Progress bar component (UX-DR-31) shows:
  - Filled portion: current_amount / target_amount * 100%
  - Visual color: Green (on track), Yellow (behind), Red (significantly behind)
  - Text label: "R$ X.XX de R$ Y.YY (Z%)"
**And** Temporal curve (line chart) shows:
  - X-axis: Time (months from goal creation to target date)
  - Y-axis: Amount (R$)
  - Actual progress line (current savings over time)
  - Projected line (based on current monthly allocation)
  - Target milestone markers
**And** Chart uses vibrant Chart 4 color (Green #10B981) per UX-DR-8
**And** Hovering over curve shows: Date + Amount saved at that point
**And** Milestones displayed: 25%, 50%, 75%, 100% markers
**And** Progress visualization updates in real-time when contributions are made
**And** Chart renders in < 1 second (NFR3)

---

## Story 8.4: Auto-Project Goal Completion Date

As a system,
I want to automatically project when each goal will be completed based on current allocation,
So that users know if they're on track.

**Acceptance Criteria:**

**Given** A goal has a target amount and monthly allocation set
**When** The goal detail page is viewed
**Then** Projected completion date is calculated and displayed
**And** Calculation logic:
  - remaining_amount = target_amount - current_amount
  - months_to_complete = remaining_amount / monthly_allocation
  - projected_date = current_date + months_to_complete
**And** Display shows: "Projeção de conclusão: [Month Year]"
**And** Visual indicator:
  - Green: Projected date <= target date (on track)
  - Yellow: Projected date 1-3 months after target (slightly behind)
  - Red: Projected date > 3 months after target (significantly behind)
**And** If no monthly allocation set, show: "Defina alocação mensal para ver projeção"
**And** If current allocation will miss target date, suggestion displayed: "Aumente alocação para R$ X.XX/mês para atingir meta no prazo"
**And** Projection updates automatically when allocation changes

---

## Story 8.5: Adjust Allocation Between Goals

As a logged-in user,
I want to adjust allocation amounts between my goals,
So that I can prioritize different goals based on changing circumstances.

**Acceptance Criteria:**

**Given** I have multiple goals with allocations
**When** I navigate to "Adjust Allocations" page
**Then** Interface shows all goals with current allocations
**And** I can use sliders or input fields to redistribute monthly savings
**And** Total monthly allocation sum is displayed
**And** I can increase/decrease any goal's allocation
**And** Clicking "Save Changes" updates all goal records
**And** Projections for all affected goals are recalculated
**And** Dashboard cache is invalidated (goals progress shown in Layer 1)
**And** Success message: "Alocações atualizadas"
**And** Visual comparison: Before vs After allocations side-by-side
**And** Warning if total allocation exceeds available monthly budget

---

## Story 8.6: Use Pre-Configured Goal Templates

As a logged-in user,
I want to use pre-configured goal templates for common objectives,
So that I can quickly set up goals without manual entry.

**Acceptance Criteria:**

**Given** I am on the Create Goal page
**When** I click "Use Template" or "Suggested Goals"
**Then** Goal template selector is displayed with options (FR79):
  - **Reserva de Emergência:** Target: R$ 25.000, Duration: 12 months, Description: "6 meses de despesas"
  - **Férias:** Target: R$ 8.000, Duration: 6 months, Description: "Viagem de férias"
  - **Compra Grande:** Target: R$ 15.000, Duration: 12 months, Description: "Eletrodoméstico, móvel, eletrônico"
  - **Custom:** Start from blank
**And** Selecting a template pre-fills the create goal form with suggested values
**And** All fields remain editable (templates are suggestions, not locked)
**And** Clicking "Create Goal" saves the goal with template-based or customized values
**And** Template icon/badge is displayed on goal card: "Template: Reserva de Emergência"
**And** Templates are culturally appropriate for Brazilian context (R$ amounts, terminology)

---

## Story 8.7: Mark Goals as Completed

As a logged-in user,
I want to mark goals as completed when I reach the target,
So that I can celebrate achievements and archive old goals.

**Acceptance Criteria:**

**Given** A goal has reached or exceeded target amount (current_amount >= target_amount)
**When** System detects completion OR I manually mark as complete
**Then** Automatic completion detection:
  - When current_amount >= target_amount, is_completed flag is set to true
  - Completion notification is displayed: "Parabéns! Você completou a meta [Goal Name]!"
  - Confetti animation or celebratory visual (optional)
**And** Manual completion:
  - I can click "Mark as Complete" on any goal
  - Confirmation dialog: "Tem certeza? Esta meta será arquivada"
  - After confirmation, is_completed = true
**And** Completed goals are moved to "Completed Goals" section (separate from active)
**And** Completed goals show:
  - Completion date
  - Final amount saved
  - Green checkmark icon
  - "Completed" badge
**And** Completed goals can be viewed but not edited
**And** Option to "Reopen Goal" if marked complete by mistake
**And** Audit log records "goal_completed" event

---

## Story 8.8: View Goal Progress History Over Time

As a logged-in user,
I want to view historical progress for each goal over time,
So that I can see how consistently I've been saving.

**Acceptance Criteria:**

**Given** I am on a goal detail page
**When** I navigate to "Progress History" tab
**Then** A `goal_progress_history` table is created via migration (V22) with fields: history_id (UUID, PK), goal_id (FK), user_id (FK), amount_snapshot (decimal), snapshot_date, contribution_amount (decimal if contribution event), created_at
**And** Historical chart is displayed:
  - X-axis: Time (months)
  - Y-axis: Saved amount (R$)
  - Line showing progress over time
  - Markers for contributions made
**And** Table view shows:
  - Date
  - Contribution Amount
  - Total Amount at that point
  - Percentage of goal reached
**And** Snapshots are automatically recorded when:
  - Goal is created (initial snapshot: amount = 0)
  - Contribution is made to goal
  - Monthly automatic capture (1st of each month)
**And** Summary statistics displayed:
  - Average monthly contribution
  - Total time to complete (if completed)
  - Consistency score (% of months with contributions)
**And** Export history as CSV
**And** History query filters by user_id for security (NFR15)

---
