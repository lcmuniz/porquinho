<template>
  <main class="px-4 py-6 md:px-8 md:py-8" :class="{ 'pb-40 lg:pb-28': selected.length }">
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

      <FeedbackBanner
        :message="feedbackAtBar ? null : successMessage"
        testid="transactions-success"
        @pause="pauseSuccess"
        @resume="resumeSuccess"
      />

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

      <template v-else>
        <!-- Edição em lote: marcar as linhas abre a barra de ações -->
        <label class="inline-flex min-h-[44px] cursor-pointer items-center gap-3 px-4 text-sm font-medium text-gray-700">
          <input
            type="checkbox"
            class="size-5 accent-purple-700"
            :checked="allSelected"
            :indeterminate="selected.length > 0 && !allSelected"
            data-testid="select-all"
            @change="toggleAll"
          />
          Selecionar todas do mês
        </label>
        <ul ref="list" class="divide-y divide-gray-200 rounded-lg border border-gray-200 bg-white">
          <li
            v-for="row in transactions"
            :key="row.id"
            class="flex gap-1 py-3 pr-4 pl-1 [contain-intrinsic-size:auto_72px] [content-visibility:auto]"
            :class="{ 'bg-purple-50': selected.includes(row.id) }"
            :data-testid="`transaction-${row.id}`"
          >
            <label class="-my-1 flex size-11 shrink-0 cursor-pointer items-center justify-center">
              <input
                v-model="selected"
                type="checkbox"
                class="size-5 accent-purple-700"
                :value="row.id"
                :aria-label="`Selecionar ${row.description}`"
                :data-testid="`select-${row.id}`"
              />
            </label>
            <div class="min-w-0 flex-1 sm:flex sm:items-center sm:gap-4">
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
            </div>
          </li>
        </ul>
      </template>
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
      @exported="notify(`Relatório exportado: ${plural($event, 'transação', 'transações')}`)"
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

    <!-- Barra da edição em lote: fixa acima da navegação do celular -->
    <section
      v-if="selected.length"
      aria-label="Ações em lote"
      class="fixed inset-x-0 bottom-[calc(4.5rem+env(safe-area-inset-bottom))] z-30 flex justify-center px-4 lg:bottom-6 lg:pl-62"
      data-testid="batch-bar"
    >
      <div class="flex w-full max-w-4xl flex-wrap items-center gap-2 rounded-xl border border-gray-200 bg-white p-2 pl-4 shadow-lg">
        <p class="mr-auto text-sm font-semibold text-gray-900" data-testid="batch-count">
          {{ plural(selected.length, 'selecionada', 'selecionadas') }}
        </p>
        <Button ref="batchCategoryButton" variant="outline" class="min-h-[44px]" data-testid="batch-category" @click="openBatch('category')">
          Mudar categoria
        </Button>
        <Button
          variant="outline"
          class="min-h-[44px] text-red-700 hover:bg-red-50 hover:text-red-700"
          data-testid="batch-delete"
          @click="openBatch('delete')"
        >
          Remover
        </Button>
        <Button variant="ghost" class="min-h-[44px]" data-testid="batch-clear" @click="selected = []">Limpar seleção</Button>
      </div>
    </section>
    <!-- O resultado do lote (com "Desfazer") aparece onde estava a barra, perto de quem agiu -->
    <div
      class="pointer-events-none fixed inset-x-0 bottom-[calc(4.5rem+env(safe-area-inset-bottom))] z-30 flex justify-center px-4 lg:bottom-6 lg:pl-62"
    >
      <FeedbackBanner
        ref="batchFeedback"
        class="pointer-events-auto w-full max-w-4xl shadow-lg"
        :message="feedbackAtBar && !selected.length ? successMessage : null"
        :action="successAction"
        testid="batch-success"
        @pause="pauseSuccess"
        @resume="resumeSuccess"
      />
    </div>
    <!-- Sempre no DOM: anuncia quantas estão marcadas -->
    <p role="status" class="sr-only" data-testid="batch-status">
      {{ selected.length ? plural(selected.length, 'transação selecionada', 'transações selecionadas') : '' }}
    </p>

    <Dialog :open="batchOpen" @update:open="onBatchOpenChange">
      <DialogContent data-testid="batch-dialog" @close-auto-focus="returnBatchFocus">
        <DialogHeader>
          <DialogTitle>
            {{ batchMode === 'category' ? 'Mudar categoria' : `Remover ${plural(selected.length, 'transação', 'transações')}` }}
          </DialogTitle>
          <DialogDescription>
            <template v-if="batchMode === 'category'">
              A categoria escolhida vai para {{ plural(selected.length, 'transação selecionada', 'transações selecionadas') }}.
            </template>
            <template v-else>
              Elas saem da lista e, se tiverem alterado o saldo de uma conta, o saldo volta ao que era.
              <span v-if="batchFuturesGoingAlong" data-testid="batch-futures">
                Parcelas futuras de compras sem outra parcela confirmada também serão removidas.
              </span>
            </template>
          </DialogDescription>
        </DialogHeader>
        <form class="space-y-4" novalidate data-testid="batch-form" @submit.prevent="confirmBatch">
          <div v-if="batchMode === 'category'" class="space-y-1">
            <label for="batch-category-select" class="text-sm font-medium text-gray-900">Categoria</label>
            <select
              id="batch-category-select"
              v-model="batchCategoryId"
              :class="SELECT_CLASS"
              :disabled="optionsLoading"
              data-testid="batch-category-select"
            >
              <!-- Critique de 20/11/2026: nada vem escolhido; tirar a categoria é uma escolha explícita, no fim -->
              <option value="" disabled>Escolha uma categoria</option>
              <optgroup v-for="group in categoryGroups" :key="group.id" :label="group.name">
                <option v-for="category in group.children" :key="category.id" :value="category.id">
                  {{ category.categoryName }}
                </option>
              </optgroup>
              <option :value="NO_CATEGORY">Sem categoria</option>
            </select>
          </div>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              class="min-h-[44px]"
              :disabled="batchSaving"
              data-testid="batch-cancel"
              @click="batchOpen = false"
            >
              Cancelar
            </Button>
            <Button
              type="submit"
              :variant="batchMode === 'delete' ? 'destructive' : 'default'"
              class="min-h-[44px]"
              :disabled="batchSaving || (batchMode === 'category' && !batchCategoryId)"
              data-testid="batch-confirm"
            >
              <template v-if="batchMode === 'category'">{{ batchSaving ? 'Salvando…' : 'Aplicar' }}</template>
              <template v-else>{{ batchSaving ? 'Removendo…' : 'Remover' }}</template>
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  </main>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useFeedback } from '@/composables/useFeedback'
import type { FeedbackAction } from '@/composables/useFeedback'
import FeedbackBanner from '@/components/common/FeedbackBanner.vue'
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
import { SELECT_CLASS } from '@/lib/formField'
import {
  categoryLabel,
  categoryOptionGroups,
  installmentLabel,
  pixLabel,
  signedAmount,
  targetName,
} from '@/lib/transactionLabels'
import { listAccounts } from '@/services/accountService'
import { listCards } from '@/services/cardService'
import { listCategories } from '@/services/categoryService'
import {
  batchDeleteTransactions,
  batchUpdateCategory,
  deleteTransaction,
  listTransactions,
  removeFutureInstallments,
} from '@/services/transactionService'
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
const { successMessage, successAction, setSuccess, pauseSuccess, resumeSuccess } = useFeedback()
/** O resultado do lote aparece embaixo, no lugar da barra; o resto, no topo. */
const feedbackAtBar = ref(false)

