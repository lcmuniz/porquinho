import axios from 'axios'
import { supabase } from './supabase'

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
    try {
      const {
        data: { session },
      } = await supabase.auth.getSession()

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

// Response interceptor: Handle 401 errors and refresh token
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config

    // If 401 and not already retried, try to refresh token
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true

      try {
        // Supabase automatically refreshes token if needed
        const {
          data: { session },
        } = await supabase.auth.getSession()

        if (session?.access_token) {
          originalRequest.headers.Authorization = `Bearer ${session.access_token}`
          return api(originalRequest)
        }
      } catch (refreshError) {
        console.error('Error refreshing token:', refreshError)
        // Redirect to login or handle as needed
        return Promise.reject(refreshError)
      }
    }

    return Promise.reject(error)
  }
)
