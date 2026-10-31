import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'
import SubscriptionView from '../SubscriptionView.vue'
import type { Subscription } from '@/types/subscription'

const mockGetMySubscription = vi.fn()
const mockCreateCheckout = vi.fn()
const mockCancelSubscription = vi.fn()
const mockRoute = { query: {} as Record<string, string> }

vi.mock('@/services/subscriptionService', () => ({
  getMySubscription: (...args: unknown[]) => mockGetMySubscription(...args),
  createCheckout: (...args: unknown[]) => mockCreateCheckout(...args),
  cancelSubscription: (...args: unknown[]) => mockCancelSubscription(...args),
}))

vi.mock('vue-router', async (importOriginal) => ({
  ...(await importOriginal<typeof import('vue-router')>()),
  useRoute: () => mockRoute,
}))

function trial(overrides: Partial<Subscription> = {}): Subscription {
  return {
    planType: 'TRIAL',
    status: 'ACTIVE',
    trialStartDate: '2026-10-31T12:00:00Z',
    trialEndDate: '2026-11-30T12:00:00Z',
    nextBillingDate: null,
    cancellationDate: null,
    daysRemaining: 20,
    hasAccess: true,
    ...overrides,
  }
}

function mountView() {
  return mount(SubscriptionView, { global: { stubs: { RouterLink: RouterLinkStub } } })
}

