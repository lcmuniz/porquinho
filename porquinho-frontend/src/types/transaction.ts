import type { ImportTransaction } from './transactionImport'

/**
 * Transação confirmada de /transacoes (Story 5.12): os campos da linha da revisão, mais onde ela está e, numa parcela,
 * quantas parcelas futuras da compra ainda estão ativas. Exatamente um de accountId/creditCardId vem preenchido.
 */
export interface Transaction extends ImportTransaction {
  accountId: string | null
  creditCardId: string | null
  installmentGroupId: string | null
  remainingInstallments: number
  /** Parcelas confirmadas ativas da compra, esta incluída: com 1, remover esta leva as futuras junto (decisão Q3). */
  confirmedInstallments: number
}

/** Corpo do PUT /api/v1/transactions/{id}: exatamente um de accountId/creditCardId; categoryId null deixa sem categoria. */
export interface TransactionUpdateInput {
  date: string
  description: string
  amount: number
  type: 'DEBIT' | 'CREDIT'
  categoryId: string | null
  accountId: string | null
  creditCardId: string | null
}

/** Corpo do POST /api/v1/transactions (Story 5.13): como o PUT, mas a categoria é obrigatória. */
export interface TransactionCreateInput extends TransactionUpdateInput {
  categoryId: string
}
