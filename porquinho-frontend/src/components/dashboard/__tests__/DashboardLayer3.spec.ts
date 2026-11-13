import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, RouterLinkStub, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import DashboardLayer3 from '../DashboardLayer3.vue'
import { useAuthStore } from '@/stores/auth'
import type { Transaction } from '@/types/transaction'
import type { DashboardDetails } from '@/types/dashboard'

const mockGetDetails = vi.fn()
const mockExport = vi.fn()
const mockUpdate = vi.fn()
vi.mock('@/services/dashboardService', () => ({
  getDetails: (...a: unknown[]) => mockGetDetails(...a),
  exportDetails: (...a: unknown[]) => mockExport(...a),
}))
vi.mock('@/services/transactionService', () => ({
  updateTransaction: (...a: unknown[]) => mockUpdate(...a),
  createTransaction: vi.fn(),
}))
const mockAccounts = vi.fn()
vi.mock('@/services/accountService', () => ({ listAccounts: () => mockAccounts() }))
vi.mock('@/services/cardService', () => ({ listCards: async () => [{ id: 'k1', cardName: 'Roxinho' }] }))
vi.mock('@/services/categoryService', () => ({
  listCategories: async () => [
    { id: 'g1', categoryName: 'Alimentação', parentCategoryId: null },
    { id: 'c1', categoryName: 'Mercado', parentCategoryId: 'g1' },
    { id: 'c2', categoryName: 'Restaurante', parentCategoryId: 'g1' },
  ],
}))

const KEY = 'porquinho:dashboard-layer3:u1'

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

function page(items: Transaction[], totalItems = items.length, number = 0): DashboardDetails {
  return { items, page: number, totalItems, totalPages: Math.ceil(totalItems / 50) }
}

const THIS_MONTH = { from: '2026-11-01', to: '2026-11-30', categoryIds: [], targets: [], q: '', sort: 'date', dir: 'desc' }

let wrapper: VueWrapper | null = null

async function mountLayer() {
  wrapper = mount(DashboardLayer3, { attachTo: document.body, global: { stubs: { RouterLink: RouterLinkStub } } })
  await flushPromises()
  return wrapper
}

/** Intl separa "R$" do número com espaço não separável. */
const norm = (text: string) => text.replace(/\u00a0/g, ' ')

const lastFilter = () => mockGetDetails.mock.lastCall![0]
const lastPage = () => mockGetDetails.mock.lastCall![1]

