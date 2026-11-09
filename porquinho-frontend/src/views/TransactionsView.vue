<template>
  <main class="min-h-screen bg-gray-50 px-4 py-8">
    <div class="mx-auto max-w-3xl space-y-6">
      <div class="flex items-start justify-between gap-4">
        <div>
          <h1 ref="heading" tabindex="-1" class="text-2xl font-semibold text-gray-900 outline-none">Transações</h1>
          <p class="mt-1 text-sm text-gray-600">Confira e corrija o que já entrou nas suas contas e cartões</p>
        </div>
        <div class="flex flex-wrap justify-end gap-2">
          <Button ref="addButton" class="min-h-[44px]" data-testid="add-transaction" @click="openCreate">
            Adicionar transação
          </Button>
          <RouterLink
            to="/dashboard"
            data-testid="back-to-dashboard"
            class="inline-flex min-h-[44px] items-center rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100"
          >
            Voltar ao dashboard
          </RouterLink>
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
        <Button variant="outline" class="min-h-[44px]" data-testid="previous-month" @click="changeMonth(-1)">
          ‹ Mês anterior
        </Button>
        <h2 class="text-base font-semibold text-gray-900 first-letter:uppercase" data-testid="month-label">
          {{ monthLabel }}
        </h2>
        <Button variant="outline" class="min-h-[44px]" data-testid="next-month" @click="changeMonth(1)">
          Próximo ›
        </Button>
      </nav>

      <div v-if="loading" class="space-y-2" aria-busy="true" data-testid="transactions-skeleton">
        <div class="h-32 animate-pulse rounded-lg bg-gray-200" />
        <span class="sr-only">Carregando as transações...</span>
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

      <p v-else-if="transactions.length === 0" class="text-sm text-gray-600" data-testid="transactions-empty">
        Nenhuma transação neste mês.
      </p>

      <ul v-else ref="list" class="divide-y divide-gray-200 rounded-lg border border-gray-200 bg-white">
        <li
          v-for="row in transactions"
          :key="row.id"
          class="flex flex-col gap-2 px-4 py-3 sm:flex-row sm:items-start sm:justify-between"
          :data-testid="`transaction-${row.id}`"
        >
          <div class="min-w-0 space-y-1">
            <div class="flex flex-wrap items-baseline gap-x-3">
              <span class="text-sm text-gray-600">{{ formatLocalDateBR(row.date) }}</span>
              <span class="break-words font-medium text-gray-900">{{ row.description }}</span>
            </div>
            <div class="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm">
              <span
                class="font-semibold"
                :class="row.type === 'CREDIT' ? 'text-green-700' : 'text-gray-900'"
                data-testid="row-amount"
              >
                {{ formatSignedBRL(signedAmount(row)) }}
              </span>
              <span class="text-gray-600" data-testid="row-target">{{ targetName(row) }}</span>
              <span class="text-gray-700" data-testid="row-category">{{ categoryLabel(row) }}</span>
              <span v-if="row.isPix" class="inline-flex items-center gap-1 text-gray-700" data-testid="row-pix">
                <ArrowLeftRight class="size-4" aria-hidden="true" />
                {{ pixLabel(row) }}
              </span>
              <span
                v-if="installmentLabel(row)"
                class="inline-flex items-center gap-1 text-gray-700"
                data-testid="row-installment"
              >
                <CreditCard class="size-4" aria-hidden="true" />
                {{ installmentLabel(row) }}
              </span>
            </div>
          </div>
          <div class="flex shrink-0 flex-wrap gap-1">
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
              class="min-h-[44px] text-red-700"
              :aria-label="`Remover ${row.description}`"
              :data-testid="`remove-${row.id}`"
              @click="openConfirm(row, 'delete')"
            >
              Remover
            </Button>
            <Button
              v-if="row.remainingInstallments > 0"
              variant="ghost"
              class="min-h-[44px] text-red-700"
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

    <Dialog :open="editOpen" @update:open="onEditOpenChange">
      <DialogContent data-testid="edit-dialog" @close-auto-focus="returnFocus">
        <DialogHeader>
          <DialogTitle>{{ creating ? 'Adicionar transação' : 'Editar transação' }}</DialogTitle>
          <DialogDescription>
            {{
              creating
                ? 'Registre um gasto em dinheiro ou um lançamento que nenhum extrato trouxe.'
                : 'Corrija data, descrição, valor, tipo, categoria ou onde a transação está.'
            }}
          </DialogDescription>
        </DialogHeader>
        <p
          v-if="formError"
          role="alert"
          data-testid="edit-error"
          class="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800"
        >
          {{ formError }}
        </p>
        <form class="space-y-4" novalidate data-testid="edit-form" @submit.prevent="save">
          <div class="space-y-1">
            <label for="transaction-date" class="text-sm font-medium text-gray-900">Data</label>
            <input
              id="transaction-date"
              v-model="form.date"
              type="date"
              :class="FIELD_CLASS"
              :aria-describedby="isFutureDate ? 'transaction-date-warning' : undefined"
              data-testid="edit-date"
            />
            <p
              v-if="isFutureDate"
              id="transaction-date-warning"
              class="text-sm text-amber-800"
              data-testid="future-date-warning"
            >
              Esta data ainda não chegou. Confira se é isso mesmo.
            </p>
          </div>
          <div class="space-y-1">
            <label for="transaction-description" class="text-sm font-medium text-gray-900">Descrição</label>
            <input
              id="transaction-description"
              v-model="form.description"
              maxlength="500"
              :class="FIELD_CLASS"
              data-testid="edit-description"
            />
          </div>
          <div class="space-y-1">
            <label for="transaction-amount" class="text-sm font-medium text-gray-900">Valor (R$)</label>
            <input
              id="transaction-amount"
              v-model="form.amount"
              inputmode="decimal"
              :class="FIELD_CLASS"
              data-testid="edit-amount"
            />
          </div>
          <div class="space-y-1">
            <label for="transaction-type" class="text-sm font-medium text-gray-900">Tipo</label>
            <select
              id="transaction-type"
              v-model="form.type"
              :class="FIELD_CLASS"
              :disabled="isInstallment"
              :aria-describedby="isInstallment ? 'installment-locked' : undefined"
              data-testid="edit-type"
            >
              <option value="DEBIT">Saída (débito)</option>
              <option value="CREDIT">Entrada (crédito)</option>
            </select>
          </div>
          <div class="space-y-1">
            <label for="transaction-category" class="text-sm font-medium text-gray-900">Categoria</label>
            <select
              id="transaction-category"
              v-model="form.categoryId"
              :class="FIELD_CLASS"
              :disabled="optionsLoading"
              data-testid="edit-category"
            >
              <option value="" :disabled="creating">{{ creating ? 'Escolha a categoria' : 'Sem categoria' }}</option>
              <optgroup v-for="group in categoryGroups" :key="group.id" :label="group.name">
                <option v-for="category in group.children" :key="category.id" :value="category.id">
                  {{ category.categoryName }}
                </option>
              </optgroup>
            </select>
          </div>
          <div class="space-y-1">
            <label for="transaction-target" class="text-sm font-medium text-gray-900">Conta ou cartão</label>
            <select
              id="transaction-target"
              v-model="form.target"
              :class="FIELD_CLASS"
              :disabled="optionsLoading || isInstallment"
              :aria-describedby="isInstallment ? 'installment-locked' : undefined"
              data-testid="edit-target"
            >
              <option value="" disabled>Escolha a conta ou o cartão</option>
              <!-- Destino removido continua aceito enquanto não mudar -->
              <option v-if="removedTargetLabel" :value="form.target">{{ removedTargetLabel }}</option>
              <optgroup v-if="accounts.length" label="Contas">
                <option v-for="account in accounts" :key="account.id" :value="`account:${account.id}`">
                  {{ account.accountName }}
                </option>
              </optgroup>
              <optgroup v-if="cards.length" label="Cartões">
                <option v-for="card in cards" :key="card.id" :value="`card:${card.id}`">{{ card.cardName }}</option>
              </optgroup>
            </select>
            <p
              v-if="creating && !optionsLoading && accounts.length === 0"
              class="text-sm text-gray-600"
              data-testid="wallet-hint"
            >
              Para gastos em dinheiro, cadastre uma conta Carteira em
              <RouterLink to="/contas" class="font-medium text-purple-700 underline">Contas</RouterLink>.
            </p>
            <p v-if="isInstallment" id="installment-locked" class="text-sm text-gray-600" data-testid="installment-locked">
              Numa parcela, a conta ou o cartão e o tipo não mudam.
            </p>
          </div>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              class="min-h-[44px]"
              :disabled="saving"
              data-testid="edit-cancel"
              @click="editOpen = false"
            >
              Cancelar
            </Button>
            <Button type="submit" class="min-h-[44px]" :disabled="saving || optionsLoading" data-testid="edit-save">
              {{ saving ? 'Salvando...' : 'Salvar' }}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>

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
            {{ confirming ? 'Removendo...' : 'Remover' }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ArrowLeftRight, CreditCard } from 'lucide-vue-next'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { formatLocalDateBR } from '@/lib/date'
