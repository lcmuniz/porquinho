<template>
  <p v-if="error" role="alert" data-testid="tactical-error" class="mt-4 text-sm text-gray-700">
    Não foi possível carregar a visão tática
  </p>
  <div
    v-else-if="tactical"
    data-testid="tactical-grid"
    class="mt-4 grid grid-cols-1 gap-4 xl:grid-cols-2"
  >
    <div data-testid="card-donut" class="min-w-0 rounded-lg border border-gray-200 bg-white p-6">
      <h3 class="mb-4 text-sm font-normal text-gray-600">Gasto do mês por categoria</h3>
      <SpendingDonut v-if="spending.length" :slices="slices" :total="total" @select="open" />
      <p v-else data-testid="donut-empty" class="text-sm text-gray-700">Nenhum gasto neste mês</p>
    </div>

    <div data-testid="card-top5" class="min-w-0 rounded-lg border border-gray-200 bg-white p-6">
      <h3 class="mb-4 text-sm font-normal text-gray-600">Top 5 categorias</h3>
      <ul v-if="spending.length" class="space-y-3">
        <li v-for="(c, i) in top5" :key="c.categoryId ?? 'uncategorized'">
          <button
            type="button"
            class="w-full min-w-0 rounded text-left hover:bg-gray-50"
            :data-testid="`top5-${c.categoryId ?? 'uncategorized'}`"
            @click="open(c)"
          >
            <span class="flex items-baseline justify-between gap-2 text-sm">
              <span class="min-w-0 break-words text-gray-900">{{ c.name }}</span>
              <span class="shrink-0 text-gray-700">{{ formatBRL(c.spent) }}</span>
            </span>
            <span class="mt-1 block h-2 w-full overflow-hidden rounded-full bg-gray-100">
              <span
                class="block h-full rounded-full"
                :style="{ width: `${(c.spent / top5[0]!.spent) * 100}%`, backgroundColor: CHART_COLORS[i] }"
              />
            </span>
          </button>
        </li>
      </ul>
      <p v-else data-testid="top5-empty" class="text-sm text-gray-700">Nenhum gasto neste mês</p>
    </div>

    <div data-testid="card-planned" class="min-w-0 rounded-lg border border-gray-200 bg-white p-6">
      <h3 class="mb-4 text-sm font-normal text-gray-600">Planejado × real</h3>
      <ul v-if="planned.length" class="space-y-3">
        <li v-for="c in planned" :key="c.categoryId!">
          <button
            type="button"
            class="w-full min-w-0 rounded text-left hover:bg-gray-50"
            :data-testid="`planned-${c.categoryId}`"
            @click="open(c)"
          >
            <span class="flex flex-wrap items-baseline justify-between gap-x-2 text-sm">
              <span class="min-w-0 break-words text-gray-900">{{ c.name }}</span>
              <span
                class="shrink-0"
                :class="c.spent > c.allocated! ? 'font-semibold text-red-600' : 'text-gray-700'"
              >
                {{ formatBRL(c.spent) }} de {{ formatBRL(c.allocated!) }}
              </span>
            </span>
            <span class="mt-1 block h-2 w-full overflow-hidden rounded-full bg-gray-100">
              <span
                class="block h-full rounded-full"
                :class="c.spent > c.allocated! ? 'bg-red-600' : 'bg-green-600'"
                :style="{ width: `${c.allocated! > 0 ? Math.min(c.spent / c.allocated!, 1) * 100 : c.spent > 0 ? 100 : 0}%` }"
                :data-testid="`planned-bar-${c.categoryId}`"
              />
            </span>
          </button>
        </li>
      </ul>
      <p v-else data-testid="planned-empty" class="text-sm text-gray-700">
        Nenhuma categoria com orçamento neste mês
      </p>
    </div>

    <div data-testid="card-trend" class="min-w-0 rounded-lg border border-gray-200 bg-white p-6">
      <h3 class="mb-4 text-sm font-normal text-gray-600">Tendências por categoria</h3>
      <TrendChart :trends="tactical.trends" />
    </div>

    <CategoryDrilldown :category="selected" :month="tactical.month" @close="selected = null" />
  </div>
  <p v-else role="status" data-testid="tactical-loading" class="mt-4 text-sm text-gray-600">
    Carregando…
  </p>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getTactical } from '@/services/dashboardService'
import { formatBRL } from '@/lib/money'
import SpendingDonut from './SpendingDonut.vue'
import TrendChart from './TrendChart.vue'
import CategoryDrilldown from './CategoryDrilldown.vue'
import { CHART_COLORS, OTHERS_COLOR } from './chartColors'
import type { CategorySpending, DashboardTactical, DonutSlice } from '@/types/dashboard'

// Story 7.5: a Camada 2 (donut, top 5, planejado × real, tendências e drill-down) com uma chamada só
const tactical = ref<DashboardTactical | null>(null)
const error = ref(false)
const selected = ref<CategorySpending | null>(null)

/** Já vem do maior para o menor gasto. */
const spending = computed(() => tactical.value!.categories.filter((c) => c.spent > 0))
const top5 = computed(() => spending.value.slice(0, CHART_COLORS.length))
const total = computed(
  () => Math.round(spending.value.reduce((sum, c) => sum + c.spent, 0) * 100) / 100,
)

const slices = computed<DonutSlice[]>(() => {
  const result: DonutSlice[] = top5.value.map((c, i) => ({
    id: c.categoryId ?? 'uncategorized',
    name: c.name,
    value: c.spent,
    color: CHART_COLORS[i]!,
    category: c,
  }))
  const rest = spending.value.slice(CHART_COLORS.length)
  if (rest.length) {
    const value = Math.round(rest.reduce((sum, c) => sum + c.spent, 0) * 100) / 100
    result.push({ id: 'others', name: 'Outras', value, color: OTHERS_COLOR, category: null })
  }
  return result
})

/** Pior diferença (gasto - alocado) primeiro. */
const planned = computed(() =>
  tactical
    .value!.categories.filter((c) => c.allocated !== null)
    .sort((a, b) => b.spent - b.allocated! - (a.spent - a.allocated!)),
)

function open(category: CategorySpending) {
  selected.value = category
}

onMounted(async () => {
  try {
    tactical.value = await getTactical()
  } catch {
    error.value = true
  }
})
</script>
