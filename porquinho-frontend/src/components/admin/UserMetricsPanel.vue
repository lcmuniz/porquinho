<template>
  <div class="space-y-6">
    <div class="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
      <p v-if="metrics" class="text-xs text-gray-600" data-testid="users-updated">
        Atualizado às {{ formatTime(metrics.generatedAt) }}. Atualiza a cada 5 minutos.
      </p>
      <button
        type="button"
        data-testid="users-export"
        class="inline-flex min-h-[44px] items-center self-start rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100 disabled:opacity-50 sm:ml-auto"
        :disabled="exporting || !metrics"
        @click="exportCsv"
      >
        Exportar CSV
      </button>
    </div>
    <p v-if="exportError" role="alert" class="text-sm text-red-800" data-testid="users-export-error">{{ exportError }}</p>

    <div v-if="!metrics && loading && !error" role="status" class="py-12 text-center text-sm text-gray-700">
      Carregando...
    </div>

    <div
      v-if="error"
      role="alert"
      data-testid="users-error"
      class="flex flex-col gap-2 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800 sm:flex-row sm:items-center sm:justify-between"
    >
      <span>{{ error }}</span>
      <button
        type="button"
        data-testid="users-retry"
        class="inline-flex min-h-[44px] items-center rounded-lg border border-red-300 px-4 py-2 font-medium hover:bg-red-100 disabled:opacity-50"
        :disabled="loading"
        @click="load"
      >
        Tentar novamente
      </button>
    </div>

    <template v-if="metrics">
      <section aria-label="Resumo de usuários" class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <div
          v-for="card in cards"
          :key="card.key"
          class="rounded-lg border bg-white p-4"
          :class="card.status ? STATUS_STYLE[card.status].border : 'border-gray-200'"
          :data-testid="`users-card-${card.key}`"
        >
          <div class="flex items-center justify-between gap-2">
            <h2 class="text-sm font-medium text-gray-700">{{ card.title }}</h2>
            <span
              v-if="card.status"
              class="rounded-full px-2 py-0.5 text-xs font-medium"
              :class="STATUS_STYLE[card.status].badge"
              :data-testid="`users-status-${card.key}`"
            >
              {{ STATUS_STYLE[card.status].label }}
            </span>
          </div>
          <p class="mt-2 text-2xl font-semibold text-gray-900">{{ card.value }}</p>
          <p v-if="card.line" class="text-xs text-gray-600">{{ card.line }}</p>
        </div>
      </section>

      <section aria-labelledby="users-engagement-heading" class="space-y-3">
        <h2 id="users-engagement-heading" class="text-base font-semibold text-gray-900">Engajamento</h2>
        <div class="grid gap-4 sm:grid-cols-3">
          <div
            v-for="card in engagement"
            :key="card.key"
            class="rounded-lg border bg-white p-4"
            :class="card.status ? STATUS_STYLE[card.status].border : 'border-gray-200'"
            :data-testid="`users-card-${card.key}`"
          >
            <div class="flex items-center justify-between gap-2">
              <h3 class="text-sm font-medium text-gray-700">{{ card.title }}</h3>
              <span
                v-if="card.status"
                class="rounded-full px-2 py-0.5 text-xs font-medium"
                :class="STATUS_STYLE[card.status].badge"
                :data-testid="`users-status-${card.key}`"
              >
                {{ STATUS_STYLE[card.status].label }}
              </span>
            </div>
            <p class="mt-2 text-2xl font-semibold text-gray-900">{{ card.value }}</p>
            <p v-if="card.line" class="text-xs text-gray-600">{{ card.line }}</p>
          </div>
        </div>
      </section>

      <section aria-labelledby="users-growth-heading" class="rounded-lg border border-gray-200 bg-white p-4">
        <h2 id="users-growth-heading" class="mb-2 text-base font-semibold text-gray-900">Crescimento de usuários (12 meses)</h2>
        <AccuracyLineChart
          :months="metrics.months.map(monthLabel)"
          :series="[{ label: 'Usuários', color: '#7c3aed', values: metrics.growth }]"
          test-id="users-chart-growth"
          label="Total de usuários no fim de cada um dos últimos 12 meses"
          :format="formatCount"
          :max="niceMax(metrics.growth)"
          :tick-format="formatCount"
          series-header="Série"
          empty-text="Sem dados"
        />
      </section>

      <section aria-labelledby="users-active-heading" class="rounded-lg border border-gray-200 bg-white p-4">
        <h2 id="users-active-heading" class="text-base font-semibold text-gray-900">Usuários ativos (12 semanas)</h2>
        <p class="mb-2 text-xs text-gray-600" data-testid="users-active-note">
          Ativos medidos no fim de cada semana (a atual, agora); o rótulo é a segunda-feira em que a semana começa.
        </p>
        <AccuracyLineChart
          :months="metrics.weeks.map(weekLabel)"
          :series="activeSeries"
          test-id="users-chart-active"
          label="Usuários ativos em 7 e em 30 dias medidos no fim de cada uma das últimas 12 semanas (a atual, agora); o rótulo é a segunda-feira em que a semana começa"
          :format="formatCount"
          :max="niceMax([...metrics.activeWeekly, ...metrics.activeMonthly])"
          :tick-format="formatCount"
          series-header="Série"
          empty-text="Sem dados"
        />
      </section>

      <section aria-labelledby="users-conversion-heading" class="rounded-lg border border-gray-200 bg-white p-4">
        <h2 id="users-conversion-heading" class="mb-2 text-base font-semibold text-gray-900">
          Conversão trial → pago por mês de cadastro (%)
        </h2>
        <AccuracyLineChart
          :months="metrics.months.map(monthLabel)"
          :series="[{ label: 'Conversão', color: '#2563eb', values: metrics.conversionByMonth }]"
          test-id="users-chart-conversion"
          label="Conversão de trial para pago dos usuários cadastrados em cada um dos últimos 12 meses"
          :format="formatPercent"
          series-header="Série"
          empty-text="Sem dados de conversão nos últimos 12 meses"
        />
      </section>

      <section aria-labelledby="users-adoption-heading" class="rounded-lg border border-gray-200 bg-white p-4">
        <h2 id="users-adoption-heading" class="mb-2 text-base font-semibold text-gray-900">Adoção por recurso</h2>
        <div class="overflow-x-auto">
          <table class="w-full text-left text-sm" data-testid="users-adoption">
            <thead class="text-gray-700">
              <tr>
                <th scope="col" class="py-2 pr-4 font-medium">Recurso</th>
                <th scope="col" class="py-2 pr-4 text-right font-medium">Usuários</th>
                <th scope="col" class="py-2 text-right font-medium">% da base</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="f in metrics.adoption"
                :key="f.feature"
                class="border-t border-gray-100 text-gray-900"
                :data-testid="`users-adoption-${f.feature}`"
              >
                <td class="py-2 pr-4">{{ FEATURE_LABELS[f.feature] }}</td>
                <td class="py-2 pr-4 text-right">{{ formatCount(f.users) }}</td>
                <td class="py-2 text-right">{{ formatPercent(f.rate) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import AccuracyLineChart from '@/components/admin/AccuracyLineChart.vue'
import { exportUserMetrics, getUserMetrics } from '@/services/adminService'
import { apiErrorStatus } from '@/lib/apiError'
import { downloadBlob } from '@/lib/download'
import { todaySaoPaulo } from '@/lib/date'
import type { HealthStatus, UserFeature, UserMetrics } from '@/types/admin'

// Story 10.4: usuários ativos, assinaturas, engajamento e adoção; o 403 sobe para a view mostrar "Acesso restrito"
const emit = defineEmits<{ forbidden: [] }>()

const REFRESH_MS = 5 * 60_000

const FEATURE_LABELS: Record<UserFeature, string> = {
  ACCOUNTS: 'Contas',
  CREDIT_CARDS: 'Cartões',
  IMPORTS: 'Importação concluída',
  BUDGETS: 'Orçamento',
  GOALS: 'Metas',
  TRANSFERS: 'Transferências',
  RECONCILIATIONS: 'Conciliação',
}

const STATUS_STYLE: Record<HealthStatus, { label: string; badge: string; border: string }> = {
  GREEN: { label: 'Dentro da meta', badge: 'bg-green-100 text-green-800', border: 'border-green-300' },
  YELLOW: { label: 'Atenção', badge: 'bg-yellow-100 text-yellow-800', border: 'border-yellow-300' },
  RED: { label: 'Crítico', badge: 'bg-red-100 text-red-800', border: 'border-red-300' },
  UNKNOWN: { label: 'Sem dados', badge: 'bg-gray-100 text-gray-700', border: 'border-gray-200' },
}

const metrics = ref<UserMetrics | null>(null)
const loading = ref(false)
const error = ref('')
const exporting = ref(false)
const exportError = ref('')
let timer: ReturnType<typeof setInterval> | undefined

const count = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 0 })
const percent = new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 })
const ratio = new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

