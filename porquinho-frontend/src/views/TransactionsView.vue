<template>
  <main class="px-4 py-6 md:px-8 md:py-8">
    <div class="mx-auto max-w-4xl space-y-6">
      <div class="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h1 ref="heading" tabindex="-1" class="text-2xl font-bold text-gray-900 md:text-[28px] outline-none">Transações</h1>
          <p class="mt-1 text-sm text-gray-600">Confira e corrija o que já entrou nas suas contas e cartões</p>
        </div>
        <div class="flex flex-wrap gap-2 sm:justify-end">
          <Button variant="outline" class="min-h-[44px]" data-testid="export-transactions" @click="exportOpen = true">
            <Download class="size-4" aria-hidden="true" />
            Exportar CSV
          </Button>
          <Button ref="addButton" class="min-h-[44px]" data-testid="add-transaction" @click="openCreate">
            Adicionar transação
          </Button>
        </div>
      </div>

      <!-- Sempre no DOM: leitores de tela só anunciam mudanças numa região live que já existe -->
      <p
        role="status"
        data-testid="transactions-success"
        :class="
          successMessage
            ? 'rounded-lg border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-800'
            : 'sr-only'
        "
      >
        {{ successMessage }}
      </p>

      <p
        v-if="actionError"
        role="alert"
        data-testid="transactions-error"
        class="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800"
      >
        {{ actionError }}
      </p>

      <nav aria-label="Mês" class="flex items-center justify-between gap-2">
        <Button variant="outline" class="min-h-[44px]" data-testid="previous-month" aria-label="Mês anterior" @click="changeMonth(-1)">
          <ChevronLeft class="size-5" aria-hidden="true" />
          <span class="hidden sm:inline">Mês anterior</span>
        </Button>
        <h2 class="text-lg font-bold whitespace-nowrap text-gray-900 first-letter:uppercase" data-testid="month-label">
          {{ monthLabel }}
        </h2>
        <Button variant="outline" class="min-h-[44px]" data-testid="next-month" aria-label="Próximo mês" @click="changeMonth(1)">
          <span class="hidden sm:inline">Próximo</span>
          <ChevronRight class="size-5" aria-hidden="true" />
        </Button>
      </nav>

      <div v-if="loading" class="space-y-2" aria-busy="true" data-testid="transactions-skeleton">
        <div class="h-32 animate-pulse rounded-lg bg-gray-200" />
        <span class="sr-only">Carregando as transações…</span>
      </div>

      <div
        v-else-if="loadFailed"
        role="alert"
        class="flex flex-col gap-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3 sm:flex-row sm:items-center sm:justify-between"
        data-testid="transactions-load-error"
      >
        <p class="text-sm text-red-800">Não foi possível carregar as transações.</p>
        <Button variant="outline" class="min-h-[44px]" @click="load">Tentar de novo</Button>
      </div>

      <div
        v-else-if="transactions.length === 0"
        class="rounded-lg border border-gray-200 bg-white p-6 text-center"
        data-testid="transactions-empty"
      >
        <Inbox class="mx-auto size-10 text-gray-400" aria-hidden="true" />
        <p class="mt-2 text-[15px] text-gray-900">Nenhuma transação neste mês.</p>
        <RouterLink to="/importar" class="mt-3 inline-flex min-h-[44px] items-center text-[15px] font-semibold text-purple-700 underline">
          Importar extrato do banco
        </RouterLink>
      </div>

      <ul v-else ref="list" class="divide-y divide-gray-200 rounded-lg border border-gray-200 bg-white">
        <li
          v-for="row in transactions"
          :key="row.id"
          class="px-4 py-3 [contain-intrinsic-size:auto_72px] [content-visibility:auto] sm:flex sm:items-center sm:gap-4"
          :data-testid="`transaction-${row.id}`"
        >
          <div class="flex min-w-0 flex-1 items-start justify-between gap-3">
            <div class="min-w-0 space-y-1">
              <p class="break-words text-[15px] font-semibold text-gray-900">{{ row.description }}</p>
              <p class="flex flex-wrap items-center gap-x-3 gap-y-0.5 text-sm text-gray-600">
                <span>{{ formatLocalDateBR(row.date) }}</span>
                <span data-testid="row-target">{{ targetName(row, accounts, cards) }}</span>
                <span class="text-gray-800" data-testid="row-category">{{ categoryLabel(row) }}</span>
                <span v-if="row.isPix" class="inline-flex items-center gap-1" data-testid="row-pix">
                  <ArrowLeftRight class="size-4" aria-hidden="true" />
                  {{ pixLabel(row) }}
                </span>
                <span
                  v-if="installmentLabel(row)"
                  class="inline-flex items-center gap-1"
                  data-testid="row-installment"
                >
                  <CreditCard class="size-4" aria-hidden="true" />
                  {{ installmentLabel(row) }}
                </span>
              </p>
            </div>
            <span
              class="shrink-0 text-right text-base font-semibold whitespace-nowrap"
              :class="row.type === 'CREDIT' ? 'text-green-700' : 'text-gray-900'"
              data-testid="row-amount"
            >
              {{ formatSignedBRL(signedAmount(row)) }}
            </span>
          </div>
          <div class="-ml-3 mt-1 flex flex-wrap gap-1 sm:mt-0 sm:ml-0 sm:shrink-0">
            <Button
              variant="ghost"
              class="min-h-[44px] text-purple-700"
              :aria-label="`Editar ${row.description}`"
              :data-testid="`edit-${row.id}`"
              @click="openEdit(row)"
            >
              Editar
            </Button>
            <Button
              variant="ghost"
              class="min-h-[44px] text-gray-700 hover:bg-red-50 hover:text-red-700"
              :aria-label="`Remover ${row.description}`"
              :data-testid="`remove-${row.id}`"
              @click="openConfirm(row, 'delete')"
            >
              Remover
            </Button>
            <Button
              v-if="row.remainingInstallments > 0"
              variant="ghost"
              class="min-h-[44px] text-gray-700 hover:bg-red-50 hover:text-red-700"
              :aria-label="`Remover parcelas futuras desta compra: ${row.description}`"
              :data-testid="`remove-futures-${row.id}`"
              @click="openConfirm(row, 'futures')"
            >
              Remover parcelas futuras desta compra
            </Button>
          </div>
        </li>
      </ul>
    </div>

    <TransactionFormDialog
      v-model:open="editOpen"
      :row="current"
      :creating="creating"
      :accounts="accounts"
      :cards="cards"
      :categories="categories"
      :options-loading="optionsLoading"
      :options-failed="optionsFailed"
      @saved="onSaved"
      @stale="load"
      @close-auto-focus="returnFocus"
    />

    <TransactionExportDialog
      v-model:open="exportOpen"
      :accounts="accounts"
      :cards="cards"
      :categories="categories"
      :initial="exportInitial"
      @exported="setSuccess(`Relatório exportado: ${plural($event, 'transação', 'transações')}`)"
    />

    <Dialog :open="confirmOpen" @update:open="onConfirmOpenChange">
      <DialogContent data-testid="confirm-dialog" @close-auto-focus="returnFocus">
        <DialogHeader>
          <DialogTitle>
            {{ confirmMode === 'delete' ? 'Remover transação' : 'Remover parcelas futuras desta compra' }}
          </DialogTitle>
          <DialogDescription>
            <template v-if="confirmMode === 'delete'">
              "{{ current?.description }}" sai da lista e, se tiver alterado o saldo da conta, o saldo volta ao que era.
              <span v-if="futuresGoingAlong > 0" data-testid="futures-going-along">
                É a única parcela confirmada desta compra:
                {{ plural(futuresGoingAlong, 'parcela futura', 'parcelas futuras') }}
                {{ futuresGoingAlong === 1 ? 'também será removida' : 'também serão removidas' }}.
              </span>
            </template>
            <template v-else>
              {{ plural(current?.remainingInstallments ?? 0, 'parcela futura', 'parcelas futuras') }} desta compra
              {{ (current?.remainingInstallments ?? 0) === 1 ? 'será removida' : 'serão removidas' }}, e as próximas
              importações não vão criá-las de novo. Use quando a compra foi estornada ou quitada.
            </template>
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button
            variant="outline"
            class="min-h-[44px]"
            :disabled="confirming"
            data-testid="confirm-cancel"
            @click="confirmOpen = false"
          >
            Cancelar
          </Button>
          <Button
            variant="destructive"
            class="min-h-[44px]"
            :disabled="confirming"
            data-testid="confirm-action"
            @click="confirmAction"
          >
            {{ confirming ? 'Removendo…' : 'Remover' }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </main>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useFeedback } from '@/composables/useFeedback'