import { formatBRLInput, formatSignedBRL, parseBRL } from '@/lib/money'
import { plural } from '@/lib/plural'
import { installmentLabel, pixLabel } from '@/lib/transactionLabels'
import { listAccounts } from '@/services/accountService'
import { listCards } from '@/services/cardService'
import { listCategories } from '@/services/categoryService'
import {
  createTransaction,
  deleteTransaction,
  listTransactions,
  removeFutureInstallments,
  updateTransaction,
} from '@/services/transactionService'
import type { Account } from '@/types/account'
import type { Category } from '@/types/category'
import type { CreditCard as Card } from '@/types/creditCard'
import type { Transaction } from '@/types/transaction'

/** Story 5.12: as transações confirmadas, um mês por vez (decisão Q1), com edição e remoção; 5.13: inclusão manual. */
const FIELD_CLASS =
  'flex min-h-[44px] w-full rounded-md border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 focus-visible:ring-2 focus-visible:ring-purple-600 focus-visible:outline-none'

const ACTION_FAILED = 'Não foi possível concluir a ação. Tente de novo.'
const SUCCESS_TIMEOUT_MS = 5000

interface ApiError {
  response?: { status?: number; data?: { detail?: string } }
}

/** "AAAA-MM-DD" de hoje em São Paulo (en-CA formata assim). */
function todaySaoPaulo(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo' }).format(new Date())
}

