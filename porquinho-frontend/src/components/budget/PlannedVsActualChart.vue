<template>
  <figure class="space-y-3" data-testid="comparison-chart">
    <svg
      ref="svg"
      :viewBox="`0 0 ${WIDTH} ${height}`"
      class="h-auto w-full overflow-visible"
      role="img"
      aria-label="Gráfico do alocado e do gasto por categoria. Os valores estão na tabela abaixo."
      data-testid="comparison-chart-svg"
    >
      <!-- Eixo X (R$) com linhas de grade -->
      <g data-testid="chart-x-axis">
        <g v-for="(tick, i) in xTicks" :key="i">
          <line
            :x1="tick.x"
            :x2="tick.x"
            :y1="PAD.top"
            :y2="height - PAD.bottom"
            stroke="#E2E8F0"
            stroke-width="1"
          />
          <text
            :x="tick.x"
            :y="height - PAD.bottom + 16"
            :text-anchor="i === xTicks.length - 1 && i > 0 ? 'end' : 'middle'"
            font-size="13"
            fill="#475569"
          >
            {{ axisBRL(tick.value, narrow) }}
          </text>
        </g>
      </g>

      <g v-for="(item, i) in items" :key="item.id" :data-testid="`chart-bars-${item.id}`">
        <text
          :x="PAD.left - 8"
          :y="rowY(i) + BAR + GAP / 2"
          text-anchor="end"
          dominant-baseline="middle"
          font-size="13"
          fill="#334155"
        >
          {{ shortName(item.name) }}
        </text>
        <rect
          v-for="(series, s) in SERIES"
          :key="series.key"
          :x="PAD.left"
          :y="rowY(i) + s * (BAR + GAP)"
          :width="widthOf(item[series.key])"
          :height="BAR"
          :fill="series.color"
          :data-testid="`chart-bar-${series.key}`"
        />
      </g>
    </svg>

    <figcaption>
      <ul class="flex flex-wrap gap-x-4 gap-y-1 text-xs text-gray-700" data-testid="chart-legend">
        <li v-for="series in SERIES" :key="series.key" class="inline-flex items-center gap-1.5">
          <span
            class="inline-block size-3 rounded-sm"
            :style="{ backgroundColor: series.color }"
            aria-hidden="true"
          />
          {{ series.label }}
        </li>
      </ul>
    </figcaption>
  </figure>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { axisBRL, useChartWidth, xTickCount } from '@/lib/chart'

/**
 * Story 6.9: alocado × gasto por categoria em barras horizontais SVG feitas à mão (padrão do AllocationHistoryChart).
 * Horizontais para caber o nome da categoria. A tabela da tela é a alternativa acessível.
 */
const props = defineProps<{
  items: { id: string; name: string; allocated: number; spent: number }[]
}>()

const PAD = { top: 8, right: 24, bottom: 28, left: 128 }
const BAR = 10
const GAP = 2
const ROW = 2 * BAR + GAP + 12
const X_TICKS = 4
const MAX_NAME = 18

const svg = ref<SVGElement | null>(null)
const { width: WIDTH, narrow } = useChartWidth(svg, 640)

const SERIES = [
  { key: 'allocated', label: 'Alocado', color: '#93B1F5' },
  { key: 'spent', label: 'Gasto', color: '#1E40AF' },
] as const

const height = computed(() => PAD.top + props.items.length * ROW + PAD.bottom)

/** De 0 até o maior valor; tudo zero ganha uma faixa mínima. */
const xMax = computed(() => Math.max(1, ...props.items.flatMap((c) => [c.allocated, c.spent])))

function widthOf(value: number): number {
  return (Math.max(value, 0) / xMax.value) * (WIDTH.value - PAD.left - PAD.right)
}

function rowY(i: number): number {
  return PAD.top + i * ROW + 6
}

function shortName(name: string): string {
  const chars = Array.from(name) // por code point, para não partir emoji
  // Estreito: nome mais curto, para sobrar barra; o nome inteiro está na tabela
  const max = narrow.value ? 14 : MAX_NAME
  return chars.length > max ? `${chars.slice(0, max - 1).join('')}…` : name
}

const xTicks = computed(() => {
  const count = xTickCount(WIDTH.value - PAD.left - PAD.right, narrow.value ? 10 : 12, X_TICKS)
  return Array.from({ length: count + 1 }, (_, i) => {
    const value = Math.round(((xMax.value * i) / count) * 100) / 100
    return { value, x: PAD.left + widthOf(value) }
  })
})
</script>