import { ArrowLeftRight, ChevronLeft, ChevronRight, CreditCard, Download, Inbox } from 'lucide-vue-next'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import TransactionExportDialog, { type ExportInitial } from '@/components/transactions/TransactionExportDialog.vue'
import TransactionFormDialog from '@/components/transactions/TransactionFormDialog.vue'
import { formatLocalDateBR, formatMonthBR, monthFromQuery, shiftMonth } from '@/lib/date'
import { formatSignedBRL } from '@/lib/money'
import { plural } from '@/lib/plural'
import { categoryLabel, installmentLabel, pixLabel, signedAmount, targetName } from '@/lib/transactionLabels'
import { listAccounts } from '@/services/accountService'
import { listCards } from '@/services/cardService'
import { listCategories } from '@/services/categoryService'
import { deleteTransaction, listTransactions, removeFutureInstallments } from '@/services/transactionService'
import type { Account } from '@/types/account'
import type { Category } from '@/types/category'
import type { CreditCard as Card } from '@/types/creditCard'
import type { Transaction } from '@/types/transaction'
import { apiErrorDetail } from '@/lib/apiError'
import type { ImportCompletedState } from '@/lib/importExit'

/**
 * Story 5.12: as transações confirmadas, um mês por vez (decisão Q1), com edição e remoção; 5.13: inclusão manual.
 * O diálogo de edição/inclusão é o TransactionFormDialog (Story 7.6).
 */

