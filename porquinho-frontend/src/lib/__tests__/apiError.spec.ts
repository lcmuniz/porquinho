import { describe, it, expect } from 'vitest'
import { apiErrorDetail, apiErrorStatus, isDeletedAccountError, isRegistrationRefused } from '../apiError'

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

  it('reconhece conta em exclusão: só 403 com o título', () => {
    expect(isDeletedAccountError({ response: { status: 403, data: { title: 'Conta em exclusão' } } })).toBe(true)
    expect(isDeletedAccountError({ response: { status: 409, data: {} } })).toBe(false)
    expect(isDeletedAccountError({ response: { status: 403, data: { title: 'Forbidden' } } })).toBe(false)
    expect(isDeletedAccountError({ response: { status: 500 } })).toBe(false)
    expect(isDeletedAccountError(new Error('Network Error'))).toBe(false)
  })

  it('reconhece cadastro recusado: conta em exclusão ou qualquer 409', () => {
    expect(isRegistrationRefused({ response: { status: 403, data: { title: 'Conta em exclusão' } } })).toBe(true)
    expect(isRegistrationRefused({ response: { status: 409, data: {} } })).toBe(true)
    expect(isRegistrationRefused({ response: { status: 403, data: { title: 'Forbidden' } } })).toBe(false)
    expect(isRegistrationRefused({ response: { status: 500 } })).toBe(false)
    expect(isRegistrationRefused(new Error('Network Error'))).toBe(false)
  })
})
