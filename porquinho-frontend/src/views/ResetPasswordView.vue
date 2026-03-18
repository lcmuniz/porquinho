<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { useAuth } from '@/composables/useAuth'
import { authService } from '@/services/authService'

const router = useRouter()
const route = useRoute()
const { updatePassword } = useAuth()

const newPassword = ref('')
const confirmPassword = ref('')
const isLoading = ref(false)
const errorMessage = ref('')
const isSuccess = ref(false)
const showNewPassword = ref(false)
const showConfirmPassword = ref(false)

// Password validation requirements
const passwordRequirements = [
  { label: 'Pelo menos 8 caracteres', validator: (pwd: string) => pwd.length >= 8 },
  { label: 'Uma letra maiúscula', validator: (pwd: string) => /[A-Z]/.test(pwd) },
  { label: 'Uma letra minúscula', validator: (pwd: string) => /[a-z]/.test(pwd) },
  { label: 'Um número', validator: (pwd: string) => /\d/.test(pwd) },
]

const passwordsMatch = computed(() => {
  if (!confirmPassword.value) return true
  return newPassword.value === confirmPassword.value
})

const isPasswordValid = computed(() => {
  return passwordRequirements.every(req => req.validator(newPassword.value))
})

const isFormValid = computed(() => {
  return (
    newPassword.value.length > 0 &&
    confirmPassword.value.length > 0 &&
    passwordsMatch.value &&
    isPasswordValid.value
  )
})

async function handleSubmit() {
  if (!isFormValid.value) return

  isLoading.value = true
  errorMessage.value = ''

  try {
    // Update password via Supabase (validates token automatically)
    await updatePassword(newPassword.value)

    // Log audit event (authenticated via JWT from reset token)
    try {
      await authService.logPasswordResetCompleted()
    } catch (auditError: any) {
      // Non-critical error - continue with success flow
      console.warn('Failed to log password reset completion:', auditError)
    }

    // Show success message
    isSuccess.value = true

    // Redirect to login after 3 seconds
    setTimeout(() => {
      router.push('/login')
    }, 3000)
  } catch (error: any) {
    if (error.message?.includes('expired') || error.message?.includes('invalid')) {
      errorMessage.value = 'Link de redefinição expirado ou inválido. Solicite um novo.'
    } else if (error.message?.includes('same as the old password')) {
      errorMessage.value = 'A nova senha deve ser diferente da senha anterior.'
    } else {
      errorMessage.value = 'Erro ao redefinir senha. Tente novamente.'
    }
  } finally {
    isLoading.value = false
  }
}

function toggleNewPasswordVisibility() {
  showNewPassword.value = !showNewPassword.value
}

function toggleConfirmPasswordVisibility() {
  showConfirmPassword.value = !showConfirmPassword.value
}

onMounted(() => {
  // Supabase handles the token validation automatically
  // The token is in the URL fragment and Supabase session will be set
})
</script>

