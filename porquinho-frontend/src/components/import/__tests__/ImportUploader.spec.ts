import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'
import ImportUploader from '../ImportUploader.vue'
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
    transfersFound: 0,
    skippedRows: 0,
    classificationTotal: 0,
    classifiedCount: 0,
    errorMessage: null,
    createdAt: '2026-11-04T15:00:00Z',
    ...overrides,
  }
}

async function mountUploader() {
  const wrapper = mount(ImportUploader, { global: { stubs: { RouterLink: RouterLinkStub } } })
  await flushPromises()
  return wrapper
}

// O comportamento do envio está coberto em ImportView.spec.ts; aqui fica o que as duas telas consomem
describe('ImportUploader', () => {
  beforeEach(() => {
    mockListAccounts.mockReset().mockResolvedValue([{ id: 'a1', accountName: 'Nubank' }])
    mockListCards.mockReset().mockResolvedValue([])
    mockListImports.mockReset().mockResolvedValue([])
    mockUpload.mockReset()
    mockGetImport.mockReset()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('entrega a lista quando carrega e quando a consulta a atualiza', async () => {
    vi.useFakeTimers()
    const done = imported({ status: 'COMPLETED' })
    mockListImports.mockResolvedValue([imported()])
    mockGetImport.mockResolvedValue(done)
    const wrapper = await mountUploader()

    expect(wrapper.emitted('imports-change')).toEqual([[[imported()]]])

    await vi.advanceTimersByTimeAsync(2000)
    await flushPromises()

    const emitted = wrapper.emitted('imports-change') ?? []
    expect(emitted[emitted.length - 1]).toEqual([[done]])
    wrapper.unmount()
  })

  it('para a consulta ao sair da tela', async () => {
    vi.useFakeTimers()
    mockListImports.mockResolvedValue([imported()])
    mockGetImport.mockResolvedValue(imported())
    const wrapper = await mountUploader()

    wrapper.unmount()
    await vi.advanceTimersByTimeAsync(6000)

    expect(mockGetImport).not.toHaveBeenCalled()
  })
})
