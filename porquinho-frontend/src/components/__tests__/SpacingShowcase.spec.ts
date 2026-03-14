import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import SpacingShowcase from '../SpacingShowcase.vue'

describe('SpacingShowcase', () => {
  it('renders without errors', () => {
    const wrapper = mount(SpacingShowcase)
    expect(wrapper.exists()).toBe(true)
  })

  it('displays spacing scale values', () => {
    const wrapper = mount(SpacingShowcase)
    const text = wrapper.text()
    expect(text).toContain('4px')
    expect(text).toContain('8px')
    expect(text).toContain('12px')
    expect(text).toContain('16px')
    expect(text).toContain('24px')
    expect(text).toContain('32px')
    expect(text).toContain('48px')
    expect(text).toContain('64px')
  })

  it('displays common spacing pattern examples', () => {
    const wrapper = mount(SpacingShowcase)
    const text = wrapper.text()
    expect(text).toContain('p-6')
    expect(text).toContain('gap-4')
    expect(text).toContain('py-12')
  })

  it('displays border radius variants', () => {
    const wrapper = mount(SpacingShowcase)
    const text = wrapper.text()
    expect(text).toContain('rounded-sm')
    expect(text).toContain('rounded-md')
    expect(text).toContain('rounded-lg')
    expect(text).toContain('rounded-xl')
    expect(text).toContain('rounded-full')
  })
})
