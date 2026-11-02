const BRL = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

/** Maior valor que cabe em DECIMAL(15,2) no backend (13 dígitos antes da vírgula). */
export const MAX_ABS_AMOUNT = 9_999_999_999_999.99

// "1.234,56", "1234,56", "-150", "1.234" (milhar), com "R$" e espaços opcionais. Até 2 casas.
const BRL_INPUT = /^(-)?(\d{1,3}(?:\.\d{3})+|\d+)(?:,(\d{1,2}))?$/

/**
 * Converte um valor digitado no formato brasileiro em número ("1.234,56" → 1234.56).
 * Aceita sinal de menos (cheque especial). Devolve null se o texto for vazio ou inválido
 * ou se o valor não couber em DECIMAL(15,2).
 */
export function parseBRL(input: string | null | undefined): number | null {
  if (input == null) return null
  const text = input.replace(/R\$/g, '').replace(/\s/g, '')
  const match = BRL_INPUT.exec(text)
  if (!match) return null
  const [, sign, integer, cents = ''] = match
  const value = Number(`${integer!.replace(/\./g, '')}.${cents.padEnd(2, '0')}`)
  if (!Number.isFinite(value) || value > MAX_ABS_AMOUNT) return null
  const signed = sign ? -value : value
  return signed === 0 ? 0 : signed
}

/** Valor em reais no formato brasileiro, ex.: 1234.56 → "R$ 1.234,56" (com espaço não separável). */
export function formatBRL(value: number): string {
  return BRL.format(value)
}

const BRL_INPUT_FORMAT = new Intl.NumberFormat('pt-BR', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

/** Valor para pré-preencher um campo de dinheiro, sem "R$": 25000 → "25.000,00" (aceito por parseBRL). */
export function formatBRLInput(value: number): string {
  return BRL_INPUT_FORMAT.format(value)
}
