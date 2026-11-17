/** Forma do erro do axios que as telas leem: status HTTP e o ProblemDetail (`detail`, `errors` por campo). */
export interface ApiError {
  response?: { status?: number; data?: { detail?: string; errors?: Record<string, string> } }
}

/** Status HTTP do erro da API (undefined sem resposta, ex.: falha de rede). */
export function apiErrorStatus(err: unknown): number | undefined {
  return (err as ApiError)?.response?.status
}

/** `detail` do ProblemDetail da API, ou `fallback` quando não veio. */
export function apiErrorDetail(err: unknown): string | undefined
export function apiErrorDetail(err: unknown, fallback: string): string
export function apiErrorDetail(err: unknown, fallback?: string): string | undefined {
  return (err as ApiError)?.response?.data?.detail ?? fallback
}

/** title do ProblemDetail que o backend devolve (403, DeletedAccountFilter) para conta em exclusão. */
export const DELETED_ACCOUNT_TITLE = 'Conta em exclusão'

/** Conta em exclusão: 403 com o título acima (DeletedAccountFilter). */
export function isDeletedAccountError(err: unknown): boolean {
  const response = (err as { response?: { status?: number; data?: { title?: string } } })?.response
  return response?.status === 403 && response.data?.title === DELETED_ACCOUNT_TITLE
}

/**
 * Cadastro/login recusado pelo backend: conta em exclusão (403 acima) ou 409 do registro, que tem duas causas
 * (exclusão pendente ou e-mail de outra conta). Em todos os casos a tela não entra no app.
 */
export function isRegistrationRefused(err: unknown): boolean {
  return isDeletedAccountError(err) || apiErrorStatus(err) === 409
}
