<template>
  <div ref="root" class="mt-4 space-y-4">
    <form
      class="grid grid-cols-1 gap-3 rounded-lg border border-gray-200 bg-white p-4 md:grid-cols-2 xl:grid-cols-4"
      data-testid="details-filters"
      @submit.prevent
    >
      <div class="space-y-1">
        <label for="details-period" class="text-sm font-medium text-gray-900">Período</label>
        <select id="details-period" v-model="stored.preset" :class="FIELD_CLASS" data-testid="details-period">
          <option value="this-month">Este mês</option>
          <option value="last-month">Mês passado</option>
          <option value="last-3-months">Últimos 3 meses</option>
          <option value="custom">Personalizado</option>
        </select>
      </div>
      <div class="space-y-1">
        <label for="details-categories" class="text-sm font-medium text-gray-900">Categorias</label>
        <MultiSelect
          id="details-categories"
          v-model="stored.categoryIds"
          label="Categorias"
          testid="details-categories"
          :options="categoryOptions"
        />
      </div>
      <div class="space-y-1">
        <label for="details-targets" class="text-sm font-medium text-gray-900">Contas e cartões</label>
        <MultiSelect
          id="details-targets"
          v-model="stored.targets"
          label="Contas e cartões"
          testid="details-targets"
          :options="targetOptions"
        />
      </div>
      <div class="space-y-1">
        <label for="details-q" class="text-sm font-medium text-gray-900">Buscar na descrição</label>
        <input id="details-q" v-model="qInput" type="search" :class="FIELD_CLASS" data-testid="details-q" />
      </div>
      <div v-if="stored.preset === 'custom'" class="grid grid-cols-2 gap-3 md:col-span-2">
        <div class="space-y-1">
          <label for="details-from" class="text-sm font-medium text-gray-900">De</label>
          <input
            id="details-from"
            v-model="stored.from"
            type="date"
            :class="FIELD_CLASS"
            :aria-invalid="!period"
            :aria-describedby="!period ? 'details-period-error' : undefined"
            data-testid="details-from"
          />
        </div>
        <div class="space-y-1">
          <label for="details-to" class="text-sm font-medium text-gray-900">Até</label>
          <input
            id="details-to"
            v-model="stored.to"
            type="date"
            :class="FIELD_CLASS"
            :aria-invalid="!period"
            :aria-describedby="!period ? 'details-period-error' : undefined"
            data-testid="details-to"
          />
        </div>
        <p
          v-if="!period"
          id="details-period-error"
          role="alert"
          class="col-span-2 text-sm text-red-700"
          data-testid="details-period-error"
        >
          Escolha datas válidas, com o início até o fim.
        </p>
      </div>
    </form>

    <div class="flex flex-wrap items-center justify-between gap-3">
      <div class="md:hidden">
        <label for="details-sort" class="sr-only">Ordenar por</label>
        <select id="details-sort" v-model="sortChoice" :class="FIELD_CLASS" data-testid="details-sort">
          <option value="date,desc">Data: mais recentes</option>
          <option value="date,asc">Data: mais antigas</option>
          <option value="amount,desc">Valor: maior primeiro</option>
          <option value="amount,asc">Valor: menor primeiro</option>
          <option value="category,asc">Categoria: A-Z</option>
          <option value="category,desc">Categoria: Z-A</option>
        </select>
      </div>
      <Button
        variant="outline"
        class="ml-auto min-h-[44px]"
        :disabled="!canExport || exporting"
        data-testid="details-export"
        @click="exportCsv"
      >
        <Download class="size-4" aria-hidden="true" />
        {{ exporting ? 'Exportando...' : 'Exportar CSV' }}
      </Button>
    </div>

    <!-- Sempre no DOM: leitores de tela só anunciam mudanças numa região live que já existe -->
    <p
      role="status"
      data-testid="details-success"
      :class="successMessage ? 'rounded-lg border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-800' : 'sr-only'"
    >
      {{ successMessage }}
    </p>
    <p
      v-if="exportError"
      role="alert"
      data-testid="details-export-error"
      class="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800"
    >
      Não foi possível exportar o CSV. Tente de novo.
    </p>

    <div
      v-if="error"
      role="alert"
      class="flex flex-col gap-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3 sm:flex-row sm:items-center sm:justify-between"
      data-testid="details-error"
    >
      <p class="text-sm text-red-800">Não foi possível carregar as transações.</p>
      <Button variant="outline" class="min-h-[44px]" @click="load">Tentar de novo</Button>
    </div>
    <p v-else-if="!details" role="status" data-testid="details-loading" class="text-sm text-gray-600">
      {{ filter ? 'Carregando…' : '' }}
    </p>
    <p v-else-if="details.items.length === 0" class="text-sm text-gray-600" data-testid="details-empty">
      Nenhuma transação encontrada
    </p>
    <template v-else>
      <table class="hidden w-full table-fixed border-collapse rounded-lg bg-white text-sm md:table" data-testid="details-table">
        <caption class="sr-only">Transações filtradas</caption>
        <thead>
          <tr class="border-b border-gray-200 text-left text-gray-600">
            <th scope="col" class="w-28 px-3 py-2 font-medium" :aria-sort="ariaSort('date')">
              <button type="button" class="min-h-[44px] font-medium hover:text-gray-900" data-testid="sort-date" @click="sortBy('date')">
                Data {{ arrow('date') }}
              </button>
            </th>
            <th scope="col" class="px-3 py-2 font-medium">Descrição</th>
            <th scope="col" class="w-32 px-3 py-2 text-right font-medium" :aria-sort="ariaSort('amount')">
              <button type="button" class="min-h-[44px] font-medium hover:text-gray-900" data-testid="sort-amount" @click="sortBy('amount')">
                Valor {{ arrow('amount') }}
              </button>
            </th>
            <th scope="col" class="px-3 py-2 font-medium" :aria-sort="ariaSort('category')">
              <button type="button" class="min-h-[44px] font-medium hover:text-gray-900" data-testid="sort-category" @click="sortBy('category')">
                Categoria {{ arrow('category') }}
              </button>
            </th>
            <th scope="col" class="px-3 py-2 font-medium">Conta</th>
            <th scope="col" class="w-20 px-3 py-2 font-medium">Status</th>
            <th scope="col" class="w-24 px-3 py-2 font-medium"><span class="sr-only">Ações</span></th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="row in details.items"
            :key="row.id"
            class="border-b border-gray-100 align-top"
            :data-testid="`details-row-${row.id}`"
          >
            <td class="px-3 py-2 text-gray-600">{{ formatLocalDateBR(row.date) }}</td>
            <td class="px-3 py-2 break-words text-gray-900">{{ row.description }}</td>
            <td class="px-3 py-2 text-right font-semibold" :class="row.type === 'CREDIT' ? 'text-green-700' : 'text-gray-900'">
              {{ formatSignedBRL(signedAmount(row)) }}
            </td>
            <td class="px-3 py-2 break-words text-gray-700">{{ categoryLabel(row) }}</td>
            <td class="px-3 py-2 break-words text-gray-700">{{ targetName(row, accounts, cards) }}</td>
            <td class="px-3 py-2 text-gray-700">
              <span class="flex gap-2">
                <span v-if="row.isPix" :title="pixLabel(row)" data-testid="row-pix">
                  <ArrowLeftRight class="size-4" aria-hidden="true" />
                  <span class="sr-only">{{ pixLabel(row) }}</span>
                </span>
                <span v-if="installmentLabel(row)" :title="installmentLabel(row)" data-testid="row-installment">
                  <CreditCard class="size-4" aria-hidden="true" />
                  <span class="sr-only">{{ installmentLabel(row) }}</span>
                </span>
              </span>
            </td>
            <td class="px-3 py-2">
              <Button
                variant="ghost"
                class="min-h-[44px] text-purple-700"
                :aria-label="`Editar ${row.description}`"
                :data-testid="`details-edit-${row.id}`"
                @click="openEdit(row)"
              >
                Editar
              </Button>
            </td>
          </tr>
        </tbody>
      </table>

      <ul class="space-y-3 md:hidden" data-testid="details-cards">
        <li
          v-for="row in details.items"
          :key="row.id"
          class="min-w-0 space-y-1 rounded-lg border border-gray-200 bg-white p-4"
          :data-testid="`details-card-${row.id}`"
        >
          <div class="flex items-baseline justify-between gap-3">
            <span class="text-sm text-gray-600">{{ formatLocalDateBR(row.date) }}</span>
            <span class="font-semibold" :class="row.type === 'CREDIT' ? 'text-green-700' : 'text-gray-900'">
              {{ formatSignedBRL(signedAmount(row)) }}
            </span>
          </div>
          <p class="break-words font-medium text-gray-900">{{ row.description }}</p>
          <p class="break-words text-sm text-gray-700">{{ categoryLabel(row) }} · {{ targetName(row, accounts, cards) }}</p>
          <p v-if="row.isPix" class="flex items-center gap-1 text-sm text-gray-700">
            <ArrowLeftRight class="size-4 shrink-0" aria-hidden="true" />{{ pixLabel(row) }}
          </p>
          <p v-if="installmentLabel(row)" class="flex items-center gap-1 text-sm text-gray-700">
            <CreditCard class="size-4 shrink-0" aria-hidden="true" />{{ installmentLabel(row) }}
          </p>
          <Button
            variant="ghost"
            class="min-h-[44px] px-0 text-purple-700"
            :aria-label="`Editar ${row.description}`"
            :data-testid="`details-card-edit-${row.id}`"
            @click="openEdit(row)"
          >
            Editar
          </Button>
        </li>
      </ul>

      <nav aria-label="Páginas das transações" class="flex flex-wrap items-center justify-between gap-3">
        <p class="text-sm text-gray-600" data-testid="details-page">
          {{ plural(details.totalItems, 'transação', 'transações') }} · página {{ page + 1 }} de {{ details.totalPages }}
        </p>
        <div class="flex gap-2">
          <Button variant="outline" class="min-h-[44px]" :disabled="loading || page === 0" data-testid="details-previous" @click="goTo(page - 1)">
            ‹ Anterior
          </Button>
          <Button
            variant="outline"
            class="min-h-[44px]"
            :disabled="loading || page + 1 >= details.totalPages"
            data-testid="details-next"
            @click="goTo(page + 1)"
          >
            Próxima ›
          </Button>
        </div>
      </nav>
    </template>

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
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useLocalStorage, watchDebounced } from '@vueuse/core'
import { ArrowLeftRight, CreditCard, Download } from 'lucide-vue-next'
import { Button } from '@/components/ui/button'
import TransactionFormDialog from '@/components/transactions/TransactionFormDialog.vue'
import { formatLocalDateBR } from '@/lib/date'
import { formatSignedBRL } from '@/lib/money'
import { plural } from '@/lib/plural'
import { categoryLabel, installmentLabel, pixLabel, signedAmount, targetName } from '@/lib/transactionLabels'
import { listAccounts } from '@/services/accountService'
import { listCards } from '@/services/cardService'
import { listCategories } from '@/services/categoryService'
import { exportDetails, getDetails } from '@/services/dashboardService'
import { useAuthStore } from '@/stores/auth'
import MultiSelect, { type MultiSelectOption } from './MultiSelect.vue'
import type { Account } from '@/types/account'
import type { Category } from '@/types/category'
import type { CreditCard as Card } from '@/types/creditCard'
import type { DashboardDetails, DetailsFilter, DetailsSortField } from '@/types/dashboard'
import type { Transaction } from '@/types/transaction'

