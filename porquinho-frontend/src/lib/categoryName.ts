/**
 * Story 3.5: mesmo tratamento do backend (CategoryService.cleanName): Unicode NFC e sem espaços nem caracteres
 * invisíveis nas pontas (espaços Unicode como NBSP e formatação como U+200B e U+FEFF).
 */
export function cleanCategoryName(value: string): string {
  return value.normalize('NFC').replace(/^[\s\p{Z}\p{Cf}]+|[\s\p{Z}\p{Cf}]+$/gu, '')
}
