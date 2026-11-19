import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import RegisterView from '../RegisterView.vue'

const mockPush = vi.fn()
const mockEndSession = vi.fn()
const mockRegisterWithEmail = vi.fn()
const mockSignUp = vi.fn()

vi.mock('vue-router', () => ({ useRouter: () => ({ push: (...a: unknown[]) => mockPush(...a) }) }))
vi.mock('@/services/session', () => ({ endSession: (...a: unknown[]) => mockEndSession(...a) }))
vi.mock('@/composables/useAuth', () => ({
  useAuth: () => ({ signUpWithEmail: (...a: unknown[]) => mockSignUp(...a), signInWithGoogle: vi.fn() }),
}))
vi.mock('@/services/authService', () => ({
  authService: { registerWithEmail: (...a: unknown[]) => mockRegisterWithEmail(...a) },
}))

async function submit() {
  const wrapper = mount(RegisterView, { global: { stubs: { 'router-link': true, RouterLink: true } } })
  await wrapper.find('#name').setValue('Ana')
  await wrapper.find('#email').setValue('a@b.com')
  await wrapper.find('#password').setValue('Senha1234')
  await wrapper.find('#confirmPassword').setValue('Senha1234')
  await wrapper.find('form').trigger('submit')
  await flushPromises()
  return wrapper
}

describe('RegisterView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockSignUp.mockResolvedValue({ user: { id: 'u1' }, session: { access_token: 't' } })
  })

  it('confirmação de e-mail pendente (sem sessão): vai para "Verifique seu email" sem chamar o backend', async () => {
    mockSignUp.mockResolvedValue({ user: { id: 'u1' }, session: null })
    await submit()
    expect(mockRegisterWithEmail).not.toHaveBeenCalled()
    expect(mockPush).toHaveBeenCalledWith({ path: '/auth/verify-email', state: { email: 'a@b.com' } })
  })

  it('conta em exclusão: mostra o detail, encerra a sessão e fica no cadastro', async () => {
    mockRegisterWithEmail.mockRejectedValue({
      response: { status: 403, data: { title: 'Conta em exclusão', detail: 'Conta marcada para exclusão.' } },
    })
    const wrapper = await submit()
    expect(wrapper.text()).toContain('Conta marcada para exclusão.')
    expect(mockEndSession).toHaveBeenCalled()
    expect(mockPush).not.toHaveBeenCalled()
  })

  it('409 de e-mail de outra conta: mostra o detail, encerra a sessão e fica no cadastro', async () => {
    mockRegisterWithEmail.mockRejectedValue({ response: { status: 409, data: { detail: 'Este e-mail já está cadastrado em outra conta.' } } })
    const wrapper = await submit()
    expect(wrapper.text()).toContain('Este e-mail já está cadastrado em outra conta.')
    expect(mockEndSession).toHaveBeenCalled()
    expect(mockPush).not.toHaveBeenCalled()
  })

  it('outro erro do backend segue para o onboarding', async () => {
    mockRegisterWithEmail.mockRejectedValue({ response: { status: 500 } })
    await submit()
    expect(mockEndSession).not.toHaveBeenCalled()
    expect(mockPush).toHaveBeenCalledWith('/onboarding')
  })
})
