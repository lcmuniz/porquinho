import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, RouterLinkStub, type VueWrapper } from '@vue/test-utils'
import ImportReviewView from '../ImportReviewView.vue'
import type { ImportTransaction, TransactionImport } from '@/types/transactionImport'

const mockGetImport = vi.fn()
const mockList = vi.fn()
const mockListImports = vi.fn()
const mockApprove = vi.fn()
const mockDiscard = vi.fn()
const mockPush = vi.fn()

vi.mock('@/services/transactionImportService', () => ({
  getImport: (...a: unknown[]) => mockGetImport(...a),
  listImportTransactions: (...a: unknown[]) => mockList(...a),
  listImports: (...a: unknown[]) => mockListImports(...a),
  approveHighConfidence: (...a: unknown[]) => mockApprove(...a),
  discardImport: (...a: unknown[]) => mockDiscard(...a),
}))

// Rota reativa: o teste troca o id para conferir a recarga
vi.mock('vue-router', async (importOriginal) => {
  const { reactive } = await import('vue')
  const route = reactive({ params: { id: 'i1' } })
  return {
    ...(await importOriginal<typeof import('vue-router')>()),
    useRoute: () => route,
    useRouter: () => ({ push: mockPush }),
    __route: route,
  }
})

async function testRoute() {
  const mod = (await import('vue-router')) as unknown as { __route: { params: { id: string } } }
  return mod.__route
}

const CATEGORY = { id: 'c1', name: 'Mercado', group: 'Alimentação' }

function importRow(overrides: Partial<TransactionImport> = {}): TransactionImport {
  return {
    id: 'i1',
    accountId: 'a1',
    creditCardId: null,
    targetName: 'Nubank',
    fileName: 'nubank-conta.ofx',
    fileType: 'OFX',
    bankName: 'NUBANK',
    fileSizeKb: 4,
    status: 'REVIEW',
    totalTransactions: 4,
    importedTransactions: 4,
    duplicatesFound: 0,
    transfersFound: 0,
    skippedRows: 0,
    classificationTotal: 4,
    classifiedCount: 4,
    errorMessage: null,
    createdAt: '2026-11-04T15:00:00Z',
    ...overrides,
  }
}

function transaction(id: string, overrides: Partial<ImportTransaction> = {}): ImportTransaction {
  return {
    id,
    date: '2026-11-03',
    description: `Compra ${id}`,
    amount: 189.9,
    type: 'DEBIT',
    kind: 'REGULAR',
    category: CATEGORY,
    source: 'AI',
    confidence: 92,
    reviewStatus: 'PENDING',
    isPix: false,
    pixDetails: null,
    isInstallment: false,
    installmentNumber: null,
    installmentTotal: null,
    isFutureInstallment: false,
    ...overrides,
  }
}

const plain = (s: string) => s.replace(/\s/g, ' ')

let wrapper: VueWrapper | null = null

async function mountView(rows: ImportTransaction[] = [], found = importRow()) {
  mockGetImport.mockResolvedValue(found)
  mockList.mockResolvedValue(rows)
  mockListImports.mockResolvedValue([])
  wrapper = mount(ImportReviewView, {
    attachTo: document.body,
    global: { stubs: { RouterLink: RouterLinkStub } },
  })
  await flushPromises()
  return wrapper
}

const groupIds = (w: VueWrapper) =>
  w.findAll('section[data-testid^="group-"]').map((s) => s.attributes('data-testid'))

const rowsOf = (w: VueWrapper, group: string) =>
  w.get(`[data-testid="group-${group}"]`).findAll('li').map((li) => li.attributes('data-testid'))

const count = (w: VueWrapper) => w.get('[data-testid="review-count"]').text()

const confirmButton = () =>
  document.body.querySelector('[data-testid="discard-confirm"]') as HTMLButtonElement | null

const cancelButton = () =>
  document.body.querySelector('[data-testid="discard-cancel"]') as HTMLButtonElement | null

const warning = () => document.body.querySelector('[data-testid="discard-warning"]')

async function openDialog(w: VueWrapper) {
  await w.get('[data-testid="discard"]').trigger('click')
  await flushPromises()
}

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((r) => {
    resolve = r
  })
  return { promise, resolve }
}

// Decisão 5A: a importação aberta é de 2026-11-04T15:00, na conta a1
const later = (overrides: Partial<TransactionImport> = {}) =>
  importRow({
    id: 'i2',
    fileName: 'novembro.ofx',
    status: 'REVIEW',
    duplicatesFound: 2,
    createdAt: '2026-11-05T15:00:00Z',
    ...overrides,
  })

