import { api } from './api'
import type { DataExportResponse } from '@/types/dataExport'

export async function requestExport(): Promise<DataExportResponse> {
  const response = await api.post<DataExportResponse>('/api/v1/data-export')
  return response.data
}

export async function getExportStatus(): Promise<DataExportResponse | null> {
  const response = await api.get<DataExportResponse>('/api/v1/data-export/status')
  if (response.status === 204) {
    return null
  }
  return response.data
}

export function getDownloadUrl(token: string): string {
  return `/api/v1/data-export/download/${token}`
}
