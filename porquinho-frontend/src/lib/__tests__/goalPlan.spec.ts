import { describe, it, expect } from 'vitest'
import {
  addMonths,
  formatMonthYear,
  monthsToReach,
  monthsUntil,
  parseISODate,
  projectedCompletion,
  todayInSaoPaulo,
  suggestedMonthlyAllocation,
  toISODate,
} from '../goalPlan'

const d = (text: string) => parseISODate(text)!

describe('goalPlan', () => {
  it('converte entre Date local e AAAA-MM-DD', () => {
    expect(toISODate(new Date(2026, 10, 2))).toBe('2026-11-02')
    expect(toISODate(d('2027-01-09'))).toBe('2027-01-09')
    expect(parseISODate('2027-02-30')).toBeNull()
    expect(parseISODate('')).toBeNull()
    expect(parseISODate('02/11/2027')).toBeNull()
  })

  it('todayInSaoPaulo usa o dia em São Paulo (UTC-3), não o UTC nem o fuso do aparelho', () => {
    // 01:30 UTC de 03/11 ainda é 22:30 de 02/11 em São Paulo
    expect(toISODate(todayInSaoPaulo(new Date('2026-11-03T01:30:00Z')))).toBe('2026-11-02')
    // 03:00 UTC de 03/11 já é meia-noite de 03/11 em São Paulo
    expect(toISODate(todayInSaoPaulo(new Date('2026-11-03T03:00:00Z')))).toBe('2026-11-03')
  })

  it('addMonths soma meses de calendário e limita o dia ao fim do mês', () => {
    expect(toISODate(addMonths(d('2026-11-02'), 12))).toBe('2027-11-02')
    expect(toISODate(addMonths(d('2027-01-31'), 1))).toBe('2027-02-28')
    expect(toISODate(addMonths(d('2028-01-31'), 1))).toBe('2028-02-29')
    expect(toISODate(addMonths(d('2026-12-15'), 2))).toBe('2027-02-15')
  })

  it('monthsUntil conta meses de calendário, no mínimo 1', () => {
    expect(monthsUntil(d('2026-11-02'), d('2027-11-02'))).toBe(12)
    expect(monthsUntil(d('2026-11-02'), d('2027-11-30'))).toBe(12)
    expect(monthsUntil(d('2026-11-30'), d('2026-12-01'))).toBe(1)
    expect(monthsUntil(d('2026-11-02'), d('2026-11-20'))).toBe(1)
    expect(monthsUntil(d('2026-11-02'), d('2026-11-02'))).toBe(1)
    expect(monthsUntil(d('2026-11-02'), d('2027-05-10'))).toBe(6)
  })

  it('aporte sugerido = valor ÷ meses, arredondado para cima no centavo', () => {
    expect(suggestedMonthlyAllocation(25000, 12)).toBe(2083.34)
    expect(suggestedMonthlyAllocation(1200, 12)).toBe(100)
    expect(suggestedMonthlyAllocation(100, 3)).toBe(33.34)
    expect(suggestedMonthlyAllocation(0.01, 12)).toBe(0.01)
    expect(suggestedMonthlyAllocation(500, 0)).toBe(500)
  })

  it('monthsToReach arredonda para cima e devolve null sem aporte positivo', () => {
    expect(monthsToReach(25000, 2083.34)).toBe(12)
    expect(monthsToReach(25000, 2083.33)).toBe(13)
    expect(monthsToReach(25000, 1000)).toBe(25)
    expect(monthsToReach(25000, 0)).toBeNull()
    expect(monthsToReach(25000, -1)).toBeNull()
  })

  it('previsão de conclusão conta os meses a partir de hoje', () => {
    expect(toISODate(projectedCompletion(d('2026-11-02'), 25000, 2083.34)!)).toBe('2027-11-02')
    expect(toISODate(projectedCompletion(d('2026-11-02'), 25000, 1000)!)).toBe('2028-12-02')
    expect(projectedCompletion(d('2026-11-02'), 25000, 0)).toBeNull()
    // 25.000 ÷ 0,01 = 2.500.000 meses: acima de 100 anos, sem montar uma data inválida
    expect(projectedCompletion(d('2026-11-02'), 25000, 0.01)).toBe('too-far')
    expect(projectedCompletion(d('2026-11-02'), 1200, 1)).toBeInstanceOf(Date)
  })

  it('formata mês e ano por extenso', () => {
    expect(formatMonthYear(d('2027-11-02'))).toBe('novembro de 2027')
  })
})
