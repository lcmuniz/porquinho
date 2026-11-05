import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'
import ImportView from '../ImportView.vue'
import type { Account } from '@/types/account'
import type { CreditCard } from '@/types/creditCard'
import type { TransactionImport } from '@/types/transactionImport'

const mockListAccounts = vi.fn()
const mockListCards = vi.fn()
const mockListImports = vi.fn()
const mockUpload = vi.fn()
const mockGetImport = vi.fn()

vi.mock('@/services/accountService', () => ({
  listAccounts: (...a: unknown[]) => mockListAccounts(...a),
}))
vi.mock('@/services/cardService', () => ({
  listCards: (...a: unknown[]) => mockListCards(...a),
}))
vi.mock('@/services/transactionImportService', () => ({
  listImports: (...a: unknown[]) => mockListImports(...a),
  uploadImport: (...a: unknown[]) => mockUpload(...a),
  getImport: (...a: unknown[]) => mockGetImport(...a),
}))

function account(overrides: Partial<Account> = {}): Account {
  return {
    id: 'a1',
    accountName: 'Nubank',
    bankName: 'Nubank',
    accountType: 'CHECKING',
    initialBalance: 100,
    currentBalance: 100,
    createdAt: '2026-11-01T12:00:00Z',
    lastReconciliationDate: null,
    version: 0,
    ...overrides,
  }
}

function card(overrides: Partial<CreditCard> = {}): CreditCard {
  return {
    id: 'c1',
    cardName: 'Itaú Visa',
    bankName: 'Itaú',
    cardLimit: 5000,
    dueDay: 10,
    createdAt: '2026-11-01T12:00:00Z',
    ...overrides,
  }
}

function imported(overrides: Partial<TransactionImport> = {}): TransactionImport {
  return {
    id: 'i1',
    accountId: 'a1',
    creditCardId: null,
    targetName: 'Nubank',
    fileName: 'nubank-conta.ofx',
    fileType: 'OFX',
    bankName: null,
    fileSizeKb: 4,
    status: 'PROCESSING',
    totalTransactions: 0,
    importedTransactions: 0,
    duplicatesFound: 0,
    skippedRows: 0,
    errorMessage: null,
    createdAt: '2026-11-04T15:00:00Z',
    ...overrides,
  }
}

function ofxFile(name = 'nubank-conta.ofx', size = 3600): File {
  const file = new File(['OFXHEADER:100'], name, { type: 'application/x-ofx' })
  Object.defineProperty(file, 'size', { value: size })
  return file
}

async function mountView() {
  const wrapper = mount(ImportView, {
    attachTo: document.body,
    global: { stubs: { RouterLink: RouterLinkStub } },
  })
  await flushPromises()
  return wrapper
}

type Wrapper = Awaited<ReturnType<typeof mountView>>

async function chooseFile(wrapper: Wrapper, file: File) {
  const input = wrapper.get('[data-testid="file-input"]')
  Object.defineProperty(input.element, 'files', { value: [file], configurable: true })
  await input.trigger('change')
  await flushPromises()
}

async function submit(wrapper: Wrapper) {
  await wrapper.get('[data-testid="import-form"]').trigger('submit')
  await flushPromises()
}

