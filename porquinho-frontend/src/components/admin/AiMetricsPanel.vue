<template>
  <div class="space-y-6">
    <form
      class="grid gap-3 rounded-lg border border-gray-200 bg-white p-4 sm:grid-cols-2 lg:grid-cols-4 lg:items-end"
      aria-label="Filtros da precisão da IA"
      data-testid="ai-filters"
      @submit.prevent="apply"
    >
      <label class="text-sm text-gray-700">
        De
        <input v-model="form.from" type="date" :class="SELECT_CLASS" data-testid="ai-filter-from" />
      </label>
      <label class="text-sm text-gray-700">
        Até
        <input v-model="form.to" type="date" :class="SELECT_CLASS" data-testid="ai-filter-to" />
      </label>
      <label class="text-sm text-gray-700">
        Banco
        <select v-model="form.bank" :class="SELECT_CLASS" data-testid="ai-filter-bank">
          <option value="">Todos</option>
          <option v-for="(name, code) in LABELS" :key="code" :value="code">{{ name }}</option>
        </select>
      </label>
      <div class="flex gap-2">
        <button
          type="submit"
          data-testid="ai-filter-apply"
          class="inline-flex min-h-[44px] items-center rounded-lg bg-purple-600 px-4 py-2 text-sm font-medium text-white hover:bg-purple-700 disabled:opacity-50"
          :disabled="loading"
        >
          Filtrar
        </button>
        <button
          type="button"
          data-testid="ai-export"
          class="inline-flex min-h-[44px] items-center rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100 disabled:opacity-50"
          :disabled="exporting || !metrics"
          @click="exportCsv"
        >
          Exportar CSV
        </button>
      </div>
      <p v-if="exportError" role="alert" class="text-sm text-red-800 sm:col-span-2 lg:col-span-4" data-testid="ai-export-error">
        {{ exportError }}
      </p>
    </form>

    <div v-if="!metrics && loading && !error" role="status" class="py-12 text-center text-sm text-gray-700">
      Carregando…
    </div>

    <div
      v-if="error"
      role="alert"
      data-testid="ai-error"
      class="flex flex-col gap-2 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800 sm:flex-row sm:items-center sm:justify-between"
    >
      <span>{{ error }}</span>
      <button
        type="button"
        data-testid="ai-retry"
        class="inline-flex min-h-[44px] items-center rounded-lg border border-red-300 px-4 py-2 font-medium hover:bg-red-100 disabled:opacity-50"
        :disabled="loading"
        @click="load"
      >
        Tentar novamente
      </button>
    </div>

    <template v-if="metrics">
      <p class="text-sm text-gray-700" data-testid="ai-target">
        Precisão alvo: &gt; {{ percent.format(metrics.target) }} % por banco ·
        {{ formatLocalDateBR(metrics.from) }} a {{ formatLocalDateBR(metrics.to) }}
      </p>

      <section aria-label="Resumo da IA" class="grid gap-4 sm:grid-cols-2">
        <div class="rounded-lg border bg-white p-4" :class="ADMIN_STATUS[metrics.overall.status].border" data-testid="ai-overall">
          <div class="flex items-center justify-between gap-2">
            <h2 class="text-sm font-medium text-gray-700">Precisão geral</h2>
            <StatusBadge :status="metrics.overall.status" />
          </div>
          <p class="mt-2 text-2xl font-semibold text-gray-900">{{ formatAccuracy(metrics.overall.accuracy) }}</p>
          <p class="text-xs text-gray-600">
            {{ metrics.overall.classifications }} classificações · {{ metrics.overall.corrections }} correções
          </p>
        </div>
        <div class="rounded-lg border border-gray-200 bg-white p-4" data-testid="ai-month-change">
          <h2 class="text-sm font-medium text-gray-700">Variação do mês</h2>
          <p class="mt-2 text-base font-semibold text-gray-900">{{ monthChangeText }}</p>
          <p class="text-xs text-gray-600">Mês atual comparado com o anterior, todos os bancos.</p>
        </div>
      </section>

      <section aria-labelledby="ai-table-heading" class="rounded-lg border border-gray-200 bg-white p-4">
        <h2 id="ai-table-heading" class="mb-2 text-base font-semibold text-gray-900">Precisão por banco</h2>
        <p v-if="!metrics.banks.length" class="py-4 text-sm text-gray-700" data-testid="ai-empty">
          Nenhuma classificação no período
        </p>
        <div v-else class="overflow-x-auto">
          <table class="w-full text-left text-sm" data-testid="ai-table">
            <thead class="text-gray-700">
              <tr>
                <th scope="col" class="py-2 pr-4 font-medium">Banco</th>
                <th scope="col" class="py-2 pr-4 text-right font-medium">Classificações</th>
                <th scope="col" class="py-2 pr-4 text-right font-medium">Correções</th>
                <th scope="col" class="py-2 pr-4 text-right font-medium">Precisão (%)</th>
                <th scope="col" class="py-2 font-medium">Status</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="b in metrics.banks"
                :key="b.bank"
                class="border-t border-gray-100"
                :class="ROW_CLASS[b.status]"
                :data-testid="`ai-row-${b.bank}`"
              >
                <td class="py-2 pr-4">
                  <button
                    type="button"
                    class="min-h-[44px] text-left font-medium text-purple-700 underline-offset-2 hover:underline"
                    :aria-expanded="selected === b.bank"
                    :aria-controls="selected === b.bank ? 'ai-bank-detail' : undefined"
                    :data-testid="`ai-open-${b.bank}`"
                    @click="openDetail(b.bank)"
                  >
                    {{ LABELS[b.bank] }}
                  </button>
                </td>
                <td class="py-2 pr-4 text-right">{{ b.classifications }}</td>
                <td class="py-2 pr-4 text-right">{{ b.corrections }}</td>
                <td class="py-2 pr-4 text-right" data-testid="ai-accuracy">{{ formatAccuracy(b.accuracy) }}</td>
                <td class="py-2">
                  <StatusBadge :status="b.status" data-testid="ai-status" />
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section aria-labelledby="ai-chart-heading" class="rounded-lg border border-gray-200 bg-white p-4">
        <h2 id="ai-chart-heading" class="mb-2 text-base font-semibold text-gray-900">Precisão por banco (%)</h2>
        <HealthBarChart
          :points="bankPoints"
          :color="COLORS.GREEN"
          test-id="ai-chart-banks"
          :has-data="bankPoints.some((p) => p.value !== null)"
          label="Precisão da IA de cada banco no período"
          :format="formatAccuracy"
          empty-text="Nenhuma classificação no período"
          :label-every="1"
          column-label="Banco"
          :max="100"
        />
      </section>

      <section aria-labelledby="ai-trend-heading" class="rounded-lg border border-gray-200 bg-white p-4">
        <h2 id="ai-trend-heading" class="mb-2 text-base font-semibold text-gray-900">Precisão por mês (últimos 6 meses)</h2>
        <AccuracyLineChart
          :months="metrics.months.map(monthLabel)"
          :series="trendSeries"
          test-id="ai-chart-trend"
          label="Precisão da IA de cada banco em cada um dos últimos 6 meses"
          :format="formatAccuracy"
        />
      </section>
    </template>

    <section
      v-if="selected"
      id="ai-bank-detail"
      aria-labelledby="ai-detail-heading"
      class="space-y-4 rounded-lg border border-gray-200 bg-white p-4"
      data-testid="ai-detail"
    >
      <h2 id="ai-detail-heading" class="text-base font-semibold text-gray-900">{{ LABELS[selected] }}</h2>

      <div v-if="detailError" role="alert" class="text-sm text-red-800" data-testid="ai-detail-error">{{ detailError }}</div>
      <p v-else-if="!detail" role="status" class="text-sm text-gray-700">Carregando…</p>

      <template v-if="detail">
        <div v-for="g in groups" :key="g.key">
          <h3 class="mb-1 text-sm font-semibold text-gray-900">{{ g.title }}</h3>
          <p v-if="!g.rows.length" class="text-sm text-gray-700">Nenhuma classificação no período.</p>
          <div v-else class="overflow-x-auto">
            <table class="w-full text-left text-sm" :data-testid="`ai-detail-${g.key}`">
              <thead class="text-gray-700">
                <tr>
                  <th scope="col" class="py-2 pr-4 font-medium">{{ g.column }}</th>
                  <th scope="col" class="py-2 pr-4 text-right font-medium">Classificações</th>
                  <th scope="col" class="py-2 pr-4 text-right font-medium">Correções</th>
                  <th scope="col" class="py-2 text-right font-medium">Precisão (%)</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="r in g.rows" :key="r.key" class="border-t border-gray-100" :class="ROW_CLASS[r.status]">
                  <td class="py-2 pr-4">{{ r.label }}</td>
                  <td class="py-2 pr-4 text-right">{{ r.classifications }}</td>
                  <td class="py-2 pr-4 text-right">{{ r.corrections }}</td>
                  <td class="py-2 text-right">{{ formatAccuracy(r.accuracy) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <div>
          <h3 class="text-sm font-semibold text-gray-900">Padrões usados por vários usuários</h3>
          <p class="mb-1 text-xs text-gray-600">Valem para todos os bancos; só entram padrões de pelo menos 3 usuários.</p>
          <p v-if="!detail.patterns.length" class="text-sm text-gray-700">Nenhum padrão compartilhado.</p>
          <ul v-else class="text-sm text-gray-900" data-testid="ai-detail-patterns">
            <li v-for="p in detail.patterns" :key="p.pattern">
              <span class="font-mono">{{ p.pattern }}</span> · {{ p.users }} usuários · {{ p.usage }} usos
            </li>
          </ul>
        </div>

        <div>
          <h3 class="mb-1 text-sm font-semibold text-gray-900">Sugestões</h3>
          <ul class="list-disc pl-5 text-sm text-gray-900" data-testid="ai-detail-suggestions">
            <li v-for="s in detail.suggestions" :key="s">{{ s }}</li>
          </ul>
        </div>
      </template>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import AccuracyLineChart from '@/components/admin/AccuracyLineChart.vue'
import HealthBarChart from '@/components/admin/HealthBarChart.vue'
import StatusBadge from '@/components/admin/StatusBadge.vue'
import { useExportDialog } from '@/composables/useExportDialog'
import { useSequencedList } from '@/composables/useSequencedList'
import { exportAiMetrics, getAiBankDetail, getAiMetrics } from '@/services/adminService'
import { ADMIN_STATUS } from '@/lib/adminStatus'
import { apiErrorStatus } from '@/lib/apiError'
import { downloadBlob } from '@/lib/download'
import { formatLocalDateBR } from '@/lib/date'
import { SELECT_CLASS } from '@/lib/formField'
import { BANK_LABELS } from '@/types/transactionImport'
import type {
  AdminBankCode,
  AiAccuracy,
  AiBankDetail,
  AiConfidenceBand,
  AiMetrics,
  AiTransactionType,
  HealthStatus,
  ImportMetricsFilters,
} from '@/types/admin'

// Story 10.3: precisão da IA por banco; o 403 sobe para a view mostrar "Acesso restrito"
const emit = defineEmits<{ forbidden: [] }>()
// Filtros iniciais vindos da URL (link do alerta por banco)
const props = defineProps<{ initial?: ImportMetricsFilters }>()

const LABELS: Record<AdminBankCode, string> = { ...BANK_LABELS, OTHER: 'Outro banco', UNKNOWN: 'Não identificado' }

// Cor fixa por banco nas linhas mensais, sem vermelho, amarelo ou verde: a linha não pode parecer um status
const BANK_COLORS: Record<AdminBankCode, string> = {
  NUBANK: '#7c3aed',
  BANCO_DO_BRASIL: '#1e3a8a',
  ITAU: '#ea580c',
  BRADESCO: '#db2777',
  CAIXA: '#2563eb',
  OTHER: '#92400e',
  UNKNOWN: '#475569',
}

// Traço fixo por banco (WCAG 1.4.1): o mesmo banco tem o mesmo traço com qualquer filtro
const BANK_DASHES: Record<AdminBankCode, string> = {
  NUBANK: 'none',
  BANCO_DO_BRASIL: '6 3',
  ITAU: '2 3',
  BRADESCO: '10 3 2 3',
  CAIXA: '1 5',
  OTHER: '12 6',
  UNKNOWN: '6 2 2 2 2 2',
}

const TYPE_LABELS: Record<AiTransactionType, string> = {
  PIX: 'Pix',
  INSTALLMENT: 'Parcelas',
  DEBIT: 'Débitos',
  CREDIT: 'Créditos',
}

const BAND_LABELS: Record<AiConfidenceBand, string> = {
  '0-49': '0 a 49',
  '50-79': '50 a 79',
  '80-100': '80 a 100',
}

// Abaixo da meta fica destacado: amarelo de 75 a 85 %, vermelho abaixo de 75 %
const ROW_CLASS: Record<HealthStatus, string> = {
  GREEN: 'text-gray-900',
  YELLOW: 'bg-yellow-50 text-yellow-900',
  RED: 'bg-red-50 text-red-700',
  UNKNOWN: 'text-gray-900',
}

const COLORS = { GREEN: '#047857', YELLOW: '#B45309', RED: '#B91C1C', UNKNOWN: '#94A3B8' }

const percent = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 1 })

