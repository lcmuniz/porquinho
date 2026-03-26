export interface Session {
  sessionId: string
  deviceInfo: string | null
  ipAddress: string | null
  createdAt: string
  lastActivity: string
  current: boolean
}
