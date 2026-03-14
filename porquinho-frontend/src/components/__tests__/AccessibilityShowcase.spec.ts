import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import AccessibilityShowcase from '../AccessibilityShowcase.vue'

describe('AccessibilityShowcase', () => {
  it('renders without errors', () => {
    const wrapper = mount(AccessibilityShowcase)
    expect(wrapper.exists()).toBe(true)
  })

  it('contains a skip link example section', () => {
    const wrapper = mount(AccessibilityShowcase)
    expect(wrapper.text()).toContain('Skip Link')
  })

  it('contains focus indicators section', () => {
    const wrapper = mount(AccessibilityShowcase)
    expect(wrapper.text()).toContain('Focus')
  })

  it('contains semantic markup section', () => {
    const wrapper = mount(AccessibilityShowcase)
    expect(wrapper.text()).toContain('Semantic')
  })

  it('contains keyboard navigation section', () => {
    const wrapper = mount(AccessibilityShowcase)
    expect(wrapper.text()).toContain('Keyboard')
  })

  it('has proper heading structure', () => {
    const wrapper = mount(AccessibilityShowcase)
    expect(wrapper.find('h2').exists()).toBe(true)
  })
})
