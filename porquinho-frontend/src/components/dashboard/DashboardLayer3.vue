<template>
  <div class="mt-4 space-y-4">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <!-- Grupo segmentado: rádios nativos (setas trocam a opção, leitor de tela anuncia "1 de 2") -->
      <fieldset class="inline-flex rounded-[10px] border border-gray-300 bg-white p-1" data-testid="details-view">
        <legend class="sr-only">O que mostrar</legend>
        <label
          v-for="option in OPTIONS"
          :key="option.value"
          class="inline-flex min-h-[44px] cursor-pointer items-center rounded-lg px-4 text-sm font-semibold text-gray-700 has-[:checked]:bg-purple-700 has-[:checked]:text-white has-[:focus-visible]:ring-[3px] has-[:focus-visible]:ring-ring/50"
        >
          <input
            v-model="view"
            type="radio"
            name="details-view"
            class="sr-only"
            :value="option.value"
            :data-testid="`details-view-${option.value}`"
          />
          {{ option.label }}
          <span v-if="option.value === 'uncategorized' && uncategorized?.length" class="ml-1.5 tabular-nums">
            ({{ uncategorized.length }})
          </span>
        </label>
      </fieldset>
      <RouterLink
        :to="view === 'uncategorized' ? '/transacoes?filtro=sem-categoria' : '/transacoes'"
        class="inline-flex min-h-[44px] items-center text-sm font-medium text-purple-700 underline hover:text-purple-800"
        data-testid="details-all"
      >
        {{ view === 'uncategorized' ? 'Ver as sem categoria em Transações' : 'Ver todas as transações' }}
      </RouterLink>
    </div>

    <FeedbackBanner :message="successMessage" testid="details-success" @pause="pauseSuccess" @resume="resumeSuccess" />

    <div
      v-if="error"
      role="alert"
      class="flex flex-col gap-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3 sm:flex-row sm:items-center sm:justify-between"
      data-testid="details-error"
    >
      <p class="text-sm text-red-800">Não foi possível carregar as transações.</p>
      <Button variant="outline" class="min-h-[44px]" @click="load">Tentar de novo</Button>
    </div>
    <p v-else-if="!rows" role="status" data-testid="details-loading" class="text-sm text-gray-600">Carregando…</p>
    <p v-else-if="rows.length === 0" class="text-sm text-gray-600" data-testid="details-empty">
      {{ view === 'uncategorized' ? 'Tudo classificado neste mês.' : 'Nenhum gasto neste mês.' }}
    </p>
    <ul v-else class="divide-y divide-gray-200 rounded-lg border border-gray-200 bg-white" data-testid="details-list">
      <li
        v-for="row in rows"
        :key="row.id"
        class="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-4 gap-y-2 px-4 py-3"
        :class="{ 'sm:grid-cols-[minmax(0,1fr)_auto_auto]': view === 'uncategorized' }"
        :data-testid="`details-row-${row.id}`"
      >
        <div class="min-w-0">
          <p class="break-words font-medium text-gray-900">{{ row.description }}</p>
          <p class="text-sm text-gray-600">
            {{ formatLocalDateBR(row.date) }} · {{ targetName(row, accounts, cards) }}
            <template v-if="view === 'largest'"> · {{ categoryLabel(row) }}</template>
          </p>
        </div>
        <span class="font-semibold tabular-nums text-gray-900">{{ formatSignedBRL(signedAmount(row)) }}</span>
        <Button
          v-if="view === 'uncategorized'"
          variant="outline"
          class="col-span-2 min-h-[44px] justify-self-start sm:col-span-1"
          :aria-label="`Classificar ${row.description}`"
          :data-testid="`details-classify-${row.id}`"
          @click="openClassify(row)"
        >
          Classificar
        </Button>
      </li>
    </ul>

    <ClassifyDialog
      v-model:open="classifyOpen"
      :row="current"
      :categories="categories"
      :options-loading="optionsLoading"
      @saved="onSaved"
      @edit-all="editAll"
      @close-auto-focus="returnFocus"
    />
    <TransactionFormDialog
      v-model:open="editOpen"
      :row="current"
      :accounts="accounts"
      :cards="cards"
      :categories="categories"
      :options-loading="optionsLoading"
      :options-failed="optionsFailed"
      @saved="onSaved"
      @stale="load"
      @close-auto-focus="returnFocus"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { useFeedback } from '@/composables/useFeedback'
