import { api } from './api'
import type {
  AdminAccess,
  AdminBankCode,
  ImportBankDetail,
  ImportMetrics,
  ImportMetricsFilters,
  ImportOutcome,
  SystemHealth,
} from '@/types/admin'

/** Story 10.1: se o usuário é admin (link "Admin" do dashboard). */
export async function getAdminAccess(): Promise<AdminAccess> {
  const response = await api.get<AdminAccess>('/api/v1/admin/access')
  return response.data
}

/** Story 10.1: saúde do sistema; 403 "Acesso restrito" para quem não é admin. */
export async function getSystemHealth(): Promise<SystemHealth> {
  const response = await api.get<SystemHealth>('/api/v1/admin/health')
  return response.data
}

/** Story 10.2: taxa de erro de importação por banco no período; 400 filtro inválido, 403 não admin. */
export async function getImportMetrics(params: ImportMetricsFilters): Promise<ImportMetrics> {
  const response = await api.get<ImportMetrics>('/api/v1/admin/imports', { params })
  return response.data
}

/** Story 10.2: detalhe de um banco (tipos de erro, importações recentes, tendência de 30 dias). */
export async function getImportBankDetail(
  params: ImportMetricsFilters & { bank: AdminBankCode; status?: ImportOutcome },
): Promise<ImportBankDetail> {
  const response = await api.get<ImportBankDetail>('/api/v1/admin/imports/detail', { params })
  return response.data
}

/** Story 10.2: CSV pt-BR com uma linha por importação que passa nos filtros. */
export async function exportImportErrors(
  params: ImportMetricsFilters & { status?: ImportOutcome },
): Promise<Blob> {
  const response = await api.get<Blob>('/api/v1/admin/imports/export', { params, responseType: 'blob' })
  return response.data
}
