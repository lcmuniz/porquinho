import { describe, it, expect } from 'vitest'
import { filtersFromQuery } from '../adminFilters'

describe('filtersFromQuery', () => {
  it('lê banco e período do link do alerta', () => {
    expect(filtersFromQuery({ tab: 'imports', bank: 'ITAU', from: '2026-11-19', to: '2026-11-19' })).toEqual({
      bank: 'ITAU',
      from: '2026-11-19',
      to: '2026-11-19',
    })
    expect(filtersFromQuery({ bank: 'UNKNOWN' })).toEqual({ bank: 'UNKNOWN' })
  })

  it('ignora banco desconhecido, data fora do formato e valores repetidos', () => {
    expect(filtersFromQuery({ bank: 'SANTANDER', from: '19/11/2026', to: ['2026-11-19', '2026-11-20'] })).toEqual({})
    expect(filtersFromQuery({})).toEqual({})
  })
})