/**
 * Story 7.6: a Camada 3, a lista completa das transações confirmadas com filtros (guardados por usuário no
 * localStorage), ordenação, páginas de 50, edição pelo diálogo de /transacoes e export CSV do resultado inteiro.
 */
const emit = defineEmits<{ 'transaction-saved': [] }>()

const FIELD_CLASS =
  'flex min-h-[44px] w-full rounded-md border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 focus-visible:ring-2 focus-visible:ring-purple-600 focus-visible:outline-none'
const SUCCESS_TIMEOUT_MS = 5000
/** Direção do primeiro clique em cada cabeçalho. */
const DEFAULT_DIR: Record<DetailsSortField, 'asc' | 'desc'> = { date: 'desc', amount: 'desc', category: 'asc' }

type Preset = 'this-month' | 'last-month' | 'last-3-months' | 'custom'

const authStore = useAuthStore()
// Depois de um F5 o store começa vazio; o usuário logado continua no localStorage
if (!authStore.user) authStore.checkSession()
const stored = useLocalStorage(
  `porquinho:dashboard-layer3:${authStore.user?.id ?? 'anon'}`,
  {
    preset: 'this-month' as Preset,
    from: '',
    to: '',
    categoryIds: [] as string[],
    targets: [] as string[],
    q: '',
    sort: 'date' as DetailsSortField,
    dir: 'desc' as 'asc' | 'desc',
  },
  { mergeDefaults: true },
)

