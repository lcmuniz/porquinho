<template>
  <main class="min-h-screen bg-gray-50 px-4 py-8">
    <div class="mx-auto max-w-3xl space-y-6">
      <div class="flex items-start justify-between gap-4">
        <h1 class="min-w-0 text-2xl font-semibold text-gray-900" data-testid="history-heading">
          Histórico do orçamento<template v-if="history"> — {{ history.categoryName }}</template>
        </h1>
        <RouterLink
          to="/orcamento"
          data-testid="back-to-budget"
          class="inline-flex min-h-[44px] shrink-0 items-center rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100"
        >
          Voltar ao orçamento
        </RouterLink>
      </div>

      <div
        v-if="!notFound"
        role="group"
        aria-label="Período"
        class="flex flex-wrap gap-2"
        data-testid="range-selector"
      >
        <Button
          v-for="option in RANGE_OPTIONS"
          :key="option.value"
          variant="outline"
          class="min-h-[44px]"
          :class="
            range === option.value
              ? 'border-purple-600 bg-purple-50 text-purple-700 hover:bg-purple-100'
              : 'text-gray-700'
          "
          :aria-pressed="range === option.value ? 'true' : 'false'"
          :data-testid="`range-${option.value}`"
          @click="range = option.value"
        >
          {{ option.label }}
        </Button>
      </div>

      <div v-if="loading" class="space-y-2" aria-busy="true" data-testid="history-skeleton">
        <div class="h-64 animate-pulse rounded-lg bg-gray-100" />
        <span class="sr-only">Carregando o histórico...</span>
      </div>

      <div
        v-else-if="notFound"
        role="alert"
        class="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900"
        data-testid="history-not-found"
      >
        Esta categoria não existe mais.
        <RouterLink to="/orcamento" class="font-medium underline">Voltar ao orçamento</RouterLink>
      </div>

      <div
        v-else-if="loadFailed"
        role="alert"
        data-testid="history-load-error"
        class="flex flex-col gap-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3 sm:flex-row sm:items-center sm:justify-between"
      >
        <p class="text-sm text-red-800">
          Não foi possível carregar o histórico. Verifique sua conexão e tente de novo.
        </p>
        <Button variant="outline" class="min-h-[44px] shrink-0" data-testid="history-retry" @click="load">
          Tentar de novo
        </Button>
      </div>

      <template v-else-if="history">
        <p
          v-if="filtered.length === 0"
          class="rounded-lg border border-gray-200 bg-white p-6 text-center text-gray-600"
          data-testid="history-empty"
        >
          Nenhum mês com orçamento neste período
        </p>

        <template v-else>
          <dl class="grid grid-cols-2 gap-3 sm:grid-cols-4" data-testid="history-stats">
            <div class="rounded-lg border border-gray-200 bg-white p-3">
              <dt class="text-xs text-gray-600">Média alocada por mês</dt>
              <dd class="font-semibold text-gray-900" data-testid="stat-avg-allocated">
                {{ formatBRL(stats.avgAllocated) }}
              </dd>
            </div>
            <div class="rounded-lg border border-gray-200 bg-white p-3">
              <dt class="text-xs text-gray-600">Média gasta por mês</dt>
              <dd class="font-semibold text-gray-900" data-testid="stat-avg-spent">
                {{ formatBRL(stats.avgSpent) }}
              </dd>
            </div>
            <div class="rounded-lg border border-gray-200 bg-white p-3">
              <dt class="text-xs text-gray-600">Meses estourados</dt>
              <dd class="font-semibold text-gray-900" data-testid="stat-overspent">{{ stats.overspent }}</dd>
            </div>
            <div class="rounded-lg border border-gray-200 bg-white p-3">
              <dt class="text-xs text-gray-600">Melhor mês (menor gasto)</dt>
              <dd class="font-semibold text-gray-900" data-testid="stat-best-month">
                {{ formatMonthBR(stats.best) }}
              </dd>
            </div>
          </dl>

          <section class="rounded-lg border border-gray-200 bg-white p-4">
            <AllocationHistoryChart :months="filtered" />
          </section>

          <section aria-labelledby="history-table-heading" class="space-y-2">
            <div class="flex items-center justify-between gap-2">
              <h2 id="history-table-heading" class="text-sm font-medium text-gray-900">Meses</h2>
              <Button variant="outline" class="min-h-[44px]" data-testid="export-csv" @click="exportCsv">
                Exportar CSV
              </Button>
            </div>
            <div class="overflow-x-auto rounded-lg border border-gray-200 bg-white">
              <table class="w-full text-left text-sm" data-testid="history-table">
                <thead class="border-b border-gray-200 bg-gray-50 text-gray-600">
                  <tr>
                    <th
                      scope="col"
                      class="px-4 py-2 font-medium"
                      :aria-sort="newestFirst ? 'descending' : 'ascending'"
                      data-testid="sort-month-header"
                    >
                      <button
                        type="button"
                        class="inline-flex min-h-[44px] items-center gap-1 font-medium hover:text-gray-900"
                        data-testid="sort-month"
                        @click="newestFirst = !newestFirst"
                      >
                        Mês
                        <component :is="newestFirst ? ArrowDown : ArrowUp" class="size-4" aria-hidden="true" />
                      </button>
                    </th>
                    <th scope="col" class="px-4 py-2 text-right font-medium">Alocado</th>
                    <th scope="col" class="px-4 py-2 text-right font-medium">Gasto</th>
                    <th scope="col" class="px-4 py-2 text-right font-medium">Restante</th>
                    <th scope="col" class="px-4 py-2 font-medium">Status</th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="m in rows"
                    :key="m.month"
                    class="border-b border-gray-100 last:border-0"
                    :data-testid="`history-row-${m.month}`"
                  >
                    <td class="px-4 py-2 whitespace-nowrap text-gray-900 first-letter:uppercase">
                      {{ formatMonthBR(m.month) }}
                    </td>
                    <td class="px-4 py-2 text-right whitespace-nowrap text-gray-900">{{ formatBRL(m.allocated) }}</td>
                    <td class="px-4 py-2 text-right whitespace-nowrap text-gray-900">{{ formatBRL(m.spent) }}</td>
                    <td
                      class="px-4 py-2 text-right whitespace-nowrap"
                      :class="m.remaining < 0 ? 'text-red-600' : 'text-gray-900'"
                    >
                      {{ formatBRL(m.remaining) }}
                    </td>
                    <td class="px-4 py-2">
                      <span class="inline-flex items-center gap-1 font-medium text-gray-900">
                        <component
                          :is="STATUS[m.status].icon"
                          class="size-4 shrink-0"
                          :class="STATUS[m.status].text"
                          aria-hidden="true"
                        />
                        {{ STATUS[m.status].label(m.remaining) }}
                      </span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
        </template>
      </template>
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { ArrowDown, ArrowUp } from 'lucide-vue-next'
import AllocationHistoryChart from '@/components/budget/AllocationHistoryChart.vue'
import { Button } from '@/components/ui/button'
import { STATUS } from '@/lib/budgetStatus'
import { currentMonth, formatMonthBR, shiftMonth } from '@/lib/date'
import { downloadCsv, fileSlug } from '@/lib/download'
import { formatBRL, formatBRLInput } from '@/lib/money'
import { getCategoryBudgetHistory } from '@/services/budgetService'
import type { CategoryBudgetHistory } from '@/types/budget'

