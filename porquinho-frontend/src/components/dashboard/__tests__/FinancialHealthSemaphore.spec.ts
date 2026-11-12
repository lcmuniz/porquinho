import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import FinancialHealthSemaphore from '../FinancialHealthSemaphore.vue'
import type { FinancialHealth } from '@/types/dashboard'

const mockGetFinancialHealth = vi.fn()
vi.mock('@/services/dashboardService', () => ({
  getFinancialHealth: () => mockGetFinancialHealth(),
}))

function health(overrides: Partial<FinancialHealth> = {}): FinancialHealth {
  return {
    status: 'HEALTHY',
    healthyCategories: 9,
    lowCategories: 1,
    overspentCategories: 0,
    negativeAccounts: 0,
    goalsBehind: 0,
    ...overrides,
  }
}

async function mountWith(h: FinancialHealth) {
  mockGetFinancialHealth.mockResolvedValue(h)
  const wrapper = mount(FinancialHealthSemaphore)
  await flushPromises()
  return wrapper
}

describe('FinancialHealthSemaphore', () => {
  beforeEach(() => {
    mockGetFinancialHealth.mockReset()
  })

  it.each([
    ['HEALTHY', 'Saudável'],
    ['ATTENTION', 'Atenção'],
    ['CRITICAL', 'Crítico'],
  ] as const)('%s mostra "Saúde Financeira: %s"', async (status, label) => {
    const wrapper = await mountWith(health({ status }))
    expect(wrapper.get('[data-testid="health-toggle"]').text()).toBe(`Saúde Financeira: ${label}`)
  })

  it('fica fixo no topo', async () => {
    const wrapper = await mountWith(health())
    expect(wrapper.classes()).toEqual(expect.arrayContaining(['sticky', 'top-0']))
  })

  it('o clique abre e fecha o detalhe e alterna aria-expanded', async () => {
    const wrapper = await mountWith(health())
    const toggle = wrapper.get('[data-testid="health-toggle"]')
    const detail = wrapper.get('[data-testid="health-detail"]')

    expect(toggle.element.tagName).toBe('BUTTON')
    expect(toggle.attributes('aria-controls')).toBe('health-detail')
    expect(detail.attributes('id')).toBe('health-detail')
    expect(toggle.attributes('aria-expanded')).toBe('false')
    expect(detail.attributes('style')).toContain('display: none')

    await toggle.trigger('click')
    expect(toggle.attributes('aria-expanded')).toBe('true')
    expect(detail.attributes('style') ?? '').not.toContain('display: none')

    await toggle.trigger('click')
    expect(toggle.attributes('aria-expanded')).toBe('false')
  })

  it('o detalhe usa o plural certo e acrescenta contas e metas quando houver', async () => {
    const wrapper = await mountWith(
      health({ status: 'CRITICAL', healthyCategories: 1, lowCategories: 2, overspentCategories: 1, negativeAccounts: 2, goalsBehind: 1 }),
    )
    expect(wrapper.findAll('[data-testid="health-detail"] li').map((li) => li.text())).toEqual([
      '1 categoria saudável, 2 com saldo baixo, 1 estourada',
      '2 contas com saldo negativo',
      '1 meta atrasada',
    ])
  })

  it('plural com várias e sem contas ou metas não acrescenta linhas', async () => {
    const wrapper = await mountWith(health({ healthyCategories: 8, lowCategories: 1, overspentCategories: 2 }))
    expect(wrapper.findAll('[data-testid="health-detail"] li').map((li) => li.text())).toEqual([
      '8 categorias saudáveis, 1 com saldo baixo, 2 estouradas',
    ])
  })

  it('sem categoria orçada mostra "Nenhuma categoria com orçamento neste mês"', async () => {
    const wrapper = await mountWith(
      health({ healthyCategories: 0, lowCategories: 0, overspentCategories: 0, negativeAccounts: 1, goalsBehind: 2 }),
    )
    expect(wrapper.findAll('[data-testid="health-detail"] li').map((li) => li.text())).toEqual([
      'Nenhuma categoria com orçamento neste mês',
      '1 conta com saldo negativo',
      '2 metas atrasadas',
    ])
  })

  it('enquanto a busca não volta mostra "Carregando saúde financeira…"', () => {
    mockGetFinancialHealth.mockReturnValue(new Promise(() => {}))
    const wrapper = mount(FinancialHealthSemaphore)

    expect(wrapper.get('[data-testid="health-loading"]').text()).toBe('Carregando saúde financeira…')
    expect(wrapper.find('[data-testid="health-toggle"]').exists()).toBe(false)
  })

  it('falha na API mostra a mensagem de erro no lugar do semáforo', async () => {
    mockGetFinancialHealth.mockRejectedValue({ response: { status: 500 } })
    const wrapper = mount(FinancialHealthSemaphore)
    await flushPromises()

    expect(wrapper.get('[data-testid="health-error"]').text()).toBe(
      'Não foi possível carregar a saúde financeira',
    )
    expect(wrapper.find('[data-testid="health-toggle"]').exists()).toBe(false)
  })
})
