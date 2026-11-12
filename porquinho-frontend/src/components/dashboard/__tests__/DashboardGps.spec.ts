import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import DashboardGps from '../DashboardGps.vue'

describe('DashboardGps', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('mostra as 3 camadas na ordem, cada uma uma section rotulada pelo seu h2', () => {
    const wrapper = mount(DashboardGps)

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
      expect(section.text()).toContain('Em breve')
    }
  })

  it('cada botão "Ir para" rola até a sua camada, sem forçar o behavior', async () => {
    const wrapper = mount(DashboardGps, { attachTo: document.body })
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
