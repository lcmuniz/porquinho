import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockGet = vi.fn()
const mockPost = vi.fn()

vi.mock('../api', () => ({
  api: {
    get: (...a: unknown[]) => mockGet(...a),
    post: (...a: unknown[]) => mockPost(...a),
  },
}))

import { createGoal, listGoals } from '../goalService'

const goal = {
  id: 'g1',
  name: 'Reserva de Emergência',
  description: null,
  targetAmount: 25000,
  currentAmount: 0,
  targetDate: '2027-11-02',
  monthlyAllocation: 2083.34,
  completed: false,
  createdAt: '2026-11-02T12:00:00Z',
}

describe('goalService', () => {
  beforeEach(() => {
    mockGet.mockReset().mockResolvedValue({ data: [goal] })
    mockPost.mockReset().mockResolvedValue({ data: goal })
  })

  it('listGoals faz GET em /api/v1/goals', async () => {
    await expect(listGoals()).resolves.toEqual([goal])
    expect(mockGet).toHaveBeenCalledWith('/api/v1/goals')
  })

  it('createGoal faz POST em /api/v1/goals com os dados', async () => {
    const input = {
      name: 'Reserva de Emergência',
      targetAmount: 25000,
      targetDate: '2027-11-02',
      monthlyAllocation: 2083.34,
    }
    await expect(createGoal(input)).resolves.toEqual(goal)
    expect(mockPost).toHaveBeenCalledWith('/api/v1/goals', input)
  })
})
