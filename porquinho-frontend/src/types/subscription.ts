export interface Subscription {
  planType: 'TRIAL' | 'MONTHLY'
  status: 'ACTIVE' | 'CANCELLED' | 'EXPIRED'
  trialStartDate: string | null
  trialEndDate: string | null
  daysRemaining: number
  hasAccess: boolean
}

export interface CheckoutSession {
  url: string
}
