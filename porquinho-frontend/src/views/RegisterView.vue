<template>
  <main class="flex min-h-screen items-center justify-center bg-white px-4">
    <section class="w-full max-w-md">
      <Card>
        <CardHeader>
          <CardTitle class="text-center text-2xl font-bold text-gray-900">
            Criar conta
          </CardTitle>
          <CardDescription class="text-center text-gray-600">
            Comece a gerenciar suas finanças hoje
          </CardDescription>
        </CardHeader>
        <CardContent class="space-y-4">
          <Button
            @click="handleGoogleSignUp"
            :disabled="isLoading"
            class="w-full min-h-[44px] bg-purple-600 hover:bg-purple-700 text-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-purple-600"
            aria-label="Cadastrar com Google"
          >
            <svg
              v-if="!isLoading"
              class="mr-2 h-5 w-5"
              aria-hidden="true"
              focusable="false"
              xmlns="http://www.w3.org/2000/svg"
              viewBox="0 0 48 48"
            >
              <path
                fill="#FFC107"
                d="M43.611 20.083H42V20H24v8h11.303c-1.649 4.657-6.08 8-11.303 8-6.627 0-12-5.373-12-12s5.373-12 12-12c3.059 0 5.842 1.154 7.961 3.039l5.657-5.657C34.046 6.053 29.268 4 24 4 12.955 4 4 12.955 4 24s8.955 20 20 20 20-8.955 20-20c0-1.341-.138-2.65-.389-3.917z"
              />
              <path
                fill="#FF3D00"
                d="m6.306 14.691 6.571 4.819C14.655 15.108 18.961 12 24 12c3.059 0 5.842 1.154 7.961 3.039l5.657-5.657C34.046 6.053 29.268 4 24 4 16.318 4 9.656 8.337 6.306 14.691z"
              />
              <path
                fill="#4CAF50"
                d="M24 44c5.166 0 9.86-1.977 13.409-5.192l-6.19-5.238A11.91 11.91 0 0 1 24 36c-5.202 0-9.619-3.317-11.283-7.946l-6.522 5.025C9.505 39.556 16.227 44 24 44z"
              />
              <path
                fill="#1976D2"
                d="M43.611 20.083H42V20H24v8h11.303a12.04 12.04 0 0 1-4.087 5.571l.003-.002 6.19 5.238C36.971 39.205 44 34 44 24c0-1.341-.138-2.65-.389-3.917z"
              />
            </svg>
            <span v-if="isLoading">Carregando...</span>
            <span v-else>Cadastrar com Google</span>
          </Button>

          <p v-if="errorMessage" class="text-sm text-red-600" role="alert">
            {{ errorMessage }}
          </p>

          <p class="text-center text-sm text-gray-600">
            Ao cadastrar, você concorda com nossos
            <router-link
              to="/terms"
              class="text-purple-600 hover:text-purple-700 underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-purple-600"
            >
              Termos de Uso
            </router-link>
            e
            <router-link
              to="/privacy"
              class="text-purple-600 hover:text-purple-700 underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-purple-600"
            >
              Política de Privacidade (LGPD)
            </router-link>
          </p>
        </CardContent>
      </Card>
    </section>
  </main>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/composables/useAuth'

const { signUpWithGoogle } = useAuth()
const isLoading = ref(false)
const errorMessage = ref('')

const handleGoogleSignUp = async () => {
  isLoading.value = true
  errorMessage.value = ''

  try {
    await signUpWithGoogle()
  } catch (error) {
    console.error('Erro ao cadastrar com Google:', error)
    errorMessage.value =
      'Erro ao conectar com Google. Por favor, tente novamente.'
  } finally {
    isLoading.value = false
  }
}
</script>
