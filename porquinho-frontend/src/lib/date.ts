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

/** "AAAA-MM" do mês atual em São Paulo (en-CA formata a data como AAAA-MM-DD). */
export function currentMonth(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo' }).format(new Date()).slice(0, 7)
}

/** "AAAA-MM" deslocado em `delta` meses. */
export function shiftMonth(value: string, delta: number): string {
  const [year, monthNumber] = value.split('-').map(Number)
  const date = new Date(Date.UTC(year!, monthNumber! - 1 + delta, 1))
  return `${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, '0')}`
}

const MONTH_BR = new Intl.DateTimeFormat('pt-BR', { month: 'long', year: 'numeric', timeZone: 'UTC' })

/** "2026-11" → "novembro de 2026". */
export function formatMonthBR(value: string): string {
  const [year, monthNumber] = value.split('-').map(Number)
  return MONTH_BR.format(Date.UTC(year!, monthNumber! - 1, 1))
}
