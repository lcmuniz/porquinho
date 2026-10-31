import { api } from './api'
import type { CheckoutSession, Subscription } from '@/types/subscription'

export async function getMySubscription(): Promise<Subscription> {
  const response = await api.get<Subscription>('/api/v1/subscriptions/me')
  return response.data
}

/**
 * Story 2.4: cancela no fim do período atual (sem nova cobrança) e devolve o status atualizado
 * (CANCELLED, com acesso até `nextBillingDate`).
 * Erros: 409 se não há assinatura mensal ativa (trial, já cancelada, expirada); 404 sem assinatura;
 * 503 se o Stripe falhou (nada muda). Sem resposta/timeout: o resultado é incerto, recarregue o status.
 */
export async function cancelSubscription(): Promise<Subscription> {
  const response = await api.post<Subscription>('/api/v1/subscriptions/cancel')
  return response.data
}

/** Story 2.2: cria a sessão do Stripe Checkout; o frontend só redireciona para a URL. */
export async function createCheckout(): Promise<CheckoutSession> {
  const response = await api.post<CheckoutSession>('/api/v1/subscriptions/checkout')
  return response.data
}
