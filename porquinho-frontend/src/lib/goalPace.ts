/**
 * Story 8.3: ritmo da meta (verde/amarelo/vermelho), reaproveitado na 8.4. Meses são "AAAA-MM" em America/Sao_Paulo.
 */
import { shiftMonth } from './date'
import { MAX_FORECAST_MONTHS } from './goalPlan'
import type { Goal } from '@/types/goal'

export type GoalPace = 'on-track' | 'late' | 'very-late'

export const PACE_LABEL: Record<GoalPace, string> = {
  'on-track': 'Em dia',
  late: 'Atrasada',
  'very-late': 'Muito atrasada',
}

/** Meses de `from` até `to` ("AAAA-MM"); negativo se `to` vem antes. */
export function monthsBetween(from: string, to: string): number {
  const [fy, fm] = from.split('-').map(Number)
  const [ty, tm] = to.split('-').map(Number)
  return (ty! - fy!) * 12 + (tm! - fm!)
}

/**
 * Meses a partir do mês corrente até o acumulado chegar ao alvo: ⌈(alvo − acumulado) / alocação⌉, em centavos.
 * 0 se já chegou; null sem alocação positiva.
 */
export function monthsToTarget(
  targetAmount: number,
  accumulated: number,
  monthlyAllocation: number | null,
): number | null {
  const missing = Math.round(targetAmount * 100) - Math.round(accumulated * 100)
  if (missing <= 0) return 0
  if (!monthlyAllocation || monthlyAllocation <= 0) return null
  return Math.ceil(missing / Math.round(monthlyAllocation * 100))
}

/**
 * Atraso = meses entre o mês da data-alvo e o mês projetado. ≤ 0 em dia; 1 a 3 atrasada; > 3 muito atrasada.
 * Sem data-alvo, sem alocação ou com o alvo atingido: em dia.
 */
export function goalPace(goal: Goal, currentMonth: string): GoalPace {
  if (!goal.targetDate) return 'on-track'
  const months = monthsToTarget(goal.targetAmount, goal.currentAmount, goal.monthlyAllocation)
  if (!months) return 'on-track'
  const delay = months - monthsBetween(currentMonth, goal.targetDate.slice(0, 7))
  if (delay <= 0) return 'on-track'
  return delay <= 3 ? 'late' : 'very-late'
}

/** Aporte para cumprir o prazo: mensal até o mês da data-alvo, ou o que falta de uma vez se o prazo já chegou. */
export type GoalSuggestion = { kind: 'monthly' | 'lump-sum'; amount: number }

export type GoalProjection =
  | { kind: 'reached' }
  | { kind: 'no-allocation'; suggestion: GoalSuggestion | null }
  | { kind: 'too-far'; suggestion: GoalSuggestion | null }
  | { kind: 'month'; month: string; suggestion: GoalSuggestion | null }

/**
 * Story 8.4: mês projetado = mês corrente + monthsToTarget. Sugestão só com data-alvo e ritmo atrasado
 * (mensal, ⌈falta / meses até o mês da data-alvo⌉ no centavo) ou com o prazo no mês corrente ou antes (o que falta).
 */
export function goalProjection(goal: Goal, currentMonth: string): GoalProjection {
  const months = monthsToTarget(goal.targetAmount, goal.currentAmount, goal.monthlyAllocation)
  if (months === 0) return { kind: 'reached' }
  const missing = Math.round(goal.targetAmount * 100) - Math.round(goal.currentAmount * 100)
  let suggestion: GoalSuggestion | null = null
  if (goal.targetDate) {
    const monthsLeft = monthsBetween(currentMonth, goal.targetDate.slice(0, 7))
    if (monthsLeft <= 0) suggestion = { kind: 'lump-sum', amount: missing / 100 }
    else if (goalPace(goal, currentMonth) !== 'on-track')
      suggestion = { kind: 'monthly', amount: Math.ceil(missing / monthsLeft) / 100 }
  }
  if (months === null) return { kind: 'no-allocation', suggestion }
  if (months > MAX_FORECAST_MONTHS) return { kind: 'too-far', suggestion }
  return { kind: 'month', month: shiftMonth(currentMonth, months), suggestion }
}