const qInput = ref(stored.value.q)
watchDebounced(qInput, (q) => (stored.value.q = q), { debounce: 300 })

const accounts = ref<Account[]>([])
const cards = ref<Card[]>([])
const categories = ref<Category[]>([])
const optionsLoading = ref(true)
const optionsFailed = ref(false)

const root = ref<HTMLElement | null>(null)
const details = ref<DashboardDetails | null>(null)
const loading = ref(false)
const error = ref(false)
const page = ref(0)
const exporting = ref(false)
const exportError = ref(false)
const successMessage = ref<string | null>(null)
const current = ref<Transaction | null>(null)
const editOpen = ref(false)

/** "AAAA-MM-DD" de hoje em São Paulo (en-CA formata assim). */
function todaySaoPaulo(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo' }).format(new Date())
}

/** Primeiro e último dia do mês `delta` meses a partir do atual (São Paulo). */
function monthBounds(delta: number): [string, string] {
  const [year, month] = todaySaoPaulo().split('-').map(Number)
  const first = new Date(Date.UTC(year!, month! - 1 + delta, 1))
  const last = new Date(Date.UTC(year!, month! + delta, 0))
  return [first.toISOString().slice(0, 10), last.toISOString().slice(0, 10)]
}

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/

/** O período do filtro, ou null quando o personalizado é inválido (não consulta). */
const period = computed<{ from: string; to: string } | null>(() => {
  const s = stored.value
  if (s.preset === 'this-month') return { from: monthBounds(0)[0], to: monthBounds(0)[1] }
  if (s.preset === 'last-month') return { from: monthBounds(-1)[0], to: monthBounds(-1)[1] }
  if (s.preset === 'last-3-months') return { from: monthBounds(-2)[0], to: monthBounds(0)[1] }
  if (!ISO_DATE.test(s.from) || !ISO_DATE.test(s.to) || s.from > s.to) return null
  return { from: s.from, to: s.to }
})

