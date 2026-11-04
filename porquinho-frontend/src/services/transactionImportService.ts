import { api } from './api'
import type { ImportTarget, TransactionImport } from '@/types/transactionImport'

/** Story 5.1: as 20 importações mais recentes do usuário logado, com o nome do destino. */
export async function listImports(): Promise<TransactionImport[]> {
  const response = await api.get<TransactionImport[]>('/api/v1/imports')
  return response.data
}

/**
 * Story 5.1: envia o extrato OFX para a conta ou o cartão escolhido. `onProgress` recebe de 0 a 100.
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
