import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import TypographyShowcase from '../TypographyShowcase.vue'

describe('TypographyShowcase', () => {
  it('renders without errors', () => {
    const wrapper = mount(TypographyShowcase)
    expect(wrapper.exists()).toBe(true)
  })

  it('displays the type scale from text-xs to text-5xl', () => {
    const wrapper = mount(TypographyShowcase)
    const text = wrapper.text()
    expect(text).toContain('text-xs')
    expect(text).toContain('text-sm')
    expect(text).toContain('text-base')
    expect(text).toContain('text-lg')
    expect(text).toContain('text-xl')
    expect(text).toContain('text-2xl')
    expect(text).toContain('text-3xl')
    expect(text).toContain('text-4xl')
    expect(text).toContain('text-5xl')
  })

  it('displays all four font weights', () => {
    const wrapper = mount(TypographyShowcase)
    const text = wrapper.text()
    expect(text).toContain('400')
    expect(text).toContain('500')
    expect(text).toContain('600')
    expect(text).toContain('700')
  })

  it('displays Dashboard GPS hierarchy labels', () => {
    const wrapper = mount(TypographyShowcase)
    const text = wrapper.text()
    expect(text).toContain('Dashboard GPS')
    expect(text).toContain('text-4xl')
    expect(text).toContain('text-xl')
    expect(text).toContain('text-sm')
  })
})
