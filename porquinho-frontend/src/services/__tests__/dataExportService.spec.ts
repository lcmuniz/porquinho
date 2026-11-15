import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockGet = vi.fn()
const mockPost = vi.fn()

vi.mock('../api', () => ({
  api: {
    get: (...a: unknown[]) => mockGet(...a),
    post: (...a: unknown[]) => mockPost(...a),
  },
}))

import { getExportStatus, requestExport } from '../dataExportService'

const processing = {
  id: 'e1',
  status: 'PROCESSING',
  createdAt: '2026-11-15T12:00:00Z',
  expiresAt: '2026-11-16T12:00:00Z',
  downloadUrl: null,
}

describe('dataExportService', () => {
  beforeEach(() => {
    mockGet.mockReset()
    mockPost.mockReset().mockResolvedValue({ status: 201, data: processing })
  })

  it('requestExport faz POST em /api/v1/data-export com a senha no corpo', async () => {
    await expect(requestExport('senha-forte-123')).resolves.toEqual(processing)
    expect(mockPost).toHaveBeenCalledWith('/api/v1/data-export', { password: 'senha-forte-123' })
  })

  it('getExportStatus devolve null no 204', async () => {
    mockGet.mockResolvedValue({ status: 204, data: '' })
    await expect(getExportStatus()).resolves.toBeNull()
    expect(mockGet).toHaveBeenCalledWith('/api/v1/data-export/status')
  })
})
