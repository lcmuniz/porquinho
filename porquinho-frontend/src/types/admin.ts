import type { BankCode, TransactionImportStatus } from './transactionImport'

/** Story 10.1: cor de cada indicador do painel; UNKNOWN = sem dados ainda. */
export type HealthStatus = 'GREEN' | 'YELLOW' | 'RED' | 'UNKNOWN'

export interface AdminAccess {
  admin: boolean
}

export interface SystemHealth {
  generatedAt: string
  uptime: { last24h: number | null; last30d: number | null; target: number; status: HealthStatus }
  errors: {
    last24h: number
    last7d: number
    last30d: number
    errorRate24h: number | null
    status: HealthStatus
  }
  responseTime: { avgMs24h: number | null; status: HealthStatus }
  endpoints: {
    method: string
    uri: string
    count: number
    avgMs: number
    errors: number
    status: HealthStatus
  }[]
  hourly: { hour: string; requests: number; errors: number; avgMs: number | null }[]
  database: {
    active: number
    idle: number
    max: number
    waiting: number
    poolStatus: HealthStatus
    slowQueries: number
    slowQueriesStatus: HealthStatus
  }
  redis: {
    up: boolean
    hitRate: number | null
    usedMemoryBytes: number | null
    maxMemoryBytes: number | null
    status: HealthStatus
  }
  sessions: { active: number; status: HealthStatus }
}

/** Story 10.2: banco detectado; UNKNOWN quando a importação falhou antes da detecção. */
export type AdminBankCode = BankCode | 'UNKNOWN'

/** Story 10.2: tipo de erro lido da mensagem fixa do processador. */
export type ImportErrorType = 'FORMAT' | 'PARSING' | 'INTERRUPTED' | 'UNEXPECTED' | 'OTHER'

/** Story 10.2: filtro de status da lista do detalhe e do CSV. */
export type ImportOutcome = 'ALL' | 'FAILED' | 'SUCCESS'

/** Período em dias de America/Sao_Paulo (AAAA-MM-DD); sem valor, os últimos 30 dias. */
export interface ImportMetricsFilters {
  from?: string
  to?: string
  bank?: AdminBankCode
}

/** Story 10.2: taxa de erro por banco; PROCESSING fica fora do total; errorRate null = sem importação terminada. */
export interface ImportMetrics {
  from: string
  to: string
  target: number
  banks: {
    bank: AdminBankCode
    total: number
    successes: number
    failures: number
    errorRate: number | null
    status: HealthStatus
  }[]
}

export interface ImportBankDetail {
  bank: AdminBankCode
  from: string
  to: string
  errorTypes: { type: ImportErrorType; count: number }[]
  recent: {
    createdAt: string
    bank: AdminBankCode
    fileType: 'OFX' | 'CSV'
    status: TransactionImportStatus
    errorType: ImportErrorType | null
    message: string | null
  }[]
  /** Últimos 30 dias até hoje, sem depender do período; errorRate null num dia sem importação terminada. */
  trend: { date: string; total: number; failures: number; errorRate: number | null }[]
}