describe('ImportView', () => {
  beforeEach(() => {
    mockListAccounts.mockReset().mockResolvedValue([account()])
    mockListCards.mockReset().mockResolvedValue([card()])
    mockListImports.mockReset().mockResolvedValue([])
    mockUpload.mockReset()
    mockGetImport.mockReset().mockImplementation(async (id: string) => imported({ id }))
  })

  afterEach(() => {
    vi.useRealTimers()
    document.body.innerHTML = ''
  })

  it('agrupa contas e cartões no seletor de destino', async () => {
    const wrapper = await mountView()

    const groups = wrapper.findAll('optgroup')
    expect(groups.map((g) => g.attributes('label'))).toEqual(['Contas', 'Cartões de crédito'])
    expect(groups[0]!.find('option').attributes('value')).toBe('account:a1')
    expect(groups[1]!.find('option').attributes('value')).toBe('card:c1')
  })

  it('sem conta nem cartão, mostra os links de cadastro e nenhum formulário', async () => {
    mockListAccounts.mockResolvedValue([])
    mockListCards.mockResolvedValue([])
    const wrapper = await mountView()

    expect(wrapper.find('[data-testid="no-targets"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="import-form"]').exists()).toBe(false)
    const links = wrapper.findAllComponents(RouterLinkStub).map((l) => l.props('to'))
    expect(links).toContain('/contas')
    expect(links).toContain('/cartoes')
  })

  it('envia o arquivo para a conta escolhida, mostra "Arquivo enviado, processando..." e a importação na lista', async () => {
    mockUpload.mockImplementation(async (_file: File, _target: unknown, onProgress?: (p: number) => void) => {
      onProgress?.(100)
      return imported()
    })
    const wrapper = await mountView()
    await wrapper.get('[data-testid="import-target-select"]').setValue('account:a1')
    const file = ofxFile()
    await chooseFile(wrapper, file)
    await submit(wrapper)

    expect(mockUpload).toHaveBeenCalledWith(file, { kind: 'account', id: 'a1' }, expect.any(Function))
    expect(wrapper.get('[data-testid="success-message"]').text()).toBe('Arquivo enviado, processando...')
    const item = wrapper.get('[data-testid="import-i1"]')
    expect(item.text()).toContain('nubank-conta.ofx')
    expect(item.text()).toContain('Conta: Nubank')
    expect(item.get('[data-testid="import-status"]').text()).toBe('Recebido — identificando o banco...')
  })

  it('envia para o cartão com o destino de cartão', async () => {
    mockUpload.mockResolvedValue(imported({ id: 'i2', accountId: null, creditCardId: 'c1', targetName: 'Itaú Visa' }))
    const wrapper = await mountView()
    await wrapper.get('[data-testid="import-target-select"]').setValue('card:c1')
    await chooseFile(wrapper, ofxFile('itau-cartao.ofx'))
    await submit(wrapper)

    expect(mockUpload).toHaveBeenCalledWith(expect.any(File), { kind: 'card', id: 'c1' }, expect.any(Function))
    expect(wrapper.get('[data-testid="import-i2"]').text()).toContain('Cartão: Itaú Visa')
  })

  it('mostra a barra de progresso durante o envio', async () => {
    let resolve: (value: TransactionImport) => void = () => {}
    mockUpload.mockImplementation((_f: File, _t: unknown, onProgress?: (p: number) => void) => {
      onProgress?.(40)
      return new Promise<TransactionImport>((r) => {
        resolve = r
      })
    })
    const wrapper = await mountView()
    await wrapper.get('[data-testid="import-target-select"]').setValue('account:a1')
    await chooseFile(wrapper, ofxFile())
    await submit(wrapper)

    const bar = wrapper.get('[data-testid="upload-progress"] [role="progressbar"]')
    expect(bar.attributes('aria-valuenow')).toBe('40')
    expect(wrapper.get('[data-testid="submit-import"]').attributes('disabled')).toBeDefined()

    resolve(imported())
    await flushPromises()
    expect(wrapper.find('[data-testid="upload-progress"]').exists()).toBe(false)
  })

  it('barra arquivo acima de 10MB antes de enviar', async () => {
    const wrapper = await mountView()
    await wrapper.get('[data-testid="import-target-select"]').setValue('account:a1')
    await chooseFile(wrapper, ofxFile('grande.ofx', 10 * 1024 * 1024 + 1))

    expect(wrapper.get('[data-testid="form-error"]').text()).toBe('Arquivo muito grande (máximo 10MB)')
    await submit(wrapper)
    expect(mockUpload).not.toHaveBeenCalled()
  })

  it('barra extensão diferente de .ofx antes de enviar', async () => {
    const wrapper = await mountView()
    await wrapper.get('[data-testid="import-target-select"]').setValue('account:a1')
    await chooseFile(wrapper, ofxFile('extrato.pdf'))

    expect(wrapper.get('[data-testid="form-error"]').text()).toBe('Formato de arquivo não suportado')
    await submit(wrapper)
    expect(mockUpload).not.toHaveBeenCalled()
  })

  it('aceita o arquivo solto na área de arrastar', async () => {
    mockUpload.mockResolvedValue(imported())
    const wrapper = await mountView()
    await wrapper.get('[data-testid="import-target-select"]').setValue('account:a1')
    const file = ofxFile()
    await wrapper.get('[data-testid="drop-zone"]').trigger('drop', { dataTransfer: { files: [file] } })
    await flushPromises()

    expect(wrapper.get('[data-testid="selected-file"]').text()).toContain('nubank-conta.ofx')
    await submit(wrapper)
    expect(mockUpload).toHaveBeenCalledWith(file, { kind: 'account', id: 'a1' }, expect.any(Function))
  })

  it('exige destino e arquivo', async () => {
    const wrapper = await mountView()
    await submit(wrapper)
    expect(wrapper.get('[data-testid="form-error"]').text()).toBe('Escolha a conta ou o cartão de destino')

    await wrapper.get('[data-testid="import-target-select"]').setValue('account:a1')
    await submit(wrapper)
    expect(wrapper.get('[data-testid="form-error"]').text()).toBe('Escolha o arquivo do extrato')
    expect(mockUpload).not.toHaveBeenCalled()
  })

  it('mostra o erro do backend (extrato de cartão para conta)', async () => {
    mockUpload.mockRejectedValue({
      response: {
        status: 400,
        data: { detail: 'Este arquivo é de cartão de crédito. Escolha um cartão como destino.' },
      },
    })
    const wrapper = await mountView()
    await wrapper.get('[data-testid="import-target-select"]').setValue('account:a1')
    await chooseFile(wrapper, ofxFile('itau-cartao.ofx'))
    await submit(wrapper)

    expect(wrapper.get('[data-testid="form-error"]').text()).toBe(
      'Este arquivo é de cartão de crédito. Escolha um cartão como destino.',
    )
    expect(wrapper.find('[data-testid="success-message"]').exists()).toBe(false)
  })

  it('usa mensagem genérica quando o backend não traz detalhe', async () => {
    mockUpload.mockRejectedValue(new Error('Network Error'))
    const wrapper = await mountView()
    await wrapper.get('[data-testid="import-target-select"]').setValue('account:a1')
    await chooseFile(wrapper, ofxFile())
    await submit(wrapper)

    expect(wrapper.get('[data-testid="form-error"]').text()).toBe('Não foi possível enviar o arquivo. Tente novamente.')
  })

  it('lista as importações recentes com status', async () => {
    mockListImports.mockResolvedValue([
      imported(),
      imported({ id: 'i3', fileName: 'velho.ofx', status: 'COMPLETED' }),
    ])
    const wrapper = await mountView()

    expect(wrapper.findAll('[data-testid="import-status"]').map((s) => s.text())).toEqual([
      'Recebido — identificando o banco...',
      'Concluída',
    ])
  })

  it('sem importações mostra o estado vazio', async () => {
    const wrapper = await mountView()
    expect(wrapper.get('[data-testid="no-imports"]').text()).toBe('Nenhum extrato enviado ainda.')
  })

  it('erro ao carregar mostra mensagem e tenta de novo', async () => {
    mockListAccounts.mockRejectedValueOnce(new Error('falhou'))
    const wrapper = await mountView()
    expect(wrapper.get('[data-testid="load-error"]').text()).toContain('Não foi possível carregar')

    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="import-form"]').exists()).toBe(true)
  })

  it('importação pronta para revisão mostra as contagens (Story 5.4)', async () => {
    mockListImports.mockResolvedValue([
      imported({ status: 'REVIEW', bankName: 'NUBANK', totalTransactions: 10, importedTransactions: 10, skippedRows: 0 }),
      imported({ id: 'i2', status: 'REVIEW', bankName: 'ITAU', totalTransactions: 6, importedTransactions: 2, skippedRows: 4 }),
      imported({ id: 'i3', status: 'REVIEW', bankName: 'ITAU', totalTransactions: 2, importedTransactions: 1, skippedRows: 1 }),
    ])
    const wrapper = await mountView()

    expect(wrapper.findAll('[data-testid="import-status"]').map((s) => s.text())).toEqual([
      'Pronta para revisão: 10 transações — a tela de revisão chega numa próxima etapa',
      'Pronta para revisão: 2 transações (4 linhas com erro) — a tela de revisão chega numa próxima etapa',
      'Pronta para revisão: 1 transação (1 linha com erro) — a tela de revisão chega numa próxima etapa',
    ])
  })

  describe('consulta de status (Stories 5.3 e 5.4)', () => {
    beforeEach(() => {
      vi.useFakeTimers()
    })

    async function tick() {
      await vi.advanceTimersByTimeAsync(2000)
      await flushPromises()
    }

    it('após o envio consulta a cada 2 s, mostra o banco, depois as contagens, e para de consultar', async () => {
      mockUpload.mockResolvedValue(imported())
      mockGetImport
        .mockResolvedValueOnce(imported())
        .mockResolvedValueOnce(imported({ bankName: 'NUBANK' }))
        .mockResolvedValueOnce(imported({ bankName: 'NUBANK', status: 'REVIEW', totalTransactions: 10, importedTransactions: 10 }))
      const wrapper = await mountView()
      await wrapper.get('[data-testid="import-target-select"]').setValue('account:a1')
      await chooseFile(wrapper, ofxFile())
      await submit(wrapper)

      expect(mockGetImport).not.toHaveBeenCalled()
      await tick()
      expect(mockGetImport).toHaveBeenCalledTimes(1)
      expect(mockGetImport).toHaveBeenCalledWith('i1')
      await tick()
      expect(mockGetImport).toHaveBeenCalledTimes(2)
      expect(wrapper.get('[data-testid="import-i1"] [data-testid="import-status"]').text()).toBe(
        'Banco detectado: Nubank — lendo as transações...',
      )

      // com o banco detectado a consulta continua até a importação sair de PROCESSING (decisão 2A)
      await tick()
      expect(mockGetImport).toHaveBeenCalledTimes(3)
      expect(wrapper.get('[data-testid="import-i1"] [data-testid="import-status"]').text()).toBe(
        'Pronta para revisão: 10 transações — a tela de revisão chega numa próxima etapa',
      )

      await tick()
      await tick()
      expect(mockGetImport).toHaveBeenCalledTimes(3)
    })

    it('para de consultar quando a importação falha e mostra a mensagem', async () => {
      mockListImports.mockResolvedValue([imported()])
      mockGetImport.mockResolvedValue(
        imported({
          status: 'FAILED',
          errorMessage: 'O arquivo está incompleto. Exporte o extrato de novo no site ou app do banco e envie outra vez.',
        }),
      )
      const wrapper = await mountView()

      await tick()
      expect(wrapper.get('[data-testid="import-status"]').text()).toBe('Falhou')
      expect(wrapper.get('[data-testid="import-error"]').text()).toBe(
        'O arquivo está incompleto. Exporte o extrato de novo no site ou app do banco e envie outra vez.',
      )
      await tick()
      expect(mockGetImport).toHaveBeenCalledTimes(1)
    })

    it('para de consultar ao sair da página', async () => {
      mockListImports.mockResolvedValue([imported()])
      const wrapper = await mountView()

      await tick()
      expect(mockGetImport).toHaveBeenCalledTimes(1)
      wrapper.unmount()
      await tick()
      await tick()
      expect(mockGetImport).toHaveBeenCalledTimes(1)
    })

    it('não consulta quando nenhuma importação está em processamento', async () => {
      mockListImports.mockResolvedValue([
        imported({ status: 'REVIEW', bankName: 'ITAU', creditCardId: 'c1', accountId: null, totalTransactions: 8, importedTransactions: 8 }),
        imported({ id: 'i4', status: 'FAILED', errorMessage: 'O processamento foi interrompido. Envie o arquivo de novo.' }),
      ])
      const wrapper = await mountView()

      await tick()
      expect(mockGetImport).not.toHaveBeenCalled()
      expect(wrapper.findAll('[data-testid="import-status"]').map((s) => s.text())).toEqual([
        'Pronta para revisão: 8 transações — a tela de revisão chega numa próxima etapa',
        'Falhou',
      ])
      expect(wrapper.get('[data-testid="import-error"]').text()).toBe(
        'O processamento foi interrompido. Envie o arquivo de novo.',
      )
    })

    it('banco fora dos cinco aparece como "Banco não identificado" e a consulta continua', async () => {
      mockListImports.mockResolvedValue([imported({ bankName: 'OTHER' })])
      mockGetImport.mockResolvedValue(imported({ bankName: 'OTHER' }))
      const wrapper = await mountView()

      expect(wrapper.get('[data-testid="import-status"]').text()).toBe(
        'Banco não identificado — lendo as transações...',
      )
      await tick()
      await tick()
      expect(mockGetImport).toHaveBeenCalledTimes(2)
    })

    it('remove da lista a importação que deixou de existir (404)', async () => {
      mockListImports.mockResolvedValue([imported()])
      mockGetImport.mockRejectedValue({ response: { status: 404 } })
      const wrapper = await mountView()

      await tick()
      expect(wrapper.find('[data-testid="import-i1"]').exists()).toBe(false)
      await tick()
      expect(mockGetImport).toHaveBeenCalledTimes(1)
    })
  })
})
