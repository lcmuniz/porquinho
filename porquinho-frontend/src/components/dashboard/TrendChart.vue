<template>
  <div class="space-y-3" data-testid="trend-chart">
    <div role="group" aria-label="Período" class="flex flex-wrap gap-2" data-testid="trend-range-selector">
      <Button
        v-for="option in RANGES"
        :key="option"
        variant="outline"
        class="min-h-[44px]"
        :class="
          months === option
            ? 'border-purple-600 bg-purple-50 text-purple-700 hover:bg-purple-100'
            : 'text-gray-700'
        "
        :aria-pressed="months === option ? 'true' : 'false'"
        :data-testid="`trend-range-${option}`"
        @click="months = option"
      >
        {{ option }} meses
      </Button>
    </div>

    <p v-if="!lines.length" class="py-12 text-center text-sm text-gray-700" data-testid="trend-empty">
      Nenhum gasto nos últimos {{ months }} meses
    </p>
    <template v-else>
      <svg
        :viewBox="`0 0 ${WIDTH} ${HEIGHT}`"
        class="h-auto w-full overflow-visible"
        role="img"
        :aria-label="`Gasto por mês das ${lines.length} maiores categorias nos últimos ${months} meses. Os valores de cada mês estão na tabela abaixo.`"
        data-testid="trend-chart-svg"
      >
        <g v-for="(tick, i) in yTicks" :key="`y-${i}`">
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
        <text
          v-for="(label, i) in monthLabels"
          :key="`x-${i}`"
          :x="xOf(i)"
          :y="HEIGHT - PAD.bottom + 18"
          text-anchor="middle"
          font-size="11"
          fill="#6b7280"
        >
          {{ label }}
        </text>
        <g v-for="line in lines" :key="line.key" :data-testid="`trend-line-${line.key}`">
          <polyline
            :points="line.values.map((v, i) => `${xOf(i)},${yOf(v)}`).join(' ')"
            fill="none"
            :stroke="line.color"
            stroke-width="2.5"
            stroke-linejoin="round"
            stroke-linecap="round"
          />
          <circle
            v-for="(v, i) in line.values"
            :key="i"
            :cx="xOf(i)"
            :cy="yOf(v)"
            r="3.5"
            :fill="line.color"
          >
            <title>{{ line.name }}, {{ monthLabels[i] }}: {{ formatBRL(v) }}</title>
          </circle>
        </g>
      </svg>

      <ul class="space-y-1 text-sm" data-testid="trend-legend">
        <li v-for="line in lines" :key="line.key" class="flex items-center gap-2">
          <span
            class="h-3 w-3 shrink-0 rounded-sm"
            :style="{ backgroundColor: line.color }"
            aria-hidden="true"
          />
          <span class="min-w-0 flex-1 break-words text-gray-900">{{ line.name }}</span>
          <span class="shrink-0 text-gray-700">{{ formatBRL(line.total) }} em {{ months }} meses</span>
        </li>
      </ul>

      <!-- Os valores dos pontos só aparecem no hover; a tabela os leva ao teclado e ao leitor de tela (retro Epic 7 A3) -->
      <details data-testid="trend-table">
        <summary class="min-h-[44px] cursor-pointer py-2 text-sm font-medium text-purple-700">
          Ver valores por mês
        </summary>
        <div class="overflow-x-auto">
          <table class="w-full text-sm">
            <thead>
              <tr class="text-left text-gray-700">
                <th scope="col" class="py-1 pr-4 font-medium">Categoria</th>
                <th v-for="label in monthLabels" :key="label" scope="col" class="py-1 pr-4 text-right font-medium">
                  {{ label }}
                </th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="line in lines" :key="line.key" class="border-t border-gray-100">
                <th scope="row" class="py-1 pr-4 text-left font-normal text-gray-900">{{ line.name }}</th>
                <td v-for="(v, i) in line.values" :key="i" class="py-1 pr-4 text-right whitespace-nowrap text-gray-700">
                  {{ formatBRL(v) }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </details>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { Button } from '@/components/ui/button'
import { formatBRL } from '@/lib/money'
import type { CategoryTrend } from '@/types/dashboard'
import { CHART_COLORS } from './chartColors'

/**
 * Story 7.5: tendência mensal das 5 maiores categorias (pelo gasto somado no período). A série de 12 meses vem do
 * backend; 3/6/12 meses filtram aqui, sem nova chamada. SVG feito à mão, como o WealthChart.
 */
const props = defineProps<{ trends: CategoryTrend[] }>()

const RANGES = [3, 6, 12] as const
const WIDTH = 640
const HEIGHT = 260
const PAD = { top: 16, right: 24, bottom: 32, left: 96 }
const Y_TICKS = 4
const SHORT_MONTH = new Intl.DateTimeFormat('pt-BR', { month: 'short', year: '2-digit', timeZone: 'UTC' })

const months = ref<(typeof RANGES)[number]>(3)

const period = computed(() => props.trends[0]?.months.slice(-months.value) ?? [])

const monthLabels = computed(() =>
  period.value.map((m) => {
    const [y, mo] = m.month.split('-').map(Number)
    return SHORT_MONTH.format(Date.UTC(y!, mo! - 1, 1))
  }),
)

const lines = computed(() =>
  props.trends
    .map((t) => {
      const values = t.months.slice(-months.value).map((m) => m.spent)
      return {
        key: t.categoryId ?? 'uncategorized',
        name: t.name,
        values,
        total: Math.round(values.reduce((a, b) => a + b, 0) * 100) / 100,
      }
    })
    .filter((l) => l.total > 0)
    .sort((a, b) => b.total - a.total)
    .slice(0, CHART_COLORS.length)
    .map((l, i) => ({ ...l, color: CHART_COLORS[i]! })),
)

const max = computed(() => Math.max(...lines.value.flatMap((l) => l.values), 1))

function xOf(i: number): number {
  const n = period.value.length
  return PAD.left + (n > 1 ? (i / (n - 1)) * (WIDTH - PAD.left - PAD.right) : 0)
}

function yOf(value: number): number {
  return PAD.top + (1 - value / max.value) * (HEIGHT - PAD.top - PAD.bottom)
}

const yTicks = computed(() =>
  Array.from({ length: Y_TICKS + 1 }, (_, i) => {
    const value = Math.round(((max.value * i) / Y_TICKS) * 100) / 100
    return { value, y: yOf(value) }
  }),
)
</script>
