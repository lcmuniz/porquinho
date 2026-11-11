import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import AllocationHistoryChart from '../AllocationHistoryChart.vue'
import type { CategoryBudgetMonth } from '@/types/budget'

const month = (m: string, allocated: number, spent: number): CategoryBudgetMonth => ({
  month: m,
  allocated,
  spent,
  remaining: allocated - spent,
  status: allocated - spent < 0 ? 'OVERSPENT' : 'HEALTHY',
})

describe('AllocationHistoryChart', () => {
  it('desenha alocado (roxo, contínuo) e gasto (vermelho, tracejado) com um ponto por mês e legenda em texto', () => {
    const wrapper = mount(AllocationHistoryChart, {
      props: { months: [month('2026-09', 100, 50), month('2026-10', 100, 120), month('2026-11', 200, 0)] },
    })
    const allocated = wrapper.get('[data-testid="chart-line-allocated"]')
    const spent = wrapper.get('[data-testid="chart-line-spent"]')
    expect(allocated.attributes('stroke')).toBe('#7c3aed')
    expect(allocated.attributes('stroke-dasharray')).toBeUndefined()
    expect(spent.attributes('stroke')).toBe('#DC2626')
    expect(spent.attributes('stroke-dasharray')).toBe('6 4')
    expect(allocated.attributes('points')!.split(' ')).toHaveLength(3)
    // gasto 0 fica na base; o maior valor (200) no topo
    const ys = (el: typeof spent) => el.attributes('points')!.split(' ').map((p) => Number(p.split(',')[1]))
    expect(ys(spent)[2]).toBe(280 - 32)
    expect(ys(allocated)[2]).toBe(16)
    expect(wrapper.get('[data-testid="chart-legend"]').text()).toMatch(/Alocado\s*Gasto/)
    expect(wrapper.get('[data-testid="chart-y-axis"]').text()).toContain('R$')
    expect(wrapper.get('[data-testid="chart-x-axis"]').text()).toContain('nov')
  })

  it('com muitos meses mostra até 6 rótulos no eixo X, sempre com o mais novo', () => {
    const months = Array.from({ length: 14 }, (_, i) => {
      const d = new Date(Date.UTC(2025, 9 + i, 1))
      return month(`${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}`, 100, 10)
    })
    const wrapper = mount(AllocationHistoryChart, { props: { months } })
    const labels = wrapper.findAll('[data-testid="chart-x-axis"] text')
    expect(labels.length).toBeLessThanOrEqual(6)
    expect(labels[labels.length - 1]!.text()).toContain('nov')
  })
})
