import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'
import CategoryBudgetHistoryView from '../CategoryBudgetHistoryView.vue'
import AllocationHistoryChart from '@/components/budget/AllocationHistoryChart.vue'
import type { CategoryBudgetHistory, CategoryBudgetMonth } from '@/types/budget'

const mockGetHistory = vi.fn()

vi.mock('@/services/budgetService', () => ({
  getCategoryBudgetHistory: (...a: unknown[]) => mockGetHistory(...a),
}))

vi.mock('vue-router', async (importOriginal) => ({
  ...(await importOriginal<typeof import('vue-router')>()),
  useRoute: () => ({ params: { id: 'c1' } }),
}))

const plain = (s: string) => s.replace(/\s/g, ' ')

function monthKey(i: number): string {
  const d = new Date(Date.UTC(2025, 9 + i, 1))
  return `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}`
}

function m(month: string, allocated: number, spent: number): CategoryBudgetMonth {
  const remaining = allocated - spent
  return {
    month,
    allocated,
    spent,
    remaining,
    status: remaining < 0 ? 'OVERSPENT' : remaining <= allocated * 0.2 ? 'LOW' : 'HEALTHY',
  }
}

// Critério de aceite: Mercado com 14 meses alocados, de out/2025 a nov/2026 (hoje)
function mercado(months?: CategoryBudgetMonth[]): CategoryBudgetHistory {
  return {
    categoryId: 'c1',
    categoryName: 'Mercado',
    months: months ?? Array.from({ length: 14 }, (_, i) => m(monthKey(i), 1000, 500)),
  }
}

async function mountView() {
  const wrapper = mount(CategoryBudgetHistoryView, {
    attachTo: document.body,
    global: { stubs: { RouterLink: RouterLinkStub } },
  })
  await flushPromises()
  return wrapper
}

const rowMonths = (wrapper: Awaited<ReturnType<typeof mountView>>) =>
  wrapper.findAll('[data-testid^="history-row-"]').map((r) => r.attributes('data-testid')!.slice(12))

