/** Transferência entre contas próprias (Story 4.5). Dinheiro vem como número em reais; data AAAA-MM-DD. */
export interface Transfer {
  id: string
  fromAccountId: string
  /** Nome da conta de origem (também de conta removida). */
  fromAccountName: string | null
  toAccountId: string
  toAccountName: string | null
  amount: number
  transferDate: string
  description: string | null
  createdAt: string
}

export interface CreateTransferInput {
  /** Gerado pelo cliente (chave de idempotência): reenviar o mesmo id devolve a mesma transferência. */
  id: string
  fromAccountId: string
  toAccountId: string
  amount: number
  transferDate: string
  description: string | null
}
