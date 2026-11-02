/** Conta bancária (Story 3.2). Dinheiro vem como número em reais. */
export type AccountType = 'CHECKING' | 'SAVINGS'

export interface Account {
  id: string
  accountName: string
  bankName: string | null
  accountType: AccountType
  initialBalance: number
  currentBalance: number
  createdAt: string
}

export interface CreateAccountInput {
  accountName: string
  bankName: string | null
  accountType: AccountType
  initialBalance: number
}
