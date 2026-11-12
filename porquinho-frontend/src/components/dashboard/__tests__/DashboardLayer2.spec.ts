import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import DashboardLayer2 from '../DashboardLayer2.vue'
import type { CategorySpending, CategoryTrend, DashboardTactical } from '@/types/dashboard'
import type { Transaction } from '@/types/transaction'

const mockGetTactical = vi.fn()
vi.mock('@/services/dashboardService', () => ({
  getTactical: () => mockGetTactical(),
}))
const mockList = vi.fn()
vi.mock('@/services/transactionService', () => ({
  listTransactions: (month: string) => mockList(month),
}))

// formatBRL usa espaço não separável entre "R$" e o número
const brl = (text: string | null | undefined) => (text ?? '').replace(/\s/g, ' ')

function cat(id: string | null, name: string, spent: number, allocated: number | null = null): CategorySpending {
  return {
    categoryId: id,
    name,
    colorHex: id ? '#000000' : null,
    spent,
    allocated,
    status: allocated === null ? null : spent > allocated ? 'OVERSPENT' : 'HEALTHY',
  }
}

function trend(id: string | null, name: string, values: number[]): CategoryTrend {
  return {
    categoryId: id,
    name,
    months: values.map((spent, i) => ({ month: `2026-${String(i + 1).padStart(2, '0')}`, spent })),
  }
}

function tactical(overrides: Partial<DashboardTactical> = {}): DashboardTactical {
  return {
    month: '2026-11',
    categories: [
      cat('c1', 'Lazer', 620, 500),
      cat('c2', 'Mercado', 400, 600),
      cat('c3', 'Casa', 300),
      cat(null, 'Não classificado', 200),
      cat('c5', 'Saúde', 100),
      cat('c6', 'Pets', 50),
      cat('c7', 'Livros', 30),
      cat('c8', 'Viagem', 0, 1000),
    ],
    trends: [trend('c1', 'Lazer', Array(12).fill(10))],
    ...overrides,
  }
}

let wrapper: VueWrapper | null = null

async function mountWith(t: DashboardTactical) {
  mockGetTactical.mockResolvedValue(t)
  wrapper = mount(DashboardLayer2, { attachTo: document.body })
  await flushPromises()
  return wrapper
}

const inDialog = (testId: string) => document.body.querySelector<HTMLElement>(`[data-testid="${testId}"]`)

function tx(id: string, categoryId: string | null, overrides: Partial<Transaction> = {}): Transaction {
  return {
    id,
    date: '2026-11-05',
    description: `Compra ${id}`,
    amount: 10,
    type: 'DEBIT',
    kind: 'REGULAR',
    category: categoryId ? { id: categoryId, name: 'X', group: null } : null,
    isFutureInstallment: false,
    ...overrides,
  } as Transaction
}

