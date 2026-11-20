import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import DashboardView from '../DashboardView.vue'

const mockGetMySubscription = vi.fn()
const mockPush = vi.fn()
const mockReplace = vi.fn()
let mockRoute: { path: string; query: Record<string, string> } = { path: '/dashboard', query: {} }
let leaveGuard: ((to: { name: string }) => void) | undefined

const mockGetFinancialHealth = vi.fn()
const mockGetOverview = vi.fn()
const mockGetDetails = vi.fn()
vi.mock('@/services/dashboardService', () => ({
  getFinancialHealth: () => mockGetFinancialHealth(),
  getOverview: () => mockGetOverview(),
  // Camadas 2 (Story 7.5) e 3 (Story 7.6): vazias, cobertas nos próprios specs; terminar de carregar libera o scroll
  getTactical: async () => ({ month: '2026-11', categories: [], trends: [] }),
  getLargestSpent: () => mockGetDetails(),
  getDrilldown: () => mockGetDetails(),
  exportDetails: vi.fn(),
}))
vi.mock('@/services/accountService', () => ({ listAccounts: async () => [] }))
vi.mock('@/services/cardService', () => ({ listCards: async () => [] }))
vi.mock('@/services/categoryService', () => ({ listCategories: async () => [] }))

// O drill-down da Camada 2 importa o transactionService; sem mock, api.ts exige o .env (CI)
vi.mock('@/services/transactionService', () => ({ listTransactions: vi.fn(), updateTransaction: vi.fn() }))

vi.mock('@/services/subscriptionService', () => ({
  getMySubscription: (...args: unknown[]) => mockGetMySubscription(...args),
}))

