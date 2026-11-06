/** Contagem com o substantivo no singular ou no plural: 1 → "1 transação", 2 → "2 transações". */
export function plural(count: number, singular: string, pluralForm: string): string {
  return `${count} ${count === 1 ? singular : pluralForm}`
}
