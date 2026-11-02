import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'
import SubscriptionView from '../SubscriptionView.vue'
import type { Subscription } from '@/types/subscription'

const mockGetMySubscription = vi.fn()
const mockCreateCheckout = vi.fn()
const mockCancelSubscription = vi.fn()
const mockReactivateSubscription = vi.fn()
const mockRoute = { query: {} as Record<string, string> }

vi.mock('@/services/subscriptionService', () => ({
  getMySubscription: (...args: unknown[]) => mockGetMySubscription(...args),
  createCheckout: (...args: unknown[]) => mockCreateCheckout(...args),
  cancelSubscription: (...args: unknown[]) => mockCancelSubscription(...args),
  reactivateSubscription: (...args: unknown[]) => mockReactivateSubscription(...args),
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
    failedPaymentAttempts: 0,
    paymentUpdateUrl: null,
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
    mockReactivateSubscription.mockReset()
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

  it('mostra o motivo do backend quando o 409 do checkout traz detail', async () => {
    mockCreateCheckout.mockRejectedValue({
      response: { status: 409, data: { detail: 'Seu último pagamento falhou. Pague a fatura pendente para manter sua assinatura.' } },
    })
    const wrapper = mountView()
    await flushPromises()

    await wrapper.get('[data-testid="subscribe-button"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="checkout-error"]').text()).toBe(
      'Seu último pagamento falhou. Pague a fatura pendente para manter sua assinatura.',
    )
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

  it('assinatura expirada mostra o badge e o botão Reativar assinatura (Checkout)', async () => {
    mockGetMySubscription.mockResolvedValue(
      trial({ planType: 'MONTHLY', status: 'EXPIRED', daysRemaining: 0, hasAccess: false }),
    )
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.get('[data-testid="subscription-expired-badge"]').text()).toBe('Assinatura expirada')
    expect(wrapper.get('[data-testid="subscribe-button"]').text()).toBe('Reativar assinatura')
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

  it('cancelada já sem acesso mostra o botão Reativar assinatura e não o aviso de cancelada', async () => {
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

    expect(wrapper.get('[data-testid="subscribe-button"]').text()).toBe('Reativar assinatura')
    expect(wrapper.find('[data-testid="cancelled-notice"]').exists()).toBe(false)
  })

  // ---- Story 2.5: reativação ----

  it('cancelada com acesso mostra "Reativar assinatura" e não o botão do Checkout', async () => {
    mockGetMySubscription.mockResolvedValue(cancelledMonthly())
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.get('[data-testid="reactivate-button"]').text()).toBe('Reativar assinatura')
    expect(wrapper.find('[data-testid="subscribe-button"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('a próxima cobrança acontece em 30/11/2026')
  })

  it('reativar (sem modal) chama a API e volta a "Assinatura ativa"', async () => {
    mockGetMySubscription.mockResolvedValue(cancelledMonthly())
    mockReactivateSubscription.mockResolvedValue(activeMonthly())
    const wrapper = mountView()
    await flushPromises()

    await wrapper.get('[data-testid="reactivate-button"]').trigger('click')
    await flushPromises()

    expect(mockReactivateSubscription).toHaveBeenCalledOnce()
    expect(mockCreateCheckout).not.toHaveBeenCalled()
    expect(wrapper.get('[data-testid="subscription-active-badge"]').text()).toBe('Assinatura ativa')
    expect(wrapper.find('[data-testid="cancel-button"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="reactivate-button"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="reactivate-error"]').exists()).toBe(false)
  })

  it('reativar com Stripe fora (503) mostra erro e mantém cancelada', async () => {
    mockGetMySubscription.mockResolvedValue(cancelledMonthly())
    mockReactivateSubscription.mockRejectedValue({ response: { status: 503 } })
    const wrapper = mountView()
    await flushPromises()

    await wrapper.get('[data-testid="reactivate-button"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="reactivate-error"]').text()).toContain('Nada foi alterado')
    expect(wrapper.find('[data-testid="cancelled-notice"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="reactivate-button"]').exists()).toBe(true)
  })

  it('reativar com 409 já ativa (outra aba) recarrega e não mostra erro', async () => {
    mockGetMySubscription.mockResolvedValueOnce(cancelledMonthly()).mockResolvedValue(activeMonthly())
    mockReactivateSubscription.mockRejectedValue({ response: { status: 409 } })
    const wrapper = mountView()
    await flushPromises()

    await wrapper.get('[data-testid="reactivate-button"]').trigger('click')
    await flushPromises()

    expect(mockGetMySubscription).toHaveBeenCalledTimes(2)
    expect(wrapper.get('[data-testid="subscription-active-badge"]').text()).toBe('Assinatura ativa')
    expect(wrapper.find('[data-testid="reactivate-error"]').exists()).toBe(false)
  })

  it('reativar com 409 do Stripe (assinatura já terminou) pede para assinar de novo', async () => {
    mockGetMySubscription.mockResolvedValue(cancelledMonthly())
    mockReactivateSubscription.mockRejectedValue({ response: { status: 409 } })
    const wrapper = mountView()
    await flushPromises()

    await wrapper.get('[data-testid="reactivate-button"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="reactivate-error"]').text()).toContain('Assine novamente')
  })

  it('reativar sem resposta (timeout) recarrega e, se ficou ativa, não mostra erro', async () => {
    mockGetMySubscription.mockResolvedValueOnce(cancelledMonthly()).mockResolvedValue(activeMonthly())
    mockReactivateSubscription.mockRejectedValue(new Error('timeout'))
    const wrapper = mountView()
    await flushPromises()

    await wrapper.get('[data-testid="reactivate-button"]').trigger('click')
    await flushPromises()

    expect(mockGetMySubscription).toHaveBeenCalledTimes(2)
    expect(wrapper.get('[data-testid="subscription-active-badge"]').text()).toBe('Assinatura ativa')
    expect(wrapper.find('[data-testid="reactivate-error"]').exists()).toBe(false)
  })

  it('reativar sem resposta (timeout) e ainda cancelada mostra aviso sem "Nada foi alterado"', async () => {
    mockGetMySubscription.mockResolvedValue(cancelledMonthly())
    mockReactivateSubscription.mockRejectedValue(new Error('timeout'))
    const wrapper = mountView()
    await flushPromises()

    await wrapper.get('[data-testid="reactivate-button"]').trigger('click')
    await flushPromises()

    expect(mockGetMySubscription).toHaveBeenCalledTimes(2)
    const text = wrapper.get('[data-testid="reactivate-error"]').text()
    expect(text).toContain('Não foi possível confirmar a reativação')
    expect(text).not.toContain('Nada foi alterado')
    expect(wrapper.find('[data-testid="cancelled-notice"]').exists()).toBe(true)
  })

  it('expirada: "Reativar assinatura" abre o Stripe Checkout', async () => {
    mockGetMySubscription.mockResolvedValue(
      trial({ planType: 'MONTHLY', status: 'EXPIRED', daysRemaining: 0, hasAccess: false }),
    )
    mockCreateCheckout.mockResolvedValue({ url: 'https://checkout.stripe.com/c/pay/cs_test_2' })
    const wrapper = mountView()
    await flushPromises()

    const button = wrapper.get('[data-testid="subscribe-button"]')
    expect(button.text()).toBe('Reativar assinatura')
    await button.trigger('click')
    await flushPromises()

    expect(mockCreateCheckout).toHaveBeenCalledOnce()
    expect(mockReactivateSubscription).not.toHaveBeenCalled()
    expect(assign).toHaveBeenCalledWith('https://checkout.stripe.com/c/pay/cs_test_2')
  })

  // ---- Story 2.7 ----

  function paymentFailed(overrides: Partial<Subscription> = {}): Subscription {
    return trial({
      planType: 'MONTHLY',
      status: 'PAYMENT_FAILED',
      daysRemaining: 0,
      nextBillingDate: '2026-10-30T12:00:00Z',
      failedPaymentAttempts: 1,
      paymentUpdateUrl: 'https://invoice.stripe.com/i/in_1',
      ...overrides,
    })
  }

  it('PAYMENT_FAILED: "Atualizar pagamento" abre a fatura no Stripe, sem Checkout', async () => {
    mockGetMySubscription.mockResolvedValue(paymentFailed())

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.get('[data-testid="subscription-payment-failed-badge"]').text()).toBe('Pagamento falhou')
    expect(wrapper.get('[data-testid="payment-failed-notice"]').text()).toContain('Não conseguimos cobrar')
    expect(wrapper.find('[data-testid="subscribe-button"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="reactivate-button"]').exists()).toBe(false)

    await wrapper.get('[data-testid="update-payment-button"]').trigger('click')

    expect(assign).toHaveBeenCalledWith('https://invoice.stripe.com/i/in_1')
    expect(mockCreateCheckout).not.toHaveBeenCalled()
  })

  it('PAYMENT_FAILED sem link não mostra o botão', async () => {
    mockGetMySubscription.mockResolvedValue(paymentFailed({ paymentUpdateUrl: null }))

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.find('[data-testid="payment-failed-notice"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="update-payment-button"]').exists()).toBe(false)
  })

  it('PAYMENT_FAILED: cancelar avisa que o acesso termina agora e mostra o estado encerrado', async () => {
    mockGetMySubscription.mockResolvedValue(paymentFailed())
    mockCancelSubscription.mockResolvedValue(
      paymentFailed({
        status: 'EXPIRED',
        cancellationDate: '2026-10-31T12:00:00Z',
        paymentUpdateUrl: null,
        hasAccess: false,
      }),
    )

    const wrapper = mountAttached()
    await flushPromises()

    await wrapper.get('[data-testid="cancel-button"]').trigger('click')
    await flushPromises()
    const description = document.body.querySelector('[data-testid="cancel-dialog-description"]')
    expect(description?.textContent).toContain('o acesso termina agora')
    expect(description?.textContent).not.toContain('fim do período')
    expect(mockCancelSubscription).not.toHaveBeenCalled()

    confirmButton().click()
    await flushPromises()

    expect(mockCancelSubscription).toHaveBeenCalledOnce()
    expect(wrapper.find('[data-testid="payment-failed-notice"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="cancelled-now-notice"]').text()).toContain('acesso foi encerrado')
    expect(wrapper.get('[data-testid="subscribe-button"]').text()).toBe('Reativar assinatura')
    expect(wrapper.find('[data-testid="cancel-error"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('PAYMENT_FAILED: cancelar com Stripe fora (503) mostra erro e mantém o aviso de falha', async () => {
    mockGetMySubscription.mockResolvedValue(paymentFailed())
    mockCancelSubscription.mockRejectedValue({ response: { status: 503 } })

    const wrapper = mountAttached()
    await flushPromises()

    await wrapper.get('[data-testid="cancel-button"]').trigger('click')
    await flushPromises()
    confirmButton().click()
    await flushPromises()

    expect(wrapper.get('[data-testid="cancel-error"]').text()).toContain('Nada foi alterado')
    expect(wrapper.find('[data-testid="payment-failed-notice"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="cancelled-now-notice"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('PAYMENT_FAILED: cancelar sem resposta e já EXPIRED no reload não mostra erro', async () => {
    const expired = paymentFailed({ status: 'EXPIRED', paymentUpdateUrl: null, hasAccess: false })
    mockGetMySubscription.mockReset().mockResolvedValueOnce(paymentFailed()).mockResolvedValue(expired)
    mockCancelSubscription.mockRejectedValue(new Error('timeout'))

    const wrapper = mountAttached()
    await flushPromises()

    await wrapper.get('[data-testid="cancel-button"]').trigger('click')
    await flushPromises()
    confirmButton().click()
    await flushPromises()

    expect(wrapper.find('[data-testid="cancel-error"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="cancelled-now-notice"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('SUSPENDED usa o fluxo de ex-assinante: "Reativar assinatura" abre o Checkout', async () => {
    mockGetMySubscription.mockResolvedValue(
      trial({ planType: 'MONTHLY', status: 'SUSPENDED', daysRemaining: 0, hasAccess: false }),
    )
    mockCreateCheckout.mockResolvedValue({ url: 'https://checkout.stripe.com/c/pay/cs_test_3' })

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.get('[data-testid="subscription-suspended-badge"]').text()).toBe('Assinatura suspensa')
    const button = wrapper.get('[data-testid="subscribe-button"]')
    expect(button.text()).toBe('Reativar assinatura')
    await button.trigger('click')
    await flushPromises()

    expect(assign).toHaveBeenCalledWith('https://checkout.stripe.com/c/pay/cs_test_3')
  })

  it('assinando de novo a partir de SUSPENDED continua recarregando até ficar ativa', async () => {
    vi.useFakeTimers()
    try {
      mockRoute.query = { status: 'sucesso' }
      const suspended = trial({ planType: 'MONTHLY', status: 'SUSPENDED', daysRemaining: 0, hasAccess: false })
      mockGetMySubscription
        .mockReset()
        .mockResolvedValueOnce(suspended)
        .mockResolvedValueOnce(suspended)
        .mockResolvedValue(trial({ planType: 'MONTHLY', daysRemaining: 0 }))

      const wrapper = mountView()
      await flushPromises()
      await vi.advanceTimersByTimeAsync(2000)
      await vi.advanceTimersByTimeAsync(2000)
      await vi.advanceTimersByTimeAsync(10000)

      expect(mockGetMySubscription).toHaveBeenCalledTimes(3)
      expect(wrapper.get('[data-testid="subscription-active-badge"]').text()).toBe('Assinatura ativa')
      wrapper.unmount()
    } finally {
      vi.useRealTimers()
    }
  })
})
