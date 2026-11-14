import { describe, it, expect } from 'vitest'
import { balanceDomain, valueTicks, yOf } from '../chart'

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
