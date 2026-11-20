<template>
  <div class="space-y-6">
    <div class="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
      <p v-if="metrics" class="text-xs text-gray-600" data-testid="users-updated">
        Atualizado em {{ formatDateTimeBR(metrics.generatedAt) }}. Atualiza a cada 5 minutos.
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
      Carregando…
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
          :class="card.status ? ADMIN_STATUS[card.status].border : 'border-gray-200'"
          :data-testid="`users-card-${card.key}`"
        >
          <div class="flex items-center justify-between gap-2">
            <h2 class="text-sm font-medium text-gray-700">{{ card.title }}</h2>
            <StatusBadge v-if="card.status" :status="card.status" :data-testid="`users-status-${card.key}`" />
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
            :class="card.status ? ADMIN_STATUS[card.status].border : 'border-gray-200'"
            :data-testid="`users-card-${card.key}`"
          >
            <div class="flex items-center justify-between gap-2">
              <h3 class="text-sm font-medium text-gray-700">{{ card.title }}</h3>
              <StatusBadge v-if="card.status" :status="card.status" :data-testid="`users-status-${card.key}`" />
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
          :series="[{ label: 'Usuários', color: '#1E40AF', values: metrics.growth }]"
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
import { computed, onMounted, ref } from 'vue'
import AccuracyLineChart from '@/components/admin/AccuracyLineChart.vue'
import StatusBadge from '@/components/admin/StatusBadge.vue'
import { useExportDialog } from '@/composables/useExportDialog'
import { usePolling } from '@/composables/usePolling'
import { exportUserMetrics, getUserMetrics } from '@/services/adminService'
import { ADMIN_STATUS } from '@/lib/adminStatus'
import { apiErrorStatus } from '@/lib/apiError'
import { downloadBlob } from '@/lib/download'
import { formatDateTimeBR, todaySaoPaulo } from '@/lib/date'
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

const metrics = ref<UserMetrics | null>(null)
const loading = ref(false)
const error = ref('')
const exportError = ref('')
const { exporting, run: runExport } = useExportDialog()
const { stop } = usePolling(() => void load(), REFRESH_MS)

const count = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 0 })
const percent = new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 })
const ratio = new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

function formatCount(value: number) {
  return count.format(value)
}

function formatPercent(value: number | null) {
  return value === null ? 'sem dados' : `${percent.format(value)} %`
}

// 'AAAA-MM' vira data UTC: formatar em UTC evita cair no mês anterior
const monthFormat = new Intl.DateTimeFormat('pt-BR', { month: '2-digit', year: '2-digit', timeZone: 'UTC' })
function monthLabel(month: string) {
  return monthFormat.format(new Date(`${month}-01`))
}

const dayFormat = new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: '2-digit', timeZone: 'UTC' })
function weekLabel(day: string) {
  return dayFormat.format(new Date(day))
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
    { label: 'Ativos em 30 dias', color: '#1E40AF', values: m.activeMonthly },
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
  exportError.value = ''
  await runExport(
    async () => downloadBlob(await exportUserMetrics(), `usuarios_${todaySaoPaulo()}.csv`),
    (err) => {
      if (apiErrorStatus(err) === 403) onForbidden()
      else exportError.value = 'Não foi possível exportar o CSV.'
    },
  )
}

function onForbidden() {
  stop()
  emit('forbidden')
}

onMounted(load)
</script>
