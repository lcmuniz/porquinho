import { describe, it, expect, vi, beforeEach } from 'vitest'
import { AxiosError, type InternalAxiosRequestConfig } from 'axios'

const mockPush = vi.fn()
const mockRefresh = vi.fn()
const mockSignOut = vi.fn()
const mockClearUser = vi.fn()
const currentRoute = { value: { path: '/dashboard', fullPath: '/dashboard', meta: { requiresAuth: true } } }

vi.mock('@/router', () => ({ default: { push: (...a: unknown[]) => mockPush(...a), currentRoute } }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ clearUser: mockClearUser }) }))
vi.mock('../supabase', () => ({
  supabase: {
    auth: {
      getSession: async () => ({ data: { session: { access_token: 't' } } }),
      refreshSession: () => mockRefresh(),
      signOut: (...a: unknown[]) => mockSignOut(...a),
    },
  },
}))

async function loadApi() {
  vi.stubEnv('VITE_API_BASE_URL', 'http://api.test')
  return (await import('../api')).api
}

function respondWith(status: number, data: unknown = {}) {
  return async (config: InternalAxiosRequestConfig) => {
    throw new AxiosError('fail', 'ERR_BAD_REQUEST', config, null, {
      status,
      statusText: '',
      headers: {},
      config,
      data,
    })
  }
}

/** 401 na primeira chamada; nas seguintes responde 200 com o token que recebeu. */
function unauthorizedOnce() {
  let calls = 0
  return async (config: InternalAxiosRequestConfig) => {
    if (calls++ === 0) return respondWith(401)(config)
    return { data: config.headers.Authorization, status: 200, statusText: '', headers: {}, config }
  }
}

describe('api response interceptor', () => {
  beforeEach(() => {
    mockPush.mockReset()
    mockRefresh.mockReset()
    mockSignOut.mockReset().mockResolvedValue({ error: null })
    mockClearUser.mockReset()
    currentRoute.value.path = '/dashboard'
    currentRoute.value.fullPath = '/dashboard'
    currentRoute.value.meta.requiresAuth = true
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

  it('401 renova a sessão e repete a requisição uma vez com o token novo', async () => {
    mockRefresh.mockResolvedValue({ data: { session: { access_token: 'novo' } }, error: null })
    const api = await loadApi()

    const response = await api.get('/api/v1/budgets', { adapter: unauthorizedOnce() })

    expect(mockRefresh).toHaveBeenCalledTimes(1)
    expect(response.data).toBe('Bearer novo')
    expect(mockClearUser).not.toHaveBeenCalled()
    expect(mockPush).not.toHaveBeenCalled()
  })

  it('401 sem refresh faz logout local e vai ao /login com a página atual em redirectTo', async () => {
    currentRoute.value.fullPath = '/transacoes?mes=2026-11'
    mockRefresh.mockResolvedValue({ data: { session: null }, error: new Error('refresh token revogado') })
    const api = await loadApi()

    await expect(api.get('/api/v1/budgets', { adapter: respondWith(401) })).rejects.toBeInstanceOf(AxiosError)
    expect(mockClearUser).toHaveBeenCalled()
    expect(mockSignOut).toHaveBeenCalledWith({ scope: 'local' })
    expect(mockPush).toHaveBeenCalledWith({ name: 'login', query: { redirectTo: '/transacoes?mes=2026-11' } })
  })

  it('401 de novo depois do refresh também encerra a sessão', async () => {
    mockRefresh.mockResolvedValue({ data: { session: { access_token: 'novo' } }, error: null })
    const api = await loadApi()

    await expect(api.get('/api/v1/budgets', { adapter: respondWith(401) })).rejects.toBeInstanceOf(AxiosError)
    expect(mockRefresh).toHaveBeenCalledTimes(1)
    expect(mockClearUser).toHaveBeenCalled()
  })

  it('403 de conta em exclusão encerra a sessão e avisa no /login', async () => {
    const api = await loadApi()

    await expect(
      api.get('/api/v1/budgets', { adapter: respondWith(403, { title: 'Conta em exclusão' }) }),
    ).rejects.toBeInstanceOf(AxiosError)
    expect(mockClearUser).toHaveBeenCalled()
    expect(mockPush).toHaveBeenCalledWith({
      name: 'login',
      query: { redirectTo: '/dashboard', msg: 'account-deletion-scheduled' },
    })
  })

  it('outro 403 não encerra a sessão', async () => {
    const api = await loadApi()

    await expect(api.get('/api/v1/budgets', { adapter: respondWith(403) })).rejects.toBeInstanceOf(AxiosError)
    expect(mockClearUser).not.toHaveBeenCalled()
  })
})
