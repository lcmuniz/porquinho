import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ResetPasswordView from '../ResetPasswordView.vue'

const mockUpdatePassword = vi.fn()

vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
vi.mock('@/composables/useAuth', () => ({
  useAuth: () => ({ updatePassword: (...a: unknown[]) => mockUpdatePassword(...a) }),
}))
vi.mock('@/services/authService', () => ({ authService: { logPasswordResetCompleted: vi.fn() } }))

const stubs = { global: { stubs: { 'router-link': true, RouterLink: true } } }

describe('ResetPasswordView', () => {
  beforeEach(() => vi.clearAllMocks())
  afterEach(() => window.history.replaceState(null, '', '/'))

  it('mesma senha (Supabase same_password): mostra a mensagem específica', async () => {
    mockUpdatePassword.mockRejectedValue({
      code: 'same_password',
      message: 'New password should be different from the old password.',
    })
    const wrapper = mount(ResetPasswordView, stubs)
    const inputs = wrapper.findAll('input[type="password"]')
    await inputs[0]!.setValue('Senha1234')
    await inputs[1]!.setValue('Senha1234')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('A nova senha deve ser diferente da senha anterior.')
  })

  it('link já usado (#error=otp_expired): mostra expirado e esconde o formulário', async () => {
    window.history.replaceState(null, '', '/auth/reset-password#error=access_denied&error_code=otp_expired')
    const wrapper = mount(ResetPasswordView, stubs)
    await flushPromises()
    expect(wrapper.text()).toContain('Link de redefinição expirado ou inválido. Solicite um novo.')
    expect(wrapper.find('form').exists()).toBe(false)
  })
})
