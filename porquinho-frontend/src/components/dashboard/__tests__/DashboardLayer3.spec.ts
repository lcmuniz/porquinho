import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, RouterLinkStub, type VueWrapper } from '@vue/test-utils'
import DashboardLayer3 from '../DashboardLayer3.vue'
import type { Transaction } from '@/types/transaction'

const mockLargest = vi.fn()
const mockDrilldown = vi.fn()
const mockUpdate = vi.fn()
const mockBatchCategory = vi.fn()
vi.mock('@/services/dashboardService', () => ({
  getLargestSpent: () => mockLargest(),
  getDrilldown: (...a: unknown[]) => mockDrilldown(...a),
}))
vi.mock('@/services/transactionService', () => ({
  updateTransaction: (...a: unknown[]) => mockUpdate(...a),
  batchUpdateCategory: (...a: unknown[]) => mockBatchCategory(...a),
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

  it('não repete a lista de transações: grupo de rádios com Maiores gastos e Sem categoria', async () => {
    const w = await mountLayer()
    const group = w.get('[data-testid="details-view"]')
    expect(group.element.tagName).toBe('FIELDSET')
    expect(group.get('legend').text()).toBe('O que mostrar')
    const radios = group.findAll('input[type="radio"]')
    expect(radios.map((r) => r.element.parentElement!.textContent!.trim().replace(/\s+/g, ' '))).toEqual([
      'Maiores gastos',
      'Sem categoria (1)',
    ])
    expect(w.find('[data-testid="details-filters"]').exists()).toBe(false)
    // em "Sem categoria", o link abre Transações já filtrada; em "Maiores gastos", a lista toda
    expect(w.findComponent(RouterLinkStub).props('to')).toBe('/transacoes?filtro=sem-categoria')
    await w.get('[data-testid="details-view-largest"]').setValue(true)
    expect(w.findComponent(RouterLinkStub).props('to')).toBe('/transacoes')
    // só as saídas sem categoria (regra de gasto do backend)
    expect(mockDrilldown).toHaveBeenCalledExactlyOnceWith(null)
  })

  it('abre em Sem categoria quando há alguma, com o atalho Classificar', async () => {
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

  /** Abre o diálogo curto da linha u1 e escolhe a categoria. */
  async function classify(w: VueWrapper, categoryId = 'c2') {
    await w.get('[data-testid="details-classify-u1"]').trigger('click')
    await flushPromises()
    const select = document.body.querySelector<HTMLSelectElement>('[data-testid="classify-category"]')!
    select.value = categoryId
    select.dispatchEvent(new Event('change'))
    await flushPromises()
  }
  const settle = async () => {
    await flushPromises()
    await new Promise((resolve) => setTimeout(resolve, 0))
    await flushPromises()
  }

  it('Classificar abre um diálogo só com a categoria, com o foco nela e Salvar desabilitado até escolher', async () => {
    const w = await mountLayer()
    await w.get('[data-testid="details-classify-u1"]').trigger('click')
    await settle()
    const dialog = document.body.querySelector('[data-testid="classify-dialog"]')!
    expect(dialog.textContent).toContain('Pix sem nome')
    expect(dialog.querySelectorAll('select, input')).toHaveLength(1)
    expect(document.activeElement?.getAttribute('data-testid')).toBe('classify-category')
    expect(document.body.querySelector<HTMLButtonElement>('[data-testid="classify-save"]')!.disabled).toBe(true)
  })

  it('salvar muda só a categoria, recarrega as listas e avisa o dashboard', async () => {
    const w = await mountLayer()
    await classify(w)
    mockBatchCategory.mockResolvedValue(1)
    mockDrilldown.mockResolvedValue([])
    document.body.querySelector<HTMLFormElement>('[data-testid="classify-form"]')!.dispatchEvent(new Event('submit'))
    await flushPromises()

    expect(mockBatchCategory).toHaveBeenCalledExactlyOnceWith(['u1'], 'c2')
    expect(mockUpdate).not.toHaveBeenCalled()
    expect(mockDrilldown).toHaveBeenCalledTimes(2)
    expect(w.emitted('transaction-saved')).toHaveLength(1)
    // a escolha do usuário fica: a lista vazia mostra que acabou
    expect(checked(w, 'uncategorized')).toBe(true)
    expect(w.get('[data-testid="details-empty"]').text()).toBe('Tudo classificado neste mês.')
    expect(w.get('[data-testid="details-success"]').text()).toBe('Transação classificada')
  })

  it('erro ao classificar fica no diálogo, que continua aberto', async () => {
    const w = await mountLayer()
    await classify(w)
    mockBatchCategory.mockRejectedValue(new Error('rede'))
    document.body.querySelector<HTMLFormElement>('[data-testid="classify-form"]')!.dispatchEvent(new Event('submit'))
    await flushPromises()
    expect(document.body.querySelector('[data-testid="classify-error"]')!.textContent).toContain('Não foi possível classificar')
    expect(document.body.querySelector('[data-testid="classify-dialog"]')).not.toBeNull()
    expect(w.emitted('transaction-saved')).toBeUndefined()
  })

  it('Editar tudo troca o diálogo curto pelo formulário completo da mesma transação', async () => {
    const w = await mountLayer()
    await w.get('[data-testid="details-classify-u1"]').trigger('click')
    await flushPromises()
    document.body.querySelector<HTMLButtonElement>('[data-testid="classify-edit-all"]')!.click()
    await settle()
    expect(document.body.querySelector('[data-testid="classify-dialog"]')).toBeNull()
    expect(document.body.querySelector('[data-testid="edit-dialog"]')!.textContent).toContain('Editar transação')

    mockUpdate.mockResolvedValue(loose)
    document.body.querySelector<HTMLFormElement>('[data-testid="edit-form"]')!.dispatchEvent(new Event('submit'))
    await flushPromises()
    expect(mockUpdate).toHaveBeenCalledExactlyOnceWith('u1', expect.objectContaining({ description: 'Pix sem nome' }))
    expect(w.emitted('transaction-saved')).toHaveLength(1)
  })

  it('a linha classificada sai da lista e o foco vai para o rádio da lista', async () => {
    const w = await mountLayer()
    await w.get('[data-testid="details-classify-u1"]').trigger('click')
    await flushPromises()
    document.body.querySelector<HTMLButtonElement>('[data-testid="classify-cancel"]')!.click()
    await settle()
    expect(document.activeElement?.getAttribute('data-testid')).toBe('details-classify-u1')

    mockDrilldown.mockResolvedValue([])
    await classify(w)
    mockBatchCategory.mockResolvedValue(1)
    document.body.querySelector<HTMLFormElement>('[data-testid="classify-form"]')!.dispatchEvent(new Event('submit'))
    await settle()
    expect(document.activeElement?.getAttribute('data-testid')).toBe('details-view-uncategorized')
  })

  it('avisa loaded depois da primeira carga', async () => {
    const w = await mountLayer()
    expect(w.emitted('loaded')).toHaveLength(1)
  })
})
