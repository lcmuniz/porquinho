import { api, publicApi } from './api'

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

/**
 * Descadastro pelo link do e-mail de alerta de orçamento: rota pública, o token assinado autentica (sem JWT).
 * É o mesmo POST de "um clique" (RFC 8058) que o cliente de e-mail faz pelo cabeçalho List-Unsubscribe.
 */
export async function unsubscribeBudgetAlert(token: string): Promise<void> {
  await publicApi.post('/api/v1/email/unsubscribe', null, { params: { token } })
}
