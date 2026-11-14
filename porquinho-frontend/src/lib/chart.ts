/**
 * Base dos gráficos SVG feitos à mão (BalanceChart, WealthChart, TrendChart): área, escala de valores, ticks e
 * navegação por teclado entre os pontos (retro Epic 7 A6).
 */

export interface ChartPadding {
  top: number
  right: number
  bottom: number
  left: number
}

/** Área das curvas de saldo e de patrimônio. */
export const LINE_CHART = {
  WIDTH: 640,
  HEIGHT: 280,
  PAD: { top: 16, right: 16, bottom: 32, left: 104 } as ChartPadding,
}

/** Mínimo e máximo; com saldo negativo o zero entra (a linha aparece abaixo dele); valor único ganha folga. */
export function balanceDomain(values: number[]): [number, number] {
  let lo = Infinity
  let hi = -Infinity
  for (const v of values) {
    if (v < lo) lo = v
    if (v > hi) hi = v
  }
  if (!values.length) [lo, hi] = [0, 0]
  if (lo < 0) hi = Math.max(hi, 0)
  if (lo === hi) {
    const pad = Math.max(Math.abs(lo) * 0.1, 1)
    lo -= pad
    hi += pad
  }
  return [lo, hi]
}

/** y do valor: `hi` no topo da área, `lo` na base. */
export function yOf(value: number, [lo, hi]: [number, number], height: number, pad: ChartPadding): number {
  return pad.top + ((hi - value) / (hi - lo || 1)) * (height - pad.top - pad.bottom)
}

/** `count` + 1 valores igualmente espaçados, em centavos; faixas pequenas arredondam igual: um tick por valor. */
export function valueTicks([lo, hi]: [number, number], count: number): number[] {
  const values = new Set<number>()
  for (let i = 0; i <= count; i++) values.add(Math.round((lo + ((hi - lo) * i) / count) * 100) / 100)
  return [...values]
}

/**
 * Tabindex móvel: setas e Home/End levam o foco ao ponto vizinho (`[data-testid="<prefix>-<i>"]` no mesmo SVG).
 * Devolve o novo índice, ou null quando a tecla não é de navegação.
 */
export function movePointFocus(event: KeyboardEvent, index: number, last: number, testIdPrefix: string): number | null {
  const next =
    event.key === 'ArrowRight'
      ? Math.min(index + 1, last)
      : event.key === 'ArrowLeft'
        ? Math.max(index - 1, 0)
        : event.key === 'Home'
          ? 0
          : event.key === 'End'
            ? last
            : null
  if (next === null) return null
  event.preventDefault()
  const svg = (event.currentTarget as SVGElement).ownerSVGElement
  svg?.querySelector<SVGElement>(`[data-testid="${testIdPrefix}-${next}"]`)?.focus()
  return next
}
