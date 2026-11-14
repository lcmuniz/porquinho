<template>
  <p v-if="error" role="alert" data-testid="overview-error" class="mt-4 text-sm text-gray-700">
    Não foi possível carregar a visão geral
  </p>
  <div v-else-if="overview" class="mt-4 space-y-4">
    <!-- Story 7.4: no desktop, a curva em 2/3 e o card (1) ao lado; no mobile, um embaixo do outro -->
    <div data-testid="wealth-row" class="grid grid-cols-1 gap-4 lg:grid-cols-3">
      <WealthChart class="lg:col-span-2" :points="overview.wealthHistory" />
      <div
        data-testid="card-balance"
        class="min-w-0 rounded-lg border border-gray-200 bg-white p-6"
      >
        <p class="text-sm text-gray-600">Saldo total disponível</p>
        <p class="break-words text-4xl font-bold text-gray-900">
          {{ formatBRL(overview.totalBalance) }}
        </p>
      </div>
    </div>

    <div data-testid="metrics-grid" class="grid grid-cols-1 gap-4 md:grid-cols-2">
      <div
        data-testid="card-categories"
        class="min-w-0 rounded-lg border border-gray-200 bg-white p-6"
      >
        <p class="text-sm text-gray-600">Status das categorias</p>
        <template v-if="overview.categories.length">
          <p class="break-words text-4xl font-bold text-gray-900">
            {{ healthyCount }} de {{ overview.categories.length }}
          </p>
          <p class="text-sm text-gray-600">{{ healthyCount === 1 ? 'saudável' : 'saudáveis' }}</p>
          <p v-if="overspentCount" class="mt-3 text-sm font-semibold text-red-600">
            Preciso ajustar?
          </p>
          <ul class="mt-2 space-y-1">
            <li
              v-for="c in overview.categories"
              :key="c.categoryId"
              :data-testid="`category-${c.categoryId}`"
              class="flex items-start gap-2 rounded px-2 py-1 text-sm text-gray-900"
              :class="{ 'bg-red-50 font-semibold': c.status === 'OVERSPENT' }"
            >
              <component
                :is="STATUS[c.status].icon"
                class="mt-0.5 h-4 w-4 shrink-0"
                :class="STATUS[c.status].text"
                aria-hidden="true"
              />
              <span class="min-w-0 break-words"
                >{{ c.name }}: {{ STATUS[c.status].label(c.remaining) }}</span
              >
            </li>
          </ul>
        </template>
        <p v-else data-testid="categories-empty" class="mt-2 text-sm text-gray-700">
          Nenhuma categoria com orçamento neste mês.
          <RouterLink to="/orcamento" class="font-medium text-purple-700 underline"
            >Definir orçamento</RouterLink
          >
        </p>
      </div>

      <div data-testid="card-goal" class="min-w-0 rounded-lg border border-gray-200 bg-white p-6">
        <p class="text-sm text-gray-600">Meta principal</p>
        <template v-if="overview.mainGoal">
          <p class="break-words text-4xl font-bold text-gray-900">
            {{ overview.mainGoal.percent }}%
          </p>
          <p class="break-words text-sm text-gray-900">{{ overview.mainGoal.name }}</p>
          <div
            role="progressbar"
            aria-valuemin="0"
            aria-valuemax="100"
            :aria-valuenow="overview.mainGoal.percent"
            :aria-label="`Progresso da meta ${overview.mainGoal.name}`"
            class="mt-2 h-2 w-full overflow-hidden rounded-full bg-gray-200"
          >
            <div class="h-full bg-purple-600" :style="{ width: `${overview.mainGoal.percent}%` }" />
          </div>
          <p class="mt-1 text-sm text-gray-600">
            {{ formatBRL(overview.mainGoal.currentAmount) }} de
            {{ formatBRL(overview.mainGoal.targetAmount) }}
          </p>
        </template>
        <p v-else data-testid="goal-empty" class="mt-2 text-sm text-gray-700">
          Nenhuma meta criada ainda
        </p>
      </div>

      <div data-testid="card-spent" class="min-w-0 rounded-lg border border-gray-200 bg-white p-6">
        <p class="text-sm text-gray-600">Gasto do mês</p>
        <p class="break-words text-4xl font-bold text-gray-900">
          {{ formatBRL(overview.monthSpent) }}
        </p>
        <p class="mt-1 text-sm text-gray-700">
          Você gastou {{ formatBRL(overview.monthSpent) }} de
          {{ formatBRL(overview.monthAllocated) }} planejados este mês
        </p>
      </div>

      <div
        data-testid="card-projection"
        class="min-w-0 rounded-lg border border-gray-200 bg-white p-6"
      >
        <p class="text-sm text-gray-600">Projeção do saldo no fim do mês</p>
        <p
          class="break-words text-4xl font-bold"
          :class="overview.projectedBalance < 0 ? 'text-red-600' : 'text-gray-900'"
        >
          {{ formatBRL(overview.projectedBalance) }}
        </p>
        <p class="mt-1 text-sm text-gray-700">
          No ritmo atual de gasto, já descontadas as parcelas do mês
        </p>
      </div>
    </div>
  </div>
  <p v-else role="status" data-testid="overview-loading" class="mt-4 text-sm text-gray-600">
    Carregando…
  </p>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { getOverview } from '@/services/dashboardService'
import { STATUS } from '@/lib/budgetStatus'
import { formatBRL } from '@/lib/money'
import WealthChart from './WealthChart.vue'
import type { DashboardOverview } from '@/types/dashboard'

// Story 7.3: as 5 métricas da Camada 1; Story 7.4: a curva do patrimônio
// Retro Epic 7 A4: `refresh` busca de novo sem voltar ao "Carregando…"; `loaded` avisa a primeira carga (a view restaura o scroll)
const props = defineProps<{ refresh?: number }>()
const emit = defineEmits<{ loaded: [] }>()
const overview = ref<DashboardOverview | null>(null)
const error = ref(false)

const healthyCount = computed(
  () => overview.value!.categories.filter((c) => c.status === 'HEALTHY').length,
)
const overspentCount = computed(
  () => overview.value!.categories.filter((c) => c.status === 'OVERSPENT').length,
)

async function load() {
  try {
    overview.value = await getOverview()
    error.value = false
  } catch {
    error.value = true
  }
}

onMounted(async () => {
  await load()
  emit('loaded')
})
watch(() => props.refresh, load)
</script>
