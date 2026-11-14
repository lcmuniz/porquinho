import { describe, it, expect } from 'vitest'
import { fileSlug } from '../download'

describe('fileSlug', () => {
  it('tira acentos e símbolos', () => {
    expect(fileSlug('Alimentação & Casa', 'categoria')).toBe('alimentacao-casa')
  })

  it('usa o fallback quando não sobra nada', () => {
    expect(fileSlug('***', 'meta')).toBe('meta')
  })
})
