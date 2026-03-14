import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import SkipLink from '../common/SkipLink.vue'

describe('SkipLink', () => {
  it('renders a link element', () => {
    const wrapper = mount(SkipLink, {
      props: { target: '#main-content' },
    })
    expect(wrapper.find('a').exists()).toBe(true)
  })

  it('has correct href pointing to target', () => {
    const wrapper = mount(SkipLink, {
      props: { target: '#main-content' },
    })
    expect(wrapper.find('a').attributes('href')).toBe('#main-content')
  })

  it('has skip-link class for CSS visibility control', () => {
    const wrapper = mount(SkipLink, {
      props: { target: '#main-content' },
    })
    expect(wrapper.find('a').classes()).toContain('skip-link')
  })

  it('renders default slot text', () => {
    const wrapper = mount(SkipLink, {
      props: { target: '#main-content' },
      slots: { default: 'Pular para o conteudo principal' },
    })
    expect(wrapper.text()).toContain('Pular para o conteudo principal')
  })

  it('renders default label when no slot provided', () => {
    const wrapper = mount(SkipLink, {
      props: { target: '#main-content' },
    })
    expect(wrapper.text()).toBeTruthy()
  })
})
