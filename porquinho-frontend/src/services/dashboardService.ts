import { api } from './api'
import type { FinancialHealth } from '@/types/dashboard'

/** Story 7.2: semáforo de saúde financeira do mês corrente. */
export async function getFinancialHealth(): Promise<FinancialHealth> {
  const response = await api.get<FinancialHealth>('/api/v1/dashboard/health')
  return response.data
}
