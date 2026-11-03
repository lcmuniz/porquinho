import type { AccountType } from '@/types/account'

/** Rótulo de cada tipo de conta bancária (Stories 3.2 e 4.1). */
export const ACCOUNT_TYPE_LABEL: Record<AccountType, string> = {
  CHECKING: 'Conta corrente',
  SAVINGS: 'Poupança',
}

export const ACCOUNT_TYPE_OPTIONS = (Object.keys(ACCOUNT_TYPE_LABEL) as AccountType[]).map(
  (value) => ({ value, label: ACCOUNT_TYPE_LABEL[value] }),
)
