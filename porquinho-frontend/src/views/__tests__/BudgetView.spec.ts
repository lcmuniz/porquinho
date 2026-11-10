import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, RouterLinkStub, type VueWrapper } from '@vue/test-utils'
import BudgetView from '../BudgetView.vue'
import type { Budget } from '@/types/budget'

const mockGet = vi.fn()
const mockSave = vi.fn()

vi.mock('@/services/budgetService', () => ({
  getBudget: (...a: unknown[]) => mockGet(...a),
  saveBudget: (...a: unknown[]) => mockSave(...a),
}))
vi.mock('@/services/categoryService', () => ({
  listCategories: async () => [
    { id: 'g1', categoryName: 'Essenciais', parentCategoryId: null },
    { id: 'c2', categoryName: 'Transporte', parentCategoryId: 'g1' },
    { id: 'c1', categoryName: 'Mercado', parentCategoryId: 'g1' },
  ],
}))

function budget(overrides: Partial<Budget> = {}): Budget {
  return {
    month: '2026-11',
    totalAvailable: 1000,
    totalAllocated: 400,
    categories: [
      { categoryId: 'c1', allocated: 400, spent: 100, committed: 50 },
      { categoryId: 'c2', allocated: null, spent: 30, committed: 0 },
    ],
    ...overrides,
  }
}

let wrapper: VueWrapper | null = null

async function mountView() {
  wrapper = mount(BudgetView, { attachTo: document.body, global: { stubs: { RouterLink: RouterLinkStub } } })
  await flushPromises()
  return wrapper
}

async function type(testId: string, value: string) {
  await wrapper!.get(`[data-testid="${testId}"]`).setValue(value)
}

