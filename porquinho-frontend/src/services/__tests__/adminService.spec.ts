import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockGet = vi.fn()

vi.mock('../api', () => ({
  api: { get: (...a: unknown[]) => mockGet(...a) },
}))

import {
  exportAiMetrics,
  exportImportErrors,
  getAdminAccess,
  getAiBankDetail,
  getAiMetrics,
  getImportBankDetail,
  getImportMetrics,
  getSystemHealth,
  getUserMetrics,
  exportUserMetrics,
} from '../adminService'

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

  it('getImportMetrics manda os filtros para /api/v1/admin/imports', async () => {
    mockGet.mockResolvedValue({ status: 200, data: { banks: [] } })
    await expect(getImportMetrics({ from: '2026-11-01', to: '2026-11-17', bank: 'NUBANK' })).resolves.toEqual({ banks: [] })
    expect(mockGet).toHaveBeenCalledWith('/api/v1/admin/imports', {
      params: { from: '2026-11-01', to: '2026-11-17', bank: 'NUBANK' },
    })
  })

  it('getImportBankDetail lê /api/v1/admin/imports/detail', async () => {
    mockGet.mockResolvedValue({ status: 200, data: { bank: 'ITAU' } })
    await expect(getImportBankDetail({ bank: 'ITAU', status: 'FAILED' })).resolves.toEqual({ bank: 'ITAU' })
    expect(mockGet).toHaveBeenCalledWith('/api/v1/admin/imports/detail', { params: { bank: 'ITAU', status: 'FAILED' } })
  })

  it('exportImportErrors baixa o CSV como blob', async () => {
    const blob = new Blob(['x'])
    mockGet.mockResolvedValue({ status: 200, data: blob })
    await expect(exportImportErrors({ status: 'ALL' })).resolves.toBe(blob)
    expect(mockGet).toHaveBeenCalledWith('/api/v1/admin/imports/export', {
      params: { status: 'ALL' },
      responseType: 'blob',
    })
  })

  it('getAiMetrics manda os filtros para /api/v1/admin/ai', async () => {
    mockGet.mockResolvedValue({ status: 200, data: { banks: [] } })
    await expect(getAiMetrics({ from: '2026-11-01', bank: 'ITAU' })).resolves.toEqual({ banks: [] })
    expect(mockGet).toHaveBeenCalledWith('/api/v1/admin/ai', { params: { from: '2026-11-01', bank: 'ITAU' } })
  })

  it('getAiBankDetail lê /api/v1/admin/ai/detail', async () => {
    mockGet.mockResolvedValue({ status: 200, data: { bank: 'NUBANK' } })
    await expect(getAiBankDetail({ bank: 'NUBANK' })).resolves.toEqual({ bank: 'NUBANK' })
    expect(mockGet).toHaveBeenCalledWith('/api/v1/admin/ai/detail', { params: { bank: 'NUBANK' } })
  })

  it('exportAiMetrics baixa o CSV como blob', async () => {
    const blob = new Blob(['x'])
    mockGet.mockResolvedValue({ status: 200, data: blob })
    await expect(exportAiMetrics({ to: '2026-11-17' })).resolves.toBe(blob)
    expect(mockGet).toHaveBeenCalledWith('/api/v1/admin/ai/export', {
      params: { to: '2026-11-17' },
      responseType: 'blob',
    })
  })

  it('getUserMetrics lê /api/v1/admin/users', async () => {
    mockGet.mockResolvedValue({ status: 200, data: { total: 3 } })
    await expect(getUserMetrics()).resolves.toEqual({ total: 3 })
    expect(mockGet).toHaveBeenCalledWith('/api/v1/admin/users')
  })

  it('exportUserMetrics baixa o CSV como blob', async () => {
    const blob = new Blob(['x'])
    mockGet.mockResolvedValue({ status: 200, data: blob })
    await expect(exportUserMetrics()).resolves.toBe(blob)
    expect(mockGet).toHaveBeenCalledWith('/api/v1/admin/users/export', { responseType: 'blob' })
  })
})
