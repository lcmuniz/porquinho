import { describe, it, expect } from 'vitest'
import { apiErrorDetail, apiErrorStatus } from '../apiError'

describe('apiError', () => {
  const problem = { response: { status: 409, data: { detail: 'Mudou em outra tela' } } }

  it('lê status e detail do erro da API', () => {
    expect(apiErrorStatus(problem)).toBe(409)
    expect(apiErrorDetail(problem, 'padrão')).toBe('Mudou em outra tela')
  })

  it('usa o fallback sem detail e aceita erro sem resposta', () => {
    expect(apiErrorDetail({ response: { status: 500, data: {} } }, 'padrão')).toBe('padrão')
    expect(apiErrorDetail(new Error('Network Error'))).toBeUndefined()
    expect(apiErrorStatus(undefined)).toBeUndefined()
  })
})
