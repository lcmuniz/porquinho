<template>
  <figure class="space-y-3" data-testid="balance-chart">
    <div class="relative">
      <svg
        :viewBox="`0 0 ${WIDTH} ${HEIGHT}`"
        class="h-auto w-full overflow-visible"
        role="group"
        aria-label="Gráfico do saldo ao longo do tempo"
        data-testid="balance-chart-svg"
        @mouseleave="active = null"
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
        <line
          v-if="zeroY !== null"
          :x1="PAD.left"
          :x2="WIDTH - PAD.right"
          :y1="zeroY"
          :y2="zeroY"
          stroke="#9ca3af"
          stroke-width="1"
          stroke-dasharray="4 3"
          data-testid="chart-zero-line"
        />

        <!-- Eixo X (datas) -->
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
            v-for="(tick, i) in xTicks"
            :key="`x-${i}`"
            :x="tick.x"
            :y="HEIGHT - PAD.bottom + 18"
            :text-anchor="anchorOf(i)"
            font-size="11"
            fill="#6b7280"
          >
            {{ tick.label }}
          </text>
        </g>

        <!-- Degraus: o saldo fica constante entre os eventos e segue até hoje -->
        <path
          v-if="plotted.length"
          :d="stepPath"
          fill="none"
          stroke="#7c3aed"
          stroke-width="2"
          stroke-linejoin="round"
          data-testid="chart-line"
        />

        <!-- Um marcador por evento. Tabindex móvel: o Tab entra no gráfico por um ponto só e as setas
             (e Home/End) movem entre eles; o foco mostra o mesmo detalhe do mouse -->
        <path
          v-for="(p, i) in plotted"
          :key="i"
          :d="markerPath(markerOf(p.event).shape, p.x, p.y, active === i ? 6 : 4.5)"
          :fill="markerOf(p.event).color"
          stroke="#ffffff"
          stroke-width="1.5"
          :tabindex="i === focusIndex ? 0 : -1"
          role="img"
          class="cursor-pointer outline-none focus-visible:stroke-gray-900"
          :aria-label="p.description"
          :data-testid="`chart-point-${i}`"
          @mouseenter="active = i"
          @focus="onFocus(i)"
          @blur="onBlur(i)"
          @keydown="onKeydown($event, i)"
        />
      </svg>

      <div
        v-if="activePoint"
        class="pointer-events-none absolute z-10 -translate-x-1/2 -translate-y-full rounded-md border border-gray-200 bg-white px-3 py-2 text-xs whitespace-nowrap shadow-md"
        :style="{
          left: `${(activePoint.x / WIDTH) * 100}%`,
          top: `calc(${(activePoint.y / HEIGHT) * 100}% - 10px)`,
        }"
        data-testid="chart-tooltip"
        aria-hidden="true"
      >
        <p class="text-gray-600">{{ activePoint.dateLabel }}</p>
        <p
          class="font-semibold"
          :class="activePoint.balance < 0 ? 'text-red-600' : 'text-gray-900'"
        >
          {{ formatBRL(activePoint.balance) }}
        </p>
        <p class="text-gray-600">{{ eventLabel(activePoint.event) }}</p>
      </div>
    </div>

    <figcaption>
      <ul class="flex flex-wrap gap-x-4 gap-y-1 text-xs text-gray-700" data-testid="chart-legend">
        <li v-for="event in legendEvents" :key="event" class="inline-flex items-center gap-1.5">
          <svg viewBox="0 0 12 12" class="h-3 w-3" aria-hidden="true">
            <path
              :d="markerPath(BALANCE_EVENT_MARKER[event].shape, 6, 6, 4)"
              :fill="BALANCE_EVENT_MARKER[event].color"
            />
          </svg>
          {{ BALANCE_EVENT_LABEL[event] }}
        </li>
      </ul>
    </figcaption>
  </figure>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  BALANCE_EVENTS,
  BALANCE_EVENT_LABEL,
  BALANCE_EVENT_MARKER,
  eventLabel,
  markerOf,
  markerPath,
} from '@/lib/balanceEvents'
import { LINE_CHART, balanceDomain, movePointFocus, valueTicks, yOf as chartY } from '@/lib/chart'
import { formatDateBR } from '@/lib/date'
import { formatBRL } from '@/lib/money'
import type { BalancePoint } from '@/types/account'

/**
 * Story 4.7: gráfico do saldo em degraus, em SVG feito à mão (sem biblioteca, Decisão A). O eixo X vai do início do
 * período (ou do primeiro ponto, em "Tudo") até agora; o ponto anterior ao período fica preso ao início.
 */
