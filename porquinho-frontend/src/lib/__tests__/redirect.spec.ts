import { describe, it, expect } from 'vitest'
import { safeRedirect } from '../redirect'

describe('safeRedirect', () => {
  it('aceita caminho interno com query', () => {
    expect(safeRedirect('/transacoes?mes=2026-11')).toBe('/transacoes?mes=2026-11')
  })

  it.each(['//evil.com', '/\\evil.com', 'https://evil.com', 'evil.com', '', undefined, ['/a']])(
    'recusa destino externo ou inválido: %s',
    (value) => {
      expect(safeRedirect(value)).toBeNull()
    },
  )
})
