<template>
  <main class="px-4 py-6 md:px-8 md:py-8 print:min-h-0 print:bg-white print:p-0">
    <div class="mx-auto max-w-4xl space-y-6">
      <div class="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h1 class="text-2xl font-bold text-gray-900 md:text-[28px]">Planejado × real</h1>
          <p class="mt-1 text-sm text-gray-600 first-letter:uppercase" data-testid="period-label">
            {{ periodLabel }}
          </p>
        </div>
        <RouterLink
          to="/orcamento"
          data-testid="back-to-budget"
          class="inline-flex min-h-[44px] shrink-0 items-center rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100 print:hidden"
        >
          Voltar ao orçamento
        </RouterLink>
      </div>

      <div class="space-y-3 print:hidden">
        <div
          role="group"
          aria-label="Período"
          class="flex flex-wrap gap-2"
          data-testid="period-selector"
        >
          <Button
            v-for="option in PERIOD_OPTIONS"
            :key="option.value"
            variant="outline"
            class="min-h-[44px]"
            :class="pressedClass(period === option.value)"
            :aria-pressed="period === option.value ? 'true' : 'false'"
            :data-testid="`period-${option.value}`"
            @click="period = option.value"
          >
            {{ option.label }}
          </Button>
        </div>

        <div v-if="period === 'custom'" class="flex flex-wrap gap-3" data-testid="custom-range">
          <label class="space-y-1 text-sm text-gray-700">
            <span class="block">Mês inicial</span>
            <MonthInput
              v-model="customFrom"
              testid="custom-from"
            />
          </label>
          <label class="space-y-1 text-sm text-gray-700">
            <span class="block">Mês final</span>
            <MonthInput
              v-model="customTo"
              testid="custom-to"
            />
          </label>
        </div>

        <div
          role="group"
          aria-label="Status"
          class="flex flex-wrap gap-2"
          data-testid="status-filter"
        >
          <Button
            v-for="option in FILTER_OPTIONS"
            :key="option.value"
            variant="outline"
            class="min-h-[44px]"
            :class="pressedClass(filter === option.value)"
            :aria-pressed="filter === option.value ? 'true' : 'false'"
            :data-testid="`filter-${option.value}`"
            @click="filter = option.value"
          >
            {{ option.label }}
          </Button>
        </div>
      </div>

      <p
        v-if="months === null"
        role="alert"
        class="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900"
        data-testid="range-error"
      >
        Escolha até 12 meses, com o mês final depois do inicial
      </p>

      <div v-else-if="loading" class="space-y-2" aria-busy="true" data-testid="comparison-skeleton">
        <div class="h-64 animate-pulse rounded-lg bg-gray-100" />
        <span class="sr-only">Carregando a comparação…</span>
      </div>

      <div
        v-else-if="loadFailed"
        role="alert"
        data-testid="comparison-load-error"
        class="flex flex-col gap-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3 sm:flex-row sm:items-center sm:justify-between"
      >
        <p class="text-sm text-red-800">
          Não foi possível carregar a comparação. Verifique sua conexão e tente de novo.
        </p>
        <Button
          variant="outline"
          class="min-h-[44px] shrink-0"
          data-testid="comparison-retry"
          @click="load"
        >
          Tentar de novo
        </Button>
      </div>

      <template v-else-if="data">
        <p
          v-if="groups.length === 0"
          class="rounded-lg border border-gray-200 bg-white p-6 text-center text-gray-600"
          data-testid="comparison-empty"
        >
          Nenhuma categoria para comparar neste período
        </p>

        <template v-else>
          <div class="flex flex-wrap gap-2 sm:justify-end print:hidden">
            <Button
              variant="outline"
              class="min-h-[44px]"
              data-testid="export-csv"
              @click="exportCsv"
            >
              Exportar CSV
            </Button>
            <Button variant="outline" class="min-h-[44px]" data-testid="print" @click="print">
              Imprimir / salvar PDF
            </Button>
          </div>

          <section
            v-if="chartItems.length > 0"
            class="rounded-lg border border-gray-200 bg-white p-4"
          >
            <PlannedVsActualChart :items="chartItems" />
          </section>

          <div class="overflow-x-auto rounded-lg border border-gray-200 bg-white">
            <table class="w-full text-left text-sm" data-testid="comparison-table">
              <thead class="border-b border-gray-200 bg-gray-50 text-gray-600">
                <tr>
                  <SortableHeader
                    v-for="column in COLUMNS"
                    :key="column.key"
                    class="px-3 py-2 font-medium"
                    :class="{ 'text-right': column.numeric }"
                    :label="column.label"
                    :sort-id="column.key"
                    :sort="
                      sortKey === column.key ? (ascending ? 'ascending' : 'descending') : 'none'
                    "
                    @sort="sortBy(column.key)"
                  />
                </tr>
              </thead>
              <tbody
                v-for="group in groups"
                :key="group.row.id"
                :data-testid="`group-${group.row.id}`"
              >
                <tr class="border-b border-gray-200 bg-gray-50 font-semibold text-gray-900">
                  <th scope="row" class="px-3 py-2 text-left">
                    <button
                      type="button"
                      class="inline-flex min-h-[44px] items-center gap-1 text-left"
                      :aria-expanded="collapsed.has(group.row.id) ? 'false' : 'true'"
                      :data-testid="`toggle-${group.row.id}`"
                      @click="toggle(group.row.id)"
                    >
                      <component
                        :is="collapsed.has(group.row.id) ? ChevronRight : ChevronDown"
                        class="size-4 shrink-0 print:hidden"
                        aria-hidden="true"
                      />
                      {{ group.row.name }}
                    </button>
                  </th>
                  <RowCells :row="group.row" />
                </tr>
                <tr
                  v-if="group.row.unbudgeted > 0"
                  class="border-b border-gray-100 text-gray-600"
                  :data-testid="`unbudgeted-${group.row.id}`"
                >
                  <th
                    scope="row"
                    :colspan="COLUMNS.length"
                    class="py-2 pr-3 pl-8 text-left font-normal"
                  >
                    {{ NO_BUDGET }}: {{ formatBRL(group.row.unbudgeted) }}
                  </th>
                </tr>
                <template v-if="!collapsed.has(group.row.id)">
                  <template v-for="row in group.children" :key="row.id">
                    <tr class="border-b border-gray-100" :data-testid="`row-${row.id}`">
                      <th scope="row" class="py-2 pr-3 pl-8 text-left font-normal text-gray-900">
                        {{ row.name }}
                      </th>
                      <RowCells :row="row" />
                    </tr>
                    <tr
                      v-if="row.unbudgeted > 0"
                      class="border-b border-gray-100 text-gray-600"
                      :data-testid="`unbudgeted-${row.id}`"
                    >
                      <th
                        scope="row"
                        :colspan="COLUMNS.length"
                        class="py-2 pr-3 pl-12 text-left font-normal"
                      >
                        {{ NO_BUDGET }}: {{ formatBRL(row.unbudgeted) }}
                      </th>
                    </tr>
                  </template>
                </template>
              </tbody>
            </table>
          </div>

          <dl class="grid grid-cols-2 gap-3 sm:grid-cols-3" data-testid="comparison-totals">
            <div class="rounded-lg border border-gray-200 bg-white p-3">
              <dt class="text-xs text-gray-600">Total alocado</dt>
              <dd class="font-semibold text-gray-900" data-testid="total-allocated">
                {{ formatBRL(totals.allocated) }}
              </dd>
            </div>
            <div class="rounded-lg border border-gray-200 bg-white p-3">
              <dt class="text-xs text-gray-600">Gasto orçado</dt>
              <dd class="font-semibold text-gray-900" data-testid="total-spent">
                {{ formatBRL(totals.spent) }}
              </dd>
            </div>
            <div class="rounded-lg border border-gray-200 bg-white p-3">
              <dt class="text-xs text-gray-600">Total restante</dt>
              <dd
                class="font-semibold"
                :class="totals.remaining < 0 ? 'text-red-600' : 'text-gray-900'"
                data-testid="total-remaining"
              >
                {{ formatBRL(totals.remaining) }}
              </dd>
            </div>
            <div
              v-if="totals.unbudgeted > 0"
              class="rounded-lg border border-gray-200 bg-white p-3"
            >
              <dt class="text-xs text-gray-600">{{ NO_BUDGET }}</dt>
              <dd class="font-semibold text-gray-900" data-testid="total-unbudgeted">
                {{ formatBRL(totals.unbudgeted) }}
              </dd>
            </div>
          </dl>
          <p
            v-if="totals.percent !== null"
            class="text-sm text-gray-900"
            data-testid="total-percent"
          >
            {{ totals.percent }}% do orçamento utilizado
          </p>
        </template>
      </template>
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ChevronDown, ChevronRight } from 'lucide-vue-next'
import BudgetStatusLabel from '@/components/budget/BudgetStatusLabel.vue'
import PlannedVsActualChart from '@/components/budget/PlannedVsActualChart.vue'
import SortableHeader from '@/components/common/SortableHeader.vue'
import { Button } from '@/components/ui/button'
import MonthInput from '@/components/common/MonthInput.vue'
import { STATUS, computeStatus } from '@/lib/budgetStatus'
import { currentMonth, formatMonthBR, shiftMonth } from '@/lib/date'
import { csvLine, downloadCsv } from '@/lib/download'
import { formatBRL, formatBRLInput } from '@/lib/money'
import { getBudget } from '@/services/budgetService'
import { listCategories } from '@/services/categoryService'
import type { Budget, BudgetStatus } from '@/types/budget'
import type { Category } from '@/types/category'

