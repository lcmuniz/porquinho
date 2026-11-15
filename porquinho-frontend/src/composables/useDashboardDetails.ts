import { computed, ref, watch } from 'vue'
import { useLocalStorage, watchDebounced } from '@vueuse/core'
import type { MultiSelectOption } from '@/components/common/MultiSelect.vue'
import { monthBounds } from '@/lib/date'
import { categoryFilterOptions, targetFilterOptions } from '@/lib/transactionLabels'
import { listAccounts } from '@/services/accountService'
import { listCards } from '@/services/cardService'
import { listCategories } from '@/services/categoryService'
import { getDetails } from '@/services/dashboardService'
import { useAuthStore } from '@/stores/auth'
import type { Account } from '@/types/account'
import type { Category } from '@/types/category'
import type { CreditCard } from '@/types/creditCard'
import type { DashboardDetails, DetailsFilter, DetailsSortField } from '@/types/dashboard'

/**
 * Story 7.6 (retro Epic 7 A6/F5): filtros da Camada 3 guardados por usuário no localStorage, opções dos filtros,
 * carga paginada com ordenação. O export CSV é o TransactionExportDialog (Story 9.1).
 */
export type Preset = 'this-month' | 'last-month' | 'last-3-months' | 'custom'

/** Direção do primeiro clique em cada cabeçalho. */
const DEFAULT_DIR: Record<DetailsSortField, 'asc' | 'desc'> = { date: 'desc', amount: 'desc', category: 'asc' }
const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/

export function useDashboardDetails() {
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
  const cards = ref<CreditCard[]>([])
  const categories = ref<Category[]>([])
  const optionsLoading = ref(true)
  const optionsFailed = ref(false)

  const details = ref<DashboardDetails | null>(null)
  const loading = ref(false)
  const error = ref(false)
  const page = ref(0)

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

  const sortChoice = computed({
    get: () => `${stored.value.sort},${stored.value.dir}`,
    set: (value: string) => {
      const [sort, dir] = value.split(',') as [DetailsSortField, 'asc' | 'desc']
      stored.value.sort = sort
      stored.value.dir = dir
    },
  })

  const categoryOptions = computed<MultiSelectOption[]>(() => categoryFilterOptions(categories.value))
  const targetOptions = computed<MultiSelectOption[]>(() => targetFilterOptions(accounts.value, cards.value))

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

  return {
    stored,
    qInput,
    accounts,
    cards,
    categories,
    optionsLoading,
    optionsFailed,
    details,
    loading,
    error,
    page,
    period,
    filter,
    sortChoice,
    categoryOptions,
    targetOptions,
    ariaSort,
    arrow,
    sortBy,
    load,
    goTo,
    loadOptions,
  }
}
