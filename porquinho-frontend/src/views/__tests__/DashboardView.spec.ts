import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import DashboardView from '../DashboardView.vue'

const mockGetMySubscription = vi.fn()
const mockPush = vi.fn()
const mockSignOut = vi.fn()

vi.mock('@/services/subscriptionService', () => ({
  getMySubscription: (...args: unknown[]) => mockGetMySubscription(...args),
}))

vi.mock('vue-router', async (importOriginal) => ({
  ...(await importOriginal<typeof import('vue-router')>()),
  useRouter: () => ({ push: mockPush }),
}))

vi.mock('@/services/supabase', () => ({
  supabase: { auth: { signOut: () => mockSignOut() } },
}))

function mountView() {
  return mount(DashboardView, { global: { stubs: { RouterLink: RouterLinkStub } } })
}

describe('DashboardView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockGetMySubscription.mockReset()
    mockPush.mockReset()
    mockSignOut.mockReset().mockResolvedValue({ error: null })
  })

  it('mostra o badge do trial quando a API responde', async () => {
    mockGetMySubscription.mockResolvedValue({
      planType: 'TRIAL',
      status: 'ACTIVE',
      trialStartDate: '2026-10-31T12:00:00Z',
      trialEndDate: '2026-11-30T12:00:00Z',
      daysRemaining: 12,
      hasAccess: true,
    })

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.get('[data-testid="trial-badge"]').text()).toBe('Trial: 12 dias restantes')
  })

  it('esconde o badge e mantém o dashboard quando a API falha (ex.: 404)', async () => {
    mockGetMySubscription.mockRejectedValue({ response: { status: 404 } })

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.find('[data-testid="trial-badge"]').exists()).toBe(false)
    expect(wrapper.get('h1').text()).toBe('Dashboard')
    expect(wrapper.text()).toContain('Excluir minha conta')
  })
  it('Sair limpa o usuário local, faz signOut e vai para /login', async () => {
    mockGetMySubscription.mockRejectedValue({ response: { status: 404 } })
    localStorage.setItem('user', '{"id":"x"}')

    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('button').trigger('click')
    await flushPromises()

    expect(localStorage.getItem('user')).toBeNull()
    expect(mockSignOut).toHaveBeenCalled()
    expect(mockPush).toHaveBeenCalledWith('/login')
  })
})
