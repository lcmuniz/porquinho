import { supabase } from '@/services/supabase'

/**
 * Composable for authentication operations using Supabase Auth.
 * Provides methods for Google OAuth sign-up and session management.
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
   * Sign out current user.
   */
  const signOut = async () => {
    const { error } = await supabase.auth.signOut()

    if (error) {
      console.error('Error signing out:', error)
      throw error
    }
  }

  return {
    signUpWithGoogle,
    getSession,
    signOut,
  }
}
