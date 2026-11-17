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
vi.mock('@/composables/useAuth', () => ({
  useAuth: () => ({
    getSession: async () => ({ access_token: 't', user: { id: 'u1', email: 'a@b.com', user_metadata: {} } }),
  }),
}))
vi.mock('@/services/authService', () => ({
  authService: { registerWithGoogle: (...a: unknown[]) => mockRegisterWithGoogle(...a) },
}))

describe('AuthCallbackView', () => {
  beforeEach(() => vi.clearAllMocks())

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