describe('DashboardLayer3', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    setActivePinia(createPinia())
    useAuthStore().setUser({ id: 'u1', email: 'u1@example.com', authProvider: 'EMAIL', createdAt: '' })
    mockGetDetails.mockResolvedValue(page([transaction('t1')]))
    mockAccounts.mockResolvedValue([{ id: 'a1', accountName: 'Nubank' }])
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-11-09T15:00:00Z'))
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    vi.useRealTimers()
  })

  it('abre com a página 1 do mês corrente por data desc, em tabela e em cards', async () => {
    const pix = transaction('pix', { isPix: true, pixDetails: { direction: 'SENT', counterpart_name: 'MARIA' } })
    const p3 = transaction('p3', {
      type: 'CREDIT',
      category: null,
      accountId: null,
      creditCardId: 'k1',
      isInstallment: true,
      installmentNumber: 3,
      installmentTotal: 10,
      remainingInstallments: 7,
    })
    mockGetDetails.mockResolvedValue(page([pix, p3], 120))
    const w = await mountLayer()

    expect(mockGetDetails).toHaveBeenCalledTimes(1)
    expect(mockGetDetails).toHaveBeenCalledWith(THIS_MONTH, 0)
    const row = w.get('[data-testid="details-row-pix"]').findAll('td').map((td) => norm(td.text()))
    expect(row.slice(0, 5)).toEqual(['03/11/2026', 'Compra pix', '-R$ 100,00', 'Alimentação › Mercado', 'Nubank'])
    expect(w.get('[data-testid="details-row-pix"] [data-testid="row-pix"]').text()).toBe('Pix enviado para MARIA')
    const card = w.get('[data-testid="details-row-p3"]').findAll('td').map((td) => norm(td.text()))
    expect(card.slice(2, 5)).toEqual(['+R$ 100,00', 'Não classificado', 'Roxinho'])
    expect(w.get('[data-testid="details-row-p3"] [data-testid="row-installment"]').text()).toBe(
      'Compra parcelada: 3/10 — restam 7 parcelas',
    )
    expect(w.find('[data-testid="details-card-p3"]').text()).toContain('Compra parcelada: 3/10')
    expect(w.get('[data-testid="details-page"]').text()).toBe('120 transações · página 1 de 3')
    expect(w.get('th[aria-sort="descending"]').text()).toContain('Data')
  })

  it('filtros persistem por usuário e um id salvo que não existe mais é descartado', async () => {
    localStorage.setItem(
      KEY,
      JSON.stringify({ ...THIS_MONTH, preset: 'last-month', categoryIds: ['gone', 'c1', 'uncategorized'], targets: ['card:k1', 'account:old'], q: 'mercado', sort: 'amount', dir: 'asc' }),
    )
    await mountLayer()

    expect(mockGetDetails).toHaveBeenLastCalledWith(
      {
        from: '2026-10-01',
        to: '2026-10-31',
        categoryIds: ['c1', 'uncategorized'],
        targets: ['card:k1'],
        q: 'mercado',
        sort: 'amount',
        dir: 'asc',
      },
      0,
    )
    expect(JSON.parse(localStorage.getItem(KEY)!).categoryIds).toEqual(['c1', 'uncategorized'])
  })

  it('trocar um filtro grava no localStorage e volta para a página 1', async () => {
    mockGetDetails.mockResolvedValue(page([transaction('t1')], 120))
    const w = await mountLayer()
    await w.get('[data-testid="details-next"]').trigger('click')
    await flushPromises()
    expect(lastPage()).toBe(1)
    expect(w.get('[data-testid="details-page"]').text()).toContain('página 2 de 3')

    await w.get('[data-testid="details-categories"]').trigger('click')
    await flushPromises()
    await w.get('[data-testid="details-categories-option-c2"]').setValue(true)
    await flushPromises()

    expect(lastFilter().categoryIds).toEqual(['c2'])
    expect(lastPage()).toBe(0)
    expect(JSON.parse(localStorage.getItem(KEY)!).categoryIds).toEqual(['c2'])

    await w.get('[data-testid="details-period"]').setValue('last-3-months')
    await flushPromises()
    expect(lastFilter()).toMatchObject({ from: '2026-09-01', to: '2026-11-30' })
  })

  it('a busca por texto espera a digitação parar', async () => {
    const w = await mountLayer()
    await w.get('[data-testid="details-q"]').setValue('Mercado')
    await flushPromises()
    expect(mockGetDetails).toHaveBeenCalledTimes(1)
    await new Promise((resolve) => setTimeout(resolve, 350))
    await flushPromises()
    expect(lastFilter().q).toBe('Mercado')
  })

  it('cabeçalho escolhe a ordenação e o segundo clique inverte, com aria-sort', async () => {
    const w = await mountLayer()
    await w.get('[data-testid="sort-amount"]').trigger('click')
    await flushPromises()
    expect(lastFilter()).toMatchObject({ sort: 'amount', dir: 'desc' })
    expect(w.get('[data-testid="sort-amount"]').element.closest('th')!.getAttribute('aria-sort')).toBe('descending')

    await w.get('[data-testid="sort-amount"]').trigger('click')
    await flushPromises()
    expect(lastFilter()).toMatchObject({ sort: 'amount', dir: 'asc' })
    expect(w.get('[data-testid="sort-amount"]').element.closest('th')!.getAttribute('aria-sort')).toBe('ascending')
    expect(w.get('[data-testid="sort-date"]').element.closest('th')!.getAttribute('aria-sort')).toBe('none')

    await w.get('[data-testid="sort-category"]').trigger('click')
    await flushPromises()
    expect(lastFilter()).toMatchObject({ sort: 'category', dir: 'asc' })
  })

  it('período personalizado inválido não consulta e avisa no filtro', async () => {
    const w = await mountLayer()
    await w.get('[data-testid="details-period"]').setValue('custom')
    await flushPromises()
    expect(w.get('[data-testid="details-period-error"]').text()).toContain('datas válidas')
    await w.get('[data-testid="details-from"]').setValue('2026-11-30')
    await w.get('[data-testid="details-to"]').setValue('2026-11-01')
    await flushPromises()

    expect(mockGetDetails).toHaveBeenCalledTimes(1)
    expect(w.find('[data-testid="details-period-error"]').exists()).toBe(true)
    expect(w.get('[data-testid="details-export"]').attributes('disabled')).toBeDefined()

    await w.get('[data-testid="details-to"]').setValue('2026-12-15')
    await flushPromises()
    expect(lastFilter()).toMatchObject({ from: '2026-11-30', to: '2026-12-15' })
  })

  it('sem resultado mostra a mensagem e desabilita o export', async () => {
    mockGetDetails.mockResolvedValue(page([]))
    const w = await mountLayer()
    expect(w.get('[data-testid="details-empty"]').text()).toBe('Nenhuma transação encontrada')
    expect(w.get('[data-testid="details-export"]').attributes('disabled')).toBeDefined()
  })

  it('falha da API mostra o erro só na Camada 3 e tenta de novo', async () => {
    mockGetDetails.mockRejectedValue(new Error('500'))
    const w = await mountLayer()
    expect(w.get('[data-testid="details-error"]').text()).toContain('Não foi possível carregar as transações')

    mockGetDetails.mockResolvedValue(page([transaction('t1')]))
    await w.get('[data-testid="details-error"] button').trigger('click')
    await flushPromises()
    expect(w.find('[data-testid="details-row-t1"]').exists()).toBe(true)
  })

  it('edita pelo diálogo de /transacoes, recarrega a página e avisa o dashboard', async () => {
    const w = await mountLayer()
    await w.get('[data-testid="details-edit-t1"]').trigger('click')
    await flushPromises()
    const amount = document.body.querySelector<HTMLInputElement>('[data-testid="edit-amount"]')!
    expect(amount.value).toBe('100,00')
    amount.value = '150,50'
    amount.dispatchEvent(new Event('input'))
    mockUpdate.mockResolvedValue(transaction('t1', { amount: 150.5 }))
    mockGetDetails.mockResolvedValue(page([transaction('t1', { amount: 150.5 })]))
    document.body.querySelector<HTMLFormElement>('[data-testid="edit-form"]')!.dispatchEvent(new Event('submit'))
    await flushPromises()

    expect(mockUpdate).toHaveBeenCalledExactlyOnceWith('t1', expect.objectContaining({ amount: 150.5, accountId: 'a1' }))
    expect(mockGetDetails).toHaveBeenCalledTimes(2)
    expect(norm(w.get('[data-testid="details-row-t1"]').text())).toContain('-R$ 150,50')
    expect(w.emitted('transaction-saved')).toHaveLength(1)
    expect(w.get('[data-testid="details-success"]').text()).toBe('Transação atualizada')
  })

  it('erro ao salvar fica no diálogo', async () => {
    const w = await mountLayer()
    await w.get('[data-testid="details-edit-t1"]').trigger('click')
    await flushPromises()
    mockUpdate.mockRejectedValue({ response: { status: 400, data: { detail: 'Saldo fora do limite' } } })
    document.body.querySelector<HTMLFormElement>('[data-testid="edit-form"]')!.dispatchEvent(new Event('submit'))
    await flushPromises()

    expect(document.body.querySelector('[data-testid="edit-error"]')?.textContent).toContain('Saldo fora do limite')
    expect(w.emitted('transaction-saved')).toBeUndefined()
  })

  it('exporta o CSV com os mesmos filtros e baixa o arquivo', async () => {
    const blob = new Blob(['x'])
    mockExport.mockResolvedValue(blob)
    const createObjectURL = vi.fn(() => 'blob:csv')
    vi.stubGlobal('URL', { ...URL, createObjectURL, revokeObjectURL: vi.fn() })
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    const w = await mountLayer()

    await w.get('[data-testid="details-export"]').trigger('click')
    await flushPromises()

    expect(mockExport).toHaveBeenCalledExactlyOnceWith(THIS_MONTH)
    expect(createObjectURL).toHaveBeenCalledWith(blob)
    expect(click).toHaveBeenCalledOnce()
    click.mockRestore()
    vi.unstubAllGlobals()
  })

  it('página que ficou além do fim depois de uma edição volta para a última', async () => {
    mockGetDetails.mockResolvedValue(page([transaction('t1')], 51))
    const w = await mountLayer()
    await w.get('[data-testid="details-next"]').trigger('click')
    await flushPromises()
    expect(lastPage()).toBe(1)

    // a única linha da página 2 saiu do filtro com a edição
    await w.get('[data-testid="details-edit-t1"]').trigger('click')
    await flushPromises()
    mockUpdate.mockResolvedValue(transaction('t1'))
    mockGetDetails.mockImplementation(async (_f: unknown, n: number) =>
      n === 0 ? page([transaction('t2')], 50) : { items: [], page: n, totalItems: 50, totalPages: 1 },
    )
    document.body.querySelector<HTMLFormElement>('[data-testid="edit-form"]')!.dispatchEvent(new Event('submit'))
    await flushPromises()

    expect(lastPage()).toBe(0)
    expect(w.find('[data-testid="details-row-t2"]').exists()).toBe(true)
    expect(w.get('[data-testid="details-page"]').text()).toBe('50 transações · página 1 de 1')
  })

  it('opções que não carregaram aparecem como erro ao abrir o diálogo', async () => {
    mockAccounts.mockRejectedValue(new Error('rede'))
    const w = await mountLayer()
    await w.get('[data-testid="details-edit-t1"]').trigger('click')
    await flushPromises()

    expect(document.body.querySelector('[data-testid="edit-error"]')?.textContent).toContain(
      'Não foi possível carregar suas contas, cartões e categorias.',
    )
  })

  it('ao fechar o diálogo o foco volta ao Editar da linha', async () => {
    const w = await mountLayer()
    await w.get('[data-testid="details-edit-t1"]').trigger('click')
    await flushPromises()
    document.body.querySelector<HTMLButtonElement>('[data-testid="edit-cancel"]')!.click()
    await flushPromises()
    await new Promise((resolve) => setTimeout(resolve, 0))
    await flushPromises()

    expect(document.activeElement?.getAttribute('data-testid')).toBe('details-edit-t1')
  })
})
