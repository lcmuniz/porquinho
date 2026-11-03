import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockGet = vi.fn()
const mockPost = vi.fn()
const mockDelete = vi.fn()

vi.mock('../api', () => ({
  api: {
    get: (...a: unknown[]) => mockGet(...a),
    post: (...a: unknown[]) => mockPost(...a),
    delete: (...a: unknown[]) => mockDelete(...a),
  },
}))

import { createCard, deleteCard, listCards, listDeletedCards, restoreCard } from '../cardService'

const card = {
  id: 'c1',
  cardName: 'Nubank Roxinho',
  bankName: 'Nubank',
  cardLimit: 5000,
  dueDay: 10,
  createdAt: '2026-11-02T12:00:00Z',
}

describe('cardService', () => {
  beforeEach(() => {
    mockGet.mockReset().mockResolvedValue({ data: [card] })
    mockPost.mockReset().mockResolvedValue({ data: card })
    mockDelete.mockReset().mockResolvedValue({ status: 204 })
  })

  it('listCards faz GET em /api/v1/credit-cards', async () => {
    await expect(listCards()).resolves.toEqual([card])
    expect(mockGet).toHaveBeenCalledWith('/api/v1/credit-cards')
  })

  it('createCard faz POST em /api/v1/credit-cards com os dados', async () => {
    const input = { cardName: 'Nubank Roxinho', bankName: 'Nubank', cardLimit: 5000, dueDay: 10 }
    await expect(createCard(input)).resolves.toEqual(card)
    expect(mockPost).toHaveBeenCalledWith('/api/v1/credit-cards', input)
  })

  it('deleteCard faz DELETE em /api/v1/credit-cards/{id}', async () => {
    await deleteCard('c1')
    expect(mockDelete).toHaveBeenCalledWith('/api/v1/credit-cards/c1')
  })

  it('listDeletedCards faz GET em /api/v1/credit-cards com status=deleted', async () => {
    await expect(listDeletedCards()).resolves.toEqual([card])
    expect(mockGet).toHaveBeenCalledWith('/api/v1/credit-cards', { params: { status: 'deleted' } })
  })

  it('restoreCard faz POST em /api/v1/credit-cards/{id}/restore', async () => {
    await expect(restoreCard('c1')).resolves.toEqual(card)
    expect(mockPost).toHaveBeenCalledWith('/api/v1/credit-cards/c1/restore')
  })
})
