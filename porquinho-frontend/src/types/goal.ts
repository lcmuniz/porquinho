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
}

export interface CreateGoalInput {
  name: string
  targetAmount: number
  targetDate: string | null
  monthlyAllocation: number | null
  /** Story 8.1: opcional, até 500 caracteres; vazia vira null no backend. */
  description?: string | null
}

/** Story 8.2: GET /api/v1/goals/summary. Soma dos saldos das contas ativas (cartões fora), como no Orçamento. */
export interface GoalsSummary {
  totalAvailable: number
}
