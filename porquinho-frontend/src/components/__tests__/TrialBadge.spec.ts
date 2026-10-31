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
})
