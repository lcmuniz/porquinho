import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'
import EmailVerificationView from '../EmailVerificationView.vue'

const mockGetSession = vi.fn()
const mockSignOut = vi.fn()

vi.mock('vue-router', () => ({ RouterLink: RouterLinkStub }))
vi.mock('@/services/supabase', () => ({
  supabase: { auth: { getSession: () => mockGetSession(), signOut: (...a: unknown[]) => mockSignOut(...a) } },
}))

const confirmed = { data: { session: { user: { email: 'a@b.com', email_confirmed_at: '2026-11-19T00:00:00Z' } } } }

describe('EmailVerificationView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockSignOut.mockResolvedValue({ error: null })
  })
  afterEach(() => window.history.replaceState(null, '', '/'))

  it('link já usado (#error=otp_expired): mostra erro, mesmo com sessão confirmada', async () => {
    window.history.replaceState(null, '', '/auth/verify-email#error=access_denied&error_code=otp_expired')
    mockGetSession.mockResolvedValue(confirmed)
    const wrapper = mount(EmailVerificationView)
    await flushPromises()
    expect(wrapper.text()).toContain('já foi usado ou expirou')
    expect(wrapper.text()).not.toContain('Email verificado com sucesso!')
  })

  it('verificado: mostra sucesso e encerra a sessão local para o login criar o usuário no backend', async () => {
    window.history.replaceState(null, '', '/auth/verify-email')
    mockGetSession.mockResolvedValue(confirmed)
    const wrapper = mount(EmailVerificationView)
    await flushPromises()
    expect(wrapper.text()).toContain('Email verificado com sucesso!')
    expect(mockSignOut).toHaveBeenCalledWith({ scope: 'local' })
  })

  it('vindo do cadastro: mostra o e-mail do history state', async () => {
    window.history.replaceState({ email: 'novo@b.com' }, '', '/auth/verify-email')
    mockGetSession.mockResolvedValue({ data: { session: null } })
    const wrapper = mount(EmailVerificationView)
    await flushPromises()
    expect(wrapper.text()).toContain('novo@b.com')
    expect(mockSignOut).not.toHaveBeenCalled()
  })
})
