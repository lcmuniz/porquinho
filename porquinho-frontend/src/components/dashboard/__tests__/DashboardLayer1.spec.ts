import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'
import DashboardLayer1 from '../DashboardLayer1.vue'
import type { DashboardOverview } from '@/types/dashboard'

const mockGetOverview = vi.fn()
vi.mock('@/services/dashboardService', () => ({
  getOverview: () => mockGetOverview(),
}))

// formatBRL usa espaço não separável entre "R$" e o número
const brl = (text: string) => text.replace(/\s/g, ' ')

function overview(overrides: Partial<DashboardOverview> = {}): DashboardOverview {
  return {
    totalBalance: 1500,
    categories: [
      { categoryId: 'c1', name: 'Lazer', status: 'OVERSPENT', remaining: -20 },
      { categoryId: 'c2', name: 'Mercado', status: 'LOW', remaining: 30 },
      { categoryId: 'c3', name: 'Casa', status: 'HEALTHY', remaining: 200 },
    ],
    mainGoal: { name: 'Reserva de Emergência', currentAmount: 250, targetAmount: 1000, percent: 25 },
    monthSpent: 320,
    monthAllocated: 1000,
    projectedBalance: 800,
    wealthHistory: [
      { date: '2026-08-12', balance: 1000 },
      { date: '2026-11-12', balance: 1500 },
    ],
    ...overrides,
  }
}

function mountIt() {
  return mount(DashboardLayer1, { global: { stubs: { RouterLink: RouterLinkStub } } })
}

async function mountWith(o: DashboardOverview) {
  mockGetOverview.mockResolvedValue(o)
  const wrapper = mountIt()
  await flushPromises()
  return wrapper
}

