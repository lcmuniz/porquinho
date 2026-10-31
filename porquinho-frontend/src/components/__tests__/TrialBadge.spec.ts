import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import TrialBadge from '../subscription/TrialBadge.vue'
import type { Subscription } from '@/types/subscription'

function makeSubscription(overrides: Partial<Subscription> = {}): Subscription {
  return {
    planType: 'TRIAL',
    status: 'ACTIVE',
    trialStartDate: '2026-10-31T12:00:00Z',
    trialEndDate: '2026-11-30T12:00:00Z',
    nextBillingDate: null,
    cancellationDate: null,
    daysRemaining: 30,
    hasAccess: true,
    ...overrides,
  }
}

describe('TrialBadge', () => {
  it('mostra os dias restantes no plural', () => {
    const wrapper = mount(TrialBadge, { props: { subscription: makeSubscription() } })

    const badge = wrapper.get('[data-testid="trial-badge"]')
    expect(badge.text()).toBe('Trial: 30 dias restantes')
    expect(badge.attributes('role')).toBe('status')
    expect(badge.classes()).toContain('rounded-full')
  })

  it('usa o singular com 1 dia restante', () => {
    const wrapper = mount(TrialBadge, {
      props: { subscription: makeSubscription({ daysRemaining: 1 }) },
    })

    expect(wrapper.get('[data-testid="trial-badge"]').text()).toBe('Trial: 1 dia restante')
  })

  it('mostra "Trial expirado" quando o trial venceu', () => {
    const wrapper = mount(TrialBadge, {
      props: { subscription: makeSubscription({ daysRemaining: 0, hasAccess: false }) },
    })

    expect(wrapper.get('[data-testid="trial-badge"]').text()).toBe('Trial expirado')
  })

  it('não renderiza para planos que não são trial', () => {
    const wrapper = mount(TrialBadge, {
      props: { subscription: makeSubscription({ planType: 'MONTHLY' }) },
    })

    expect(wrapper.find('[data-testid="trial-badge"]').exists()).toBe(false)
  })

  it('mostra "Assinatura ativa" para plano mensal ativo', () => {
    const wrapper = mount(TrialBadge, {
      props: { subscription: makeSubscription({ planType: 'MONTHLY', daysRemaining: 0 }) },
    })

    expect(wrapper.get('[data-testid="subscription-active-badge"]').text()).toBe('Assinatura ativa')
  })

  // Story 2.4
  it('mostra "Assinatura cancelada (ativa até dd/mm/aaaa)" dentro do período pago', () => {
    const wrapper = mount(TrialBadge, {
      props: {
        subscription: makeSubscription({
          planType: 'MONTHLY',
          status: 'CANCELLED',
          daysRemaining: 0,
          // 01/12 01:00Z ainda é 30/11 em Brasília
          nextBillingDate: '2026-12-01T01:00:00Z',
          cancellationDate: '2026-11-10T12:00:00Z',
        }),
      },
    })

    expect(wrapper.get('[data-testid="subscription-cancelled-badge"]').text()).toBe(
      'Assinatura cancelada (ativa até 30/11/2026)',
    )
    expect(wrapper.find('[data-testid="subscription-active-badge"]').exists()).toBe(false)
  })

  it('mostra "Assinatura expirada" para EXPIRED e para cancelada após o fim do período', () => {
    const expired = mount(TrialBadge, {
      props: {
        subscription: makeSubscription({
          planType: 'MONTHLY',
          status: 'EXPIRED',
          daysRemaining: 0,
          hasAccess: false,
        }),
      },
    })
    const cancelledOver = mount(TrialBadge, {
      props: {
        subscription: makeSubscription({
          planType: 'MONTHLY',
          status: 'CANCELLED',
          daysRemaining: 0,
          hasAccess: false,
          nextBillingDate: '2026-10-01T12:00:00Z',
        }),
      },
    })

    expect(expired.get('[data-testid="subscription-expired-badge"]').text()).toBe('Assinatura expirada')
    expect(cancelledOver.get('[data-testid="subscription-expired-badge"]').text()).toBe(
      'Assinatura expirada',
    )
    expect(cancelledOver.find('[data-testid="subscription-cancelled-badge"]').exists()).toBe(false)
  })
})
