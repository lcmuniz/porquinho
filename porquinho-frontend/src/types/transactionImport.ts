/** Status de uma importação de extrato (Story 5.1). Nesta etapa toda importação nova fica em PROCESSING. */
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
  /** Banco detectado; null até a Story 5.3. */
  bankName: string | null
  fileSizeKb: number
  status: TransactionImportStatus
  totalTransactions: number
  importedTransactions: number
  duplicatesFound: number
  skippedRows: number
  errorMessage: string | null
  createdAt: string
}

/** Destino escolhido antes do envio (decisão D2 do Epic 5). */
export type ImportTarget = { kind: 'account'; id: string } | { kind: 'card'; id: string }
