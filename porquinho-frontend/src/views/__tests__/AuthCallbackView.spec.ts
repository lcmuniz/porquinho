import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import AuthCallbackView from '../AuthCallbackView.vue'

const mockPush = vi.fn()
const mockSetUser = vi.fn()
const mockEndSession = vi.fn()
const mockRegisterWithGoogle = vi.fn()

vi.mock('vue-router', () => ({ useRouter: () => ({ push: (...a: unknown[]) => mockPush(...a) }) }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ setUser: mockSetUser }) }))
vi.mock('@/services/session', () => ({ endSession: (...a: unknown[]) => mockEndSession(...a) }))
const SESSION = { access_token: 't', user: { id: 'u1', email: 'a@b.com', user_metadata: {} } }
const mockGetSession = vi.fn()
const mockSetSession = vi.fn()

vi.mock('@/composables/useAuth', () => ({
  useAuth: () => ({ getSession: () => mockGetSession() }),
}))
vi.mock('@/services/supabase', () => ({
  supabase: { auth: { setSession: (...a: unknown[]) => mockSetSession(...a) } },
}))
vi.mock('@/services/authService', () => ({
  authService: { registerWithGoogle: (...a: unknown[]) => mockRegisterWithGoogle(...a) },
}))

describe('AuthCallbackView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGetSession.mockResolvedValue(SESSION)
    window.location.hash = ''
  })

  it('sem sessão mas com tokens na URL: refaz a sessão com eles e segue', async () => {
    window.location.hash = '#access_token=at&refresh_token=rt'
    mockGetSession.mockResolvedValue(null)
    mockSetSession.mockResolvedValue({ data: { session: SESSION } })
    mockRegisterWithGoogle.mockResolvedValue({ id: 'u1' })
    mount(AuthCallbackView)
    await flushPromises()
    expect(mockSetSession).toHaveBeenCalledWith({ access_token: 'at', refresh_token: 'rt' })
    expect(mockPush).toHaveBeenCalledWith('/onboarding')
  })

  it('tokens na URL e a nova tentativa também falha: mensagem de conexão', async () => {
    window.location.hash = '#access_token=at&refresh_token=rt'
    mockGetSession.mockResolvedValue(null)
    mockSetSession.mockRejectedValue(new TypeError('Failed to fetch'))
    const wrapper = mount(AuthCallbackView)
    await flushPromises()
    expect(wrapper.text()).toContain('Verifique sua conexão e tente novamente.')
    expect(mockPush).not.toHaveBeenCalled()
  })

  it('sem sessão e sem tokens (cancelou no Google): nenhuma sessão encontrada', async () => {
    window.location.hash = '#error=access_denied'
    mockGetSession.mockResolvedValue(null)
    const wrapper = mount(AuthCallbackView)
    await flushPromises()
    expect(wrapper.text()).toContain('Nenhuma sessão encontrada.')
    expect(mockSetSession).not.toHaveBeenCalled()
  })

  it.each([
    [403, { title: 'Conta em exclusão', detail: 'Conta marcada para exclusão.' }],
    [409, { detail: 'Conta marcada para exclusão.' }],
  ])('conta em exclusão (%i): mostra o detail e encerra a sessão', async (status, data) => {
    mockRegisterWithGoogle.mockRejectedValue(Object.assign(new Error('Request failed with status code'), { response: { status, data } }))
    const wrapper = mount(AuthCallbackView)
    await flushPromises()
    expect(wrapper.text()).toContain('Conta marcada para exclusão.')
    expect(wrapper.text()).not.toContain('Request failed')
    expect(mockEndSession).toHaveBeenCalled()
    expect(mockSetUser).not.toHaveBeenCalled()
    expect(mockPush).not.toHaveBeenCalled()
  })

  it('409 de e-mail de outra conta: mostra o detail e encerra a sessão', async () => {
    mockRegisterWithGoogle.mockRejectedValue({ response: { status: 409, data: { detail: 'Este e-mail já está cadastrado em outra conta.' } } })
    const wrapper = mount(AuthCallbackView)
    await flushPromises()
    expect(wrapper.text()).toContain('Este e-mail já está cadastrado em outra conta.')
    expect(mockEndSession).toHaveBeenCalled()
    expect(mockSetUser).not.toHaveBeenCalled()
    expect(mockPush).not.toHaveBeenCalled()
  })

  it('sucesso: grava o usuário e segue para o onboarding', async () => {
    mockRegisterWithGoogle.mockResolvedValue({ id: 'u1' })
    mount(AuthCallbackView)
    await flushPromises()
    expect(mockSetUser).toHaveBeenCalledWith({ id: 'u1' })
    expect(mockPush).toHaveBeenCalledWith('/onboarding')
    expect(mockEndSession).not.toHaveBeenCalled()
  })
})
