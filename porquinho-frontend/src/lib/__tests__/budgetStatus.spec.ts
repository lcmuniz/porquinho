import { describe, it, expect } from 'vitest'
import { computeStatus } from '../budgetStatus'

describe('computeStatus', () => {
  it.each([
    [100, -0.01, 'OVERSPENT'],
    [100, 20, 'LOW'],
    [100, 20.01, 'HEALTHY'],
    [0.3, 0.06, 'LOW'],
    [0, 0, 'LOW'],
  ] as const)('alocado %s, restante %s → %s', (allocated, remaining, status) => {
    expect(computeStatus(allocated, remaining)).toBe(status)
  })
})
