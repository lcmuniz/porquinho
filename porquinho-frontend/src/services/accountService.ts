import { api } from './api'
import type {
  Account,
  BalanceHistory,
  BalanceRange,
  CreateAccountInput,
  CreateReconciliationInput,
  Reconciliation,
  ReconciliationResult,
  UpdateAccountInput,
} from '@/types/account'

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
 * 404 se a conta não for do usuário ou já tiver sido removida; 409 se a `version` enviada não for mais a atual
 * (a conta mudou em outra tela depois de o formulário abrir).
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

/**
 * Story 4.6: reconcilia o saldo com o banco. O servidor calcula a diferença a partir do saldo que ele tem, ajusta o
 * saldo atual e grava a data. 400 traz `errors` por campo; 404 se a conta não for do usuário ou tiver sido removida.
 */
export async function reconcileAccount(
  id: string,
  input: CreateReconciliationInput,
): Promise<ReconciliationResult> {
  const response = await api.post<ReconciliationResult>(
    `/api/v1/accounts/${id}/reconciliations`,
    input,
  )
  return response.data
}

/** Story 4.6: as 20 reconciliações mais recentes da conta. */
export async function listReconciliations(id: string): Promise<Reconciliation[]> {
  const response = await api.get<Reconciliation[]>(`/api/v1/accounts/${id}/reconciliations`)
  return response.data
}

/**
 * Story 4.7: pontos do saldo da conta no período, do mais antigo para o mais recente; o primeiro é o último saldo
 * antes do período. 400 "Período inválido"; 404 se a conta não for do usuário (removida continua consultável).
 */
export async function getBalanceHistory(id: string, range: BalanceRange): Promise<BalanceHistory> {
  const response = await api.get<BalanceHistory>(`/api/v1/accounts/${id}/balance-history`, {
    params: { range },
  })
  return response.data
}
