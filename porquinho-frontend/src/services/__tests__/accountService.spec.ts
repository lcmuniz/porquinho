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

import {
  createAccount,
  deleteAccount,
  listAccounts,
  listDeletedAccounts,
  restoreAccount,
} from '../accountService'

const account = {
  id: 'a1',
  accountName: 'Nubank',
  bankName: 'Nubank',
  accountType: 'CHECKING' as const,
  initialBalance: 1234.56,
  currentBalance: 1234.56,
  createdAt: '2026-11-02T12:00:00Z',
}

describe('accountService', () => {
  beforeEach(() => {
    mockGet.mockReset().mockResolvedValue({ data: [account] })
    mockPost.mockReset().mockResolvedValue({ data: account })
    mockDelete.mockReset().mockResolvedValue({ status: 204 })
  })

  it('listAccounts faz GET em /api/v1/accounts', async () => {
    await expect(listAccounts()).resolves.toEqual([account])
    expect(mockGet).toHaveBeenCalledWith('/api/v1/accounts')
  })

  it('createAccount faz POST em /api/v1/accounts com os dados', async () => {
    const input = {
      accountName: 'Nubank',
      bankName: 'Nubank',
      accountType: 'CHECKING' as const,
      initialBalance: 1234.56,
    }
    await expect(createAccount(input)).resolves.toEqual(account)
    expect(mockPost).toHaveBeenCalledWith('/api/v1/accounts', input)
  })

  it('deleteAccount faz DELETE em /api/v1/accounts/{id}', async () => {
    await deleteAccount('a1')
    expect(mockDelete).toHaveBeenCalledWith('/api/v1/accounts/a1')
  })

  it('listDeletedAccounts faz GET em /api/v1/accounts com status=deleted', async () => {
    await expect(listDeletedAccounts()).resolves.toEqual([account])
    expect(mockGet).toHaveBeenCalledWith('/api/v1/accounts', { params: { status: 'deleted' } })
  })

  it('restoreAccount faz POST em /api/v1/accounts/{id}/restore', async () => {
    await expect(restoreAccount('a1')).resolves.toEqual(account)
    expect(mockPost).toHaveBeenCalledWith('/api/v1/accounts/a1/restore')
  })
})
