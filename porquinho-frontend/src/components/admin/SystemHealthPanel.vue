<template>
  <div class="space-y-6">
    <div v-if="!health && loading && !error" role="status" class="py-12 text-center text-sm text-gray-700">
      Carregando…
    </div>

    <div
      v-if="error"
      role="alert"
      data-testid="admin-error"
      class="flex flex-col gap-2 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800 sm:flex-row sm:items-center sm:justify-between"
    >
      <span>{{ error }}</span>
      <button
        type="button"
        data-testid="admin-retry"
        class="inline-flex min-h-[44px] items-center rounded-lg border border-red-300 px-4 py-2 font-medium hover:bg-red-100 disabled:opacity-50"
        :disabled="loading"
        @click="load"
      >
        Tentar novamente
      </button>
    </div>

    <template v-if="health">
      <p class="text-xs text-gray-600" data-testid="admin-updated">
        Atualizado em {{ formatDateTimeBR(health.generatedAt) }}. Atualiza a cada 30 segundos.
      </p>

      <section aria-label="Resumo" class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <div
          v-for="card in cards"
          :key="card.key"
          class="rounded-lg border bg-white p-4"
          :class="ADMIN_STATUS[card.status].border"
          :data-testid="`card-${card.key}`"
        >
          <div class="flex items-center justify-between gap-2">
            <h2 class="text-sm font-medium text-gray-700">{{ card.title }}</h2>
            <StatusBadge :status="card.status" :data-testid="`status-${card.key}`" />
          </div>
          <p class="mt-2 text-2xl font-semibold text-gray-900">{{ card.value }}</p>
          <p v-for="line in card.lines" :key="line" class="text-xs text-gray-600">{{ line }}</p>
        </div>
      </section>

      <section aria-labelledby="hourly-time-heading" class="rounded-lg border border-gray-200 bg-white p-4">
        <h2 id="hourly-time-heading" class="mb-2 text-base font-semibold text-gray-900">
          Tempo médio de resposta por hora (24 h)
        </h2>
        <HealthBarChart
          :points="hourlyTime"
          color="#1E40AF"
          test-id="chart-time"
          :has-data="hasHourlyData"
          label="Tempo médio de resposta de cada hora nas últimas 24 horas"
          :format="formatMs"
        />
      </section>

      <section aria-labelledby="hourly-errors-heading" class="rounded-lg border border-gray-200 bg-white p-4">
        <h2 id="hourly-errors-heading" class="mb-2 text-base font-semibold text-gray-900">
          Erros (5xx) por hora (24 h)
        </h2>
        <HealthBarChart
          :points="hourlyErrors"
          color="#B91C1C"
          test-id="chart-errors"
          :has-data="hasHourlyData"
          label="Erros 5xx de cada hora nas últimas 24 horas"
          :format="(v: number) => String(v)"
        />
      </section>

      <section aria-labelledby="endpoints-heading" class="rounded-lg border border-gray-200 bg-white p-4">
        <h2 id="endpoints-heading" class="text-base font-semibold text-gray-900">Tempo médio por endpoint</h2>
        <p class="mb-2 text-xs text-gray-600">Desde o último start do backend.</p>
        <p v-if="!health.endpoints.length" class="py-4 text-sm text-gray-700">Nenhuma requisição ainda.</p>
        <div v-else class="overflow-x-auto">
          <table class="w-full text-left text-sm" data-testid="endpoints-table">
            <thead class="text-gray-700">
              <tr>
                <th scope="col" class="py-2 pr-4 font-medium">Endpoint</th>
                <th scope="col" class="py-2 pr-4 text-right font-medium">Requisições</th>
                <th scope="col" class="py-2 pr-4 text-right font-medium">Tempo médio</th>
                <th scope="col" class="py-2 pr-4 text-right font-medium">Erros</th>
                <th scope="col" class="py-2 font-medium">Status</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="e in health.endpoints"
                :key="`${e.method} ${e.uri}`"
                class="border-t border-gray-100 text-gray-900"
              >
                <td class="py-2 pr-4 font-mono text-xs">{{ e.method }} {{ e.uri }}</td>
                <td class="py-2 pr-4 text-right">{{ e.count }}</td>
                <td class="py-2 pr-4 text-right">{{ formatMs(e.avgMs) }}</td>
                <td class="py-2 pr-4 text-right">{{ e.errors }}</td>
                <td class="py-2">
                  <StatusBadge :status="e.status" data-testid="endpoint-status" />
                </td>
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
import HealthBarChart from '@/components/admin/HealthBarChart.vue'
import StatusBadge from '@/components/admin/StatusBadge.vue'
import { usePolling } from '@/composables/usePolling'
import { getSystemHealth } from '@/services/adminService'
import { ADMIN_STATUS } from '@/lib/adminStatus'
import { apiErrorStatus } from '@/lib/apiError'
import { formatDateTimeBR } from '@/lib/date'
import type { SystemHealth } from '@/types/admin'

