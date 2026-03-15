<template>
  <main class="flex min-h-screen items-center justify-center bg-white px-4">
    <div class="text-center space-y-6 max-w-md">
      <div class="inline-flex h-16 w-16 items-center justify-center rounded-full bg-purple-100">
        <svg
          class="h-8 w-8 text-purple-600"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
          aria-hidden="true"
        >
          <path
            stroke-linecap="round"
            stroke-linejoin="round"
            stroke-width="2"
            d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"
          />
        </svg>
      </div>

      <div>
        <h1 class="text-3xl font-bold text-gray-900">Bem-vindo ao Porquinho!</h1>
        <p class="mt-2 text-gray-600">
          Sua conta foi criada com sucesso. Prepare-se para transformar sua relação com as
          finanças!
        </p>
      </div>

      <div class="rounded-lg bg-purple-50 p-4 text-sm text-gray-700">
        <p class="font-semibold text-purple-900">🎯 Próximos passos (Epic 3):</p>
        <ul class="mt-2 space-y-1 text-left">
          <li>• Configurar contas bancárias e cartões</li>
          <li>• Importar transações</li>
          <li>• Criar orçamento personalizado</li>
          <li>• Definir metas financeiras</li>
        </ul>
      </div>

      <div class="flex flex-col gap-3">
        <Button
          @click="goToDashboard"
          class="w-full bg-purple-600 hover:bg-purple-700 text-white min-h-[44px]"
        >
          Ir para o Dashboard
        </Button>

        <p class="text-xs text-gray-500">
          O processo de onboarding guiado será implementado no Epic 3
        </p>
      </div>
    </div>
  </main>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { Button } from '@/components/ui/button'

const router = useRouter()

const goToDashboard = () => {
  router.push('/dashboard')
}
</script>
