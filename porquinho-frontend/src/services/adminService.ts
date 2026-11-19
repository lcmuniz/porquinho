import { api } from './api'
import type {
  AdminAccess,
  AdminBankCode,
  AiBankDetail,
  AiMetrics,
  ImportBankDetail,
  ImportMetrics,
  ImportMetricsFilters,
  ImportOutcome,
  SystemAlert,
  SystemAlerts,
  SystemHealth,
  UserMetrics,
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

/** Story 10.3: precisão da IA por banco no período; 400 filtro inválido, 403 não admin. */
export async function getAiMetrics(params: ImportMetricsFilters): Promise<AiMetrics> {
  const response = await api.get<AiMetrics>('/api/v1/admin/ai', { params })
  return response.data
}

/** Story 10.3: detalhe de um banco (por tipo, por faixa de confiança, padrões compartilhados e sugestões). */
export async function getAiBankDetail(params: ImportMetricsFilters & { bank: AdminBankCode }): Promise<AiBankDetail> {
  const response = await api.get<AiBankDetail>('/api/v1/admin/ai/detail', { params })
  return response.data
}

/** Story 10.3: CSV pt-BR com uma linha por banco e a linha Total. */
export async function exportAiMetrics(params: ImportMetricsFilters): Promise<Blob> {
  const response = await api.get<Blob>('/api/v1/admin/ai/export', { params, responseType: 'blob' })
  return response.data
}

/** Story 10.4: usuários ativos, assinaturas, engajamento e adoção, agora; 403 não admin. */
export async function getUserMetrics(): Promise<UserMetrics> {
  const response = await api.get<UserMetrics>('/api/v1/admin/users')
  return response.data
}

/** Story 10.4: CSV pt-BR com uma linha por métrica (Métrica;Valor). */
export async function exportUserMetrics(): Promise<Blob> {
  const response = await api.get<Blob>('/api/v1/admin/users/export', { responseType: 'blob' })
  return response.data
}

/** Story 10.5: os 100 alertas mais recentes e a contagem dos abertos; 403 não admin. */
export async function getAlerts(): Promise<SystemAlerts> {
  const response = await api.get<SystemAlerts>('/api/v1/admin/alerts')
  return response.data
}

/** Story 10.5: marca o alerta como resolvido; idempotente (de novo mantém a data original). */
export async function resolveAlert(id: number): Promise<SystemAlert> {
  const response = await api.post<SystemAlert>(`/api/v1/admin/alerts/${id}/resolve`)
  return response.data
}
