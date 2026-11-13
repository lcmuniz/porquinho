<template>
  <div class="min-w-0 space-y-2" data-testid="goal-chart">
    <p class="flex flex-wrap gap-4 text-xs text-gray-600">
      <span class="flex items-center gap-1">
        <span class="inline-block h-0.5 w-4 bg-[#10B981]" aria-hidden="true" />
        Acumulado
      </span>
      <span v-if="projected.length > 1" class="flex items-center gap-1">
        <span
          class="inline-block w-4 border-t-2 border-dashed border-[#10B981]"
          aria-hidden="true"
        />
        Projeção com o aporte atual
      </span>
    </p>
    <div class="relative">
      <svg
        :viewBox="`0 0 ${WIDTH} ${HEIGHT}`"
        class="h-auto w-full overflow-visible"
        role="group"
        :aria-label="`Evolução de ${goal.name}`"
        data-testid="goal-chart-svg"
        @mouseleave="active = null"
      >
        <!-- Marcos de 25/50/75/100% do alvo -->
        <g
          v-for="m in milestones"
          :key="m.percent"
          :data-testid="`goal-chart-milestone-${m.percent}`"
        >
          <line
            :x1="PAD.left"
            :x2="WIDTH - PAD.right"
            :y1="m.y"
            :y2="m.y"
            stroke="#d1d5db"
            stroke-width="1"
            stroke-dasharray="4 3"
          />
          <text
            :x="PAD.left - 8"
            :y="m.y"
            text-anchor="end"
            dominant-baseline="middle"
            font-size="11"
            fill="#6b7280"
          >
            {{ formatBRL(m.value) }}
          </text>
          <text
            :x="WIDTH - PAD.right + 6"
            :y="m.y"
            dominant-baseline="middle"
            font-size="11"
            fill="#6b7280"
          >
            {{ m.percent }}%
          </text>
        </g>
        <line
          :x1="PAD.left"
          :x2="WIDTH - PAD.right"
          :y1="yOf(0)"
          :y2="yOf(0)"
          stroke="#9ca3af"
          stroke-width="1"
        />
        <text
          :x="PAD.left - 8"
          :y="yOf(0)"
          text-anchor="end"
          dominant-baseline="middle"
          font-size="11"
          fill="#6b7280"
        >
          {{ formatBRL(0) }}
        </text>
        <text
          v-for="(tick, i) in xTicks"
          :key="`x-${i}`"
          :x="xOf(tick)"
          :y="HEIGHT - PAD.bottom + 18"
          :text-anchor="
            xTicks.length > 1 && i === 0
              ? 'start'
              : i === xTicks.length - 1 && i > 0
                ? 'end'
                : 'middle'
          "
          font-size="11"
          fill="#6b7280"
        >
          {{ shortMonth(shiftMonth(start, tick)) }}
        </text>

        <polyline
          v-if="projected.length > 1"
          :points="projected.map((p) => `${xOf(p.index)},${yOf(p.amount)}`).join(' ')"
          fill="none"
          stroke="#10B981"
          stroke-width="2"
          stroke-dasharray="6 4"
          :data-months="projected.length - 1"
          data-testid="goal-chart-projected"
        />
        <polyline
          :points="real.map((p) => `${p.x},${p.y}`).join(' ')"
          fill="none"
          stroke="#10B981"
          stroke-width="2.5"
          stroke-linejoin="round"
          stroke-linecap="round"
          data-testid="goal-chart-real"
        />

        <!-- Tabindex móvel, como no WealthChart: Tab entra por um ponto; setas e Home/End movem entre eles -->
        <circle
          v-for="(p, i) in real"
          :key="p.month"
          :cx="p.x"
          :cy="p.y"
          r="5"
          :fill="active === i ? '#10B981' : 'transparent'"
          :stroke="active === i ? '#ffffff' : 'transparent'"
          stroke-width="1.5"
          :tabindex="i === focusIndex ? 0 : -1"
          role="img"
          class="cursor-pointer outline-none focus-visible:stroke-gray-900"
          :aria-label="`${p.label}, ${formatBRL(p.amount)}`"
          :data-testid="`goal-chart-point-${i}`"
          @mouseenter="active = i"
          @focus="onFocus(i)"
          @blur="active === i && (active = null)"
          @keydown="onKeydown($event, i)"
        />
      </svg>

      <div
        v-if="activePoint"
        class="pointer-events-none absolute z-10 -translate-y-full rounded-md border border-gray-200 bg-white px-3 py-2 text-xs whitespace-nowrap shadow-md"
        :class="tooltipAlign"
        :style="{
          left: `${(activePoint.x / WIDTH) * 100}%`,
          top: `calc(${(activePoint.y / HEIGHT) * 100}% - 10px)`,
        }"
        data-testid="goal-chart-tooltip"
        aria-hidden="true"
      >
        <p class="text-gray-600">{{ activePoint.label }}</p>
        <p class="font-semibold text-gray-900">{{ formatBRL(activePoint.amount) }}</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { formatMonthBR, shiftMonth } from '@/lib/date'
