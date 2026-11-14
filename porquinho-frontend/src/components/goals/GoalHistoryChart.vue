<template>
  <figure class="space-y-3" data-testid="goal-history-chart">
    <svg
      :viewBox="`0 0 ${WIDTH} ${HEIGHT}`"
      class="h-auto w-full overflow-visible"
      role="img"
      aria-label="Gráfico do valor acumulado da meta ao longo do tempo. Os valores estão na tabela abaixo."
      data-testid="goal-history-chart-svg"
    >
      <!-- Eixo Y (R$) com linhas de grade -->
      <g data-testid="chart-y-axis">
        <g v-for="(tick, i) in yTicks" :key="i">
          <line
            :x1="PAD.left"
            :x2="WIDTH - PAD.right"
            :y1="tick.y"
            :y2="tick.y"
            stroke="#e5e7eb"
            stroke-width="1"
          />
          <text
            :x="PAD.left - 8"
            :y="tick.y"
            text-anchor="end"
            dominant-baseline="middle"
            font-size="11"
            fill="#6b7280"
          >
            {{ formatBRL(tick.value) }}
          </text>
        </g>
      </g>

      <!-- Eixo X (meses, escala de tempo) -->
      <g data-testid="chart-x-axis">
        <line
          :x1="PAD.left"
          :x2="WIDTH - PAD.right"
          :y1="HEIGHT - PAD.bottom"
          :y2="HEIGHT - PAD.bottom"
          stroke="#9ca3af"
          stroke-width="1"
        />
        <text
          v-for="tick in xTicks"
          :key="tick.month"
          :x="tick.x"
          :y="HEIGHT - PAD.bottom + 18"
          text-anchor="middle"
          font-size="11"
          fill="#6b7280"
          data-testid="chart-x-tick"
        >
          {{ tick.label }}
        </text>
      </g>

      <polyline
        :points="points.map((p) => `${p.x},${p.y}`).join(' ')"
        fill="none"
        :stroke="COLOR"
        stroke-width="2"
        stroke-linejoin="round"
        data-testid="chart-line"
      />
      <!-- Marcador só nos aportes -->
      <circle
        v-for="(p, i) in markers"
        :key="i"
        :cx="p.x"
        :cy="p.y"
        r="4"
        :fill="COLOR"
        data-testid="chart-contribution-marker"
      />
    </svg>

    <figcaption>
      <ul class="flex flex-wrap gap-x-4 gap-y-1 text-xs text-gray-700" data-testid="chart-legend">
        <li class="inline-flex items-center gap-1.5">
          <span class="inline-block h-0.5 w-4 bg-[#10B981]" aria-hidden="true" />
          Acumulado
        </li>
        <li class="inline-flex items-center gap-1.5">
          <span class="inline-block size-2 rounded-full bg-[#10B981]" aria-hidden="true" />
          Aporte
        </li>
      </ul>
    </figcaption>
  </figure>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { formatBRL } from '@/lib/money'
import type { GoalHistoryEntry } from '@/types/goal'

/**
 * Story 8.8: acumulado da meta em SVG feito à mão (molde do AllocationHistoryChart). Eixo X em escala de tempo (a
 * distância entre pontos segue as datas), com rótulos nos meses. A tabela da tela é a alternativa acessível.
 */
const props = defineProps<{ entries: GoalHistoryEntry[] }>()

const WIDTH = 640
const HEIGHT = 280
const PAD = { top: 16, right: 24, bottom: 32, left: 104 }
const Y_TICKS = 4
const MAX_X_LABELS = 6
const COLOR = '#10B981'

const SHORT_MONTH = new Intl.DateTimeFormat('pt-BR', { month: 'short', year: '2-digit', timeZone: 'UTC' })

/** "AAAA-MM-DD" em ms UTC, sem passar pelo fuso local. */
function timeOf(date: string): number {
  const [year, month, day] = date.split('-').map(Number)
  return Date.UTC(year!, month! - 1, day!)
}

const times = computed(() => props.entries.map((e) => timeOf(e.date)))
const minTime = computed(() => Math.min(...times.value))
const maxTime = computed(() => Math.max(...times.value))

/** De 0 até o maior valor; tudo zero ganha uma faixa mínima. */
const yMax = computed(() => Math.max(1, ...props.entries.map((e) => e.amount)))

function xOf(time: number): number {
  const inner = WIDTH - PAD.left - PAD.right
  const span = maxTime.value - minTime.value
  return span === 0 ? PAD.left + inner / 2 : PAD.left + ((time - minTime.value) / span) * inner
}

function yOf(value: number): number {
  return PAD.top + ((yMax.value - Math.max(value, 0)) / yMax.value) * (HEIGHT - PAD.top - PAD.bottom)
}

const points = computed(() =>
  props.entries.map((e, i) => ({ x: xOf(times.value[i]!), y: yOf(e.amount), contribution: e.contributionAmount })),
)

const markers = computed(() => points.value.filter((p) => p.contribution !== null))

const yTicks = computed(() =>
  Array.from({ length: Y_TICKS + 1 }, (_, i) => {
    const value = Math.round(((yMax.value * i) / Y_TICKS) * 100) / 100
    return { value, y: yOf(value) }
  }),
)

/**
 * Um rótulo por mês do período, no dia 1º (o primeiro mês fica no primeiro ponto); até 6, sempre com o mês mais novo.
 */
const xTicks = computed(() => {
  if (props.entries.length === 0) return []
  const first = new Date(minTime.value)
  const months: number[] = []
  for (let i = 0; Date.UTC(first.getUTCFullYear(), first.getUTCMonth() + i, 1) <= maxTime.value; i++) {
    months.push(Date.UTC(first.getUTCFullYear(), first.getUTCMonth() + i, 1))
  }
  const step = Math.ceil(months.length / MAX_X_LABELS)
  const lastIndex = months.length - 1
  return months
    .filter((_, i) => (lastIndex - i) % step === 0)
    .map((start) => ({
      month: start,
      x: xOf(Math.max(start, minTime.value)),
      label: SHORT_MONTH.format(start),
    }))
})
</script>
