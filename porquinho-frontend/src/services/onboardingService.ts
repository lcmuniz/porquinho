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

/**
 * Story 3.3: conclui o passo 3 (cartões, opcionais) e avança para o passo 4.
 * Próximo sem cartões é permitido (grava `cards_skipped`). Pular não cria cartão.
 * Idempotente a partir do passo 4.
 */
export async function completeCards(skipped: boolean): Promise<OnboardingProgress> {
  const response = await api.post<OnboardingProgress>('/api/v1/onboarding/me/cards', { skipped })
  return response.data
}

/**
 * Story 3.4: conclui o passo 4 criando as categorias sugeridas escolhidas e avança para o passo 5.
 * Exige ao menos 1 chave válida (400). Idempotente a partir do passo 5 (nada é criado de novo).
 */
export async function completeCategories(keys: string[]): Promise<OnboardingProgress> {
  const response = await api.post<OnboardingProgress>('/api/v1/onboarding/me/categories', { keys })
  return response.data
}

/**
 * Story 3.5: conclui o passo 5 (personalizar categorias) e avança para o passo 6.
 * As edições já foram salvas uma a uma; pular não desfaz nada. Idempotente a partir do passo 6.
 */
export async function completeCustomization(skipped: boolean): Promise<OnboardingProgress> {
  const response = await api.post<OnboardingProgress>('/api/v1/onboarding/me/customization', {
    skipped,
  })
  return response.data
}

/**
 * Conclui o passo 6 e avança para o passo 7. Sem `importId`, "Importar depois": o backend grava `import_skipped` (Story
 * 3.6). Com ele, a importação concluída grava `first_import_completed` (Story 5.10): 404 se não for do usuário; 409 em
 * outro status. Idempotente a partir do passo 7.
 */
export async function completeImport(importId?: string): Promise<OnboardingProgress> {
  const response = importId
    ? await api.post<OnboardingProgress>('/api/v1/onboarding/me/import', { importId })
    : await api.post<OnboardingProgress>('/api/v1/onboarding/me/import')
  return response.data
}

/**
 * Story 3.7: a última tela do tour do Dashboard GPS conclui o passo 7 e avança para o passo 8.
 * Sem corpo; o backend grava `dashboard_gps_explained`. Idempotente a partir do passo 8.
 */
export async function completeDashboardTour(): Promise<OnboardingProgress> {
  const response = await api.post<OnboardingProgress>('/api/v1/onboarding/me/dashboard-tour')
  return response.data
}

/**
 * Story 3.8: conclui o passo 8, o último, e marca o onboarding como concluído.
 * Sem pular, exige ao menos 1 meta ativa (409); pulando, não cria meta. Idempotente depois de concluído.
 */
export async function finishOnboarding(skipped: boolean): Promise<OnboardingProgress> {
  const response = await api.post<OnboardingProgress>('/api/v1/onboarding/me/finish', { skipped })
  return response.data
}
