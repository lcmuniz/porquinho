import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockGet = vi.fn()
const mockPost = vi.fn()
const mockPatch = vi.fn()
const mockDelete = vi.fn()

vi.mock('../api', () => ({
  api: {
    get: (...a: unknown[]) => mockGet(...a),
    post: (...a: unknown[]) => mockPost(...a),
    patch: (...a: unknown[]) => mockPatch(...a),
    delete: (...a: unknown[]) => mockDelete(...a),
  },
}))

import {
  createCategory,
  deleteCategory,
  getCategoryOptions,
  getCategorySuggestions,
  getCategoryUsage,
  listCategories,
  updateCategory,
} from '../categoryService'

const category = {
  id: 'c1',
  categoryName: 'Moradia',
  parentCategoryId: 'p1',
  colorHex: '#3B82F6',
  iconName: 'house',
  systemDefault: true,
  createdAt: '2026-11-02T12:00:00Z',
}

describe('categoryService', () => {
  beforeEach(() => {
    mockGet.mockReset().mockResolvedValue({ data: [] })
    mockPost.mockReset().mockResolvedValue({ data: category })
    mockPatch.mockReset().mockResolvedValue({ data: category })
    mockDelete.mockReset().mockResolvedValue({ data: undefined })
  })

  it('listCategories faz GET em /api/v1/categories', async () => {
    mockGet.mockResolvedValue({ data: [category] })
    await expect(listCategories()).resolves.toEqual([category])
    expect(mockGet).toHaveBeenCalledWith('/api/v1/categories')
  })

  it('getCategorySuggestions faz GET em /api/v1/categories/suggestions', async () => {
    await expect(getCategorySuggestions()).resolves.toEqual([])
    expect(mockGet).toHaveBeenCalledWith('/api/v1/categories/suggestions')
  })

  it('getCategoryOptions faz GET em /api/v1/categories/options', async () => {
    const options = { colors: ['#3B82F6'], icons: ['house'] }
    mockGet.mockResolvedValue({ data: options })
    await expect(getCategoryOptions()).resolves.toEqual(options)
    expect(mockGet).toHaveBeenCalledWith('/api/v1/categories/options')
  })

  it('createCategory faz POST em /api/v1/categories com o grupo', async () => {
    const input = { name: 'Pets', colorHex: '#F59E0B', iconName: 'paw-print', parentId: 'p1' }
    await expect(createCategory(input)).resolves.toEqual(category)
    expect(mockPost).toHaveBeenCalledWith('/api/v1/categories', input)
  })

  it('updateCategory faz PATCH só com os campos enviados', async () => {
    await expect(updateCategory('c1', { name: 'Casa' })).resolves.toEqual(category)
    expect(mockPatch).toHaveBeenCalledWith('/api/v1/categories/c1', { name: 'Casa' })
  })

  it('deleteCategory faz DELETE em /api/v1/categories/{id}', async () => {
    await expect(deleteCategory('c1')).resolves.toBeUndefined()
    expect(mockDelete).toHaveBeenCalledWith('/api/v1/categories/c1', undefined)
  })

  it('deleteCategory com destino envia targetCategoryId', async () => {
    await deleteCategory('c1', 'c2')
    expect(mockDelete).toHaveBeenCalledWith('/api/v1/categories/c1', { params: { targetCategoryId: 'c2' } })
  })

  it('getCategoryUsage faz GET em /api/v1/categories/{id}/usage', async () => {
    mockGet.mockResolvedValueOnce({ data: { transactionCount: 3 } })
    await expect(getCategoryUsage('c1')).resolves.toEqual({ transactionCount: 3 })
    expect(mockGet).toHaveBeenCalledWith('/api/v1/categories/c1/usage')
  })
})
