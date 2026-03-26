import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import DataExportView from '../DataExportView.vue'

// Mock the data export service
const mockRequestExport = vi.fn()
const mockGetExportStatus = vi.fn()

vi.mock('@/services/dataExportService', () => ({
  requestExport: (...args: unknown[]) => mockRequestExport(...args),
  getExportStatus: (...args: unknown[]) => mockGetExportStatus(...args),
  getDownloadUrl: (token: string) => `/api/v1/data-export/download/${token}`,
}))

// Mock vue-router
vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: vi.fn(),
  }),
}))

// Mock api
vi.mock('@/services/api', () => ({
  api: {
    defaults: { baseURL: 'http://localhost:8080' },
  },
}))

const mockExportProcessing = {
  id: '11111111-1111-1111-1111-111111111111',
  status: 'PROCESSING' as const,
  createdAt: '2026-03-26T10:00:00.000Z',
  expiresAt: '2026-03-27T10:00:00.000Z',
  downloadUrl: null,
}

const mockExportReady = {
  id: '11111111-1111-1111-1111-111111111111',
  status: 'READY' as const,
  createdAt: '2026-03-26T10:00:00.000Z',
  expiresAt: '2026-03-27T10:00:00.000Z',
  downloadUrl: '/api/v1/data-export/download/22222222-2222-2222-2222-222222222222',
}

const mockExportExpired = {
  id: '11111111-1111-1111-1111-111111111111',
  status: 'EXPIRED' as const,
  createdAt: '2026-03-25T10:00:00.000Z',
  expiresAt: '2026-03-26T10:00:00.000Z',
  downloadUrl: null,
}

describe('DataExportView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('should render loading state while fetching status', async () => {
    let resolvePromise: (value: unknown) => void
    mockGetExportStatus.mockReturnValue(new Promise((resolve) => { resolvePromise = resolve }))
    const wrapper = mount(DataExportView)
    await wrapper.vm.$nextTick()

    expect(wrapper.find('[aria-label="Carregando status da exportacao"]').exists()).toBe(true)

    resolvePromise!(null)
    await flushPromises()
    expect(wrapper.find('[aria-label="Carregando status da exportacao"]').exists()).toBe(false)
  })

  it('should render idle state when no previous export', async () => {
    mockGetExportStatus.mockResolvedValue(null)
    const wrapper = mount(DataExportView)
    await flushPromises()

    expect(wrapper.text()).toContain('Solicitar exportacao')
    expect(wrapper.text()).toContain('Exportar Meus Dados')
  })

  it('should render idle state when previous export expired', async () => {
    mockGetExportStatus.mockResolvedValue(mockExportExpired)
    const wrapper = mount(DataExportView)
    await flushPromises()

    expect(wrapper.text()).toContain('Solicitar exportacao')
    expect(wrapper.text()).toContain('Exportar Meus Dados')
  })

  it('should show confirm dialog when clicking export button', async () => {
    mockGetExportStatus.mockResolvedValue(null)
    const wrapper = mount(DataExportView, { attachTo: document.body })
    await flushPromises()

    await wrapper.find('[aria-label="Solicitar exportacao de dados"]').trigger('click')
    await flushPromises()

    expect(document.body.textContent).toContain('Confirmar exportacao de dados')
    wrapper.unmount()
  })

  it('should render processing state', async () => {
    mockGetExportStatus.mockResolvedValue(mockExportProcessing)
    const wrapper = mount(DataExportView)
    await flushPromises()

    expect(wrapper.text()).toContain('Exportacao em andamento')
    expect(wrapper.text()).toContain('Seus dados estao sendo coletados')
  })

  it('should render ready state with download button', async () => {
    mockGetExportStatus.mockResolvedValue(mockExportReady)
    const wrapper = mount(DataExportView)
    await flushPromises()

    expect(wrapper.text()).toContain('Exportacao pronta!')
    expect(wrapper.text()).toContain('Baixar arquivo')
  })

  it('should show error state when loading fails', async () => {
    mockGetExportStatus.mockRejectedValue(new Error('Network error'))
    const wrapper = mount(DataExportView)
    await flushPromises()

    expect(wrapper.text()).toContain('Nao foi possivel carregar o status da exportacao')
  })

  it('should show rate limit error when requesting too frequently', async () => {
    mockGetExportStatus.mockResolvedValue(null)
    mockRequestExport.mockRejectedValue({ response: { status: 429 } })
    const wrapper = mount(DataExportView, { attachTo: document.body })
    await flushPromises()

    // Open dialog
    await wrapper.find('[aria-label="Solicitar exportacao de dados"]').trigger('click')
    await flushPromises()

    // Click confirm in the teleported dialog
    const allButtons = document.querySelectorAll('button')
    const confirmBtn = Array.from(allButtons).find((b) => b.textContent?.trim() === 'Confirmar')
    confirmBtn?.click()
    await flushPromises()

    expect(wrapper.text()).toContain('Limite de exportacao atingido')
    wrapper.unmount()
  })
})
