import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ForgotPasswordView from '../ForgotPasswordView.vue'

const mockRequestPasswordReset = vi.fn()
const mockLogRequested = vi.fn()

vi.mock('@/composables/useAuth', () => ({
  useAuth: () => ({ requestPasswordReset: (...a: unknown[]) => mockRequestPasswordReset(...a) }),
}))
vi.mock('@/services/authService', () => ({
  authService: { logPasswordResetRequested: (...a: unknown[]) => mockLogRequested(...a) },
}))

async function submit() {
  const wrapper = mount(ForgotPasswordView, { global: { stubs: { 'router-link': true, RouterLink: true } } })
  await wrapper.find('#email').setValue('a@b.com')
  await wrapper.find('form').trigger('submit')
  await flushPromises()
  return wrapper
}

describe('ForgotPasswordView', () => {
  beforeEach(() => vi.clearAllMocks())

  it('limite do Supabase (AuthError com status 429): mostra o aviso e não o sucesso', async () => {
    mockRequestPasswordReset.mockRejectedValue({ status: 429, message: 'email rate limit exceeded' })
    const wrapper = await submit()
    expect(wrapper.text()).toContain('Muitas solicitações. Aguarde alguns minutos e tente de novo.')
    expect(wrapper.text()).not.toContain('Verifique seu email')
  })

  it('outro erro do Supabase: sucesso genérico, sem revelar se o e-mail existe', async () => {
    mockRequestPasswordReset.mockRejectedValue({ status: 400, message: 'qualquer' })
    const wrapper = await submit()
    expect(wrapper.text()).toContain('Verifique seu email')
  })

  it('429 da auditoria no backend não muda nada: o e-mail já saiu, a tela mostra sucesso', async () => {
    mockRequestPasswordReset.mockResolvedValue({})
    mockLogRequested.mockRejectedValue({ response: { status: 429 } })
    const wrapper = await submit()
    expect(wrapper.text()).toContain('Verifique seu email')
    expect(wrapper.text()).not.toContain('Muitas solicitações')
  })
})
