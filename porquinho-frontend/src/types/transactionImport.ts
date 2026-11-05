/**
 * Status de uma importação de extrato (Story 5.1). PROCESSING enquanto o processamento roda (o banco aparece no meio,
 * Story 5.3); REVIEW quando as transações foram lidas e gravadas (Story 5.4, decisão 1A); FAILED com `errorMessage`
 * quando o arquivo está incompleto, não tem transações ou o processamento cai.
 */
export type TransactionImportStatus = 'PROCESSING' | 'REVIEW' | 'COMPLETED' | 'FAILED' | 'DISCARDED'

/** Extrato enviado para uma conta ou um cartão (Story 5.1). Exatamente um de accountId/creditCardId vem preenchido. */
export interface TransactionImport {
  id: string
  accountId: string | null
  creditCardId: string | null
  /** Nome da conta ou do cartão de destino (também de conta ou cartão removido). */
  targetName: string | null
  fileName: string
  fileType: 'OFX' | 'CSV'
  /** Banco detectado em segundo plano (Story 5.3); null enquanto não detectado. */
  bankName: BankCode | null
  fileSizeKb: number
  status: TransactionImportStatus
  /** Lançamentos do arquivo, inclusive as linhas puladas (Story 5.4). */
  totalTransactions: number
  /** Transações gravadas da importação (Story 5.4). */
  importedTransactions: number
  duplicatesFound: number
  /** Lançamentos que não puderam ser lidos (Story 5.4). */
  skippedRows: number
  errorMessage: string | null
  createdAt: string
}

/** Código do banco detectado no OFX (Story 5.3). OTHER: banco fora dos cinco suportados (decisão 2A). */
export type BankCode = 'NUBANK' | 'BANCO_DO_BRASIL' | 'ITAU' | 'BRADESCO' | 'CAIXA' | 'OTHER'

/** Destino escolhido antes do envio (decisão D2 do Epic 5). */
export type ImportTarget = { kind: 'account'; id: string } | { kind: 'card'; id: string }
