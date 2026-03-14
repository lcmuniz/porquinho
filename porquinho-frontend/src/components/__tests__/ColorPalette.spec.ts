import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ColorPalette from '../ColorPalette.vue'

describe('ColorPalette', () => {
  it('renders without errors', () => {
    const wrapper = mount(ColorPalette)
    expect(wrapper.exists()).toBe(true)
  })

  it('displays all color sections', () => {
    const wrapper = mount(ColorPalette)
    const text = wrapper.text()
    expect(text).toContain('Purple Scale')
    expect(text).toContain('Gray Scale')
    expect(text).toContain('State Colors')
    expect(text).toContain('Semantic Colors')
    expect(text).toContain('Chart Colors')
    expect(text).toContain('WCAG')
  })

  it('displays all 6 chart color swatches', () => {
    const wrapper = mount(ColorPalette)
    const text = wrapper.text()
    expect(text).toContain('chart-1 Purple')
    expect(text).toContain('chart-2 Cyan')
    expect(text).toContain('chart-3 Amber')
    expect(text).toContain('chart-4 Green')
    expect(text).toContain('chart-5 Pink')
    expect(text).toContain('chart-6 Violet')
  })

  it('displays all 8 gray scale swatches', () => {
    const wrapper = mount(ColorPalette)
    const text = wrapper.text()
    expect(text).toContain('gray-50')
    expect(text).toContain('gray-100')
    expect(text).toContain('gray-200')
    expect(text).toContain('gray-300')
    expect(text).toContain('gray-400')
    expect(text).toContain('gray-500')
    expect(text).toContain('gray-700')
    expect(text).toContain('gray-900')
  })

  it('displays state color tokens (success, warning, error)', () => {
    const wrapper = mount(ColorPalette)
    const text = wrapper.text()
    expect(text).toContain('success')
    expect(text).toContain('warning')
    expect(text).toContain('error')
  })

  it('displays all WCAG contrast rows', () => {
    const wrapper = mount(ColorPalette)
    const text = wrapper.text()
    expect(text).toContain('Gray 900 on White')
    expect(text).toContain('Gray 700 on White')
    expect(text).toContain('Gray 500 on White')
    expect(text).toContain('Purple 600 on White')
    expect(text).toContain('Green 600 on White')
    expect(text).toContain('Red 600 on White')
  })

  it('shows PASSA AA for all contrast rows', () => {
    const wrapper = mount(ColorPalette)
    const passElements = wrapper.findAll('span').filter(s => s.text() === 'PASSA AA')
    expect(passElements.length).toBe(6)
  })

  it('displays purple scale swatches including info token', () => {
    const wrapper = mount(ColorPalette)
    const text = wrapper.text()
    expect(text).toContain('purple-50')
    expect(text).toContain('purple-100')
    expect(text).toContain('purple-500')
    expect(text).toContain('purple-700')
    expect(text).toContain('info')
  })
})
