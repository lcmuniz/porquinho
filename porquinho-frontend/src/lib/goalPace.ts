/**
 * Story 8.3: ritmo da meta (verde/amarelo/vermelho), reaproveitado na 8.4. Meses são "AAAA-MM" em America/Sao_Paulo.
 */
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