const filter = computed<DetailsFilter | null>(() => {
  if (!period.value) return null
  const s = stored.value
  return { ...period.value, categoryIds: s.categoryIds, targets: s.targets, q: s.q, sort: s.sort, dir: s.dir }
})

const canExport = computed(() => !!filter.value && !!details.value && details.value.totalItems > 0)

const sortChoice = computed({
  get: () => `${stored.value.sort},${stored.value.dir}`,
  set: (value: string) => {
    const [sort, dir] = value.split(',') as [DetailsSortField, 'asc' | 'desc']
    stored.value.sort = sort
    stored.value.dir = dir
  },
})

const categoryOptions = computed<MultiSelectOption[]>(() => {
  const byName = (a: Category, b: Category) => a.categoryName.localeCompare(b.categoryName, 'pt-BR')
  const parents = categories.value.filter((c) => c.parentCategoryId === null).sort(byName)
  return [
    { value: 'uncategorized', label: 'Não classificado' },
    ...parents.flatMap((parent) =>
      categories.value
        .filter((c) => c.parentCategoryId === parent.id)
        .sort(byName)
        .map((c) => ({ value: c.id, label: c.categoryName, group: parent.categoryName })),
    ),
  ]
})

const targetOptions = computed<MultiSelectOption[]>(() => [
  ...accounts.value.map((a) => ({ value: `account:${a.id}`, label: a.accountName, group: 'Contas' })),
  ...cards.value.map((c) => ({ value: `card:${c.id}`, label: c.cardName, group: 'Cartões' })),
])

function ariaSort(field: DetailsSortField) {
  if (stored.value.sort !== field) return 'none'
  return stored.value.dir === 'asc' ? 'ascending' : 'descending'
}

