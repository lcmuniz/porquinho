import { supabase } from '@/services/supabase'

/**
 * Composable for authentication operations using Supabase Auth.
 * Provides methods for Google OAuth, email/password sign-up/sign-in and session management.
 * All user data is stored in Supabase auth.users and user_metadata (no backend sync).
 */
export function useAuth() {
  /**
   * Sign up with Google OAuth.
   * Redirects to Google OAuth consent screen and returns to callback URL after authentication.
   */
  const signUpWithGoogle = async () => {
    const { data, error } = await supabase.auth.signInWithOAuth({
      provider: 'google',
      options: {
        redirectTo: `${window.location.origin}/auth/callback`,
      },
    })

    if (error) {
      console.error('Error signing up with Google:', error)
      throw error
    }

    return data
  }

  /**
   * Sign up with email and password.
   * Creates user in Supabase Auth (with automatic bcrypt hashing).
   * Supabase automatically sends email verification.
   * User data stored in user_metadata with LGPD consent timestamp.
   *
   * @param email User's email address
   * @param password User's password (min 8 chars, 1 uppercase, 1 lowercase, 1 number)
   * @param name User's display name (optional)
   */
  const signUpWithEmail = async (email: string, password: string, name?: string) => {
    // Create user in Supabase Auth (password is automatically hashed with bcrypt)
    const { data, error } = await supabase.auth.signUp({
      email,
      password,
      options: {
        emailRedirectTo: `${window.location.origin}/auth/verify-email`,
        data: {
          name: name || '',
          lgpd_consent_at: new Date().toISOString(), // NFR25: LGPD consent timestamp
        },
      },
    })

    if (error) {
      console.error('Error signing up with email:', error)
      throw error
    }

    return data
  }

  /**
   * Get current session from Supabase.
   */
  const getSession = async () => {
    const { data, error } = await supabase.auth.getSession()

    if (error) {
      console.error('Error getting session:', error)
      throw error
    }

    return data.session
  }

  /**
   * Sign in with email and password.
   * Authenticates user with Supabase Auth.
   *
   * @param email User's email address
   * @param password User's password
   */
  const signInWithEmail = async (email: string, password: string) => {
    const { data, error } = await supabase.auth.signInWithPassword({
      email,
      password,
    })

    if (error) {
      console.error('Error signing in with email:', error)
      throw error
    }

    return data
  }

  /**
   * Sign in with Google OAuth.
   * Redirects to Google OAuth consent screen and returns to callback URL after authentication.
   */
  const signInWithGoogle = async () => {
    const { data, error } = await supabase.auth.signInWithOAuth({
      provider: 'google',
      options: {
        redirectTo: `${window.location.origin}/auth/callback`,
      },
    })

    if (error) {
      console.error('Error signing in with Google:', error)
      throw error
    }

    return data
  }

  /**
   * Sign out current user.
   */
  const signOut = async () => {
    const { error } = await supabase.auth.signOut()

    if (error) {
      console.error('Error signing out:', error)
      throw error
    }
  }

  /**
   * Request password reset via email.
   * Supabase will send an email with a reset link.
   *
   * @param email User's email address
   */
  const requestPasswordReset = async (email: string) => {
    const { data, error } = await supabase.auth.resetPasswordForEmail(email, {
      redirectTo: `${window.location.origin}/auth/reset-password`,
    })

    if (error) {
      console.error('Error requesting password reset:', error)
      throw error
    }

    return data
  }

  /**
   * Update user password.
   * Called after user clicks reset link in email.
   * Supabase validates the token automatically.
   *
   * @param newPassword New password for the user
   */
  const updatePassword = async (newPassword: string) => {
    const { data, error } = await supabase.auth.updateUser({
      password: newPassword,
    })

    if (error) {
      console.error('Error updating password:', error)
      throw error
    }

    return data
  }

  return {
    signUpWithGoogle,
    signUpWithEmail,
    signInWithEmail,
    signInWithGoogle,
    getSession,
    signOut,
    requestPasswordReset,
    updatePassword,
  }
}
