import { api } from './api'
import type { CreateGoalInput, Goal } from '@/types/goal'

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
