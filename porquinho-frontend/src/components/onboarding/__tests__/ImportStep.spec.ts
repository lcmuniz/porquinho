import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'
import ImportStep from '../ImportStep.vue'
import type { TransactionImport } from '@/types/transactionImport'

const mockCompleteImport = vi.fn()
const mockListAccounts = vi.fn()
const mockListCards = vi.fn()
const mockListImports = vi.fn()
const mockGetImport = vi.fn()

vi.mock('@/services/onboardingService', () => ({
  completeImport: (...a: unknown[]) => mockCompleteImport(...a),
}))
vi.mock('@/services/accountService', () => ({
  listAccounts: (...a: unknown[]) => mockListAccounts(...a),
}))
vi.mock('@/services/cardService', () => ({
  listCards: (...a: unknown[]) => mockListCards(...a),
}))
vi.mock('@/services/transactionImportService', () => ({
  listImports: (...a: unknown[]) => mockListImports(...a),
  getImport: (...a: unknown[]) => mockGetImport(...a),
  uploadImport: vi.fn(),
}))

function imported(overrides: Partial<TransactionImport> = {}): TransactionImport {
  return {
    id: 'i1',
    accountId: 'a1',
    creditCardId: null,
    targetName: 'Nubank',
    fileName: 'nubank-conta.ofx',
    fileType: 'OFX',
    bankName: 'NUBANK',
    fileSizeKb: 4,
    status: 'COMPLETED',
    totalTransactions: 2,
    importedTransactions: 2,
    duplicatesFound: 0,
    transfersFound: 0,
    skippedRows: 0,
    classificationTotal: 2,
    classifiedCount: 2,
    errorMessage: null,
    createdAt: '2026-11-04T15:00:00Z',
    ...overrides,
  }
}

const ACCOUNT = { id: 'a1', accountName: 'Nubank' }

const nextProgress = {
  currentStep: 7,
  stepsCompleted: [
    'tutorial_viewed',
    'accounts_added',
    'cards_added',
    'categories_suggested',
    'categories_customized',
    'import_skipped',
  ],
  completed: false,
}

const BANKS = ['Nubank', 'Banco do Brasil', 'Itaú', 'Bradesco', 'Caixa']

async function mountStep() {
  const wrapper = mount(ImportStep, {
    attachTo: document.body,
    global: { stubs: { RouterLink: RouterLinkStub } },
  })
  await flushPromises()
  return wrapper
}

