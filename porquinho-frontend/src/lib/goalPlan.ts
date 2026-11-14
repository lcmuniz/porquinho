/**
 * Story 3.8: cálculos do plano de uma meta (aporte mensal sugerido e previsão de conclusão). Datas são
 * datas de calendário (AAAA-MM-DD) representadas como meia-noite local; "hoje" é o dia em America/Sao_Paulo,
 * o mesmo critério do backend (NotPastInSaoPaulo).
 */
import { cleanCategoryName } from './categoryName'
import { parseBRL } from './money'
import type { CreateGoalInput, GoalTemplate } from '@/types/goal'

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

/** Story 8.6: modelos de meta (FR76). A data-alvo é hoje + `months`. */
export const GOAL_TEMPLATES: {
  code: GoalTemplate
  name: string
  targetAmount: number
  months: number
  description: string
}[] = [
  {
    code: 'EMERGENCY_FUND',
    name: 'Reserva de Emergência',
    targetAmount: 25000,
    months: 12,
    description: '6 meses de despesas',
  },
  { code: 'VACATION', name: 'Férias', targetAmount: 8000, months: 6, description: 'Viagem de férias' },
  {
    code: 'BIG_PURCHASE',
    name: 'Compra Grande',
    targetAmount: 15000,
    months: 12,
    description: 'Eletrodoméstico, móvel, eletrônico',
  },
]

export type GoalField = 'name' | 'targetAmount' | 'targetDate' | 'monthlyAllocation' | 'description'
export const GOAL_FIELDS: GoalField[] = ['name', 'targetAmount', 'targetDate', 'monthlyAllocation', 'description']

/** Campos do formulário como texto. Sem `description` (onboarding), ela não vai no payload. */
export interface GoalForm {
  name: string
  targetAmount: string
  targetDate: string
  monthlyAllocation: string
  description?: string
}

/**
 * Story 8.1: validação local do formulário de meta (onboarding e tela de metas), com as mesmas regras e
 * mensagens do backend (CreateGoalRequest). `values` é null quando há algum erro.
 */
export function validateGoalForm(
  form: GoalForm,
  today: Date,
): { values: CreateGoalInput | null; errors: Partial<Record<GoalField, string>> } {
  const errors: Partial<Record<GoalField, string>> = {}
  const name = cleanCategoryName(form.name)
  if (!name) errors.name = 'Informe o nome da meta'
  else if (name.length > 100) errors.name = 'Use no máximo 100 caracteres no nome da meta'

  let targetAmount: number | null = null
  if (!form.targetAmount.trim()) {
    errors.targetAmount = 'Informe o valor-alvo da meta'
  } else {
    targetAmount = parseBRL(form.targetAmount)
    if (targetAmount === null) {
      errors.targetAmount = 'Digite o valor no formato 25.000,00 (até 2 casas depois da vírgula)'
    } else if (targetAmount <= 0) {
      errors.targetAmount = 'O valor-alvo precisa ser maior que zero'
    }
  }

  let targetDate: string | null = null
  if (form.targetDate) {
    const date = parseISODate(form.targetDate)
    if (date === null) errors.targetDate = 'Escolha uma data válida'
    else if (date < today) errors.targetDate = 'Escolha uma data de hoje em diante'
    else targetDate = form.targetDate
  }

  let monthlyAllocation: number | null = null
  if (form.monthlyAllocation.trim()) {
    monthlyAllocation = parseBRL(form.monthlyAllocation)
    if (monthlyAllocation === null) {
      errors.monthlyAllocation = 'Digite o aporte no formato 2.083,34 (até 2 casas depois da vírgula)'
    } else if (monthlyAllocation < 0) {
      errors.monthlyAllocation = 'O aporte mensal não pode ser negativo'
    }
  }

  const values: CreateGoalInput = { name, targetAmount: targetAmount ?? 0, targetDate, monthlyAllocation }
  if (form.description !== undefined) {
    const description = cleanCategoryName(form.description)
    if (description.length > 500) errors.description = 'Use no máximo 500 caracteres na descrição'
    values.description = description || null
  }

  if (Object.keys(errors).length > 0 || targetAmount === null) return { values: null, errors }
  return { values, errors }
}
