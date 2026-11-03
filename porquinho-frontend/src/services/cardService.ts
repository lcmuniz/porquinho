import { api } from './api'
import type { CreateCreditCardInput, CreditCard } from '@/types/creditCard'

/** Story 3.3: cartões ativos do usuário logado, do mais antigo para o mais novo. */
export async function listCards(): Promise<CreditCard[]> {
  const response = await api.get<CreditCard[]>('/api/v1/credit-cards')
  return response.data
}

/** Story 3.3: cria o cartão. 400 traz `errors` por campo. */
export async function createCard(input: CreateCreditCardInput): Promise<CreditCard> {
  const response = await api.post<CreditCard>('/api/v1/credit-cards', input)
  return response.data
}

/**
 * Story 4.3: edita nome, banco, limite e vencimento (mesmas regras da criação). 400 traz `errors` por
 * campo; 404 se o cartão não for do usuário ou já tiver sido removido.
 */
export async function updateCard(id: string, input: CreateCreditCardInput): Promise<CreditCard> {
  const response = await api.put<CreditCard>(`/api/v1/credit-cards/${id}`, input)
  return response.data
}

/** Story 3.3: remove o cartão (soft delete). 404 se não for do usuário ou já tiver sido removido. */
export async function deleteCard(id: string): Promise<void> {
  await api.delete(`/api/v1/credit-cards/${id}`)
}

/** Story 4.4: cartões removidos do usuário, do removido mais recentemente para o mais antigo. */
export async function listDeletedCards(): Promise<CreditCard[]> {
  const response = await api.get<CreditCard[]>('/api/v1/credit-cards', {
    params: { status: 'deleted' },
  })
  return response.data
}

/** Story 4.4: restaura o cartão removido. 404 se ele não for do usuário ou não estiver removido. */
export async function restoreCard(id: string): Promise<CreditCard> {
  const response = await api.post<CreditCard>(`/api/v1/credit-cards/${id}/restore`)
  return response.data
}
