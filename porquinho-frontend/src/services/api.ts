import axios from 'axios'
import { supabase } from './supabase'
import router from '@/router'
import { endSession } from './session'
import { isDeletedAccountError } from '@/lib/apiError'

const apiBaseURL = import.meta.env.VITE_API_BASE_URL

if (!apiBaseURL) {
  throw new Error('VITE_API_BASE_URL environment variable is not set')
}

/**
 * Axios instance configured for API requests to the backend.
 * Automatically attaches JWT token from Supabase session.
 */
export const api = axios.create({
  baseURL: apiBaseURL,
  headers: {
    'Content-Type': 'application/json',
  },
})

// Request interceptor: Attach JWT token from Supabase session
api.interceptors.request.use(
  async (config) => {
    // Token já informado (login recém-feito, repetição após o refresh do 401): usa esse
    if (config.headers.Authorization) return config
    try {
      // Try to get session, with retry logic for recently authenticated users
      let session = null
      let attempts = 0
      const maxAttempts = 3

      while (!session && attempts < maxAttempts) {
        const { data } = await supabase.auth.getSession()
        session = data.session

        if (!session && attempts < maxAttempts - 1) {
          // Wait a bit before retrying (session might be persisting to storage)
          await new Promise(resolve => setTimeout(resolve, 200))
        }
        attempts++
      }

      if (session?.access_token) {
        config.headers.Authorization = `Bearer ${session.access_token}`
      }
    } catch (error) {
      console.error('Error getting session for request interceptor:', error)
      // Continue without token - backend will return 401 if authentication is required
    }

    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

export const SUBSCRIPTION_ROUTE = '/assinatura'

// Response interceptor: 402 -> tela de assinatura (Story 2.2); 403 conta em exclusão e 401 sem refresh -> /login
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config

    // 402: trial vencido ou sem assinatura. Leva para a tela de assinatura (sem loop se já estiver lá).
    if (error.response?.status === 402) {
      if (router.currentRoute.value.path !== SUBSCRIPTION_ROUTE) {
        router.push(SUBSCRIPTION_ROUTE)
      }
      return Promise.reject(error)
    }

    // 403 do DeletedAccountFilter: a conta está em exclusão e o login não vale mais.
    if (isDeletedAccountError(error)) {
      endSession({ msg: 'account-deletion-scheduled' })
      return Promise.reject(error)
    }

    // 401: token vencido ou revogado. Renova uma vez e repete; se não renovar (ou o repetido também der 401), a sessão
    // acabou: logout local e /login com a página atual em redirectTo.
    if (error.response?.status === 401 && originalRequest) {
      if (!originalRequest._retry) {
        originalRequest._retry = true
        const token = await supabase.auth
          .refreshSession()
          .then(({ data, error: refreshError }) => (refreshError ? null : data.session?.access_token))
          .catch(() => null)
        if (token) {
          originalRequest.headers.Authorization = `Bearer ${token}`
          return api(originalRequest)
        }
      }
      endSession()
    }

    return Promise.reject(error)
  }
)

/**
 * Public API instance without authentication interceptors.
 * Used for endpoints that don't require JWT token (e.g., /login/check, /login/failed).
 */
export const publicApi = axios.create({
  baseURL: apiBaseURL,
  headers: {
    'Content-Type': 'application/json',
  },
})
