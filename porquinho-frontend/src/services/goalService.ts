import { api } from './api'
import type { CreateGoalInput, Goal, GoalsSummary } from '@/types/goal'

/** Story 3.8: metas ativas do usuário logado, da mais antiga para a mais nova. */
export async function listGoals(): Promise<Goal[]> {
  const response = await api.get<Goal[]>('/api/v1/goals')
  return response.data
}

/** Story 3.8: cria a meta (valor atual 0). 400 traz `errors` por campo. */
export async function createGoal(input: CreateGoalInput): Promise<Goal> {
  const response = await api.post<Goal>('/api/v1/goals', input)
  return response.data
}

/** Story 8.2: muda o aporte mensal da meta e o alocado do mês corrente. 400 traz `errors.monthlyAllocation`. */
export async function updateGoalAllocation(id: string, monthlyAllocation: number): Promise<Goal> {
  const response = await api.put<Goal>(`/api/v1/goals/${id}/allocation`, { monthlyAllocation })
  return response.data
}

/** Story 8.2: registra um aporte (só na meta; não cria transação). 400 traz `errors.amount`. */
export async function contributeToGoal(id: string, amount: number): Promise<Goal> {
  const response = await api.post<Goal>(`/api/v1/goals/${id}/contributions`, { amount })
  return response.data
}

/** Story 8.2: total disponível para comparar com a soma das alocações. */
export async function getGoalsSummary(): Promise<GoalsSummary> {
  const response = await api.get<GoalsSummary>('/api/v1/goals/summary')
  return response.data
}
