<template>
  <main class="min-h-screen bg-gray-50 px-4 py-8">
    <div class="mx-auto max-w-3xl space-y-6">
      <div class="flex items-start justify-between gap-4">
        <h1 class="min-w-0 text-2xl font-semibold break-words text-gray-900" data-testid="history-heading">
          Histórico da meta<template v-if="history"> — {{ history.goalName }}</template>
        </h1>
        <RouterLink
          to="/metas"
          data-testid="back-to-goals"
          class="inline-flex min-h-[44px] shrink-0 items-center rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100"
        >
          Voltar às metas
        </RouterLink>
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
        Meta não encontrada.
        <RouterLink to="/metas" class="font-medium underline">Voltar às metas</RouterLink>
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
        <dl class="grid grid-cols-2 gap-3 sm:grid-cols-3" data-testid="history-stats">
          <div class="rounded-lg border border-gray-200 bg-white p-3">
            <dt class="text-xs text-gray-600">Média mensal de aporte</dt>
            <dd class="font-semibold text-gray-900" data-testid="stat-avg-contribution">
              {{ formatBRL(stats.avgContribution) }}
            </dd>
          </div>
          <div class="rounded-lg border border-gray-200 bg-white p-3">
            <dt class="text-xs text-gray-600">Consistência</dt>
            <dd class="font-semibold text-gray-900" data-testid="stat-consistency">
              {{ stats.monthsWithContribution }} de {{ monthsLabel(stats.periodMonths) }} com aporte
            </dd>
          </div>
          <div v-if="stats.timeToComplete !== null" class="rounded-lg border border-gray-200 bg-white p-3">
            <dt class="text-xs text-gray-600">Tempo até concluir</dt>
            <dd class="font-semibold text-gray-900" data-testid="stat-time-to-complete">
              {{ stats.timeToComplete === 0 ? 'menos de 1 mês' : monthsLabel(stats.timeToComplete) }}
            </dd>
          </div>
        </dl>

        <section v-if="history.entries.length > 0" class="rounded-lg border border-gray-200 bg-white p-4">
          <GoalHistoryChart :entries="history.entries" />
        </section>

        <section aria-labelledby="history-table-heading" class="space-y-2">
          <div class="flex items-center justify-between gap-2">
            <h2 id="history-table-heading" class="text-sm font-medium text-gray-900">Registros</h2>
            <Button variant="outline" class="min-h-[44px]" data-testid="export-csv" @click="exportCsv">
              Exportar CSV
            </Button>
          </div>
          <div class="overflow-x-auto rounded-lg border border-gray-200 bg-white">
            <table class="w-full text-left text-sm" data-testid="history-table">
              <thead class="border-b border-gray-200 bg-gray-50 text-gray-600">
                <tr>
                  <th scope="col" class="px-4 py-2 font-medium">Data</th>
                  <th scope="col" class="px-4 py-2 text-right font-medium">Aporte</th>
                  <th scope="col" class="px-4 py-2 text-right font-medium">Total</th>
                  <th scope="col" class="px-4 py-2 text-right font-medium">% do alvo</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="(entry, i) in history.entries"
                  :key="i"
                  class="border-b border-gray-100 last:border-0"
                  data-testid="history-row"
                >
                  <td class="px-4 py-2 whitespace-nowrap text-gray-900">{{ formatLocalDateBR(entry.date) }}</td>
                  <td class="px-4 py-2 text-right whitespace-nowrap text-gray-900">
                    {{ entry.contributionAmount === null ? '—' : formatBRL(entry.contributionAmount) }}
                  </td>
                  <td class="px-4 py-2 text-right whitespace-nowrap text-gray-900">{{ formatBRL(entry.amount) }}</td>
                  <td class="px-4 py-2 text-right whitespace-nowrap text-gray-900">{{ percentOf(entry.amount) }}%</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </template>
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import GoalHistoryChart from '@/components/goals/GoalHistoryChart.vue'
import { Button } from '@/components/ui/button'
import { currentMonth, formatLocalDateBR } from '@/lib/date'
import { formatBRL, formatBRLInput } from '@/lib/money'
import { getGoalHistory } from '@/services/goalService'
import type { GoalHistory } from '@/types/goal'

