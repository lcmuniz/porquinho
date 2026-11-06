const DATE_BR = new Intl.DateTimeFormat('pt-BR', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  timeZone: 'America/Sao_Paulo',
})

/** dd/mm/aaaa no horário de Brasília (mesma data que o backend usa nos e-mails). */
export function formatDateBR(iso: string | null | undefined): string {
  if (!iso) return ''
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? '' : DATE_BR.format(date)
}

/**
 * Data sem hora ("2026-11-03") em dd/mm/aaaa, direto do texto: `new Date` a leria como UTC e, em São Paulo, mostraria o
 * dia anterior.
 */
export function formatLocalDateBR(date: string | null | undefined): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(date ?? '')
  return match ? `${match[3]}/${match[2]}/${match[1]}` : ''
}
