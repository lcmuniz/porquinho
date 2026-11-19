import type { RouteLocationRaw } from 'vue-router'
import { completeImport as completeOnboardingImport, getMyOnboarding } from '@/services/onboardingService'

/** Passo do onboarding em que a importação é a primeira do usuário. */
const ONBOARDING_IMPORT_STEP = 6

/** Sem conseguir ler o progresso, vale a /importar. */
async function isOnOnboardingImportStep(): Promise<boolean> {
  try {
    const onboarding = await getMyOnboarding()
    return !onboarding.completed && onboarding.currentStep === ONBOARDING_IMPORT_STEP
  } catch {
    return false
  }
}

/** A /importar não tem caminho de volta ao onboarding: quem veio do passo 6 retorna para lá (Story 5.10). */
export async function exitRoute(): Promise<string> {
  return (await isOnOnboardingImportStep()) ? '/onboarding' : '/importar'
}

/** Estado do histórico que a /transacoes lê ao abrir depois de uma importação concluída. */
export interface ImportCompletedState {
  /** AAAA-MM da transação mais recente da importação; sem ele, o mês atual. */
  importMonth?: string
  importMessage: string
}

/**
 * No passo 6 do onboarding, registra a primeira importação e volta para lá. Se o POST falhar, o "Continuar" do passo 6
 * resolve; a importação já está concluída e não falha por causa do onboarding. Fora dele, abre a /transacoes no mês
 * da importação, com o resumo da conclusão.
 */
export async function routeAfterCompletion(
  id: string,
  state: ImportCompletedState,
): Promise<RouteLocationRaw> {
  if (!(await isOnOnboardingImportStep())) return { name: 'transactions', state: { ...state } }
  try {
    await completeOnboardingImport(id)
  } catch {
    // o passo 6 mostra "Continuar", porque a importação já está concluída
  }
  return '/onboarding'
}
