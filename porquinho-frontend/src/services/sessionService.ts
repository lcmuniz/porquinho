import { api } from './api'
import type { Session } from '@/types/session'

export async function getSessions(): Promise<Session[]> {
  const response = await api.get<Session[]>('/api/v1/auth/sessions')
  return response.data
}

export async function revokeSession(sessionId: string): Promise<void> {
  await api.delete(`/api/v1/auth/sessions/${sessionId}`)
}

export async function revokeAllOtherSessions(): Promise<void> {
  await api.delete('/api/v1/auth/sessions')
}
