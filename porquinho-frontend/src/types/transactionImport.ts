/**
 * Status de uma importação de extrato (Story 5.1). PROCESSING enquanto o processamento roda (o banco aparece no meio,
 * Story 5.3, e a classificação pela IA, Story 5.8); REVIEW quando as transações foram gravadas e classificadas (Story
 * 5.4, decisão 1A); COMPLETED direto quando
 * todos os lançamentos eram duplicatas ou transferências já registradas (Story 5.5, decisão 1A); FAILED com `errorMessage`
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
  /** Lançamentos ignorados por já existirem no destino (Story 5.5). */
  duplicatesFound: number
  /** Lançamentos ignorados por corresponderem a uma transferência já registrada (Story 5.5, D4). */
  transfersFound: number
  /** Lançamentos que não puderam ser lidos (Story 5.4). */
  skippedRows: number
  /** Linhas enviadas à IA: o Y de "X de Y" (Story 5.8). */
  classificationTotal: number
  /** Linhas dos lotes concluídos, com sucesso ou não: o X de "X de Y" (Story 5.8). */
  classifiedCount: number
  errorMessage: string | null
  createdAt: string
}

/** Código do banco detectado no OFX (Story 5.3). OTHER: banco fora dos cinco suportados (decisão 2A). */
export type BankCode = 'NUBANK' | 'BANCO_DO_BRASIL' | 'ITAU' | 'BRADESCO' | 'CAIXA' | 'OTHER'

/** Destino escolhido antes do envio (decisão D2 do Epic 5). */
export type ImportTarget = { kind: 'account'; id: string } | { kind: 'card'; id: string }

/** Estado da linha na revisão (Story 5.9): APPROVED pelo "Aprovar todas"; CONFIRMED com a importação concluída. */
export type ImportReviewStatus = 'PENDING' | 'APPROVED' | 'CONFIRMED'

/** Detalhes do Pix (Story 5.6), com as chaves como foram gravadas. */
export interface PixDetails {
  direction: 'SENT' | 'RECEIVED'
  counterpart_name?: string
}

/** Uma linha da importação na revisão (Story 5.9). As parcelas futuras não vêm na lista (decisão 2A). */
export interface ImportTransaction {
  id: string
  /** Data sem hora (AAAA-MM-DD): formatar com `formatLocalDateBR`. */
  date: string
  description: string
  /** Sempre positivo; o sentido está em `type`. */
  amount: number
  type: 'DEBIT' | 'CREDIT'
  kind: 'REGULAR' | 'CARD_PAYMENT'
  /** Resolvida no backend, inclusive categoria removida; null sem categoria. `group` é o nome da categoria pai. */
  category: { id: string; name: string; group: string | null } | null
  source: 'AI' | 'PATTERN' | 'USER' | 'NONE'
  /** De 0 a 100; null em CARD_PAYMENT e em linhas anteriores à Story 5.8. */
  confidence: number | null
  reviewStatus: ImportReviewStatus
  isPix: boolean
  pixDetails: PixDetails | null
  isInstallment: boolean
  installmentNumber: number | null
  installmentTotal: number | null
  isFutureInstallment: boolean
}

/** Resultado de aprovar ou descartar: o status da importação e quantas linhas foram afetadas (Story 5.9). */
export interface ImportReviewActionResult {
  importId: string
  status: TransactionImportStatus
  affectedTransactions: number
}
