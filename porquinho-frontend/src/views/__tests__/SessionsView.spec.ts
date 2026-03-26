import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import SessionsView from '../SessionsView.vue'

// Mock the session service
const mockGetSessions = vi.fn()
const mockRevokeSession = vi.fn()
const mockRevokeAllOtherSessions = vi.fn()

vi.mock('@/services/sessionService', () => ({
  getSessions: (...args: unknown[]) => mockGetSessions(...args),
  revokeSession: (...args: unknown[]) => mockRevokeSession(...args),
  revokeAllOtherSessions: (...args: unknown[]) => mockRevokeAllOtherSessions(...args),
}))

// Mock vue-router
vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: vi.fn(),
  }),
}))

const mockSessions = [
  {
    sessionId: '11111111-1111-1111-1111-111111111111',
    deviceInfo: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0',
    ipAddress: '192.168.1.1',
    createdAt: '2026-03-24T10:00:00.000Z',
    lastActivity: '2026-03-24T12:00:00.000Z',
    current: true,
  },
  {
    sessionId: '22222222-2222-2222-2222-222222222222',
    deviceInfo: 'Mozilla/5.0 (iPhone) AppleWebKit/605.1.15 Safari/605.1',
    ipAddress: '10.0.0.1',
    createdAt: '2026-03-23T08:00:00.000Z',
    lastActivity: '2026-03-23T18:00:00.000Z',
    current: false,
  },
]

describe('SessionsView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should render loading state while fetching', async () => {
    let resolvePromise: (value: unknown[]) => void
    mockGetSessions.mockReturnValue(new Promise((resolve) => { resolvePromise = resolve }))
    const wrapper = mount(SessionsView)
    await wrapper.vm.$nextTick()

    expect(wrapper.find('[aria-label="Carregando sessoes"]').exists()).toBe(true)

    // Resolve and verify loading disappears
    resolvePromise!(mockSessions)
    await flushPromises()
    expect(wrapper.find('[aria-label="Carregando sessoes"]').exists()).toBe(false)
  })

  it('should render sessions after loading', async () => {
    mockGetSessions.mockResolvedValue(mockSessions)
    const wrapper = mount(SessionsView)
    await flushPromises()

    expect(wrapper.text()).toContain('Google Chrome')
    expect(wrapper.text()).toContain('Safari')
    expect(wrapper.text()).toContain('192.168.1.1')
    expect(wrapper.text()).toContain('10.0.0.1')
  })

  it('should show "Dispositivo atual" badge for current session', async () => {
    mockGetSessions.mockResolvedValue(mockSessions)
    const wrapper = mount(SessionsView)
    await flushPromises()

    expect(wrapper.text()).toContain('Dispositivo atual')
  })

  it('should show "Encerrar sessao" button only for non-current sessions', async () => {
    mockGetSessions.mockResolvedValue(mockSessions)
    const wrapper = mount(SessionsView)
    await flushPromises()

    const revokeButtons = wrapper.findAll('button').filter((b) => b.text() === 'Encerrar sessao')
    expect(revokeButtons.length).toBe(1)
  })

  it('should show error state when loading fails', async () => {
    mockGetSessions.mockRejectedValue(new Error('Network error'))
    const wrapper = mount(SessionsView)
    await flushPromises()

    expect(wrapper.text()).toContain('Nao foi possivel carregar as sessoes')
  })

  it('should show empty state when no sessions', async () => {
    mockGetSessions.mockResolvedValue([])
    const wrapper = mount(SessionsView)
    await flushPromises()

    expect(wrapper.text()).toContain('Nenhuma sessao ativa encontrada')
  })

  it('should show "Encerrar todas as outras sessoes" button when other sessions exist', async () => {
    mockGetSessions.mockResolvedValue(mockSessions)
    const wrapper = mount(SessionsView)
    await flushPromises()

    expect(wrapper.text()).toContain('Encerrar todas as outras sessoes')
  })

  it('should not show "Encerrar todas as outras sessoes" button when only current session', async () => {
    mockGetSessions.mockResolvedValue([mockSessions[0]])
    const wrapper = mount(SessionsView)
    await flushPromises()

    const revokeAllButtons = wrapper
      .findAll('button')
      .filter((b) => b.text() === 'Encerrar todas as outras sessoes')
    expect(revokeAllButtons.length).toBe(0)
  })
})
