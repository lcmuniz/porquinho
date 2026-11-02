import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockGet = vi.fn()

vi.mock('../api', () => ({
  api: {
    get: (...a: unknown[]) => mockGet(...a),
  },
}))

import { getCategorySuggestions, listCategories } from '../categoryService'

describe('categoryService', () => {
  beforeEach(() => {
    mockGet.mockReset().mockResolvedValue({ data: [] })
  })

  it('listCategories faz GET em /api/v1/categories', async () => {
    const category = {
      id: 'c1',
      categoryName: 'Moradia',
      parentCategoryId: 'p1',
      colorHex: '#3B82F6',
      iconName: 'house',
      systemDefault: true,
      createdAt: '2026-11-02T12:00:00Z',
    }
    mockGet.mockResolvedValue({ data: [category] })
    await expect(listCategories()).resolves.toEqual([category])
    expect(mockGet).toHaveBeenCalledWith('/api/v1/categories')
  })

  it('getCategorySuggestions faz GET em /api/v1/categories/suggestions', async () => {
    await expect(getCategorySuggestions()).resolves.toEqual([])
    expect(mockGet).toHaveBeenCalledWith('/api/v1/categories/suggestions')
  })
})
