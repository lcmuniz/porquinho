import { plural } from '@/lib/plural'
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
