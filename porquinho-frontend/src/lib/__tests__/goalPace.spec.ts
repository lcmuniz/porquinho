import { describe, it, expect } from 'vitest'
import { goalPace, monthsBetween, monthsToTarget } from '../goalPace'
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
