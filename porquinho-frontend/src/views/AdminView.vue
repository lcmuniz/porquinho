<template>
  <main class="px-4 py-6 md:px-8 md:py-8">
    <div class="mx-auto max-w-6xl space-y-6">
      <div class="flex items-center justify-between gap-4">
        <h1 class="text-2xl font-bold text-gray-900 md:text-[28px]">Admin</h1>
        <RouterLink
          to="/dashboard"
          class="inline-flex min-h-[44px] items-center rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100"
        >
          Voltar
        </RouterLink>
      </div>

      <div
        v-if="forbidden"
        role="alert"
        data-testid="admin-forbidden"
        class="rounded-lg border border-red-200 bg-red-50 px-4 py-6 text-center text-red-800"
      >
        <p class="font-semibold">Acesso restrito</p>
        <p class="text-sm">Esta área é só para administradores.</p>
      </div>

      <template v-else>
        <div
          v-if="openAlerts > 0 && tab !== 'alerts'"
          role="alert"
          data-testid="admin-open-alerts"
          class="flex flex-col gap-2 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800 sm:flex-row sm:items-center sm:justify-between"
        >
          <span>
            {{ openAlerts === 1 ? 'Há 1 alerta aberto.' : `Há ${openAlerts} alertas abertos.` }}
          </span>
          <button
            type="button"
            data-testid="admin-open-alerts-go"
            class="inline-flex min-h-[44px] items-center rounded-lg border border-red-300 px-4 py-2 font-medium hover:bg-red-100"
            @click="selectTab('alerts')"
          >
            Ver alertas
          </button>
        </div>

        <nav aria-label="Seções do admin" class="flex gap-2 overflow-x-auto border-b border-gray-200">
          <button
            v-for="t in TABS"
            :key="t.key"
            type="button"
            :aria-current="tab === t.key ? 'page' : undefined"
            :data-testid="`tab-${t.key}`"
            class="min-h-[44px] shrink-0 border-b-2 px-3 py-2 text-sm font-medium whitespace-nowrap"
            :class="tab === t.key ? 'border-purple-600 text-purple-700' : 'border-transparent text-gray-700 hover:text-gray-900'"
            @click="selectTab(t.key)"
          >
            {{ t.label }}<template v-if="t.key === 'alerts' && openAlerts > 0"> ({{ openAlerts }})</template>
          </button>
        </nav>

        <div v-if="tab === 'imports'">
          <ImportMetricsPanel :initial="initialFilters" @forbidden="onForbidden" />
        </div>

        <div v-else-if="tab === 'ai'">
          <AiMetricsPanel :initial="initialFilters" @forbidden="onForbidden" />
        </div>

        <div v-else-if="tab === 'users'">
          <UserMetricsPanel @forbidden="onForbidden" />
        </div>

        <div v-else-if="tab === 'alerts'">
          <AlertsPanel @forbidden="onForbidden" @count="openAlerts = $event" />
        </div>

        <SystemHealthPanel v-else @forbidden="onForbidden" @refresh="loadAlertCount" />
      </template>
    </div>
  </main>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import AlertsPanel from '@/components/admin/AlertsPanel.vue'
import AiMetricsPanel from '@/components/admin/AiMetricsPanel.vue'
import ImportMetricsPanel from '@/components/admin/ImportMetricsPanel.vue'
import SystemHealthPanel from '@/components/admin/SystemHealthPanel.vue'
import UserMetricsPanel from '@/components/admin/UserMetricsPanel.vue'
import { usePolling } from '@/composables/usePolling'
import { getAlerts } from '@/services/adminService'
import { apiErrorStatus } from '@/lib/apiError'
import { filtersFromQuery } from '@/lib/adminFilters'
import type { AdminTab } from '@/types/admin'

const REFRESH_MS = 30_000

type Tab = AdminTab
const TABS: { key: Tab; label: string }[] = [
  { key: 'health', label: 'Saúde do sistema' },
  { key: 'imports', label: 'Importações' },
  { key: 'ai', label: 'IA' },
  { key: 'users', label: 'Usuários' },
  { key: 'alerts', label: 'Alertas' },
]

const route = useRoute()
const router = useRouter()

// Story 10.5: ?tab= abre a aba (link do e-mail de alerta); valor desconhecido cai na saúde
function tabFromQuery(): Tab {
  const q = route.query.tab
  return TABS.some((t) => t.key === q) ? (q as Tab) : 'health'
}
const tab = ref<Tab>(tabFromQuery())
// Banco e período da URL (link do alerta por banco); a aba os lê ao montar
const initialFilters = ref(filtersFromQuery(route.query))
const openAlerts = ref(0)
const forbidden = ref(false)

// A contagem do aviso do topo a cada 30 s fora da saúde; na aba de saúde ela vem junto com cada carga do painel
// (@refresh), inclusive ao voltar a aba do navegador
const { stop } = usePolling(
  () => {
    if (tab.value !== 'health') void loadAlertCount()
  },
  REFRESH_MS,
  { refreshOnVisible: false },
)

// O aviso do topo: contagem dos abertos, junto com a saúde (e o painel de alertas ao carregar ou resolver)
async function loadAlertCount() {
  try {
    openAlerts.value = (await getAlerts()).openCount
  } catch (err) {
    if (apiErrorStatus(err) === 403) onForbidden()
  }
}

function onForbidden() {
  forbidden.value = true
  stop()
}

// O poll de 30 s da saúde só roda com a aba dela aberta (o painel desmonta fora dela); ao voltar, carrega na hora
function selectTab(next: Tab) {
  if (tab.value === next) return
  // Banco e período só valem para a aba que o link abriu, não para a troca à mão
  initialFilters.value = route.query.tab === next ? filtersFromQuery(route.query) : {}
  tab.value = next
  // Trocar de aba à mão descarta o banco e o período do link do alerta
  if (route.query.tab !== next) void router.replace({ query: { tab: next } })
}

// Um link interno para /admin?tab=... (ex.: a coluna Aba dos alertas) troca a aba sem remontar a tela
watch(
  () => route.query.tab,
  () => selectTab(tabFromQuery()),
)

onMounted(() => {
  if (tab.value !== 'health') void loadAlertCount()
})
</script>
