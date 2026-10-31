import { describe, it, expect, vi, beforeEach } from 'vitest'
import { AxiosError, type InternalAxiosRequestConfig } from 'axios'

const mockPush = vi.fn()
const currentRoute = { value: { path: '/dashboard' } }

vi.mock('@/router', () => ({ default: { push: (...a: unknown[]) => mockPush(...a), currentRoute } }))
vi.mock('../supabase', () => ({
  supabase: { auth: { getSession: async () => ({ data: { session: { access_token: 't' } } }) } },
}))

async function loadApi() {
  vi.stubEnv('VITE_API_BASE_URL', 'http://api.test')
  return (await import('../api')).api
}

function respondWith(status: number) {
  return async (config: InternalAxiosRequestConfig) => {
    throw new AxiosError('fail', 'ERR_BAD_REQUEST', config, null, {
      status,
      statusText: '',
      headers: {},
      config,
      data: {},
    })
  }
}

describe('api response interceptor', () => {
  beforeEach(() => {
    mockPush.mockReset()
    currentRoute.value.path = '/dashboard'
  })

  it('redireciona para /assinatura quando a API responde 402', async () => {
    const api = await loadApi()

    await expect(api.get('/api/v1/budgets', { adapter: respondWith(402) })).rejects.toBeInstanceOf(AxiosError)
    expect(mockPush).toHaveBeenCalledWith('/assinatura')
  })

  it('não redireciona de novo se já está em /assinatura', async () => {
    currentRoute.value.path = '/assinatura'
    const api = await loadApi()

    await expect(api.get('/api/v1/budgets', { adapter: respondWith(402) })).rejects.toBeInstanceOf(AxiosError)
    expect(mockPush).not.toHaveBeenCalled()
  })

  it('não redireciona em outros erros', async () => {
    const api = await loadApi()

    await expect(api.get('/api/v1/budgets', { adapter: respondWith(500) })).rejects.toBeInstanceOf(AxiosError)
    expect(mockPush).not.toHaveBeenCalled()
  })
})