describe('DashboardLayer1', () => {
  beforeEach(() => {
    mockGetOverview.mockReset()
  })

  it('mostra os 5 cards; os 4 da grade de métricas ficam em 1 coluna no mobile e 2 no desktop', async () => {
    const wrapper = await mountWith(overview())

    expect(wrapper.get('[data-testid="metrics-grid"]').classes()).toEqual(
      expect.arrayContaining(['grid', 'grid-cols-1', 'md:grid-cols-2', 'gap-4']),
    )
    const cards = ['balance', 'categories', 'goal', 'spent', 'projection'].map((id) =>
      wrapper.get(`[data-testid="card-${id}"]`),
    )
    for (const card of cards) expect(card.classes()).toContain('p-6')
    expect(brl(cards[0]!.text())).toContain('R$ 1.500,00')
    expect(brl(cards[0]!.get('.text-4xl').text())).toBe('R$ 1.500,00')
    expect(brl(cards[3]!.text())).toContain('Você gastou R$ 320,00 de R$ 1.000,00 planejados este mês')
    expect(brl(cards[4]!.get('.text-4xl').text())).toBe('R$ 800,00')
    expect(cards[4]!.get('.text-4xl').classes()).not.toContain('text-red-600')
  })

  it('projeção negativa aparece em vermelho, com o sinal', async () => {
    const wrapper = await mountWith(overview({ projectedBalance: -250 }))
    const value = wrapper.get('[data-testid="card-projection"] .text-4xl')

    expect(value.classes()).toContain('text-red-600')
    expect(brl(value.text())).toContain('250,00')
    expect(value.text()).toContain('-')
  })

  it('lista as categorias na ordem do backend, com ícone e texto, e destaca as estouradas', async () => {
    const wrapper = await mountWith(overview())
    const card = wrapper.get('[data-testid="card-categories"]')

    expect(card.get('.text-4xl').text()).toBe('1 de 3')
    expect(card.text()).toContain('Preciso ajustar?')
    const items = card.findAll('li')
    expect(items.map((li) => brl(li.text()))).toEqual([
      'Lazer: Categoria estourada! R$ 20,00 acima do orçamento',
      'Mercado: Saldo baixo - R$ 30,00 restantes',
      'Casa: Saudável',
    ])
    for (const li of items) expect(li.find('svg').exists()).toBe(true)
    expect(items[0]!.classes()).toContain('bg-red-50')
    expect(items[1]!.classes()).not.toContain('bg-red-50')
  })

  it('sem estourada não pergunta "Preciso ajustar?"', async () => {
    const wrapper = await mountWith(
      overview({ categories: [{ categoryId: 'c3', name: 'Casa', status: 'HEALTHY', remaining: 200 }] }),
    )
    expect(wrapper.text()).not.toContain('Preciso ajustar?')
  })

  it('a meta mostra nome, valores e a barra com role="progressbar"', async () => {
    const wrapper = await mountWith(overview())
    const card = wrapper.get('[data-testid="card-goal"]')

    expect(card.get('.text-4xl').text()).toBe('25%')
    expect(brl(card.text())).toContain('Reserva de Emergência')
    expect(brl(card.text())).toContain('R$ 250,00 de R$ 1.000,00')
    const bar = card.get('[role="progressbar"]')
    expect(bar.attributes('aria-valuenow')).toBe('25')
    expect(bar.get('div').attributes('style')).toContain('width: 25%')
  })

  it('nada orçado: card de categorias vazio com link para /orcamento e R$ 0,00 de R$ 0,00', async () => {
    const wrapper = await mountWith(overview({ categories: [], monthSpent: 0, monthAllocated: 0 }))

    expect(wrapper.get('[data-testid="categories-empty"]').text()).toContain(
      'Nenhuma categoria com orçamento neste mês',
    )
    expect(wrapper.getComponent(RouterLinkStub).props('to')).toBe('/orcamento')
    expect(brl(wrapper.get('[data-testid="card-spent"]').text())).toContain('Você gastou R$ 0,00 de R$ 0,00')
  })

  it('sem meta mostra "Nenhuma meta criada ainda"', async () => {
    const wrapper = await mountWith(overview({ mainGoal: null }))

    expect(wrapper.get('[data-testid="goal-empty"]').text()).toBe('Nenhuma meta criada ainda')
    expect(wrapper.find('[role="progressbar"]').exists()).toBe(false)
  })

  it('enquanto a busca não volta mostra "Carregando…"', () => {
    mockGetOverview.mockReturnValue(new Promise(() => {}))
    const wrapper = mountIt()

    const loading = wrapper.get('[data-testid="overview-loading"]')
    expect(loading.text()).toBe('Carregando…')
    expect(loading.attributes('role')).toBe('status')
  })

  it('falha na API mostra a mensagem de erro no lugar dos cards', async () => {
    mockGetOverview.mockRejectedValue({ response: { status: 500 } })
    const wrapper = mountIt()
    await flushPromises()

    expect(wrapper.get('[data-testid="overview-error"]').text()).toBe('Não foi possível carregar a visão geral')
    expect(wrapper.find('[data-testid="card-balance"]').exists()).toBe(false)
  })

  it('a curva do patrimônio ocupa 2/3 no desktop, com o card de saldo ao lado; no mobile, largura toda', async () => {
    const wrapper = await mountWith(overview())
    const row = wrapper.get('[data-testid="wealth-row"]')

    expect(row.classes()).toEqual(expect.arrayContaining(['grid', 'grid-cols-1', 'lg:grid-cols-3']))
    const chart = row.get('[data-testid="wealth-chart"]')
    expect(chart.classes()).toEqual(expect.arrayContaining(['lg:col-span-2', 'min-w-0']))
    expect(row.find('[data-testid="card-balance"]').exists()).toBe(true)
    expect(brl(chart.text())).toContain('+R$ 500,00 em 3 meses')
  })

  it('falha na API não mostra o gráfico', async () => {
    mockGetOverview.mockRejectedValue(new Error('network'))
    const wrapper = mountIt()
    await flushPromises()

    expect(wrapper.find('[data-testid="wealth-chart"]').exists()).toBe(false)
  })
})
