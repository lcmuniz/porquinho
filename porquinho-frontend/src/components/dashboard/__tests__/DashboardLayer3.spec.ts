import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, RouterLinkStub, type VueWrapper } from '@vue/test-utils'
import DashboardLayer3 from '../DashboardLayer3.vue'
import type { Transaction } from '@/types/transaction'

const mockLargest = vi.fn()
const mockDrilldown = vi.fn()
const mockUpdate = vi.fn()
vi.mock('@/services/dashboardService', () => ({
  getLargestSpent: () => mockLargest(),
  getDrilldown: (...a: unknown[]) => mockDrilldown(...a),
}))
vi.mock('@/services/transactionService', () => ({
  updateTransaction: (...a: unknown[]) => mockUpdate(...a),
  createTransaction: vi.fn(),
}))
vi.mock('@/services/accountService', () => ({ listAccounts: async () => [{ id: 'a1', accountName: 'Nubank' }] }))
vi.mock('@/services/cardService', () => ({ listCards: async () => [{ id: 'k1', cardName: 'Roxinho' }] }))
vi.mock('@/services/categoryService', () => ({
  listCategories: async () => [
    { id: 'g1', categoryName: 'Alimentação', parentCategoryId: null },
    { id: 'c1', categoryName: 'Mercado', parentCategoryId: 'g1' },
    { id: 'c2', categoryName: 'Restaurante', parentCategoryId: 'g1' },
  ],
}))

function transaction(id: string, overrides: Partial<Transaction> = {}): Transaction {
  return {
    id,
    date: '2026-11-03',
    description: `Compra ${id}`,
    amount: 100,
    type: 'DEBIT',
    kind: 'REGULAR',
    category: { id: 'c1', name: 'Mercado', group: 'Alimentação' },
    source: 'AI',
    confidence: 92,
    reviewStatus: 'CONFIRMED',
    isPix: false,
    pixDetails: null,
    isInstallment: false,
    installmentNumber: null,
    installmentTotal: null,
    isFutureInstallment: false,
    accountId: 'a1',
    creditCardId: null,
    installmentGroupId: null,
    remainingInstallments: 0,
    confirmedInstallments: 0,
    ...overrides,
  }
}

let wrapper: VueWrapper | null = null

async function mountLayer() {
  wrapper = mount(DashboardLayer3, { attachTo: document.body, global: { stubs: { RouterLink: RouterLinkStub } } })
  await flushPromises()
  return wrapper
}

/** Intl separa "R$" do número com espaço não separável. */
const norm = (text: string) => text.replace(/\u00a0/g, ' ')
const checked = (w: VueWrapper, value: string) =>
  (w.get(`[data-testid="details-view-${value}"]`).element as HTMLInputElement).checked

const loose = transaction('u1', { description: 'Pix sem nome', amount: 30, category: null })

