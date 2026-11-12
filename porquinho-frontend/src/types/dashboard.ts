import type { BudgetStatus } from './budget'

/** Story 7.2: cor do semáforo de saúde financeira. */
export type HealthStatus = 'HEALTHY' | 'ATTENTION' | 'CRITICAL'

/** Story 7.2: resposta de GET /api/v1/dashboard/health (categorias com orçamento no mês corrente). */
export interface FinancialHealth {
  status: HealthStatus
  healthyCategories: number
  lowCategories: number
  overspentCategories: number
  negativeAccounts: number
  goalsBehind: number
}

/** Story 7.3: resposta de GET /api/v1/dashboard/layer1 (mês corrente). Categorias: estouradas, saldo baixo, saudáveis. */
export interface DashboardOverview {
  totalBalance: number
  categories: { categoryId: string; name: string; status: BudgetStatus; remaining: number }[]
  mainGoal: { name: string; currentAmount: number; targetAmount: number; percent: number } | null
  monthSpent: number
  monthAllocated: number
  projectedBalance: number
}