/**
 * Story 8.8: histórico de uma meta (ativa ou concluída), molde do CategoryBudgetHistoryView. Uma chamada só;
 * estatísticas e CSV são calculados aqui sobre a mesma resposta.
 */
interface ApiError {
  response?: { status?: number }
}

const route = useRoute()
const history = ref<GoalHistory | null>(null)
const loading = ref(true)
const loadFailed = ref(false)
const notFound = ref(false)

/** Data AAAA-MM-DD de um instante ISO em São Paulo (en-CA formata como AAAA-MM-DD). */
const SP_DATE = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo' })
const spDate = (iso: string) => SP_DATE.format(new Date(iso))

/** Meses-calendário de "AAAA-MM" a "AAAA-MM" (b - a). */
function monthDiff(a: string, b: string): number {
  const [ya, ma] = a.split('-').map(Number)
  const [yb, mb] = b.split('-').map(Number)
  return (yb! - ya!) * 12 + (mb! - ma!)
}

/** Meses inteiros entre duas datas AAAA-MM-DD (o mês só conta quando o dia é alcançado). */
function fullMonths(from: string, to: string): number {
  const months = monthDiff(from.slice(0, 7), to.slice(0, 7))
  return Math.max(0, Number(to.slice(8)) < Number(from.slice(8)) ? months - 1 : months)
}

const monthsLabel = (n: number) => (n === 1 ? '1 mês' : `${n} meses`)

/**
 * Período: do mês de criação ao mês de conclusão (ou ao mês atual). Média = aportes ÷ meses do período;
 * consistência = meses com aporte ÷ meses do período.
 */
const stats = computed(() => {
  const h = history.value!
  const start = spDate(h.createdAt).slice(0, 7)
  const end = h.completedAt ? spDate(h.completedAt).slice(0, 7) : currentMonth()
  const periodMonths = Math.max(1, monthDiff(start, end) + 1)
  const contributions = h.entries.filter((e) => e.contributionAmount !== null)
  const months = new Set(
    contributions.map((e) => e.date.slice(0, 7)).filter((m) => m >= start && m <= end),
  )
  return {
    avgContribution: contributions.reduce((total, e) => total + e.contributionAmount!, 0) / periodMonths,
    monthsWithContribution: months.size,
    periodMonths,
    timeToComplete: h.completedAt ? fullMonths(spDate(h.createdAt), spDate(h.completedAt)) : null,
  }
})

/** % do alvo arredondado para baixo: 99,6% ainda não é 100%. */
function percentOf(amount: number): number {
  return Math.floor((amount / history.value!.targetAmount) * 100)
}

/** "Reserva de Emergência" → "reserva-de-emergencia". */
function slug(name: string): string {
  const s = name
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
  return s || 'meta'
}

/** Excel pt-BR (padrão da 6.8): `;` como separador, vírgula decimal, CRLF e BOM. Aporte vazio quando não houve. */
function exportCsv() {
  const lines = [
    'Data;Aporte;Total;% do alvo',
    ...history.value!.entries.map((e) =>
      [
        formatLocalDateBR(e.date),
        e.contributionAmount === null ? '' : formatBRLInput(e.contributionAmount),
        formatBRLInput(e.amount),
        `${percentOf(e.amount)}%`,
      ].join(';'),
    ),
  ]
  const blob = new Blob(['﻿' + lines.join('\r\n') + '\r\n'], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `historico-meta-${slug(history.value!.goalName)}.csv`
  link.click()
  // revogar na hora pode abortar o download em alguns navegadores (Firefox)
  setTimeout(() => URL.revokeObjectURL(url), 0)
}

/** Sequência das cargas: só a resposta da carga mais recente é aplicada. */
let request = 0

async function load() {
  const current = ++request
  loading.value = true
  loadFailed.value = false
  notFound.value = false
  try {
    const data = await getGoalHistory(String(route.params.id))
    if (current !== request) return
    history.value = data
  } catch (err) {
    if (current !== request) return
    // 400 só vem de um id malformado na URL: para o usuário, é uma meta que não existe
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
