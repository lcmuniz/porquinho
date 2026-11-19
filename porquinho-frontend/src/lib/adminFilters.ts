import type { LocationQuery } from 'vue-router'
import type { AdminBankCode, ImportMetricsFilters } from '@/types/admin'
import { BANK_LABELS } from '@/types/transactionImport'

const DATE = /^\d{4}-\d{2}-\d{2}$/

/**
 * Retro do Epic 10, A4: o link do alerta por banco abre a aba já filtrada (?bank=ITAU&from=AAAA-MM-DD&to=AAAA-MM-DD).
 * Valor fora do formato é ignorado (a aba abre no padrão de 30 dias e todos os bancos).
 */
export function filtersFromQuery(query: LocationQuery): ImportMetricsFilters {
  const one = (v: unknown) => (typeof v === 'string' ? v : undefined)
  const from = one(query.from)
  const to = one(query.to)
  const bank = one(query.bank)
  return {
    ...(from && DATE.test(from) ? { from } : {}),
    ...(to && DATE.test(to) ? { to } : {}),
    ...(bank && (bank in BANK_LABELS || bank === 'UNKNOWN') ? { bank: bank as AdminBankCode } : {}),
  }
}