const initial = props.initial ?? {}
const form = reactive({ from: initial.from ?? '', to: initial.to ?? '', bank: (initial.bank ?? '') as AdminBankCode | '' })
// Filtros aplicados (o form pode mudar sem "Filtrar"); sem datas, o backend usa os últimos 30 dias
const applied = ref<ImportMetricsFilters>({ ...initial })

const metrics = ref<AiMetrics | null>(null)
const loading = ref(false)
const error = ref('')
const exportError = ref('')
const { exporting, run: runExport } = useExportDialog()
const selected = ref<AdminBankCode | null>(null)
const detailError = ref('')
// Só a resposta mais nova vale: outro banco ou outro "Filtrar" no meio da carga descarta esta
const detailList = useSequencedList<AiBankDetail>(
  async () => [await getAiBankDetail({ ...applied.value, bank: selected.value! })],
  (err) => handle(err, 'Não foi possível carregar o detalhe do banco.', detailError),
)
const detail = computed(() => detailList.items.value[0] ?? null)

function formatAccuracy(value: number | null) {
  return value === null ? 'sem dados' : `${percent.format(value)} %`
}

// 'AAAA-MM' vira data UTC: formatar em UTC evita cair no mês anterior
const monthFormat = new Intl.DateTimeFormat('pt-BR', { month: '2-digit', year: '2-digit', timeZone: 'UTC' })
function monthLabel(month: string) {
  return monthFormat.format(new Date(`${month}-01`))
}

