import type { BalanceEvent } from '@/types/account'

/** Story 4.7: rótulo de cada evento do histórico de saldo. */
export const BALANCE_EVENT_LABEL: Record<BalanceEvent, string> = {
  initial: 'Saldo inicial',
  edit: 'Edição',
  transfer: 'Transferência',
  reconciliation: 'Reconciliação',
  transaction: 'Transação',
}

export type MarkerShape = 'circle' | 'square' | 'diamond' | 'triangle' | 'triangle-down'

/** Cor e forma do marcador de cada evento no gráfico (a forma distingue os tipos sem depender da cor). */
export interface Marker {
  color: string
  shape: MarkerShape
}

export const BALANCE_EVENT_MARKER: Record<BalanceEvent, Marker> = {
  initial: { color: '#4b5563', shape: 'circle' },
  edit: { color: '#2563eb', shape: 'square' },
  transfer: { color: '#0891b2', shape: 'diamond' },
  reconciliation: { color: '#16a34a', shape: 'triangle' },
  transaction: { color: '#d97706', shape: 'triangle-down' },
}

/** Evento desconhecido (versão nova do backend) cai num marcador neutro em vez de quebrar o gráfico. */
export const DEFAULT_MARKER: Marker = { color: '#6b7280', shape: 'circle' }

export function markerOf(event: string): Marker {
  return BALANCE_EVENT_MARKER[event as BalanceEvent] ?? DEFAULT_MARKER
}

export function eventLabel(event: string): string {
  return BALANCE_EVENT_LABEL[event as BalanceEvent] ?? event
}

export const BALANCE_EVENTS = Object.keys(BALANCE_EVENT_LABEL) as BalanceEvent[]

/** Caminho SVG do marcador centrado em (x, y) com raio r. */
export function markerPath(shape: MarkerShape, x: number, y: number, r: number): string {
  switch (shape) {
    case 'square':
      return `M${x - r},${y - r}h${2 * r}v${2 * r}h${-2 * r}Z`
    case 'diamond':
      return `M${x},${y - r * 1.25}L${x + r * 1.25},${y}L${x},${y + r * 1.25}L${x - r * 1.25},${y}Z`
    case 'triangle-down':
      return `M${x},${y + r * 1.3}L${x + r * 1.2},${y - r}L${x - r * 1.2},${y - r}Z`
    case 'triangle':
      return `M${x},${y - r * 1.3}L${x + r * 1.2},${y + r}L${x - r * 1.2},${y + r}Z`
    default:
      return `M${x - r},${y}a${r},${r} 0 1,0 ${2 * r},0a${r},${r} 0 1,0 ${-2 * r},0`
  }
}
