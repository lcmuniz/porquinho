import { api } from './api'
import type {
  DashboardOverview,
  DashboardTactical,
  DetailsFilter,
  FinancialHealth,
} from '@/types/dashboard'
import type { Transaction } from '@/types/transaction'

/** Story 7.2: semáforo de saúde financeira do mês corrente. */
export async function getFinancialHealth(): Promise<FinancialHealth> {
  const response = await api.get<FinancialHealth>('/api/v1/dashboard/health')
  return response.data
}

/** Story 7.3: as 5 métricas da Camada 1 do mês corrente. */
export async function getOverview(): Promise<DashboardOverview> {
  const response = await api.get<DashboardOverview>('/api/v1/dashboard/layer1')
  return response.data
}

/** Story 7.5: gasto por categoria do mês corrente e tendências dos últimos 12 meses (Camada 2). */
export async function getTactical(): Promise<DashboardTactical> {
  const response = await api.get<DashboardTactical>('/api/v1/dashboard/layer2')
  return response.data
}

/** Story 7.5: as transações de uma fatia da Camada 2 no mês corrente, pela regra de gasto do backend (null = sem categoria). */
export async function getDrilldown(categoryId: string | null): Promise<Transaction[]> {
  const params = categoryId ? { categoryId } : {}
  const response = await api.get<Transaction[]>('/api/v1/dashboard/layer2/drilldown', { params })
  return response.data
}

/** Story 7.6: os filtros como query params do backend (listas repetem a chave). */
function detailsParams(filter: DetailsFilter): URLSearchParams {
  const params = new URLSearchParams({ from: filter.from, to: filter.to, sort: `${filter.sort},${filter.dir}` })
  if (filter.q.trim()) params.set('q', filter.q.trim())
  for (const id of filter.categoryIds) {
    if (id === 'uncategorized') params.set('uncategorized', 'true')
    else params.append('categoryIds', id)
  }
  for (const target of filter.targets) {
    const [kind, id] = target.split(':')
    params.append(kind === 'card' ? 'creditCardIds' : 'accountIds', id!)
  }
  return params
}

/** Camada 3 (pós-critique): as 10 maiores saídas do mês corrente, pela regra de gasto do backend. */
export async function getLargestSpent(): Promise<Transaction[]> {
  const response = await api.get<Transaction[]>('/api/v1/dashboard/layer3/largest')
  return response.data
}

/**
 * Story 7.6: o CSV de todo o resultado filtrado, na mesma ordem. Story 9.1: as opções incluem parcelas futuras e
 * excluídas; `count` é o número de transações do arquivo (cabeçalho X-Export-Count).
 */
export async function exportDetails(
  filter: DetailsFilter,
  options: { includeFutureInstallments: boolean; includeDeleted: boolean } = {
    includeFutureInstallments: false,
    includeDeleted: false,
  },
): Promise<{ blob: Blob; count: number }> {
  const params = detailsParams(filter)
  if (options.includeFutureInstallments) params.set('includeFutureInstallments', 'true')
  if (options.includeDeleted) params.set('includeDeleted', 'true')
  const response = await api.get<Blob>('/api/v1/dashboard/layer3/export', { params, responseType: 'blob' })
  return { blob: response.data, count: Number(response.headers?.['x-export-count'] ?? 0) }
}