function arrow(field: DetailsSortField) {
  if (stored.value.sort !== field) return ''
  return stored.value.dir === 'asc' ? '↑' : '↓'
}

/** Primeiro clique escolhe o campo; o segundo inverte a direção. */
function sortBy(field: DetailsSortField) {
  if (stored.value.sort === field) stored.value.dir = stored.value.dir === 'asc' ? 'desc' : 'asc'
  else {
    stored.value.sort = field
    stored.value.dir = DEFAULT_DIR[field]
  }
}

/** Sequência das cargas: só a resposta da carga mais recente é aplicada. */
let request = 0

async function load() {
  const f = filter.value
  if (!f) return
  const currentRequest = ++request
  error.value = false
  loading.value = true
  try {
    const data = await getDetails(f, page.value)
    if (currentRequest !== request) return
    // a página ficou além do fim (ex.: a única linha da última página saiu do filtro): volta para a última
    if (data.items.length === 0 && page.value > 0 && page.value >= data.totalPages) {
      page.value = Math.max(data.totalPages - 1, 0)
      return void load()
    }
    details.value = data
  } catch {
    if (currentRequest === request) error.value = true
  } finally {
    if (currentRequest === request) loading.value = false
  }
}

function goTo(target: number) {
  page.value = target
  void load()
}

// Trocar qualquer filtro (ou a ordenação) volta para a página 1
watch(
  () => JSON.stringify(filter.value),
  () => {
    page.value = 0
    void load()
  },
)

/** Contas, cartões e categorias: opções dos filtros, nomes na lista e o diálogo. Sem eles a lista ainda funciona. */
async function loadOptions() {
  optionsLoading.value = true
  optionsFailed.value = false
  try {
    const [accountList, cardList, categoryList] = await Promise.all([listAccounts(), listCards(), listCategories()])
    accounts.value = accountList
    cards.value = cardList
    categories.value = categoryList
    // Filtro salvo obsoleto: id que não existe mais sai da seleção
    const categoryIds = new Set(categoryOptions.value.map((o) => o.value))
    const targets = new Set(targetOptions.value.map((o) => o.value))
    stored.value.categoryIds = stored.value.categoryIds.filter((id) => categoryIds.has(id))
    stored.value.targets = stored.value.targets.filter((id) => targets.has(id))
  } catch {
    optionsFailed.value = true
  } finally {
    optionsLoading.value = false
  }
}

function openEdit(row: Transaction) {
  current.value = row
  editOpen.value = true
}

/** O foco volta ao "Editar" visível da linha (tabela no desktop, card no mobile), se ela ainda estiver na página. */
function returnFocus(event: Event) {
  event.preventDefault()
  void nextTick(() => {
    const id = current.value?.id
    const buttons = id
      ? root.value?.querySelectorAll<HTMLElement>(
          `[data-testid="details-edit-${id}"], [data-testid="details-card-edit-${id}"]`,
        )
      : undefined
    const visible = [...(buttons ?? [])].find((b) => b.offsetParent !== null) ?? buttons?.[0]
    visible?.focus()
  })
}

let successTimer: ReturnType<typeof setTimeout> | undefined

async function onSaved({ message }: { message: string }) {
  emit('transaction-saved')
  await load()
  clearTimeout(successTimer)
  successMessage.value = message
  successTimer = setTimeout(() => (successMessage.value = null), SUCCESS_TIMEOUT_MS)
}

/** Excel pt-BR: o backend já manda `;`, vírgula decimal e BOM; aqui só baixa. */
async function exportCsv() {
  const f = filter.value
  if (!f || exporting.value) return
  exporting.value = true
  exportError.value = false
  try {
    const blob = await exportDetails(f)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `transacoes-${f.from}-a-${f.to}.csv`
    link.click()
    // revogar na hora pode abortar o download em alguns navegadores (Firefox)
    setTimeout(() => URL.revokeObjectURL(url), 0)
  } catch {
    exportError.value = true
  } finally {
    exporting.value = false
  }
}

onMounted(async () => {
  // As opções primeiro: o filtro salvo é limpo antes da primeira consulta
  await loadOptions()
  void load()
})
</script>
