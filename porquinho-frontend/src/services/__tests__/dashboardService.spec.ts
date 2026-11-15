import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockGet = vi.fn()

vi.mock('../api', () => ({
  api: {
    get: (...a: unknown[]) => mockGet(...a),
  },
}))

import { exportDetails, getDetails, getFinancialHealth, getOverview } from '../dashboardService'

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

  it('getDetails manda os filtros como query params do backend', async () => {
    const page = { items: [], page: 1, totalItems: 0, totalPages: 0 }
    mockGet.mockResolvedValue({ data: page })
    const filter = {
      from: '2026-11-01',
      to: '2026-11-30',
      categoryIds: ['c1', 'uncategorized', 'c2'],
      targets: ['account:a1', 'card:k1'],
      q: ' mercado ',
      sort: 'amount' as const,
      dir: 'desc' as const,
    }
    await expect(getDetails(filter, 1)).resolves.toEqual(page)
    const [url, config] = mockGet.mock.calls[0]!
    expect(url).toBe('/api/v1/dashboard/layer3')
    expect((config.params as URLSearchParams).toString()).toBe(
      'from=2026-11-01&to=2026-11-30&sort=amount%2Cdesc&q=mercado&categoryIds=c1&uncategorized=true&categoryIds=c2' +
        '&accountIds=a1&creditCardIds=k1&page=1',
    )
  })

  it('exportDetails pede o CSV como blob, sem página, e devolve a contagem do cabeçalho', async () => {
    const blob = new Blob(['x'])
    mockGet.mockResolvedValue({ data: blob, headers: { 'x-export-count': '42' } })
    const filter = { from: '2026-11-01', to: '2026-11-30', categoryIds: [], targets: [], q: '', sort: 'date' as const, dir: 'desc' as const }
    await expect(exportDetails(filter)).resolves.toEqual({ blob, count: 42 })
    const [url, config] = mockGet.mock.calls[0]!
    expect(url).toBe('/api/v1/dashboard/layer3/export')
    expect(config.responseType).toBe('blob')
    expect((config.params as URLSearchParams).toString()).toBe('from=2026-11-01&to=2026-11-30&sort=date%2Cdesc')
  })

  it('exportDetails manda as opções de parcelas futuras e excluídas só quando ligadas', async () => {
    mockGet.mockResolvedValue({ data: new Blob(['x']), headers: { 'x-export-count': '0' } })
    const filter = { from: '0001-01-01', to: '9999-12-31', categoryIds: [], targets: [], q: '', sort: 'date' as const, dir: 'desc' as const }
    await expect(exportDetails(filter, { includeFutureInstallments: true, includeDeleted: true })).resolves.toMatchObject({
      count: 0,
    })
    expect((mockGet.mock.calls[0]![1].params as URLSearchParams).toString()).toBe(
      'from=0001-01-01&to=9999-12-31&sort=date%2Cdesc&includeFutureInstallments=true&includeDeleted=true',
    )
  })
})
