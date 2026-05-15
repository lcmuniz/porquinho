export type DeletionStatus = 'PENDING_PURGE' | 'PURGED'

export interface AccountDeletionResponse {
  id: string
  status: DeletionStatus
  requestedAt: string
  scheduledPurgeAt: string
}
