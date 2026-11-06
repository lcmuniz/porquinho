import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, RouterLinkStub, type VueWrapper } from '@vue/test-utils'
import ImportReviewView from '../ImportReviewView.vue'
import type { ImportTransaction, TransactionImport } from '@/types/transactionImport'

const mockGetImport = vi.fn()
const mockList = vi.fn()
const mockListImports = vi.fn()
const mockApprove = vi.fn()
const mockDiscard = vi.fn()
const mockUpdateCategory = vi.fn()
const mockComplete = vi.fn()
const mockListCategories = vi.fn()
const mockGetOnboarding = vi.fn()
const mockCompleteOnboardingImport = vi.fn()
const mockPush = vi.fn()

vi.mock('@/services/transactionImportService', () => ({
  getImport: (...a: unknown[]) => mockGetImport(...a),
  listImportTransactions: (...a: unknown[]) => mockList(...a),
  listImports: (...a: unknown[]) => mockListImports(...a),
  approveHighConfidence: (...a: unknown[]) => mockApprove(...a),
  discardImport: (...a: unknown[]) => mockDiscard(...a),
  updateImportCategory: (...a: unknown[]) => mockUpdateCategory(...a),
  completeTransactionImport: (...a: unknown[]) => mockComplete(...a),
}))

vi.mock('@/services/categoryService', () => ({
  listCategories: (...a: unknown[]) => mockListCategories(...a),
}))

