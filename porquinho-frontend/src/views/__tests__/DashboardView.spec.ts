import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import DashboardView from '../DashboardView.vue'

const mockGetMySubscription = vi.fn()
const mockPush = vi.fn()
const mockReplace = vi.fn()
let mockRoute: { path: string; query: Record<string, string> } = { path: '/dashboard', query: {} }
const mockSignOut = vi.fn()

vi.mock('@/services/subscriptionService', () => ({
  getMySubscription: (...args: unknown[]) => mockGetMySubscription(...args),
}))

vi.mock('vue-router', async (importOriginal) => ({
  ...(await importOriginal<typeof import('vue-router')>()),
  useRouter: () => ({ push: mockPush, replace: mockReplace }),
  useRoute: () => mockRoute,
}))

vi.mock('@/services/supabase', () => ({
  supabase: { auth: { signOut: () => mockSignOut() } },
}))

function mountView() {
  return mount(DashboardView, { global: { stubs: { RouterLink: RouterLinkStub } } })
}

describe('DashboardView', () => {
  const assign = vi.fn()

  beforeEach(() => {
    assign.mockReset()
    vi.stubGlobal('location', { ...window.location, assign })
    setActivePinia(createPinia())
    mockGetMySubscription.mockReset()
    mockPush.mockReset()
    mockReplace.mockReset()
    mockRoute = { path: '/dashboard', query: {} }
    mockSignOut.mockReset().mockResolvedValue({ error: null })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
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
    expect(wrapper.get('[data-testid="subscribe-cta"]').text()).toBe('Assinar')
  })

  it('esconde o badge e mantém o dashboard quando a API falha (ex.: 404)', async () => {
    mockGetMySubscription.mockRejectedValue({ response: { status: 404 } })

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.find('[data-testid="trial-badge"]').exists()).toBe(false)
    expect(wrapper.get('h1').text()).toBe('Dashboard')
    expect(wrapper.text()).toContain('Excluir minha conta')
  })
  it('a navegação tem o link "Contas bancárias" para /contas (Story 4.1)', async () => {
    mockGetMySubscription.mockRejectedValue({ response: { status: 404 } })

    const wrapper = mountView()
    await flushPromises()

    const link = wrapper
      .findAllComponents(RouterLinkStub)
      .find((l) => l.attributes('data-testid') === 'accounts-link')
    expect(link?.props('to')).toBe('/contas')
    expect(link?.text()).toBe('Contas bancárias')
  })

  it('a navegação tem o link "Importar extrato" para /importar (Story 5.1)', async () => {
    mockGetMySubscription.mockRejectedValue({ response: { status: 404 } })

    const wrapper = mountView()
    await flushPromises()

    const link = wrapper
      .findAllComponents(RouterLinkStub)
      .find((l) => l.attributes('data-testid') === 'import-link')
    expect(link?.props('to')).toBe('/importar')
    expect(link?.text()).toBe('Importar extrato')
  })

  it('a navegação tem o link "Cartões de crédito" para /cartoes (Story 4.2)', async () => {
    mockGetMySubscription.mockRejectedValue({ response: { status: 404 } })

    const wrapper = mountView()
    await flushPromises()

    const link = wrapper
      .findAllComponents(RouterLinkStub)
      .find((l) => l.attributes('data-testid') === 'cards-link')
    expect(link?.props('to')).toBe('/cartoes')
    expect(link?.text()).toBe('Cartões de crédito')
  })

  it('a navegação tem o link "Categorias" para /categorias (Story 6.1)', async () => {
    mockGetMySubscription.mockRejectedValue({ response: { status: 404 } })

    const wrapper = mountView()
    await flushPromises()

    const link = wrapper
      .findAllComponents(RouterLinkStub)
      .find((l) => l.attributes('data-testid') === 'categories-link')
    expect(link?.props('to')).toBe('/categorias')
    expect(link?.text()).toBe('Categorias')
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

  it('mostra "Assinatura ativa" e esconde o CTA para plano mensal', async () => {
    mockGetMySubscription.mockResolvedValue({
      planType: 'MONTHLY',
      status: 'ACTIVE',
      trialStartDate: '2026-10-01T12:00:00Z',
      trialEndDate: '2026-10-31T12:00:00Z',
      daysRemaining: 0,
      hasAccess: true,
    })
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.get('[data-testid="subscription-active-badge"]').text()).toBe('Assinatura ativa')
    expect(wrapper.find('[data-testid="subscribe-cta"]').exists()).toBe(false)
  })

  // Story 2.4
  it('mostra o CTA Reativar para assinatura expirada (Story 2.5)', async () => {
    mockGetMySubscription.mockResolvedValue({
      planType: 'MONTHLY',
      status: 'EXPIRED',
      trialStartDate: '2026-09-01T12:00:00Z',
      trialEndDate: '2026-10-01T12:00:00Z',
      nextBillingDate: '2026-10-30T12:00:00Z',
      cancellationDate: '2026-10-10T12:00:00Z',
      daysRemaining: 0,
      hasAccess: false,
    })
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.get('[data-testid="subscription-expired-badge"]').text()).toBe(
      'Assinatura expirada',
    )
    expect(wrapper.get('[data-testid="subscribe-cta"]').text()).toBe('Reativar')
  })

  it('cancelada dentro do período mostra a data e não oferece o CTA', async () => {
    mockGetMySubscription.mockResolvedValue({
      planType: 'MONTHLY',
      status: 'CANCELLED',
      trialStartDate: '2026-09-01T12:00:00Z',
      trialEndDate: '2026-10-01T12:00:00Z',
      nextBillingDate: '2026-11-30T12:00:00Z',
      cancellationDate: '2026-10-31T12:00:00Z',
      daysRemaining: 0,
      hasAccess: true,
    })
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.get('[data-testid="subscription-cancelled-badge"]').text()).toBe(
      'Assinatura cancelada (ativa até 30/11/2026)',
    )
    expect(wrapper.find('[data-testid="subscribe-cta"]').exists()).toBe(false)
  })

  it('cancelada já sem acesso mostra o CTA Reativar', async () => {
    mockGetMySubscription.mockResolvedValue({
      planType: 'MONTHLY',
      status: 'CANCELLED',
      trialStartDate: '2026-09-01T12:00:00Z',
      trialEndDate: '2026-10-01T12:00:00Z',
      nextBillingDate: '2026-10-30T12:00:00Z',
      cancellationDate: '2026-10-10T12:00:00Z',
      daysRemaining: 0,
      hasAccess: false,
    })
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.get('[data-testid="subscribe-cta"]').text()).toBe('Reativar')
  })

  it('PAYMENT_FAILED mostra o badge e o banner com "Atualizar pagamento" (Story 2.7)', async () => {
    mockGetMySubscription.mockResolvedValue({
      planType: 'MONTHLY',
      status: 'PAYMENT_FAILED',
      trialStartDate: null,
      trialEndDate: null,
      nextBillingDate: '2026-10-30T12:00:00Z',
      cancellationDate: null,
      failedPaymentAttempts: 1,
      paymentUpdateUrl: 'https://invoice.stripe.com/i/in_1',
      daysRemaining: 0,
      hasAccess: true,
    })

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.get('[data-testid="subscription-payment-failed-badge"]').text()).toBe(
      'Pagamento falhou',
    )
    const banner = wrapper.get('[data-testid="payment-failed-banner"]')
    expect(banner.attributes('role')).toBe('alert')
    expect(banner.text()).toContain('Pagamento falhou')
    expect(wrapper.find('[data-testid="subscribe-cta"]').exists()).toBe(false)

    // O CTA abre direto a fatura no Stripe (AC), não a tela de assinatura
    const cta = wrapper.get('[data-testid="payment-failed-cta"]')
    expect(cta.text()).toBe('Atualizar pagamento')
    await cta.trigger('click')
    expect(assign).toHaveBeenCalledWith('https://invoice.stripe.com/i/in_1')
  })

  it('PAYMENT_FAILED sem link: o CTA cai na tela de assinatura (Story 2.7)', async () => {
    mockGetMySubscription.mockResolvedValue({
      planType: 'MONTHLY',
      status: 'PAYMENT_FAILED',
      trialStartDate: null,
      trialEndDate: null,
      nextBillingDate: '2026-10-30T12:00:00Z',
      cancellationDate: null,
      failedPaymentAttempts: 1,
      paymentUpdateUrl: null,
      daysRemaining: 0,
      hasAccess: true,
    })

    const wrapper = mountView()
    await flushPromises()

    const cta = wrapper.getComponent<typeof RouterLinkStub>('[data-testid="payment-failed-cta"]')
    expect(cta.text()).toBe('Atualizar pagamento')
    expect(cta.props('to')).toBe('/assinatura')
  })

  it('SUSPENDED mostra "Assinatura suspensa" e o CTA Reativar, sem o banner (Story 2.7)', async () => {
    mockGetMySubscription.mockResolvedValue({
      planType: 'MONTHLY',
      status: 'SUSPENDED',
      trialStartDate: null,
      trialEndDate: null,
      nextBillingDate: '2026-10-20T12:00:00Z',
      cancellationDate: null,
      failedPaymentAttempts: 3,
      paymentUpdateUrl: null,
      daysRemaining: 0,
      hasAccess: false,
    })

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.get('[data-testid="subscription-suspended-badge"]').text()).toBe(
      'Assinatura suspensa',
    )
    expect(wrapper.get('[data-testid="subscribe-cta"]').text()).toBe('Reativar')
    expect(wrapper.find('[data-testid="payment-failed-banner"]').exists()).toBe(false)
  })

  describe('Story 3.8: fim do onboarding', () => {
    it('com ?onboarding=done mostra "Parabéns! Sua conta está configurada" e limpa o parâmetro', async () => {
      mockGetMySubscription.mockRejectedValue({ response: { status: 404 } })
      mockRoute = { path: '/dashboard', query: { onboarding: 'done', outro: '1' } }

      const wrapper = mountView()
      await flushPromises()

      const message = wrapper.get('[data-testid="onboarding-done-message"]')
      expect(message.attributes('role')).toBe('status')
      expect(message.text()).toContain('Parabéns! Sua conta está configurada')
      expect(mockReplace).toHaveBeenCalledWith({ path: '/dashboard', query: { outro: '1' } })
    })

    it('Fechar esconde a mensagem', async () => {
      mockGetMySubscription.mockRejectedValue({ response: { status: 404 } })
      mockRoute = { path: '/dashboard', query: { onboarding: 'done' } }

      const wrapper = mountView()
      await flushPromises()
      await wrapper.get('[data-testid="onboarding-done-close"]').trigger('click')

      expect(wrapper.find('[data-testid="onboarding-done-message"]').exists()).toBe(false)
    })

    it('sem o parâmetro não mostra a mensagem nem mexe na URL', async () => {
      mockGetMySubscription.mockRejectedValue({ response: { status: 404 } })

      const wrapper = mountView()
      await flushPromises()

      expect(wrapper.find('[data-testid="onboarding-done-message"]').exists()).toBe(false)
      expect(mockReplace).not.toHaveBeenCalled()
    })
  })
})
