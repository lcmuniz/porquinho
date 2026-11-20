<script setup lang="ts">
import BrandMark from '@/components/common/BrandMark.vue'
import { ref } from 'vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { useAuth } from '@/composables/useAuth'
import { authService } from '@/services/authService'

const { requestPasswordReset } = useAuth()

const email = ref('')
const isLoading = ref(false)
const isSuccess = ref(false)
const errorMessage = ref('')

async function handleSubmit() {
  if (!email.value) return

  isLoading.value = true
  errorMessage.value = ''

  try {
    // Request password reset via Supabase
    await requestPasswordReset(email.value)

    // Log audit event (no auth required)
    try {
      await authService.logPasswordResetRequested(email.value)
    } catch (auditError) {
      // Non-critical error - continue with success flow
      console.warn('Failed to log password reset request:', auditError)
    }

    // Show generic success message (don't reveal if email exists)
    isSuccess.value = true
  } catch (error) {
    // Limite de envio do Supabase (AuthError traz o status em error.status, não em response.status).
    // Vale para qualquer e-mail e o Supabase já responde assim a quem chama a API direto: não revela cadastro.
    if ((error as { status?: number } | null)?.status === 429) {
      errorMessage.value = 'Muitas solicitações. Aguarde alguns minutos e tente de novo.'
    } else {
      // Still show success message even on error (security: don't reveal if email exists)
      isSuccess.value = true
    }
  } finally {
    isLoading.value = false
  }
}
</script>

<template>
  <main class="flex min-h-screen flex-col items-center justify-center bg-gray-50 px-4 py-10">
    <BrandMark />
    <section class="w-full max-w-md">
      <Card>
        <CardHeader>
          <CardTitle class="text-center text-2xl font-bold text-gray-900">
            Esqueceu sua senha?
          </CardTitle>
          <CardDescription class="text-center text-gray-600">
            Digite seu e-mail e enviaremos um link para redefinir sua senha
          </CardDescription>
        </CardHeader>
        <CardContent class="space-y-4">
          <!-- Success message -->
          <div
            v-if="isSuccess"
            role="alert"
            class="rounded-md border border-green-200 bg-green-50 p-4 text-sm text-green-700"
          >
            <p class="font-medium">Verifique seu email</p>
            <p class="mt-1">
              Se esse email existir, você receberá instruções para redefinir sua senha em até 5
              minutos.
            </p>
          </div>

          <!-- Error message -->
          <div
            v-if="errorMessage"
            role="alert"
            class="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700"
          >
            {{ errorMessage }}
          </div>

          <!-- Form -->
          <form @submit.prevent="handleSubmit" class="space-y-4">
            <div class="space-y-2">
              <label for="email" class="text-sm font-medium text-gray-900"> Email </label>
              <Input
                id="email"
                name="email"
                autocomplete="email"
                spellcheck="false"
                autocapitalize="off"
                v-model="email"
                type="email"
                required
                aria-label="Endereço de email"
                placeholder="seu@email.com"
                class="min-h-[44px]"
                :disabled="isSuccess"
              />
            </div>

            <Button
              type="submit"
              :disabled="isLoading || !email || isSuccess"
              class="w-full min-h-[44px] bg-purple-600 hover:bg-purple-700 text-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-purple-600 disabled:opacity-50 disabled:cursor-not-allowed"
              aria-label="Enviar link de redefinição"
            >
              <span v-if="isLoading">Enviando…</span>
              <span v-else>Enviar link de redefinição</span>
            </Button>
          </form>

          <!-- Back to login -->
          <div class="mt-6 text-center">
            <router-link
              to="/login"
              class="inline-flex min-h-[44px] items-center gap-1 text-sm text-purple-600 hover:text-purple-700 rounded-sm focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-purple-600"
            >
              <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                <path
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  stroke-width="2"
                  d="M15 19l-7-7 7-7"
                />
              </svg>
              Voltar para login
            </router-link>
          </div>
        </CardContent>
      </Card>
    </section>
  </main>
</template>
