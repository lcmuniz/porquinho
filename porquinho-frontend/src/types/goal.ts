/** Story 8.6: modelo de origem da meta (os modelos ficam só no front, em GOAL_TEMPLATES). */
export type GoalTemplate = 'EMERGENCY_FUND' | 'VACATION' | 'BIG_PURCHASE'

/** Meta financeira (Story 3.8; ampliada no Epic 8). Dinheiro vem como número em reais. */
export interface Goal {
  id: string
  name: string
  description: string | null
  targetAmount: number
  currentAmount: number
  /** Data-alvo ISO (AAAA-MM-DD) ou null. */
  targetDate: string | null
  monthlyAllocation: number | null
  completed: boolean
  createdAt: string
  /** Story 8.6: modelo usado ao criar; null em metas personalizadas, do onboarding ou antigas. */
  template: GoalTemplate | null
}

export interface CreateGoalInput {
  name: string
  targetAmount: number
  targetDate: string | null
  monthlyAllocation: number | null
  /** Story 8.1: opcional, até 500 caracteres; vazia vira null no backend. */
  description?: string | null
  /** Story 8.6: modelo escolhido no diálogo; ausente em meta personalizada. */
  template?: GoalTemplate
}

/** Story 8.2: GET /api/v1/goals/summary. Soma dos saldos das contas ativas (cartões fora), como no Orçamento. */
export interface GoalsSummary {
  totalAvailable: number
}

/** Story 8.3: valor acumulado da meta num mês ("AAAA-MM", America/Sao_Paulo). */
export interface GoalProgressPoint {
  month: string
  amount: number
}

/** Story 8.3: GET /api/v1/goals/{id}/progress. Um ponto por mês, da criação até o mês corrente. */
export interface GoalProgress {
  points: GoalProgressPoint[]
}

/** Story 8.5: item do PUT /api/v1/goals/allocations. */
export interface GoalAllocationItem {
  goalId: string
  monthlyAllocation: number
}
