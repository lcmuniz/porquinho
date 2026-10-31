import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'
import DashboardView from '../DashboardView.vue'

const mockGetMySubscription = vi.fn()

vi.mock('@/services/subscriptionService', () => ({
  getMySubscription: (...args: unknown[]) => mockGetMySubscription(...args),
}))

function mountView() {
  return mount(DashboardView, { global: { stubs: { RouterLink: RouterLinkStub } } })
}

describe('DashboardView', () => {
  beforeEach(() => {
    mockGetMySubscription.mockReset()
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
})
