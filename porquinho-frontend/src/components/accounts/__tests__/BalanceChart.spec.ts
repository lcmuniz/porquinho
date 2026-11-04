import { describe, it, expect, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import BalanceChart from '../BalanceChart.vue'
import type { BalancePoint } from '@/types/account'

const plain = (s: string) => s.replace(/\s/g, ' ')

const POINTS: BalancePoint[] = [
  { date: '2026-10-01T15:00:00Z', balance: 1000, event: 'initial' },
  { date: '2026-10-10T15:00:00Z', balance: 1200, event: 'edit' },
  { date: '2026-10-20T15:00:00Z', balance: 1000, event: 'transfer' },
  { date: '2026-11-03T15:00:00Z', balance: 1050, event: 'reconciliation' },
]

const NOW = '2026-11-04T15:00:00Z'

function mountChart(points: BalancePoint[] = POINTS, periodStart: string | null = null) {
  return mount(BalanceChart, { props: { points, periodStart, now: NOW }, attachTo: document.body })
}

const xOfMarker = (d: string) => Number(/^M([-\d.]+),/.exec(d)![1])

afterEach(() => {
  document.body.innerHTML = ''
})

describe('BalanceChart', () => {
  it('desenha a linha, um marcador por evento e a legenda dos tipos presentes', () => {
    const wrapper = mountChart()
    expect(wrapper.find('[data-testid="chart-line"]').exists()).toBe(true)
    expect(wrapper.findAll('[data-testid^="chart-point-"]')).toHaveLength(4)
    const legend = plain(wrapper.get('[data-testid="chart-legend"]').text())
    expect(legend).toContain('Saldo inicial')
    expect(legend).toContain('Edição')
    expect(legend).toContain('Transferência')
    expect(legend).toContain('Reconciliação')
    expect(legend).not.toContain('Transação')
  })

  it('cada tipo tem cor e forma próprias', () => {
    const wrapper = mountChart()
    const points = wrapper.findAll('[data-testid^="chart-point-"]')
    const fills = new Set(points.map((p) => p.attributes('fill')))
    expect(fills.size).toBe(4)
    // Quadrado (edição) e losango (transferência) são caminhos com retas; círculo usa arcos
    expect(points[0]!.attributes('d')).toContain('a')
    expect(points[1]!.attributes('d')).toContain('h')
    expect(points[2]!.attributes('d')).toContain('L')
  })

  it('mostra datas no eixo X e valores em R$ no eixo Y', () => {
    const wrapper = mountChart()
    const x = wrapper.get('[data-testid="chart-x-axis"]').text()
    expect(x).toContain('01/10/2026')
    // O eixo vai até agora
    expect(x).toContain('04/11/2026')
    expect(plain(wrapper.get('[data-testid="chart-y-axis"]').text())).toContain('R$ 1.200,00')
  })

  it('ao passar o mouse num ponto mostra data, saldo e tipo; ao sair, esconde', async () => {
    const wrapper = mountChart()
    expect(wrapper.find('[data-testid="chart-tooltip"]').exists()).toBe(false)
    await wrapper.get('[data-testid="chart-point-3"]').trigger('mouseenter')
    const tooltip = plain(wrapper.get('[data-testid="chart-tooltip"]').text())
    expect(tooltip).toContain('03/11/2026')
    expect(tooltip).toContain('R$ 1.050,00')
    expect(tooltip).toContain('Reconciliação')
    await wrapper.get('[data-testid="balance-chart-svg"]').trigger('mouseleave')
    expect(wrapper.find('[data-testid="chart-tooltip"]').exists()).toBe(false)
  })

  it('só um ponto entra no Tab (o mais recente); rótulo acessível e o foco mostra o detalhe', async () => {
    const wrapper = mountChart()
    const tabindexes = wrapper
      .findAll('[data-testid^="chart-point-"]')
      .map((p) => p.attributes('tabindex'))
    expect(tabindexes).toEqual(['-1', '-1', '-1', '0'])
    const point = wrapper.get('[data-testid="chart-point-1"]')
    expect(plain(point.attributes('aria-label')!)).toBe('10/10/2026, R$ 1.200,00, Edição')
    await point.trigger('focus')
    expect(plain(wrapper.get('[data-testid="chart-tooltip"]').text())).toContain('Edição')
    await point.trigger('blur')
    expect(wrapper.find('[data-testid="chart-tooltip"]').exists()).toBe(false)
  })

  it('setas, Home e End movem o foco e o tabindex entre os pontos', async () => {
    const wrapper = mountChart()
    const at = (i: number) => wrapper.get(`[data-testid="chart-point-${i}"]`).element
    ;(at(0) as SVGElement).focus()
    await wrapper.get('[data-testid="chart-point-0"]').trigger('keydown', { key: 'ArrowRight' })
    expect(document.activeElement).toBe(at(1))
    await wrapper.get('[data-testid="chart-point-1"]').trigger('keydown', { key: 'End' })
    expect(document.activeElement).toBe(at(3))
    await wrapper.get('[data-testid="chart-point-3"]').trigger('keydown', { key: 'ArrowLeft' })
    expect(document.activeElement).toBe(at(2))
    await wrapper.get('[data-testid="chart-point-2"]').trigger('keydown', { key: 'Home' })
    expect(document.activeElement).toBe(at(0))
    expect(wrapper.get('[data-testid="chart-point-0"]').attributes('tabindex')).toBe('0')
    expect(wrapper.get('[data-testid="chart-point-3"]').attributes('tabindex')).toBe('-1')
  })

  it('a linha é em degraus e o último saldo segue até agora', () => {
    const wrapper = mountChart([POINTS[0]!, POINTS[1]!])
    const d = wrapper.get('[data-testid="chart-line"]').attributes('d')!
    const x1 = xOfMarker(wrapper.get('[data-testid="chart-point-1"]').attributes('d')!)
    // M x0,y0  H x1 V y1  H (agora = borda direita: 640 - 16)
    expect(d).toMatch(/^M[-\d.]+,[-\d.]+H[-\d.]+V[-\d.]+H624$/)
    expect(Number(/H([-\d.]+)V/.exec(d)![1])).toBeCloseTo(x1 + 4.5, 5)
  })

  it('o ponto anterior ao período fica preso ao início; o eixo começa no início do período', () => {
    const wrapper = mountChart(
      [
        { date: '2026-08-01T15:00:00Z', balance: 1050, event: 'reconciliation' },
        { date: '2026-10-20T15:00:00Z', balance: 900, event: 'edit' },
      ],
      '2026-10-05T15:00:00Z',
    )
    const first = wrapper.get('[data-testid="chart-point-0"]')
    // Triângulo: M x,y-... — o x do topo é o início do eixo (PAD.left = 104)
    expect(xOfMarker(first.attributes('d')!)).toBe(104)
    expect(first.attributes('aria-label')).toContain('01/08/2026')
    const x = wrapper.get('[data-testid="chart-x-axis"]').text()
    expect(x).toContain('05/10/2026')
    expect(x).toContain('04/11/2026')
  })

  it('um único ponto antes do período vira uma linha reta do início até agora', () => {
    const wrapper = mountChart(
      [{ date: '2026-09-01T15:00:00Z', balance: 1050, event: 'reconciliation' }],
      '2026-10-05T15:00:00Z',
    )
    expect(wrapper.findAll('[data-testid^="chart-point-"]')).toHaveLength(1)
    expect(wrapper.get('[data-testid="chart-line"]').attributes('d')).toMatch(/^M104,[-\d.]+H624$/)
  })

  it('evento desconhecido usa o marcador padrão e o próprio nome, sem quebrar', async () => {
    const wrapper = mountChart([
      POINTS[0]!,
      { date: '2026-10-10T15:00:00Z', balance: 1, event: 'novo' as BalancePoint['event'] },
    ])
    const point = wrapper.get('[data-testid="chart-point-1"]')
    expect(point.attributes('fill')).toBe('#6b7280')
    expect(point.attributes('aria-label')).toContain('novo')
    await point.trigger('mouseenter')
    expect(wrapper.get('[data-testid="chart-tooltip"]').text()).toContain('novo')
  })

  it('transação tem forma própria, diferente do saldo inicial', () => {
    const wrapper = mountChart([
      POINTS[0]!,
      { date: '2026-10-10T15:00:00Z', balance: 900, event: 'transaction' },
    ])
    const initial = wrapper.get('[data-testid="chart-point-0"]').attributes('d')!
    const transaction = wrapper.get('[data-testid="chart-point-1"]').attributes('d')!
    expect(initial).toContain('a')
    expect(transaction).not.toContain('a')
    expect(plain(wrapper.get('[data-testid="chart-legend"]').text())).toContain('Transação')
  })

  it('rótulos do eixo Y não se repetem quando a faixa é de centavos', () => {
    const wrapper = mountChart([
      { date: '2026-10-01T15:00:00Z', balance: 100, event: 'initial' },
      { date: '2026-10-02T15:00:00Z', balance: 100.01, event: 'edit' },
    ])
    const labels = wrapper.findAll('[data-testid="chart-y-axis"] text').map((t) => t.text())
    expect(new Set(labels).size).toBe(labels.length)
    expect(labels.length).toBeLessThan(5)
  })

  it('saldo negativo fica abaixo da linha do zero', () => {
    const wrapper = mountChart([
      { date: '2026-10-01T15:00:00Z', balance: 100, event: 'initial' },
      { date: '2026-10-02T15:00:00Z', balance: -50, event: 'edit' },
    ])
    const zeroY = Number(wrapper.get('[data-testid="chart-zero-line"]').attributes('y1'))
    const d = wrapper.get('[data-testid="chart-point-1"]').attributes('d')!
    const pointY = Number(/^M[-\d.]+,([-\d.]+)/.exec(d)![1])
    expect(pointY).toBeGreaterThan(zeroY)
  })

  it('renderiza 1000 pontos em menos de 1 segundo', () => {
    const start = Date.parse('2025-11-01T00:00:00Z')
    const points: BalancePoint[] = Array.from({ length: 1000 }, (_, i) => ({
      date: new Date(start + i * 6 * 3600_000).toISOString(),
      balance: i,
      event: 'edit',
    }))
    const begin = performance.now()
    const wrapper = mountChart(points)
    const elapsed = performance.now() - begin
    expect(wrapper.findAll('[data-testid^="chart-point-"]')).toHaveLength(1000)
    expect(elapsed).toBeLessThan(1000)
  })
})
