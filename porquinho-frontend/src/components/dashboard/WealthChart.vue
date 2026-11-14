<template>
  <div
    class="min-w-0 space-y-3 rounded-lg border border-gray-200 bg-white p-6"
    data-testid="wealth-chart"
  >
    <div class="flex flex-wrap items-center justify-between gap-2">
      <h3 class="text-sm font-normal text-gray-600">Crescimento do patrimônio</h3>
      <div
        role="group"
        aria-label="Período"
        class="flex flex-wrap gap-2"
        data-testid="wealth-range-selector"
      >
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
          :data-testid="`wealth-range-${option}`"
          @click="months = option"
        >
          {{ option }} meses
        </Button>
      </div>
    </div>

    <p v-if="!trend" class="py-12 text-center text-sm text-gray-700" data-testid="wealth-empty">
      Ainda não há histórico suficiente
    </p>
    <template v-else>
      <p
        class="flex items-center gap-2 text-sm font-semibold"
        :class="trend.text"
        data-testid="wealth-trend"
      >
        <component :is="trend.icon" class="h-5 w-5 shrink-0" aria-hidden="true" />
        <span>{{ trend.label }}</span>
      </p>

      <div class="relative">
        <svg
          :viewBox="`0 0 ${WIDTH} ${HEIGHT}`"
          class="h-auto w-full overflow-visible"
          role="group"
          aria-label="Gráfico do patrimônio ao longo do tempo"
          data-testid="wealth-chart-svg"
          @mouseleave="active = null"
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
          <line
            v-if="zeroY !== null"
            :x1="PAD.left"
            :x2="WIDTH - PAD.right"
            :y1="zeroY"
            :y2="zeroY"
            stroke="#9ca3af"
            stroke-width="1"
            stroke-dasharray="4 3"
            data-testid="wealth-zero-line"
          />
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
            :text-anchor="i === 0 ? 'start' : i === xTicks.length - 1 ? 'end' : 'middle'"
            font-size="11"
            fill="#6b7280"
          >
            {{ tick.label }}
          </text>

          <!-- Desenha-se uma vez: a classe sai no fim da animação, então trocar o período não anima de novo -->
          <path
            :d="curve"
            fill="none"
            stroke="#9333EA"
            stroke-width="2.5"
            stroke-linejoin="round"
            stroke-linecap="round"
            pathLength="1"
            :class="{ 'wealth-draw': drawing }"
            data-testid="wealth-line"
            @animationend="drawing = false"
          />

          <!-- Tabindex móvel: o Tab entra por um ponto; setas e Home/End movem entre eles (como na 4.7).
               Só o ponto ativo aparece; os outros ficam transparentes, mas recebem mouse e foco -->
          <circle
            v-for="(p, i) in plotted"
            :key="i"
            :cx="p.x"
            :cy="p.y"
            r="5"
            :fill="active === i ? '#9333EA' : 'transparent'"
            :stroke="active === i ? '#ffffff' : 'transparent'"
            stroke-width="1.5"
            :tabindex="i === focusIndex ? 0 : -1"
            role="img"
            class="cursor-pointer outline-none focus-visible:stroke-gray-900"
            :aria-label="`${p.dateLabel}, ${formatBRL(p.balance)}`"
            :data-testid="`wealth-point-${i}`"
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
          data-testid="wealth-tooltip"
          aria-hidden="true"
        >
          <p class="text-gray-600">{{ activePoint.dateLabel }}</p>
          <p
            class="font-semibold"
            :class="activePoint.balance < 0 ? 'text-red-600' : 'text-gray-900'"
          >
            {{ formatBRL(activePoint.balance) }}
          </p>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ArrowDown, ArrowUp, Minus } from 'lucide-vue-next'
import { Button } from '@/components/ui/button'
import { formatLocalDateBR } from '@/lib/date'
import { formatBRL, formatSignedBRL } from '@/lib/money'
import type { WealthPoint } from '@/types/dashboard'

/**
 * Story 7.4: curva do patrimônio em SVG feito à mão (padrão do BalanceChart da 4.7). A série de 12 meses vem do
 * backend; 3/6/12 meses filtram aqui. Cada ponto é o patrimônio no fim do dia; o último é hoje.
 */
const props = defineProps<{ points: WealthPoint[] }>()

const RANGES = [3, 6, 12] as const
const WIDTH = 640
const HEIGHT = 280
const PAD = { top: 16, right: 16, bottom: 32, left: 104 }
const Y_TICKS = 4
const X_TICKS = 4
const DAY = 86_400_000

const months = ref<(typeof RANGES)[number]>(3)
const active = ref<number | null>(null)
const drawing = ref(true)

const toTime = (date: string) => Date.parse(`${date}T00:00:00Z`)
const toDate = (time: number) => new Date(time).toISOString().slice(0, 10)

/** "AAAA-MM-DD" menos n meses, com o dia preso ao fim do mês (como o minusMonths do backend). */
function minusMonths(date: string, n: number): string {
  const [y, m, d] = date.split('-').map(Number)
  const lastDay = new Date(Date.UTC(y!, m! - 1 - n + 1, 0)).getUTCDate()
  return toDate(Date.UTC(y!, m! - 1 - n, Math.min(d!, lastDay)))
}

/** O período começa no fim do dia de início: vale o último ponto até esse dia. */
const series = computed<WealthPoint[]>(() => {
  const all = props.points
  if (all.length < 2) return []
  const start = minusMonths(all[all.length - 1]!.date, months.value)
  if (start <= all[0]!.date) return all
  const before = all.filter((p) => p.date <= start)
  return [
    { date: start, balance: before[before.length - 1]!.balance },
    ...all.filter((p) => p.date > start),
  ]
})

