<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { useAuth } from '@/composables/useAuth'
import { useAuthStore } from '@/stores/auth'
import { authService } from '@/services/authService'

const router = useRouter()
const _route = useRoute() // Reserved for future use (e.g., redirectTo query param)
const { signInWithEmail, signInWithGoogle } = useAuth()
const authStore = useAuthStore()

const email = ref('')
const password = ref('')
const isLoading = ref(false)
const errorMessage = ref('')
const showPassword = ref(false)

const isFormValid = computed(() => {
  return email.value.length > 0 && password.value.length > 0
})

async function handleEmailLogin() {
  if (!isFormValid.value) return

  isLoading.value = true
  errorMessage.value = ''

  try {
    // Step 1: Check if login attempt is allowed (rate limiting and account lock)
    try {
      await authService.checkLoginAllowed(email.value)
    } catch (checkError: any) {
      // Handle rate limiting and account locking BEFORE trying Supabase
      if (checkError.response?.status === 429) {
        errorMessage.value = 'Muitas tentativas de login. Aguarde 1 minuto.'
        return
      } else if (checkError.response?.status === 423) {
        errorMessage.value =
          'Conta temporariamente bloqueada devido a múltiplas tentativas falhas. Tente novamente em 15 minutos.'
        return
      }
      throw checkError // Re-throw unexpected errors
    }

    // Step 2: Authenticate with Supabase
    const { session, user } = await signInWithEmail(email.value, password.value)

    if (!user || !session) {
      throw new Error('Authentication failed')
    }

    // Wait a moment to ensure session is fully saved
    await new Promise(resolve => setTimeout(resolve, 100))

    // Step 3: Log successful login to backend (audit + reset failed attempts counter)
    try {
      await authService.logLogin(email.value)
    } catch (backendError: any) {
      // If user doesn't exist in backend (404), create them now (lazy sync)
      // JWT token is automatically included by api interceptor
      if (backendError.response?.status === 404) {
        try {
          console.log('User not found in backend, creating record (lazy sync)...')
          await authService.registerWithEmail({
            email: email.value,
            name: user.user_metadata?.name || email.value,
          })
          // Retry logging the login
          await authService.logLogin(email.value)
        } catch (syncError: any) {
          console.warn('Failed to sync user to backend:', syncError)
        }
      } else {
        // Other backend errors are non-critical
        console.warn('Backend audit log failed:', backendError)
      }
    }

    // Step 4: Update auth store
    authStore.setUser({
      id: user.id,
      email: user.email!,
      authProvider: 'EMAIL', // TODO: Get from backend or Supabase metadata
      createdAt: new Date().toISOString(),
    })

    // Step 5: Redirect to dashboard or original page
    const redirectTo = _route.query.redirectTo as string
    router.push(redirectTo || '/dashboard')
  } catch (error: any) {
    // Step 6: Record failed login attempt (increment counter)
    try {
      await authService.recordFailedLogin(email.value)
    } catch (recordError: any) {
      console.warn('Failed to record failed login attempt:', recordError)
    }

    // Step 7: Show error message after 1 second delay (security best practice)
    await new Promise((resolve) => setTimeout(resolve, 1000))

    if (error.message?.includes('Invalid login credentials')) {
      errorMessage.value = 'Email ou senha inválidos.'
    } else if (error.message?.includes('Email not confirmed')) {
      errorMessage.value =
        'Email não verificado. Verifique sua caixa de entrada e clique no link de confirmação.'
    } else {
      errorMessage.value = 'Erro ao fazer login. Tente novamente.'
    }
  } finally {
    isLoading.value = false
  }
}

async function handleGoogleLogin() {
  isLoading.value = true
  errorMessage.value = ''

  try {
    await signInWithGoogle()
    // Redirect handled by Supabase (to /auth/callback)
  } catch (error: any) {
    errorMessage.value = 'Erro ao fazer login com Google. Tente novamente.'
    isLoading.value = false
  }
}

function togglePasswordVisibility() {
  showPassword.value = !showPassword.value
}
</script>

