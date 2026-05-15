import { api } from './api'
import type { AccountDeletionResponse } from '@/types/accountDeletion'

export async function requestDeletion(): Promise<AccountDeletionResponse> {
  const response = await api.post<AccountDeletionResponse>('/api/v1/account-deletion')
  return response.data
}
