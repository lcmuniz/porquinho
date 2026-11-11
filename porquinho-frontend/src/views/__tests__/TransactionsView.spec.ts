import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, RouterLinkStub, type VueWrapper } from '@vue/test-utils'
import TransactionsView from '../TransactionsView.vue'
import type { Transaction } from '@/types/transaction'

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

async function setValue(testId: string, value: string) {
  const element = inDialog<HTMLInputElement | HTMLSelectElement>(testId)!
  element.value = value
  element.dispatchEvent(new Event(element.tagName === 'SELECT' ? 'change' : 'input'))
  await flushPromises()
}

describe('TransactionsView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
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
    expect(w.find('[data-testid="remove-futures-pix"]').exists()).toBe(false)

    await w.get('[data-testid="previous-month"]').trigger('click')
    await flushPromises()
    expect(mockList).toHaveBeenLastCalledWith('2026-10')
    await w.get('[data-testid="next-month"]').trigger('click')
    await w.get('[data-testid="next-month"]').trigger('click')
    await flushPromises()
    expect(mockList).toHaveBeenLastCalledWith('2026-12')
  })

  it('edita e mostra "Transação atualizada" numa região de status', async () => {
    const w = await mountView([transaction('t1')])
    await w.get('[data-testid="edit-t1"]').trigger('click')
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

    await w.get('[data-testid="edit-p3"]').trigger('click')
    await flushPromises()
    await setValue('edit-category', 'c2')
    mockUpdate.mockResolvedValue(transaction('p3', { ...installment, remainingInstallments: 7 }))
    inDialog<HTMLFormElement>('edit-form')!.dispatchEvent(new Event('submit'))
    await flushPromises()
    expect(status()).toBe('Transação atualizada. As 7 parcelas futuras também mudaram de categoria')

    await w.get('[data-testid="edit-p3"]').trigger('click')
    await flushPromises()
    await setValue('edit-category', '')
    mockUpdate.mockResolvedValue(transaction('p3', { ...installment, remainingInstallments: 1 }))
    inDialog<HTMLFormElement>('edit-form')!.dispatchEvent(new Event('submit'))
    await flushPromises()
    expect(status()).toBe('Transação atualizada. A parcela futura também mudou de categoria')

    // sem mudar a categoria, as futuras não mudam
    await w.get('[data-testid="edit-p3"]').trigger('click')
    await flushPromises()
    await setValue('edit-amount', '150,50')
    inDialog<HTMLFormElement>('edit-form')!.dispatchEvent(new Event('submit'))
    await flushPromises()
    expect(status()).toBe('Transação atualizada')
  })

  it('avisa sobre data futura sem bloquear o envio', async () => {
    const w = await mountView([transaction('t1')])
    await w.get('[data-testid="edit-t1"]').trigger('click')
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
    await w.get('[data-testid="edit-t1"]').trigger('click')
    await flushPromises()
    await setValue('edit-amount', '0')
    inDialog<HTMLFormElement>('edit-form')!.dispatchEvent(new Event('submit'))
    await flushPromises()

    expect(mockUpdate).not.toHaveBeenCalled()
    expect(inDialog('edit-error')?.textContent).toContain('maior que zero')
  })

  it('remove depois de confirmar e a linha sai da lista', async () => {
    const w = await mountView([transaction('t1')])
    await w.get('[data-testid="remove-t1"]').trigger('click')
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
    await w.get('[data-testid="remove-futures-p3"]').trigger('click')
    await flushPromises()
    expect(inDialog('confirm-dialog')?.textContent).toContain('7 parcelas futuras')
    mockRemoveFutures.mockResolvedValue(7)
    mockList.mockResolvedValue([transaction('p3', { ...installment, remainingInstallments: 0 })])
    inDialog<HTMLButtonElement>('confirm-action')!.click()
    await flushPromises()

    expect(mockRemoveFutures).toHaveBeenCalledExactlyOnceWith('p3')
    expect(w.get('[data-testid="row-installment"]').text()).toBe('Compra parcelada: 3/10')
    expect(w.find('[data-testid="remove-futures-p3"]').exists()).toBe(false)
    expect(w.get('[data-testid="transactions-success"]').text()).toBe('7 parcelas futuras removidas')
  })

  it('o botão de remover futuras cita a descrição', async () => {
    const w = await mountView([
      transaction('p3', { isInstallment: true, installmentNumber: 3, installmentTotal: 10, remainingInstallments: 7 }),
    ])
    expect(w.get('[data-testid="remove-futures-p3"]').attributes('aria-label')).toContain('Compra p3')
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
    await w.get('[data-testid="edit-p3"]').trigger('click')
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
    await w.get('[data-testid="remove-only"]').trigger('click')
    await flushPromises()
    expect(inDialog('futures-going-along')?.textContent).toContain('7 parcelas futuras')
    inDialog<HTMLButtonElement>('confirm-cancel')!.click()
    await flushPromises()

    await w.get('[data-testid="remove-one-of-two"]').trigger('click')
    await flushPromises()
    expect(inDialog('futures-going-along')).toBeNull()
  })

  it.each([
    [400, 'Esta alteração deixaria o saldo da conta fora do limite permitido', false],
    [404, 'Transação não encontrada', true],
    [409, 'Esta transação acabou de ser alterada. Recarregue a página e tente de novo.', true],
  ])('falha %i no salvar mostra o detail e recarrega só em 404/409', async (status, detail, reloads) => {
    const w = await mountView([transaction('t1')])
    await w.get('[data-testid="edit-t1"]').trigger('click')
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
    await w.get('[data-testid="remove-t1"]').trigger('click')
    await flushPromises()
    mockDelete.mockRejectedValue({ response: { status: 404, data: { detail: 'Transação não encontrada' } } })
    mockList.mockResolvedValue([])
    inDialog<HTMLButtonElement>('confirm-action')!.click()
    await flushPromises()

    expect(w.get('[data-testid="transactions-error"]').text()).toBe('Transação não encontrada')
    expect(w.find('[data-testid="transaction-t1"]').exists()).toBe(false)
    expect(w.get('[data-testid="transactions-success"]').text()).toBe('')
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
})
