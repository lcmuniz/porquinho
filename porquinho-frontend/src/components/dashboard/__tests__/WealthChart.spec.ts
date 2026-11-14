import { describe, it, expect, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import WealthChart from '../WealthChart.vue'
import type { WealthPoint } from '@/types/dashboard'

const plain = (s: string) => s.replace(/\s/g, ' ')

// 12 meses até 2026-11-30: A 1000 desde antes, B 500 há 2 meses, A 1500 há 1 mês
const POINTS: WealthPoint[] = [
  { date: '2025-11-30', balance: 1000 },
  { date: '2026-09-30', balance: 1500 },
  { date: '2026-10-30', balance: 2000 },
  { date: '2026-11-30', balance: 2000 },
]

function mountChart(points: WealthPoint[] = POINTS) {
  return mount(WealthChart, { props: { points }, attachTo: document.body })
}

const trendText = (wrapper: ReturnType<typeof mountChart>) =>
  plain(wrapper.get('[data-testid="wealth-trend"]').text())

/** Pares x,y de cada comando do caminho. */
function coords(d: string): number[][] {
  return [...d.matchAll(/(-?[\d.]+),(-?[\d.]+)/g)].map((m) => [Number(m[1]), Number(m[2])])
}

afterEach(() => {
  document.body.innerHTML = ''
})

describe('WealthChart', () => {
  it('começa em 3 meses: o ponto inicial é o patrimônio no início do período e a tendência sobe', () => {
    const wrapper = mountChart()

    expect(wrapper.get('[data-testid="wealth-range-3"]').attributes('aria-pressed')).toBe('true')
    // início 2026-08-30 vale 1000 (último ponto até lá), depois 1500, 2000 e 2000 hoje
    expect(wrapper.findAll('[data-testid^="wealth-point-"]')).toHaveLength(4)
    const trend = wrapper.get('[data-testid="wealth-trend"]')
    expect(trend.classes()).toContain('text-green-700')
    expect(plain(trend.text())).toBe('+R$ 1.000,00 em 3 meses')
    expect(wrapper.get('[data-testid="wealth-line"]').attributes('stroke')).toBe('#9333EA')
  })

  it('trocar para 12 e 6 meses filtra no cliente', async () => {
    const wrapper = mountChart([{ date: '2025-11-30', balance: 3000 }, ...POINTS.slice(1)])

    await wrapper.get('[data-testid="wealth-range-12"]').trigger('click')
    expect(wrapper.findAll('[data-testid^="wealth-point-"]')).toHaveLength(4)
    expect(trendText(wrapper)).toBe('-R$ 1.000,00 em 12 meses')
    expect(wrapper.get('[data-testid="wealth-trend"]').classes()).toContain('text-red-600')

    await wrapper.get('[data-testid="wealth-range-6"]').trigger('click')
    // início 2026-05-30 vale 3000
    expect(trendText(wrapper)).toBe('-R$ 1.000,00 em 6 meses')
    expect(wrapper.get('[data-testid="wealth-range-6"]').attributes('aria-pressed')).toBe('true')
  })

  it('o início do período prende o dia ao fim do mês', async () => {
    // 2026-05-31 menos 3 meses = 2026-02-28
    const wrapper = mountChart([
      { date: '2025-05-31', balance: 100 },
      { date: '2026-02-28', balance: 200 },
      { date: '2026-03-01', balance: 300 },
      { date: '2026-05-31', balance: 300 },
    ])
    expect(wrapper.findAll('[data-testid^="wealth-point-"]')).toHaveLength(3)
    expect(trendText(wrapper)).toBe('+R$ 100,00 em 3 meses')
  })

  it('variação abaixo de 1% é estável, em amarelo e com texto', () => {
    const wrapper = mountChart([
      { date: '2025-11-30', balance: 1000 },
      { date: '2026-11-30', balance: 1005 },
    ])
    const trend = wrapper.get('[data-testid="wealth-trend"]')
    expect(trend.classes()).toContain('text-yellow-700')
    expect(plain(trend.text())).toBe('+R$ 5,00 em 3 meses')
  })

  it('tudo zero é estável', () => {
    const wrapper = mountChart([
      { date: '2025-11-30', balance: 0 },
      { date: '2026-11-30', balance: 0 },
    ])
    expect(wrapper.get('[data-testid="wealth-trend"]').classes()).toContain('text-yellow-700')
  })

  it('sem histórico mostra o aviso no lugar da linha e esconde a tendência', () => {
    const wrapper = mountChart([])
    expect(wrapper.get('[data-testid="wealth-empty"]').text()).toBe(
      'Ainda não há histórico suficiente',
    )
    expect(wrapper.find('[data-testid="wealth-line"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="wealth-trend"]').exists()).toBe(false)
  })

  it('saldo negativo: linha do zero tracejada', () => {
    const wrapper = mountChart([
      { date: '2025-11-30', balance: 100 },
      { date: '2026-11-20', balance: -200 },
      { date: '2026-11-30', balance: -200 },
    ])
    expect(wrapper.get('[data-testid="wealth-zero-line"]').attributes('stroke-dasharray')).toBe(
      '4 3',
    )
    expect(trendText(wrapper)).toBe('-R$ 300,00 em 3 meses')
  })

  it('a curva é monotônica: não passa acima nem abaixo dos pontos reais', () => {
    const wrapper = mountChart([
      { date: '2025-11-30', balance: 0 },
      { date: '2026-09-01', balance: 0 },
      { date: '2026-09-02', balance: 1000 },
      { date: '2026-10-15', balance: 1000 },
      { date: '2026-11-30', balance: 400 },
    ])
    const ys = wrapper
      .findAll('[data-testid^="wealth-point-"]')
      .map((c) => Number(c.attributes('cy')))
    const all = coords(wrapper.get('[data-testid="wealth-line"]').attributes('d')!).map(
      ([, y]) => y!,
    )
    for (const y of all) {
      expect(y).toBeGreaterThanOrEqual(Math.min(...ys) - 1e-9)
      expect(y).toBeLessThanOrEqual(Math.max(...ys) + 1e-9)
    }
  })

  it('anima uma vez na montagem: a classe sai no fim e trocar o período não a devolve', async () => {
    const wrapper = mountChart()
    const line = wrapper.get('[data-testid="wealth-line"]')
    expect(line.classes()).toContain('wealth-draw')
    expect(line.attributes('pathLength')).toBe('1')

    await line.trigger('animationend')
    expect(line.classes()).not.toContain('wealth-draw')

    await wrapper.get('[data-testid="wealth-range-12"]').trigger('click')
    expect(wrapper.get('[data-testid="wealth-line"]').classes()).not.toContain('wealth-draw')
  })

  it('tooltip com data e valor no hover', async () => {
    const wrapper = mountChart()
    await wrapper.get('[data-testid="wealth-point-1"]').trigger('mouseenter')
    const tooltip = plain(wrapper.get('[data-testid="wealth-tooltip"]').text())
    expect(tooltip).toContain('30/09/2026')
    expect(tooltip).toContain('R$ 1.500,00')

    await wrapper.get('[data-testid="wealth-chart-svg"]').trigger('mouseleave')
    expect(wrapper.find('[data-testid="wealth-tooltip"]').exists()).toBe(false)
  })

  it('só o ponto ativo aparece; os outros ficam transparentes', async () => {
    const wrapper = mountChart()
    const point = (i: number) => wrapper.get(`[data-testid="wealth-point-${i}"]`)
    expect(point(1).attributes('fill')).toBe('transparent')

    await point(1).trigger('mouseenter')
    expect(point(1).attributes('fill')).toBe('#9333EA')
    expect(point(2).attributes('fill')).toBe('transparent')
  })

  it('o tooltip abre para dentro do card nas pontas e centraliza no meio', async () => {
    // 2025-11-30 a 2026-11-30 em 12 meses: pontos no início, no meio e no fim
    const wrapper = mountChart([
      { date: '2025-11-30', balance: 100 },
      { date: '2026-05-30', balance: 200 },
      { date: '2026-11-30', balance: 300 },
    ])
    await wrapper.get('[data-testid="wealth-range-12"]').trigger('click')
    const align = async (i: number) => {
      await wrapper.get(`[data-testid="wealth-point-${i}"]`).trigger('mouseenter')
      return wrapper.get('[data-testid="wealth-tooltip"]').classes()
    }
    expect(await align(0)).toContain('translate-x-0')
    expect(await align(1)).toContain('-translate-x-1/2')
    expect(await align(2)).toContain('-translate-x-full')
  })

  it('teclado: tabindex móvel começa no ponto mais recente; setas e Home/End movem o foco e o tooltip', async () => {
    const wrapper = mountChart()
    const point = (i: number) => wrapper.get(`[data-testid="wealth-point-${i}"]`)
    expect(point(3).attributes('tabindex')).toBe('0')
    expect(point(0).attributes('tabindex')).toBe('-1')

    await point(3).trigger('focus')
    await point(3).trigger('keydown', { key: 'Home' })
    expect(document.activeElement).toBe(point(0).element)
    expect(point(0).attributes('tabindex')).toBe('0')
    expect(plain(wrapper.get('[data-testid="wealth-tooltip"]').text())).toContain('30/08/2026')

    await point(0).trigger('keydown', { key: 'ArrowRight' })
    expect(document.activeElement).toBe(point(1).element)
    await point(1).trigger('keydown', { key: 'End' })
    expect(document.activeElement).toBe(point(3).element)
    expect(point(0).attributes('aria-label')).toContain('30/08/2026')
  })

  it('a animação respeita prefers-reduced-motion', async () => {
    const source = (await import('../WealthChart.vue?raw')).default as string
    expect(source).toMatch(
      /prefers-reduced-motion: reduce\)\s*{\s*\.wealth-draw\s*{[^}]*animation: none/,
    )
  })
})
