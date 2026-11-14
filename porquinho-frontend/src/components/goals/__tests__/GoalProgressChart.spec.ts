import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import GoalProgressChart from '../GoalProgressChart.vue'
import type { Goal, GoalProgressPoint } from '@/types/goal'

const goal = (extra: Partial<Goal> = {}): Goal => ({
  id: 'g1',
  name: 'Viagem',
  description: null,
  targetAmount: 1000,
  currentAmount: 300,
  targetDate: null,
  monthlyAllocation: null,
  completed: false,
  createdAt: '2026-09-05T12:00:00Z',
  template: null,
  completedAt: null,
  ...extra,
})

const SERIES: GoalProgressPoint[] = [
  { month: '2026-09', amount: 100 },
  { month: '2026-10', amount: 100 },
  { month: '2026-11', amount: 300 },
]

const plain = (s: string) => s.replace(/\s/g, ' ')

function render(g: Goal, points: GoalProgressPoint[] = SERIES) {
  return mount(GoalProgressChart, { props: { goal: g, points }, attachTo: document.body })
}

/** Quantos vértices tem a polyline. */
const count = (points: string | undefined) => (points ?? '').trim().split(' ').length

describe('GoalProgressChart', () => {
  it('meta nova: um ponto, sem linha projetada', () => {
    const w = render(goal({ currentAmount: 0 }), [{ month: '2026-11', amount: 0 }])
    expect(w.findAll('[data-testid^="goal-chart-point-"]')).toHaveLength(1)
    expect(w.find('[data-testid="goal-chart-projected"]').exists()).toBe(false)
  })

  it('desenha a linha real com um ponto por mês e os marcos de 25/50/75/100%', () => {
    const w = render(goal())
    expect(count(w.get('[data-testid="goal-chart-real"]').attributes('points'))).toBe(3)
    for (const m of [25, 50, 75, 100])
      expect(w.find(`[data-testid="goal-chart-milestone-${m}"]`).exists()).toBe(true)
    expect(plain(w.get('[data-testid="goal-chart-milestone-50"]').text())).toContain('R$ 500,00')
  })

  it('projeção: 300 de 1000 com 250/mês chega ao alvo em 3 meses (550, 800, 1000)', () => {
    const w = render(goal({ monthlyAllocation: 250 }))
    const line = w.get('[data-testid="goal-chart-projected"]')
    expect(line.attributes('data-months')).toBe('3')
    // Sem data-alvo, o eixo vai até o fim da projeção: o último ponto fica no topo (o alvo, 100%)
    const last = line.attributes('points')!.split(' ').pop()!.split(',').map(Number)
    const top = Number(w.get('[data-testid="goal-chart-milestone-100"] line').attributes('y1'))
    expect(last[1]).toBeCloseTo(top)
    expect(last[0]).toBeCloseTo(640 - 40)
  })

  it('Story 8.7: meta concluída abaixo do alvo, com alocação, não desenha a linha projetada', () => {
    const w = render(
      goal({ monthlyAllocation: 250, completed: true, completedAt: '2026-11-10T12:00:00Z' }),
    )
    expect(w.find('[data-testid="goal-chart-projected"]').exists()).toBe(false)
    expect(count(w.get('[data-testid="goal-chart-real"]').attributes('points'))).toBe(3)
  })

  it('aumentar a alocação encurta a linha projetada', async () => {
    const w = render(goal({ monthlyAllocation: 250, targetDate: '2027-12-01' }))
    expect(w.get('[data-testid="goal-chart-projected"]').attributes('data-months')).toBe('3')
    await w.setProps({ goal: goal({ monthlyAllocation: 500, targetDate: '2027-12-01' }) })
    expect(w.get('[data-testid="goal-chart-projected"]').attributes('data-months')).toBe('2')
  })

  it.each([
    ['sem alocação', goal({ monthlyAllocation: null })],
    ['alocação 0', goal({ monthlyAllocation: 0 })],
    ['acumulado acima do alvo', goal({ currentAmount: 1200, monthlyAllocation: 100 })],
  ])('%s: só a linha real', (_, g) => {
    const points =
      g.currentAmount > 1000 ? [...SERIES.slice(0, 2), { month: '2026-11', amount: 1200 }] : SERIES
    const w = render(g, points)
    expect(w.find('[data-testid="goal-chart-projected"]').exists()).toBe(false)
    expect(w.find('[data-testid="goal-chart-real"]').exists()).toBe(true)
  })

  it('com data-alvo, o eixo vai até o mês dela e corta a projeção', () => {
    // 100/mês a partir de 300: 7 meses; data-alvo em 2027-01 (2 meses depois de nov)
    const w = render(goal({ monthlyAllocation: 100, targetDate: '2027-01-20' }))
    expect(w.get('[data-testid="goal-chart-projected"]').attributes('data-months')).toBe('2')
    expect(w.text()).toContain('jan. de 27')
  })

  it('tooltip com mês e valor por mouse e por teclado (setas)', async () => {
    const w = render(goal())
    await w.get('[data-testid="goal-chart-point-0"]').trigger('mouseenter')
    expect(plain(w.get('[data-testid="goal-chart-tooltip"]').text())).toBe(
      'setembro de 2026R$ 100,00',
    )
    await w.get('[data-testid="goal-chart-svg"]').trigger('mouseleave')
    expect(w.find('[data-testid="goal-chart-tooltip"]').exists()).toBe(false)

    // Só o mês corrente entra no Tab
    expect(w.get('[data-testid="goal-chart-point-2"]').attributes('tabindex')).toBe('0')
    expect(w.get('[data-testid="goal-chart-point-0"]').attributes('tabindex')).toBe('-1')
    await w.get('[data-testid="goal-chart-point-2"]').trigger('focus')
    expect(plain(w.get('[data-testid="goal-chart-tooltip"]').text())).toBe(
      'novembro de 2026R$ 300,00',
    )
    await w.get('[data-testid="goal-chart-point-2"]').trigger('keydown', { key: 'ArrowLeft' })
    expect(document.activeElement?.getAttribute('data-testid')).toBe('goal-chart-point-1')
    expect(plain(w.get('[data-testid="goal-chart-tooltip"]').text())).toBe(
      'outubro de 2026R$ 100,00',
    )
    w.unmount()
  })
})
