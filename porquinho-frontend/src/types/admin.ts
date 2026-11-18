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
  endpoints: { method: string; uri: string; count: number; avgMs: number; errors: number }[]
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