const monthChangeText = computed(() => {
  const delta = metrics.value?.monthChange.delta ?? null
  if (delta === null) return 'Sem comparação'
  const points = percent.format(Math.abs(delta))
  if (Math.abs(delta) < 0.05) return 'A precisão ficou estável neste mês'
  return `A precisão ${delta > 0 ? 'subiu' : 'caiu'} ${points} p.p. neste mês`
})

const bankPoints = computed(() =>
  (metrics.value?.banks ?? []).map((b) => ({ label: LABELS[b.bank], value: b.accuracy, color: COLORS[b.status] })),
)

const trendSeries = computed(() =>
  (metrics.value?.trend ?? []).map((s) => ({ label: LABELS[s.bank], color: BANK_COLORS[s.bank], dash: BANK_DASHES[s.bank], values: s.accuracy })),
)

type Row = AiAccuracy & { key: string; label: string }
const groups = computed<{ key: string; title: string; column: string; rows: Row[] }[]>(() => {
  const d = detail.value
  if (!d) return []
  return [
    {
      key: 'types',
      title: 'Tipos de transação mais errados',
      column: 'Tipo',
      rows: d.types.map((t) => ({ ...t, label: TYPE_LABELS[t.key] })),
    },
    {
      key: 'confidence',
      title: 'Por faixa de confiança da IA',
      column: 'Confiança',
      rows: d.confidence.map((c) => ({ ...c, label: BAND_LABELS[c.key] })),
    },
  ]
})