describe('DashboardLayer2', () => {
  beforeEach(() => {
    mockGetTactical.mockReset()
    mockList.mockReset()
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
  })

  it('carrega com uma chamada só e mostra os 4 cards em 1 coluna, 2 a partir de xl', async () => {
    const w = await mountWith(tactical())

    expect(mockGetTactical).toHaveBeenCalledTimes(1)
    expect(w.get('[data-testid="tactical-grid"]').classes()).toEqual(
      expect.arrayContaining(['grid', 'grid-cols-1', 'xl:grid-cols-2']),
    )
    for (const id of ['donut', 'top5', 'planned', 'trend'])
      expect(w.find(`[data-testid="card-${id}"]`).exists()).toBe(true)
  })

  it('donut com as 5 maiores em Chart 2–6, "Outras" em cinza e total = gasto do mês', async () => {
    const w = await mountWith(tactical())

    const slices = w.findAll('[data-testid^="donut-slice-"]')
    expect(slices.map((s) => s.attributes('stroke'))).toEqual([
      '#06B6D4',
      '#F59E0B',
      '#10B981',
      '#EC4899',
      '#8B5CF6',
      '#9CA3AF',
    ])
    expect(brl(slices[5]!.attributes('aria-label'))).toBe('Outras: R$ 80,00')
    expect(slices[5]!.attributes('role')).toBe('img')
    expect(brl(slices[3]!.attributes('aria-label'))).toBe('Não classificado: R$ 200,00')
    expect(brl(w.get('[data-testid="spending-donut-svg"]').attributes('aria-label'))).toContain(
      'total R$ 1.700,00',
    )
    expect(w.findAll('[data-testid^="top5-"]').map((b) => b.text().split('R$')[0]!.trim())).toEqual([
      'Lazer',
      'Mercado',
      'Casa',
      'Não classificado',
      'Saúde',
    ])
  })

  it('com 2 categorias: 2 fatias, sem "Outras", top 5 com 2 barras', async () => {
    const w = await mountWith(
      tactical({ categories: [cat('c1', 'Lazer', 100), cat('c2', 'Mercado', 50)] }),
    )

    expect(w.findAll('[data-testid^="donut-slice-"]')).toHaveLength(2)
    expect(w.find('[data-testid="donut-slice-others"]').exists()).toBe(false)
    expect(w.findAll('[data-testid^="top5-"]')).toHaveLength(2)
  })

  it('sem gasto e sem orçamento: mensagens no lugar do donut, do top 5 e do planejado × real', async () => {
    const w = await mountWith(tactical({ categories: [], trends: [] }))

    expect(w.get('[data-testid="donut-empty"]').text()).toBe('Nenhum gasto neste mês')
    expect(w.get('[data-testid="top5-empty"]').text()).toBe('Nenhum gasto neste mês')
    expect(w.get('[data-testid="planned-empty"]').text()).toBe(
      'Nenhuma categoria com orçamento neste mês',
    )
  })

  it('planejado × real: só com alocação, pior diferença primeiro, vermelho quando estoura, com texto', async () => {
    const w = await mountWith(tactical())

    const rows = w.findAll('[data-testid^="planned-c"]').filter((r) => r.element.tagName === 'BUTTON')
    expect(rows.map((r) => r.attributes('data-testid'))).toEqual([
      'planned-c1',
      'planned-c2',
      'planned-c8',
    ])
    expect(brl(rows[0]!.text())).toContain('R$ 620,00 de R$ 500,00')
    expect(w.get('[data-testid="planned-bar-c1"]').classes()).toContain('bg-red-600')
    expect(w.get('[data-testid="planned-bar-c2"]').classes()).toContain('bg-green-600')
    expect(w.get('[data-testid="planned-bar-c8"]').classes()).toContain('bg-green-600')
  })

  it('erro na API: mensagem só na Camada 2', async () => {
    mockGetTactical.mockRejectedValue(new Error('500'))
    wrapper = mount(DashboardLayer2)
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toBe('Não foi possível carregar a visão tática')
  })

  it('trocar 3 → 12 meses não faz nova chamada', async () => {
    const w = await mountWith(tactical())

    await w.get('[data-testid="trend-range-12"]').trigger('click')

    expect(mockGetTactical).toHaveBeenCalledTimes(1)
    expect(w.findAll('[data-testid="trend-line-c1"] circle')).toHaveLength(12)
  })

  it('clicar numa barra abre o drill-down do mês com só as transações de gasto da categoria', async () => {
    mockList.mockResolvedValue([
      tx('t1', 'c1'),
      tx('t2', 'c2'),
      tx('t3', 'c1', { type: 'CREDIT' }),
      tx('t4', 'c1', { isFutureInstallment: true }),
      tx('t5', 'c1', { kind: 'CARD_PAYMENT' }),
    ])
    const w = await mountWith(tactical())

    await w.get('[data-testid="top5-c1"]').trigger('click')
    await flushPromises()

    expect(mockList).toHaveBeenCalledWith('2026-11')
    expect(inDialog('drilldown-dialog')?.textContent).toContain('Lazer')
    expect(inDialog('drilldown-dialog')?.textContent).toContain('novembro de 2026')
    expect(inDialog('drilldown-list')?.querySelectorAll('li')).toHaveLength(1)
    expect(inDialog('drilldown-list')?.textContent).toContain('Compra t1')
  })

  it('falha ao listar as transações mostra o erro no drill-down', async () => {
    mockList.mockRejectedValue(new Error('500'))
    const w = await mountWith(tactical())

    await w.get('[data-testid="top5-c1"]').trigger('click')
    await flushPromises()

    expect(inDialog('drilldown-error')?.textContent).toContain('Não foi possível carregar as transações')
    expect(inDialog('drilldown-loading')).toBeNull()
  })

  it('Enter numa fatia do "Não classificado" lista as sem categoria; Esc fecha e devolve o foco', async () => {
    mockList.mockResolvedValue([tx('t1', null), tx('t2', 'c1')])
    const w = await mountWith(tactical())
    const slice = w.get('[data-testid="donut-slice-uncategorized"]')
    ;(slice.element as SVGElement).focus()

    await slice.trigger('keydown', { key: 'Enter' })
    await flushPromises()

    expect(inDialog('drilldown-list')?.textContent).toContain('Compra t1')
    expect(inDialog('drilldown-list')?.textContent).not.toContain('Compra t2')

    inDialog('drilldown-dialog')!.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }),
    )
    await flushPromises()

    expect(inDialog('drilldown-dialog')).toBeNull()
    expect(document.activeElement).toBe(slice.element)
  })
})
