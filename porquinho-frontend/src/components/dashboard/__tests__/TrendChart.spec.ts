import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import TrendChart from '../TrendChart.vue'
import type { CategoryTrend } from '@/types/dashboard'

// 12 meses, de 2025-12 a 2026-11; `recent` vale nos 3 últimos, `old` nos 9 primeiros
function trend(id: string, old: number, recent: number): CategoryTrend {
  return {
    categoryId: id,
    name: id.toUpperCase(),
    months: Array.from({ length: 12 }, (_, i) => ({
      month: i === 0 ? '2025-12' : `2026-${String(i).padStart(2, '0')}`,
      spent: i < 9 ? old : recent,
    })),
  }
}

const lineKeys = (w: ReturnType<typeof mount>) =>
  w.findAll('[data-testid^="trend-line-"]').map((g) => g.attributes('data-testid'))

describe('TrendChart', () => {
  it('padrão 3 meses: 5 maiores do período, 3 pontos cada, sem quem não gastou', () => {
    const trends = [
      trend('a', 0, 60),
      trend('b', 0, 50),
      trend('c', 0, 40),
      trend('d', 0, 30),
      trend('e', 0, 20),
      trend('f', 0, 10),
      trend('g', 1000, 0),
    ]
    const w = mount(TrendChart, { props: { trends } })

    expect(w.get('[data-testid="trend-range-3"]').attributes('aria-pressed')).toBe('true')
    expect(lineKeys(w)).toEqual(['a', 'b', 'c', 'd', 'e'].map((k) => `trend-line-${k}`))
    expect(w.findAll('[data-testid="trend-line-a"] circle')).toHaveLength(3)
    expect(w.findAll('[data-testid="trend-legend"] li')).toHaveLength(5)
  })

  it('12 meses: as maiores passam a ser pelo gasto somado no ano', async () => {
    const trends = [trend('a', 0, 60), trend('g', 1000, 0)]
    const w = mount(TrendChart, { props: { trends } })

    await w.get('[data-testid="trend-range-12"]').trigger('click')

    expect(lineKeys(w)).toEqual(['trend-line-g', 'trend-line-a'])
    expect(w.findAll('[data-testid="trend-line-g"] circle')).toHaveLength(12)
  })

  it('tabela de valores por mês acompanha o período e as linhas do gráfico', () => {
    const w = mount(TrendChart, { props: { trends: [trend('a', 0, 60), trend('b', 0, 50)] } })

    const table = w.get('[data-testid="trend-table"]')
    expect(table.findAll('thead th')).toHaveLength(4)
    const rows = table.findAll('tbody tr')
    expect(rows.map((r) => r.get('th').text())).toEqual(['A', 'B'])
    expect(rows[0]!.findAll('td').map((td) => td.text().replace(/\s/g, ' '))).toEqual(
      Array(3).fill('R$ 60,00'),
    )
  })

  it('sem gasto no período mostra a mensagem', () => {
    const w = mount(TrendChart, { props: { trends: [trend('g', 1000, 0)] } })

    expect(w.get('[data-testid="trend-empty"]').text()).toBe('Nenhum gasto nos últimos 3 meses')
  })
})