const ACTION_FAILED = 'Não foi possível concluir a ação. Tente de novo.'

const heading = ref<HTMLElement | null>(null)
const addButton = ref<{ $el: HTMLElement } | null>(null)
const list = ref<HTMLElement | null>(null)
const route = useRoute()
const router = useRouter()
// o mês mostrado fica na URL (?mes=AAAA-MM): recarregar ou compartilhar abre no mesmo mês
const month = ref(monthFromQuery(route.query.mes))
watch(month, (mes) => void router.replace({ query: { ...route.query, mes } }))
const transactions = ref<Transaction[]>([])
const accounts = ref<Account[]>([])
const cards = ref<Card[]>([])
const categories = ref<Category[]>([])
const loading = ref(true)
const loadFailed = ref(false)
const optionsLoading = ref(false)
const optionsFailed = ref(false)
const { successMessage, setSuccess } = useFeedback()
const actionError = ref<string | null>(null)
/** A linha do diálogo aberto. Continua definida depois de fechar, para o foco voltar ao botão dela. */
const current = ref<Transaction | null>(null)
const editOpen = ref(false)
/** O diálogo de edição em modo "Adicionar transação" (Story 5.13). */
const creating = ref(false)
const confirmOpen = ref(false)
const confirmMode = ref<'delete' | 'futures'>('delete')
const confirming = ref(false)
const exportOpen = ref(false)

/** Story 9.1: o export abre no mês mostrado, como personalizado. */
const exportInitial = computed<ExportInitial>(() => {
  const [year, monthNumber] = month.value.split('-').map(Number)
  const last = new Date(Date.UTC(year!, monthNumber!, 0)).toISOString().slice(0, 10)
  return { preset: 'custom', from: `${month.value}-01`, to: last, categoryIds: [], targets: [] }
})

