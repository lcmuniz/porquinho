<template>
  <main class="min-h-screen bg-gray-50 px-4 py-8 print:min-h-0 print:bg-white print:p-0">
    <div class="mx-auto max-w-3xl space-y-6">
      <div class="flex items-start justify-between gap-4">
        <div>
          <h1 class="text-2xl font-semibold text-gray-900">Planejado × real</h1>
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
            <Input
              v-model="customFrom"
              type="month"
              placeholder="AAAA-MM"
              class="min-h-[44px]"
              data-testid="custom-from"
            />
          </label>
          <label class="space-y-1 text-sm text-gray-700">
            <span class="block">Mês final</span>
            <Input
              v-model="customTo"
              type="month"
              placeholder="AAAA-MM"
              class="min-h-[44px]"
              data-testid="custom-to"
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
        <span class="sr-only">Carregando a comparação...</span>
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
          <div class="flex flex-wrap justify-end gap-2 print:hidden">
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
                  <th
                    v-for="column in COLUMNS"
                    :key="column.key"
                    scope="col"
                    class="px-3 py-2 font-medium"
                    :class="{ 'text-right': column.numeric }"
                    :aria-sort="
                      sortKey === column.key ? (ascending ? 'ascending' : 'descending') : 'none'
                    "
                    :data-testid="`sort-${column.key}-header`"
                  >
                    <button
                      type="button"
                      class="inline-flex min-h-[44px] items-center gap-1 font-medium hover:text-gray-900"
                      :data-testid="`sort-${column.key}`"
                      @click="sortBy(column.key)"
                    >
                      {{ column.label }}
                      <component
                        :is="ascending ? ArrowUp : ArrowDown"
                        v-if="sortKey === column.key"
                        class="size-4"
                        aria-hidden="true"
                      />
                    </button>
                  </th>
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
                  <tr
                    v-for="row in group.children"
                    :key="row.id"
                    class="border-b border-gray-100"
                    :data-testid="`row-${row.id}`"
                  >
                    <th scope="row" class="py-2 pr-3 pl-8 text-left font-normal text-gray-900">
                      {{ row.name }}
                    </th>
                    <RowCells :row="row" />
                  </tr>
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
import { RouterLink } from 'vue-router'
import { ArrowDown, ArrowUp, ChevronDown, ChevronRight } from 'lucide-vue-next'
import PlannedVsActualChart from '@/components/budget/PlannedVsActualChart.vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { STATUS, computeStatus } from '@/lib/budgetStatus'
import { currentMonth, formatMonthBR, shiftMonth } from '@/lib/date'
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
  /** Gasto das filhas sem orçamento, à parte do alocado (retro do Epic 6, A3); 0 nas filhas. */
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

const period = ref<Period>('current')
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
 * Soma em centavos só das linhas com orçamento; o gasto das demais fica em `unbudgeted`, sem entrar no status. Se
 * nenhuma linha tiver orçamento, o alocado é nulo e o gasto é o de todas, como uma filha sem orçamento.
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
  row.unbudgeted = (spent(rows) - spent(withBudget)) / 100
  return row
}

/** Uma linha por filha devolvida em algum mês do período, somando os meses. */
const childRows = computed(() => {
  const sums = new Map<string, { allocated: number | null; spent: number }>()
  for (const budget of data.value?.budgets ?? []) {
    for (const c of budget.categories) {
      const sum = sums.get(c.categoryId) ?? { allocated: null, spent: 0 }
      if (c.allocated !== null) sum.allocated = (sum.allocated ?? 0) + Math.round(c.allocated * 100)
      sum.spent += Math.round(c.spent * 100)
      sums.set(c.categoryId, sum)
    }
  }
  const rows = new Map<string, Row>()
  for (const c of data.value?.categories ?? []) {
    const sum = sums.get(c.id)
    if (c.parentCategoryId !== null && sum)
      rows.set(c.id, makeRow(c.id, c.categoryName, sum.allocated, sum.spent))
  }
  return rows
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
    .sort((a, b) => compare(a.row, b.row)),
)

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
        h('td', { class: 'px-3 py-2' }, [
          h('span', { class: 'inline-flex items-center gap-1 font-medium' }, [
            r.status
              ? h(STATUS[r.status].icon, {
                  class: ['size-4 shrink-0', STATUS[r.status].text],
                  'aria-hidden': 'true',
                })
              : null,
            statusText(r),
          ]),
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
    'Grupo;Categoria;Alocado;Gasto;Diferença;Percentual;Status',
    ...groups.value.flatMap((g) =>
      g.children.map((r) =>
        [
          g.row.name,
          r.name,
          money(r.allocated),
          money(r.spent),
          money(r.difference),
          r.percent === null ? '' : String(r.percent),
          statusText(r).replace(/\s/g, ' '),
        ]
          .map((cell) => cell.replace(/;/g, ','))
          .join(';'),
      ),
    ),
  ]
  const list = months.value!
  const suffix = list.length === 1 ? list[0] : `${list[0]}_${list[list.length - 1]}`
  const blob = new Blob(['\uFEFF' + lines.join('\r\n') + '\r\n'], {
    type: 'text/csv;charset=utf-8',
  })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `planejado-x-real-${suffix}.csv`
  link.click()
  // revogar na hora pode abortar o download em alguns navegadores (Firefox)
  setTimeout(() => URL.revokeObjectURL(url), 0)
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
