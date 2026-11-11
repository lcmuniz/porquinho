import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import PlannedVsActualChart from '../PlannedVsActualChart.vue'

describe('PlannedVsActualChart', () => {
  it('duas barras por categoria (alocado roxo claro, gasto roxo escuro), proporcionais, com legenda em texto', () => {
    const wrapper = mount(PlannedVsActualChart, {
      props: {
        items: [
          { id: 'c1', name: 'Mercado', allocated: 400, spent: 200 },
          { id: 'c2', name: 'Uma categoria com nome bem comprido', allocated: 100, spent: 0 },
        ],
      },
    })
    for (const id of ['c1', 'c2'])
      expect(wrapper.findAll(`[data-testid="chart-bars-${id}"] rect`)).toHaveLength(2)
    const allocated = wrapper.get(
      '[data-testid="chart-bars-c1"] [data-testid="chart-bar-allocated"]',
    )
    const spent = wrapper.get('[data-testid="chart-bars-c1"] [data-testid="chart-bar-spent"]')
    expect(allocated.attributes('fill')).toBe('#8b5cf6')
    expect(spent.attributes('fill')).toBe('#4c1d95')
    expect(Number(spent.attributes('width'))).toBeCloseTo(Number(allocated.attributes('width')) / 2)
    expect(
      Number(
        wrapper
          .get('[data-testid="chart-bars-c2"] [data-testid="chart-bar-spent"]')
          .attributes('width'),
      ),
    ).toBe(0)
    expect(wrapper.get('[data-testid="chart-bars-c2"] text').text()).toBe('Uma categoria com…')
    expect(wrapper.get('[data-testid="chart-legend"]').text()).toMatch(/Alocado\s*Gasto/)
    expect(wrapper.get('[data-testid="chart-x-axis"]').text()).toContain('R$')
  })
})