describe('ImportReviewView', () => {
  beforeEach(async () => {
    vi.clearAllMocks()
    ;(await testRoute()).params.id = 'i1'
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
  })

  it('agrupa por confiança, com o pagamento de fatura à parte, e conta X e Y', async () => {
    const w = await mountView([
      transaction('low', { confidence: 30 }),
      transaction('payment', { kind: 'CARD_PAYMENT', confidence: null, category: null }),
      transaction('medium', { confidence: 60 }),
      transaction('high', { confidence: 92 }),
    ])

    expect(groupIds(w)).toEqual(['group-high', 'group-medium', 'group-low', 'group-card-payment'])
    expect(w.get('[data-testid="group-card-payment"]').text()).toContain('Pagamento de fatura')
    expect(count(w)).toBe('1 transação de alta confiança, 2 precisam de revisão')
  })

  it('respeita os limites 80, 79, 50 e 49', async () => {
    const w = await mountView([
      transaction('c80', { confidence: 80 }),
      transaction('c79', { confidence: 79 }),
      transaction('c50', { confidence: 50 }),
      transaction('c49', { confidence: 49 }),
    ])

    expect(rowsOf(w, 'high')).toEqual(['transaction-c80'])
    expect(rowsOf(w, 'medium')).toEqual(['transaction-c79', 'transaction-c50'])
    expect(rowsOf(w, 'low')).toEqual(['transaction-c49'])
    expect(count(w)).toBe('1 transação de alta confiança, 3 precisam de revisão')
  })

  it('usa o singular quando só uma linha precisa de revisão', async () => {
    const w = await mountView([
      transaction('a'),
      transaction('b'),
      transaction('c', { confidence: 10 }),
    ])

    expect(count(w)).toBe('2 transações de alta confiança, 1 precisa de revisão')
  })

  it('confiança nula ou falta de categoria ficam na Baixa como "Não classificado"', async () => {
    const w = await mountView([
      transaction('legacy', { confidence: null }),
      transaction('no-category', { confidence: 92, category: null }),
    ])

    expect(rowsOf(w, 'low')).toEqual(['transaction-legacy', 'transaction-no-category'])
    for (const id of ['legacy', 'no-category']) {
      expect(w.get(`[data-testid="transaction-${id}"] [data-testid="row-confidence"]`).text()).toBe('Não classificado')
    }
    expect(count(w)).toBe('0 transações de alta confiança, 2 precisam de revisão')
  })

  it('mostra data do extrato, valor em BRL, categoria e indicadores com ícone e texto', async () => {
    const w = await mountView([
      transaction('high', { description: 'Mercado Bom', date: '2026-11-03' }),
      transaction('medium', { confidence: 60, type: 'CREDIT', amount: 50, category: { id: 'c2', name: 'Salário', group: null } }),
      transaction('low', { confidence: 30 }),
    ])

    const high = w.get('[data-testid="transaction-high"]')
    expect(high.get('[data-testid="row-description"]').text()).toBe('Mercado Bom')
    expect(high.get('[data-testid="row-date"]').text()).toBe('03/11/2026')
    expect(plain(high.get('[data-testid="row-amount"]').text())).toBe('-R$ 189,90')
    expect(high.get('[data-testid="row-category"]').text()).toBe('Alimentação › Mercado')
    expect(high.get('[data-testid="row-confidence"]').text()).toBe('Alta confiança (92%)')
    expect(high.get('[data-testid="row-confidence"] svg').classes()).toContain('lucide-circle-check-icon')

    const medium = w.get('[data-testid="transaction-medium"]')
    expect(plain(medium.get('[data-testid="row-amount"]').text())).toBe('+R$ 50,00')
    expect(medium.get('[data-testid="row-category"]').text()).toBe('Salário')
    expect(medium.get('[data-testid="row-confidence"]').text()).toBe('Média confiança (60%)')
    expect(medium.get('[data-testid="row-confidence"] svg').classes()).toContain('lucide-triangle-alert-icon')

    const low = w.get('[data-testid="transaction-low"]')
    expect(low.get('[data-testid="row-confidence"]').text()).toBe('Baixa confiança (30%)')
    expect(low.get('[data-testid="row-confidence"] svg').classes()).toContain('lucide-flag-icon')
  })

  it('pagamento de fatura não tem indicador de confiança nem "Não classificado"', async () => {
    const w = await mountView([transaction('payment', { kind: 'CARD_PAYMENT', confidence: null, category: null })])

    const row = w.get('[data-testid="transaction-payment"]')
    expect(row.find('[data-testid="row-confidence"]').exists()).toBe(false)
    expect(row.text()).not.toContain('Não classificado')
  })

  it('marca Pix com o texto da direção e da contraparte', async () => {
    const w = await mountView([
      transaction('sent', { isPix: true, pixDetails: { direction: 'SENT', counterpart_name: 'MARIA' } }),
      transaction('received', { isPix: true, pixDetails: { direction: 'RECEIVED' } }),
      transaction('bare', { isPix: true }),
      transaction('none'),
    ])

    const pix = (id: string) => w.get(`[data-testid="transaction-${id}"] [data-testid="row-pix"]`).text()
    expect(pix('sent')).toBe('Pix enviado para MARIA')
    expect(pix('received')).toBe('Pix recebido')
    expect(pix('bare')).toBe('Pix')
    expect(w.find('[data-testid="transaction-none"] [data-testid="row-pix"]').exists()).toBe(false)
  })

  it('marca a parcela com o que resta da compra', async () => {
    const installment = { isInstallment: true, installmentTotal: 10 }
    const w = await mountView([
      transaction('p3', { ...installment, installmentNumber: 3 }),
      transaction('p9', { ...installment, installmentNumber: 9 }),
    ])

    expect(w.get('[data-testid="transaction-p3"] [data-testid="row-installment"]').text()).toBe(
      'Compra parcelada: 3/10 — restam 7 parcelas',
    )
    expect(w.get('[data-testid="transaction-p9"] [data-testid="row-installment"]').text()).toBe(
      'Compra parcelada: 9/10 — resta 1 parcela',
    )
    expect(count(w)).toBe('2 transações de alta confiança, 0 precisam de revisão')
  })

  it('a última parcela não fala em "restam 0"', async () => {
    const w = await mountView([
      transaction('p10', { isInstallment: true, installmentNumber: 10, installmentTotal: 10 }),
    ])

    expect(w.get('[data-testid="row-installment"]').text()).toBe('Compra parcelada: 10/10 — última parcela')
  })

  it('só com pagamento de fatura, X e Y são 0 e "Aprovar todas" fica desabilitado', async () => {
    const w = await mountView([transaction('payment', { kind: 'CARD_PAYMENT', confidence: null, category: null })])

    expect(count(w)).toBe('0 transações de alta confiança, 0 precisam de revisão')
    expect(w.get('[data-testid="approve-all"]').attributes('disabled')).toBeDefined()
  })

  it('aprova as de alta confiança, recarrega e mantém o estado na tela', async () => {
    const w = await mountView([transaction('high'), transaction('medium', { confidence: 60 })])
    mockApprove.mockResolvedValue({ importId: 'i1', status: 'REVIEW', affectedTransactions: 1 })
    mockList.mockResolvedValue([
      transaction('high', { reviewStatus: 'APPROVED' }),
      transaction('medium', { confidence: 60 }),
    ])

    await w.get('[data-testid="approve-all"]').trigger('click')
    await flushPromises()

    expect(mockApprove).toHaveBeenCalledWith('i1')
    expect(w.get('[data-testid="review-success"]').text()).toBe('1 transação aprovada')
    expect(w.get('[data-testid="transaction-high"] [data-testid="row-approved"]').text()).toBe('Aprovada')
    expect(w.find('[data-testid="transaction-medium"] [data-testid="row-approved"]').exists()).toBe(false)
    expect(w.get('[data-testid="approve-all"]').attributes('disabled')).toBeDefined()
  })

  it('a recarga depois de aprovar não troca a tela pelo esqueleto e a região de status já estava no DOM', async () => {
    const w = await mountView([transaction('high')])
    const region = w.get('[data-testid="review-status-region"]')
    expect(region.attributes('role')).toBe('status')
    expect(region.text()).toBe('')
    mockApprove.mockResolvedValue({ importId: 'i1', status: 'REVIEW', affectedTransactions: 1 })
    const reload = deferred<ImportTransaction[]>()
    mockList.mockReturnValue(reload.promise)

    await w.get('[data-testid="approve-all"]').trigger('click')
    await flushPromises()

    expect(w.find('[data-testid="review-skeleton"]').exists()).toBe(false)
    expect(w.find('[data-testid="transaction-high"]').exists()).toBe(true)

    reload.resolve([transaction('high', { reviewStatus: 'APPROVED' })])
    await flushPromises()

    expect(w.get('[data-testid="review-status-region"]').element).toBe(region.element)
    expect(region.text()).toBe('1 transação aprovada')
  })

  it('ao voltar depois de aprovar, a linha continua aprovada e o botão desabilitado', async () => {
    const w = await mountView([transaction('high', { reviewStatus: 'APPROVED' })])

    expect(w.get('[data-testid="row-approved"]').text()).toBe('Aprovada')
    expect(count(w)).toBe('1 transação de alta confiança, 0 precisam de revisão')
    expect(w.get('[data-testid="approve-all"]').attributes('disabled')).toBeDefined()
  })

  it('409 ao aprovar recarrega e mostra que a importação saiu da revisão', async () => {
    const w = await mountView([transaction('high')])
    mockApprove.mockRejectedValue({
      response: { status: 409, data: { detail: 'Esta importação não está em revisão' } },
    })
    mockGetImport.mockResolvedValue(importRow({ status: 'COMPLETED' }))

    await w.get('[data-testid="approve-all"]').trigger('click')
    await flushPromises()

    expect(w.find('[data-testid="review-closed"]').exists()).toBe(true)
  })

  it('erro ao aprovar mantém a tela e avisa', async () => {
    const w = await mountView([transaction('high')])
    mockApprove.mockRejectedValue(new Error('rede'))

    await w.get('[data-testid="approve-all"]').trigger('click')
    await flushPromises()

    expect(w.get('[data-testid="review-action-error"]').text()).toContain('Não foi possível')
    expect(w.find('[data-testid="transaction-high"]').exists()).toBe(true)
  })

  it('descartar pede confirmação e só então chama a API e volta para /importar', async () => {
    const w = await mountView([transaction('high')])
    mockDiscard.mockResolvedValue({ importId: 'i1', status: 'DISCARDED', affectedTransactions: 1 })

    await w.get('[data-testid="discard"]').trigger('click')
    await flushPromises()
    expect(mockDiscard).not.toHaveBeenCalled()

    confirmButton()?.click()
    await flushPromises()

    expect(mockDiscard).toHaveBeenCalledWith('i1')
    expect(mockPush).toHaveBeenCalledWith('/importar')
  })

  it('cancelar o diálogo não descarta', async () => {
    const w = await mountView([transaction('high')])

    await w.get('[data-testid="discard"]').trigger('click')
    await flushPromises()
    const cancel = [...document.body.querySelectorAll('button')].find((b) => b.textContent?.trim() === 'Cancelar')
    cancel?.click()
    await flushPromises()

    expect(mockDiscard).not.toHaveBeenCalled()
    expect(confirmButton()).toBeNull()
  })

  it('com o descarte em andamento, "Cancelar" fica desabilitado e o diálogo não fecha', async () => {
    const w = await mountView([transaction('high')])
    const pending = deferred<unknown>()
    mockDiscard.mockReturnValue(pending.promise)
    await openDialog(w)

    confirmButton()?.click()
    await flushPromises()

    expect(cancelButton()?.disabled).toBe(true)
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await flushPromises()
    expect(confirmButton()).not.toBeNull()

    pending.resolve({ importId: 'i1', status: 'DISCARDED', affectedTransactions: 1 })
    await flushPromises()
    expect(mockPush).toHaveBeenCalledWith('/importar')
  })

  it('trocar de importação fecha o diálogo de descarte', async () => {
    const w = await mountView([transaction('high')])
    await openDialog(w)
    expect(confirmButton()).not.toBeNull()

    ;(await testRoute()).params.id = 'i9'
    await flushPromises()

    expect(confirmButton()).toBeNull()
    expect(mockGetImport).toHaveBeenLastCalledWith('i9')
  })

  it('avisa, com o nome do arquivo, sobre importação posterior do mesmo destino com duplicatas (5A)', async () => {
    const w = await mountView([transaction('high')])
    mockListImports.mockResolvedValue([later(), importRow()])

    await openDialog(w)

    expect(warning()?.textContent).toContain('novembro.ofx')
    expect(warning()?.textContent).toContain('envie esses arquivos de novo')
    expect(confirmButton()?.disabled).toBe(false)
  })

  it.each([
    ['sem importação posterior', []],
    ['posterior de outro destino', [later({ accountId: 'a2' })]],
    ['posterior no cartão', [later({ accountId: null, creditCardId: 'c1' })]],
    ['anterior', [later({ createdAt: '2026-11-03T15:00:00Z' })]],
    ['posterior descartada', [later({ status: 'DISCARDED' })]],
    ['posterior que falhou', [later({ status: 'FAILED' })]],
    ['posterior sem duplicatas', [later({ duplicatesFound: 0 })]],
  ])('não avisa: %s (5A)', async (_name, imports) => {
    const w = await mountView([transaction('high')])
    mockListImports.mockResolvedValue(imports)

    await openDialog(w)

    expect(warning()).toBeNull()
    expect(confirmButton()?.disabled).toBe(false)
  })

  it('"Descartar" fica desabilitado até a checagem terminar (5A)', async () => {
    const w = await mountView([transaction('high')])
    const check = deferred<TransactionImport[]>()
    mockListImports.mockReturnValue(check.promise)

    await openDialog(w)
    expect(confirmButton()?.disabled).toBe(true)

    check.resolve([])
    await flushPromises()
    expect(confirmButton()?.disabled).toBe(false)
  })

  it('se a checagem falhar, o diálogo funciona sem o aviso (5A)', async () => {
    const w = await mountView([transaction('high')])
    mockListImports.mockRejectedValue(new Error('rede'))
    mockDiscard.mockResolvedValue({ importId: 'i1', status: 'DISCARDED', affectedTransactions: 1 })

    await openDialog(w)

    expect(warning()).toBeNull()
    confirmButton()?.click()
    await flushPromises()
    expect(mockDiscard).toHaveBeenCalledWith('i1')
  })

  it('409 ao descartar fecha o diálogo, fica na tela e mostra que saiu da revisão', async () => {
    const w = await mountView([transaction('high')])
    mockDiscard.mockRejectedValue({
      response: { status: 409, data: { detail: 'Esta importação não está em revisão' } },
    })
    mockGetImport.mockResolvedValue(importRow({ status: 'COMPLETED' }))
    await openDialog(w)

    confirmButton()?.click()
    await flushPromises()

    expect(mockPush).not.toHaveBeenCalled()
    expect(confirmButton()).toBeNull()
    expect(w.find('[data-testid="review-closed"]').exists()).toBe(true)
  })

  it('404 numa ação mostra que a importação não existe', async () => {
    const w = await mountView([transaction('high')])
    mockApprove.mockRejectedValue({ response: { status: 404 } })

    await w.get('[data-testid="approve-all"]').trigger('click')
    await flushPromises()

    expect(w.find('[data-testid="review-not-found"]').exists()).toBe(true)
  })

  it('"Continuar depois" volta para /importar', async () => {
    const w = await mountView([transaction('high')])

    expect(w.getComponent(RouterLinkStub).props('to')).toBe('/importar')
    expect(w.get('[data-testid="continue-later"]').text()).toBe('Continuar depois')
  })

  it('importação fora de REVIEW não oferece as ações', async () => {
    const w = await mountView([], importRow({ status: 'DISCARDED' }))

    expect(w.find('[data-testid="review-closed"]').exists()).toBe(true)
    expect(w.find('[data-testid="approve-all"]').exists()).toBe(false)
    expect(w.find('[data-testid="discard"]').exists()).toBe(false)
  })

  it('404 mostra que a importação não existe', async () => {
    mockGetImport.mockRejectedValue({ response: { status: 404 } })
    mockList.mockRejectedValue({ response: { status: 404 } })
    wrapper = mount(ImportReviewView, { global: { stubs: { RouterLink: RouterLinkStub } } })
    await flushPromises()

    expect(wrapper.find('[data-testid="review-not-found"]').exists()).toBe(true)
  })

  it('400 de id malformado mostra que a importação não existe, sem tentar de novo', async () => {
    mockGetImport.mockRejectedValue({ response: { status: 400 } })
    mockList.mockRejectedValue({ response: { status: 400 } })
    wrapper = mount(ImportReviewView, { global: { stubs: { RouterLink: RouterLinkStub } } })
    await flushPromises()

    expect(wrapper.find('[data-testid="review-not-found"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="review-retry"]').exists()).toBe(false)
  })

  it('falha na carga oferece tentar de novo', async () => {
    mockGetImport.mockRejectedValueOnce(new Error('rede'))
    mockGetImport.mockResolvedValue(importRow())
    mockList.mockResolvedValue([transaction('high')])
    wrapper = mount(ImportReviewView, { global: { stubs: { RouterLink: RouterLinkStub } } })
    await flushPromises()

    await wrapper.get('[data-testid="review-retry"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="transaction-high"]').exists()).toBe(true)
  })
})
