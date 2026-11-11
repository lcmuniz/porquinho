import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, RouterLinkStub, type VueWrapper } from '@vue/test-utils'
import BudgetComparisonView from '../BudgetComparisonView.vue'
import PlannedVsActualChart from '@/components/budget/PlannedVsActualChart.vue'
import type { Budget, CategoryBudget } from '@/types/budget'

const mockGet = vi.fn()

vi.mock('@/services/budgetService', () => ({ getBudget: (...a: unknown[]) => mockGet(...a) }))
vi.mock('@/services/categoryService', () => ({
  listCategories: async () => [
    { id: 'g1', categoryName: 'Essenciais', parentCategoryId: null },
    { id: 'c1', categoryName: 'Mercado', parentCategoryId: 'g1' },
    { id: 'c2', categoryName: 'Transporte', parentCategoryId: 'g1' },
    { id: 'c3', categoryName: 'Farmácia', parentCategoryId: 'g1' },
    { id: 'c4', categoryName: 'Lazer', parentCategoryId: 'g1' },
    { id: 'g2', categoryName: 'Casa', parentCategoryId: null },
    { id: 'c5', categoryName: 'Luz', parentCategoryId: 'g2' },
    // grupo sem filhas no orçamento não aparece
    { id: 'g3', categoryName: 'Vazio', parentCategoryId: null },
  ],
}))

const plain = (s: string) => s.replace(/\s/g, ' ')

function cb(categoryId: string, allocated: number | null, spent: number): CategoryBudget {
  const remaining = allocated === null ? null : allocated - spent
  return { categoryId, allocated, spent, committed: null, remaining, status: null }
}

// 5 filhas: Mercado estourada, Transporte saudável, Farmácia baixa, Lazer sem orçamento com gasto, Luz alocada em 0
function budget(month = '2026-11', categories?: CategoryBudget[]): Budget {
  return {
    month,
    totalAvailable: 5000,
    totalAllocated: 2100,
    categories: categories ?? [
      cb('c1', 400, 500),
      cb('c2', 200, 50),
      cb('c3', 1500, 1234.56),
      cb('c4', null, 80),
      cb('c5', 0, 0),
    ],
  }
}

let wrapper: VueWrapper | null = null

async function mountView() {
  wrapper = mount(BudgetComparisonView, {
    attachTo: document.body,
    global: { stubs: { RouterLink: RouterLinkStub } },
  })
  await flushPromises()
  return wrapper
}

const rowIds = (w: VueWrapper) =>
  w.findAll('[data-testid^="row-"]').map((r) => r.attributes('data-testid')!.slice(4))
const groupIds = (w: VueWrapper) =>
  w.findAll('tbody[data-testid^="group-"]').map((r) => r.attributes('data-testid')!.slice(6))

