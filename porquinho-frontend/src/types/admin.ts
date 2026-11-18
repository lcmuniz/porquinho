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

/** Story 10.3: classificações, correções e precisão (%); accuracy null = sem classificação. */
export interface AiAccuracy {
  classifications: number
  corrections: number
  accuracy: number | null
  status: HealthStatus
}

/** Story 10.3: precisão da IA no período, geral e por banco, e por mês nos últimos 6 meses (sem depender do período). */
export interface AiMetrics {
  from: string
  to: string
  target: number
  overall: AiAccuracy & { bank: 'TOTAL' }
  /** delta em pontos percentuais; null quando um dos dois meses não tem dado. */
  monthChange: { current: number | null; previous: number | null; delta: number | null }
  banks: (AiAccuracy & { bank: AdminBankCode })[]
  /** AAAA-MM, do mais antigo ao mês atual. */
  months: string[]
  /** Um valor por mês de `months`; null num mês sem classificação. */
  trend: { bank: AdminBankCode; accuracy: (number | null)[] }[]
}

export type AiTransactionType = 'PIX' | 'INSTALLMENT' | 'DEBIT' | 'CREDIT'
export type AiConfidenceBand = '0-49' | '50-79' | '80-100'

export interface AiBankDetail {
  bank: AdminBankCode
  from: string
  to: string
  types: (AiAccuracy & { key: AiTransactionType })[]
  confidence: (AiAccuracy & { key: AiConfidenceBand })[]
  /** Padrões que pelo menos 3 usuários têm; valem para todos os bancos. */
  patterns: { pattern: string; users: number; usage: number }[]
  suggestions: string[]
}

/** Story 10.4: meta com cor; rate null = sem denominador ("sem dados"). */
export interface UserMetricsGoal {
  rate: number | null
  target: number
  status: HealthStatus
}

export type UserFeature =
  | 'ACCOUNTS'
  | 'CREDIT_CARDS'
  | 'IMPORTS'
  | 'BUDGETS'
  | 'GOALS'
  | 'TRANSFERS'
  | 'RECONCILIATIONS'

/** Story 10.4: usuários "agora" (sem período), só agregados; percentuais com 1 casa, null = sem dados. */
export interface UserMetrics {
  generatedAt: string
  total: number
  active24h: number
  active7d: number
  active30d: number
  trials: number
  paid: number
  /** Meta: até 5 % é verde, acima é vermelho. */
  churn: UserMetricsGoal
  /** Semanas com importação concluída por usuário ativo em 30 d (média das 4 últimas semanas completas). */
  weeklyCheckins: number | null
  /** Meta: 70 % ou mais é verde, abaixo amarelo. */
  onboarding: UserMetricsGoal
  conversion: number | null
  adoption: { feature: UserFeature; users: number; rate: number | null }[]
  /** AAAA-MM, os últimos 12 meses até o atual. */
  months: string[]
  growth: number[]
  conversionByMonth: (number | null)[]
  /** AAAA-MM-DD (segunda-feira), as últimas 12 semanas até a atual. */
  weeks: string[]
  activeWeekly: number[]
  activeMonthly: number[]
}
