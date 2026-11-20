import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, RouterLinkStub, type VueWrapper } from '@vue/test-utils'
import TransactionsView from '../TransactionsView.vue'
import type { Transaction } from '@/types/transaction'

const route: { query: Record<string, string> } = { query: {} }
const mockReplace = vi.fn()
vi.mock('vue-router', async (importOriginal) => ({
  ...(await importOriginal<typeof import('vue-router')>()),
  useRoute: () => route,
  useRouter: () => ({ replace: mockReplace }),
}))
const mockList = vi.fn()
const mockUpdate = vi.fn()
const mockCreate = vi.fn()
const mockAccounts = vi.fn()
const mockDelete = vi.fn()
const mockRemoveFutures = vi.fn()

vi.mock('@/services/transactionService', () => ({
  listTransactions: (...a: unknown[]) => mockList(...a),
  updateTransaction: (...a: unknown[]) => mockUpdate(...a),
  createTransaction: (...a: unknown[]) => mockCreate(...a),
  deleteTransaction: (...a: unknown[]) => mockDelete(...a),
  removeFutureInstallments: (...a: unknown[]) => mockRemoveFutures(...a),
  batchUpdateCategory: (...a: unknown[]) => mockBatchCategory(...a),
  batchDeleteTransactions: (...a: unknown[]) => mockBatchDelete(...a),
}))
const mockBatchCategory = vi.fn()
const mockBatchDelete = vi.fn()
const mockExport = vi.fn()
vi.mock('@/services/dashboardService', () => ({
  exportDetails: (...a: unknown[]) => mockExport(...a),
}))
vi.mock('@/services/accountService', () => ({
  listAccounts: () => mockAccounts(),
}))
vi.mock('@/services/cardService', () => ({ listCards: async () => [{ id: 'k1', cardName: 'Nubank Roxinho' }] }))
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

async function mountView(rows: Transaction[]) {
  mockList.mockResolvedValue(rows)
  wrapper = mount(TransactionsView, { attachTo: document.body, global: { stubs: { RouterLink: RouterLinkStub } } })
  await flushPromises()
  return wrapper
}

const inDialog = <T extends Element = HTMLElement>(testId: string) =>
  document.body.querySelector<T>(`[data-testid="${testId}"]`)

/** Abre o menu "⋯" da linha e escolhe a ação (Editar, Remover ou Remover parcelas futuras). */
async function chooseRowAction(w: VueWrapper, action: 'edit' | 'remove' | 'remove-futures', id: string) {
  await w.get(`[data-testid="actions-${id}"]`).trigger('keydown', { key: 'Enter' })
  await flushPromises()
  inDialog(`${action}-${id}`)!.click()
  await flushPromises()
}

async function setValue(testId: string, value: string) {
  const element = inDialog<HTMLInputElement | HTMLSelectElement>(testId)!
  element.value = value
  element.dispatchEvent(new Event(element.tagName === 'SELECT' ? 'change' : 'input'))
  await flushPromises()
}

