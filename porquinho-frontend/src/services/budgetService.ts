import { api } from './api'
import type {
  Budget,
  BudgetAllocationInput,
  BudgetReallocationInput,
  CategoryBudgetHistory,
} from '@/types/budget'

/** Story 6.3: orçamento do mês (AAAA-MM) com gasto e comprometido por categoria filha. 400 mês inválido. */
export async function getBudget(month: string): Promise<Budget> {
  const response = await api.get<Budget>(`/api/v1/budgets/${month}`)
  return response.data
}

/**
 * Story 6.3: substitui as alocações do mês e devolve o mês recalculado. 400 sem gravar nada; 409 quando `version`
 * (a do GET que preencheu o formulário) não é mais a do mês.
 */
export async function saveBudget(month: string, version: string, allocations: BudgetAllocationInput[]): Promise<Budget> {
  const response = await api.put<Budget>(`/api/v1/budgets/${month}`, { version, allocations })
  return response.data
}

/** Story 6.5: move dinheiro entre duas categorias do mês e devolve o mês recalculado. 400 sem gravar nada. */
export async function reallocateBudget(month: string, input: BudgetReallocationInput): Promise<Budget> {
  const response = await api.post<Budget>(`/api/v1/budgets/${month}/reallocations`, input)
  return response.data
}

/** Story 6.8: meses com orçamento da categoria filha até o atual. 404 grupo, removida ou alheia; 400 id malformado. */
export async function getCategoryBudgetHistory(categoryId: string): Promise<CategoryBudgetHistory> {
  const response = await api.get<CategoryBudgetHistory>(`/api/v1/budgets/categories/${categoryId}/history`)
  return response.data
}

/**
 * Story 9.2: relatório de orçamento por categoria filha em CSV, de `from` a `to` (AAAA-MM, 1 a 12 meses). As opções
 * acrescentam a linha de total por grupo e as colunas de média e tendência. 400 período inválido.
 */
export async function exportBudgetReport(params: {
  from: string
  to: string
  hierarchy: boolean
  averages: boolean
}): Promise<Blob> {
  const response = await api.get<Blob>('/api/v1/budgets/export', { params, responseType: 'blob' })
  return response.data
}
