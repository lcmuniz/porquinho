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
  RouterLink: { props: ['to'], template: '<a><slot /></a>' },
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

    expect(wrapper.find('[aria-label="Carregando status da exportação"]').exists()).toBe(true)

    resolvePromise!(null)
    await flushPromises()
    expect(wrapper.find('[aria-label="Carregando status da exportação"]').exists()).toBe(false)
  })

  it('should render idle state when no previous export', async () => {
    mockGetExportStatus.mockResolvedValue(null)
    const wrapper = mount(DataExportView)
    await flushPromises()

    expect(wrapper.text()).toContain('Solicitar exportação')
    expect(wrapper.text()).toContain('Exportar meus dados')
  })

  it('should render idle state when previous export expired', async () => {
    mockGetExportStatus.mockResolvedValue(mockExportExpired)
    const wrapper = mount(DataExportView)
    await flushPromises()

    expect(wrapper.text()).toContain('Solicitar exportação')
    expect(wrapper.text()).toContain('Exportar meus dados')
  })

  it('should show confirm dialog when clicking export button', async () => {
    mockGetExportStatus.mockResolvedValue(null)
    const wrapper = mount(DataExportView, { attachTo: document.body })
    await flushPromises()

    await wrapper.find('[aria-label="Solicitar exportação de dados"]').trigger('click')
    await flushPromises()

    expect(document.body.textContent).toContain('Confirmar exportação de dados')
    wrapper.unmount()
  })

  it('should render processing state', async () => {
    mockGetExportStatus.mockResolvedValue(mockExportProcessing)
    const wrapper = mount(DataExportView)
    await flushPromises()

    expect(wrapper.text()).toContain('Exportação em andamento')
    expect(wrapper.text()).toContain('Seus dados estão sendo coletados')
  })

  it('should render ready state with download button', async () => {
    mockGetExportStatus.mockResolvedValue(mockExportReady)
    const wrapper = mount(DataExportView)
    await flushPromises()

    expect(wrapper.text()).toContain('Exportação pronta!')
    expect(wrapper.text()).toContain('Baixar arquivo')
  })

  it('should show error state when loading fails', async () => {
    mockGetExportStatus.mockRejectedValue(new Error('Network error'))
    const wrapper = mount(DataExportView)
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível carregar o status da exportação')
  })

  async function openDialogAndFill(wrapper: ReturnType<typeof mount>, pwd: string, confirm: string) {
    await wrapper.find('[aria-label="Solicitar exportação de dados"]').trigger('click')
    await flushPromises()
    const pwdInput = document.getElementById('export-password') as HTMLInputElement
    const confirmInput = document.getElementById('export-password-confirm') as HTMLInputElement
    pwdInput.value = pwd
    pwdInput.dispatchEvent(new Event('input'))
    confirmInput.value = confirm
    confirmInput.dispatchEvent(new Event('input'))
    await flushPromises()
  }

  function exportButton() {
    return Array.from(document.querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === 'Exportar',
    ) as HTMLButtonElement
  }

  it('should ask for confirmation before any request', async () => {
    mockGetExportStatus.mockResolvedValue(null)
    const wrapper = mount(DataExportView, { attachTo: document.body })
    await flushPromises()

    await wrapper.find('[aria-label="Solicitar exportação de dados"]').trigger('click')
    await flushPromises()

    expect(document.body.textContent).toContain('Isso vai exportar TODOS os seus dados. Continuar?')
    expect(document.body.textContent).toContain('7-Zip, WinRAR ou Keka')
    expect(mockRequestExport).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('should keep Exportar disabled with a short password', async () => {
    mockGetExportStatus.mockResolvedValue(null)
    const wrapper = mount(DataExportView, { attachTo: document.body })
    await flushPromises()

    await openDialogAndFill(wrapper, 'curta', 'curta')

    expect(exportButton().disabled).toBe(true)
    expect(document.body.textContent).toContain('A senha precisa ter pelo menos 8 caracteres.')
    wrapper.unmount()
  })

  it('should keep Exportar disabled when the confirmation differs', async () => {
    mockGetExportStatus.mockResolvedValue(null)
    const wrapper = mount(DataExportView, { attachTo: document.body })
    await flushPromises()

    await openDialogAndFill(wrapper, 'senha-forte-123', 'senha-forte-124')

    expect(exportButton().disabled).toBe(true)
    expect(document.body.textContent).toContain('As senhas não conferem.')
    wrapper.unmount()
  })

  it('should keep Exportar disabled with only spaces and mark the fields invalid', async () => {
    mockGetExportStatus.mockResolvedValue(null)
    const wrapper = mount(DataExportView, { attachTo: document.body })
    await flushPromises()

    await openDialogAndFill(wrapper, '          ', '          ')

    expect(exportButton().disabled).toBe(true)
    expect(document.getElementById('export-password')?.getAttribute('aria-invalid')).toBe('true')
    expect(document.getElementById('export-password-confirm')?.getAttribute('aria-invalid')).toBe('true')
    expect(document.getElementById('export-password')?.getAttribute('maxlength')).toBe('128')
    wrapper.unmount()
  })

  it('should clear both password fields when the dialog is cancelled', async () => {
    mockGetExportStatus.mockResolvedValue(null)
    const wrapper = mount(DataExportView, { attachTo: document.body })
    await flushPromises()

    await openDialogAndFill(wrapper, 'senha-forte-123', 'senha-forte-123')
    expect(exportButton().disabled).toBe(false)
    const cancel = Array.from(document.querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === 'Cancelar',
    ) as HTMLButtonElement
    cancel.click()
    await flushPromises()

    await wrapper.find('[aria-label="Solicitar exportação de dados"]').trigger('click')
    await flushPromises()
    expect((document.getElementById('export-password') as HTMLInputElement).value).toBe('')
    expect((document.getElementById('export-password-confirm') as HTMLInputElement).value).toBe('')
    expect(exportButton().disabled).toBe(true)
    expect(mockRequestExport).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('should open the dialog from Tentar de novo on the failed card', async () => {
    mockGetExportStatus.mockResolvedValue({ ...mockExportProcessing, status: 'FAILED' as const })
    const wrapper = mount(DataExportView, { attachTo: document.body })
    await flushPromises()

    const retry = wrapper.findAll('button').find((b) => b.text() === 'Tentar de novo')!
    await retry.trigger('click')
    await flushPromises()

    expect(document.body.textContent).toContain('Isso vai exportar TODOS os seus dados. Continuar?')
    expect(mockRequestExport).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('should send the password and show processing', async () => {
    mockGetExportStatus.mockResolvedValue(null)
    mockRequestExport.mockResolvedValue(mockExportProcessing)
    const wrapper = mount(DataExportView, { attachTo: document.body })
    await flushPromises()

    await openDialogAndFill(wrapper, 'senha-forte-123', 'senha-forte-123')
    expect(exportButton().disabled).toBe(false)
    exportButton().click()
    await flushPromises()

    expect(mockRequestExport).toHaveBeenCalledWith('senha-forte-123')
    expect(wrapper.text()).toContain('Exportação em andamento')
    wrapper.unmount()
  })

  it('should render failed state with a retry', async () => {
    mockGetExportStatus.mockResolvedValue({ ...mockExportProcessing, status: 'FAILED' as const })
    const wrapper = mount(DataExportView)
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível gerar o export. Tente de novo.')
    expect(wrapper.text()).toContain('Tentar de novo')
  })

  it('should show rate limit error when requesting too frequently', async () => {
    mockGetExportStatus.mockResolvedValue(null)
    mockRequestExport.mockRejectedValue({ response: { status: 429 } })
    const wrapper = mount(DataExportView, { attachTo: document.body })
    await flushPromises()

    await openDialogAndFill(wrapper, 'senha-forte-123', 'senha-forte-123')
    exportButton().click()
    await flushPromises()

    expect(wrapper.text()).toContain('Limite de exportação atingido')
    wrapper.unmount()
  })
})
