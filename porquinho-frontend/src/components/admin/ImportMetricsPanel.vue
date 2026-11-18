<template>
  <div class="space-y-6">
    <form
      class="grid gap-3 rounded-lg border border-gray-200 bg-white p-4 sm:grid-cols-2 lg:grid-cols-5 lg:items-end"
      aria-label="Filtros das importações"
      data-testid="import-filters"
      @submit.prevent="apply"
    >
      <label class="text-sm text-gray-700">
        De
        <input v-model="form.from" type="date" :class="SELECT_CLASS" data-testid="filter-from" />
      </label>
      <label class="text-sm text-gray-700">
        Até
        <input v-model="form.to" type="date" :class="SELECT_CLASS" data-testid="filter-to" />
      </label>
      <label class="text-sm text-gray-700">
        Banco
        <select v-model="form.bank" :class="SELECT_CLASS" data-testid="filter-bank">
          <option value="">Todos</option>
          <option v-for="(name, code) in LABELS" :key="code" :value="code">{{ name }}</option>
        </select>
      </label>
      <label class="text-sm text-gray-700">
        Status (detalhe e CSV)
        <select v-model="form.status" :class="SELECT_CLASS" data-testid="filter-status">
          <option value="ALL">Todos</option>
          <option value="FAILED">Falhas</option>
          <option value="SUCCESS">Sucessos</option>
        </select>
      </label>
      <div class="flex gap-2">
        <button
          type="submit"
          data-testid="filter-apply"
          class="inline-flex min-h-[44px] items-center rounded-lg bg-purple-600 px-4 py-2 text-sm font-medium text-white hover:bg-purple-700 disabled:opacity-50"
          :disabled="loading"
        >
          Filtrar
        </button>
        <button
          type="button"
          data-testid="import-export"
          class="inline-flex min-h-[44px] items-center rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100 disabled:opacity-50"
          :disabled="exporting || !metrics"
          @click="exportCsv"
        >
          Exportar CSV
        </button>
      </div>
      <p v-if="exportError" role="alert" class="text-sm text-red-800 sm:col-span-2 lg:col-span-5" data-testid="export-error">
        {{ exportError }}
      </p>
    </form>

    <div v-if="!metrics && loading && !error" role="status" class="py-12 text-center text-sm text-gray-700">
      Carregando...
    </div>

    <div
      v-if="error"
      role="alert"
      data-testid="imports-error"
      class="flex flex-col gap-2 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800 sm:flex-row sm:items-center sm:justify-between"
    >
      <span>{{ error }}</span>
      <button
        type="button"
        data-testid="imports-retry"
        class="inline-flex min-h-[44px] items-center rounded-lg border border-red-300 px-4 py-2 font-medium hover:bg-red-100 disabled:opacity-50"
        :disabled="loading"
        @click="load"
      >
        Tentar novamente
      </button>
    </div>

    <template v-if="metrics">
      <p class="text-sm text-gray-700" data-testid="imports-target">
        Taxa de erro alvo: &lt; {{ percent.format(metrics.target) }} % por banco ·
        {{ formatLocalDateBR(metrics.from) }} a {{ formatLocalDateBR(metrics.to) }}
      </p>

      <section aria-labelledby="imports-table-heading" class="rounded-lg border border-gray-200 bg-white p-4">
        <h2 id="imports-table-heading" class="mb-2 text-base font-semibold text-gray-900">Taxa de erro por banco</h2>
        <p v-if="!metrics.banks.length" class="py-4 text-sm text-gray-700" data-testid="imports-empty">
          Nenhuma importação no período
        </p>
        <div v-else class="overflow-x-auto">
          <table class="w-full text-left text-sm" data-testid="imports-table">
            <thead class="text-gray-700">
              <tr>
                <th scope="col" class="py-2 pr-4 font-medium">Banco</th>
                <th scope="col" class="py-2 pr-4 text-right font-medium">Total</th>
                <th scope="col" class="py-2 pr-4 text-right font-medium">Sucessos</th>
                <th scope="col" class="py-2 pr-4 text-right font-medium">Falhas</th>
                <th scope="col" class="py-2 pr-4 text-right font-medium">Taxa de erro (%)</th>
                <th scope="col" class="py-2 font-medium">Status</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="b in metrics.banks"
                :key="b.bank"
                class="border-t border-gray-100"
                :class="b.status === 'RED' ? 'text-red-700' : 'text-gray-900'"
                :data-testid="`bank-row-${b.bank}`"
              >
                <td class="py-2 pr-4">
                  <button
                    type="button"
                    class="min-h-[44px] text-left font-medium text-purple-700 underline-offset-2 hover:underline"
                    :aria-pressed="selected === b.bank"
                    :data-testid="`bank-open-${b.bank}`"
                    @click="openDetail(b.bank)"
                  >
                    {{ LABELS[b.bank] }}
                  </button>
                </td>
                <td class="py-2 pr-4 text-right">{{ b.total }}</td>
                <td class="py-2 pr-4 text-right">{{ b.successes }}</td>
                <td class="py-2 pr-4 text-right">{{ b.failures }}</td>
                <td class="py-2 pr-4 text-right" data-testid="bank-rate">{{ formatRate(b.errorRate) }}</td>
                <td class="py-2">
                  <span
                    class="rounded-full px-2 py-0.5 text-xs font-medium"
                    :class="STATUS_STYLE[b.status].badge"
                    data-testid="bank-status"
                  >
                    {{ STATUS_STYLE[b.status].label }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section aria-labelledby="imports-chart-heading" class="rounded-lg border border-gray-200 bg-white p-4">
        <h2 id="imports-chart-heading" class="mb-2 text-base font-semibold text-gray-900">Taxa de erro por banco (%)</h2>
        <HealthBarChart
          :points="bankPoints"
          :color="GREEN"
          test-id="chart-banks"
          :has-data="bankPoints.some((p) => p.value !== null)"
          label="Taxa de erro de importação de cada banco no período"
          :format="formatRate"
          empty-text="Nenhuma importação terminada no período"
          :label-every="1"
          column-label="Banco"
        />
      </section>
    </template>

    <section
      v-if="selected"
      aria-labelledby="bank-detail-heading"
      class="space-y-4 rounded-lg border border-gray-200 bg-white p-4"
      data-testid="bank-detail"
    >
      <h2 id="bank-detail-heading" class="text-base font-semibold text-gray-900">{{ LABELS[selected] }}</h2>

      <div v-if="detailError" role="alert" class="text-sm text-red-800" data-testid="detail-error">{{ detailError }}</div>
      <p v-else-if="!detail" role="status" class="text-sm text-gray-700">Carregando...</p>

      <template v-if="detail">
        <div>
          <h3 class="mb-1 text-sm font-semibold text-gray-900">Tipos de erro</h3>
          <p v-if="!detail.errorTypes.length" class="text-sm text-gray-700">Nenhuma falha no período.</p>
          <ul v-else class="text-sm text-gray-900" data-testid="detail-error-types">
            <li v-for="t in detail.errorTypes" :key="t.type">{{ ERROR_TYPE_LABELS[t.type] }}: {{ t.count }}</li>
          </ul>
        </div>

        <div>
          <h3 class="mb-1 text-sm font-semibold text-gray-900">Importações recentes</h3>
          <p v-if="!detail.recent.length" class="text-sm text-gray-700">Nenhuma importação com esse filtro.</p>
          <div v-else class="overflow-x-auto">
            <table class="w-full text-left text-sm" data-testid="detail-recent">
              <thead class="text-gray-700">
                <tr>
                  <th scope="col" class="py-2 pr-4 font-medium">Data</th>
                  <th scope="col" class="py-2 pr-4 font-medium">Arquivo</th>
                  <th scope="col" class="py-2 pr-4 font-medium">Status</th>
                  <th scope="col" class="py-2 pr-4 font-medium">Tipo de erro</th>
                  <th scope="col" class="py-2 font-medium">Mensagem</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(r, i) in detail.recent" :key="i" class="border-t border-gray-100 text-gray-900">
                  <td class="py-2 pr-4 whitespace-nowrap">{{ formatDateTimeBR(r.createdAt) }}</td>
                  <td class="py-2 pr-4">{{ r.fileType }}</td>
                  <td class="py-2 pr-4">{{ STATUS_LABELS[r.status] }}</td>
                  <td class="py-2 pr-4">{{ r.errorType ? ERROR_TYPE_LABELS[r.errorType] : '—' }}</td>
                  <td class="py-2">{{ r.message ?? '—' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <div>
          <h3 class="mb-1 text-sm font-semibold text-gray-900">Taxa de erro por dia (últimos 30 dias)</h3>
          <HealthBarChart
            :points="trendPoints"
            :color="GREEN"
            test-id="chart-trend"
            :has-data="trendPoints.some((p) => p.value !== null)"
            label="Taxa de erro de importação de cada dia nos últimos 30 dias"
            :format="formatRate"
            empty-text="Nenhuma importação terminada nos últimos 30 dias"
            :label-every="5"
            column-label="Dia"
          />
        </div>
      </template>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import HealthBarChart from '@/components/admin/HealthBarChart.vue'
import { exportImportErrors, getImportBankDetail, getImportMetrics } from '@/services/adminService'
import { apiErrorStatus } from '@/lib/apiError'
import { downloadBlob } from '@/lib/download'
import { formatDateTimeBR, formatLocalDateBR } from '@/lib/date'
import { SELECT_CLASS } from '@/lib/formField'
import { BANK_LABELS } from '@/types/transactionImport'
import type { TransactionImportStatus } from '@/types/transactionImport'
import type {
  AdminBankCode,
  HealthStatus,
  ImportBankDetail,
  ImportErrorType,
  ImportMetrics,
  ImportMetricsFilters,
  ImportOutcome,
} from '@/types/admin'

// Story 10.2: taxa de erro de importação por banco; o 403 sobe para a view mostrar "Acesso restrito"
const emit = defineEmits<{ forbidden: [] }>()

// No admin os dois aparecem lado a lado: OTHER é um banco fora dos cinco, UNKNOWN falhou antes da detecção
const LABELS: Record<AdminBankCode, string> = { ...BANK_LABELS, OTHER: 'Outro banco', UNKNOWN: 'Não identificado' }

const ERROR_TYPE_LABELS: Record<ImportErrorType, string> = {
  FORMAT: 'Problema de formato',
  PARSING: 'Nenhuma transação lida',
  INTERRUPTED: 'Interrompida',
  UNEXPECTED: 'Erro inesperado',
  OTHER: 'Outro',
}

const STATUS_LABELS: Record<TransactionImportStatus, string> = {
  PROCESSING: 'Em processamento',
  REVIEW: 'Aguardando revisão',
  COMPLETED: 'Concluída',
  FAILED: 'Falhou',
  DISCARDED: 'Descartada',
}

const STATUS_STYLE: Record<HealthStatus, { label: string; badge: string }> = {
  GREEN: { label: 'Dentro da meta', badge: 'bg-green-100 text-green-800' },
  YELLOW: { label: 'Atenção', badge: 'bg-yellow-100 text-yellow-800' },
  RED: { label: 'Acima da meta', badge: 'bg-red-100 text-red-800' },
  UNKNOWN: { label: 'Sem dados', badge: 'bg-gray-100 text-gray-700' },
}

const GREEN = '#16a34a'
const RED = '#dc2626'

const percent = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 2 })

const form = reactive({ from: '', to: '', bank: '' as AdminBankCode | '', status: 'ALL' as ImportOutcome })
// Filtros aplicados (o form pode mudar sem "Filtrar"); sem datas, o backend usa os últimos 30 dias
const applied = ref<{ period: ImportMetricsFilters; status: ImportOutcome }>({ period: {}, status: 'ALL' })

const metrics = ref<ImportMetrics | null>(null)
const loading = ref(false)
const error = ref('')
const exporting = ref(false)
const exportError = ref('')
const selected = ref<AdminBankCode | null>(null)
const detail = ref<ImportBankDetail | null>(null)
const detailError = ref('')
let detailSeq = 0

function formatRate(value: number | null) {
  return value === null ? 'sem dados' : `${percent.format(value)} %`
}

const bankPoints = computed(() =>
  (metrics.value?.banks ?? []).map((b) => ({
    label: LABELS[b.bank],
    value: b.errorRate,
    color: b.status === 'RED' ? RED : GREEN,
  })),
)

const trendPoints = computed(() =>
  (detail.value?.trend ?? []).map((d) => ({
    label: formatLocalDateBR(d.date).slice(0, 5),
    value: d.errorRate,
    color: d.errorRate !== null && d.errorRate > (metrics.value?.target ?? 5) ? RED : GREEN,
  })),
)

function handle(err: unknown, message: string, target: typeof error) {
  if (apiErrorStatus(err) === 403) emit('forbidden')
  else if (apiErrorStatus(err) === 400) target.value = 'Filtro inválido: confira as datas (no máximo 366 dias).'
  else target.value = message
}

async function load() {
  if (loading.value) return
  loading.value = true
  try {
    metrics.value = await getImportMetrics(applied.value.period)
    form.from = metrics.value.from
    form.to = metrics.value.to
    error.value = ''
  } catch (err) {
    handle(err, 'Não foi possível carregar as importações.', error)
  } finally {
    loading.value = false
  }
}

async function loadDetail() {
  const bank = selected.value
  if (!bank) return
  // Só a resposta mais nova vale: outro banco ou outro "Filtrar" no meio da carga descarta esta
  const seq = ++detailSeq
  detail.value = null
  detailError.value = ''
  try {
    const result = await getImportBankDetail({ ...applied.value.period, bank, status: applied.value.status })
    // Um clique em outro banco durante a carga não pode receber o detalhe deste
    if (seq === detailSeq) detail.value = result
  } catch (err) {
    if (seq === detailSeq) handle(err, 'Não foi possível carregar o detalhe do banco.', detailError)
  }
}

function openDetail(bank: AdminBankCode) {
  selected.value = bank
  void loadDetail()
}

async function apply() {
  applied.value = {
    period: {
      ...(form.from ? { from: form.from } : {}),
      ...(form.to ? { to: form.to } : {}),
      ...(form.bank ? { bank: form.bank } : {}),
    },
    status: form.status,
  }
  // O banco filtrado exclui o detalhe aberto de outro banco
  if (form.bank && form.bank !== selected.value) {
    selected.value = null
    detail.value = null
  }
  await load()
  if (selected.value) void loadDetail()
}

async function exportCsv() {
  if (!metrics.value) return
  exporting.value = true
  exportError.value = ''
  try {
    const blob = await exportImportErrors({ ...applied.value.period, status: applied.value.status })
    downloadBlob(blob, `erros_importacao_${metrics.value.from}_${metrics.value.to}.csv`)
  } catch (err) {
    handle(err, 'Não foi possível exportar o CSV.', exportError)
  } finally {
    exporting.value = false
  }
}

onMounted(load)
</script>
