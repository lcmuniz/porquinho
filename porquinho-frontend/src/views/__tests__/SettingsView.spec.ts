import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'
import SettingsView from '../SettingsView.vue'

const mockGetMyPreferences = vi.fn()
const mockUpdateMyPreferences = vi.fn()
vi.mock('@/services/userPreferencesService', () => ({
  getMyPreferences: () => mockGetMyPreferences(),
  updateMyPreferences: (...args: unknown[]) => mockUpdateMyPreferences(...args),
}))

function mountView() {
  return mount(SettingsView, { global: { stubs: { RouterLink: RouterLinkStub } } })
}

describe('SettingsView', () => {
  beforeEach(() => {
    mockGetMyPreferences.mockReset().mockResolvedValue({ budgetAlertEmail: true })
    mockUpdateMyPreferences.mockReset()
  })

  it('tem os links da conta e a zona de perigo', async () => {
    const wrapper = mountView()
    await flushPromises()

    const targets = wrapper.findAllComponents(RouterLinkStub).map((l) => l.props('to'))
    expect(targets).toEqual(['/assinatura', '/auth/sessions', '/auth/data-export', '/auth/account-deletion'])
    expect(wrapper.text()).toContain('Excluir minha conta')
  })

  describe('Pré-Epic 10: alerta de orçamento por e-mail', () => {
    it('mostra o checkbox ligado e desligar salva a preferência', async () => {
      mockUpdateMyPreferences.mockResolvedValue({ budgetAlertEmail: false })
      const wrapper = mountView()
      await flushPromises()

      const checkbox = wrapper.get<HTMLInputElement>('[data-testid="budget-alert-email"]')
      expect(checkbox.element.checked).toBe(true)
      expect(wrapper.text()).toContain('Receber alerta de orçamento por e-mail')

      await checkbox.setValue(false)
      await flushPromises()

      expect(mockUpdateMyPreferences).toHaveBeenCalledWith({ budgetAlertEmail: false })
      expect(checkbox.element.checked).toBe(false)
    })

    it('falha ao salvar volta o checkbox e mostra o erro', async () => {
      mockUpdateMyPreferences.mockRejectedValue(new Error('boom'))
      const wrapper = mountView()
      await flushPromises()

      const checkbox = wrapper.get<HTMLInputElement>('[data-testid="budget-alert-email"]')
      await checkbox.setValue(false)
      await flushPromises()

      expect(checkbox.element.checked).toBe(true)
      expect(wrapper.find('[data-testid="budget-alert-email-error"]').exists()).toBe(true)
    })

    it('esconde o checkbox se a leitura falhar', async () => {
      mockGetMyPreferences.mockRejectedValue({ response: { status: 402 } })
      const wrapper = mountView()
      await flushPromises()

      expect(wrapper.find('[data-testid="budget-alert-email"]').exists()).toBe(false)
    })
  })
})