/**
 * Story 6.9: planejado × real montado no front sobre GET /budgets/{mês} (uma chamada por mês do período, em paralelo)
 * e a lista de categorias. Valores somados em centavos.
 */
type Period = 'current' | 'previous' | 'custom'
type Filter = 'all' | BudgetStatus
type SortKey = 'name' | 'allocated' | 'spent' | 'difference' | 'percent' | 'status'

interface Row {
  id: string
  name: string
  allocated: number | null
  spent: number
  difference: number | null
  percent: number | null
  status: BudgetStatus | null
  /**
   * Gasto fora do orçamento, à parte do alocado (retro do Epic 6, A3): no grupo, o das filhas sem orçamento mais o
   * das filhas orçadas nos meses sem orçamento; na filha orçada em algum mês, o dos meses sem orçamento (pré-10.4,
   * decisão A); 0 na filha sem orçamento em nenhum mês.
   */
  unbudgeted: number
}

const PERIOD_OPTIONS: { value: Period; label: string }[] = [
  { value: 'current', label: 'Mês atual' },
  { value: 'previous', label: 'Mês passado' },
  { value: 'custom', label: 'Personalizado' },
]
const FILTER_OPTIONS: { value: Filter; label: string }[] = [
  { value: 'all', label: 'Todas' },
  { value: 'HEALTHY', label: 'Saudáveis' },
  { value: 'LOW', label: 'Baixas' },
  { value: 'OVERSPENT', label: 'Estouradas' },
]
const COLUMNS: { key: SortKey; label: string; numeric: boolean }[] = [
  { key: 'name', label: 'Categoria', numeric: false },
  { key: 'allocated', label: 'Alocado', numeric: true },
  { key: 'spent', label: 'Gasto', numeric: true },
  { key: 'difference', label: 'Diferença', numeric: true },
  { key: 'percent', label: '%', numeric: true },
  { key: 'status', label: 'Status', numeric: false },
]
const MAX_MONTHS = 12
const NO_BUDGET = 'Sem orçamento'
// Ordem crescente do status: pior primeiro
const STATUS_RANK: Record<BudgetStatus, number> = { OVERSPENT: 0, LOW: 1, HEALTHY: 2 }

