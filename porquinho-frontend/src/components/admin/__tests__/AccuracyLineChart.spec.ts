import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import AccuracyLineChart from '../AccuracyLineChart.vue'

const format = (v: number) => `${v} %`

function mountChart(series: { label: string; color: string; values: (number | null)[] }[]) {
  return mount(AccuracyLineChart, {
    props: { months: ['06/26', '07/26', '08/26', '09/26'], series, label: 'Precisão', testId: 'c', format },
  })
}

describe('AccuracyLineChart', () => {
  it('desenha uma linha por banco com a cor e a legenda', () => {
    const wrapper = mountChart([
      { label: 'Nubank', color: '#7c3aed', values: [90, 80, 85, 88] },
      { label: 'Itaú', color: '#ea580c', values: [70, 72, 74, 76] },
    ])
    const series = wrapper.findAll('[data-testid="c-series"]')
    expect(series).toHaveLength(2)
    expect(series[0]!.get('path').attributes('stroke')).toBe('#7c3aed')
    expect(series[0]!.findAll('circle')).toHaveLength(4)
    const dash = series.map((g) => g.get('path').attributes('stroke-dasharray'))
    expect(dash[0]).not.toBe(dash[1])
    const legend = wrapper.findAll('[data-testid="c-legend"] line').map((l) => l.attributes('stroke-dasharray'))
    expect(legend).toEqual(dash)
    expect(wrapper.get('[data-testid="c-legend"]').text()).toContain('Nubank')
    expect(wrapper.get('[data-testid="c-legend"]').text()).toContain('Itaú')
  })

  it('um mês null interrompe a linha', () => {
    const wrapper = mountChart([{ label: 'Nubank', color: '#7c3aed', values: [90, null, 85, 88] }])
    const d = wrapper.get('[data-testid="c-series"] path').attributes('d') ?? ''
    expect(d.match(/M/g)).toHaveLength(2)
    expect(d.match(/L/g)).toHaveLength(1)
    expect(wrapper.findAll('[data-testid="c-series"] circle')).toHaveLength(3)
  })

  it('tabela acessível com um valor por mês e "sem dados" no null', () => {
    const wrapper = mountChart([{ label: 'Nubank', color: '#7c3aed', values: [90, null, 85, 88] }])
    const table = wrapper.get('[data-testid="c-table"]')
    expect(table.element.parentElement!.classList).toContain('sr-only')
    expect(table.findAll('thead th').map((th) => th.text())).toEqual(['Banco', '06/26', '07/26', '08/26', '09/26'])
    expect(table.get('tbody tr').text()).toContain('sem dados')
    expect(wrapper.get('svg').attributes('role')).toBe('img')
    expect(wrapper.get('svg').attributes('aria-label')).toBe('Precisão. Os valores de cada ponto estão na tabela a seguir.')
  })

  it('sem nenhum dado mostra o texto vazio', () => {
    const wrapper = mountChart([{ label: 'Nubank', color: '#7c3aed', values: [null, null, null, null] }])
    expect(wrapper.get('[data-testid="c-empty"]').text()).toBe('Nenhuma classificação nos últimos 6 meses')
    expect(wrapper.find('svg').exists()).toBe(false)
  })

  it('max, tickFormat e seriesHeader trocam o eixo e a tabela', () => {
    const wrapper = mount(AccuracyLineChart, {
      props: {
        months: ['01/26', '02/26'],
        series: [{ label: 'Usuários', color: '#7c3aed', values: [0, 400] }],
        label: 'Crescimento',
        testId: 'c',
        format: (v: number) => String(v),
        max: 400,
        tickFormat: (t: number) => `${t} usuários`,
        seriesHeader: 'Série',
      },
    })
    const ticks = wrapper.findAll('[data-testid="c"] > g > text').map((t) => t.text())
    expect(ticks).toEqual(['0 usuários', '200 usuários', '400 usuários'])
    const circles = wrapper.findAll('[data-testid="c-series"] circle')
    // 400 de 400 fica no topo (y = 10), 0 na base (y = 176)
    expect(circles.map((c) => c.attributes('cy'))).toEqual(['176', '10'])
    expect(wrapper.get('[data-testid="c-table"] thead th').text()).toBe('Série')
  })

  it('sem max o eixo continua de 0 a 100 %', () => {
    const wrapper = mountChart([{ label: 'Nubank', color: '#7c3aed', values: [100, 50, 0, 0] }])
    expect(wrapper.findAll('[data-testid="c"] > g > text').map((t) => t.text())).toEqual(['0 %', '50 %', '100 %'])
    expect(wrapper.get('[data-testid="c-series"] circle').attributes('cy')).toBe('10')
  })
})
