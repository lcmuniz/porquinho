import { api } from './api'
import type { Subscription } from '@/types/subscription'

export async function getMySubscription(): Promise<Subscription> {
  const response = await api.get<Subscription>('/api/v1/subscriptions/me')
  return response.data
}
