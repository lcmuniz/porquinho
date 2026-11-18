import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import HealthBarChart from '../HealthBarChart.vue'

const points = [
  { label: '10:00', value: 0 },
  { label: '11:00', value: null },
]

function mountChart(hasData: boolean) {
  return mount(HealthBarChart, {
    props: { points, label: 'Erros por hora', color: '#dc2626', testId: 'chart', format: String, hasData },
  })
}

describe('HealthBarChart', () => {
  it('dia com requisições e zero erros mostra o gráfico, não "sem dados"', () => {
    const wrapper = mountChart(true)
    expect(wrapper.find('[data-testid="chart"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="chart-empty"]').exists()).toBe(false)
  })

  it('tem tabela sr-only com hora e valor', () => {
    const wrapper = mountChart(true)
    const table = wrapper.get('[data-testid="chart-table"]')
    expect(table.classes()).toContain('sr-only')
    const rows = table.findAll('tbody tr').map((r) => r.text())
    expect(rows).toEqual(['10:000', '11:00sem dados'])
    expect(wrapper.get('svg').attributes('aria-label')).toContain('tabela')
  })

  it('sem requisições mostra "sem dados"', () => {
    const wrapper = mountChart(false)
    expect(wrapper.find('[data-testid="chart-empty"]').exists()).toBe(true)
  })
})
