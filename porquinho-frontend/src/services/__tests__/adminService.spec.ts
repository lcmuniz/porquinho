import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockGet = vi.fn()

vi.mock('../api', () => ({
  api: { get: (...a: unknown[]) => mockGet(...a) },
}))

import { getAdminAccess, getSystemHealth } from '../adminService'

describe('adminService', () => {
  beforeEach(() => {
    mockGet.mockReset()
  })

  it('getAdminAccess lê /api/v1/admin/access', async () => {
    mockGet.mockResolvedValue({ status: 200, data: { admin: true } })
    await expect(getAdminAccess()).resolves.toEqual({ admin: true })
    expect(mockGet).toHaveBeenCalledWith('/api/v1/admin/access')
  })

  it('getSystemHealth lê /api/v1/admin/health', async () => {
    mockGet.mockResolvedValue({ status: 200, data: { generatedAt: 'x' } })
    await expect(getSystemHealth()).resolves.toEqual({ generatedAt: 'x' })
    expect(mockGet).toHaveBeenCalledWith('/api/v1/admin/health')
  })
})
