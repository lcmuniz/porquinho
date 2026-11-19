import type { HealthStatus } from '@/types/admin'

/** Retro do Epic 10, A5/D1: um rótulo e uma cor por status em todas as abas do admin. */
export const ADMIN_STATUS: Record<HealthStatus, { label: string; badge: string; border: string }> = {
  GREEN: { label: 'Dentro da meta', badge: 'bg-green-100 text-green-800', border: 'border-green-300' },
  YELLOW: { label: 'Atenção', badge: 'bg-yellow-100 text-yellow-800', border: 'border-yellow-300' },
  RED: { label: 'Crítico', badge: 'bg-red-100 text-red-800', border: 'border-red-300' },
  UNKNOWN: { label: 'Sem dados', badge: 'bg-gray-100 text-gray-700', border: 'border-gray-200' },
}
