/** Cartão de crédito (Story 3.3). Dinheiro vem como número em reais. */
export interface CreditCard {
  id: string
  cardName: string
  bankName: string | null
  cardLimit: number
  /** Dia do vencimento da fatura, de 1 a 31. */
  dueDay: number
  createdAt: string
}

export interface CreateCreditCardInput {
  cardName: string
  bankName: string | null
  cardLimit: number
  dueDay: number
}
