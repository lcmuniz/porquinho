/**
 * Destino pós-login vindo da URL (?redirectTo=): só caminho interno. "//host" e "/\host" o navegador trata como outro
 * site, então são recusados (open redirect).
 */
export function safeRedirect(value: unknown): string | null {
  if (typeof value !== 'string' || !value.startsWith('/')) return null
  if (value.startsWith('//') || value.startsWith('/\\')) return null
  return value
}