describe('ImportStep', () => {
  beforeEach(() => {
    mockCompleteImport.mockReset().mockResolvedValue(nextProgress)
    mockListAccounts.mockReset().mockResolvedValue([])
    mockListCards.mockReset().mockResolvedValue([])
    mockListImports.mockReset().mockResolvedValue([])
    mockGetImport.mockReset()
  })

  afterEach(() => {
    vi.useRealTimers()
    document.body.innerHTML = ''
  })

  it('mostra o título e o texto do extrato, sem o aviso de "em breve", e foca o título', async () => {
    const wrapper = await mountStep()

    const heading = wrapper.get('h1')
    expect(heading.text()).toBe('Importe suas transações')
    expect(document.activeElement).toBe(heading.element)
    expect(wrapper.text()).toContain(
      'Baixe o extrato do seu banco em formato OFX ou CSV e faça upload aqui',
    )
    expect(wrapper.find('[data-testid="import-coming-soon"]').exists()).toBe(false)
  })

  it('com conta cadastrada mostra o mesmo envio da /importar', async () => {
    mockListAccounts.mockResolvedValue([ACCOUNT])
    const wrapper = await mountStep()

    expect(wrapper.find('[data-testid="import-form"]').exists()).toBe(true)
    expect(wrapper.find('input[type="file"]').exists()).toBe(true)
  })

  it('sem conta nem cartão avisa e mantém "Importar depois"', async () => {
    const wrapper = await mountStep()

    expect(wrapper.find('[data-testid="no-targets"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="import-form"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="import-later-button"]').exists()).toBe(true)
  })

  it('sem importação concluída não mostra "Continuar"', async () => {
    mockListImports.mockResolvedValue([imported({ status: 'REVIEW' })])
    const wrapper = await mountStep()

    expect(wrapper.find('[data-testid="import-continue-button"]').exists()).toBe(false)
  })

  it('com importação concluída "Continuar" envia o id e "Importar depois" continua', async () => {
    mockListAccounts.mockResolvedValue([ACCOUNT])
    mockListImports.mockResolvedValue([imported({ id: 'i2', status: 'REVIEW' }), imported()])
    const wrapper = await mountStep()

    expect(wrapper.find('[data-testid="import-later-button"]').exists()).toBe(true)
    await wrapper.get('[data-testid="import-continue-button"]').trigger('click')
    await flushPromises()

    expect(mockCompleteImport).toHaveBeenCalledExactlyOnceWith('i1')
    expect(wrapper.emitted('done')).toEqual([[nextProgress]])
  })

  it('"Continuar" aparece quando a consulta encontra a importação sem nada novo (5.5, decisão 1A)', async () => {
    vi.useFakeTimers()
    mockListAccounts.mockResolvedValue([ACCOUNT])
    mockListImports.mockResolvedValue([imported({ status: 'PROCESSING' })])
    mockGetImport.mockResolvedValue(imported({ importedTransactions: 0, duplicatesFound: 2 }))
    const wrapper = await mountStep()
    expect(wrapper.find('[data-testid="import-continue-button"]').exists()).toBe(false)

    await vi.advanceTimersByTimeAsync(2000)
    await flushPromises()

    expect(wrapper.find('[data-testid="import-continue-button"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('erro em "Continuar" emite a mensagem do servidor e libera os botões', async () => {
    mockCompleteImport.mockRejectedValueOnce({
      response: { status: 409, data: { detail: 'Conclua a importação antes de seguir para o próximo passo.' } },
    })
    mockListImports.mockResolvedValue([imported()])
    const wrapper = await mountStep()

    await wrapper.get('[data-testid="import-continue-button"]').trigger('click')
    await flushPromises()

    expect(wrapper.emitted('error')?.[0]?.[0]).toContain('Conclua a importação')
    expect(wrapper.get('[data-testid="import-continue-button"]').attributes('disabled')).toBeUndefined()
  })

  it('lista os 5 bancos em seções expansíveis nativas, fechadas, com passos e sem links externos', async () => {
    const wrapper = await mountStep()

    const details = wrapper.findAll('details')
    expect(details).toHaveLength(5)
    expect(details.map((d) => d.get('summary').text())).toEqual(BANKS)
    for (const d of details) {
      expect((d.element as HTMLDetailsElement).open).toBe(false)
      expect(d.findAll('ol li').length).toBeGreaterThan(0)
      expect(d.text()).toMatch(/OFX ou CSV/)
    }
    for (const d of details) expect(d.findAll('a')).toHaveLength(0)
  })

  it('abre as instruções de um banco ao clicar no resumo', async () => {
    const wrapper = await mountStep()
    const nubank = wrapper.get<HTMLDetailsElement>('[data-testid="bank-guide-nubank"]')

    await nubank.get('summary').trigger('click')

    expect(nubank.element.open).toBe(true)
  })

  it('sem importação concluída tem os botões Voltar e "Importar depois", com alvo de toque de 44px', async () => {
    const wrapper = await mountStep()

    const buttons = wrapper.findAll('button')
    expect(buttons.map((b) => b.text())).toEqual(['Voltar', 'Importar depois'])
    for (const b of buttons) expect(b.classes()).toContain('min-h-[44px]')
  })

  it('"Importar depois" conclui o passo e emite done com o progresso', async () => {
    const wrapper = await mountStep()

    await wrapper.get('[data-testid="import-later-button"]').trigger('click')
    await flushPromises()

    expect(mockCompleteImport).toHaveBeenCalledExactlyOnceWith()
    expect(wrapper.emitted('done')).toEqual([[nextProgress]])
  })

  it('duplo clique em "Importar depois" salva só uma vez e mantém os botões desabilitados no sucesso', async () => {
    const wrapper = await mountStep()
    const button = wrapper.get('[data-testid="import-later-button"]')

    void button.trigger('click')
    void button.trigger('click')
    await flushPromises()

    expect(mockCompleteImport).toHaveBeenCalledOnce()
    expect(wrapper.emitted('done')).toEqual([[nextProgress]])
    expect(button.attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-testid="back-button"]').attributes('disabled')).toBeDefined()
  })

  it('falha da API emite o erro e libera os botões para tentar de novo', async () => {
    mockCompleteImport.mockRejectedValueOnce(new Error('Network Error'))
    const wrapper = await mountStep()

    await wrapper.get('[data-testid="import-later-button"]').trigger('click')
    await flushPromises()

    expect(wrapper.emitted('done')).toBeUndefined()
    expect(wrapper.emitted('error')?.[0]?.[0]).toContain('Não foi possível salvar seu progresso')
    const button = wrapper.get('[data-testid="import-later-button"]')
    expect(button.attributes('disabled')).toBeUndefined()

    await button.trigger('click')
    await flushPromises()
    expect(wrapper.emitted('done')).toEqual([[nextProgress]])
  })

  it('usa a mensagem do servidor quando houver (ex.: 409)', async () => {
    mockCompleteImport.mockRejectedValueOnce({
      response: {
        status: 409,
        data: { detail: 'Conclua os passos anteriores antes de importar suas transações.' },
      },
    })
    const wrapper = await mountStep()

    await wrapper.get('[data-testid="import-later-button"]').trigger('click')
    await flushPromises()

    expect(wrapper.emitted('error')).toEqual([
      ['Conclua os passos anteriores antes de importar suas transações.'],
    ])
  })

  it('detail vazio do servidor cai na mensagem genérica', async () => {
    mockCompleteImport.mockRejectedValueOnce({ response: { status: 500, data: { detail: '' } } })
    const wrapper = await mountStep()

    await wrapper.get('[data-testid="import-later-button"]').trigger('click')
    await flushPromises()

    expect(wrapper.emitted('error')?.[0]?.[0]).toContain('Não foi possível salvar seu progresso')
  })

  it('Voltar emite back sem salvar', async () => {
    const wrapper = await mountStep()

    await wrapper.get('[data-testid="back-button"]').trigger('click')

    expect(wrapper.emitted('back')).toHaveLength(1)
    expect(mockCompleteImport).not.toHaveBeenCalled()
  })
})
