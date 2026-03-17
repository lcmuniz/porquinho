import { api } from './api'

export interface RegisterGoogleRequest {
  email: string
  googleId: string
  name?: string
}

export interface RegisterEmailRequest {
  email: string
  name: string
}

export interface UserResponse {
  id: string
  email: string
  authProvider: string
  createdAt: string
}

/**
 * Authentication service for backend API calls.
 * Handles user registration and authentication operations.
 */
export const authService = {
  /**
   * Register or login user with Google OAuth.
   * Calls backend to create user record if needed.
   *
   * @param data User data from Google OAuth
   * @returns User data from backend
   */
  async registerWithGoogle(
    data: RegisterGoogleRequest
  ): Promise<UserResponse> {
    const response = await api.post<UserResponse>(
      '/api/v1/auth/register/google',
      data
    )
    return response.data
  },

  /**
   * Register user with email/password.
   * Calls backend to create user record after Supabase Auth registration.
   * JWT token is automatically included in Authorization header by api interceptor.
   *
   * @param data User data from email/password registration
   * @returns User data from backend
   */
  async registerWithEmail(
    data: RegisterEmailRequest
  ): Promise<UserResponse> {
    const response = await api.post<UserResponse>(
      '/api/v1/auth/register/email',
      data
    )
    return response.data
  },
}
