import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockGet = vi.fn()

vi.mock('../api', () => ({
  api: {
    get: (...a: unknown[]) => mockGet(...a),
  },
}))

import { getFinancialHealth, getOverview } from '../dashboardService'

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

  it('getOverview faz GET em /api/v1/dashboard/layer1 e devolve o data', async () => {
    const overview = { totalBalance: 1500, categories: [], mainGoal: null, monthSpent: 0, monthAllocated: 0, projectedBalance: 1500 }
    mockGet.mockResolvedValue({ data: overview })
    await expect(getOverview()).resolves.toEqual(overview)
    expect(mockGet).toHaveBeenCalledWith('/api/v1/dashboard/layer1')
  })
})
