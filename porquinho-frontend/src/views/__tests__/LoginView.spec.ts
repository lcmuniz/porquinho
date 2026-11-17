import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import LoginView from '../LoginView.vue'

const mockPush = vi.fn()
const mockSetUser = vi.fn()
const mockEndSession = vi.fn()
const mockSignIn = vi.fn()
const mockLogLogin = vi.fn()
const mockRegisterWithEmail = vi.fn()
const mockRecordFailed = vi.fn()

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: (...a: unknown[]) => mockPush(...a) }),
  useRoute: () => ({ query: {} }),
}))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ setUser: mockSetUser }) }))
vi.mock('@/services/session', () => ({ endSession: (...a: unknown[]) => mockEndSession(...a) }))
vi.mock('@/composables/useAuth', () => ({
  useAuth: () => ({ signInWithEmail: (...a: unknown[]) => mockSignIn(...a), signInWithGoogle: vi.fn() }),
}))
vi.mock('@/services/authService', () => ({
  authService: {
    checkLoginAllowed: async () => undefined,
    logLogin: (...a: unknown[]) => mockLogLogin(...a),
    registerWithEmail: (...a: unknown[]) => mockRegisterWithEmail(...a),
    recordFailedLogin: (...a: unknown[]) => mockRecordFailed(...a),
  },
}))

const deleted = { response: { status: 403, data: { title: 'Conta em exclusão', detail: 'Conta marcada para exclusão.' } } }

async function submit() {
  const wrapper = mount(LoginView, { global: { stubs: { 'router-link': true } } })
  await wrapper.find('#email').setValue('a@b.com')
  await wrapper.find('#password').setValue('Senha123')
  await wrapper.find('form').trigger('submit')
  await flushPromises()
  return wrapper
}

describe('LoginView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockSignIn.mockResolvedValue({ session: { access_token: 't' }, user: { id: 'u1', email: 'a@b.com', user_metadata: {} } })
  })

  it('conta em exclusão: mostra o detail, encerra a sessão e não entra', async () => {
    mockLogLogin.mockRejectedValue(deleted)
    const wrapper = await submit()
    expect(wrapper.text()).toContain('Conta marcada para exclusão.')
    expect(mockEndSession).toHaveBeenCalled()
    expect(mockSetUser).not.toHaveBeenCalled()
    expect(mockPush).not.toHaveBeenCalled()
    expect(mockRecordFailed).not.toHaveBeenCalled()
  })

  it('conta em exclusão no lazy sync (409): mesmo bloqueio', async () => {
    mockLogLogin.mockRejectedValue({ response: { status: 404 } })
    mockRegisterWithEmail.mockRejectedValue({ response: { status: 409, data: { detail: 'Conta em exclusão.' } } })
    const wrapper = await submit()
    expect(wrapper.text()).toContain('Conta em exclusão.')
    expect(mockEndSession).toHaveBeenCalled()
    expect(mockSetUser).not.toHaveBeenCalled()
    expect(mockPush).not.toHaveBeenCalled()
  })

  it('409 de e-mail de outra conta no lazy sync: mesmo bloqueio', async () => {
    mockLogLogin.mockRejectedValue({ response: { status: 404 } })
    mockRegisterWithEmail.mockRejectedValue({ response: { status: 409, data: { detail: 'Este e-mail já está cadastrado em outra conta.' } } })
    const wrapper = await submit()
    expect(wrapper.text()).toContain('Este e-mail já está cadastrado em outra conta.')
    expect(mockEndSession).toHaveBeenCalled()
    expect(mockSetUser).not.toHaveBeenCalled()
    expect(mockPush).not.toHaveBeenCalled()
    expect(mockRecordFailed).not.toHaveBeenCalled()
  })

  it('outro erro do backend segue o fluxo', async () => {
    mockLogLogin.mockRejectedValue({ response: { status: 500 } })
    await submit()
    expect(mockEndSession).not.toHaveBeenCalled()
    expect(mockSetUser).toHaveBeenCalled()
    expect(mockPush).toHaveBeenCalledWith('/onboarding')
  })
})
