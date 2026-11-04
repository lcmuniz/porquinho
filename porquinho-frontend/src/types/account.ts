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

/** Story 4.7: evento que gerou o ponto do histórico de saldo. */
export type BalanceEvent = 'initial' | 'edit' | 'transfer' | 'reconciliation' | 'transaction'

/** Story 4.7: período do histórico, contado a partir de agora no horário de Brasília. */
export type BalanceRange = '30d' | '3m' | '6m' | '1y' | 'all'

export interface BalancePoint {
  /** ISO com fuso. */
  date: string
  balance: number
  event: BalanceEvent
}

/** Resposta do GET: a conta (inclusive removida) e os pontos do período, do mais antigo para o mais recente. */
export interface BalanceHistory {
  account: { id: string; accountName: string; currentBalance: number }
  /** Início do período (ISO); null em "Tudo". */
  periodStart: string | null
  points: BalancePoint[]
}
