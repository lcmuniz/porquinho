import { CircleAlert, CircleCheck, TriangleAlert } from 'lucide-vue-next'
import { formatBRL } from '@/lib/money'
import type { BudgetStatus } from '@/types/budget'

// Stories 6.4/6.7/6.8. Green/Yellow/Red 600 na barra; o ícone amarelo usa o 700 para ter contraste 3:1 (WCAG 1.4.11).
// O rótulo fica escuro
export const STATUS: Record<
  BudgetStatus,
  { label: (remaining: number) => string; icon: typeof CircleCheck; bar: string; text: string }
> = {
  HEALTHY: { label: () => 'Saudável', icon: CircleCheck, bar: 'bg-green-600', text: 'text-green-600' },
  LOW: {
    label: (r) => `Saldo baixo - ${formatBRL(r)} restantes`,
    icon: TriangleAlert,
    bar: 'bg-yellow-600',
    text: 'text-yellow-700',
  },
  OVERSPENT: {
    label: (r) => `Categoria estourada! ${formatBRL(Math.abs(r))} acima do orçamento`,
    icon: CircleAlert,
    bar: 'bg-red-600',
    text: 'text-red-600',
  },
}

export function statusOf(c: { status: BudgetStatus | null }) {
  return c.status ? STATUS[c.status] : null
}

/**
 * Story 6.9: regra do épico para linhas somadas no front (restante < 0; até 20% do alocado; acima). Em centavos para
 * não errar a fronteira dos 20% por arredondamento de float.
 */
export function computeStatus(allocated: number, remaining: number): BudgetStatus {
  const a = Math.round(allocated * 100)
  const r = Math.round(remaining * 100)
  return r < 0 ? 'OVERSPENT' : r * 5 <= a ? 'LOW' : 'HEALTHY'
}
