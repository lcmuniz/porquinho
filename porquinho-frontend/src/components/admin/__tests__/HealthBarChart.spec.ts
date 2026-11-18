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

describe('HealthBarChart configurável (Story 10.2)', () => {
  it('padrões antigos: rótulo a cada 4, cabeçalho "Hora" e texto vazio de 24 h', () => {
    const many = Array.from({ length: 8 }, (_, i) => ({ label: `h${i}`, value: i }))
    const wrapper = mount(HealthBarChart, {
      props: { points: many, label: 'x', color: '#000', testId: 'chart', format: String, hasData: true },
    })
    expect(wrapper.findAll('svg text').map((t) => t.text()).filter((t) => t.startsWith('h'))).toEqual(['h0', 'h4'])
    expect(wrapper.get('thead').text()).toContain('Hora')
    const empty = mount(HealthBarChart, {
      props: { points: [], label: 'x', color: '#000', testId: 'chart', format: String, hasData: false },
    })
    expect(empty.get('[data-testid="chart-empty"]').text()).toBe('Sem dados nas últimas 24 horas')
  })

  it('aceita texto vazio, rótulo a cada N, cabeçalho e cor por barra', () => {
    const wrapper = mount(HealthBarChart, {
      props: {
        points: [
          { label: 'Nubank', value: 10, color: '#dc2626' },
          { label: 'Itaú', value: 2 },
        ],
        label: 'Taxa por banco',
        color: '#16a34a',
        testId: 'chart',
        format: String,
        hasData: true,
        labelEvery: 1,
        columnLabel: 'Banco',
      },
    })
    expect(wrapper.findAll('rect').map((r) => r.attributes('fill'))).toEqual(['#dc2626', '#16a34a'])
    expect(wrapper.get('thead').text()).toContain('Banco')
    expect(wrapper.get('svg').attributes('aria-label')).toContain('de cada banco')
    const empty = mount(HealthBarChart, {
      props: { points: [], label: 'x', color: '#000', testId: 'chart', format: String, hasData: false, emptyText: 'Nada' },
    })
    expect(empty.get('[data-testid="chart-empty"]').text()).toBe('Nada')
  })
})