/**
 * Story 6.8: histórico de alocações de uma categoria. Uma chamada só; filtro, estatísticas e CSV são calculados aqui
 * sobre a mesma resposta.
 */
type Range = '3m' | '6m' | '1y' | 'all'

/** "N meses" = os N últimos meses-calendário contando o atual; null = tudo. */
const RANGE_OPTIONS: { value: Range; label: string; months: number | null }[] = [
  { value: '3m', label: '3 meses', months: 3 },
  { value: '6m', label: '6 meses', months: 6 },
  { value: '1y', label: '1 ano', months: 12 },
  { value: 'all', label: 'Tudo', months: null },
]

interface ApiError {
  response?: { status?: number }
}

const route = useRoute()
const range = ref<Range>('1y')
const newestFirst = ref(true)
const history = ref<CategoryBudgetHistory | null>(null)
const loading = ref(true)
const loadFailed = ref(false)
const notFound = ref(false)

/** Meses do período, do mais antigo ao mais novo (ordem da API). */
const filtered = computed(() => {
  const months = history.value?.months ?? []
  const n = RANGE_OPTIONS.find((o) => o.value === range.value)!.months
  if (n === null) return months
  const from = shiftMonth(currentMonth(), -(n - 1))
  return months.filter((m) => m.month >= from)
})

const rows = computed(() => (newestFirst.value ? [...filtered.value].reverse() : filtered.value))

const stats = computed(() => {
  const months = filtered.value
  const sum = (key: 'allocated' | 'spent') => months.reduce((total, m) => total + m[key], 0)
  // De antigo para novo com <=: no empate fica o mais recente
  let best = months[0]!
  for (const m of months) if (m.spent <= best.spent) best = m
  return {
    avgAllocated: sum('allocated') / months.length,
    avgSpent: sum('spent') / months.length,
    overspent: months.filter((m) => m.status === 'OVERSPENT').length,
    best: best.month,
  }
})

/** Excel pt-BR: `;` como separador, vírgula decimal e BOM para abrir os acentos certos. */
function exportCsv() {
  const lines = [
    'Mês;Alocado;Gasto;Restante;Status',
    ...filtered.value.map((m) =>
      [
        formatMonthBR(m.month),
        formatBRLInput(m.allocated),
        formatBRLInput(m.spent),
        formatBRLInput(m.remaining),
        STATUS[m.status].label(m.remaining).replace(/\s/g, ' '),
      ].join(';'),
    ),
  ]
  downloadCsv(lines, `historico-${fileSlug(history.value!.categoryName, 'categoria')}.csv`)
}

/** Sequência das cargas: só a resposta da carga mais recente é aplicada. */
let request = 0

async function load() {
  const current = ++request
  loading.value = true
  loadFailed.value = false
  notFound.value = false
  try {
    const data = await getCategoryBudgetHistory(String(route.params.id))
    if (current !== request) return
    history.value = data
  } catch (err) {
    if (current !== request) return
    // 400 só vem de um id malformado na URL: para o usuário, é uma categoria que não existe
    const status = (err as ApiError)?.response?.status
    if (status === 404 || status === 400) notFound.value = true
    else loadFailed.value = true
  } finally {
    if (current === request) loading.value = false
  }
}

onMounted(load)

watch(
  () => route.params.id,
  () => {
    history.value = null
    void load()
  },
)
</script>
