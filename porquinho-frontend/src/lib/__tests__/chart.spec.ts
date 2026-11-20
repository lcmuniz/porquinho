import { describe, it, expect } from 'vitest'
import { axisBRL, balanceDomain, labelPad, valueTicks, xTickCount, yOf } from '../chart'

describe('balanceDomain', () => {
  it('inclui o zero quando há saldo negativo', () => {
    expect(balanceDomain([-50, -10])).toEqual([-50, 0])
  })

  it('dá folga a um valor único e à série vazia', () => {
    expect(balanceDomain([100, 100])).toEqual([90, 110])
    expect(balanceDomain([])).toEqual([-1, 1])
  })
})

describe('valueTicks', () => {
  it('espaça igual e arredonda para centavos sem repetir', () => {
    expect(valueTicks([0, 100], 4)).toEqual([0, 25, 50, 75, 100])
    expect(valueTicks([10, 10.01], 4)).toEqual([10, 10.01])
  })
})

describe('yOf', () => {
  it('põe o máximo no topo e o mínimo na base da área', () => {
    const pad = { top: 10, right: 0, bottom: 20, left: 0 }
    expect(yOf(100, [0, 100], 130, pad)).toBe(10)
    expect(yOf(0, [0, 100], 130, pad)).toBe(110)
  })
})

describe('rótulos no gráfico estreito', () => {
  it('axisBRL compacta só quando pedido', () => {
    expect(axisBRL(33000, true).replace(/\s/g, ' ')).toBe('R$ 33 mil')
    expect(axisBRL(-12500, true).replace(/\s/g, ' ')).toBe('-R$ 12,5 mil')
    expect(axisBRL(33000, false).replace(/\s/g, ' ')).toBe('R$ 33.000,00')
  })

  it('labelPad cabe o maior rótulo e xTickCount não sobrepõe nem passa do máximo', () => {
    expect(labelPad(['R$ 0', 'R$ 33 mil'])).toBe(Math.ceil(9 * 7.5) + 12)
    expect(xTickCount(204, 10, 4)).toBe(2)
    expect(xTickCount(520, 10, 4)).toBe(4)
    expect(xTickCount(40, 10, 4)).toBe(1)
  })
})
