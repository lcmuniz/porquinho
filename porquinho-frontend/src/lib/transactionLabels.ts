import { plural } from '@/lib/plural'
import type { Account } from '@/types/account'
import type { Category } from '@/types/category'
import type { CreditCard } from '@/types/creditCard'
import type { Transaction } from '@/types/transaction'
import type { PixDetails } from '@/types/transactionImport'

/** Pix sempre com texto, nunca só ícone (Stories 5.9 e 5.12). */
export function pixLabel(row: { pixDetails: PixDetails | null }): string {
  const details = row.pixDetails
  if (!details) return 'Pix'
  const name = details.counterpart_name
  if (details.direction === 'SENT') return name ? `Pix enviado para ${name}` : 'Pix enviado'
  return name ? `Pix recebido de ${name}` : 'Pix recebido'
}

/**
 * "Compra parcelada: 3/10 — restam 7 parcelas" (Stories 5.9 e 5.12). `remainingInstallments`, quando vem, são as
 * parcelas futuras ainda ativas: depois de "Remover parcelas futuras" o texto fica sem "restam".
 */
export function installmentLabel(row: {
  isInstallment: boolean
  installmentNumber: number | null
  installmentTotal: number | null
  remainingInstallments?: number
}): string {
  if (!row.isInstallment || row.installmentNumber === null || row.installmentTotal === null) return ''
  const progress = `Compra parcelada: ${row.installmentNumber}/${row.installmentTotal}`
  if (row.installmentNumber >= row.installmentTotal) return `${progress} — última parcela`
  const left = row.remainingInstallments ?? row.installmentTotal - row.installmentNumber
  if (left === 0) return progress
  return `${progress} — ${left === 1 ? 'resta' : 'restam'} ${plural(left, 'parcela', 'parcelas')}`
}

/** Valor com sinal: saída negativa, entrada positiva (Stories 5.12 e 7.6). */
export function signedAmount(row: { type: 'DEBIT' | 'CREDIT'; amount: number }): number {
  return row.type === 'DEBIT' ? -row.amount : row.amount
}

/** "Grupo › Categoria", "Pagamento de fatura" ou "Não classificado" (Stories 5.12 e 7.6). */
export function categoryLabel(row: Pick<Transaction, 'kind' | 'category'>): string {
  if (row.kind === 'CARD_PAYMENT') return 'Pagamento de fatura'
  if (!row.category) return 'Não classificado'
  return row.category.group ? `${row.category.group} › ${row.category.name}` : row.category.name
}

/** Nome da conta ou do cartão; fora das listas de ativos, "Conta removida"/"Cartão removido" (Stories 5.12 e 7.6). */
export function targetName(
  row: Pick<Transaction, 'accountId' | 'creditCardId'>,
  accounts: Pick<Account, 'id' | 'accountName'>[],
  cards: Pick<CreditCard, 'id' | 'cardName'>[],
): string {
  if (row.accountId) return accounts.find((a) => a.id === row.accountId)?.accountName ?? 'Conta removida'
  return cards.find((c) => c.id === row.creditCardId)?.cardName ?? 'Cartão removido'
}

/**
 * Grupos do select de categoria da transação, em ordem alfabética (Stories 5.10 e 5.13). Grupos sem categoria filha
 * ficam de fora: grupo não é categoria de transação.
 */
export function categoryOptionGroups(categories: Category[]): { id: string; name: string; children: Category[] }[] {
  const byName = (a: Category, b: Category) => a.categoryName.localeCompare(b.categoryName, 'pt-BR')
  return categories
    .filter((category) => category.parentCategoryId === null)
    .sort(byName)
    .map((parent) => ({
      id: parent.id,
      name: parent.categoryName,
      children: categories.filter((category) => category.parentCategoryId === parent.id).sort(byName),
    }))
    .filter((group) => group.children.length > 0)
}
