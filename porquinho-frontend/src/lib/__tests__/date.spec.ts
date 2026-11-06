import { describe, it, expect } from 'vitest'
import { formatLocalDateBR } from '../date'

describe('formatLocalDateBR', () => {
  it('mostra o mesmo dia do texto, sem conversão de fuso', () => {
    expect(formatLocalDateBR('2026-11-03')).toBe('03/11/2026')
    expect(formatLocalDateBR('2026-01-01')).toBe('01/01/2026')
  })

  it('devolve vazio sem data ou com texto inválido', () => {
    expect(formatLocalDateBR(null)).toBe('')
    expect(formatLocalDateBR(undefined)).toBe('')
    expect(formatLocalDateBR('')).toBe('')
    expect(formatLocalDateBR('03/11/2026')).toBe('')
  })
})
