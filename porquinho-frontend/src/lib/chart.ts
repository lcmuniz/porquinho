/**
 * Base dos gráficos SVG feitos à mão (BalanceChart, WealthChart, TrendChart): área, escala de valores, ticks e
 * navegação por teclado entre os pontos (retro Epic 7 A6).
 */

import { computed, type Ref } from 'vue'
import { useElementSize } from '@vueuse/core'
import { formatBRL } from './money'

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

/**
 * Largura do viewBox: a do projeto, ou a largura real (px) quando o SVG é mais estreito. Assim 1 unidade = 1px e o
 * font-size 13 dos rótulos fica com 13px na tela, em qualquer largura. Sem medida (jsdom, antes de montar), a do projeto.
 */
export function useChartWidth(svg: Ref<SVGElement | null | undefined>, design: number) {
  const { width } = useElementSize(svg)
  const value = computed(() => (width.value > 0 && width.value < design ? Math.floor(width.value) : design))
  return { width: value, narrow: computed(() => value.value < design) }
}

const COMPACT_BRL = new Intl.NumberFormat('pt-BR', {
  style: 'currency',
  currency: 'BRL',
  notation: 'compact',
  minimumFractionDigits: 0,
  maximumFractionDigits: 1,
})

/** Rótulo de eixo em R$: compacto ("R$ 33 mil") no gráfico estreito; o valor exato fica no tooltip e na tabela. */
export function axisBRL(value: number, compact: boolean): string {
  return compact ? COMPACT_BRL.format(value) : formatBRL(value)
}

/** Largura média aproximada de um caractere em 13px. */
const CHAR_PX = 7.5

/** Margem esquerda que cabe o maior rótulo do eixo Y (+ 8 de folga até a área e 4 da borda). */
export function labelPad(labels: string[]): number {
  return Math.ceil(Math.max(0, ...labels.map((l) => l.length)) * CHAR_PX) + 12
}

/** Quantos intervalos de eixo X cabem em `inner` px sem sobrepor rótulos de `chars` caracteres (no máximo `max`). */
export function xTickCount(inner: number, chars: number, max: number): number {
  return Math.max(1, Math.min(max, Math.floor(inner / (chars * CHAR_PX + 16))))
}
