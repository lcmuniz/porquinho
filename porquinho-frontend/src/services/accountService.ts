import { api } from './api'
import type { Account, CreateAccountInput, UpdateAccountInput } from '@/types/account'

/** Story 3.2: contas ativas do usuário logado, da mais antiga para a mais nova. */
export async function listAccounts(): Promise<Account[]> {
  const response = await api.get<Account[]>('/api/v1/accounts')
  return response.data
}

/** Story 3.2: cria a conta (o saldo atual começa igual ao inicial). 400 traz `errors` por campo. */
export async function createAccount(input: CreateAccountInput): Promise<Account> {
  const response = await api.post<Account>('/api/v1/accounts', input)
  return response.data
}

/**
 * Story 4.3: edita nome, banco, tipo e saldo atual (o inicial não muda). 400 traz `errors` por campo;
 * 404 se a conta não for do usuário ou já tiver sido removida.
 */
export async function updateAccount(id: string, input: UpdateAccountInput): Promise<Account> {
  const response = await api.put<Account>(`/api/v1/accounts/${id}`, input)
  return response.data
}

/**
 * Story 3.2: remove a conta (soft delete). 404 se não for do usuário. Story 4.4: 409 "Mantenha ao menos uma conta
 * ativa" ao remover a última conta ativa depois do onboarding.
 */
export async function deleteAccount(id: string): Promise<void> {
  await api.delete(`/api/v1/accounts/${id}`)
}

/** Story 4.4: contas removidas do usuário, da removida mais recentemente para a mais antiga. */
export async function listDeletedAccounts(): Promise<Account[]> {
  const response = await api.get<Account[]>('/api/v1/accounts', { params: { status: 'deleted' } })
  return response.data
}

/** Story 4.4: restaura a conta removida. 404 se ela não for do usuário ou não estiver removida. */
export async function restoreAccount(id: string): Promise<Account> {
  const response = await api.post<Account>(`/api/v1/accounts/${id}/restore`)
  return response.data
}
