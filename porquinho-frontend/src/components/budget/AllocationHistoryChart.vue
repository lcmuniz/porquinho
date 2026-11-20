<template>
  <figure class="space-y-3" data-testid="allocation-chart">
    <svg
      ref="svg"
      :viewBox="`0 0 ${WIDTH} ${HEIGHT}`"
      class="h-auto w-full overflow-visible"
      role="img"
      aria-label="Gráfico do alocado e do gasto por mês. Os valores estão na tabela abaixo."
      data-testid="allocation-chart-svg"
    >
      <!-- Eixo Y (R$) com linhas de grade -->
      <g data-testid="chart-y-axis">
        <g v-for="(tick, i) in yTicks" :key="i">
          <line
            :x1="PAD.left"
            :x2="WIDTH - PAD.right"
            :y1="tick.y"
            :y2="tick.y"
            stroke="#E2E8F0"
            stroke-width="1"
          />
          <text
            :x="PAD.left - 8"
            :y="tick.y"
            text-anchor="end"
            dominant-baseline="middle"
            font-size="13"
            fill="#475569"
          >
            {{ tick.label }}
          </text>
        </g>
      </g>

      <!-- Eixo X (meses) -->
      <g data-testid="chart-x-axis">
        <line
          :x1="PAD.left"
          :x2="WIDTH - PAD.right"
          :y1="HEIGHT - PAD.bottom"
          :y2="HEIGHT - PAD.bottom"
          stroke="#94A3B8"
          stroke-width="1"
        />
        <text
          v-for="tick in xTicks"
          :key="tick.month"
          :x="tick.x"
          :y="HEIGHT - PAD.bottom + 18"
          :text-anchor="anchorOf(tick.x)"
          font-size="13"
          fill="#475569"
        >
          {{ tick.label }}
        </text>
      </g>

      <g v-for="series in SERIES" :key="series.key">
        <polyline
          :points="points(series.key).map((p) => `${p.x},${p.y}`).join(' ')"
          fill="none"
          :stroke="series.color"
          stroke-width="2"
          stroke-linejoin="round"
          :stroke-dasharray="series.dash"
          :data-testid="`chart-line-${series.key}`"
        />
        <!-- Marcadores: um mês só ainda aparece no gráfico -->
        <circle
          v-for="(p, i) in points(series.key)"
          :key="i"
          :cx="p.x"
          :cy="p.y"
          r="3"
          :fill="series.color"
        />
      </g>
    </svg>

    <figcaption>
      <ul class="flex flex-wrap gap-x-4 gap-y-1 text-xs text-gray-700" data-testid="chart-legend">
        <li v-for="series in SERIES" :key="series.key" class="inline-flex items-center gap-1.5">
          <svg viewBox="0 0 16 4" class="h-1 w-4" aria-hidden="true">
            <line x1="0" y1="2" x2="16" y2="2" :stroke="series.color" stroke-width="2" :stroke-dasharray="series.dash" />
          </svg>
          {{ series.label }}
        </li>
      </ul>
    </figcaption>
  </figure>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { axisBRL, labelPad, useChartWidth, xTickCount } from '@/lib/chart'
import type { CategoryBudgetMonth } from '@/types/budget'

/**
 * Story 6.8: alocado × gasto por mês em SVG feito à mão (sem biblioteca, como o BalanceChart). Meses em espaços iguais,
 * do mais antigo ao mais novo. A tabela da tela é a alternativa acessível.
 */
const props = defineProps<{ months: CategoryBudgetMonth[] }>()

const HEIGHT = 280
const DESIGN_PAD = { top: 16, right: 24, bottom: 32, left: 104 }
const Y_TICKS = 4
const MAX_X_LABELS = 6

const SERIES = [
  { key: 'allocated', label: 'Alocado', color: '#1E40AF', dash: undefined },
  { key: 'spent', label: 'Gasto', color: '#B91C1C', dash: '6 4' },
] as const

const SHORT_MONTH = new Intl.DateTimeFormat('pt-BR', { month: 'short', year: '2-digit', timeZone: 'UTC' })

const svg = ref<SVGElement | null>(null)
const { width: WIDTH, narrow } = useChartWidth(svg, 640)

/** De 0 até o maior valor; tudo zero ganha uma faixa mínima. */
const yMax = computed(() => Math.max(1, ...props.months.flatMap((m) => [m.allocated, m.spent])))

/** Rótulo a menos de meio rótulo (~40px) da ponta fica ancorado nela: não invade o eixo Y nem sai do gráfico. */
function anchorOf(x: number): 'start' | 'middle' | 'end' {
  if (xTicks.value.length < 2) return 'middle'
  if (x - PAD.value.left < 40) return 'start'
  return WIDTH.value - PAD.value.right - x < 40 ? 'end' : 'middle'
}

/** Estreito: margem esquerda do tamanho do maior rótulo. */
const PAD = computed(() =>
  narrow.value ? { ...DESIGN_PAD, left: labelPad([axisBRL(yMax.value, true)]) } : DESIGN_PAD,
)

function xOf(i: number): number {
  const inner = WIDTH.value - PAD.value.left - PAD.value.right
  return props.months.length === 1 ? PAD.value.left + inner / 2 : PAD.value.left + (i / (props.months.length - 1)) * inner
}

function yOf(value: number): number {
  return (
    DESIGN_PAD.top + ((yMax.value - Math.max(value, 0)) / yMax.value) * (HEIGHT - DESIGN_PAD.top - DESIGN_PAD.bottom)
  )
}

function points(key: 'allocated' | 'spent') {
  return props.months.map((m, i) => ({ x: xOf(i), y: yOf(m[key]) }))
}

const yTicks = computed(() =>
  Array.from({ length: Y_TICKS + 1 }, (_, i) => {
    const value = Math.round(((yMax.value * i) / Y_TICKS) * 100) / 100
    return { value, label: axisBRL(value, narrow.value), y: yOf(value) }
  }),
)

/** Até 6 rótulos, sempre com o mês mais novo. */
const xTicks = computed(() => {
  // Rótulo na ponta fica ancorado nela (não invade o eixo Y): cada intervalo precisa de 1,5 rótulo
  const labels = xTickCount(WIDTH.value - PAD.value.left - PAD.value.right, 15, MAX_X_LABELS - 1) + 1
  const step = Math.ceil(props.months.length / labels)
  const last = props.months.length - 1
  return props.months
    .map((m, i) => ({ i, month: m.month }))
    .filter(({ i }) => (last - i) % step === 0)
    .map(({ i, month }) => {
      const [year, monthNumber] = month.split('-').map(Number)
      return { month, x: xOf(i), label: SHORT_MONTH.format(Date.UTC(year!, monthNumber! - 1, 1)) }
    })
})
</script>
