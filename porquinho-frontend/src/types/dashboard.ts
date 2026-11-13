import type { Transaction } from './transaction'
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
  /** Story 7.4: patrimônio dos últimos 12 meses, do mais antigo a hoje (o último vale totalBalance); vazio sem histórico. */
  wealthHistory: WealthPoint[]
}

/** Story 7.4: patrimônio no fim do dia `date` ("AAAA-MM-DD", São Paulo); o último ponto é agora. */
export interface WealthPoint {
  date: string
  balance: number
}

/**
 * Story 7.5: resposta de GET /api/v1/dashboard/layer2. `categories`: filhas com gasto ou alocação no mês `month`
 * ("AAAA-MM"), maior gasto primeiro; `categoryId` null é "Não classificado". `trends`: 12 meses por categoria, do mais
 * antigo ao corrente (parcial), meses sem gasto valem 0.
 */
export interface DashboardTactical {
  month: string
  categories: CategorySpending[]
  trends: CategoryTrend[]
}

export interface CategorySpending {
  categoryId: string | null
  name: string
  colorHex: string | null
  spent: number
  /** null sem alocação no mês (assim como `status`). */
  allocated: number | null
  status: BudgetStatus | null
}

export interface CategoryTrend {
  categoryId: string | null
  name: string
  months: { month: string; spent: number }[]
}

/** Story 7.5: fatia do donut; `category` null é "Outras" (soma de várias, sem drill-down). */
export interface DonutSlice {
  id: string
  name: string
  value: number
  color: string
  category: CategorySpending | null
}

/** Story 7.6: campo e direção da ordenação da Camada 3. */
export type DetailsSortField = 'date' | 'amount' | 'category'

/**
 * Story 7.6: filtros da Camada 3. `from`/`to` "AAAA-MM-DD"; `categoryIds` com 'uncategorized' para "Não classificado";
 * `targets` como "account:<id>"/"card:<id>"; listas vazias não filtram.
 */
export interface DetailsFilter {
  from: string
  to: string
  categoryIds: string[]
  targets: string[]
  q: string
  sort: DetailsSortField
  dir: 'asc' | 'desc'
}

/** Story 7.6: resposta de GET /api/v1/dashboard/layer3 (página de 50, começando em 0). */
export interface DashboardDetails {
  items: Transaction[]
  page: number
  totalItems: number
  totalPages: number
}
