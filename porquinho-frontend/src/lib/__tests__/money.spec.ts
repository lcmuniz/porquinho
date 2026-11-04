import { describe, it, expect } from 'vitest'
import { formatBRL, formatBRLInput, formatSignedBRL, parseBRL } from '../money'

/** Intl usa espaço não separável depois de "R$"; normaliza para comparar. */
const plain = (s: string) => s.replace(/\s/g, ' ')

describe('parseBRL', () => {
  it.each([
    ['1.234,56', 1234.56],
    ['1234,56', 1234.56],
    ['1234,5', 1234.5],
    ['1234', 1234],
    ['1.234', 1234],
    ['0', 0],
    ['0,00', 0],
    ['-0,00', 0],
    ['-150,00', -150],
    ['R$ 1.234,56', 1234.56],
    ['  12,30  ', 12.3],
    ['1.000.000,99', 1000000.99],
    ['9.999.999.999.999,99', 9999999999999.99],
  ])('"%s" → %s', (input, expected) => {
    expect(parseBRL(input)).toBe(expected)
  })

  it.each([
    [''],
    ['   '],
    ['abc'],
    ['1,234'],
    ['12.34'],
    ['1.23,00'],
    ['1,2,3'],
    ['--1'],
    ['1-'],
    ['10.000.000.000.000,00'],
  ])('"%s" é inválido', (input) => {
    expect(parseBRL(input)).toBeNull()
  })

  it('null e undefined são inválidos', () => {
    expect(parseBRL(null)).toBeNull()
    expect(parseBRL(undefined)).toBeNull()
  })
})

describe('formatBRL', () => {
  it('formata em reais no padrão brasileiro', () => {
    expect(plain(formatBRL(1234.56))).toBe('R$ 1.234,56')
    expect(plain(formatBRL(0))).toBe('R$ 0,00')
  })

  it('formata valores negativos (cheque especial)', () => {
    expect(plain(formatBRL(-150))).toBe('-R$ 150,00')
  })
})

describe('formatBRLInput', () => {
  it('formata sem "R$" e o resultado volta pelo parseBRL', () => {
    expect(formatBRLInput(25000)).toBe('25.000,00')
    expect(formatBRLInput(2083.34)).toBe('2.083,34')
    expect(formatBRLInput(0.5)).toBe('0,50')
    expect(parseBRL(formatBRLInput(1234567.89))).toBe(1234567.89)
  })
})

describe('formatSignedBRL', () => {
  it('mostra o sinal de mais nos positivos e o de menos nos negativos', () => {
    expect(plain(formatSignedBRL(50))).toBe('+R$ 50,00')
    expect(plain(formatSignedBRL(-20))).toBe('-R$ 20,00')
  })

  it('zero (inclusive -0) fica sem sinal', () => {
    expect(plain(formatSignedBRL(0))).toBe('R$ 0,00')
    expect(plain(formatSignedBRL(-0))).toBe('R$ 0,00')
  })
})