<template>
  <main class="flex min-h-screen items-center justify-center bg-white px-4">
    <section class="w-full max-w-md">
      <Card>
        <CardHeader>
          <CardTitle class="text-center text-2xl font-bold text-gray-900">
            Redefinir senha
          </CardTitle>
          <CardDescription class="text-center text-gray-600">
            Digite uma nova senha para sua conta
          </CardDescription>
        </CardHeader>
        <CardContent class="space-y-4">
          <!-- Success message -->
          <div
            v-if="isSuccess"
            role="alert"
            class="rounded-md border border-green-200 bg-green-50 p-4 text-sm text-green-700"
          >
            <p class="font-medium">Senha redefinida com sucesso!</p>
            <p class="mt-1">Redirecionando para login...</p>
          </div>

          <!-- Error message -->
          <div
            v-if="errorMessage"
            role="alert"
            class="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700"
          >
            {{ errorMessage }}
            <router-link
              v-if="errorMessage.includes('expirado')"
              to="/auth/forgot-password"
              class="mt-2 block text-sm underline"
            >
              Solicitar novo link
            </router-link>
          </div>

          <!-- Form -->
          <form v-if="!isSuccess" @submit.prevent="handleSubmit" class="space-y-4">
            <div class="space-y-2">
              <label for="new-password" class="text-sm font-medium text-gray-900">
                Nova senha
              </label>
              <div class="relative">
                <Input
                  id="new-password"
                  v-model="newPassword"
                  :type="showNewPassword ? 'text' : 'password'"
                  required
                  aria-label="Nova senha"
                  placeholder="Digite sua nova senha"
                  class="min-h-[44px] pr-10"
                />
                <button
                  type="button"
                  @click="toggleNewPasswordVisibility"
                  class="absolute right-2 top-1/2 -translate-y-1/2 rounded text-gray-500 hover:text-gray-700 focus:outline-none focus:ring-2 focus:ring-purple-600 focus:ring-offset-2"
                  :aria-label="showNewPassword ? 'Ocultar senha' : 'Mostrar senha'"
                >
                  <svg
                    v-if="!showNewPassword"
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
                  <svg v-else class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
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

            <div class="space-y-2">
              <label for="confirm-password" class="text-sm font-medium text-gray-900">
                Confirmar senha
              </label>
              <div class="relative">
                <Input
                  id="confirm-password"
                  v-model="confirmPassword"
                  :type="showConfirmPassword ? 'text' : 'password'"
                  required
                  aria-label="Confirmar senha"
                  placeholder="Confirme sua nova senha"
                  class="min-h-[44px] pr-10"
                  :class="{ 'border-red-500': !passwordsMatch && confirmPassword }"
                />
                <button
                  type="button"
                  @click="toggleConfirmPasswordVisibility"
                  class="absolute right-2 top-1/2 -translate-y-1/2 rounded text-gray-500 hover:text-gray-700 focus:outline-none focus:ring-2 focus:ring-purple-600 focus:ring-offset-2"
                  :aria-label="showConfirmPassword ? 'Ocultar senha' : 'Mostrar senha'"
                >
                  <svg
                    v-if="!showConfirmPassword"
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
                  <svg v-else class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path
                      stroke-linecap="round"
                      stroke-linejoin="round"
                      stroke-width="2"
                      d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858.908a3 3 0 114.243 4.243M9.878 9.878l4.242 4.242M9.88 9.88l-3.29-3.29m7.532 7.532l3.29 3.29M3 3l3.59 3.59m0 0A9.953 9.953 0 0112 5c4.478 0 8.268 2.943 9.543 7a10.025 10.025 0 01-4.132 5.411m0 0L21 21"
                    />
                  </svg>
                </button>
              </div>
              <p v-if="!passwordsMatch && confirmPassword" class="text-sm text-red-600">
                As senhas não coincidem
              </p>
            </div>

            <!-- Password requirements -->
            <div class="rounded-md bg-gray-50 p-3">
              <p class="mb-2 text-sm font-medium text-gray-900">A senha deve conter:</p>
              <ul class="space-y-1">
                <li
                  v-for="req in passwordRequirements"
                  :key="req.label"
                  class="flex items-center gap-2 text-sm"
                  :class="req.validator(newPassword) ? 'text-green-600' : 'text-gray-600'"
                >
                  <span v-if="req.validator(newPassword)">✓</span>
                  <span v-else>○</span>
                  {{ req.label }}
                </li>
              </ul>
            </div>

            <Button
              type="submit"
              :disabled="isLoading || !isFormValid"
              class="w-full min-h-[44px] bg-purple-600 hover:bg-purple-700 text-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-purple-600 disabled:opacity-50 disabled:cursor-not-allowed"
              aria-label="Redefinir senha"
            >
              <span v-if="isLoading">Redefinindo...</span>
              <span v-else>Redefinir senha</span>
            </Button>
          </form>
        </CardContent>
      </Card>
    </section>
  </main>
</template>
