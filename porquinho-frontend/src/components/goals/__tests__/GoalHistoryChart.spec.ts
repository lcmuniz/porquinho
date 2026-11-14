import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import GoalHistoryChart from '../GoalHistoryChart.vue'
import type { GoalHistoryEntry } from '@/types/goal'

const entries: GoalHistoryEntry[] = [
  { date: '2026-01-10', contributionAmount: null, amount: 0 },
  { date: '2026-01-15', contributionAmount: 100, amount: 100 },
  { date: '2026-02-01', contributionAmount: null, amount: 100 },
  { date: '2026-03-05', contributionAmount: 200, amount: 300 },
]

const xs = (points: string) => points.split(' ').map((p) => Number(p.split(',')[0]))

describe('GoalHistoryChart', () => {
  it('linha verde do acumulado e marcador só nos aportes', () => {
    const wrapper = mount(GoalHistoryChart, { props: { entries } })
    const line = wrapper.get('[data-testid="chart-line"]')
    expect(line.attributes('stroke')).toBe('#10B981')
    expect(xs(line.attributes('points')!)).toHaveLength(4)
    const markers = wrapper.findAll('[data-testid="chart-contribution-marker"]')
    expect(markers).toHaveLength(2)
    expect(markers.every((m) => m.attributes('fill') === '#10B981')).toBe(true)
  })

  it('eixo X em escala de tempo: distância proporcional às datas e rótulos por mês', () => {
    const wrapper = mount(GoalHistoryChart, { props: { entries } })
    const [a, b, c, d] = xs(wrapper.get('[data-testid="chart-line"]').attributes('points')!)
    // 10/01 → 15/01 (5 dias) é bem menor que 01/02 → 05/03 (32 dias)
    expect(b! - a!).toBeLessThan(d! - c!)
    expect((b! - a!) / (d! - a!)).toBeCloseTo(5 / 54, 5)
    const ticks = wrapper.findAll('[data-testid="chart-x-tick"]').map((t) => t.text())
    expect(ticks).toHaveLength(3)
    expect(ticks[0]).toMatch(/jan/)
    expect(ticks[2]).toMatch(/mar/)
  })

  it('uma entrada só fica no centro, com um rótulo', () => {
    const wrapper = mount(GoalHistoryChart, {
      props: { entries: [{ date: '2026-03-15', contributionAmount: null, amount: 0 }] },
    })
    expect(xs(wrapper.get('[data-testid="chart-line"]').attributes('points')!)).toEqual([104 + (640 - 104 - 24) / 2])
    expect(wrapper.findAll('[data-testid="chart-x-tick"]')).toHaveLength(1)
    expect(wrapper.findAll('[data-testid="chart-contribution-marker"]')).toHaveLength(0)
  })
})