vi.mock('vue-router', async (importOriginal) => ({
  ...(await importOriginal<typeof import('vue-router')>()),
  useRouter: () => ({ push: mockPush, replace: mockReplace }),
  useRoute: () => mockRoute,
  onBeforeRouteLeave: (guard: (to: { name: string }) => void) => {
    leaveGuard = guard
  },
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
    mockGetFinancialHealth.mockReset().mockResolvedValue({
      status: 'HEALTHY',
      healthyCategories: 9,
      lowCategories: 1,
      overspentCategories: 0,
      negativeAccounts: 0,
      goalsBehind: 0,
    })
    mockGetOverview.mockReset().mockResolvedValue({
      totalBalance: 1500,
      categories: [{ categoryId: 'c1', name: 'Mercado', status: 'HEALTHY', remaining: 100 }],
      mainGoal: null,
      monthSpent: 0,
      monthAllocated: 0,
      projectedBalance: 1500,
      wealthHistory: [],
    })
    mockGetDetails.mockReset().mockResolvedValue([])
    mockPush.mockReset()
    mockReplace.mockReset()
    mockRoute = { path: '/dashboard', query: {} }
    vi.spyOn(window, 'scrollTo').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
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
    expect(wrapper.get('h1').text()).toBe('Início')
    expect(wrapper.find('#layer-details').exists()).toBe(true)
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

  describe('Story 7.2: semáforo de saúde financeira', () => {
    it('aparece no topo, antes do cabeçalho', async () => {
      mockGetMySubscription.mockRejectedValue({ response: { status: 404 } })

      const wrapper = mountView()
      await flushPromises()

      expect(wrapper.get('[data-testid="health-toggle"]').text()).toBe('Saúde Financeira: Saudável')
      const container = wrapper.get('main > div')
      expect(container.element.firstElementChild?.classList.contains('lg:sticky')).toBe(true)
    })

    it('falha do semáforo não derruba o resto do dashboard', async () => {
      mockGetMySubscription.mockRejectedValue({ response: { status: 404 } })
      mockGetFinancialHealth.mockRejectedValue(new Error('rede'))

      const wrapper = mountView()
      await flushPromises()

      expect(wrapper.find('[data-testid="health-error"]').exists()).toBe(true)
      expect(wrapper.get('h1').text()).toBe('Início')
      expect(wrapper.find('#layer-details').exists()).toBe(true)
    })

    it('recarrega quando uma edição é salva na Camada 3 (Story 7.6, Q1=B)', async () => {
      mockGetMySubscription.mockRejectedValue({ response: { status: 404 } })
      const wrapper = mountView()
      await flushPromises()
      expect(mockGetFinancialHealth).toHaveBeenCalledTimes(1)

      wrapper.findComponent({ name: 'DashboardGps' }).vm.$emit('transaction-saved')
      await flushPromises()

      expect(mockGetFinancialHealth).toHaveBeenCalledTimes(2)
      // Retro Epic 7 A4: o semáforo antigo fica na tela, sem voltar ao "Carregando…"
      expect(wrapper.find('[data-testid="health-loading"]').exists()).toBe(false)
    })
  })

  describe('Story 7.3: Camada 1', () => {
    it('mostra os cards na Visão geral', async () => {
      mockGetMySubscription.mockRejectedValue({ response: { status: 404 } })

      const wrapper = mountView()
      await flushPromises()

      expect(wrapper.get('#layer-overview').find('[data-testid="card-balance"]').exists()).toBe(true)
    })

    it('falha da Camada 1 não derruba o semáforo nem o resto do dashboard', async () => {
      mockGetMySubscription.mockRejectedValue({ response: { status: 404 } })
      mockGetOverview.mockRejectedValue(new Error('rede'))

      const wrapper = mountView()
      await flushPromises()

      expect(wrapper.find('[data-testid="overview-error"]').exists()).toBe(true)
      expect(wrapper.get('[data-testid="health-toggle"]').text()).toBe('Saúde Financeira: Saudável')
      expect(wrapper.find('#layer-details').exists()).toBe(true)
    })
  })

  describe('Story 7.1: Dashboard GPS', () => {
    it('mostra as 3 camadas depois dos banners, sem as configurações da conta', async () => {
      mockGetMySubscription.mockRejectedValue({ response: { status: 404 } })

      const wrapper = mountView()
      await flushPromises()

      const ids = wrapper.findAll('section').map((s) => s.attributes('aria-labelledby'))
      expect(ids).toEqual([
        'layer-overview-heading',
        'layer-tactical-heading',
        'layer-details-heading',
      ])
      expect(wrapper.text()).not.toContain('Em desenvolvimento')
    })

    // Antes do teste de 800: o estado do módulo é compartilhado entre os testes
    it('primeira visita abre no topo', async () => {
      mockGetMySubscription.mockRejectedValue({ response: { status: 404 } })

      mountView()
      await flushPromises()

      expect(window.scrollTo).toHaveBeenLastCalledWith({ top: 0, behavior: 'instant' })
    })

    it('sair grava o scrollY e remontar volta para a mesma altura', async () => {
      mockGetMySubscription.mockRejectedValue({ response: { status: 404 } })

      const first = mountView()
      await flushPromises()
      vi.stubGlobal('scrollY', 800)
      leaveGuard?.({ name: 'transactions' })
      first.unmount()

      mountView()
      await flushPromises()

      expect(window.scrollTo).toHaveBeenLastCalledWith({ top: 800, behavior: 'instant' })
    })

    it('só restaura o scroll quando as camadas terminam de carregar (retro Epic 7 A4)', async () => {
      mockGetMySubscription.mockRejectedValue({ response: { status: 404 } })
      let finishDetails!: (value: unknown) => void
      mockGetDetails.mockReturnValue(new Promise((resolve) => (finishDetails = resolve)))

      mountView()
      await flushPromises()
      expect(window.scrollTo).not.toHaveBeenCalled()

      finishDetails([])
      await flushPromises()
      expect(window.scrollTo).toHaveBeenCalledTimes(1)
    })

    it('ir para o login (Sair) zera a posição: o próximo login abre no topo', async () => {
      mockGetMySubscription.mockRejectedValue({ response: { status: 404 } })

      const first = mountView()
      await flushPromises()
      vi.stubGlobal('scrollY', 800)
      leaveGuard?.({ name: 'login' })
      first.unmount()

      mountView()
      await flushPromises()

      expect(window.scrollTo).toHaveBeenLastCalledWith({ top: 0, behavior: 'instant' })
    })
  })
})
