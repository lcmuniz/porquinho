/**
 * Story 3.8: cálculos do plano de uma meta (aporte mensal sugerido e previsão de conclusão). Datas são
 * datas de calendário (AAAA-MM-DD) representadas como meia-noite local; "hoje" é o dia em America/Sao_Paulo,
 * o mesmo critério do backend (NotPastInSaoPaulo).
 */

/** Acima disso a previsão fica longe demais para mostrar (e para o Date do navegador). */
export const MAX_FORECAST_MONTHS = 1200

/** Data local como AAAA-MM-DD (formato do `<input type="date">` e do backend). */
export function toISODate(date: Date): string {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

/** AAAA-MM-DD → Date local (meia-noite). null se o texto não for uma data válida. */
export function parseISODate(text: string | null | undefined): Date | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(text ?? '')
  if (!match) return null
  const [, y, m, d] = match.map(Number) as [number, number, number, number]
  const date = new Date(y, m - 1, d)
  if (date.getFullYear() !== y || date.getMonth() !== m - 1 || date.getDate() !== d) return null
  return date
}

const SAO_PAULO_DATE = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'America/Sao_Paulo',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
})

/** Hoje em America/Sao_Paulo (meia-noite local com esse dia), qualquer que seja o fuso do aparelho. */
export function todayInSaoPaulo(now: Date = new Date()): Date {
  return parseISODate(SAO_PAULO_DATE.format(now))!
}

/** Soma meses de calendário; o dia é limitado ao último do mês (31/01 + 1 mês = 28 ou 29/02). */
export function addMonths(date: Date, months: number): Date {
  const target = new Date(date.getFullYear(), date.getMonth() + months, 1)
  const lastDay = new Date(target.getFullYear(), target.getMonth() + 1, 0).getDate()
  target.setDate(Math.min(date.getDate(), lastDay))
  return target
}

/** Meses de calendário entre hoje e a data-alvo (só mês e ano contam), no mínimo 1. */
export function monthsUntil(today: Date, target: Date): number {
  const months =
    (target.getFullYear() - today.getFullYear()) * 12 + (target.getMonth() - today.getMonth())
  return Math.max(1, months)
}

/** Aporte mensal sugerido: valor-alvo ÷ meses, arredondado para cima no centavo. */
export function suggestedMonthlyAllocation(targetAmount: number, months: number): number {
  const cents = Math.round(targetAmount * 100)
  return Math.ceil(cents / Math.max(1, months)) / 100
}

/** Meses para chegar ao valor-alvo com o aporte (arredondado para cima). null sem aporte positivo. */
export function monthsToReach(targetAmount: number, monthlyAllocation: number): number | null {
  if (!(monthlyAllocation > 0)) return null
  return Math.ceil(Math.round(targetAmount * 100) / Math.round(monthlyAllocation * 100))
}

/**
 * Previsão de conclusão a partir de hoje com o aporte mensal; null sem aporte positivo e 'too-far' acima de
 * MAX_FORECAST_MONTHS.
 */
export function projectedCompletion(
  today: Date,
  targetAmount: number,
  monthlyAllocation: number,
): Date | 'too-far' | null {
  const months = monthsToReach(targetAmount, monthlyAllocation)
  if (months === null) return null
  if (months > MAX_FORECAST_MONTHS) return 'too-far'
  return addMonths(today, months)
}

const MONTH_YEAR = new Intl.DateTimeFormat('pt-BR', { month: 'long', year: 'numeric' })

/** Mês e ano por extenso, ex.: "novembro de 2027". */
export function formatMonthYear(date: Date): string {
  return MONTH_YEAR.format(date)
}
