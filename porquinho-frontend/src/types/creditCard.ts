/** Cartão de crédito (Story 3.3). Dinheiro vem como número em reais. */
export interface CreditCard {
  id: string
  cardName: string
  bankName: string | null
  cardLimit: number
  /** Dia do vencimento da fatura, de 1 a 31. */
  dueDay: number
  createdAt: string
  /** Versão otimista: volta no PUT da edição (409 se outra tela gravou antes). */
  version: number
}

export interface CreateCreditCardInput {
  cardName: string
  bankName: string | null
  cardLimit: number
  dueDay: number
}

/** PUT da edição: os campos da criação mais a versão lida ao abrir o formulário. */
export interface UpdateCreditCardInput extends CreateCreditCardInput {
  version: number
}