const route = useRoute()
const router = useRouter()
// o período escolhido fica na URL (?periodo=); valor desconhecido cai no mês atual
const period = ref<Period>(PERIOD_OPTIONS.find((o) => o.value === route.query.periodo)?.value ?? 'current')
watch(period, (periodo) => void router.replace({ query: { ...route.query, periodo } }))
const customFrom = ref(currentMonth())
const customTo = ref(currentMonth())
const filter = ref<Filter>('all')
const sortKey = ref<SortKey>('difference')
const ascending = ref(true)
const collapsed = ref(new Set<string>())
const data = ref<{ categories: Category[]; budgets: Budget[] } | null>(null)
const loading = ref(true)
const loadFailed = ref(false)

/** Meses do período (AAAA-MM, do mais antigo ao mais novo); null = intervalo personalizado inválido. */
const months = computed<string[] | null>(() => {
  const now = currentMonth()
  if (period.value === 'current') return [now]
  if (period.value === 'previous') return [shiftMonth(now, -1)]
  const from = customFrom.value
  const to = customTo.value
  if (!/^\d{4}-\d{2}$/.test(from) || !/^\d{4}-\d{2}$/.test(to) || to < from) return null
  const list = [from]
  while (list[list.length - 1]! < to) {
    if (list.length === MAX_MONTHS) return null
    list.push(shiftMonth(list[list.length - 1]!, 1))
  }
  return list
})

