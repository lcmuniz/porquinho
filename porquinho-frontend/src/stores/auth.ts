import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserResponse } from '@/services/authService'

/**
 * Pinia store for authentication state management.
 * Stores user data and authentication status.
 */
export const useAuthStore = defineStore('auth', () => {
  // State
  const user = ref<UserResponse | null>(null)
  const loading = ref(false)

  // Getters
  const isAuthenticated = computed(() => user.value !== null)

  // Actions
  const setUser = (userData: UserResponse) => {
    user.value = userData
    // Persist to localStorage for session persistence
    localStorage.setItem('user', JSON.stringify(userData))
  }

  const clearUser = () => {
    user.value = null
    localStorage.removeItem('user')
    localStorage.removeItem('auth_token')
  }

  const checkSession = () => {
    // Load user from localStorage if exists
    const storedUser = localStorage.getItem('user')
    if (storedUser) {
      try {
        user.value = JSON.parse(storedUser)
      } catch (error) {
        console.error('Error parsing stored user:', error)
        clearUser()
      }
    }
  }

  return {
    user,
    loading,
    isAuthenticated,
    setUser,
    clearUser,
    checkSession,
  }
})