import { monthsBetween } from '@/lib/goalPace'
import { formatBRL } from '@/lib/money'
import type { Goal, GoalProgressPoint } from '@/types/goal'

/**
 * Story 8.3: evolução da meta em SVG feito à mão (molde do WealthChart). Linha real = série do backend (o último ponto
 * é o mês corrente); linha projetada = soma da alocação mensal a partir do mês corrente até o alvo.
 */
const props = defineProps<{ goal: Goal; points: GoalProgressPoint[] }>()

const WIDTH = 640
const HEIGHT = 240
const PAD = { top: 16, right: 40, bottom: 32, left: 104 }
const MAX_MONTHS = 120
const X_TICKS = 4
const SHORT_MONTH = new Intl.DateTimeFormat('pt-BR', {
  month: 'short',
  year: '2-digit',
  timeZone: 'UTC',
})

const active = ref<number | null>(null)

const start = computed(() => props.points[0]?.month ?? '')
const currentIndex = computed(() => Math.max(props.points.length - 1, 0))

/** Do mês corrente em diante (índice 0 = mês de criação), limitada ao alvo. Vazia sem alocação ou com o alvo atingido. */
const projection = computed(() => {
  const target = props.goal.targetAmount
  const allocation = props.goal.monthlyAllocation ?? 0
  // Parte da meta, não do último ponto: enquanto a série recarrega, a projeção já bate com a barra
  let amount = props.goal.currentAmount
  if (!(allocation > 0) || amount >= target) return []
  const result = [{ index: currentIndex.value, amount }]
  while (amount < target && result.length <= MAX_MONTHS) {
    amount = Math.min(target, Math.round((amount + allocation) * 100) / 100)
    result.push({ index: currentIndex.value + result.length, amount })
  }
  return result
})

/** Último índice do eixo: data-alvo, senão fim da projeção, senão mês corrente; no máximo 120 meses no eixo. */
const lastIndex = computed(() => {
  const end = props.goal.targetDate
    ? monthsBetween(start.value, props.goal.targetDate.slice(0, 7))
    : (projection.value[projection.value.length - 1]?.index ?? 0)
  return Math.max(currentIndex.value, Math.min(end, MAX_MONTHS - 1))
})

const projected = computed(() => projection.value.filter((p) => p.index <= lastIndex.value))

const yMax = computed(() =>
  Math.max(props.goal.targetAmount, ...props.points.map((p) => p.amount), 1),
)
const yMin = computed(() => Math.min(0, ...props.points.map((p) => p.amount)))

function xOf(index: number): number {
  return PAD.left + (index / (lastIndex.value || 1)) * (WIDTH - PAD.left - PAD.right)
}

function yOf(value: number): number {
  return (
    PAD.top + ((yMax.value - value) / (yMax.value - yMin.value)) * (HEIGHT - PAD.top - PAD.bottom)
  )
}

const milestones = computed(() =>
  [25, 50, 75, 100].map((percent) => {
    const value = (props.goal.targetAmount * percent) / 100
    return { percent, value, y: yOf(value) }
  }),
)

const real = computed(() =>
  props.points.map((p, i) => ({
    ...p,
    x: xOf(i),
    y: yOf(p.amount),
    label: formatMonthBR(p.month),
  })),
)

function shortMonth(month: string): string {
  const [y, m] = month.split('-').map(Number)
  return SHORT_MONTH.format(Date.UTC(y!, m! - 1, 1))
}

const xTicks = computed(() => {
  const ticks = new Set<number>()
  for (let i = 0; i <= X_TICKS; i++) ticks.add(Math.round((lastIndex.value * i) / X_TICKS))
  return [...ticks]
})

/** Ponto com tabindex 0; começa no mês corrente. */
const focusIndex = ref(currentIndex.value)
watch(currentIndex, (i) => {
  active.value = null
  focusIndex.value = i
})

const activePoint = computed(() => (active.value === null ? null : real.value[active.value]))

const tooltipAlign = computed(() => {
  const ratio = (activePoint.value?.x ?? 0) / WIDTH
  if (ratio < 0.2) return 'translate-x-0'
  return ratio > 0.8 ? '-translate-x-full' : '-translate-x-1/2'
})

function onFocus(i: number) {
  active.value = i
  focusIndex.value = i
}

function onKeydown(event: KeyboardEvent, index: number) {
  const last = real.value.length - 1
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
  if (next === null) return
  event.preventDefault()
  focusIndex.value = next
  const svg = (event.currentTarget as SVGElement).ownerSVGElement
  svg?.querySelector<SVGElement>(`[data-testid="goal-chart-point-${next}"]`)?.focus()
}
</script>
