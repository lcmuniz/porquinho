import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockGet = vi.fn()
const mockPost = vi.fn()
const mockPut = vi.fn()

vi.mock('../api', () => ({
  api: {
    get: (...a: unknown[]) => mockGet(...a),
    post: (...a: unknown[]) => mockPost(...a),
    put: (...a: unknown[]) => mockPut(...a),
  },
}))

import {
  contributeToGoal,
  createGoal,
  getGoalsSummary,
  listGoals,
  updateGoalAllocation,
} from '../goalService'

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
    mockPut.mockReset().mockResolvedValue({ data: goal })
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

  it('updateGoalAllocation faz PUT em /api/v1/goals/{id}/allocation', async () => {
    await expect(updateGoalAllocation('g1', 700)).resolves.toEqual(goal)
    expect(mockPut).toHaveBeenCalledWith('/api/v1/goals/g1/allocation', { monthlyAllocation: 700 })
  })

  it('contributeToGoal faz POST em /api/v1/goals/{id}/contributions', async () => {
    await expect(contributeToGoal('g1', 50)).resolves.toEqual(goal)
    expect(mockPost).toHaveBeenCalledWith('/api/v1/goals/g1/contributions', { amount: 50 })
  })

  it('getGoalsSummary faz GET em /api/v1/goals/summary', async () => {
    mockGet.mockResolvedValue({ data: { totalAvailable: 1000 } })
    await expect(getGoalsSummary()).resolves.toEqual({ totalAvailable: 1000 })
    expect(mockGet).toHaveBeenCalledWith('/api/v1/goals/summary')
  })
})
