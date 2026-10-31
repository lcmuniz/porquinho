import { api } from './api'
import type { CheckoutSession, Subscription } from '@/types/subscription'

export async function getMySubscription(): Promise<Subscription> {
  const response = await api.get<Subscription>('/api/v1/subscriptions/me')
  return response.data
}

/** Story 2.2: cria a sessão do Stripe Checkout; o frontend só redireciona para a URL. */
export async function createCheckout(): Promise<CheckoutSession> {
  const response = await api.post<CheckoutSession>('/api/v1/subscriptions/checkout')
  return response.data
}