const periodLabel = computed(() => {
  const list = months.value
  if (!list) return ''
  const first = formatMonthBR(list[0]!)
  return list.length === 1 ? first : `${first} a ${formatMonthBR(list[list.length - 1]!)}`
})

function makeRow(id: string, name: string, allocatedCents: number | null, spentCents: number): Row {
  const allocated = allocatedCents === null ? null : allocatedCents / 100
  const difference = allocatedCents === null ? null : (allocatedCents - spentCents) / 100
  return {
    id,
    name,
    allocated,
    spent: spentCents / 100,
    difference,
    percent: allocatedCents ? Math.round((spentCents / allocatedCents) * 100) : null,
    status: allocated === null ? null : computeStatus(allocated, difference!),
    unbudgeted: 0,
  }
}

/**
 * Soma em centavos só das linhas com orçamento; o gasto das demais, mais o `unbudgeted` das orçadas, fica em
 * `unbudgeted`, sem entrar no status. Se nenhuma linha tiver orçamento, o alocado é nulo e o gasto é o de todas, como
 * uma filha sem orçamento.
 */
function sumRows(id: string, name: string, rows: Row[]): Row {
  const cents = (v: number) => Math.round(v * 100)
  const spent = (list: Row[]) => list.reduce((t, r) => t + cents(r.spent), 0)
  const withBudget = rows.filter((r) => r.allocated !== null)
  if (withBudget.length === 0) return makeRow(id, name, null, spent(rows))
  const row = makeRow(
    id,
    name,
    withBudget.reduce((t, r) => t + cents(r.allocated!), 0),
    spent(withBudget),
  )
  const budgetedUnbudgeted = withBudget.reduce((t, r) => t + cents(r.unbudgeted), 0)
  row.unbudgeted = (spent(rows) - spent(withBudget) + budgetedUnbudgeted) / 100
  return row
}

/**
 * Uma linha por filha devolvida em algum mês do período, somando os meses. Na filha orçada em algum mês, alocado e
 * gasto são só dos meses com orçamento e o gasto dos outros vai para `unbudgeted` (pré-10.4, decisão A).
 */