describe('SubscriptionView', () => {
  const assign = vi.fn()

  beforeEach(() => {
    mockGetMySubscription.mockReset().mockResolvedValue(trial())
    mockCreateCheckout.mockReset()
    mockCancelSubscription.mockReset()
    mockRoute.query = {}
    assign.mockReset()
    vi.stubGlobal('location', { ...window.location, assign })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    document.body.innerHTML = ''
  })

  it('mostra o plano de R$ 19,90/mês e o botão Assinar', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.get('[data-testid="plan-price"]').text()).toContain('R$ 19,90')
    expect(wrapper.get('[data-testid="plan-price"]').text()).toContain('/mês')
    expect(wrapper.get('[data-testid="subscribe-button"]').text()).toBe('Assinar')
  })

  it('redireciona para a URL do Stripe Checkout ao clicar em Assinar', async () => {
    mockCreateCheckout.mockResolvedValue({ url: 'https://checkout.stripe.com/c/pay/cs_test_1' })
    const wrapper = mountView()
    await flushPromises()

    await wrapper.get('[data-testid="subscribe-button"]').trigger('click')
    await flushPromises()

    expect(mockCreateCheckout).toHaveBeenCalledOnce()
    expect(assign).toHaveBeenCalledWith('https://checkout.stripe.com/c/pay/cs_test_1')
  })

  it('mostra erro amigável quando o pagamento está indisponível (503)', async () => {
    mockCreateCheckout.mockRejectedValue({ response: { status: 503 } })
    const wrapper = mountView()
    await flushPromises()

    await wrapper.get('[data-testid="subscribe-button"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="checkout-error"]').text()).toContain('indisponível')
    expect(assign).not.toHaveBeenCalled()
    expect(wrapper.get('[data-testid="subscribe-button"]').attributes('disabled')).toBeUndefined()
  })

  it('mostra que já existe assinatura quando o checkout responde 409', async () => {
    mockCreateCheckout.mockRejectedValue({ response: { status: 409 } })
    const wrapper = mountView()
    await flushPromises()

    await wrapper.get('[data-testid="subscribe-button"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="checkout-error"]').text()).toBe('Você já possui uma assinatura ativa.')
    expect(assign).not.toHaveBeenCalled()
  })

  it('confirma a assinatura e esconde o botão no retorno com ?status=sucesso', async () => {
    mockRoute.query = { status: 'sucesso' }
    const wrapper = mountView()
    await flushPromises()

    const text = wrapper.get('[data-testid="checkout-success"]').text()
    expect(text).toContain('Assinatura confirmada!')
    expect(text).toContain('primeira cobrança acontece no fim do trial')
    expect(text).not.toContain('Pagamento recebido')
    expect(wrapper.find('[data-testid="subscribe-button"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('recarrega a assinatura a cada 2s e para quando vira MONTHLY', async () => {
    vi.useFakeTimers()
    try {
      mockRoute.query = { status: 'sucesso' }
      mockGetMySubscription
        .mockResolvedValueOnce(trial())
        .mockResolvedValueOnce(trial())
        .mockResolvedValue(trial({ planType: 'MONTHLY', daysRemaining: 0 }))
      const wrapper = mountView()
      await flushPromises()
      expect(mockGetMySubscription).toHaveBeenCalledTimes(1)

      await vi.advanceTimersByTimeAsync(2000)
      expect(mockGetMySubscription).toHaveBeenCalledTimes(2)
      await vi.advanceTimersByTimeAsync(2000)
      expect(mockGetMySubscription).toHaveBeenCalledTimes(3)

      await vi.advanceTimersByTimeAsync(10000)
      expect(mockGetMySubscription).toHaveBeenCalledTimes(3)
      expect(wrapper.get('[data-testid="subscription-active-badge"]').text()).toBe('Assinatura ativa')
      expect(wrapper.get('[data-testid="checkout-success"]').text()).not.toContain('fim do trial')
      wrapper.unmount()
    } finally {
      vi.useRealTimers()
    }
  })

  it('para de recarregar depois de 5 tentativas', async () => {
    vi.useFakeTimers()
    try {
      mockRoute.query = { status: 'sucesso' }
      const wrapper = mountView()
      await flushPromises()

      await vi.advanceTimersByTimeAsync(30000)
      expect(mockGetMySubscription).toHaveBeenCalledTimes(6) // carga inicial + 5 tentativas
      wrapper.unmount()
    } finally {
      vi.useRealTimers()
    }
  })

  it('no retorno cancelado com trial vencido não promete trial', async () => {
    mockRoute.query = { status: 'cancelado' }
    mockGetMySubscription.mockResolvedValue(trial({ daysRemaining: 0, hasAccess: false }))
    const wrapper = mountView()
    await flushPromises()

    const text = wrapper.get('[data-testid="checkout-cancelled"]').text()
    expect(text).toBe('Assinatura não concluída.')
    expect(text).not.toContain('trial')
  })

  it('avisa que o trial continua no retorno com ?status=cancelado', async () => {
    mockRoute.query = { status: 'cancelado' }
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.get('[data-testid="checkout-cancelled"]').text()).toContain('trial continua')
  })

  it('não oferece o botão para quem já tem assinatura ativa', async () => {
    mockGetMySubscription.mockResolvedValue(trial({ planType: 'MONTHLY', daysRemaining: 0 }))
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.find('[data-testid="subscribe-button"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="already-subscribed"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="subscription-active-badge"]').text()).toBe('Assinatura ativa')
  })

  // ---- Story 2.4: cancelamento ----

  const activeMonthly = () =>
    trial({ planType: 'MONTHLY', daysRemaining: 0, nextBillingDate: '2026-11-30T12:00:00Z' })
  const cancelledMonthly = () =>
    trial({
      planType: 'MONTHLY',
      status: 'CANCELLED',
      daysRemaining: 0,
      nextBillingDate: '2026-11-30T12:00:00Z',
      cancellationDate: '2026-10-31T12:00:00Z',
    })

  function mountAttached() {
    return mount(SubscriptionView, {
      attachTo: document.body,
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
  }

  function confirmButton(): HTMLButtonElement {
    const button = document.body.querySelector<HTMLButtonElement>('[data-testid="confirm-cancel-button"]')
    if (!button) throw new Error('confirm button not rendered')
    return button
  }

  it('oferece "Cancelar assinatura" só para mensal ativo', async () => {
    mockGetMySubscription.mockResolvedValue(activeMonthly())
    const monthly = mountView()
    await flushPromises()
    expect(monthly.get('[data-testid="cancel-button"]').text()).toBe('Cancelar assinatura')

    mockGetMySubscription.mockResolvedValue(trial())
    const inTrial = mountView()
    await flushPromises()
    expect(inTrial.find('[data-testid="cancel-button"]').exists()).toBe(false)

    mockGetMySubscription.mockResolvedValue(cancelledMonthly())
    const cancelled = mountView()
    await flushPromises()
    expect(cancelled.find('[data-testid="cancel-button"]').exists()).toBe(false)
  })

  it('abre o modal com a data do fim do período e só cancela ao confirmar', async () => {
    mockGetMySubscription.mockResolvedValue(activeMonthly())
    mockCancelSubscription.mockResolvedValue(cancelledMonthly())
    const wrapper = mountAttached()
    await flushPromises()

    await wrapper.get('[data-testid="cancel-button"]').trigger('click')
    await flushPromises()

    expect(document.body.textContent).toContain(
      'Tem certeza? Você perde o acesso ao fim do período atual (30/11/2026)',
    )
    expect(mockCancelSubscription).not.toHaveBeenCalled()

    confirmButton().click()
    await flushPromises()

    expect(mockCancelSubscription).toHaveBeenCalledOnce()
    expect(wrapper.get('[data-testid="cancelled-notice"]').text()).toContain(
      'Assinatura cancelada — ativa até 30/11/2026',
    )
    expect(wrapper.get('[data-testid="subscription-cancelled-badge"]').text()).toBe(
      'Assinatura cancelada (ativa até 30/11/2026)',
    )
    expect(wrapper.find('[data-testid="cancel-button"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="subscribe-button"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('mostra erro e mantém a assinatura quando o Stripe falha (503)', async () => {
    mockGetMySubscription.mockResolvedValue(activeMonthly())
    mockCancelSubscription.mockRejectedValue({ response: { status: 503 } })
    const wrapper = mountAttached()
    await flushPromises()

    await wrapper.get('[data-testid="cancel-button"]').trigger('click')
    await flushPromises()
    confirmButton().click()
    await flushPromises()

    expect(wrapper.get('[data-testid="cancel-error"]').text()).toContain('Nada foi alterado')
    expect(wrapper.get('[data-testid="subscription-active-badge"]').text()).toBe('Assinatura ativa')
    expect(wrapper.find('[data-testid="cancel-button"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('no 409 recarrega e mostra o estado atual', async () => {
    mockGetMySubscription
      .mockResolvedValueOnce(activeMonthly())
      .mockResolvedValue(cancelledMonthly())
    mockCancelSubscription.mockRejectedValue({ response: { status: 409 } })
    const wrapper = mountAttached()
    await flushPromises()

    await wrapper.get('[data-testid="cancel-button"]').trigger('click')
    await flushPromises()
    confirmButton().click()
    await flushPromises()

    expect(mockGetMySubscription).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[data-testid="cancelled-notice"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="cancel-error"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('assinatura expirada mostra o badge e o botão Assinar', async () => {
    mockGetMySubscription.mockResolvedValue(
      trial({ planType: 'MONTHLY', status: 'EXPIRED', daysRemaining: 0, hasAccess: false }),
    )
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.get('[data-testid="subscription-expired-badge"]').text()).toBe('Assinatura expirada')
    expect(wrapper.find('[data-testid="subscribe-button"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="cancel-button"]').exists()).toBe(false)
  })

  it('assinando de novo a partir de MONTHLY/EXPIRED continua recarregando até ficar ativa', async () => {
    vi.useFakeTimers()
    try {
      mockRoute.query = { status: 'sucesso' }
      const expired = trial({ planType: 'MONTHLY', status: 'EXPIRED', daysRemaining: 0, hasAccess: false })
      mockGetMySubscription
        .mockResolvedValueOnce(expired)
        .mockResolvedValueOnce(expired)
        .mockResolvedValue(trial({ planType: 'MONTHLY', daysRemaining: 0 }))
      const wrapper = mountView()
      await flushPromises()

      await vi.advanceTimersByTimeAsync(4000)
      expect(mockGetMySubscription).toHaveBeenCalledTimes(3)
      await vi.advanceTimersByTimeAsync(10000)
      expect(mockGetMySubscription).toHaveBeenCalledTimes(3)
      expect(wrapper.get('[data-testid="subscription-active-badge"]').text()).toBe('Assinatura ativa')
      wrapper.unmount()
    } finally {
      vi.useRealTimers()
    }
  })

  it('sem resposta do servidor recarrega o status e não promete que nada mudou', async () => {
    mockGetMySubscription
      .mockResolvedValueOnce(activeMonthly())
      .mockResolvedValue(cancelledMonthly())
    mockCancelSubscription.mockRejectedValue(new Error('timeout'))
    const wrapper = mountAttached()
    await flushPromises()

    await wrapper.get('[data-testid="cancel-button"]').trigger('click')
    await flushPromises()
    confirmButton().click()
    await flushPromises()

    expect(mockGetMySubscription).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[data-testid="cancelled-notice"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="cancel-error"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('erro sem resposta e assinatura ainda ativa mostra aviso sem "Nada foi alterado"', async () => {
    mockGetMySubscription.mockResolvedValue(activeMonthly())
    mockCancelSubscription.mockRejectedValue(new Error('timeout'))
    const wrapper = mountAttached()
    await flushPromises()

    await wrapper.get('[data-testid="cancel-button"]').trigger('click')
    await flushPromises()
    confirmButton().click()
    await flushPromises()

    const text = wrapper.get('[data-testid="cancel-error"]').text()
    expect(text).toContain('Não foi possível confirmar')
    expect(text).not.toContain('Nada foi alterado')
    wrapper.unmount()
  })

  it('cancelada já sem acesso mostra o botão Assinar e não o aviso de cancelada', async () => {
    mockGetMySubscription.mockResolvedValue(
      trial({
        planType: 'MONTHLY',
        status: 'CANCELLED',
        daysRemaining: 0,
        hasAccess: false,
        nextBillingDate: '2026-10-01T12:00:00Z',
      }),
    )
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.find('[data-testid="subscribe-button"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="cancelled-notice"]').exists()).toBe(false)
  })
})
