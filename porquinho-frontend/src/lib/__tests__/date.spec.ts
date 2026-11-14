import { describe, it, expect } from 'vitest'
import { dateInSaoPaulo, formatLocalDateBR } from '../date'

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

describe('dateInSaoPaulo', () => {
  it('converte o instante para o dia em São Paulo', () => {
    expect(dateInSaoPaulo('2026-11-14T02:30:00Z')).toBe('2026-11-13')
    expect(dateInSaoPaulo(new Date('2026-11-14T03:00:00Z'))).toBe('2026-11-14')
  })
})
