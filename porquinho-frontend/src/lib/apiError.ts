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
