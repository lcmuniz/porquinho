import { api } from './api'
import type { CreateTransferInput, Transfer } from '@/types/transfer'

/** Story 4.5: as 20 transferências mais recentes do usuário logado, com os nomes das contas. */
export async function listTransfers(): Promise<Transfer[]> {
  const response = await api.get<Transfer[]>('/api/v1/transfers')
  return response.data
}

/**
 * Story 4.5: registra a transferência (debita a origem e credita o destino). Com um `id` já usado pelo usuário,
 * devolve a transferência existente (200) sem mudar nada. 400 traz `errors` por campo
 * (`differentAccounts` para contas iguais); 404 "Conta não encontrada" se uma das contas não for do usuário ou
 * tiver sido removida.
 */
export async function createTransfer(input: CreateTransferInput): Promise<Transfer> {
  const response = await api.post<Transfer>('/api/v1/transfers', input)
  return response.data
}
