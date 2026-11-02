import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockGet = vi.fn()
const mockPost = vi.fn()

vi.mock('../api', () => ({
  api: {
    get: (...a: unknown[]) => mockGet(...a),
    post: (...a: unknown[]) => mockPost(...a),
  },
}))

import {
  completeAccounts,
  completeCards,
  completeTutorial,
  getMyOnboarding,
  completeCategories,
} from '../onboardingService'

const progress = { currentStep: 2, stepsCompleted: ['tutorial_skipped'], completed: false }

describe('onboardingService', () => {
  beforeEach(() => {
    mockGet.mockReset().mockResolvedValue({ data: progress })
    mockPost.mockReset().mockResolvedValue({ data: progress })
  })

  it('getMyOnboarding faz GET em /api/v1/onboarding/me', async () => {
    await expect(getMyOnboarding()).resolves.toEqual(progress)
    expect(mockGet).toHaveBeenCalledWith('/api/v1/onboarding/me')
  })

  it('completeTutorial(true) faz POST em /api/v1/onboarding/me/tutorial com { skipped: true }', async () => {
    await expect(completeTutorial(true)).resolves.toEqual(progress)
    expect(mockPost).toHaveBeenCalledWith('/api/v1/onboarding/me/tutorial', { skipped: true })
  })

  it('completeAccounts(false) faz POST em /api/v1/onboarding/me/accounts com { skipped: false }', async () => {
    await expect(completeAccounts(false)).resolves.toEqual(progress)
    expect(mockPost).toHaveBeenCalledWith('/api/v1/onboarding/me/accounts', { skipped: false })
  })

  it('completeCards(true) faz POST em /api/v1/onboarding/me/cards com { skipped: true }', async () => {
    await expect(completeCards(true)).resolves.toEqual(progress)
    expect(mockPost).toHaveBeenCalledWith('/api/v1/onboarding/me/cards', { skipped: true })
  })

  it('completeCategories faz POST em /api/v1/onboarding/me/categories com as chaves', async () => {
    await expect(completeCategories(['housing', 'leisure'])).resolves.toEqual(progress)
    expect(mockPost).toHaveBeenCalledWith('/api/v1/onboarding/me/categories', {
      keys: ['housing', 'leisure'],
    })
  })
})
