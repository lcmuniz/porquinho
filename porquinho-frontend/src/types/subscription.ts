export interface Subscription {
  planType: 'TRIAL' | 'MONTHLY'
  /** PAYMENT_FAILED: cobrança recusada, o Stripe tenta de novo (acesso mantido); SUSPENDED: sem acesso (Story 2.7). */
  status: 'ACTIVE' | 'CANCELLED' | 'EXPIRED' | 'PAYMENT_FAILED' | 'SUSPENDED'
  trialStartDate: string | null
  trialEndDate: string | null
  /** Próxima cobrança; para CANCELLED, até quando o acesso vale (Story 2.4). */
  nextBillingDate: string | null
  /** Quando o usuário cancelou (Story 2.4). */
  cancellationDate: string | null
  /** Tentativas de cobrança que falharam na fatura atual (Story 2.7). */
  failedPaymentAttempts: number
  /** Página do Stripe para pagar a fatura que falhou; só em PAYMENT_FAILED (Story 2.7). */
  paymentUpdateUrl: string | null
  daysRemaining: number
  hasAccess: boolean
}

export interface CheckoutSession {
  url: string
}