function handle(err: unknown, message: string, target: typeof error) {
  if (apiErrorStatus(err) === 403) emit('forbidden')
  else if (apiErrorStatus(err) === 400) target.value = 'Filtro inválido: confira as datas (no máximo 366 dias).'
  else target.value = message
}

async function load() {
  if (loading.value) return
  loading.value = true
  try {
    metrics.value = await getAiMetrics(applied.value)
    form.from = metrics.value.from
    form.to = metrics.value.to
    error.value = ''
  } catch (err) {
    handle(err, 'Não foi possível carregar a precisão da IA.', error)
  } finally {
    loading.value = false
  }
}

async function loadDetail() {
  if (!selected.value) return
  detailList.items.value = []
  detailError.value = ''
  await detailList.load()
}

function openDetail(bank: AdminBankCode) {
  selected.value = bank
  void loadDetail()
}

async function apply() {
  applied.value = {
    ...(form.from ? { from: form.from } : {}),
    ...(form.to ? { to: form.to } : {}),
    ...(form.bank ? { bank: form.bank } : {}),
  }
  // O banco filtrado exclui o detalhe aberto de outro banco
  if (form.bank && form.bank !== selected.value) {
    selected.value = null
    detailList.items.value = []
  }
  await load()
  if (selected.value) void loadDetail()
}

async function exportCsv() {
  const m = metrics.value
  if (!m) return
  exportError.value = ''
  await runExport(
    async () => downloadBlob(await exportAiMetrics(applied.value), `precisao_ia_${m.from}_${m.to}.csv`),
    (err) => handle(err, 'Não foi possível exportar o CSV.', exportError),
  )
}

onMounted(load)
</script>