vi.mock('@/services/onboardingService', () => ({
  getMyOnboarding: (...a: unknown[]) => mockGetOnboarding(...a),
  completeImport: (...a: unknown[]) => mockCompleteOnboardingImport(...a),
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

  it('mostra "Continuar depois"', async () => {
    const w = await mountView([transaction('high')])

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

  // ---- Story 5.10 ----

  const inDialog = <T extends Element = HTMLElement>(testId: string) =>
    document.body.querySelector<T>(`[data-testid="${testId}"]`)

  const categoriesList = [
    { id: 'g1', categoryName: 'Alimentação', parentCategoryId: null },
    { id: 'g2', categoryName: 'Vazio', parentCategoryId: null },
    { id: 'c2', categoryName: 'Restaurante', parentCategoryId: 'g1' },
    { id: 'c1', categoryName: 'Mercado', parentCategoryId: 'g1' },
  ]

  async function openEdit(w: VueWrapper, id: string) {
    await w.get(`[data-testid="edit-${id}"]`).trigger('click')
    await flushPromises()
  }

  async function choose(categoryId: string) {
    const select = inDialog<HTMLSelectElement>('category-select')!
    select.value = categoryId
    select.dispatchEvent(new Event('change'))
    await flushPromises()
  }

  async function save() {
    inDialog<HTMLFormElement>('category-form')!.dispatchEvent(new Event('submit', { cancelable: true }))
    await flushPromises()
  }

  async function openComplete(w: VueWrapper) {
    await w.get('[data-testid="complete-import"]').trigger('click')
    await flushPromises()
  }

  async function confirmComplete() {
    inDialog('complete-confirm')!.click()
    await flushPromises()
  }

  describe('edição de categoria', () => {
    beforeEach(() => {
      mockListCategories.mockReset().mockResolvedValue(categoriesList)
      mockUpdateCategory.mockReset().mockResolvedValue({ importId: 'i1', status: 'REVIEW', affectedTransactions: 1 })
    })

    it('mostra data, descrição, valor e categoria atual só para leitura, e o select agrupado', async () => {
      const w = await mountView([transaction('t1', { confidence: 60 })])

      await openEdit(w, 't1')

      expect(inDialog('dialog-date')?.textContent).toContain('03/11/2026')
      expect(inDialog('dialog-description')?.textContent).toContain('Compra t1')
      expect(plain(inDialog('dialog-amount')?.textContent ?? '')).toContain('189,90')
      expect(inDialog('dialog-current-category')?.textContent).toContain('Alimentação › Mercado')
      expect(document.body.querySelectorAll('dd input, dd select, dd textarea')).toHaveLength(0)

      const select = inDialog<HTMLSelectElement>('category-select')!
      expect(select.value).toBe('c1')
      const groups = [...select.querySelectorAll('optgroup')]
      expect(groups.map((g) => g.label)).toEqual(['Alimentação'])
      expect([...groups[0]!.querySelectorAll('option')].map((o) => o.textContent?.trim())).toEqual([
        'Mercado',
        'Restaurante',
      ])
    })

    it('linha sem categoria mostra "Não classificado" e exige a escolha', async () => {
      const w = await mountView([transaction('t1', { category: null, confidence: null })])

      await openEdit(w, 't1')

      expect(inDialog('dialog-current-category')?.textContent).toContain('Não classificado')
      expect(inDialog<HTMLButtonElement>('category-save')?.disabled).toBe(true)
    })

    it('categoria atual removida não fica escolhida', async () => {
      const w = await mountView([transaction('t1', { category: { id: 'x', name: 'Antiga', group: null } })])

      await openEdit(w, 't1')

      expect(inDialog('dialog-current-category')?.textContent).toContain('Antiga')
      expect(inDialog<HTMLSelectElement>('category-select')?.value).toBe('')
      expect(inDialog<HTMLButtonElement>('category-save')?.disabled).toBe(true)
    })

    it('salvar envia a linha e leva para "Revisadas por você", com ícone e texto', async () => {
      const w = await mountView([transaction('t1', { confidence: 60 }), transaction('t2', { confidence: 60 })])
      expect(count(w)).toContain('2 precisam de revisão')
      await openEdit(w, 't1')
      await choose('c2')
      mockList.mockResolvedValue([
        transaction('t1', {
          category: { id: 'c2', name: 'Restaurante', group: 'Alimentação' },
          source: 'USER',
          confidence: 100,
          reviewStatus: 'APPROVED',
        }),
        transaction('t2', { confidence: 60 }),
      ])

      await save()

      expect(mockUpdateCategory).toHaveBeenCalledExactlyOnceWith('i1', ['t1'], 'c2')
      expect(groupIds(w)).toEqual(['group-medium', 'group-reviewed'])
      expect(w.get('[data-testid="group-reviewed"] h2').text()).toBe('Revisadas por você (1)')
      const mark = w.get('[data-testid="group-reviewed"] [data-testid="row-confidence"]')
      expect(mark.text()).toBe('Revisada por você')
      expect(mark.find('svg').exists()).toBe(true)
      expect(w.find('[data-testid="group-reviewed"] [data-testid="row-approved"]').exists()).toBe(false)
      expect(count(w)).toContain('1 precisa de revisão')
      expect(w.get('[data-testid="review-success"]').text()).toBe('1 categoria alterada')
      expect(inDialog('category-dialog')).toBeNull()
    })

    it('salvar a mesma categoria também é permitido (decisão 2A)', async () => {
      const w = await mountView([transaction('t1', { confidence: 60 })])

      await openEdit(w, 't1')
      await save()

      expect(mockUpdateCategory).toHaveBeenCalledExactlyOnceWith('i1', ['t1'], 'c1')
    })

    it('linha revisada não conta como alta confiança nem como revisão', async () => {
      const w = await mountView([transaction('t1', { source: 'USER', confidence: 100, reviewStatus: 'APPROVED' })])

      expect(count(w)).toBe('0 transações de alta confiança, 0 precisam de revisão')
      expect(groupIds(w)).toEqual(['group-reviewed'])
    })

    it('pagamento de fatura não tem checkbox nem "Editar"', async () => {
      const w = await mountView([transaction('t1', { kind: 'CARD_PAYMENT', category: null, confidence: null })])

      expect(w.find('[data-testid="select-t1"]').exists()).toBe(false)
      expect(w.find('[data-testid="edit-t1"]').exists()).toBe(false)
    })

    it('"Alterar categoria" muda as marcadas e limpa a seleção', async () => {
      const w = await mountView([transaction('t1'), transaction('t2'), transaction('t3')])
      const button = w.get('[data-testid="change-category"]')
      expect(button.attributes('disabled')).toBeDefined()

      await w.get('[data-testid="select-t1"]').setValue(true)
      await w.get('[data-testid="select-t3"]').setValue(true)
      expect(button.text()).toBe('Alterar categoria (2)')
      await button.trigger('click')
      await flushPromises()
      expect(inDialog('category-dialog-row')).toBeNull()
      expect(inDialog('category-dialog')?.textContent).toContain('2 transações selecionadas recebem')
      await choose('c2')
      mockUpdateCategory.mockResolvedValue({ importId: 'i1', status: 'REVIEW', affectedTransactions: 2 })

      await save()

      expect(mockUpdateCategory).toHaveBeenCalledExactlyOnceWith('i1', ['t1', 't3'], 'c2')
      expect(w.get('[data-testid="review-success"]').text()).toBe('2 categorias alteradas')
      expect(w.get('[data-testid="change-category"]').text()).toBe('Alterar categoria')
      expect((w.get('[data-testid="select-t1"]').element as HTMLInputElement).checked).toBe(false)
    })

    it('desmarcar tira a linha da seleção', async () => {
      const w = await mountView([transaction('t1')])
      const box = w.get('[data-testid="select-t1"]')

      await box.setValue(true)
      await box.setValue(false)

      expect(w.get('[data-testid="change-category"]').attributes('disabled')).toBeDefined()
    })

    it('404 de categoria removida mostra a mensagem e mantém a revisão', async () => {
      mockUpdateCategory.mockRejectedValue({
        response: { status: 404, data: { detail: 'Categoria não encontrada' } },
      })
      const w = await mountView([transaction('t1')])
      await openEdit(w, 't1')

      await save()

      expect(w.get('[data-testid="review-action-error"]').text()).toBe('Categoria não encontrada')
      expect(w.find('[data-testid="review-not-found"]').exists()).toBe(false)
      expect(w.find('[data-testid="transaction-t1"]').exists()).toBe(true)
    })

    it('409 recarrega e mostra que a importação saiu da revisão', async () => {
      mockUpdateCategory.mockRejectedValue({
        response: { status: 409, data: { detail: 'Esta importação não está em revisão' } },
      })
      const w = await mountView([transaction('t1')])
      await openEdit(w, 't1')
      mockGetImport.mockResolvedValue(importRow({ status: 'COMPLETED' }))

      await save()

      expect(w.find('[data-testid="review-closed"]').exists()).toBe(true)
    })

    it('com as categorias carregando, "Salvar" fica desabilitado e não grava', async () => {
      const pending = deferred<unknown>()
      mockListCategories.mockReturnValue(pending.promise)
      const w = await mountView([transaction('t1')])

      await openEdit(w, 't1')

      expect(inDialog<HTMLButtonElement>('category-save')?.disabled).toBe(true)
      await save()
      expect(mockUpdateCategory).not.toHaveBeenCalled()

      pending.resolve(categoriesList)
      await flushPromises()
      expect(inDialog<HTMLButtonElement>('category-save')?.disabled).toBe(false)
    })

    it('com a carga das categorias falhada, "Salvar" fica desabilitado e não grava', async () => {
      mockListCategories.mockRejectedValue(new Error('Network Error'))
      const w = await mountView([transaction('t1')])

      await openEdit(w, 't1')

      expect(inDialog<HTMLButtonElement>('category-save')?.disabled).toBe(true)
      await save()
      expect(mockUpdateCategory).not.toHaveBeenCalled()
    })

    it('em linha parcelada avisa que as parcelas futuras recebem a mesma categoria', async () => {
      const w = await mountView([
        transaction('t1', { isInstallment: true, installmentNumber: 3, installmentTotal: 10 }),
        transaction('t2'),
      ])

      await openEdit(w, 't1')
      expect(inDialog('category-dialog')?.textContent).toContain('parcelas futuras desta compra')

      inDialog('category-cancel')!.click()
      await flushPromises()
      await openEdit(w, 't2')
      expect(inDialog('category-dialog')?.textContent).toContain('só a categoria desta transação')
    })

    it('a recarga tira da seleção a linha que saiu da revisão', async () => {
      const w = await mountView([transaction('t1'), transaction('t2')])
      await w.get('[data-testid="select-t1"]').setValue(true)
      await w.get('[data-testid="select-t2"]').setValue(true)
      await w.get('[data-testid="change-category"]').trigger('click')
      await flushPromises()
      await choose('c2')
      mockUpdateCategory.mockRejectedValueOnce({
        response: { status: 404, data: { detail: 'Transação não encontrada nesta revisão' } },
      })
      mockList.mockResolvedValue([transaction('t1')])

      await save()

      expect(w.get('[data-testid="change-category"]').text()).toBe('Alterar categoria (1)')

      await w.get('[data-testid="change-category"]').trigger('click')
      await flushPromises()
      await choose('c2')
      await save()

      expect(mockUpdateCategory).toHaveBeenLastCalledWith('i1', ['t1'], 'c2')
    })

    it('com o PATCH em andamento o diálogo não fecha', async () => {
      const pending = deferred<unknown>()
      mockUpdateCategory.mockReturnValue(pending.promise)
      const w = await mountView([transaction('t1')])
      await openEdit(w, 't1')

      await save()
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
      await flushPromises()

      expect(inDialog('category-dialog')).not.toBeNull()
      expect(inDialog<HTMLButtonElement>('category-cancel')?.disabled).toBe(true)

      pending.resolve({ importId: 'i1', status: 'REVIEW', affectedTransactions: 1 })
      await flushPromises()
      expect(inDialog('category-dialog')).toBeNull()
    })

    it('falha ao carregar as categorias avisa no diálogo e deixa tentar de novo', async () => {
      mockListCategories.mockRejectedValueOnce(new Error('Network Error'))
      const w = await mountView([transaction('t1', { category: null, confidence: null })])

      await openEdit(w, 't1')

      const error = inDialog('categories-error')!
      expect(error.textContent).toContain('Não foi possível carregar suas categorias')
      error.querySelector('button')!.click()
      await flushPromises()
      expect(inDialog('categories-error')).toBeNull()
      expect(inDialog<HTMLSelectElement>('category-select')?.querySelectorAll('optgroup')).toHaveLength(1)
    })
  })

  describe('saída da revisão no onboarding', () => {
    const atStep = (currentStep: number) => ({ currentStep, stepsCompleted: [], completed: false })

    async function leaveLater(w: VueWrapper) {
      await w.get('[data-testid="continue-later"]').trigger('click')
      await flushPromises()
    }

    async function discardNow(w: VueWrapper) {
      await openDialog(w)
      confirmButton()!.click()
      await flushPromises()
    }

    beforeEach(() => {
      mockGetOnboarding.mockReset().mockResolvedValue(atStep(6))
      mockCompleteOnboardingImport.mockReset()
      mockDiscard.mockReset().mockResolvedValue({ importId: 'i1', status: 'DISCARDED', affectedTransactions: 1 })
    })

    it('no passo 6, "Continuar depois" vai para /onboarding sem o POST', async () => {
      const w = await mountView([transaction('t1')])

      await leaveLater(w)

      expect(mockPush).toHaveBeenCalledExactlyOnceWith('/onboarding')
      expect(mockCompleteOnboardingImport).not.toHaveBeenCalled()
    })

    it('no passo 6, o descarte vai para /onboarding sem o POST', async () => {
      const w = await mountView([transaction('t1')])

      await discardNow(w)

      expect(mockPush).toHaveBeenCalledExactlyOnceWith('/onboarding')
      expect(mockCompleteOnboardingImport).not.toHaveBeenCalled()
    })

    it('em outro passo ou com o onboarding concluído, os dois vão para a /importar', async () => {
      mockGetOnboarding.mockResolvedValueOnce(atStep(5))
      const w = await mountView([transaction('t1')])
      await leaveLater(w)

      mockGetOnboarding.mockResolvedValueOnce({ ...atStep(6), completed: true })
      await discardNow(w)

      expect(mockPush.mock.calls).toEqual([['/importar'], ['/importar']])
    })

    it('sem conseguir ler o onboarding, os dois vão para a /importar', async () => {
      mockGetOnboarding.mockRejectedValue(new Error('Network Error'))
      const w = await mountView([transaction('t1')])

      await leaveLater(w)
      await discardNow(w)

      expect(mockPush.mock.calls).toEqual([['/importar'], ['/importar']])
    })
  })

  describe('conclusão', () => {
    beforeEach(() => {
      mockComplete.mockReset().mockResolvedValue({ importId: 'i1', status: 'COMPLETED', affectedTransactions: 3 })
      mockGetOnboarding.mockReset().mockResolvedValue({ currentStep: 8, stepsCompleted: [], completed: true })
      mockCompleteOnboardingImport.mockReset().mockResolvedValue({ currentStep: 7 })
    })

    it('sempre pede confirmação e informa o que segue sem revisão e sem categoria', async () => {
      const w = await mountView([
        transaction('t1'),
        transaction('t2', { category: null, confidence: null }),
        transaction('t3', { reviewStatus: 'APPROVED' }),
        transaction('t4', { kind: 'CARD_PAYMENT', category: null, confidence: null }),
      ])

      await openComplete(w)

      expect(mockComplete).not.toHaveBeenCalled()
      expect(inDialog('complete-dialog')?.textContent).toContain('não dá para desfazer')
      expect(inDialog('complete-unreviewed')?.textContent).toContain('2 transações seguem sem revisão')
      expect(inDialog('complete-uncategorized')?.textContent).toContain('1 transação segue sem categoria')
    })

    it('em importação de conta explica que só o que vem depois do último ajuste altera o saldo', async () => {
      const w = await mountView([transaction('t1')])

      await openComplete(w)

      expect(inDialog('complete-dialog')?.textContent).toContain(
        'Só as transações posteriores ao último ajuste do saldo alteram o saldo da conta',
      )
    })

    it('em importação de cartão explica que nenhum saldo de conta muda', async () => {
      const w = await mountView([transaction('t1')], importRow({ accountId: null, creditCardId: 'k1' }))

      await openComplete(w)

      expect(inDialog('complete-dialog')?.textContent).toContain('não alteram o saldo de nenhuma conta')
      expect(inDialog('complete-dialog')?.textContent).toContain('não dá para desfazer')
    })

    it('com o POST em andamento o diálogo não fecha', async () => {
      const pending = deferred<unknown>()
      mockComplete.mockReturnValue(pending.promise)
      const w = await mountView([transaction('t1')])
      await openComplete(w)

      await confirmComplete()
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
      await flushPromises()

      expect(inDialog('complete-dialog')).not.toBeNull()
      expect(inDialog<HTMLButtonElement>('complete-cancel')?.disabled).toBe(true)

      pending.resolve({ importId: 'i1', status: 'COMPLETED', affectedTransactions: 1 })
      await flushPromises()
      expect(inDialog('complete-dialog')).toBeNull()
    })

    it('cancelar não conclui', async () => {
      const w = await mountView([transaction('t1')])
      await openComplete(w)

      inDialog('complete-cancel')!.click()
      await flushPromises()

      expect(mockComplete).not.toHaveBeenCalled()
      expect(inDialog('complete-dialog')).toBeNull()
    })

    it('terminada, volta para a /importar', async () => {
      const w = await mountView([transaction('t1')])
      await openComplete(w)

      await confirmComplete()

      expect(mockComplete).toHaveBeenCalledExactlyOnceWith('i1')
      expect(mockCompleteOnboardingImport).not.toHaveBeenCalled()
      expect(mockPush).toHaveBeenCalledExactlyOnceWith('/importar')
    })

    it('no passo 5 do onboarding não faz o POST e volta para a /importar', async () => {
      mockGetOnboarding.mockResolvedValue({ currentStep: 5, stepsCompleted: [], completed: false })
      const w = await mountView([transaction('t1')])
      await openComplete(w)

      await confirmComplete()

      expect(mockCompleteOnboardingImport).not.toHaveBeenCalled()
      expect(mockPush).toHaveBeenCalledExactlyOnceWith('/importar')
    })

    it('no passo 6 do onboarding faz o POST e vai para /onboarding', async () => {
      mockGetOnboarding.mockResolvedValue({ currentStep: 6, stepsCompleted: [], completed: false })
      const w = await mountView([transaction('t1')])
      await openComplete(w)

      await confirmComplete()

      expect(mockCompleteOnboardingImport).toHaveBeenCalledExactlyOnceWith('i1')
      expect(mockPush).toHaveBeenCalledExactlyOnceWith('/onboarding')
    })

    it('falha no POST do onboarding não prende o usuário: o passo 6 mostra "Continuar"', async () => {
      mockGetOnboarding.mockResolvedValue({ currentStep: 6, stepsCompleted: [], completed: false })
      mockCompleteOnboardingImport.mockRejectedValue(new Error('Network Error'))
      const w = await mountView([transaction('t1')])
      await openComplete(w)

      await confirmComplete()

      expect(mockPush).toHaveBeenCalledExactlyOnceWith('/onboarding')
      expect(w.find('[data-testid="review-action-error"]').exists()).toBe(false)
    })

    it('sem conseguir ler o onboarding, volta para a /importar', async () => {
      mockGetOnboarding.mockRejectedValue(new Error('Network Error'))
      const w = await mountView([transaction('t1')])
      await openComplete(w)

      await confirmComplete()

      expect(mockPush).toHaveBeenCalledExactlyOnceWith('/importar')
    })

    it('400 mostra a mensagem e a revisão continua disponível', async () => {
      mockComplete.mockRejectedValue({
        response: { status: 400, data: { detail: 'Concluir esta importação deixaria o saldo da conta fora do limite' } },
      })
      const w = await mountView([transaction('t1')])
      await openComplete(w)

      await confirmComplete()

      expect(w.get('[data-testid="review-action-error"]').text()).toContain('fora do limite')
      expect(w.find('[data-testid="complete-import"]').exists()).toBe(true)
      expect(inDialog('complete-dialog')).toBeNull()
      expect(mockPush).not.toHaveBeenCalled()
    })

    it('409 de destino removido mostra a mensagem com a importação ainda em REVIEW', async () => {
      mockComplete.mockRejectedValue({
        response: { status: 409, data: { detail: 'A conta desta importação foi removida: restaure ou descarte a importação' } },
      })
      const w = await mountView([transaction('t1')])
      await openComplete(w)

      await confirmComplete()

      expect(w.get('[data-testid="review-action-error"]').text()).toContain('restaure ou descarte a importação')
      expect(w.find('[data-testid="discard"]').exists()).toBe(true)
      expect(mockPush).not.toHaveBeenCalled()
    })

    it('duplo clique conclui uma vez só', async () => {
      const w = await mountView([transaction('t1')])
      await openComplete(w)

      inDialog('complete-confirm')!.click()
      inDialog('complete-confirm')!.click()
      await flushPromises()

      expect(mockComplete).toHaveBeenCalledOnce()
    })
  })
})