function notify(message: string, action: FeedbackAction | null = null, atBar = false) {
  feedbackAtBar.value = atBar
  setSuccess(message, action)
}
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
    // a seleção fica só com as linhas que ainda estão na lista
    const ids = new Set(rows.map((r) => r.id))
    selected.value = selected.value.filter((id) => ids.has(id))
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
  selected.value = []
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
  notify(message)
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
      notify('Transação removida')
    } else {
      const removed = await removeFutureInstallments(row.id)
      notify(
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

// --- Edição em lote ---

const selected = ref<string[]>([])
const allSelected = computed(() => transactions.value.length > 0 && selected.value.length === transactions.value.length)
const batchOpen = ref(false)
const batchMode = ref<'category' | 'delete'>('category')
/** Valor do "Sem categoria" no seletor do lote ("" é o "Escolha uma categoria", que não vale). */
const NO_CATEGORY = 'none'
const batchCategoryId = ref('')
const batchFeedback = ref<InstanceType<typeof FeedbackBanner> | null>(null)
const batchSaving = ref(false)
const batchCategoryButton = ref<{ $el: HTMLElement } | null>(null)
const categoryGroups = computed(() => categoryOptionGroups(categories.value))
/** Decisão Q3, em lote: alguma selecionada é a única parcela confirmada de uma compra com futuras. */
const batchFuturesGoingAlong = computed(() =>
  transactions.value.some(
    (r) => selected.value.includes(r.id) && r.isInstallment && r.confirmedInstallments <= 1 && r.remainingInstallments > 0,
  ),
)

function toggleAll() {
  selected.value = allSelected.value ? [] : transactions.value.map((r) => r.id)
}

function openBatch(mode: 'category' | 'delete') {
  batchMode.value = mode
  batchCategoryId.value = ''
  actionError.value = null
  batchOpen.value = true
}

function onBatchOpenChange(open: boolean) {
  if (!batchSaving.value) batchOpen.value = open
}

/** O foco volta à barra (se ainda há seleção), ao resultado do lote ou ao título. */
function returnBatchFocus(event: Event) {
  event.preventDefault()
  void nextTick(() => {
    if (feedbackAtBar.value && successMessage.value && !selected.value.length) batchFeedback.value?.focus()
    else (batchCategoryButton.value?.$el ?? heading.value)?.focus()
  })
}

async function confirmBatch() {
  if (batchSaving.value || selected.value.length === 0) return
  if (batchMode.value === 'category' && !batchCategoryId.value) return
  batchSaving.value = true
  actionError.value = null
  const ids = [...selected.value]
  try {
    if (batchMode.value === 'category') {
      const before = previousCategories(ids)
      const target = batchCategoryId.value === NO_CATEGORY ? null : batchCategoryId.value
      const updated = await batchUpdateCategory(ids, target)
      notify(
        plural(updated, 'transação atualizada', 'transações atualizadas'),
        { label: 'Desfazer', run: () => void undoBatchCategory(before) },
        true,
      )
    } else {
      const removed = await batchDeleteTransactions(ids)
      notify(plural(removed, 'transação removida', 'transações removidas'), null, true)
    }
    selected.value = []
  } catch (err) {
    // tudo ou nada: a seleção fica para tentar de novo
    actionError.value = apiErrorDetail(err, ACTION_FAILED)
  } finally {
    batchOpen.value = false
    batchSaving.value = false
  }
  await load()
}

/** A categoria de cada transação antes do lote, agrupada: um pedido por categoria de antes. */
function previousCategories(ids: string[]): Map<string | null, string[]> {
  const groups = new Map<string | null, string[]>()
  for (const row of transactions.value) {
    if (!ids.includes(row.id)) continue
    const key = row.category?.id ?? null
    groups.set(key, [...(groups.get(key) ?? []), row.id])
  }
  return groups
}

/** "Desfazer" do lote: cada transação volta para a categoria que tinha. */
async function undoBatchCategory(before: Map<string | null, string[]>) {
  if (batchSaving.value) return
  batchSaving.value = true
  setSuccess(null)
  actionError.value = null
  try {
    // shortcut: um pedido por categoria de antes; se um falhar no meio, os anteriores já voltaram (endpoint de
    // "restaurar" atômico se isso incomodar)
    for (const [categoryId, ids] of before) await batchUpdateCategory(ids, categoryId)
    notify('Alterações desfeitas', null, true)
  } catch (err) {
    actionError.value = apiErrorDetail(err, ACTION_FAILED)
  } finally {
    batchSaving.value = false
  }
  await load()
  void nextTick(() => (successMessage.value ? batchFeedback.value?.focus() : heading.value?.focus()))
}

onMounted(() => {
  // A conclusão da importação abre aqui no mês dela, com o resumo; limpo para não repetir ao recarregar
  const state = window.history.state as Partial<ImportCompletedState> | null
  if (state?.importMessage) {
    if (state.importMonth) month.value = state.importMonth
    notify(state.importMessage)
    window.history.replaceState({ ...state, importMonth: null, importMessage: null }, '')
  }
  void load()
  void loadOptions()
})
</script>
