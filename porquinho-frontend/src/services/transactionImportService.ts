import { api } from './api'
import type {
  ImportReviewActionResult,
  ImportTarget,
  ImportTransaction,
  TransactionImport,
} from '@/types/transactionImport'

/** Story 5.1: as 20 importações mais recentes do usuário logado, com o nome do destino. */
export async function listImports(): Promise<TransactionImport[]> {
  const response = await api.get<TransactionImport[]>('/api/v1/imports')
  return response.data
}

/**
 * Story 5.3: uma importação do usuário logado, consultada a cada 2 s enquanto o banco não é detectado.
 * 404 "Importação não encontrada" se não for do usuário.
 */
export async function getImport(id: string): Promise<TransactionImport> {
  const response = await api.get<TransactionImport>(`/api/v1/imports/${id}`)
  return response.data
}

/**
 * Stories 5.1 e 5.2: envia o extrato OFX ou CSV para a conta ou o cartão escolhido. `onProgress` recebe de 0 a 100.
 * 400 traz `detail` em pt-BR (formato, tamanho, extrato de cartão para conta e vice-versa); 404 "Conta não
 * encontrada" / "Cartão não encontrado" se o destino não for do usuário ou tiver sido removido.
 */
export async function uploadImport(
  file: File,
  target: ImportTarget,
  onProgress?: (percent: number) => void,
): Promise<TransactionImport> {
  const form = new FormData()
  form.append('file', file)
  form.append(target.kind === 'account' ? 'accountId' : 'creditCardId', target.id)
  const response = await api.post<TransactionImport>('/api/v1/imports', form, {
    // api.ts usa JSON por padrão; o navegador completa o boundary do multipart
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: (event) => {
      if (onProgress && event.total) {
        onProgress(Math.round((event.loaded / event.total) * 100))
      }
    },
  })
  return response.data
}

/** Story 5.9: as linhas da importação para a revisão, sem as parcelas futuras. 404 se não for do usuário. */
export async function listImportTransactions(id: string): Promise<ImportTransaction[]> {
  const response = await api.get<ImportTransaction[]>(`/api/v1/imports/${id}/transactions`)
  return response.data
}

/** Story 5.9: aprova as linhas de alta confiança. 404 se não for do usuário; 409 fora de REVIEW, com `detail` em pt-BR. */
export async function approveHighConfidence(id: string): Promise<ImportReviewActionResult> {
  const response = await api.post<ImportReviewActionResult>(`/api/v1/imports/${id}/approve-high-confidence`)
  return response.data
}

/** Story 5.9: descarta a importação e as linhas dela. 404 se não for do usuário; 409 fora de REVIEW, com `detail` em pt-BR. */
export async function discardImport(id: string): Promise<ImportReviewActionResult> {
  const response = await api.post<ImportReviewActionResult>(`/api/v1/imports/${id}/discard`)
  return response.data
}

/**
 * Story 5.10: troca a categoria de uma ou de várias linhas, e das parcelas futuras das compras delas. 400 em grupo; 404
 * com linha ou categoria indisponível, sem gravar nada; 409 fora de REVIEW. `detail` sempre em pt-BR.
 */
export async function updateImportCategory(
  id: string,
  transactionIds: string[],
  categoryId: string,
): Promise<ImportReviewActionResult> {
  const response = await api.patch<ImportReviewActionResult>(`/api/v1/imports/${id}/transactions/category`, {
    transactionIds,
    categoryId,
  })
  return response.data
}

/**
 * Story 5.10: conclui a importação. Repetir responde 200 sem mudar nada. 400 se o saldo estourar; 404 se não for do
 * usuário; 409 em outro status ou com o destino removido. `detail` sempre em pt-BR.
 */
export async function completeTransactionImport(id: string): Promise<ImportReviewActionResult> {
  const response = await api.post<ImportReviewActionResult>(`/api/v1/imports/${id}/complete`)
  return response.data
}
