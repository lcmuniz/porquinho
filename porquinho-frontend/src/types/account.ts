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
  /** Story 4.6: última reconciliação com o banco (ISO com fuso); null se nunca reconciliada. */
  lastReconciliationDate: string | null
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

/** Story 4.6: reconciliação do saldo com o banco. A diferença é calculada no servidor. */
export interface Reconciliation {
  id: string
  accountId: string
  previousBalance: number
  bankBalance: number
  difference: number
  reason: string | null
  reconciledAt: string
}

export interface CreateReconciliationInput {
  bankBalance: number
  reason: string | null
}

/** Resposta do POST: a conta com o saldo e a data novos, e o evento registrado. */
export interface ReconciliationResult {
  account: Account
  reconciliation: Reconciliation
}