const monthLabel = computed(() => formatMonthBR(month.value))

/** Decisão Q3: remover a única parcela confirmada leva as futuras ativas junto. */
const futuresGoingAlong = computed(() =>
  current.value?.isInstallment && current.value.confirmedInstallments <= 1 ? current.value.remainingInstallments : 0,
)

/** Sequência das cargas: só a resposta da carga mais recente é aplicada. */
let request = 0

async function load() {
  const currentRequest = ++request
  loading.value = transactions.value.length === 0
  loadFailed.value = false
  try {
    const rows = await listTransactions(month.value)
    if (currentRequest !== request) return
    transactions.value = rows
  } catch {
    if (currentRequest === request) loadFailed.value = true
  } finally {
    if (currentRequest === request) loading.value = false
  }
}

/** Contas, cartões e categorias: nomes na lista e opções do formulário. Sem eles a lista ainda funciona. */
async function loadOptions() {
  optionsLoading.value = true
  optionsFailed.value = false
  try {
    const [accountList, cardList, categoryList] = await Promise.all([listAccounts(), listCards(), listCategories()])
    accounts.value = accountList
    cards.value = cardList
    categories.value = categoryList
  } catch {
    optionsFailed.value = true
  } finally {
    optionsLoading.value = false
  }
}

function changeMonth(delta: number) {
  month.value = shiftMonth(month.value, delta)
  transactions.value = []
  void load()
}

function openCreate() {
  creating.value = true
  current.value = null
  editOpen.value = true
}

function openEdit(row: Transaction) {
  creating.value = false
  current.value = row
  editOpen.value = true
}

function onConfirmOpenChange(open: boolean) {
  if (!confirming.value) confirmOpen.value = open
}

/** O foco volta ao "Adicionar transação" ou ao "Editar" da linha, se ela ainda estiver na lista; senão, ao título. */
function returnFocus(event: Event) {
  event.preventDefault()
  void nextTick(() => {
    const button = creating.value
      ? addButton.value?.$el
      : current.value
      ? list.value?.querySelector<HTMLElement>(`[data-testid="edit-${current.value.id}"]`)
      : null
    ;(button ?? heading.value)?.focus()
  })
}

async function onSaved({ message, month: createdMonth }: { message: string; month?: string }) {
  // a lista vai para o mês da transação criada, para ela aparecer
  if (createdMonth) month.value = createdMonth
  actionError.value = null
  await load()
  setSuccess(message)
}

function openConfirm(row: Transaction, mode: 'delete' | 'futures') {
  creating.value = false
  current.value = row
  confirmMode.value = mode
  confirmOpen.value = true
}

async function confirmAction() {
  const row = current.value
  if (!row || confirming.value) return
  confirming.value = true
  actionError.value = null
  try {
    if (confirmMode.value === 'delete') {
      await deleteTransaction(row.id)
      setSuccess('Transação removida')
    } else {
      const removed = await removeFutureInstallments(row.id)
      setSuccess(
        removed === 1 ? '1 parcela futura removida' : `${plural(removed, 'parcela futura', 'parcelas futuras')} removidas`,
      )
    }
  } catch (err) {
    actionError.value = apiErrorDetail(err, ACTION_FAILED)
  } finally {
    confirmOpen.value = false
    confirming.value = false
  }
  await load()
}

onMounted(() => {
  // A conclusão da importação abre aqui no mês dela, com o resumo; limpo para não repetir ao recarregar
  const state = window.history.state as Partial<ImportCompletedState> | null
  if (state?.importMessage) {
    if (state.importMonth) month.value = state.importMonth
    setSuccess(state.importMessage)
    window.history.replaceState({ ...state, importMonth: null, importMessage: null }, '')
  }
  void load()
  void loadOptions()
})
</script>
