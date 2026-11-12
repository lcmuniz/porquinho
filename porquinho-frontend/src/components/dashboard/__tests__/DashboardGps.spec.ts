import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, RouterLinkStub } from '@vue/test-utils'
import DashboardGps from '../DashboardGps.vue'

// As Camadas 1 (Story 7.3) e 2 (Story 7.5) buscam no onMounted; aqui ficam carregando
vi.mock('@/services/dashboardService', () => ({
  getOverview: () => new Promise(() => {}),
  getTactical: () => new Promise(() => {}),
}))

const global = { stubs: { RouterLink: RouterLinkStub } }

describe('DashboardGps', () => {
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
      expect(section.classes()).toContain('py-12')
    }
    expect(sections[0]!.find('[data-testid="overview-loading"]').exists()).toBe(true)
    expect(sections[1]!.find('[data-testid="tactical-loading"]').exists()).toBe(true)
    expect(sections[2]!.text()).toContain('Em breve')
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
})
