<template>
  <p v-if="!hasData" class="py-8 text-center text-sm text-gray-700" :data-testid="`${testId}-empty`">
    Sem dados nas últimas 24 horas
  </p>
  <template v-else>
  <svg
    :viewBox="`0 0 ${WIDTH} ${HEIGHT}`"
    class="h-auto w-full"
    role="img"
    :aria-label="`${label}. Os valores de cada hora estão na tabela a seguir.`"
    :data-testid="testId"
  >
    <line
      :x1="PAD_LEFT"
      :x2="WIDTH"
      :y1="BASE"
      :y2="BASE"
      stroke="#e5e7eb"
      stroke-width="1"
    />
    <text :x="PAD_LEFT - 6" :y="TOP" text-anchor="end" dominant-baseline="middle" font-size="11" fill="#6b7280">
      {{ format(max) }}
    </text>
    <text :x="PAD_LEFT - 6" :y="BASE" text-anchor="end" dominant-baseline="middle" font-size="11" fill="#6b7280">
      0
    </text>
    <g v-for="(point, i) in points" :key="i">
      <rect
        v-if="point.value"
        :x="xOf(i) + 1"
        :y="BASE - heightOf(point.value)"
        :width="barWidth - 2"
        :height="heightOf(point.value)"
        :fill="color"
      >
        <title>{{ point.label }}: {{ format(point.value) }}</title>
      </rect>
      <text
        v-if="i % 4 === 0"
        :x="xOf(i) + barWidth / 2"
        :y="BASE + 16"
        text-anchor="middle"
        font-size="11"
        fill="#6b7280"
      >
        {{ point.label }}
      </text>
    </g>
  </svg>
  <table class="sr-only" :data-testid="`${testId}-table`">
    <caption>{{ label }}</caption>
    <thead>
      <tr><th scope="col">Hora</th><th scope="col">Valor</th></tr>
    </thead>
    <tbody>
      <tr v-for="(point, i) in points" :key="i">
        <td>{{ point.label }}</td>
        <td>{{ point.value === null ? 'sem dados' : format(point.value) }}</td>
      </tr>
    </tbody>
  </table>
  </template>
</template>

<script setup lang="ts">
import { computed } from 'vue'

// Story 10.1: barras simples em SVG próprio (sem biblioteca de gráfico), uma por hora
const props = defineProps<{
  points: { label: string; value: number | null }[]
  label: string
  color: string
  testId: string
  format: (value: number) => string
  // Vem do pai (houve requisição): um dia sem nenhum erro ainda tem dados
  hasData: boolean
}>()

const WIDTH = 600
const HEIGHT = 180
const PAD_LEFT = 56
const TOP = 10
const BASE = HEIGHT - 24

const max = computed(() => Math.max(0, ...props.points.map((p) => p.value ?? 0)))
const barWidth = computed(() => (WIDTH - PAD_LEFT) / Math.max(1, props.points.length))

function xOf(i: number) {
  return PAD_LEFT + i * barWidth.value
}

function heightOf(value: number) {
  return max.value === 0 ? 0 : (value / max.value) * (BASE - TOP)
}
</script>
