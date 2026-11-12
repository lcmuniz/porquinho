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
