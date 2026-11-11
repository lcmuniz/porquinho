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
