import { api } from './api'
import type { Budget, BudgetAllocationInput, BudgetReallocationInput } from '@/types/budget'

/** Story 6.3: orçamento do mês (AAAA-MM) com gasto e comprometido por categoria filha. 400 mês inválido. */
export async function getBudget(month: string): Promise<Budget> {
  const response = await api.get<Budget>(`/api/v1/budgets/${month}`)
  return response.data
}

/** Story 6.3: substitui as alocações do mês e devolve o mês recalculado. 400 sem gravar nada. */
export async function saveBudget(month: string, allocations: BudgetAllocationInput[]): Promise<Budget> {
  const response = await api.put<Budget>(`/api/v1/budgets/${month}`, { allocations })
  return response.data
}

/** Story 6.5: move dinheiro entre duas categorias do mês e devolve o mês recalculado. 400 sem gravar nada. */
export async function reallocateBudget(month: string, input: BudgetReallocationInput): Promise<Budget> {
  const response = await api.post<Budget>(`/api/v1/budgets/${month}/reallocations`, input)
  return response.data
}
