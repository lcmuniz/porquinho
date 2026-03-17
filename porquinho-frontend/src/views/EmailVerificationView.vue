<template>
  <main class="flex min-h-screen items-center justify-center bg-white px-4">
    <section class="w-full max-w-md">
      <Card>
        <CardHeader>
          <CardTitle class="text-center text-2xl font-bold text-gray-900">
            {{ pageTitle }}
          </CardTitle>
          <CardDescription class="text-center text-gray-600">
            {{ pageDescription }}
          </CardDescription>
        </CardHeader>
        <CardContent class="space-y-4">
          <!-- Verificando -->
          <div v-if="verificationStatus === 'verifying'" class="text-center py-8">
            <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-purple-600 mx-auto"></div>
            <p class="mt-4 text-gray-600">Verificando seu email...</p>
          </div>

          <!-- Sucesso -->
          <div v-else-if="verificationStatus === 'success'" class="text-center py-4">
            <div class="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-green-100">
              <svg
                class="h-6 w-6 text-green-600"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  stroke-width="2"
                  d="M5 13l4 4L19 7"
                />
              </svg>
            </div>
            <p class="mt-4 text-gray-900 font-medium text-lg">Email verificado com sucesso!</p>
            <p class="mt-2 text-sm text-gray-600">
              Sua conta <strong>{{ userEmail }}</strong> foi ativada.
            </p>
            <Button
              @click="router.push('/login')"
              class="mt-6 w-full min-h-[44px] bg-purple-600 hover:bg-purple-700 text-white"
            >
              Ir para Login
            </Button>
          </div>

          <!-- Erro -->
          <div v-else-if="verificationStatus === 'error'" class="text-center py-4">
            <div class="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-red-100">
              <svg
                class="h-6 w-6 text-red-600"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  stroke-width="2"
                  d="M6 18L18 6M6 6l12 12"
                />
              </svg>
            </div>
            <p class="mt-4 text-red-600 font-medium">Erro na verificação</p>
            <p class="mt-2 text-sm text-gray-600">{{ errorMessage }}</p>
            <Button
              @click="router.push('/register')"
              class="mt-4 w-full bg-purple-600 hover:bg-purple-700 text-white"
            >
              Voltar para Registro
            </Button>
          </div>

          <!-- Aguardando verificação (default) -->
          <div v-else class="text-center py-4">
            <div class="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-blue-100">
              <svg
                class="h-6 w-6 text-blue-600"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  stroke-width="2"
                  d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z"
                />
              </svg>
            </div>
            <p class="mt-4 text-gray-900 font-medium">Verifique seu email</p>
            <p class="mt-2 text-sm text-gray-600">
              Enviamos um link de verificação para <strong>{{ userEmail || 'seu email' }}</strong>
            </p>
            <p class="mt-4 text-sm text-gray-600">
              Por favor, verifique sua caixa de entrada e clique no link para ativar sua conta.
            </p>
            <div class="mt-6 space-y-2">
              <Button
                @click="router.push('/login')"
                class="w-full min-h-[44px] bg-purple-600 hover:bg-purple-700 text-white"
              >
                Já verifiquei - Ir para Login
              </Button>
              <Button
                @click="router.push('/register')"
                variant="outline"
                class="w-full min-h-[44px]"
              >
                Voltar para Registro
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>
    </section>
  </main>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { supabase } from '@/services/supabase'

const router = useRouter()
const route = useRoute()

const verificationStatus = ref<'pending' | 'verifying' | 'success' | 'error'>('pending')
const errorMessage = ref('')
const userEmail = ref('')

const pageTitle = computed(() => {
  switch (verificationStatus.value) {
    case 'verifying':
      return 'Verificando Email'
    case 'success':
      return 'Email Verificado!'
    case 'error':
      return 'Erro na Verificação'
    default:
      return 'Verifique seu Email'
  }
})

const pageDescription = computed(() => {
  switch (verificationStatus.value) {
    case 'verifying':
      return 'Aguarde enquanto verificamos seu email...'
    case 'success':
      return 'Sua conta foi ativada com sucesso'
    case 'error':
      return 'Não foi possível verificar seu email'
    default:
      return 'Confirme seu endereço de email'
  }
})

onMounted(async () => {
  // Tentar obter email do usuário atual
  const { data: sessionData } = await supabase.auth.getSession()
  if (sessionData.session?.user?.email) {
    userEmail.value = sessionData.session.user.email
  }

  // Verificar se temos tokens de verificação na URL
  // Supabase envia tokens no hash (#) quando usuário clica no link de verificação
  const hashParams = new URLSearchParams(window.location.hash.substring(1))
  const accessToken = hashParams.get('access_token')
  const refreshToken = hashParams.get('refresh_token')
  const type = hashParams.get('type')

  console.log('Hash params:', { accessToken: !!accessToken, refreshToken: !!refreshToken, type })

  // Se encontrou tokens na URL, processar verificação
  if (accessToken || refreshToken) {
    verificationStatus.value = 'verifying'

    try {
      // Buscar sessão atualizada (Supabase já processou os tokens da URL)
      const { data, error } = await supabase.auth.getSession()

      console.log('Session after verification:', {
        hasSession: !!data.session,
        email: data.session?.user?.email,
        emailConfirmed: data.session?.user?.email_confirmed_at
      })

      if (error) {
        throw error
      }

      // Verificar se temos sessão válida
      if (data.session?.user) {
        userEmail.value = data.session.user.email || ''

        // Verificar se email foi confirmado
        if (data.session.user.email_confirmed_at) {
          verificationStatus.value = 'success'
        } else {
          // Retry com backoff exponencial (máximo 3 tentativas)
          let attempts = 0
          const maxAttempts = 3
          let delay = 300

          while (attempts < maxAttempts) {
            await new Promise(resolve => setTimeout(resolve, delay))

            const { data: retryData } = await supabase.auth.getSession()
            if (retryData.session?.user?.email_confirmed_at) {
              verificationStatus.value = 'success'
              break
            }

            attempts++
            delay *= 2 // Exponential backoff: 300ms, 600ms, 1200ms
          }

          if (verificationStatus.value !== 'success') {
            throw new Error('Email ainda não foi confirmado após múltiplas tentativas')
          }
        }
      } else {
        throw new Error('Sessão não encontrada após verificação')
      }
    } catch (error: any) {
      console.error('Email verification error:', error)
      verificationStatus.value = 'error'
      errorMessage.value = error.message ||
        'Link de verificação inválido ou expirado. Por favor, registre-se novamente.'
    }
  } else {
    // Sem tokens na URL - usuário veio do redirect após registro
    console.log('No tokens in URL - showing pending state')
    verificationStatus.value = 'pending'
  }
})
</script>