<template>
  <main class="flex min-h-screen items-center justify-center bg-white px-4">
    <section class="w-full max-w-md">
      <Card>
        <CardHeader>
          <CardTitle class="text-center text-2xl font-bold text-gray-900">
            Bem-vindo de volta
          </CardTitle>
          <CardDescription class="text-center text-gray-600">
            Entre na sua conta do Porquinho
          </CardDescription>
        </CardHeader>
        <CardContent class="space-y-4">
          <!-- Error message -->
          <div
            v-if="errorMessage"
            role="alert"
            class="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700"
          >
            {{ errorMessage }}
          </div>

          <!-- Email/Password Form -->
          <form @submit.prevent="handleEmailLogin" class="space-y-4">
            <div class="space-y-2">
              <label for="email" class="text-sm font-medium text-gray-900">
                Email
              </label>
              <Input
                id="email"
                v-model="email"
                type="email"
                required
                placeholder="seu@email.com"
                aria-label="Endereço de email"
                :disabled="isLoading"
                class="min-h-[44px]"
              />
            </div>

            <div class="space-y-2">
              <label for="password" class="text-sm font-medium text-gray-900">
                Senha
              </label>
              <div class="relative">
                <Input
                  id="password"
                  v-model="password"
                  :type="showPassword ? 'text' : 'password'"
                  required
                  placeholder="Digite sua senha"
                  aria-label="Senha"
                  :disabled="isLoading"
                  class="min-h-[44px] pr-10"
                />
                <button
                  type="button"
                  @click="togglePasswordVisibility"
                  class="absolute right-2 top-1/2 -translate-y-1/2 text-gray-500 hover:text-gray-700 focus:outline-none focus:ring-2 focus:ring-purple-600 focus:ring-offset-2 rounded"
                  :aria-label="showPassword ? 'Ocultar senha' : 'Mostrar senha'"
                >
                  <svg
                    v-if="!showPassword"
                    class="h-5 w-5"
                    fill="none"
                    stroke="currentColor"
                    viewBox="0 0 24 24"
                  >
                    <path
                      stroke-linecap="round"
                      stroke-linejoin="round"
                      stroke-width="2"
                      d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"
                    />
                    <path
                      stroke-linecap="round"
                      stroke-linejoin="round"
                      stroke-width="2"
                      d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"
                    />
                  </svg>
                  <svg
                    v-else
                    class="h-5 w-5"
                    fill="none"
                    stroke="currentColor"
                    viewBox="0 0 24 24"
                  >
                    <path
                      stroke-linecap="round"
                      stroke-linejoin="round"
                      stroke-width="2"
                      d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858.908a3 3 0 114.243 4.243M9.878 9.878l4.242 4.242M9.88 9.88l-3.29-3.29m7.532 7.532l3.29 3.29M3 3l3.59 3.59m0 0A9.953 9.953 0 0112 5c4.478 0 8.268 2.943 9.543 7a10.025 10.025 0 01-4.132 5.411m0 0L21 21"
                    />
                  </svg>
                </button>
              </div>
            </div>

            <!-- Forgot password link (Story 1.4 - not yet implemented)
            <div class="flex justify-end">
              <router-link
                to="/auth/forgot-password"
                class="text-sm text-purple-600 hover:text-purple-700 focus:outline-none focus:underline"
              >
                Esqueceu a senha?
              </router-link>
            </div>
            -->

            <Button
              type="submit"
              :disabled="isLoading || !isFormValid"
              class="w-full min-h-[44px] bg-purple-600 hover:bg-purple-700 text-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-purple-600 disabled:opacity-50 disabled:cursor-not-allowed"
              aria-label="Entrar com email"
            >
              <span v-if="isLoading">Entrando...</span>
              <span v-else>Entrar</span>
            </Button>
          </form>

          <!-- Divider -->
          <div class="relative my-6">
            <div class="absolute inset-0 flex items-center">
              <div class="w-full border-t border-gray-200"></div>
            </div>
            <div class="relative flex justify-center text-sm">
              <span class="bg-white px-2 text-gray-500">OU</span>
            </div>
          </div>

          <!-- Google OAuth button -->
          <Button
            @click="handleGoogleLogin"
            variant="outline"
            :disabled="isLoading"
            class="w-full min-h-[44px] border-gray-300 hover:bg-gray-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-purple-600"
            aria-label="Entrar com Google"
          >
            <svg class="mr-2 h-5 w-5" viewBox="0 0 24 24">
              <path
                fill="#4285F4"
                d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
              />
              <path
                fill="#34A853"
                d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
              />
              <path
                fill="#FBBC05"
                d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"
              />
              <path
                fill="#EA4335"
                d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"
              />
            </svg>
            Entrar com Google
          </Button>

          <!-- Sign up link -->
          <p class="text-center text-sm text-gray-600">
            Não tem uma conta?
            <router-link
              to="/register"
              class="font-medium text-purple-600 hover:text-purple-700 focus:outline-none focus:underline"
            >
              Cadastre-se
            </router-link>
          </p>
        </CardContent>
      </Card>
    </section>
  </main>
</template>
