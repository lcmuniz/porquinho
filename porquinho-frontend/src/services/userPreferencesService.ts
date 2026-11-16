import { api } from './api'

/** Pré-Epic 10: preferências do usuário; hoje só o alerta de orçamento por e-mail (ligado por padrão). */
export interface UserPreferences {
  budgetAlertEmail: boolean
}

export async function getMyPreferences(): Promise<UserPreferences> {
  const response = await api.get<UserPreferences>('/api/v1/users/me/preferences')
  return response.data
}

export async function updateMyPreferences(preferences: UserPreferences): Promise<UserPreferences> {
  const response = await api.put<UserPreferences>('/api/v1/users/me/preferences', preferences)
  return response.data
}
