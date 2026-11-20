import { describe, it, expect, vi, afterEach, beforeEach } from 'vitest'
import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import DashboardGps from '../DashboardGps.vue'

// As Camadas 1 (Story 7.3) e 2 (Story 7.5) buscam no onMounted; aqui ficam carregando
// O drill-down da Camada 2 importa o transactionService; sem mock, api.ts exige o .env (CI)
vi.mock('@/services/transactionService', () => ({ listTransactions: vi.fn(), updateTransaction: vi.fn() }))

const mockGetOverview = vi.fn()
const mockGetTactical = vi.fn()
const mockGetDetails = vi.fn()
vi.mock('@/services/dashboardService', () => ({
  getOverview: () => mockGetOverview(),
  getTactical: () => mockGetTactical(),
  // Camada 3: coberta no DashboardLayer3.spec (a mesma promessa para as duas listas)
  getLargestSpent: () => mockGetDetails(),
  getDrilldown: () => mockGetDetails(),
  exportDetails: vi.fn(),
}))
vi.mock('@/services/accountService', () => ({ listAccounts: async () => [] }))
vi.mock('@/services/cardService', () => ({ listCards: async () => [] }))
vi.mock('@/services/categoryService', () => ({ listCategories: async () => [] }))

const global = { stubs: { RouterLink: RouterLinkStub } }

describe('DashboardGps', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockGetOverview.mockReset().mockReturnValue(new Promise(() => {}))
    mockGetTactical.mockReset().mockReturnValue(new Promise(() => {}))
    mockGetDetails.mockReset().mockReturnValue(new Promise(() => {}))
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('mostra as 3 camadas na ordem, cada uma uma section rotulada pelo seu h2', () => {
    const wrapper = mount(DashboardGps, { global })

    const sections = wrapper.findAll('section')
    expect(sections.map((s) => s.attributes('id'))).toEqual([
      'layer-overview',
      'layer-tactical',
      'layer-details',
    ])
    expect(sections.map((s) => s.get('h2').text())).toEqual([
      'Visão geral',
      'Visão tática',
      'Detalhes',
    ])
    for (const section of sections) {
      const h2 = section.get('h2')
      expect(section.attributes('aria-labelledby')).toBe(h2.attributes('id'))
      expect(section.classes()).toContain('py-8')
      expect(h2.find('button').exists()).toBe(false)
    }
    // Critique de 20/11/2026: só "Visão tática" tem ajuda (fora do h2, para não entrar no nome da seção), sem "camada"
    expect(sections.map((s) => s.find('[data-testid^="help-layer-"]').exists())).toEqual([false, true, false])
    const help = sections[1]!.get('[data-testid="help-layer-tactical"]')
    expect(help.attributes('aria-label')).toBe('O que é Visão tática?')
    expect(sections[0]!.find('[data-testid="overview-loading"]').exists()).toBe(true)
    expect(sections[1]!.find('[data-testid="tactical-loading"]').exists()).toBe(true)
    expect(sections[2]!.find('[data-testid="details-view"]').exists()).toBe(true)
  })

  it('cada botão "Ir para" rola até a sua camada, sem forçar o behavior', async () => {
    const wrapper = mount(DashboardGps, { attachTo: document.body, global })
    const scrolled: string[] = []
    const scrollIntoView = vi.fn(function (this: Element) {
      scrolled.push(this.id)
    })
    Element.prototype.scrollIntoView = scrollIntoView

    const nav = wrapper.get('nav[aria-label="Ir para a camada"]')
    const buttons = nav.findAll('button')
    expect(buttons.map((b) => b.text())).toEqual(['Visão geral', 'Visão tática', 'Detalhes'])
    for (const button of buttons) {
      await button.trigger('click')
      expect(document.activeElement?.id).toBe(scrolled[scrolled.length - 1])
    }

    expect(scrolled).toEqual(['layer-overview', 'layer-tactical', 'layer-details'])
    for (const call of scrollIntoView.mock.calls) expect(call).toEqual([])
    wrapper.unmount()
  })

  it('uma edição salva na Camada 3 recarrega as Camadas 1 e 2 e avisa a view (Q1=B)', async () => {
    const wrapper = mount(DashboardGps, { global })
    await flushPromises()
    expect(mockGetOverview).toHaveBeenCalledTimes(1)
    expect(mockGetTactical).toHaveBeenCalledTimes(1)

    wrapper.findComponent({ name: 'DashboardLayer3' }).vm.$emit('transaction-saved')
    await flushPromises()

    expect(mockGetOverview).toHaveBeenCalledTimes(2)
    expect(mockGetTactical).toHaveBeenCalledTimes(2)
    expect(wrapper.emitted('transaction-saved')).toHaveLength(1)
  })

  it('a recarga pós-edição mantém as Camadas 1 e 2 na tela, sem "Carregando…" (retro Epic 7 A4)', async () => {
    mockGetOverview.mockRejectedValue(new Error('rede'))
    mockGetTactical.mockRejectedValue(new Error('rede'))
    const wrapper = mount(DashboardGps, { global })
    await flushPromises()
    mockGetOverview.mockReturnValue(new Promise(() => {}))
    mockGetTactical.mockReturnValue(new Promise(() => {}))

    wrapper.findComponent({ name: 'DashboardLayer3' }).vm.$emit('transaction-saved')
    await flushPromises()

    expect(mockGetOverview).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[data-testid="overview-loading"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="tactical-loading"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="overview-error"]').exists()).toBe(true)
  })

  it('avisa "loaded" só quando as 3 camadas terminam a primeira carga (retro Epic 7 A4)', async () => {
    mockGetOverview.mockRejectedValue(new Error('rede'))
    mockGetTactical.mockRejectedValue(new Error('rede'))
    let finishDetails!: (value: unknown) => void
    mockGetDetails.mockReturnValue(new Promise((resolve) => (finishDetails = resolve)))
    const wrapper = mount(DashboardGps, { global })
    await flushPromises()
    expect(wrapper.emitted('loaded')).toBeUndefined()

    finishDetails([])
    await flushPromises()
    expect(wrapper.emitted('loaded')).toHaveLength(1)

    wrapper.findComponent({ name: 'DashboardLayer3' }).vm.$emit('transaction-saved')
    await flushPromises()
    expect(wrapper.emitted('loaded')).toHaveLength(1)
  })
})