describe('BudgetView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-11-09T15:00:00Z'))
    mockGet.mockResolvedValue(budget())
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    document.body.innerHTML = ''
    vi.useRealTimers()
  })

  it('carrega o mês atual agrupado, com gasto, comprometido, barra e restante', async () => {
    const w = await mountView()

    expect(mockGet).toHaveBeenCalledWith('2026-11')
    expect(w.get('[data-testid="month-label"]').text()).toBe('novembro de 2026')
    expect(w.get('[data-testid="group-g1"] h3').text()).toBe('Essenciais')
    const items = w.findAll('[data-testid^="category-"]').map((li) => li.attributes('data-testid'))
    expect(items).toEqual(['category-c1', 'category-c2'])
    expect((w.get('[data-testid="amount-c1"]').element as HTMLInputElement).value).toBe('400,00')
    expect(w.get('[data-testid="spent-c1"]').text()).toContain('100,00')
    expect(w.get('[data-testid="committed-c1"]').text()).toContain('50,00')
    expect(w.get('[data-testid="progress-c1"]').attributes('aria-valuenow')).toBe('25')
    expect(w.get('[data-testid="progress-text-c1"]').text()).toContain('Restante: R$')
    expect(w.get('[data-testid="progress-text-c1"]').text()).toContain('300,00')
    expect(w.get('[data-testid="total-available"]').text()).toContain('1.000,00')
  })

  it('categoria sem alocação mostra "Sem orçamento definido" e o gasto, sem barra', async () => {
    const w = await mountView()
    expect(w.get('[data-testid="no-budget-c2"]').text()).toBe('Sem orçamento definido')
    expect(w.get('[data-testid="spent-c2"]').text()).toContain('30,00')
    expect(w.find('[data-testid="progress-c2"]').exists()).toBe(false)
  })

  it('mês fechado não mostra o comprometido', async () => {
    mockGet.mockResolvedValue(
      budget({
        categories: [
          { categoryId: 'c1', allocated: 400, spent: 100, committed: null },
          { categoryId: 'c2', allocated: null, spent: 0, committed: null },
        ],
      }),
    )
    const w = await mountView()
    expect(w.find('[data-testid="committed-c1"]').exists()).toBe(false)
  })

  it('salva só os campos preenchidos e mostra "Orçamento mensal definido"', async () => {
    mockSave.mockResolvedValue(
      budget({
        categories: [
          { categoryId: 'c1', allocated: 400, spent: 100, committed: 50 },
          { categoryId: 'c2', allocated: 0, spent: 30, committed: 0 },
        ],
      }),
    )
    const w = await mountView()
    await type('amount-c2', '0')
    await w.get('[data-testid="budget-form"]').trigger('submit')
    await flushPromises()

    expect(mockSave).toHaveBeenCalledWith('2026-11', [
      { categoryId: 'c1', amount: 400 },
      { categoryId: 'c2', amount: 0 },
    ])
    expect(w.get('[data-testid="budget-success"]').text()).toBe('Orçamento mensal definido')
    expect(w.find('[data-testid="no-budget-c2"]').exists()).toBe(false)
  })

  it('não envia valor negativo ou inválido', async () => {
    const w = await mountView()
    await type('amount-c2', '-10')
    await w.get('[data-testid="budget-form"]').trigger('submit')
    await flushPromises()
    expect(mockSave).not.toHaveBeenCalled()
    expect(w.get('[data-testid="amount-error-c2"]').text()).toBe('Informe um valor válido, zero ou maior')
  })

  it('mostra o aviso de excesso na hora, sem impedir o salvamento', async () => {
    mockSave.mockResolvedValue(budget())
    const w = await mountView()
    expect(w.find('[data-testid="over-available"]').exists()).toBe(false)

    await type('amount-c2', '700,00')
    expect(w.get('[data-testid="over-available"]').text()).toBe('Você está alocando mais do que tem disponível')
    expect(w.get('[data-testid="total-allocated"]').text()).toContain('1.100,00')

    await w.get('[data-testid="budget-form"]').trigger('submit')
    await flushPromises()
    expect(mockSave).toHaveBeenCalled()
  })

  it('sem nada alocado não avisa excesso, mesmo com o disponível negativo', async () => {
    mockGet.mockResolvedValue(
      budget({
        totalAvailable: -50,
        categories: [{ categoryId: 'c1', allocated: null, spent: 0, committed: 0 }],
      }),
    )
    const w = await mountView()
    expect(w.find('[data-testid="over-available"]').exists()).toBe(false)
  })

  it('desabilita Salvar enquanto repete o mês anterior', async () => {
    const w = await mountView()
    let resolve: (b: Budget) => void = () => {}
    mockGet.mockReturnValueOnce(new Promise<Budget>((r) => (resolve = r)))
    await w.get('[data-testid="repeat-previous"]').trigger('click')
    expect(w.get('[data-testid="save-budget"]').attributes('disabled')).toBeDefined()
    await w.get('[data-testid="budget-form"]').trigger('submit')
    expect(mockSave).not.toHaveBeenCalled()
    resolve(budget())
    await flushPromises()
    expect(w.get('[data-testid="save-budget"]').attributes('disabled')).toBeUndefined()
  })

  it('a mensagem de sucesso some ao trocar de mês e não aparece se o mês mudou durante o salvamento', async () => {
    mockSave.mockResolvedValue(budget())
    const w = await mountView()
    await w.get('[data-testid="budget-form"]').trigger('submit')
    await flushPromises()
    expect(w.get('[data-testid="budget-success"]').text()).toBe('Orçamento mensal definido')
    await w.get('[data-testid="next-month"]').trigger('click')
    await flushPromises()
    expect(w.get('[data-testid="budget-success"]').text()).toBe('')

    let resolve: (b: Budget) => void = () => {}
    mockSave.mockReturnValueOnce(new Promise<Budget>((r) => (resolve = r)))
    await w.get('[data-testid="budget-form"]').trigger('submit')
    await w.get('[data-testid="previous-month"]').trigger('click')
    resolve(budget())
    await flushPromises()
    expect(w.get('[data-testid="budget-success"]').text()).toBe('')
  })

  it('repete o orçamento do mês anterior sem salvar', async () => {
    const w = await mountView()
    mockGet.mockResolvedValueOnce(
      budget({
        month: '2026-10',
        categories: [
          { categoryId: 'c1', allocated: null, spent: 0, committed: null },
          { categoryId: 'c2', allocated: 250.5, spent: 0, committed: null },
        ],
      }),
    )
    await w.get('[data-testid="repeat-previous"]').trigger('click')
    await flushPromises()

    expect(mockGet).toHaveBeenLastCalledWith('2026-10')
    expect((w.get('[data-testid="amount-c1"]').element as HTMLInputElement).value).toBe('')
    expect((w.get('[data-testid="amount-c2"]').element as HTMLInputElement).value).toBe('250,50')
    expect(mockSave).not.toHaveBeenCalled()
  })

  it('avisa quando o mês anterior não tem orçamento e mantém os campos', async () => {
    const w = await mountView()
    mockGet.mockResolvedValueOnce(
      budget({
        categories: [
          { categoryId: 'c1', allocated: null, spent: 0, committed: null },
          { categoryId: 'c2', allocated: null, spent: 0, committed: null },
        ],
      }),
    )
    await w.get('[data-testid="repeat-previous"]').trigger('click')
    await flushPromises()

    expect(w.get('[data-testid="budget-notice"]').text()).toBe('O mês anterior não tem orçamento')
    expect((w.get('[data-testid="amount-c1"]').element as HTMLInputElement).value).toBe('400,00')
  })

  it('troca de mês e recarrega', async () => {
    const w = await mountView()
    await w.get('[data-testid="previous-month"]').trigger('click')
    await flushPromises()
    expect(mockGet).toHaveBeenLastCalledWith('2026-10')
    await w.get('[data-testid="next-month"]').trigger('click')
    await w.get('[data-testid="next-month"]').trigger('click')
    await flushPromises()
    expect(mockGet).toHaveBeenLastCalledWith('2026-12')
    expect(w.get('[data-testid="month-label"]').text()).toBe('dezembro de 2026')
  })

  it('erro 400 ao salvar mostra a mensagem e mantém os valores digitados', async () => {
    mockSave.mockRejectedValue({ response: { status: 400, data: { detail: 'Escolha categorias válidas' } } })
    const w = await mountView()
    await type('amount-c2', '80')
    await w.get('[data-testid="budget-form"]').trigger('submit')
    await flushPromises()

    expect(w.get('[data-testid="budget-error"]').text()).toBe('Escolha categorias válidas')
    expect((w.get('[data-testid="amount-c2"]').element as HTMLInputElement).value).toBe('80')
  })

  it('erro 400 ao carregar mostra a mensagem na tela', async () => {
    mockGet.mockRejectedValue({ response: { status: 400, data: { detail: 'Mês inválido: use o formato AAAA-MM' } } })
    const w = await mountView()
    expect(w.get('[data-testid="budget-load-error"]').text()).toContain('Mês inválido: use o formato AAAA-MM')
  })
})
