/** Baixa o blob com o nome dado, via link temporário. */
export function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  // revogar na hora pode abortar o download em alguns navegadores (Firefox)
  setTimeout(() => URL.revokeObjectURL(url), 0)
}

/** CSV para o Excel pt-BR: BOM (para o UTF-8) e CRLF; as linhas já vêm com `;`. */
export function downloadCsv(lines: string[], filename: string): void {
  downloadBlob(new Blob(['﻿' + lines.join('\r\n') + '\r\n'], { type: 'text/csv;charset=utf-8' }), filename)
}

/** Nome para arquivo: sem acento, minúsculo, só letras, dígitos e hífen; `fallback` quando não sobra nada. */
export function fileSlug(name: string, fallback: string): string {
  const s = name
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
  return s || fallback
}
