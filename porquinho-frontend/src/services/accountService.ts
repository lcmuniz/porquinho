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

/** Story 3.2: remove a conta (soft delete). 404 se não for do usuário. */
export async function deleteAccount(id: string): Promise<void> {
  await api.delete(`/api/v1/accounts/${id}`)
}
