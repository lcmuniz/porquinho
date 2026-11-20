<template>
  <p v-if="!hasData" class="py-8 text-center text-sm text-gray-700" :data-testid="`${testId}-empty`">
    {{ emptyText }}
  </p>
  <template v-else>
    <svg
      ref="svg"
      :viewBox="`0 0 ${WIDTH} ${HEIGHT}`"
      class="h-auto w-full"
      role="img"
      :aria-label="`${label}. Os valores de cada ponto estão na tabela a seguir.`"
      :data-testid="testId"
    >
      <g v-for="tick in ticks" :key="tick">
        <line :x1="PAD_LEFT" :x2="WIDTH - PAD_RIGHT" :y1="yOf(tick)" :y2="yOf(tick)" stroke="#E2E8F0" stroke-width="1" />
        <text :x="PAD_LEFT - 6" :y="yOf(tick)" text-anchor="end" dominant-baseline="middle" font-size="13" fill="#475569">
          {{ tickFormat(tick) }}
        </text>
      </g>
      <text
        v-for="(month, i) in months"
        v-show="(months.length - 1 - i) % labelStep === 0"
        :key="month"
        :x="xOf(i)"
        :y="BASE + 16"
        text-anchor="middle"
        font-size="13"
        fill="#475569"
      >
        {{ month }}
      </text>
      <g v-for="(s, si) in series" :key="s.label" :data-testid="`${testId}-series`">
        <path :d="pathOf(s.values)" fill="none" :stroke="s.color" stroke-width="2" :stroke-dasharray="s.dash ?? dashOf(si)" />
        <template v-for="(value, i) in s.values" :key="i">
          <circle v-if="value !== null" :cx="xOf(i)" :cy="yOf(value)" r="3" :fill="s.color">
            <title>{{ s.label }}, {{ months[i] }}: {{ format(value) }}</title>
          </circle>
        </template>
      </g>
    </svg>
    <ul class="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-xs text-gray-700" :data-testid="`${testId}-legend`" aria-hidden="true">
      <li v-for="(s, si) in series" :key="s.label" class="flex items-center gap-1">
        <svg width="24" height="8" aria-hidden="true">
          <line x1="0" x2="24" y1="4" y2="4" :stroke="s.color" stroke-width="2" :stroke-dasharray="s.dash ?? dashOf(si)" />
        </svg>
        {{ s.label }}
      </li>
    </ul>
    <!-- table ignora o width/overflow do sr-only e alarga a página; o div recorta -->
    <div class="sr-only">
      <table :data-testid="`${testId}-table`">
        <caption>{{ label }}</caption>
        <thead>
          <tr>
            <th scope="col">{{ seriesHeader }}</th>
            <th v-for="month in months" :key="month" scope="col">{{ month }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="s in series" :key="s.label">
            <th scope="row">{{ s.label }}</th>
            <td v-for="(value, i) in s.values" :key="i">{{ value === null ? 'sem dados' : format(value) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </template>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useChartWidth, xTickCount } from '@/lib/chart'

// Story 10.3: linhas em SVG próprio (sem biblioteca), uma por banco, eixo de 0 a 100 %. Um mês null interrompe a linha.
// Story 10.4: `max` e `tickFormat` trocam o eixo (contagens de usuários), `seriesHeader` a coluna da tabela acessível
const props = withDefaults(defineProps<{
  months: string[]
  series: { label: string; color: string; dash?: string; values: (number | null)[] }[]
  label: string
  testId: string
  format: (value: number) => string
  emptyText?: string
  max?: number
  tickFormat?: (tick: number) => string
  seriesHeader?: string
}>(), {
  emptyText: 'Nenhuma classificação nos últimos 6 meses',
  max: 100,
  tickFormat: (tick: number) => `${tick} %`,
  seriesHeader: 'Banco',
})

const svg = ref<SVGElement | null>(null)
const { width: WIDTH } = useChartWidth(svg, 600)
const HEIGHT = 200
const PAD_LEFT = 48
const PAD_RIGHT = 24
const TOP = 10
const BASE = HEIGHT - 24
// Traço próprio por série: a linha não depende só da cor (WCAG 1.4.1)
const DASHES = ['none', '6 3', '2 3', '10 3 2 3', '1 5', '12 6', '6 2 2 2 2 2']

function dashOf(i: number) {
  return DASHES[i % DASHES.length]
}

// Zero, meio e topo; max 0 (sem nenhum usuário) não divide por zero
const top = computed(() => Math.max(props.max, 1))
const ticks = computed(() => [0, top.value / 2, top.value])

const hasData = computed(() => props.series.some((s) => s.values.some((v) => v !== null)))

function xOf(i: number) {
  const step = (WIDTH.value - PAD_LEFT - PAD_RIGHT) / Math.max(1, props.months.length - 1)
  return PAD_LEFT + i * step
}

// Só os rótulos do eixo X que cabem sem sobrepor, sempre com o mais novo
const labelStep = computed(() => {
  const n = props.months.length
  const chars = Math.max(1, ...props.months.map((m) => m.length))
  return Math.ceil(n / (xTickCount(WIDTH.value - PAD_LEFT - PAD_RIGHT, chars, n) + 1))
})

function yOf(value: number) {
  return BASE - (value / top.value) * (BASE - TOP)
}

// "M" recomeça a linha depois de um mês sem dado
function pathOf(values: (number | null)[]) {
  let d = ''
  let open = false
  values.forEach((v, i) => {
    if (v === null) {
      open = false
      return
    }
    d += `${open ? 'L' : 'M'}${xOf(i)},${yOf(v)} `
    open = true
  })
  return d.trim()
}
</script>
