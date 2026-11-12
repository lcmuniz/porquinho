import { api } from './api'
import type { DashboardOverview, DashboardTactical, FinancialHealth } from '@/types/dashboard'

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
