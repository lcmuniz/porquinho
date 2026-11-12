<template>
  <div class="flex flex-col items-center gap-4 sm:flex-row" data-testid="spending-donut">
    <svg
      :viewBox="`0 0 ${SIZE} ${SIZE}`"
      class="h-48 w-48 shrink-0"
      role="group"
      :aria-label="`Gasto do mês por categoria, total ${formatBRL(total)}`"
      data-testid="spending-donut-svg"
    >
      <!-- Cada fatia é um traço do mesmo círculo, deslocado pelo que veio antes; começa no topo (-90°) -->
      <circle
        v-for="arc in arcs"
        :key="arc.slice.id"
        :cx="SIZE / 2"
        :cy="SIZE / 2"
        :r="RADIUS"
        fill="none"
        :stroke="arc.slice.color"
        :stroke-width="STROKE"
        :stroke-dasharray="`${arc.length} ${CIRCUMFERENCE - arc.length}`"
        :stroke-dashoffset="-arc.offset"
        :transform="`rotate(-90 ${SIZE / 2} ${SIZE / 2})`"
        :role="arc.slice.category ? 'button' : 'img'"
        :tabindex="arc.slice.category ? 0 : undefined"
        :aria-label="`${arc.slice.name}: ${formatBRL(arc.slice.value)}`"
        :class="arc.slice.category ? 'cursor-pointer outline-none focus-visible:[stroke-width:44px]' : ''"
        :data-testid="`donut-slice-${arc.slice.id}`"
        @click="pick(arc.slice)"
        @keydown.enter.prevent="pick(arc.slice)"
        @keydown.space.prevent="pick(arc.slice)"
      />
      <text
        :x="SIZE / 2"
        :y="SIZE / 2"
        text-anchor="middle"
        dominant-baseline="middle"
        font-size="14"
        font-weight="700"
        fill="#111827"
        aria-hidden="true"
      >
        {{ formatBRL(total) }}
      </text>
    </svg>

    <ul class="w-full min-w-0 space-y-1 text-sm" data-testid="spending-donut-legend">
      <li v-for="slice in slices" :key="slice.id" class="flex items-center gap-2">
        <span
          class="h-3 w-3 shrink-0 rounded-sm"
          :style="{ backgroundColor: slice.color }"
          aria-hidden="true"
        />
        <span class="min-w-0 flex-1 break-words text-gray-900">{{ slice.name }}</span>
        <span class="shrink-0 text-gray-700">{{ formatBRL(slice.value) }}</span>
      </li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { formatBRL } from '@/lib/money'
import type { CategorySpending, DonutSlice } from '@/types/dashboard'

const props = defineProps<{ slices: DonutSlice[]; total: number }>()
const emit = defineEmits<{ select: [category: CategorySpending] }>()

const SIZE = 200
const STROKE = 32
const RADIUS = (SIZE - STROKE) / 2
const CIRCUMFERENCE = 2 * Math.PI * RADIUS

const arcs = computed(() => {
  let offset = 0
  return props.slices.map((slice) => {
    const length = props.total > 0 ? (slice.value / props.total) * CIRCUMFERENCE : 0
    const arc = { slice, length, offset }
    offset += length
    return arc
  })
})

function pick(slice: DonutSlice) {
  if (slice.category) emit('select', slice.category)
}
</script>
