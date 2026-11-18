<template>
  <p v-if="!hasData" class="py-8 text-center text-sm text-gray-700" :data-testid="`${testId}-empty`">
    {{ emptyText }}
  </p>
  <template v-else>
    <svg
      :viewBox="`0 0 ${WIDTH} ${HEIGHT}`"
      class="h-auto w-full"
      role="img"
      :aria-label="`${label}. Os valores de cada mês estão na tabela a seguir.`"
      :data-testid="testId"
    >
      <g v-for="tick in TICKS" :key="tick">
        <line :x1="PAD_LEFT" :x2="WIDTH - PAD_RIGHT" :y1="yOf(tick)" :y2="yOf(tick)" stroke="#e5e7eb" stroke-width="1" />
        <text :x="PAD_LEFT - 6" :y="yOf(tick)" text-anchor="end" dominant-baseline="middle" font-size="11" fill="#6b7280">
          {{ tick }} %
        </text>
      </g>
      <text
        v-for="(month, i) in months"
        :key="month"
        :x="xOf(i)"
        :y="BASE + 16"
        text-anchor="middle"
        font-size="11"
        fill="#6b7280"
      >
        {{ month }}
      </text>
      <g v-for="s in series" :key="s.label" :data-testid="`${testId}-series`">
        <path :d="pathOf(s.values)" fill="none" :stroke="s.color" stroke-width="2" />
        <template v-for="(value, i) in s.values" :key="i">
          <circle v-if="value !== null" :cx="xOf(i)" :cy="yOf(value)" r="3" :fill="s.color">
            <title>{{ s.label }}, {{ months[i] }}: {{ format(value) }}</title>
          </circle>
        </template>
      </g>
    </svg>
    <ul class="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-xs text-gray-700" :data-testid="`${testId}-legend`" aria-hidden="true">
      <li v-for="s in series" :key="s.label" class="flex items-center gap-1">
        <span class="inline-block h-2 w-4 rounded" :style="{ backgroundColor: s.color }" />
        {{ s.label }}
      </li>
    </ul>
    <table class="sr-only" :data-testid="`${testId}-table`">
      <caption>{{ label }}</caption>
      <thead>
        <tr>
          <th scope="col">Banco</th>
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
  </template>
</template>

<script setup lang="ts">
import { computed } from 'vue'

// Story 10.3: linhas em SVG próprio (sem biblioteca), uma por banco, eixo de 0 a 100 %. Um mês null interrompe a linha
const props = withDefaults(defineProps<{
  months: string[]
  series: { label: string; color: string; values: (number | null)[] }[]
  label: string
  testId: string
  format: (value: number) => string
  emptyText?: string
}>(), {
  emptyText: 'Nenhuma classificação nos últimos 6 meses',
})

const WIDTH = 600
const HEIGHT = 200
const PAD_LEFT = 48
const PAD_RIGHT = 24
const TOP = 10
const BASE = HEIGHT - 24
const TICKS = [0, 50, 100]

const hasData = computed(() => props.series.some((s) => s.values.some((v) => v !== null)))

function xOf(i: number) {
  const step = (WIDTH - PAD_LEFT - PAD_RIGHT) / Math.max(1, props.months.length - 1)
  return PAD_LEFT + i * step
}

function yOf(value: number) {
  return BASE - (value / 100) * (BASE - TOP)
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
