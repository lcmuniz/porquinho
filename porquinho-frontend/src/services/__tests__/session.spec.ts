import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockPush = vi.fn()
const mockClearUser = vi.fn()
let listener: ((event: string) => void) | undefined
const { currentRoute } = vi.hoisted(() => ({
  currentRoute: { value: { fullPath: '/metas', meta: { requiresAuth: true as boolean } } },
}))

vi.mock('@/router', () => ({ default: { push: (...a: unknown[]) => mockPush(...a), currentRoute } }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ clearUser: mockClearUser }) }))
vi.mock('../supabase', () => ({
  supabase: {
    auth: {
      onAuthStateChange: vi.fn((cb: (event: string) => void) => {
        listener = cb
        return { data: { subscription: { unsubscribe: vi.fn() } } }
      }),
    },
  },
}))

import { registerAuthListener } from '../session'
import { supabase } from '../supabase'

describe('registerAuthListener', () => {
  beforeEach(() => {
    mockPush.mockReset()
    mockClearUser.mockReset()
    currentRoute.value = { fullPath: '/metas', meta: { requiresAuth: true } }
    registerAuthListener()
  })

  it('registra o ouvinte uma vez só', () => {
    registerAuthListener()
    expect(supabase.auth.onAuthStateChange).toHaveBeenCalledTimes(1)
  })

  it('SIGNED_OUT limpa o usuário e vai ao /login guardando a página', () => {
    listener!('SIGNED_OUT')

    expect(mockClearUser).toHaveBeenCalled()
    expect(mockPush).toHaveBeenCalledWith({ name: 'login', query: { redirectTo: '/metas' } })
  })

  it('SIGNED_OUT numa tela pública só limpa o usuário', () => {
    currentRoute.value = { fullPath: '/login', meta: { requiresAuth: false } }
    listener!('SIGNED_OUT')

    expect(mockClearUser).toHaveBeenCalled()
    expect(mockPush).not.toHaveBeenCalled()
  })

  it('TOKEN_REFRESHED não mexe na sessão', () => {
    listener!('TOKEN_REFRESHED')

    expect(mockClearUser).not.toHaveBeenCalled()
    expect(mockPush).not.toHaveBeenCalled()
  })
})
