export interface DataExportResponse {
  id: string
  status: 'PROCESSING' | 'READY' | 'DOWNLOADED' | 'EXPIRED' | 'FAILED'
  createdAt: string
  expiresAt: string | null
  downloadUrl: string | null
}