const heading = ref<HTMLElement | null>(null)
const addButton = ref<{ $el: HTMLElement } | null>(null)
const list = ref<HTMLElement | null>(null)
const month = ref(todaySaoPaulo().slice(0, 7))
const transactions = ref<Transaction[]>([])
const accounts = ref<Account[]>([])
const cards = ref<Card[]>([])
const categories = ref<Category[]>([])
const loading = ref(true)
const loadFailed = ref(false)
const optionsLoading = ref(false)
const successMessage = ref<string | null>(null)
const actionError = ref<string | null>(null)
/** A linha do diálogo aberto. Continua definida depois de fechar, para o foco voltar ao botão dela. */
const current = ref<Transaction | null>(null)
const editOpen = ref(false)
/** O diálogo de edição em modo "Adicionar transação" (Story 5.13). */
const creating = ref(false)
const saving = ref(false)
const formError = ref<string | null>(null)
const form = reactive({ date: '', description: '', amount: '', type: 'DEBIT', categoryId: '', target: '' })
const confirmOpen = ref(false)
const confirmMode = ref<'delete' | 'futures'>('delete')
const confirming = ref(false)

const monthLabel = computed(() => {
  const [year, monthNumber] = month.value.split('-').map(Number)
  return new Intl.DateTimeFormat('pt-BR', { month: 'long', year: 'numeric', timeZone: 'UTC' }).format(
    Date.UTC(year!, monthNumber! - 1, 1),
  )
})

/** Decisão Q4: numa parcela, destino e tipo ficam como estão. */
const isInstallment = computed(() => !!current.value?.isInstallment)

/** Decisão Q3: remover a única parcela confirmada leva as futuras ativas junto. */
const futuresGoingAlong = computed(() =>
  current.value?.isInstallment && current.value.confirmedInstallments <= 1 ? current.value.remainingInstallments : 0,
)

/** Rótulo do destino atual quando ele já foi removido (fora das listas de ativos). */
const removedTargetLabel = computed(() => {
  const row = current.value
  if (!row || optionsLoading.value) return null
  if (row.accountId) return accounts.value.some((a) => a.id === row.accountId) ? null : 'Conta removida'
  return cards.value.some((c) => c.id === row.creditCardId) ? null : 'Cartão removido'
})

const isFutureDate = computed(() => !!form.date && form.date > todaySaoPaulo())

/** Grupos sem categoria filha ficam de fora: grupo não é categoria de transação. */
const categoryGroups = computed(() => {
  const byName = (a: Category, b: Category) => a.categoryName.localeCompare(b.categoryName, 'pt-BR')
  return categories.value
    .filter((category) => category.parentCategoryId === null)
    .sort(byName)
    .map((parent) => ({
      id: parent.id,
      name: parent.categoryName,
      children: categories.value.filter((category) => category.parentCategoryId === parent.id).sort(byName),
    }))
    .filter((group) => group.children.length > 0)
})