const childRows = computed(() => {
  const sums = new Map<string, { allocated: number | null; budgeted: number; unbudgeted: number }>()
  for (const budget of data.value?.budgets ?? []) {
    for (const c of budget.categories) {
      const sum = sums.get(c.categoryId) ?? { allocated: null, budgeted: 0, unbudgeted: 0 }
      const spent = Math.round(c.spent * 100)
      if (c.allocated !== null) {
        sum.allocated = (sum.allocated ?? 0) + Math.round(c.allocated * 100)
        sum.budgeted += spent
      } else sum.unbudgeted += spent
      sums.set(c.categoryId, sum)
    }
  }
  const rows = new Map<string, Row>()
  for (const c of data.value?.categories ?? []) {
    const sum = sums.get(c.id)
    if (c.parentCategoryId === null || !sum) continue
    // Sem orçamento e sem gasto no período não há o que comparar: a linha seria só "—" (critique: parede de traços)
    if (sum.allocated === null && sum.unbudgeted === 0) continue
    // Sem orçamento em nenhum mês: gasto total, como antes
    if (sum.allocated === null) {
      rows.set(c.id, makeRow(c.id, c.categoryName, null, sum.unbudgeted))
      continue
    }
    const row = makeRow(c.id, c.categoryName, sum.allocated, sum.budgeted)
    row.unbudgeted = sum.unbudgeted / 100
    rows.set(c.id, row)
  }
  return rows
})

// Filhas excluídas que mantiveram orçamento de meses fechados (retro Epic 6 A4): grupo próprio, para o total bater
const removedRows = computed(() => {
  const sums = new Map<string, { name: string; allocated: number; spent: number }>()
  for (const budget of data.value?.budgets ?? []) {
    for (const c of budget.categories) {
      if (!c.removedCategoryName) continue
      const sum = sums.get(c.categoryId) ?? { name: c.removedCategoryName, allocated: 0, spent: 0 }
      sum.allocated += Math.round((c.allocated ?? 0) * 100)
      sum.spent += Math.round(c.spent * 100)
      sums.set(c.categoryId, sum)
    }
  }
  return [...sums].map(([id, s]) => makeRow(id, s.name, s.allocated, s.spent))
})

const visible = (r: Row) => filter.value === 'all' || r.status === filter.value

function compare(a: Row, b: Row): number {
  const byName =
    a.name.localeCompare(b.name, 'pt-BR', { sensitivity: 'base' }) || a.id.localeCompare(b.id)
  if (sortKey.value === 'name') return ascending.value ? byName : -byName
  const value = (r: Row) =>
    sortKey.value === 'status'
      ? r.status
        ? STATUS_RANK[r.status]
        : null
      : r[sortKey.value as Exclude<SortKey, 'name' | 'status'>]
  const va = value(a)
  const vb = value(b)
  // Sem valor (sem orçamento) sempre por último
  if (va === null || vb === null) return va === vb ? byName : va === null ? 1 : -1
  return (ascending.value ? va - vb : vb - va) || byName
}

const groups = computed(() =>
  (data.value?.categories ?? [])
    .filter((c) => c.parentCategoryId === null)
    .map((parent) => {
      const children = (data.value?.categories ?? [])
        .filter((c) => c.parentCategoryId === parent.id)
        .map((c) => childRows.value.get(c.id))
        .filter((r): r is Row => !!r && visible(r))
        .sort(compare)
      return { row: sumRows(parent.id, parent.categoryName, children), children }
    })
    .filter((g) => g.children.length > 0)
    .sort((a, b) => compare(a.row, b.row))
    .concat(removedGroup.value),
)

const removedGroup = computed(() => {
  const children = removedRows.value.filter(visible).sort(compare)
  return children.length > 0 ? [{ row: sumRows('removed', 'Categorias excluídas', children), children }] : []
})

const filteredRows = computed(() => groups.value.flatMap((g) => g.children))

