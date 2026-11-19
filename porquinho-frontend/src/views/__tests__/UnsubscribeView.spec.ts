import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import UnsubscribeView from '../UnsubscribeView.vue'

const mockUnsubscribe = vi.fn()
let query: Record<string, string> = {}

vi.mock('vue-router', () => ({ useRoute: () => ({ query }) }))
vi.mock('@/services/userPreferencesService', () => ({
  unsubscribeBudgetAlert: (...a: unknown[]) => mockUnsubscribe(...a),
}))

describe('UnsubscribeView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    query = { token: 'abc.def' }
  })

  it('só descadastra no clique e confirma', async () => {
    mockUnsubscribe.mockResolvedValue(undefined)
    const wrapper = mount(UnsubscribeView)
    await flushPromises()
    expect(mockUnsubscribe).not.toHaveBeenCalled()

    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(mockUnsubscribe).toHaveBeenCalledWith('abc.def')
    expect(wrapper.text()).toContain('Você não receberá mais alertas de orçamento por e-mail.')
  })

  it('400: link inválido', async () => {
    mockUnsubscribe.mockRejectedValue({ response: { status: 400 } })
    const wrapper = mount(UnsubscribeView)

    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('link de descadastro é inválido')
    expect(wrapper.find('button').exists()).toBe(false)
  })

  it('outra falha: mantém o botão para tentar de novo', async () => {
    mockUnsubscribe.mockRejectedValue({ request: {} })
    const wrapper = mount(UnsubscribeView)

    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Tente de novo')
    expect(wrapper.find('button').exists()).toBe(true)
  })

  it('sem token: link inválido, sem chamar a API', () => {
    query = {}
    const wrapper = mount(UnsubscribeView)

    expect(wrapper.text()).toContain('link de descadastro é inválido')
    expect(mockUnsubscribe).not.toHaveBeenCalled()
  })
})