/** Ponto com tabindex 0; começa no mais recente. */
const focusIndex = ref(Math.max(series.value.length - 1, 0))
watch(series, (s) => {
  active.value = null
  focusIndex.value = Math.max(s.length - 1, 0)
})

/** Sobe/desce; estável quando a diferença fica abaixo de 1% do valor inicial (ou os dois são zero). */
const trend = computed(() => {
  const s = series.value
  if (s.length < 2) return null
  const first = s[0]!.balance
  const diff = s[s.length - 1]!.balance - first
  const label = `${formatSignedBRL(Math.round(diff * 100) / 100)} em ${months.value} meses`
  // Texto text-sm precisa de 4,5:1 (WCAG 1.4.3): amarelo e verde 600 não chegam, o 700 sim (retro Epic 7 A3)
  if (diff === 0 || Math.abs(diff) < Math.abs(first) * 0.01)
    return { icon: Minus, text: 'text-yellow-700', label }
  return diff > 0
    ? { icon: ArrowUp, text: 'text-green-700', label }
    : { icon: ArrowDown, text: 'text-red-600', label }
})

const xDomain = computed<[number, number]>(() => {
  const s = series.value
  return s.length ? [toTime(s[0]!.date), toTime(s[s.length - 1]!.date)] : [0, DAY]
})

/** Com saldo negativo, o zero entra para a linha aparecer abaixo dele. */
const yDomain = computed<[number, number]>(() => {
  const values = series.value.map((p) => p.balance)
  let lo = Math.min(...values, Infinity)
  let hi = Math.max(...values, -Infinity)
  if (!values.length) [lo, hi] = [0, 0]
  if (lo < 0) hi = Math.max(hi, 0)
  if (lo === hi) {
    const pad = Math.max(Math.abs(lo) * 0.1, 1)
    lo -= pad
    hi += pad
  }
  return [lo, hi]
})

function xOf(time: number): number {
  const [min, max] = xDomain.value
  return PAD.left + ((time - min) / (max - min || 1)) * (WIDTH - PAD.left - PAD.right)
}

function yOf(value: number): number {
  const [lo, hi] = yDomain.value
  return PAD.top + ((hi - value) / (hi - lo)) * (HEIGHT - PAD.top - PAD.bottom)
}

const plotted = computed(() =>
  series.value.map((p) => ({
    x: xOf(toTime(p.date)),
    y: yOf(p.balance),
    balance: p.balance,
    dateLabel: formatLocalDateBR(p.date),
  })),
)

/**
 * Cúbica monotônica (Fritsch–Carlson, como a curveMonotoneX do d3): a curva nunca passa acima nem abaixo dos pontos
 * vizinhos.
 */
const curve = computed(() => {
  const p = plotted.value
  if (!p.length) return ''
  const n = p.length
  const secant = p.slice(1).map((q, i) => (q.y - p[i]!.y) / (q.x - p[i]!.x))
  const tangent = p.map((_, i) => {
    if (i === 0) return secant[0] ?? 0
    if (i === n - 1) return secant[n - 2]!
    const s0 = secant[i - 1]!
    const s1 = secant[i]!
    if (s0 * s1 <= 0) return 0
    const h0 = p[i]!.x - p[i - 1]!.x
    const h1 = p[i + 1]!.x - p[i]!.x
    const blend = (s0 * h1 + s1 * h0) / (h0 + h1)
    return Math.sign(s0) * Math.min(Math.abs(s0), Math.abs(s1), 0.5 * Math.abs(blend)) * 2
  })
  let d = `M${p[0]!.x},${p[0]!.y}`
  for (let i = 0; i < n - 1; i++) {
    const a = p[i]!
    const b = p[i + 1]!
    const h = (b.x - a.x) / 3
    d += `C${a.x + h},${a.y + tangent[i]! * h} ${b.x - h},${b.y - tangent[i + 1]! * h} ${b.x},${b.y}`
  }
  return d
})

const activePoint = computed(() => (active.value === null ? null : plotted.value[active.value]))

/** Perto das bordas, o tooltip abre para dentro do card em vez de centralizar no ponto. */
const tooltipAlign = computed(() => {
  const ratio = (activePoint.value?.x ?? 0) / WIDTH
  if (ratio < 0.2) return 'translate-x-0'
  return ratio > 0.8 ? '-translate-x-full' : '-translate-x-1/2'
})

const yTicks = computed(() => {
  const [lo, hi] = yDomain.value
  const values = new Set<number>()
  for (let i = 0; i <= Y_TICKS; i++)
    values.add(Math.round((lo + ((hi - lo) * i) / Y_TICKS) * 100) / 100)
  return [...values].map((value) => ({ value, y: yOf(value) }))
})

const zeroY = computed(() => (yDomain.value[0] < 0 ? yOf(0) : null))

const xTicks = computed(() => {
  const [min, max] = xDomain.value
  return Array.from({ length: X_TICKS + 1 }, (_, i) => {
    const time = Math.round((min + ((max - min) * i) / X_TICKS) / DAY) * DAY
    return { x: xOf(time), label: formatLocalDateBR(toDate(time)) }
  })
})

function onFocus(i: number) {
  active.value = i
  focusIndex.value = i
}

function onKeydown(event: KeyboardEvent, index: number) {
  const last = plotted.value.length - 1
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
  svg?.querySelector<SVGElement>(`[data-testid="wealth-point-${next}"]`)?.focus()
}
</script>

<style scoped>
.wealth-draw {
  stroke-dasharray: 1;
  animation: wealth-draw 1.2s ease-out both;
}

@keyframes wealth-draw {
  from {
    stroke-dashoffset: 1;
  }
  to {
    stroke-dashoffset: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .wealth-draw {
    stroke-dasharray: none;
    animation: none;
  }
}
</style>