import FeedbackBanner from '@/components/common/FeedbackBanner.vue'
import { Button } from '@/components/ui/button'
import ClassifyDialog from '@/components/dashboard/ClassifyDialog.vue'
import TransactionFormDialog from '@/components/transactions/TransactionFormDialog.vue'
import { formatLocalDateBR } from '@/lib/date'
import { formatSignedBRL } from '@/lib/money'
import { categoryLabel, signedAmount, targetName } from '@/lib/transactionLabels'
import { listAccounts } from '@/services/accountService'
import { listCards } from '@/services/cardService'
import { listCategories } from '@/services/categoryService'
import { getDrilldown, getLargestSpent } from '@/services/dashboardService'
import type { Account } from '@/types/account'
import type { Category } from '@/types/category'
import type { CreditCard } from '@/types/creditCard'
import type { Transaction } from '@/types/transaction'

/**
 * Camada 3 (pós-critique 20/11/2026): em vez de repetir /transacoes, mostra os 10 maiores gastos do mês ou as saídas
 * do mês sem categoria, com "Classificar" abrindo um diálogo só com a categoria ("Editar tudo" abre o de /transacoes). Abre em "Sem categoria" se houver
 * alguma; senão em "Maiores gastos".
 */
const emit = defineEmits<{ 'transaction-saved': []; loaded: [] }>()

type View = 'uncategorized' | 'largest'
const OPTIONS: { value: View; label: string }[] = [
  { value: 'largest', label: 'Maiores gastos' },
  { value: 'uncategorized', label: 'Sem categoria' },
]

const view = ref<View>('largest')
const largest = ref<Transaction[] | null>(null)
const uncategorized = ref<Transaction[] | null>(null)
const error = ref(false)
const rows = computed(() => (view.value === 'largest' ? largest.value : uncategorized.value))

const accounts = ref<Account[]>([])
const cards = ref<CreditCard[]>([])
const categories = ref<Category[]>([])
const optionsLoading = ref(true)
const optionsFailed = ref(false)

const { successMessage, setSuccess, pauseSuccess, resumeSuccess } = useFeedback()
const current = ref<Transaction | null>(null)
const editOpen = ref(false)
const classifyOpen = ref(false)

// A lista padrão é escolhida só na primeira carga que der certo; depois vale a escolha do usuário
let defaulted = false

async function load() {
  error.value = false
  try {
    const [top, loose] = await Promise.all([getLargestSpent(), getDrilldown(null)])
    largest.value = top
    uncategorized.value = loose
    if (!defaulted && loose.length) view.value = 'uncategorized'
    defaulted = true
  } catch {
    error.value = true
  }
}

/** Contas, cartões e categorias: nomes na lista e opções do diálogo. Sem eles a lista ainda funciona. */
async function loadOptions() {
  try {
    ;[accounts.value, cards.value, categories.value] = await Promise.all([listAccounts(), listCards(), listCategories()])
  } catch {
    optionsFailed.value = true
  } finally {
    optionsLoading.value = false
  }
}

function openClassify(row: Transaction) {
  current.value = row
  classifyOpen.value = true
}

/** "Editar tudo": troca o diálogo curto pelo formulário completo da mesma transação. */
function editAll() {
  classifyOpen.value = false
  editOpen.value = true
}

/** O foco volta ao "Classificar" da linha (menos quando o diálogo curto dá lugar ao completo); se ela saiu da lista (foi classificada), ao grupo de opções. */
function returnFocus(event: Event) {
  event.preventDefault()
  if (editOpen.value) return
  void nextTick(() => {
    const button = document.querySelector<HTMLElement>(`[data-testid="details-classify-${current.value?.id}"]`)
    ;(button ?? document.querySelector<HTMLElement>(`[data-testid="details-view-${view.value}"]`))?.focus()
  })
}

async function onSaved({ message }: { message: string }) {
  emit('transaction-saved')
  await load()
  setSuccess(message)
}

onMounted(async () => {
  await Promise.all([loadOptions(), load()])
  emit('loaded')
})
</script>
