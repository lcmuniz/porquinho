export interface Subscription {
  planType: 'TRIAL' | 'MONTHLY'
  status: 'ACTIVE' | 'CANCELLED' | 'EXPIRED'
  trialStartDate: string | null
  trialEndDate: string | null
  /** Próxima cobrança; para CANCELLED, até quando o acesso vale (Story 2.4). */
  nextBillingDate: string | null
  /** Quando o usuário cancelou (Story 2.4). */
  cancellationDate: string | null
  daysRemaining: number
  hasAccess: boolean
}

export interface CheckoutSession {
  url: string
}
