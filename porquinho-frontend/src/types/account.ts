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

/** Story 4.3: edição. O saldo inicial não muda; o saldo atual é o informado. */
export interface UpdateAccountInput {
  accountName: string
  bankName: string | null
  accountType: AccountType
  currentBalance: number
}