describe('CategoryBudgetHistoryView', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-11-15T15:00:00Z'))
    mockGetHistory.mockReset().mockResolvedValue(mercado())
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
    document.body.innerHTML = ''
  })

  it('abre em 1 ano: 12 meses, o mais novo no topo, gráfico e quatro estatísticas; "Tudo" mostra 14 sem nova chamada', async () => {
    const wrapper = await mountView()
    expect(mockGetHistory).toHaveBeenCalledWith('c1')
    expect(plain(wrapper.get('[data-testid="history-heading"]').text())).toBe('Histórico do orçamento — Mercado')
    expect(wrapper.get('[data-testid="range-1y"]').attributes('aria-pressed')).toBe('true')
    expect(rowMonths(wrapper)).toHaveLength(12)
    expect(rowMonths(wrapper)[0]).toBe('2026-11')
    expect(rowMonths(wrapper)[11]).toBe('2025-12')
    expect(wrapper.findComponent(AllocationHistoryChart).props('months')).toHaveLength(12)
    expect(wrapper.get('[data-testid="history-stats"]').findAll('dd')).toHaveLength(4)

    await wrapper.get('[data-testid="range-all"]').trigger('click')
    expect(rowMonths(wrapper)).toHaveLength(14)
    await wrapper.get('[data-testid="range-3m"]').trigger('click')
    expect(rowMonths(wrapper)).toEqual(['2026-11', '2026-10', '2026-09'])
    expect(mockGetHistory).toHaveBeenCalledTimes(1)
  })

  it('estatísticas dos meses filtrados: médias, estourados e melhor mês (no empate, o mais recente)', async () => {
    mockGetHistory.mockResolvedValue(
      mercado([m('2026-01', 100, 0), m('2026-09', 100, 150), m('2026-10', 300, 0), m('2026-11', 200, 250)]),
    )
    const wrapper = await mountView()
    expect(plain(wrapper.get('[data-testid="stat-avg-allocated"]').text())).toBe('R$ 175,00')
    expect(plain(wrapper.get('[data-testid="stat-avg-spent"]').text())).toBe('R$ 100,00')
    expect(wrapper.get('[data-testid="stat-overspent"]').text()).toBe('2')
    expect(wrapper.get('[data-testid="stat-best-month"]').text()).toBe('outubro de 2026')

    await wrapper.get('[data-testid="range-3m"]').trigger('click')
    expect(plain(wrapper.get('[data-testid="stat-avg-allocated"]').text())).toBe('R$ 200,00')
  })

  it('a linha mostra os valores e o status com ícone e o mesmo texto da tela do orçamento', async () => {
    mockGetHistory.mockResolvedValue(mercado([m('2026-10', 1000, 1234.56), m('2026-11', 1000, 900)]))
    const wrapper = await mountView()
    const over = plain(wrapper.get('[data-testid="history-row-2026-10"]').text())
    expect(over).toContain('outubro de 2026')
    expect(over).toContain('R$ 1.234,56')
    expect(over).toContain('-R$ 234,56')
    expect(over).toContain('Categoria estourada! R$ 234,56 acima do orçamento')
    expect(wrapper.get('[data-testid="history-row-2026-11"]').find('svg').exists()).toBe(true)
    expect(plain(wrapper.get('[data-testid="history-row-2026-11"]').text())).toContain(
      'Saldo baixo - R$ 100,00 restantes',
    )
  })

  it('o cabeçalho Mês alterna a ordem e o aria-sort', async () => {
    const wrapper = await mountView()
    const header = () => wrapper.get('[data-testid="sort-month-header"]')
    expect(header().attributes('aria-sort')).toBe('descending')
    await wrapper.get('[data-testid="sort-month"]').trigger('click')
    expect(header().attributes('aria-sort')).toBe('ascending')
    expect(rowMonths(wrapper)[0]).toBe('2025-12')
    await wrapper.get('[data-testid="sort-month"]').trigger('click')
    expect(rowMonths(wrapper)[0]).toBe('2026-11')
  })

  it('sem meses no período mostra a mensagem e esconde estatísticas, gráfico e CSV', async () => {
    mockGetHistory.mockResolvedValue(mercado([m('2026-01', 100, 0)]))
    const wrapper = await mountView()
    await wrapper.get('[data-testid="range-3m"]').trigger('click')
    expect(wrapper.get('[data-testid="history-empty"]').text()).toBe('Nenhum mês com orçamento neste período')
    expect(wrapper.find('[data-testid="history-stats"]').exists()).toBe(false)
    expect(wrapper.findComponent(AllocationHistoryChart).exists()).toBe(false)
    expect(wrapper.find('[data-testid="export-csv"]').exists()).toBe(false)
  })

  it.each([404, 400])('%i mostra "Esta categoria não existe mais" sem o seletor de período', async (status) => {
    mockGetHistory.mockRejectedValue({ response: { status } })
    const wrapper = await mountView()
    expect(wrapper.get('[data-testid="history-not-found"]').text()).toContain('Esta categoria não existe mais.')
    expect(wrapper.find('[data-testid="range-selector"]').exists()).toBe(false)
  })

  it('outro erro mostra "Tentar de novo", que recarrega', async () => {
    mockGetHistory.mockRejectedValueOnce(new Error('rede'))
    const wrapper = await mountView()
    expect(wrapper.find('[data-testid="history-load-error"]').exists()).toBe(true)
    await wrapper.get('[data-testid="history-retry"]').trigger('click')
    await flushPromises()
    expect(rowMonths(wrapper)).toHaveLength(12)
  })

  it('exporta CSV com BOM, `;`, vírgula decimal e só os meses filtrados', async () => {
    mockGetHistory.mockResolvedValue({
      ...mercado([m('2026-01', 50, 0), m('2026-10', 1000, 1234.56), m('2026-11', 1500, 0)]),
      categoryName: 'Alimentação & Casa',
    })
    let blob: Blob | null = null
    URL.createObjectURL = vi.fn((b: Blob) => {
      blob = b
      return 'blob:x'
    })
    URL.revokeObjectURL = vi.fn()
    let download = ''
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      download = this.download
    })
    const wrapper = await mountView()
    await wrapper.get('[data-testid="range-3m"]').trigger('click')
    await wrapper.get('[data-testid="export-csv"]').trigger('click')

    expect(download).toBe('historico-alimentacao-casa.csv')
    const buffer = await new Promise<ArrayBuffer>((resolve) => {
      const reader = new FileReader()
      reader.onload = () => resolve(reader.result as ArrayBuffer)
      reader.readAsArrayBuffer(blob!)
    })
    const bytes = new Uint8Array(buffer)
    expect(Array.from(bytes.slice(0, 3))).toEqual([0xef, 0xbb, 0xbf])
    const text = new TextDecoder().decode(bytes.slice(3))
    expect(text.split('\r\n')).toEqual([
      'Mês;Alocado;Gasto;Restante;Status',
      'outubro de 2026;1.000,00;1.234,56;-234,56;Categoria estourada! R$ 234,56 acima do orçamento',
      'novembro de 2026;1.500,00;0,00;1.500,00;Saudável',
      '',
    ])
  })
})