describe('BudgetComparisonView', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-11-15T15:00:00Z'))
    mockGet.mockReset().mockImplementation(async (month: string) => budget(month))
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    vi.useRealTimers()
    vi.restoreAllMocks()
    document.body.innerHTML = ''
  })

  it('abre no mês atual com o maior estouro no topo, gráfico das alocadas e os quatro totais', async () => {
    const w = await mountView()
    expect(mockGet).toHaveBeenCalledExactlyOnceWith('2026-11')
    expect(w.get('[data-testid="period-current"]').attributes('aria-pressed')).toBe('true')
    expect(w.get('[data-testid="sort-difference-header"]').attributes('aria-sort')).toBe(
      'ascending',
    )
    expect(w.get('[data-testid="sort-name-header"]').attributes('aria-sort')).toBe('none')
    // grupo Casa (diferença 0) antes de Essenciais (235,44); sem orçamento por último
    expect(groupIds(w)).toEqual(['g2', 'g1'])
    expect(rowIds(w)).toEqual(['c5', 'c1', 'c2', 'c3', 'c4'])

    const chart = w.findComponent(PlannedVsActualChart)
    expect(chart.props('items').map((i: { id: string }) => i.id)).toEqual(['c5', 'c1', 'c2', 'c3'])
    expect(chart.findAll('rect')).toHaveLength(8)

    expect(plain(w.get('[data-testid="total-allocated"]').text())).toBe('R$ 2.100,00')
    // retro do Epic 6 (A3): gasto orçado + sem orçamento (Lazer) = gasto real
    expect(plain(w.get('[data-testid="total-spent"]').text())).toBe('R$ 1.784,56')
    expect(plain(w.get('[data-testid="total-unbudgeted"]').text())).toBe('R$ 80,00')
    expect(plain(w.get('[data-testid="total-remaining"]').text())).toBe('R$ 315,44')
    expect(w.get('[data-testid="total-percent"]').text()).toBe('85% do orçamento utilizado')
  })

  it('linhas: valores, %, status com ícone; sem orçamento e alocado 0 mostram "—"', async () => {
    const w = await mountView()
    const over = plain(w.get('[data-testid="row-c1"]').text())
    expect(over).toContain('R$ 400,00')
    expect(over).toContain('-R$ 100,00')
    expect(over).toContain('125%')
    expect(over).toContain('Categoria estourada! R$ 100,00 acima do orçamento')
    expect(w.get('[data-testid="row-c1"]').find('svg').exists()).toBe(true)

    const none = w
      .get('[data-testid="row-c4"]')
      .findAll('td')
      .map((td) => plain(td.text()))
    expect(none).toEqual(['—', 'R$ 80,00', '—', '—', 'Sem orçamento'])

    const zero = w
      .get('[data-testid="row-c5"]')
      .findAll('td')
      .map((td) => plain(td.text()))
    expect(zero[3]).toBe('—')
    expect(zero[4]).toBe('Saldo baixo - R$ 0,00 restantes')

    // linha do grupo: soma das filhas orçadas e status recalculado; o gasto sem orçamento fica à parte
    const group = plain(w.get('[data-testid="group-g1"] tr').text())
    expect(group).toContain('Essenciais')
    expect(group).toContain('R$ 2.100,00')
    expect(group).toContain('R$ 1.784,56')
    expect(group).toContain('Saldo baixo - R$ 315,44 restantes')
    expect(plain(w.get('[data-testid="unbudgeted-g1"]').text())).toBe('Sem orçamento: R$ 80,00')
    expect(w.find('[data-testid="unbudgeted-g2"]').exists()).toBe(false)
  })

  it('o gasto sem orçamento não estoura o grupo; grupo só sem orçamento fica como antes', async () => {
    mockGet.mockResolvedValue(
      budget('2026-11', [cb('c1', 100, 90), cb('c4', null, 500), cb('c5', null, 30)]),
    )
    const w = await mountView()
    const group = plain(w.get('[data-testid="group-g1"] tr').text())
    expect(group).toContain('R$ 90,00')
    expect(group).not.toContain('estourada')
    expect(plain(w.get('[data-testid="unbudgeted-g1"]').text())).toBe('Sem orçamento: R$ 500,00')

    const only = w
      .get('[data-testid="group-g2"] tr')
      .findAll('td')
      .map((td) => plain(td.text()))
    expect(only).toEqual(['—', 'R$ 30,00', '—', '—', 'Sem orçamento'])
    expect(w.find('[data-testid="unbudgeted-g2"]').exists()).toBe(false)

    expect(plain(w.get('[data-testid="total-spent"]').text())).toBe('R$ 90,00')
    expect(plain(w.get('[data-testid="total-unbudgeted"]').text())).toBe('R$ 530,00')
  })

  it('sem nenhuma linha orçada, todo o gasto vai para "Sem orçamento" nos totais', async () => {
    mockGet.mockResolvedValue(budget('2026-11', [cb('c4', null, 80), cb('c5', null, 30)]))
    const w = await mountView()
    expect(plain(w.get('[data-testid="total-allocated"]').text())).toBe('R$ 0,00')
    expect(plain(w.get('[data-testid="total-spent"]').text())).toBe('R$ 0,00')
    expect(plain(w.get('[data-testid="total-unbudgeted"]').text())).toBe('R$ 110,00')
    expect(w.find('[data-testid="total-percent"]').exists()).toBe(false)
  })

  it('o grupo abre e fecha mantendo o total', async () => {
    const w = await mountView()
    const toggle = () => w.get('[data-testid="toggle-g1"]')
    expect(toggle().attributes('aria-expanded')).toBe('true')
    await toggle().trigger('click')
    expect(toggle().attributes('aria-expanded')).toBe('false')
    expect(rowIds(w)).toEqual(['c5'])
    expect(plain(w.get('[data-testid="group-g1"]').text())).toContain('R$ 2.100,00')
    expect(w.find('[data-testid="unbudgeted-g1"]').exists()).toBe(true)
    await toggle().trigger('click')
    expect(rowIds(w)).toHaveLength(5)
  })

  it('ordenação por coluna alterna aria-sort e vale dentro do grupo', async () => {
    const w = await mountView()
    await w.get('[data-testid="sort-name"]').trigger('click')
    expect(w.get('[data-testid="sort-name-header"]').attributes('aria-sort')).toBe('ascending')
    expect(w.get('[data-testid="sort-difference-header"]').attributes('aria-sort')).toBe('none')
    expect(groupIds(w)).toEqual(['g2', 'g1'])
    expect(rowIds(w)).toEqual(['c5', 'c3', 'c4', 'c1', 'c2'])
    await w.get('[data-testid="sort-name"]').trigger('click')
    expect(w.get('[data-testid="sort-name-header"]').attributes('aria-sort')).toBe('descending')
    expect(groupIds(w)).toEqual(['g1', 'g2'])
    expect(rowIds(w)).toEqual(['c2', 'c1', 'c4', 'c3', 'c5'])
  })

  it('filtros de status: "Sem orçamento" só em Todas; totais sobre as linhas visíveis; vazio esconde gráfico e CSV', async () => {
    const w = await mountView()
    await w.get('[data-testid="filter-OVERSPENT"]').trigger('click')
    expect(rowIds(w)).toEqual(['c1'])
    expect(plain(w.get('[data-testid="total-allocated"]').text())).toBe('R$ 400,00')
    expect(w.get('[data-testid="total-percent"]').text()).toBe('125% do orçamento utilizado')
    expect(w.find('[data-testid="unbudgeted-g1"]').exists()).toBe(false)
    expect(w.find('[data-testid="total-unbudgeted"]').exists()).toBe(false)
    await w.get('[data-testid="filter-HEALTHY"]').trigger('click')
    expect(rowIds(w)).toEqual(['c2'])

    mockGet.mockResolvedValue(budget('2026-11', [cb('c2', 200, 50), cb('c5', 0, 0)]))
    await w.get('[data-testid="period-previous"]').trigger('click')
    await flushPromises()
    await w.get('[data-testid="filter-OVERSPENT"]').trigger('click')
    expect(w.get('[data-testid="comparison-empty"]').text()).toBe(
      'Nenhuma categoria para comparar neste período',
    )
    expect(w.findComponent(PlannedVsActualChart).exists()).toBe(false)
    expect(w.find('[data-testid="export-csv"]').exists()).toBe(false)

    // só alocação 0: % do orçamento oculto
    await w.get('[data-testid="filter-LOW"]').trigger('click')
    expect(rowIds(w)).toEqual(['c5'])
    expect(w.find('[data-testid="total-percent"]').exists()).toBe(false)
  })

  it('ordem decrescente e por status deixam "Sem orçamento" por último', async () => {
    const w = await mountView()
    await w.get('[data-testid="sort-difference"]').trigger('click')
    expect(w.get('[data-testid="sort-difference-header"]').attributes('aria-sort')).toBe(
      'descending',
    )
    expect(rowIds(w)).toEqual(['c3', 'c2', 'c1', 'c4', 'c5'])
    await w.get('[data-testid="sort-difference"]').trigger('click')
    expect(rowIds(w)).toEqual(['c5', 'c1', 'c2', 'c3', 'c4'])

    await w.get('[data-testid="sort-status"]').trigger('click')
    expect(w.get('[data-testid="sort-status-header"]').attributes('aria-sort')).toBe('ascending')
    // Essenciais: estourada → baixa → saudável → sem orçamento
    expect(rowIds(w).slice(1)).toEqual(['c1', 'c3', 'c2', 'c4'])
  })

  it('só a carga do período mais recente é aplicada', async () => {
    const w = await mountView()
    let resolveFirst: (b: Budget) => void = () => {}
    mockGet
      .mockReturnValueOnce(new Promise<Budget>((resolve) => (resolveFirst = resolve)))
      .mockResolvedValueOnce(budget('2026-11', [cb('c2', 200, 50)]))
    await w.get('[data-testid="period-previous"]').trigger('click')
    await w.get('[data-testid="period-current"]').trigger('click')
    await flushPromises()
    expect(rowIds(w)).toEqual(['c2'])

    resolveFirst(budget('2026-10', [cb('c1', 400, 500)]))
    await flushPromises()
    expect(rowIds(w)).toEqual(['c2'])
    expect(plain(w.get('[data-testid="total-spent"]').text())).toBe('R$ 50,00')
  })

  it('falha de rede mostra "Tentar de novo", que recarrega', async () => {
    mockGet.mockRejectedValueOnce(new Error('rede'))
    const w = await mountView()
    expect(w.find('[data-testid="comparison-load-error"]').exists()).toBe(true)
    await w.get('[data-testid="comparison-retry"]').trigger('click')
    await flushPromises()
    expect(rowIds(w)).toHaveLength(5)
  })

  it('cada troca de período recarrega; personalizado soma os meses e valida o intervalo', async () => {
    const w = await mountView()
    await w.get('[data-testid="period-previous"]').trigger('click')
    await flushPromises()
    expect(mockGet).toHaveBeenLastCalledWith('2026-10')
    await w.get('[data-testid="period-current"]').trigger('click')
    await flushPromises()
    expect(mockGet).toHaveBeenCalledTimes(3)

    mockGet.mockImplementation(async (month: string) =>
      month === '2026-09'
        ? budget(month, [cb('c1', 100, 50), cb('c4', null, 10)])
        : budget(month, [cb('c1', null, 20), cb('c4', null, 10)]),
    )
    await w.get('[data-testid="period-custom"]').trigger('click')
    await w.get('[data-testid="custom-from"]').setValue('2026-09')
    await flushPromises()
    mockGet.mockClear()
    await w.get('[data-testid="custom-to"]').setValue('2026-10')
    await flushPromises()
    expect(mockGet.mock.calls.map((c) => c[0])).toEqual(['2026-09', '2026-10'])
    // alocado em um mês só já conta como "com orçamento"
    const mercado = w
      .get('[data-testid="row-c1"]')
      .findAll('td')
      .map((td) => plain(td.text()))
    expect(mercado).toEqual(['R$ 100,00', 'R$ 70,00', 'R$ 30,00', '70%', 'Saudável'])
    expect(w.get('[data-testid="row-c4"]').text()).toContain('Sem orçamento')
    expect(w.get('[data-testid="period-label"]').text()).toBe('setembro de 2026 a outubro de 2026')

    mockGet.mockClear()
    await w.get('[data-testid="custom-to"]').setValue('2026-08')
    await flushPromises()
    expect(w.get('[data-testid="range-error"]').text()).toBe(
      'Escolha até 12 meses, com o mês final depois do inicial',
    )
    await w.get('[data-testid="custom-from"]').setValue('2024-01')
    await w.get('[data-testid="custom-to"]').setValue('2026-11')
    await flushPromises()
    expect(w.find('[data-testid="range-error"]').exists()).toBe(true)
    expect(mockGet).not.toHaveBeenCalled()
    await w.get('[data-testid="custom-from"]').setValue('2025-12')
    await flushPromises()
    expect(mockGet).toHaveBeenCalledTimes(12)
  })

  it('exporta CSV só das linhas filtradas (Baixas), com BOM, `;` e vírgula decimal', async () => {
    let blob: Blob | null = null
    URL.createObjectURL = vi.fn((b: Blob) => {
      blob = b
      return 'blob:x'
    })
    URL.revokeObjectURL = vi.fn()
    let download = ''
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
      this: HTMLAnchorElement,
    ) {
      download = this.download
    })
    const w = await mountView()
    await w.get('[data-testid="filter-LOW"]').trigger('click')
    await w.get('[data-testid="export-csv"]').trigger('click')

    expect(download).toBe('planejado-x-real-2026-11.csv')
    const buffer = await new Promise<ArrayBuffer>((resolve) => {
      const reader = new FileReader()
      reader.onload = () => resolve(reader.result as ArrayBuffer)
      reader.readAsArrayBuffer(blob!)
    })
    const bytes = new Uint8Array(buffer)
    expect(Array.from(bytes.slice(0, 3))).toEqual([0xef, 0xbb, 0xbf])
    expect(new TextDecoder().decode(bytes.slice(3)).split('\r\n')).toEqual([
      'Grupo;Categoria;Alocado;Gasto;Diferença;Percentual;Status',
      'Casa;Luz;0,00;0,00;0,00;;Saldo baixo - R$ 0,00 restantes',
      'Essenciais;Farmácia;1.500,00;1.234,56;265,44;82;Saldo baixo - R$ 265,44 restantes',
      '',
    ])
  })

  it('"Imprimir / salvar PDF" chama window.print', async () => {
    const print = vi.spyOn(window, 'print').mockImplementation(() => {})
    const w = await mountView()
    await w.get('[data-testid="print"]').trigger('click')
    expect(print).toHaveBeenCalledOnce()
  })
})