describe('TransactionsView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    route.query = {}
    mockAccounts.mockResolvedValue([
      { id: 'a1', accountName: 'Nubank' },
      { id: 'a2', accountName: 'Itaú' },
    ])
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-11-09T15:00:00Z'))
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    vi.useRealTimers()
  })

  it('abre no mês de ?mes= e grava o mês navegado na URL', async () => {
    route.query = { mes: '2026-03' }
    const w = await mountView([])
    expect(mockList).toHaveBeenLastCalledWith('2026-03')
    await w.get('[data-testid="next-month"]').trigger('click')
    await flushPromises()
    expect(mockList).toHaveBeenLastCalledWith('2026-04')
    expect(mockReplace).toHaveBeenLastCalledWith({ query: { mes: '2026-04' } })
  })

  it('?mes= inválido abre no mês atual', async () => {
    route.query = { mes: '2026-13' }
    await mountView([])
    expect(mockList).toHaveBeenLastCalledWith('2026-11')
  })

  it('lista o mês atual com Pix e parcela em texto, e navega entre os meses', async () => {
    const w = await mountView([
      transaction('pix', { isPix: true, pixDetails: { direction: 'SENT', counterpart_name: 'MARIA' } }),
      transaction('p3', { isInstallment: true, installmentNumber: 3, installmentTotal: 10, remainingInstallments: 7 }),
    ])

    expect(mockList).toHaveBeenCalledWith('2026-11')
    expect(w.get('[data-testid="month-label"]').text()).toBe('novembro de 2026')
    expect(w.get('[data-testid="transaction-pix"] [data-testid="row-pix"]').text()).toBe('Pix enviado para MARIA')
    expect(w.get('[data-testid="transaction-p3"] [data-testid="row-installment"]').text()).toBe(
      'Compra parcelada: 3/10 — restam 7 parcelas',
    )
    expect(w.get('[data-testid="transaction-pix"] [data-testid="row-target"]').text()).toBe('Nubank')
    await w.get('[data-testid="actions-pix"]').trigger('keydown', { key: 'Enter' })
    await flushPromises()
    expect(inDialog('edit-pix')).not.toBeNull()
    expect(inDialog('remove-futures-pix')).toBeNull()
    await w.get('[data-testid="actions-pix"]').trigger('keydown', { key: 'Escape' })

    await w.get('[data-testid="previous-month"]').trigger('click')
    await flushPromises()
    expect(mockList).toHaveBeenLastCalledWith('2026-10')
    await w.get('[data-testid="next-month"]').trigger('click')
    await w.get('[data-testid="next-month"]').trigger('click')
    await flushPromises()
    expect(mockList).toHaveBeenLastCalledWith('2026-12')
  })

  it('"Exportar CSV" abre o diálogo no mês mostrado, como personalizado, e avisa a contagem', async () => {
    mockExport.mockResolvedValue({ blob: new Blob(['x']), count: 12 })
    vi.stubGlobal('URL', { ...URL, createObjectURL: vi.fn(() => 'blob:csv'), revokeObjectURL: vi.fn() })
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    const w = await mountView([transaction('t1')])
    await w.get('[data-testid="previous-month"]').trigger('click')
    await flushPromises()

    await w.get('[data-testid="export-transactions"]').trigger('click')
    await flushPromises()
    expect(inDialog<HTMLSelectElement>('export-period')!.value).toBe('custom')
    inDialog<HTMLFormElement>('export-form')!.dispatchEvent(new Event('submit'))
    await flushPromises()

    expect(mockExport).toHaveBeenCalledExactlyOnceWith(
      { from: '2026-10-01', to: '2026-10-31', categoryIds: [], targets: [], q: '', sort: 'date', dir: 'desc' },
      { includeFutureInstallments: false, includeDeleted: false },
    )
    expect(w.get('[data-testid="transactions-success"]').text()).toBe('Relatório exportado: 12 transações')
    click.mockRestore()
    vi.unstubAllGlobals()
  })

  it('edita e mostra "Transação atualizada" numa região de status', async () => {
    const w = await mountView([transaction('t1')])
    await chooseRowAction(w, 'edit', 't1')
    await flushPromises()

    expect(inDialog<HTMLInputElement>('edit-amount')?.value).toBe('100,00')
    await setValue('edit-amount', '150,50')
    await setValue('edit-description', 'Supermercado Extra')
    await setValue('edit-category', 'c2')
    await setValue('edit-target', 'card:k1')
    mockUpdate.mockResolvedValue(transaction('t1'))
    inDialog<HTMLFormElement>('edit-form')!.dispatchEvent(new Event('submit'))
    await flushPromises()

    expect(mockUpdate).toHaveBeenCalledExactlyOnceWith('t1', {
      date: '2026-11-03',
      description: 'Supermercado Extra',
      amount: 150.5,
      type: 'DEBIT',
      categoryId: 'c2',
      accountId: null,
      creditCardId: 'k1',
    })
    const status = w.get('[data-testid="transactions-success"]')
    expect(status.attributes('role')).toBe('status')
    expect(status.text()).toBe('Transação atualizada')
    expect(inDialog('edit-dialog')).toBeNull()
  })

  it('avisa quando a nova categoria vai junto para as parcelas futuras', async () => {
    const installment = { isInstallment: true, installmentNumber: 3, installmentTotal: 10, installmentGroupId: 'grp' }
    const w = await mountView([transaction('p3', { ...installment, remainingInstallments: 7 })])
    const status = () => w.get('[data-testid="transactions-success"]').text()

    await chooseRowAction(w, 'edit', 'p3')
    await flushPromises()
    await setValue('edit-category', 'c2')
    mockUpdate.mockResolvedValue(transaction('p3', { ...installment, remainingInstallments: 7 }))
    inDialog<HTMLFormElement>('edit-form')!.dispatchEvent(new Event('submit'))
    await flushPromises()
    expect(status()).toBe('Transação atualizada. As 7 parcelas futuras também mudaram de categoria')

    await chooseRowAction(w, 'edit', 'p3')
    await flushPromises()
    await setValue('edit-category', '')
    mockUpdate.mockResolvedValue(transaction('p3', { ...installment, remainingInstallments: 1 }))
    inDialog<HTMLFormElement>('edit-form')!.dispatchEvent(new Event('submit'))
    await flushPromises()
    expect(status()).toBe('Transação atualizada. A parcela futura também mudou de categoria')

    // sem mudar a categoria, as futuras não mudam
    await chooseRowAction(w, 'edit', 'p3')
    await flushPromises()
    await setValue('edit-amount', '150,50')
    inDialog<HTMLFormElement>('edit-form')!.dispatchEvent(new Event('submit'))
    await flushPromises()
    expect(status()).toBe('Transação atualizada')
  })

  it('avisa sobre data futura sem bloquear o envio', async () => {
    const w = await mountView([transaction('t1')])
    await chooseRowAction(w, 'edit', 't1')
    await flushPromises()
    expect(inDialog('future-date-warning')).toBeNull()

    await setValue('edit-date', '2026-12-01')
    expect(inDialog('future-date-warning')?.textContent).toContain('ainda não chegou')
    mockUpdate.mockResolvedValue(transaction('t1'))
    inDialog<HTMLFormElement>('edit-form')!.dispatchEvent(new Event('submit'))
    await flushPromises()
    expect(mockUpdate).toHaveBeenCalledOnce()
  })

  it('valor zero não é enviado', async () => {
    const w = await mountView([transaction('t1')])
    await chooseRowAction(w, 'edit', 't1')
    await flushPromises()
    await setValue('edit-amount', '0')
    inDialog<HTMLFormElement>('edit-form')!.dispatchEvent(new Event('submit'))
    await flushPromises()

    expect(mockUpdate).not.toHaveBeenCalled()
    expect(inDialog('edit-error')?.textContent).toContain('maior que zero')
  })

  it.each(['12345678901234,00', '10,555'])(
    'valor fora do formato (%s) pede o formato, não "maior que zero"',
    async (amount) => {
      const w = await mountView([transaction('t1')])
      await chooseRowAction(w, 'edit', 't1')
      await flushPromises()
      await setValue('edit-amount', amount)
      inDialog<HTMLFormElement>('edit-form')!.dispatchEvent(new Event('submit'))
      await flushPromises()

      expect(mockUpdate).not.toHaveBeenCalled()
      expect(inDialog('edit-error')?.textContent).toContain('no máximo 13 dígitos antes da vírgula')
    },
  )

  it('remove depois de confirmar e a linha sai da lista', async () => {
    const w = await mountView([transaction('t1')])
    await chooseRowAction(w, 'remove', 't1')
    await flushPromises()
    mockList.mockResolvedValue([])
    inDialog<HTMLButtonElement>('confirm-action')!.click()
    await flushPromises()

    expect(mockDelete).toHaveBeenCalledExactlyOnceWith('t1')
    expect(w.find('[data-testid="transaction-t1"]').exists()).toBe(false)
    expect(w.get('[data-testid="transactions-success"]').text()).toBe('Transação removida')
  })

  it('remove as parcelas futuras e o texto deixa de falar em "restam"', async () => {
    const installment = { isInstallment: true, installmentNumber: 3, installmentTotal: 10 }
    const w = await mountView([transaction('p3', { ...installment, remainingInstallments: 7 })])
    await chooseRowAction(w, 'remove-futures', 'p3')
    await flushPromises()
    expect(inDialog('confirm-dialog')?.textContent).toContain('7 parcelas futuras')
    mockRemoveFutures.mockResolvedValue(7)
    mockList.mockResolvedValue([transaction('p3', { ...installment, remainingInstallments: 0 })])
    inDialog<HTMLButtonElement>('confirm-action')!.click()
    await flushPromises()

    expect(mockRemoveFutures).toHaveBeenCalledExactlyOnceWith('p3')
    expect(w.get('[data-testid="row-installment"]').text()).toBe('Compra parcelada: 3/10')
    await w.get('[data-testid="actions-p3"]').trigger('keydown', { key: 'Enter' })
    await flushPromises()
    expect(inDialog('remove-futures-p3')).toBeNull()
    expect(w.get('[data-testid="transactions-success"]').text()).toBe('7 parcelas futuras removidas')
  })

  it('o menu "⋯" da linha cita a descrição', async () => {
    const w = await mountView([
      transaction('p3', { isInstallment: true, installmentNumber: 3, installmentTotal: 10, remainingInstallments: 7 }),
    ])
    expect(w.get('[data-testid="actions-p3"]').attributes('aria-label')).toBe('Mais ações de Compra p3')
  })

  it('numa parcela, conta/cartão e tipo ficam desabilitados', async () => {
    const w = await mountView([
      transaction('p3', {
        isInstallment: true,
        installmentNumber: 3,
        installmentTotal: 10,
        accountId: null,
        creditCardId: 'k1',
      }),
    ])
    await chooseRowAction(w, 'edit', 'p3')
    await flushPromises()

    expect(inDialog<HTMLSelectElement>('edit-type')?.disabled).toBe(true)
    expect(inDialog<HTMLSelectElement>('edit-target')?.disabled).toBe(true)
    expect(inDialog('installment-locked')).not.toBeNull()
  })

  it('remover a única parcela confirmada avisa as futuras que saem junto', async () => {
    const installment = { isInstallment: true, installmentNumber: 3, installmentTotal: 10, remainingInstallments: 7 }
    const w = await mountView([
      transaction('only', { ...installment, confirmedInstallments: 1 }),
      transaction('one-of-two', { ...installment, confirmedInstallments: 2 }),
    ])
    await chooseRowAction(w, 'remove', 'only')
    await flushPromises()
    expect(inDialog('futures-going-along')?.textContent).toContain('7 parcelas futuras')
    inDialog<HTMLButtonElement>('confirm-cancel')!.click()
    await flushPromises()

    await chooseRowAction(w, 'remove', 'one-of-two')
    await flushPromises()
    expect(inDialog('futures-going-along')).toBeNull()
  })

  it.each([
    [400, 'Esta alteração deixaria o saldo da conta fora do limite permitido', false],
    [404, 'Transação não encontrada', true],
    [409, 'Esta transação acabou de ser alterada. Recarregue a página e tente de novo.', true],
  ])('falha %i no salvar mostra o detail e recarrega só em 404/409', async (status, detail, reloads) => {
    const w = await mountView([transaction('t1')])
    await chooseRowAction(w, 'edit', 't1')
    await flushPromises()
    mockUpdate.mockRejectedValue({ response: { status, data: { detail } } })
    inDialog<HTMLFormElement>('edit-form')!.dispatchEvent(new Event('submit'))
    await flushPromises()

    expect(inDialog('edit-error')?.textContent).toContain(detail)
    expect(inDialog('edit-dialog')).not.toBeNull()
    expect(mockList).toHaveBeenCalledTimes(reloads ? 2 : 1)
  })

  it('falha ao remover mostra o detail e recarrega a lista', async () => {
    const w = await mountView([transaction('t1')])
    await chooseRowAction(w, 'remove', 't1')
    await flushPromises()
    mockDelete.mockRejectedValue({ response: { status: 404, data: { detail: 'Transação não encontrada' } } })
    mockList.mockResolvedValue([])
    inDialog<HTMLButtonElement>('confirm-action')!.click()
    await flushPromises()

    expect(w.get('[data-testid="transactions-error"]').text()).toBe('Transação não encontrada')
    expect(w.find('[data-testid="transaction-t1"]').exists()).toBe(false)
    expect(w.get('[data-testid="transactions-success"]').text()).toBe('')
  })

  describe('filtro Só sem categoria (critique de 20/11, 3ª)', () => {
    const rows = () => [
      transaction('t1'),
      transaction('t2', { category: null, description: 'Pix solto' }),
      transaction('t3', { category: null, kind: 'CARD_PAYMENT', description: 'Pagamento fatura' }),
    ]

    it('marca a sem categoria como pendência e não conta o pagamento de fatura', async () => {
      const w = await mountView(rows())
      const category = w.get('[data-testid="transaction-t2"] [data-testid="row-category"]')
      expect(category.text()).toBe('Sem categoria')
      expect(category.classes()).toContain('text-amber-800')
      expect(category.find('svg').exists()).toBe(true)
      expect(w.get('[data-testid="transaction-t3"] [data-testid="row-category"]').text()).toBe('Pagamento de fatura')
      expect(w.get('[data-testid="only-uncategorized"]').element.parentElement!.textContent).toContain('Só sem categoria (1)')
    })

    it('filtra a lista, guarda na URL e seleciona só as do filtro', async () => {
      const w = await mountView(rows())
      await w.get('[data-testid="only-uncategorized"]').setValue(true)
      await flushPromises()
      expect(w.findAll('[data-testid^="transaction-t"]').map((r) => r.attributes('data-testid'))).toEqual(['transaction-t2'])
      expect(mockReplace).toHaveBeenLastCalledWith({ query: expect.objectContaining({ filtro: 'sem-categoria' }) })
      await w.get('[data-testid="select-all"]').setValue(true)
      expect(w.get('[data-testid="batch-status"]').text()).toBe('1 transação selecionada')
    })

    it('abre já filtrada com ?filtro=sem-categoria e mostra o vazio quando não sobra nenhuma', async () => {
      route.query = { filtro: 'sem-categoria' }
      const w = await mountView([transaction('t1')])
      expect(w.get('[data-testid="uncategorized-empty"]').text()).toBe('Nenhuma transação sem categoria neste mês.')
      // o filtro continua à vista para poder ser desligado
      await w.get('[data-testid="only-uncategorized"]').setValue(false)
      await flushPromises()
      expect(w.find('[data-testid="transaction-t1"]').exists()).toBe(true)
      expect(w.find('[data-testid="only-uncategorized"]').exists()).toBe(false)
    })
  })

  describe('foco ao fechar os diálogos', () => {
    const focused = async () => {
      await new Promise((r) => setTimeout(r, 0))
      await flushPromises()
      return document.activeElement
    }

    it('cancelar a inclusão devolve o foco ao "Adicionar transação"', async () => {
      const w = await mountView([transaction('t1')])
      await w.get('[data-testid="add-transaction"]').trigger('click')
      await flushPromises()
      inDialog<HTMLButtonElement>('edit-cancel')!.click()
      await flushPromises()

      expect(inDialog('edit-dialog')).toBeNull()
      expect((await focused())?.getAttribute('data-testid')).toBe('add-transaction')
    })

    it('cancelar a edição devolve o foco ao "⋯" da linha', async () => {
      const w = await mountView([transaction('t1')])
      await chooseRowAction(w, 'edit', 't1')
      await flushPromises()
      inDialog<HTMLButtonElement>('edit-cancel')!.click()
      await flushPromises()

      expect(inDialog('edit-dialog')).toBeNull()
      expect((await focused())?.getAttribute('data-testid')).toBe('actions-t1')
    })

    it('cancelar a remoção devolve o foco ao "⋯" da linha', async () => {
      const w = await mountView([transaction('t1')])
      await chooseRowAction(w, 'remove', 't1')
      await flushPromises()
      inDialog<HTMLButtonElement>('confirm-cancel')!.click()
      await flushPromises()

      expect(inDialog('confirm-dialog')).toBeNull()
      expect((await focused())?.getAttribute('data-testid')).toBe('actions-t1')
    })

    it('remover a linha devolve o foco ao título, porque o botão dela saiu da lista', async () => {
      const w = await mountView([transaction('t1')])
      await chooseRowAction(w, 'remove', 't1')
      await flushPromises()
      mockList.mockResolvedValue([])
      inDialog<HTMLButtonElement>('confirm-action')!.click()
      await flushPromises()

      expect(inDialog('confirm-dialog')).toBeNull()
      expect((await focused())?.textContent?.trim()).toBe('Transações')
    })
  })

  describe('vindo da conclusão de uma importação', () => {
    afterEach(() => window.history.replaceState(null, ''))

    it('abre no mês da importação, mostra o resumo e limpa o estado', async () => {
      const message = '10 transações importadas. 10 são anteriores ao saldo informado e não alteram o saldo da conta.'
      window.history.replaceState({ importMonth: '2026-10', importMessage: message }, '')

      const w = await mountView([transaction('t1', { date: '2026-10-15' })])

      expect(mockList).toHaveBeenCalledExactlyOnceWith('2026-10')
      expect(w.get('[data-testid="month-label"]').text()).toBe('outubro de 2026')
      expect(w.get('[data-testid="transactions-success"]').text()).toBe(message)
      expect(window.history.state.importMessage).toBeNull()
      expect(window.history.state.importMonth).toBeNull()
    })

    it('sem o estado, abre no mês atual sem mensagem', async () => {
      const w = await mountView([])

      expect(mockList).toHaveBeenCalledExactlyOnceWith('2026-11')
      expect(w.get('[data-testid="transactions-success"]').text()).toBe('')
    })
  })

  describe('adicionar transação (Story 5.13)', () => {
    async function openCreate(w: VueWrapper) {
      await w.get('[data-testid="add-transaction"]').trigger('click')
      await flushPromises()
    }

    it('adiciona com hoje como data, leva a lista ao mês dela e mostra "Transação adicionada"', async () => {
      const w = await mountView([])
      await openCreate(w)
      expect(inDialog('edit-dialog')?.textContent).toContain('Adicionar transação')
      expect(inDialog<HTMLInputElement>('edit-date')?.value).toBe('2026-11-09')
      expect(inDialog<HTMLSelectElement>('edit-type')?.value).toBe('DEBIT')
      expect(inDialog<HTMLSelectElement>('edit-target')?.disabled).toBe(false)
      expect(inDialog('wallet-hint')).toBeNull()

      await setValue('edit-description', 'Feira')
      await setValue('edit-amount', '50,00')
      await setValue('edit-category', 'c1')
      await setValue('edit-target', 'account:a2')
      await setValue('edit-date', '2026-10-30')
      mockCreate.mockResolvedValue(transaction('n1'))
      mockList.mockResolvedValue([transaction('n1', { description: 'Feira' })])
      inDialog<HTMLFormElement>('edit-form')!.dispatchEvent(new Event('submit'))
      await flushPromises()

      expect(mockCreate).toHaveBeenCalledExactlyOnceWith({
        date: '2026-10-30',
        description: 'Feira',
        amount: 50,
        type: 'DEBIT',
        categoryId: 'c1',
        accountId: 'a2',
        creditCardId: null,
      })
      expect(mockUpdate).not.toHaveBeenCalled()
      expect(mockList).toHaveBeenLastCalledWith('2026-10')
      expect(w.find('[data-testid="transaction-n1"]').exists()).toBe(true)
      expect(w.get('[data-testid="transactions-success"]').text()).toBe('Transação adicionada')
      expect(inDialog('edit-dialog')).toBeNull()
    })

    it('sem categoria ou com valor zero mostra o erro e nada é enviado', async () => {
      const w = await mountView([])
      await openCreate(w)
      await setValue('edit-description', 'Feira')
      await setValue('edit-amount', '50,00')
      await setValue('edit-target', 'account:a1')
      inDialog<HTMLFormElement>('edit-form')!.dispatchEvent(new Event('submit'))
      await flushPromises()
      expect(inDialog('edit-error')?.textContent).toContain('Escolha uma categoria')

      await setValue('edit-category', 'c1')
      await setValue('edit-amount', '0')
      inDialog<HTMLFormElement>('edit-form')!.dispatchEvent(new Event('submit'))
      await flushPromises()
      expect(inDialog('edit-error')?.textContent).toContain('maior que zero')
      expect(mockCreate).not.toHaveBeenCalled()
    })

    it.each([
      [400, 'Esta alteração deixaria o saldo da conta fora do limite permitido'],
      [404, 'Conta não encontrada'],
    ])('falha %i mostra o detail e o diálogo continua aberto', async (status, detail) => {
      const w = await mountView([])
      await openCreate(w)
      await setValue('edit-description', 'Feira')
      await setValue('edit-amount', '50,00')
      await setValue('edit-category', 'c1')
      await setValue('edit-target', 'account:a1')
      mockCreate.mockRejectedValue({ response: { status, data: { detail } } })
      inDialog<HTMLFormElement>('edit-form')!.dispatchEvent(new Event('submit'))
      await flushPromises()

      expect(inDialog('edit-error')?.textContent).toContain(detail)
      expect(inDialog('edit-dialog')).not.toBeNull()
      expect(w.get('[data-testid="transactions-success"]').text()).toBe('')
    })

    it('sem conta ativa, sugere cadastrar uma conta Carteira', async () => {
      mockAccounts.mockResolvedValue([])
      const w = await mountView([])
      await openCreate(w)
      expect(inDialog('wallet-hint')?.textContent).toContain('cadastre uma conta Carteira')
    })
  })

  describe('edição em lote', () => {
    const rows = () => [
      transaction('t1', { description: 'Mercado' }),
      transaction('t2', { description: 'Padaria' }),
      transaction('t3', { description: 'Farmácia' }),
    ]
    const check = (w: VueWrapper, id: string) => w.get(`[data-testid="select-${id}"]`).setValue(true)

    it('sem seleção não há barra; marcar mostra a contagem e as ações', async () => {
      const w = await mountView(rows())
      expect(w.find('[data-testid="batch-bar"]').exists()).toBe(false)
      expect(w.get('[data-testid="select-t1"]').attributes('aria-label')).toBe('Selecionar Mercado')

      await check(w, 't1')
      await check(w, 't2')
      const bar = w.get('[data-testid="batch-bar"]')
      expect(bar.attributes('aria-label')).toBe('Ações em lote')
      expect(bar.get('[data-testid="batch-count"]').text()).toBe('2 selecionadas')
      expect(w.get('[data-testid="batch-status"]').text()).toBe('2 transações selecionadas')
      expect((w.get('[data-testid="select-all"]').element as HTMLInputElement).indeterminate).toBe(true)

      await w.get('[data-testid="batch-clear"]').trigger('click')
      expect(w.find('[data-testid="batch-bar"]').exists()).toBe(false)
    })

    it('"Selecionar todas do mês" marca e desmarca todas', async () => {
      const w = await mountView(rows())
      await w.get('[data-testid="select-all"]').trigger('change')
      expect(w.get('[data-testid="batch-count"]').text()).toBe('3 selecionadas')
      expect((w.get('[data-testid="select-all"]').element as HTMLInputElement).checked).toBe(true)
      await w.get('[data-testid="select-all"]').trigger('change')
      expect(w.find('[data-testid="batch-bar"]').exists()).toBe(false)
    })

    it('Mudar categoria aplica a categoria escolhida às selecionadas e recarrega', async () => {
      const w = await mountView(rows())
      await check(w, 't1')
      await check(w, 't3')
      await w.get('[data-testid="batch-category"]').trigger('click')
      await flushPromises()
      expect(inDialog('batch-dialog')?.textContent).toContain('2 transações selecionadas')
      await setValue('batch-category-select', 'c2')
      mockBatchCategory.mockResolvedValue(2)
      inDialog<HTMLFormElement>('batch-form')!.dispatchEvent(new Event('submit'))
      await flushPromises()

      expect(mockBatchCategory).toHaveBeenCalledExactlyOnceWith(['t1', 't3'], 'c2')
      expect(mockList).toHaveBeenCalledTimes(2)
      expect(inDialog('batch-dialog')).toBeNull()
      expect(w.get('[data-testid="batch-success"]').text()).toBe('2 transações atualizadas')
      expect(w.get('[data-testid="transactions-success"]').text()).toBe('')
      expect(w.find('[data-testid="batch-bar"]').exists()).toBe(false)
      expect(document.activeElement?.getAttribute('data-testid')).toBe('batch-success-action')
    })

    it('nada vem escolhido: Aplicar fica desabilitado até escolher uma categoria', async () => {
      const w = await mountView(rows())
      await check(w, 't2')
      await w.get('[data-testid="batch-category"]').trigger('click')
      await flushPromises()
      const select = inDialog<HTMLSelectElement>('batch-category-select')!
      expect(select.value).toBe('')
      expect(select.options[0]!.textContent).toBe('Escolha uma categoria')
      expect(select.options[0]!.disabled).toBe(true)
      expect(select.options[select.options.length - 1]!.textContent).toBe('Sem categoria')
      expect(inDialog<HTMLButtonElement>('batch-confirm')!.disabled).toBe(true)
      inDialog<HTMLFormElement>('batch-form')!.dispatchEvent(new Event('submit'))
      await flushPromises()
      expect(mockBatchCategory).not.toHaveBeenCalled()
    })

    it('Desfazer devolve cada transação à categoria que tinha', async () => {
      const list = [
        transaction('t1', { description: 'Mercado' }),
        transaction('t2', { description: 'Padaria', category: null }),
        transaction('t3', { description: 'Farmácia' }),
      ]
      const w = await mountView(list)
      await w.get('[data-testid="select-all"]').trigger('change')
      await w.get('[data-testid="batch-category"]').trigger('click')
      await flushPromises()
      await setValue('batch-category-select', 'c2')
      mockBatchCategory.mockResolvedValue(3)
      inDialog<HTMLFormElement>('batch-form')!.dispatchEvent(new Event('submit'))
      await flushPromises()

      mockBatchCategory.mockClear()
      await w.get('[data-testid="batch-success-action"]').trigger('click')
      await flushPromises()
      expect(mockBatchCategory).toHaveBeenCalledTimes(2)
      expect(mockBatchCategory).toHaveBeenCalledWith(['t1', 't3'], 'c1')
      expect(mockBatchCategory).toHaveBeenCalledWith(['t2'], null)
      expect(w.get('[data-testid="batch-success"]').text()).toBe('Alterações desfeitas')
      expect(w.find('[data-testid="batch-success-action"]').exists()).toBe(false)
    })

    it('"Sem categoria" manda null', async () => {
      const w = await mountView(rows())
      await check(w, 't2')
      await w.get('[data-testid="batch-category"]').trigger('click')
      await flushPromises()
      await setValue('batch-category-select', 'none')
      mockBatchCategory.mockResolvedValue(1)
      inDialog<HTMLFormElement>('batch-form')!.dispatchEvent(new Event('submit'))
      await flushPromises()
      expect(mockBatchCategory).toHaveBeenCalledExactlyOnceWith(['t2'], null)
      expect(w.get('[data-testid="batch-success"]').text()).toBe('1 transação atualizada')
    })

    it('Remover confirma, remove as selecionadas e avisa das parcelas futuras', async () => {
      const list = rows()
      list[0] = transaction('t1', { description: 'Loja', isInstallment: true, confirmedInstallments: 1, remainingInstallments: 3 })
      const w = await mountView(list)
      await check(w, 't1')
      await check(w, 't2')
      await w.get('[data-testid="batch-delete"]').trigger('click')
      await flushPromises()
      expect(inDialog('batch-dialog')?.textContent).toContain('Remover 2 transações')
      expect(inDialog('batch-futures')).not.toBeNull()
      mockBatchDelete.mockResolvedValue(2)
      mockList.mockResolvedValue([list[2]])
      inDialog<HTMLFormElement>('batch-form')!.dispatchEvent(new Event('submit'))
      await flushPromises()

      expect(mockBatchDelete).toHaveBeenCalledExactlyOnceWith(['t1', 't2'])
      expect(w.findAll('[data-testid^="transaction-"]')).toHaveLength(1)
      expect(w.get('[data-testid="batch-success"]').text()).toBe('2 transações removidas')
      expect(w.find('[data-testid="batch-success-action"]').exists()).toBe(false)
    })

    it('erro do lote não grava nada: mostra a mensagem e mantém a seleção', async () => {
      const w = await mountView(rows())
      await check(w, 't1')
      await w.get('[data-testid="batch-delete"]').trigger('click')
      await flushPromises()
      expect(inDialog('batch-futures')).toBeNull()
      mockBatchDelete.mockRejectedValue({ response: { status: 404, data: { detail: 'Transação não encontrada' } } })
      inDialog<HTMLFormElement>('batch-form')!.dispatchEvent(new Event('submit'))
      await flushPromises()

      expect(w.get('[data-testid="transactions-error"]').text()).toBe('Transação não encontrada')
      expect(w.get('[data-testid="batch-count"]').text()).toBe('1 selecionada')
    })

    it('trocar de mês limpa a seleção', async () => {
      const w = await mountView(rows())
      await check(w, 't1')
      await w.get('[data-testid="next-month"]').trigger('click')
      await flushPromises()
      expect(w.find('[data-testid="batch-bar"]').exists()).toBe(false)
    })
  })
})
