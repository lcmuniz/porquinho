import { api } from './api'
import type { OnboardingProgress } from '@/types/onboarding'

/** Story 3.1: progresso do usuário logado. O backend cria o registro (passo 1) no primeiro acesso. */
export async function getMyOnboarding(): Promise<OnboardingProgress> {
  const response = await api.get<OnboardingProgress>('/api/v1/onboarding/me')
  return response.data
}

/**
 * Story 3.1: conclui (ou pula) o tutorial do passo 1 e avança para o passo 2.
 * Idempotente: repetir devolve o estado atual sem mudar nada.
 */
export async function completeTutorial(skipped: boolean): Promise<OnboardingProgress> {
  const response = await api.post<OnboardingProgress>('/api/v1/onboarding/me/tutorial', { skipped })
  return response.data
}

/**
 * Story 3.2: conclui o passo 2 (contas) e avança para o passo 3.
 * Sem pular, exige ao menos 1 conta (409). Pulando sem contas, o backend cria "Minha Conta Principal".
 * Idempotente a partir do passo 3.
 */
export async function completeAccounts(skipped: boolean): Promise<OnboardingProgress> {
  const response = await api.post<OnboardingProgress>('/api/v1/onboarding/me/accounts', { skipped })
  return response.data
}