// Story 10.1: saúde do sistema; o 403 sobe para a view mostrar "Acesso restrito". `refresh` avisa a view a cada carga
// para ela atualizar junto a contagem de alertas abertos
const emit = defineEmits<{ forbidden: []; refresh: [] }>()

const REFRESH_MS = 30_000

const health = ref<SystemHealth | null>(null)
const loading = ref(false)
const error = ref('')
const { stop } = usePolling(() => void load(), REFRESH_MS)

// 1 casa, como o backend arredonda antes da cor (retro do Epic 10, A2)
const percent = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 1 })

function formatPercent(value: number | null) {
  return value === null ? 'sem dados' : `${percent.format(value)} %`
}

function formatMs(value: number) {
  // Arredonda antes de escolher a unidade: 999,6 ms vira "1 s", não "1000 ms"
  const ms = Math.round(value)
  return ms >= 1000 ? `${percent.format(ms / 1000)} s` : `${ms} ms`
}

function formatBytes(value: number | null) {
  if (value === null) return '—'
  return `${percent.format(value / 1024 / 1024)} MB`
}

function hourLabel(iso: string) {
  return new Date(iso).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })
}

const cards = computed(() => {
  const h = health.value
  if (!h) return []
  const redisLines = h.redis.up
    ? [
        `Hit rate: ${formatPercent(h.redis.hitRate)}`,
        `Memória: ${formatBytes(h.redis.usedMemoryBytes)}${h.redis.maxMemoryBytes ? ` de ${formatBytes(h.redis.maxMemoryBytes)}` : ''}`,
      ]
    : []
  return [
    {
      key: 'uptime',
      title: 'Uptime (30 dias)',
      status: h.uptime.status,
      value: formatPercent(h.uptime.last30d),
      lines: [`24 h: ${formatPercent(h.uptime.last24h)}`, `Meta: ${percent.format(h.uptime.target)} %`],
    },
    {
      key: 'errors',
      title: 'Taxa de erro (24 h)',
      status: h.errors.status,
      value: formatPercent(h.errors.errorRate24h),
      lines: [`Erros: 24 h: ${h.errors.last24h} · 7 dias: ${h.errors.last7d} · 30 dias: ${h.errors.last30d}`],
    },
    {
      key: 'response',
      title: 'Tempo médio (24 h)',
      status: h.responseTime.status,
      value: h.responseTime.avgMs24h === null ? 'sem dados' : formatMs(h.responseTime.avgMs24h),
      lines: [],
    },
    {
      key: 'sessions',
      title: 'Sessões ativas',
      status: h.sessions.status,
      value: String(h.sessions.active),
      lines: ['Com atividade nos últimos 30 min'],
    },
    {
      key: 'pool',
      title: 'Pool do banco',
      status: h.database.poolStatus,
      value: `${h.database.active} de ${h.database.max}`,
      lines: [`Ociosas: ${h.database.idle} · Esperando: ${h.database.waiting}`],
    },
    {
      key: 'slow-queries',
      title: 'Consultas lentas',
      status: h.database.slowQueriesStatus,
      value: String(h.database.slowQueries),
      lines: ['Ativas há mais de 1 s agora'],
    },
    {
      key: 'redis',
      title: 'Redis',
      status: h.redis.status,
      value: h.redis.up ? 'no ar' : 'fora do ar',
      lines: redisLines,
    },
  ]
})

const hourlyTime = computed(() =>
  (health.value?.hourly ?? []).map((p) => ({ label: hourLabel(p.hour), value: p.avgMs })),
)
const hasHourlyData = computed(() => (health.value?.hourly ?? []).some((p) => p.requests > 0))
const hourlyErrors = computed(() =>
  (health.value?.hourly ?? []).map((p) => ({ label: hourLabel(p.hour), value: p.errors })),
)

async function load() {
  // O intervalo não empilha requisições: uma resposta atrasada não sobrescreve dados mais novos
  if (loading.value) return
  loading.value = true
  emit('refresh')
  try {
    health.value = await getSystemHealth()
    error.value = ''
  } catch (err) {
    if (apiErrorStatus(err) === 403) {
      stop()
      emit('forbidden')
    } else {
      error.value = 'Não foi possível carregar a saúde do sistema.'
    }
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