const props = defineProps<{
  points: BalancePoint[]
  /** Início do período (ISO); null em "Tudo". */
  periodStart?: string | null
  /** Agora (ISO); padrão é o relógio do navegador. */
  now?: string
}>()

const { WIDTH, HEIGHT, PAD } = LINE_CHART
const Y_TICKS = 4
const X_TICKS = 4

const active = ref<number | null>(null)
/** Ponto com tabindex 0; começa no mais recente. */
const focusIndex = ref(Math.max(props.points.length - 1, 0))
watch(
  () => props.points,
  (points) => {
    active.value = null
    focusIndex.value = Math.max(points.length - 1, 0)
  },
)

function minMax(values: number[]): [number, number] {
  let lo = Infinity
  let hi = -Infinity
  for (const v of values) {
    if (v < lo) lo = v
    if (v > hi) hi = v
  }
  return values.length ? [lo, hi] : [0, 0]
}

const times = computed(() => props.points.map((p) => new Date(p.date).getTime()))

const xDomain = computed<[number, number]>(() => {
  const [first, last] = minMax(times.value)
  const start = props.periodStart ? new Date(props.periodStart).getTime() : first
  const now = props.now ? new Date(props.now).getTime() : Date.now()
  return [start, Math.max(now, last, start)]
})

const yDomain = computed(() => balanceDomain(props.points.map((p) => p.balance)))

function xOf(time: number): number {
  const [min, max] = xDomain.value
  const inner = WIDTH - PAD.left - PAD.right
  if (max === min) return PAD.left + inner / 2
  return PAD.left + ((Math.max(time, min) - min) / (max - min)) * inner
}

function yOf(value: number): number {
  return chartY(value, yDomain.value, HEIGHT, PAD)
}

interface PlottedPoint {
  x: number
  y: number
  balance: number
  event: string
  dateLabel: string
  description: string
}

const plotted = computed<PlottedPoint[]>(() =>
  props.points.map((p, i) => {
    const dateLabel = formatDateBR(p.date)
    return {
      x: xOf(times.value[i]!),
      y: yOf(p.balance),
      balance: p.balance,
      event: p.event,
      dateLabel,
      description: `${dateLabel}, ${formatBRL(p.balance)}, ${eventLabel(p.event)}`,
    }
  }),
)

/** Horizontal até o próximo evento, vertical no evento; o último saldo segue até agora. */
const stepPath = computed(() => {
  const [first, ...rest] = plotted.value
  if (!first) return ''
  const steps = rest.map((p) => `H${p.x}V${p.y}`).join('')
  return `M${first.x},${first.y}${steps}H${xOf(xDomain.value[1])}`
})

const activePoint = computed(() => (active.value === null ? null : plotted.value[active.value]))

const yTicks = computed(() => valueTicks(yDomain.value, Y_TICKS).map((value) => ({ value, y: yOf(value) })))

const zeroY = computed(() => {
  const [lo, hi] = yDomain.value
  return lo < 0 && hi >= 0 ? yOf(0) : null
})

const xTicks = computed(() => {
  const [min, max] = xDomain.value
  if (!props.points.length) return []
  if (min === max) return [{ x: xOf(min), label: formatDateBR(new Date(min).toISOString()) }]
  return Array.from({ length: X_TICKS + 1 }, (_, i) => {
    const time = min + ((max - min) * i) / X_TICKS
    return { x: xOf(time), label: formatDateBR(new Date(time).toISOString()) }
  })
})

/** As datas das pontas ficam dentro da área do gráfico. */
function anchorOf(i: number): 'start' | 'middle' | 'end' {
  if (xTicks.value.length === 1) return 'middle'
  if (i === 0) return 'start'
  return i === xTicks.value.length - 1 ? 'end' : 'middle'
}

function onFocus(i: number) {
  active.value = i
  focusIndex.value = i
}

function onBlur(i: number) {
  if (active.value === i) active.value = null
}

/** Só os tipos presentes no período, na ordem fixa. */
const legendEvents = computed(() =>
  BALANCE_EVENTS.filter((e) => props.points.some((p) => p.event === e)),
)

/** Setas e Home/End movem o tabindex e o foco entre os pontos. */
function onKeydown(event: KeyboardEvent, index: number) {
  const next = movePointFocus(event, index, plotted.value.length - 1, 'chart-point')
  if (next !== null) focusIndex.value = next
}
</script>