const totals = computed(() => {
  const total = sumRows('total', 'Total', filteredRows.value)
  // sem nenhuma orçada, todo o gasto é sem orçamento
  if (total.allocated === null)
    return { allocated: 0, spent: 0, remaining: 0, percent: null, unbudgeted: total.spent }
  return {
    allocated: total.allocated,
    spent: total.spent,
    remaining: total.difference!,
    percent: total.percent,
    unbudgeted: total.unbudgeted,
  }
})

const chartItems = computed(() =>
  filteredRows.value
    .filter((r) => r.allocated !== null)
    .map((r) => ({ id: r.id, name: r.name, allocated: r.allocated!, spent: r.spent })),
)

function statusText(r: Row): string {
  return r.status ? STATUS[r.status].label(r.difference!) : NO_BUDGET
}

/** Células numéricas e de status, iguais para filha e grupo. */
const RowCells = defineComponent({
  props: { row: { type: Object as () => Row, required: true } },
  setup(props) {
    const num = 'px-3 py-2 text-right whitespace-nowrap'
    return () => {
      const r = props.row
      return [
        h('td', { class: num }, r.allocated === null ? '—' : formatBRL(r.allocated)),
        h('td', { class: num }, formatBRL(r.spent)),
        h(
          'td',
          { class: [num, r.difference !== null && r.difference < 0 ? 'text-red-600' : ''] },
          r.difference === null ? '—' : formatBRL(r.difference),
        ),
        h('td', { class: num }, r.percent === null ? '—' : `${r.percent}%`),
        h('td', { class: 'min-w-52 px-3 py-2' }, [
          h(
            BudgetStatusLabel,
            { status: r.status, remaining: r.difference },
            { default: () => NO_BUDGET },
          ),
        ]),
      ]
    }
  },
})

function pressedClass(pressed: boolean) {
  return pressed
    ? 'border-purple-600 bg-purple-50 text-purple-700 hover:bg-purple-100'
    : 'text-gray-700'
}

function sortBy(key: SortKey) {
  if (sortKey.value === key) ascending.value = !ascending.value
  else {
    sortKey.value = key
    ascending.value = true
  }
}

function toggle(id: string) {
  const next = new Set(collapsed.value)
  if (!next.delete(id)) next.add(id)
  collapsed.value = next
}

function print() {
  window.print()
}

/** Mesmo formato da 6.8: `;`, vírgula decimal e BOM. Só as linhas filtradas, na ordem da tabela. */
function exportCsv() {
  const money = (v: number | null) => (v === null ? '' : formatBRLInput(v))
  const lines = [
    'Grupo;Categoria;Alocado;Gasto;Diferença;Percentual;Status;Gasto sem orçamento',
    ...groups.value.flatMap((g) =>
      g.children.map((r) =>
        csvLine([
          g.row.name,
          r.name,
          money(r.allocated),
          money(r.spent),
          money(r.difference),
          r.percent === null ? '' : String(r.percent),
          statusText(r),
          // vazia na filha sem orçamento em nenhum mês: o gasto dela já está todo em Gasto
          r.allocated === null ? '' : money(r.unbudgeted),
        ]),
      ),
    ),
  ]
  const list = months.value!
  const suffix = list.length === 1 ? list[0] : `${list[0]}_${list[list.length - 1]}`
  downloadCsv(lines, `planejado-x-real-${suffix}.csv`)
}

/** Sequência das cargas: só a resposta da carga mais recente é aplicada. */
let request = 0

async function load() {
  const current = ++request
  const list = months.value
  if (!list) return
  loading.value = true
  loadFailed.value = false
  try {
    const [categories, ...budgets] = await Promise.all([
      listCategories(),
      ...list.map((m) => getBudget(m)),
    ])
    if (current !== request) return
    data.value = { categories: categories as Category[], budgets: budgets as Budget[] }
  } catch {
    if (current !== request) return
    loadFailed.value = true
  } finally {
    if (current === request) loading.value = false
  }
}

onMounted(load)

// Cada troca de período busca de novo no servidor (tempo real)
watch([period, customFrom, customTo], () => void load())
</script>
