import { api } from './api'
import type { Transaction, TransactionCreateInput, TransactionUpdateInput } from '@/types/transaction'

/** Story 5.12: as transações confirmadas do mês (AAAA-MM), da mais nova para a mais antiga, sem as parcelas futuras. */
export async function listTransactions(month: string): Promise<Transaction[]> {
  const response = await api.get<Transaction[]>('/api/v1/transactions', { params: { month } })
  return response.data
}

/**
 * Story 5.13: adiciona uma transação já confirmada (despesa em dinheiro, lançamento que nenhum extrato trouxe). 400 traz
 * `errors` por campo (ou `detail`); 404 se a conta, o cartão ou a categoria não estiverem disponíveis.
 */
export async function createTransaction(input: TransactionCreateInput): Promise<Transaction> {
  const response = await api.post<Transaction>('/api/v1/transactions', input)
  return response.data
}

/**
 * Story 5.12: edita data, descrição, valor, tipo, categoria e conta ou cartão. 400 traz `errors` por campo (ou `detail`
 * quando o saldo sairia do limite); 404 se a transação, a conta, o cartão ou a categoria não estiverem disponíveis.
 */
export async function updateTransaction(id: string, input: TransactionUpdateInput): Promise<Transaction> {
  const response = await api.put<Transaction>(`/api/v1/transactions/${id}`, input)
  return response.data
}

/** Story 5.12: remove a transação e tira o efeito dela do saldo. 404 se já tiver sido removida. */
export async function deleteTransaction(id: string): Promise<void> {
  await api.delete(`/api/v1/transactions/${id}`)
}

/** Story 5.12: remove as parcelas futuras da compra e devolve quantas foram removidas (0 se não havia). */
export async function removeFutureInstallments(id: string): Promise<number> {
  const response = await api.post<{ removedInstallments: number }>(
    `/api/v1/transactions/${id}/remove-future-installments`,
  )
  return response.data.removedInstallments
}
