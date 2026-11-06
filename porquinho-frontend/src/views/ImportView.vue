<template>
  <main class="min-h-screen bg-gray-50 px-4 py-8">
    <div class="mx-auto max-w-3xl space-y-6">
      <div class="flex items-center justify-between gap-4">
        <div>
          <h1 class="text-2xl font-semibold text-gray-900">Importar extrato</h1>
          <p class="mt-1 text-sm text-gray-600">
            Envie o extrato OFX ou CSV do seu banco para uma conta ou um cartão.
          </p>
        </div>
        <RouterLink
          to="/dashboard"
          class="inline-flex min-h-[44px] items-center rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100"
        >
          Voltar
        </RouterLink>
      </div>

      <ImportUploader />
    </div>
  </main>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router'
import ImportUploader from '@/components/import/ImportUploader.vue'
</script>
