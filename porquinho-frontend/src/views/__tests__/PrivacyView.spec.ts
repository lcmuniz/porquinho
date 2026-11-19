import { describe, it, expect } from 'vitest'
import { mount, RouterLinkStub } from '@vue/test-utils'
import PrivacyView from '../PrivacyView.vue'

describe('PrivacyView', () => {
  it('cita a Anthropic como operadora das descrições das transações (Story 5.8)', () => {
    const wrapper = mount(PrivacyView, { global: { stubs: { RouterLink: RouterLinkStub } } })

    const section = wrapper
      .findAll('section')
      .find((s) => s.find('h2').text().includes('Compartilhamento de Dados'))

    expect(section?.text()).toContain('Anthropic')
    expect(section?.text()).toContain('descrições das transações')
  })

  it('mostra a data fixa da última mudança do texto, não a de hoje', () => {
    const wrapper = mount(PrivacyView, { global: { stubs: { RouterLink: RouterLinkStub } } })

    expect(wrapper.text()).toContain('Última atualização: 19 de novembro de 2026')
  })
})
