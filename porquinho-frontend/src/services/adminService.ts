import { api } from './api'
import type { AdminAccess, SystemHealth } from '@/types/admin'

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