function formatCount(value: number) {
  return count.format(value)
}

function formatPercent(value: number | null) {
  return value === null ? 'sem dados' : `${percent.format(value)} %`
}

function formatTime(iso: string) {
  return new Date(iso).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })
}

function monthLabel(month: string) {
  const [year, mm] = month.split('-')
  return `${mm}/${year?.slice(2)}`
}

function weekLabel(day: string) {
  const [, mm, dd] = day.split('-')
  return `${dd}/${mm}`
}

// Topo par: a marca do meio do eixo fica inteira
function niceMax(values: number[]) {
  return Math.max(2, Math.ceil(Math.max(0, ...values) / 2) * 2)
}

type Card = { key: string; title: string; value: string; line?: string; status?: HealthStatus }

const cards = computed<Card[]>(() => {
  const m = metrics.value
  if (!m) return []
  return [
    { key: 'total', title: 'Total de usuários', value: formatCount(m.total) },
    { key: 'active24h', title: 'Ativos em 24 h', value: formatCount(m.active24h) },
    { key: 'active7d', title: 'Ativos em 7 dias', value: formatCount(m.active7d) },
    { key: 'active30d', title: 'Ativos em 30 dias (MAU)', value: formatCount(m.active30d) },
    { key: 'trials', title: 'Em trial', value: formatCount(m.trials) },
    { key: 'paid', title: 'Pagos', value: formatCount(m.paid) },
    {
      key: 'churn',
      title: 'Churn',
      value: formatPercent(m.churn.rate),
      line: `Meta: < ${count.format(m.churn.target)} %`,
      status: m.churn.status,
    },
  ]
})