describe('DashboardLayer3', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockLargest.mockResolvedValue([transaction('t1', { amount: 500 }), transaction('t2', { amount: 80 })])
    mockDrilldown.mockResolvedValue([loose])
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    document.body.innerHTML = ''
  })

  it('não repete a lista de transações: grupo de rádios com Maiores gastos e Não classificadas', async () => {
    const w = await mountLayer()
    const group = w.get('[data-testid="details-view"]')
    expect(group.element.tagName).toBe('FIELDSET')
    expect(group.get('legend').text()).toBe('O que mostrar')
    const radios = group.findAll('input[type="radio"]')
    expect(radios.map((r) => r.element.parentElement!.textContent!.trim().replace(/\s+/g, ' '))).toEqual([
      'Maiores gastos',
      'Não classificadas (1)',
    ])
    expect(w.find('[data-testid="details-filters"]').exists()).toBe(false)
    expect(w.findComponent(RouterLinkStub).props('to')).toBe('/transacoes')
    // só as saídas sem categoria (regra de gasto do backend)
    expect(mockDrilldown).toHaveBeenCalledExactlyOnceWith(null)
  })

  it('abre em Não classificadas quando há alguma, com o atalho Classificar', async () => {
    const w = await mountLayer()
    expect(checked(w, 'uncategorized')).toBe(true)
    const row = w.get('[data-testid="details-row-u1"]')
    expect(norm(row.text())).toContain('Pix sem nome')
    expect(norm(row.text())).toContain('-R$ 30,00')
    expect(row.get('[data-testid="details-classify-u1"]').attributes('aria-label')).toBe('Classificar Pix sem nome')
  })

  it('sem não classificadas abre em Maiores gastos, na ordem do backend e com a categoria', async () => {
    mockDrilldown.mockResolvedValue([])
    const w = await mountLayer()
    expect(checked(w, 'largest')).toBe(true)
    const rows = w.findAll('[data-testid^="details-row-"]')
    expect(rows.map((r) => r.attributes('data-testid'))).toEqual(['details-row-t1', 'details-row-t2'])
    expect(norm(rows[0]!.text())).toContain('-R$ 500,00')
    expect(rows[0]!.text()).toContain('Alimentação › Mercado')
    expect(w.find('[data-testid^="details-classify-"]').exists()).toBe(false)
  })

  it('trocar o rádio troca a lista sem buscar de novo', async () => {
    const w = await mountLayer()
    await w.get('[data-testid="details-view-largest"]').setValue(true)
    expect(w.find('[data-testid="details-row-t1"]').exists()).toBe(true)
    expect(w.find('[data-testid="details-row-u1"]').exists()).toBe(false)
    expect(mockLargest).toHaveBeenCalledTimes(1)
  })

  it('mensagens de vazio de cada lista', async () => {
    mockDrilldown.mockResolvedValue([])
    mockLargest.mockResolvedValue([])
    const w = await mountLayer()
    expect(w.get('[data-testid="details-empty"]').text()).toBe('Nenhum gasto neste mês.')
    await w.get('[data-testid="details-view-uncategorized"]').setValue(true)
    expect(w.get('[data-testid="details-empty"]').text()).toBe('Tudo classificado neste mês.')
  })

  it('erro ao carregar mostra Tentar de novo', async () => {
    mockLargest.mockRejectedValueOnce(new Error('rede'))
    const w = await mountLayer()
    expect(w.get('[data-testid="details-error"]').text()).toContain('Não foi possível carregar as transações')
    await w.get('[data-testid="details-error"] button').trigger('click')
    await flushPromises()
    expect(w.find('[data-testid="details-error"]').exists()).toBe(false)
    expect(w.find('[data-testid="details-row-u1"]').exists()).toBe(true)
  })

  it('Classificar abre o diálogo de /transacoes; salvar recarrega as listas e avisa o dashboard', async () => {
    const w = await mountLayer()
    await w.get('[data-testid="details-classify-u1"]').trigger('click')
    await flushPromises()
    const category = document.body.querySelector<HTMLSelectElement>('[data-testid="edit-category"]')!
    category.value = 'c2'
    category.dispatchEvent(new Event('change'))
    await flushPromises()
    mockUpdate.mockResolvedValue({ ...loose, category: { id: 'c2', name: 'Restaurante', group: 'Alimentação' } })
    mockDrilldown.mockResolvedValue([])
    document.body.querySelector<HTMLFormElement>('[data-testid="edit-form"]')!.dispatchEvent(new Event('submit'))
    await flushPromises()

    expect(mockUpdate).toHaveBeenCalledExactlyOnceWith('u1', expect.objectContaining({ categoryId: 'c2' }))
    expect(mockDrilldown).toHaveBeenCalledTimes(2)
    expect(w.emitted('transaction-saved')).toHaveLength(1)
    // a escolha do usuário fica: a lista vazia mostra que acabou
    expect(checked(w, 'uncategorized')).toBe(true)
    expect(w.get('[data-testid="details-empty"]').text()).toBe('Tudo classificado neste mês.')
    expect(w.get('[data-testid="details-success"]').text()).toBe('Transação atualizada')
  })

  it('a linha classificada sai da lista e o foco vai para o rádio da lista', async () => {
    const w = await mountLayer()
    await w.get('[data-testid="details-classify-u1"]').trigger('click')
    await flushPromises()
    document.body.querySelector<HTMLButtonElement>('[data-testid="edit-cancel"]')!.click()
    await flushPromises()
    await new Promise((resolve) => setTimeout(resolve, 0))
    await flushPromises()
    expect(document.activeElement?.getAttribute('data-testid')).toBe('details-classify-u1')

    mockDrilldown.mockResolvedValue([])
    await w.get('[data-testid="details-classify-u1"]').trigger('click')
    await flushPromises()
    mockUpdate.mockResolvedValue(loose)
    document.body.querySelector<HTMLFormElement>('[data-testid="edit-form"]')!.dispatchEvent(new Event('submit'))
    await flushPromises()
    await new Promise((resolve) => setTimeout(resolve, 0))
    await flushPromises()
    expect(document.activeElement?.getAttribute('data-testid')).toBe('details-view-uncategorized')
  })

  it('avisa loaded depois da primeira carga', async () => {
    const w = await mountLayer()
    expect(w.emitted('loaded')).toHaveLength(1)
  })
})
