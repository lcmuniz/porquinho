import { api } from './api'
import type {
  DashboardDetails,
  DashboardOverview,
  DashboardTactical,
  DetailsFilter,
  FinancialHealth,
} from '@/types/dashboard'

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

/** Story 7.6: página `page` (0 = primeira) da lista filtrada da Camada 3. */
export async function getDetails(filter: DetailsFilter, page: number): Promise<DashboardDetails> {
  const params = detailsParams(filter)
  params.set('page', String(page))
  const response = await api.get<DashboardDetails>('/api/v1/dashboard/layer3', { params })
  return response.data
}

/** Story 7.6: o CSV de todo o resultado filtrado, na mesma ordem. */
export async function exportDetails(filter: DetailsFilter): Promise<Blob> {
  const response = await api.get<Blob>('/api/v1/dashboard/layer3/export', {
    params: detailsParams(filter),
    responseType: 'blob',
  })
  return response.data
}