const engagement = computed<Card[]>(() => {
  const m = metrics.value
  if (!m) return []
  return [
    {
      key: 'checkins',
      title: 'Check-ins semanais por usuário',
      value: m.weeklyCheckins === null ? 'sem dados' : ratio.format(m.weeklyCheckins),
      line: 'Semanas com importação concluída por ativo em 30 dias (média de 4 semanas)',
    },
    {
      key: 'onboarding',
      title: 'Onboarding concluído',
      value: formatPercent(m.onboarding.rate),
      line: `Meta: ${count.format(m.onboarding.target)} % ou mais`,
      status: m.onboarding.status,
    },
    {
      key: 'conversion',
      title: 'Conversão trial → pago',
      value: formatPercent(m.conversion),
      line: 'Assinantes sobre assinantes e trials já encerrados',
    },
  ]
})

const activeSeries = computed(() => {
  const m = metrics.value
  if (!m) return []
  return [
    { label: 'Ativos em 7 dias', color: '#2563eb', values: m.activeWeekly },
    { label: 'Ativos em 30 dias', color: '#7c3aed', values: m.activeMonthly },
  ]
})

async function load() {
  // O intervalo não empilha requisições
  if (loading.value) return
  loading.value = true
  try {
    metrics.value = await getUserMetrics()
    error.value = ''
  } catch (err) {
    if (apiErrorStatus(err) === 403) onForbidden()
    else error.value = 'Não foi possível carregar as métricas de usuários.'
  } finally {
    loading.value = false
  }
}

async function exportCsv() {
  exporting.value = true
  exportError.value = ''
  try {
    downloadBlob(await exportUserMetrics(), `usuarios_${todaySaoPaulo()}.csv`)
  } catch (err) {
    if (apiErrorStatus(err) === 403) onForbidden()
    else exportError.value = 'Não foi possível exportar o CSV.'
  } finally {
    exporting.value = false
  }
}

function onForbidden() {
  stop()
  emit('forbidden')
}

function stop() {
  if (timer !== undefined) clearInterval(timer)
  timer = undefined
}

// Aba do navegador escondida não consulta: cada chamada renova last_activity e manteria a sessão viva para sempre
function onVisibilityChange() {
  if (document.visibilityState === 'visible' && timer !== undefined) void load()
}

onMounted(() => {
  void load()
  timer = setInterval(() => {
    if (document.visibilityState !== 'hidden') void load()
  }, REFRESH_MS)
  document.addEventListener('visibilitychange', onVisibilityChange)
})

onBeforeUnmount(() => {
  stop()
  document.removeEventListener('visibilitychange', onVisibilityChange)
})
</script>
