import { describe, it, expect } from 'vitest'
import { goalPace, goalProjection, monthsBetween, monthsToTarget } from '../goalPace'
import type { Goal } from '@/types/goal'

const goal = (extra: Partial<Goal>): Goal => ({
  id: 'g1',
  name: 'Meta',
  description: null,
  targetAmount: 1000,
  currentAmount: 0,
  targetDate: null,
  monthlyAllocation: 100,
  completed: false,
  createdAt: '2026-11-01T12:00:00Z',
  ...extra,
})

describe('goalPace', () => {
  it('monthsBetween atravessa o ano', () => {
    expect(monthsBetween('2026-11', '2027-02')).toBe(3)
    expect(monthsBetween('2026-11', '2026-09')).toBe(-2)
  })

  it('monthsToTarget arredonda para cima, 0 quando já chegou e null sem alocação', () => {
    expect(monthsToTarget(1000, 300, 250)).toBe(3)
    expect(monthsToTarget(1000, 1200, null)).toBe(0)
    expect(monthsToTarget(1000, 0, 0)).toBeNull()
    expect(monthsToTarget(1000, 0, null)).toBeNull()
  })

  // Alvo 1000, acumulado 0, alocação 100: projeção em 10 meses (2027-09)
  it.each([
    ['2027-09-30', 0, 'on-track'],
    ['2027-12-01', -3, 'on-track'],
    ['2027-08-15', 1, 'late'],
    ['2027-06-15', 3, 'late'],
    ['2027-05-15', 4, 'very-late'],
    ['2027-04-15', 5, 'very-late'],
  ] as const)('data-alvo %s (atraso %s) → %s', (targetDate, _delay, pace) => {
    expect(goalPace(goal({ targetDate }), '2026-11')).toBe(pace)
  })

  it('sem data-alvo, sem alocação ou com o alvo atingido fica em dia', () => {
    expect(goalPace(goal({ targetDate: null }), '2026-11')).toBe('on-track')
    expect(goalPace(goal({ targetDate: '2026-12-01', monthlyAllocation: null }), '2026-11')).toBe(
      'on-track',
    )
    expect(goalPace(goal({ targetDate: '2026-12-01', monthlyAllocation: 0 }), '2026-11')).toBe(
      'on-track',
    )
    expect(goalPace(goal({ targetDate: '2026-12-01', currentAmount: 1000 }), '2026-11')).toBe(
      'on-track',
    )
  })
})

// Story 8.4, mês corrente 2026-11
describe('goalProjection', () => {
  it.each([
    ['sem prazo', {}, { kind: 'month', month: '2027-09', suggestion: null }],
    ['em dia', { targetDate: '2027-10-01' }, { kind: 'month', month: '2027-09', suggestion: null }],
    [
      'atrasada',
      { targetDate: '2027-07-13' },
      { kind: 'month', month: '2027-09', suggestion: { kind: 'monthly', amount: 125 } },
    ],
    [
      'arredondamento no centavo',
      { monthlyAllocation: 50, targetDate: '2027-02-01' },
      { kind: 'month', month: '2028-07', suggestion: { kind: 'monthly', amount: 333.34 } },
    ],
    ['sem alocação', { monthlyAllocation: null }, { kind: 'no-allocation', suggestion: null }],
    [
      'sem alocação com prazo futuro',
      { monthlyAllocation: 0, targetDate: '2027-07-13' },
      { kind: 'no-allocation', suggestion: null },
    ],
    ['atingida', { currentAmount: 1200, targetDate: '2026-01-01' }, { kind: 'reached' }],
    [
      'longe demais',
      { targetAmount: 1_000_000, monthlyAllocation: 0.01 },
      { kind: 'too-far', suggestion: null },
    ],
    [
      'longe demais com prazo',
      { targetAmount: 1_000_000, monthlyAllocation: 0.01, targetDate: '2027-07-13' },
      { kind: 'too-far', suggestion: { kind: 'monthly', amount: 125000 } },
    ],
    [
      'prazo chegou',
      { currentAmount: 400, targetDate: '2026-11-30' },
      { kind: 'month', month: '2027-05', suggestion: { kind: 'lump-sum', amount: 600 } },
    ],
    [
      'prazo passou sem alocação',
      { currentAmount: 400, monthlyAllocation: null, targetDate: '2026-09-30' },
      { kind: 'no-allocation', suggestion: { kind: 'lump-sum', amount: 600 } },
    ],
  ] as const)('%s', (_name, extra, expected) => {
    expect(goalProjection(goal(extra), '2026-11')).toEqual(expected)
  })

  it('alocação 125 com prazo 2027-07: projeção no mês do prazo e sem sugestão', () => {
    expect(
      goalProjection(goal({ monthlyAllocation: 125, targetDate: '2027-07-13' }), '2026-11'),
    ).toEqual({ kind: 'month', month: '2027-07', suggestion: null })
  })

  it.each([
    [1000, 0, 50, '2027-02-01'],
    [1000, 0, 100, '2027-07-13'],
    [999.99, 123.45, 10, '2028-03-01'],
    [5000, 1, 1, '2027-01-15'],
    [7, 0, 0.01, '2026-12-01'],
  ])(
    'com o aporte sugerido o mês projetado = mês da data-alvo (alvo %s, acumulado %s, alocação %s, prazo %s)',
    (targetAmount, currentAmount, monthlyAllocation, targetDate) => {
      const base = goal({ targetAmount, currentAmount, monthlyAllocation, targetDate })
      const before = goalProjection(base, '2026-11')
      if (before.kind === 'reached' || before.suggestion?.kind !== 'monthly')
        throw new Error('sem sugestão')
      const after = goalProjection(
        { ...base, monthlyAllocation: before.suggestion.amount },
        '2026-11',
      )
      expect(after).toEqual({ kind: 'month', month: targetDate.slice(0, 7), suggestion: null })
      expect(goalPace({ ...base, monthlyAllocation: before.suggestion.amount }, '2026-11')).toBe(
        'on-track',
      )
    },
  )
})
