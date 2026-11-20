<template>
  <div
    v-if="error"
    role="alert"
    data-testid="overview-error"
    class="mt-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between"
  >
    <p class="text-sm text-gray-700">Não foi possível carregar a visão geral</p>
    <Button variant="outline" class="min-h-[44px]" data-testid="overview-error-retry" @click="retry">Tentar de novo</Button>
  </div>
  <div v-else-if="overview" class="mt-4 space-y-4">
    <!-- novo-design: no desktop, a curva em 2/3 e os três valores do mês empilhados ao lado; no mobile, um embaixo do outro -->
    <div data-testid="wealth-row" class="grid grid-cols-1 gap-4 lg:grid-cols-3">
      <!-- novo-design: no celular os valores do mês vêm antes do gráfico ("onde estou?" na primeira tela) -->
      <WealthChart class="order-2 lg:order-1 lg:col-span-2" :points="overview.wealthHistory" />
      <div class="order-1 grid min-w-0 grid-cols-1 gap-4 md:grid-cols-3 lg:order-2 lg:grid-cols-1">
        <div data-testid="card-balance" class="min-w-0 rounded-lg border border-gray-200 bg-white p-4 md:p-6">
          <p class="text-sm font-medium text-gray-600">Saldo total disponível</p>
          <p data-kpi
            class="mt-1 break-words text-[28px] leading-9 font-bold text-gray-900 md:text-[32px] md:leading-10">
            {{ formatBRL(overview.totalBalance) }}
          </p>
        </div>
        <div data-testid="card-spent" class="min-w-0 rounded-lg border border-gray-200 bg-white p-4 md:p-6">
          <p class="text-sm font-medium text-gray-600">Gasto do mês</p>
          <p data-kpi
            class="mt-1 break-words text-[28px] leading-9 font-bold md:text-[32px] md:leading-10 text-gray-900">
            {{ formatBRL(overview.monthSpent) }}
          </p>
          <p class="mt-1 text-sm text-gray-600">
            Você gastou {{ formatBRL(overview.monthSpent) }} de
            {{ formatBRL(overview.monthAllocated) }} planejados este mês
          </p>
        </div>

        <div
          data-testid="card-projection"
          class="min-w-0 rounded-lg border border-gray-200 bg-white p-4 md:p-6"
        >
          <p class="text-sm font-medium text-gray-600">Projeção do saldo no fim do mês</p>
          <p
            data-kpi
            class="mt-1 break-words text-[28px] leading-9 font-bold md:text-[32px] md:leading-10"
            :class="overview.projectedBalance < 0 ? 'text-red-600' : 'text-gray-900'"
          >
            {{ formatBRL(overview.projectedBalance) }}
          </p>
          <p class="mt-1 text-sm text-gray-600">
            No ritmo atual de gasto, já descontadas as parcelas do mês
          </p>
        </div>
      </div>
    </div>

    <div data-testid="metrics-grid" class="grid grid-cols-1 gap-4 md:grid-cols-2">
      <div
        data-testid="card-categories"
        class="min-w-0 rounded-lg border border-gray-200 bg-white p-4 md:p-6"
      >
        <p class="text-sm font-medium text-gray-600">Status das categorias</p>
        <template v-if="overview.categories.length">
          <p data-kpi
            class="mt-1 break-words text-[28px] leading-9 font-bold md:text-[32px] md:leading-10 text-gray-900">
            {{ healthyCount }} de {{ overview.categories.length }}
          </p>
          <p class="text-sm font-medium text-gray-600">{{ healthyCount === 1 ? 'saudável' : 'saudáveis' }}</p>
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

      <div data-testid="card-goal" class="min-w-0 rounded-lg border border-gray-200 bg-white p-4 md:p-6">
        <p class="text-sm font-medium text-gray-600">Meta principal</p>
        <template v-if="overview.mainGoal">
          <p data-kpi
            class="mt-1 break-words text-[28px] leading-9 font-bold md:text-[32px] md:leading-10 text-gray-900">
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
            <div class="h-full bg-green-600" :style="{ width: `${overview.mainGoal.percent}%` }" />
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

    </div>
  </div>
  <p v-else role="status" data-testid="overview-loading" class="mt-4 text-sm text-gray-600">
    Carregando…
  </p>
</template>

<script setup lang="ts">
import { Button } from '@/components/ui/button'
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

/** Retro Epic 7 A6/F11: volta ao "Carregando…" e busca de novo. */
function retry() {
  error.value = false
  void load()
}

onMounted(async () => {
  await load()
  emit('loaded')
})
watch(() => props.refresh, load)
</script>
