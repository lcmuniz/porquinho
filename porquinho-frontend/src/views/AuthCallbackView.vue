<template>
  <main class="flex min-h-screen items-center justify-center bg-white px-4">
    <div class="text-center">
      <div v-if="isLoading" class="space-y-4">
        <div
          class="inline-block h-12 w-12 animate-spin rounded-full border-4 border-solid border-purple-600 border-r-transparent"
          role="status"
          aria-label="Carregando"
        ></div>
        <p class="text-gray-600">Finalizando seu cadastro...</p>
      </div>

      <div v-if="errorMessage" class="space-y-4">
        <p class="text-red-600" role="alert">{{ errorMessage }}</p>
        <Button
          @click="goToRegister"
          class="bg-purple-600 hover:bg-purple-700"
        >
          Voltar para registro
        </Button>
      </div>
    </div>
  </main>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/composables/useAuth'
import { authService } from '@/services/authService'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const { getSession } = useAuth()
const authStore = useAuthStore()

const isLoading = ref(true)
const errorMessage = ref('')

onMounted(async () => {
  try {
    // Get session from Supabase (contains JWT after OAuth)
    const session = await getSession()

    if (!session) {
      throw new Error('Nenhuma sessão encontrada. Por favor, tente novamente.')
    }

    // Extract user info from session
    const { user } = session
    if (!user || !user.email) {
      throw new Error('Informações do usuário incompletas.')
    }

    // Get Google ID from user metadata
    const googleId =
      user.user_metadata?.sub || user.user_metadata?.provider_id || user.id

    // Call backend to register/verify user
    // Pass access token directly to avoid waiting for localStorage sync
    const userData = await authService.registerWithGoogle(
      {
        email: user.email,
        googleId,
        name: user.user_metadata?.full_name || user.user_metadata?.name || '',
      },
      session.access_token
    )

    // Update auth store with user data
    authStore.setUser(userData)

    // Redirect to onboarding
    router.push('/onboarding')
  } catch (error) {
    console.error('Error processing OAuth callback:', error)
    errorMessage.value =
      error instanceof Error
        ? error.message
        : 'Erro ao processar autenticação. Por favor, tente novamente.'
  } finally {
    isLoading.value = false
  }
})

const goToRegister = () => {
  router.push('/register')
}
</script>
