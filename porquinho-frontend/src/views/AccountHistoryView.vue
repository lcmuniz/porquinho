<template>
  <main class="min-h-screen bg-gray-50 px-4 py-8">
    <div class="mx-auto max-w-3xl space-y-6">
      <div class="flex items-start justify-between gap-4">
        <div class="min-w-0">
          <h1 class="text-2xl font-semibold text-gray-900" data-testid="history-heading">
            Histórico de saldo<template v-if="history">
              — {{ history.account.accountName }}</template
            >
          </h1>
          <p
            v-if="history"
            class="mt-1 text-sm text-gray-600"
            data-testid="history-current-balance"
          >
            Saldo atual:
            <span
              class="font-semibold"
              :class="history.account.currentBalance < 0 ? 'text-red-600' : 'text-gray-900'"
              >{{ formatBRL(history.account.currentBalance) }}</span
            >
          </p>
        </div>
        <RouterLink
          to="/contas"
          data-testid="back-to-accounts"
          class="inline-flex min-h-[44px] shrink-0 items-center rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100"
        >
          Voltar para contas
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
          @click="selectRange(option.value)"
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
        Esta conta não existe mais.
        <RouterLink to="/contas" class="font-medium underline">Voltar para contas</RouterLink>
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
        <Button
          variant="outline"
          class="min-h-[44px] shrink-0"
          data-testid="history-retry"
          @click="load"
        >
          Tentar de novo
        </Button>
      </div>

      <template v-else-if="history">
        <p
          v-if="history.points.length === 0"
          class="rounded-lg border border-gray-200 bg-white p-6 text-center text-gray-600"
          data-testid="history-empty"
        >
          Nenhuma movimentação neste período
        </p>

        <template v-else>
          <section class="rounded-lg border border-gray-200 bg-white p-4">
            <BalanceChart :points="history.points" :period-start="history.periodStart" />
          </section>

          <section aria-labelledby="history-table-heading" class="space-y-2">
            <h2 id="history-table-heading" class="text-sm font-medium text-gray-900">
              Movimentações
            </h2>
            <div class="overflow-x-auto rounded-lg border border-gray-200 bg-white">
              <table class="w-full text-left text-sm" data-testid="history-table">
                <thead class="border-b border-gray-200 bg-gray-50 text-gray-600">
                  <tr>
                    <th scope="col" class="px-4 py-2 font-medium">Data</th>
                    <th scope="col" class="px-4 py-2 font-medium">Evento</th>
                    <th scope="col" class="px-4 py-2 text-right font-medium">Saldo</th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="(point, i) in newestFirst"
                    :key="i"
                    class="border-b border-gray-100 last:border-0"
                    data-testid="history-row"
                  >
                    <td class="px-4 py-2 whitespace-nowrap text-gray-700">
                      {{ formatDateBR(point.date) }}
                    </td>
                    <td class="px-4 py-2 text-gray-900">
                      {{ BALANCE_EVENT_LABEL[point.event] ?? point.event }}
                    </td>
                    <td
                      class="px-4 py-2 text-right font-semibold whitespace-nowrap"
                      :class="point.balance < 0 ? 'text-red-600' : 'text-gray-900'"
                      data-testid="history-row-balance"
                    >
                      {{ formatBRL(point.balance) }}
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
import BalanceChart from '@/components/accounts/BalanceChart.vue'
import { Button } from '@/components/ui/button'
import { BALANCE_EVENT_LABEL } from '@/lib/balanceEvents'
import { formatDateBR } from '@/lib/date'
import { formatBRL } from '@/lib/money'
import { getBalanceHistory } from '@/services/accountService'
import type { BalanceHistory, BalanceRange } from '@/types/account'

/** Story 4.7: histórico de saldo de uma conta — seletor de período, gráfico e a tabela com os mesmos dados. */
const RANGE_OPTIONS: { value: BalanceRange; label: string }[] = [
  { value: '30d', label: '30 dias' },
  { value: '3m', label: '3 meses' },
  { value: '6m', label: '6 meses' },
  { value: '1y', label: '1 ano' },
  { value: 'all', label: 'Tudo' },
]

interface ApiError {
  response?: { status?: number }
}

const route = useRoute()
const range = ref<BalanceRange>('3m')
const history = ref<BalanceHistory | null>(null)
const loading = ref(true)
const loadFailed = ref(false)
const notFound = ref(false)

const newestFirst = computed(() => (history.value ? [...history.value.points].reverse() : []))

/** Sequência das cargas: só a resposta da carga mais recente é aplicada (troca rápida de período). */
let request = 0

async function load() {
  const current = ++request
  loading.value = true
  loadFailed.value = false
  notFound.value = false
  try {
    const data = await getBalanceHistory(String(route.params.id), range.value)
    if (current !== request) return
    history.value = data
  } catch (err) {
    if (current !== request) return
    // 400 só vem de um id malformado na URL: para o usuário, é uma conta que não existe
    const status = (err as ApiError)?.response?.status
    if (status === 404 || status === 400) notFound.value = true
    else loadFailed.value = true
  } finally {
    if (current === request) loading.value = false
  }
}

function selectRange(value: BalanceRange) {
  if (value === range.value && !loadFailed.value) return
  range.value = value
  void load()
}

onMounted(load)

// Outra conta na mesma página (ex.: voltar/avançar no navegador): recarrega do zero
watch(
  () => route.params.id,
  () => {
    history.value = null
    void load()
  },
)
</script>