let successTimer: ReturnType<typeof setTimeout> | undefined

function setSuccess(message: string | null) {
  clearTimeout(successTimer)
  successMessage.value = message
  if (message) successTimer = setTimeout(() => (successMessage.value = null), SUCCESS_TIMEOUT_MS)
}

function signedAmount(row: Transaction): number {
  return row.type === 'DEBIT' ? -row.amount : row.amount
}

function categoryLabel(row: Transaction): string {
  if (row.kind === 'CARD_PAYMENT') return 'Pagamento de fatura'
  if (!row.category) return 'Não classificado'
  return row.category.group ? `${row.category.group} › ${row.category.name}` : row.category.name
}

function targetName(row: Transaction): string {
  if (row.accountId) return accounts.value.find((a) => a.id === row.accountId)?.accountName ?? 'Conta removida'
  return cards.value.find((c) => c.id === row.creditCardId)?.cardName ?? 'Cartão removido'
}

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
  try {
    const [accountList, cardList, categoryList] = await Promise.all([listAccounts(), listCards(), listCategories()])
    accounts.value = accountList
    cards.value = cardList
    categories.value = categoryList
  } catch {
    formError.value = editOpen.value ? 'Não foi possível carregar suas contas, cartões e categorias.' : null
  } finally {
    optionsLoading.value = false
  }
}

function changeMonth(delta: number) {
  const [year, monthNumber] = month.value.split('-').map(Number)
  const date = new Date(Date.UTC(year!, monthNumber! - 1 + delta, 1))
  month.value = `${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, '0')}`
  transactions.value = []
  void load()
}

function openCreate() {
  creating.value = true
  current.value = null
  formError.value = null
  Object.assign(form, { date: todaySaoPaulo(), description: '', amount: '', type: 'DEBIT', categoryId: '', target: '' })
  editOpen.value = true
}

function openEdit(row: Transaction) {
  creating.value = false
  current.value = row
  formError.value = null
  form.date = row.date
  form.description = row.description
  form.amount = formatBRLInput(row.amount)
  form.type = row.type
  // Categoria removida fica em branco no select, mas é enviada igual: o backend só valida categoria trocada
  form.categoryId = row.category?.id ?? ''
  form.target = row.accountId ? `account:${row.accountId}` : `card:${row.creditCardId}`
  editOpen.value = true
}

/** Com o PUT em andamento o diálogo não fecha: fechar pareceria cancelar uma alteração que já vai acontecer. */
function onEditOpenChange(open: boolean) {
  if (!saving.value) editOpen.value = open
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

async function save() {
  const row = current.value
  const isCreate = creating.value
  if ((!row && !isCreate) || saving.value) return
  const amount = parseBRL(form.amount)
  const description = form.description.trim()
  if (!form.date) return void (formError.value = 'Informe a data')
  if (!description) return void (formError.value = 'Informe a descrição')
  if (amount === null || amount <= 0) return void (formError.value = 'Informe um valor maior que zero')
  if (isCreate && !form.categoryId) return void (formError.value = 'Escolha uma categoria')
  if (!form.target) return void (formError.value = 'Escolha uma conta ou um cartão')
  const [kind, targetId] = form.target.split(':')
  const input = {
    date: form.date,
    description,
    amount,
    type: form.type as Transaction['type'],
    categoryId: form.categoryId || null,
    accountId: kind === 'account' ? targetId! : null,
    creditCardId: kind === 'card' ? targetId! : null,
  }
  formError.value = null
  saving.value = true
  try {
    if (isCreate) {
      await createTransaction({ ...input, categoryId: form.categoryId })
      // a lista vai para o mês da transação criada, para ela aparecer
      month.value = form.date.slice(0, 7)
    } else {
      await updateTransaction(row!.id, input)
    }
    editOpen.value = false
    actionError.value = null
    await load()
    setSuccess(isCreate ? 'Transação adicionada' : 'Transação atualizada')
  } catch (err) {
    const response = (err as ApiError)?.response
    if (response?.status === 404 || response?.status === 400 || response?.status === 409) {
      formError.value = response.data?.detail ?? ACTION_FAILED
      if (response.status !== 400) void load()
    } else {
      formError.value = ACTION_FAILED
    }
  } finally {
    saving.value = false
  }
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
    actionError.value = (err as ApiError)?.response?.data?.detail ?? ACTION_FAILED
  } finally {
    confirmOpen.value = false
    confirming.value = false
  }
  await load()
}

onMounted(() => {
  void load()
  void loadOptions()
})
</script>
