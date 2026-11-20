<template>
  <div class="space-y-4">
    <p
      v-if="data && !data.emailConfigured"
      role="status"
      data-testid="alerts-email-off"
      class="rounded-lg border border-yellow-200 bg-yellow-50 px-4 py-3 text-sm text-yellow-800"
    >
      E-mail não configurado: os alertas ficam só aqui{{ data.slackConfigured ? ' e no Slack' : '' }}.
    </p>

    <div v-if="!data && loading && !error" role="status" class="py-12 text-center text-sm text-gray-700">
      Carregando…
    </div>

    <div
      v-if="error"
      role="alert"
      data-testid="alerts-error"
      class="flex flex-col gap-2 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800 sm:flex-row sm:items-center sm:justify-between"
    >
      <span>{{ error }}</span>
      <button
        type="button"
        data-testid="alerts-retry"
        class="inline-flex min-h-[44px] items-center rounded-lg border border-red-300 px-4 py-2 font-medium hover:bg-red-100 disabled:opacity-50"
        :disabled="loading"
        @click="load"
      >
        Tentar novamente
      </button>
    </div>

    <p v-if="actionError" role="alert" class="text-sm text-red-800" data-testid="alerts-resolve-error">
      {{ actionError }}
    </p>

    <section v-if="data" aria-labelledby="alerts-heading" class="rounded-lg border border-gray-200 bg-white p-4">
      <h2 id="alerts-heading" class="text-base font-semibold text-gray-900">Histórico de alertas</h2>
      <p class="mb-2 text-xs text-gray-600">
        Os 100 mais recentes. A métrica é verificada a cada 5 minutos; um alerta por episódio.
      </p>
      <p v-if="!data.alerts.length" class="py-4 text-sm text-gray-700" data-testid="alerts-empty">
        Nenhum alerta até agora.
      </p>
      <div v-else class="overflow-x-auto">
        <table class="w-full text-left text-sm" data-testid="alerts-table">
          <thead class="text-gray-700">
            <tr>
              <th scope="col" class="py-2 pr-4 font-medium">Métrica</th>
              <th scope="col" class="py-2 pr-4 font-medium">Banco</th>
              <th scope="col" class="py-2 pr-4 font-medium">Valor</th>
              <th scope="col" class="py-2 pr-4 font-medium">Limite</th>
              <th scope="col" class="py-2 pr-4 font-medium">Hora</th>
              <th scope="col" class="py-2 pr-4 font-medium">Ação sugerida</th>
              <th scope="col" class="py-2 pr-4 font-medium">Aba</th>
              <th scope="col" class="py-2 font-medium">Estado</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="a in data.alerts"
              :key="a.id"
              class="border-t border-gray-100 align-top text-gray-900"
              :data-testid="`alert-${a.id}`"
            >
              <td class="py-2 pr-4">{{ a.metric }}</td>
              <td class="py-2 pr-4">{{ a.bankLabel ?? '—' }}</td>
              <td class="py-2 pr-4">{{ a.valueLabel }}</td>
              <td class="py-2 pr-4">{{ a.thresholdLabel }}</td>
              <td class="py-2 pr-4 whitespace-nowrap">{{ formatDateTimeBR(a.triggeredAt) }}</td>
              <td class="py-2 pr-4 text-xs text-gray-700">{{ a.action }}</td>
              <td class="py-2 pr-4">
                <RouterLink
                  :to="{ name: 'admin', query: linkQuery(a) }"
                  class="text-purple-700 underline hover:text-purple-900"
                  :data-testid="`alert-link-${a.id}`"
                >
                  {{ TAB_LABELS[a.tab] }}
                </RouterLink>
              </td>
              <td class="py-2">
                <template v-if="a.resolvedAt">
                  <span
                    class="rounded-full bg-green-100 px-2 py-0.5 text-xs font-medium text-green-800"
                    :data-testid="`alert-state-${a.id}`"
                  >
                    Resolvido em {{ formatDateTimeBR(a.resolvedAt) }}
                  </span>
                </template>
                <div v-else class="flex flex-col items-start gap-1">
                  <span
                    class="rounded-full bg-red-100 px-2 py-0.5 text-xs font-medium text-red-800"
                    :data-testid="`alert-state-${a.id}`"
                  >
                    Aberto
                  </span>
                  <button
                    type="button"
                    class="inline-flex min-h-[44px] items-center rounded-lg border border-gray-300 px-3 py-1 text-xs font-medium text-gray-700 hover:bg-gray-100 disabled:opacity-50"
                    :data-testid="`alert-resolve-${a.id}`"
                    :disabled="resolving !== null"
                    @click="resolve(a)"
                  >
                    {{ resolving === a.id ? 'Salvando…' : 'Marcar como resolvido' }}
                  </button>
                </div>
                <p v-if="a.normalizedAt" class="mt-1 text-xs text-gray-600">
                  Normalizou em {{ formatDateTimeBR(a.normalizedAt) }}
                </p>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { getAlerts, resolveAlert } from '@/services/adminService'
import { apiErrorDetail, apiErrorStatus } from '@/lib/apiError'
import { formatDateTimeBR } from '@/lib/date'
import type { AdminTab, SystemAlert, SystemAlerts } from '@/types/admin'

const emit = defineEmits<{ forbidden: []; count: [open: number] }>()

const TAB_LABELS: Record<AdminTab, string> = {
  health: 'Saúde do sistema',
  imports: 'Importações',
  ai: 'IA',
  users: 'Usuários',
  alerts: 'Alertas',
}

// Os mesmos parâmetros do link do e-mail: aba e, no alerta por banco, banco e dia (retro do Epic 10, A4)
function linkQuery(a: SystemAlert) {
  return Object.fromEntries(new URL(a.link, window.location.origin).searchParams)
}

const data = ref<SystemAlerts | null>(null)
const loading = ref(false)
const error = ref('')
const actionError = ref('')
const resolving = ref<number | null>(null)

async function load() {
  if (loading.value) return
  loading.value = true
  try {
    data.value = await getAlerts()
    error.value = ''
    emit('count', data.value.openCount)
  } catch (err) {
    if (apiErrorStatus(err) === 403) emit('forbidden')
    else error.value = 'Não foi possível carregar os alertas.'
  } finally {
    loading.value = false
  }
}

async function resolve(alert: SystemAlert) {
  if (resolving.value !== null || !data.value) return
  resolving.value = alert.id
  actionError.value = ''
  try {
    const updated = await resolveAlert(alert.id)
    const d = data.value
    d.alerts = d.alerts.map((a) => (a.id === updated.id ? updated : a))
    if (!alert.resolvedAt && updated.resolvedAt) d.openCount = Math.max(0, d.openCount - 1)
    emit('count', d.openCount)
  } catch (err) {
    if (apiErrorStatus(err) === 403) emit('forbidden')
    else actionError.value = apiErrorDetail(err, 'Não foi possível marcar o alerta como resolvido.')
  } finally {
    resolving.value = null
  }
}

onMounted(load)
</script>
