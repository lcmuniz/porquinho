import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockGet = vi.fn()

vi.mock('../api', () => ({
  api: {
    get: (...a: unknown[]) => mockGet(...a),
  },
}))

import { getFinancialHealth } from '../dashboardService'

const health = {
  status: 'ATTENTION',
  healthyCategories: 9,
  lowCategories: 0,
  overspentCategories: 1,
  negativeAccounts: 0,
  goalsBehind: 0,
}

describe('dashboardService', () => {
  beforeEach(() => {
    mockGet.mockReset().mockResolvedValue({ data: health })
  })

  it('getFinancialHealth faz GET em /api/v1/dashboard/health e devolve o data', async () => {
    await expect(getFinancialHealth()).resolves.toEqual(health)
    expect(mockGet).toHaveBeenCalledWith('/api/v1/dashboard/health')
  })
})
