<template>
  <main class="px-4 py-6 md:px-8 md:py-8">
    <div class="mx-auto max-w-3xl space-y-6">
      <div>
        <div>
          <h1 class="text-2xl font-bold text-gray-900 md:text-[28px]">Importar extrato</h1>
          <p class="mt-1 text-sm text-gray-600">
            Envie o extrato OFX ou CSV do seu banco para uma conta ou um cartão.
          </p>
        </div>
      </div>

      <ImportUploader />
    </div>
  </main>
</template>

<script setup lang="ts">
import ImportUploader from '@/components/import/ImportUploader.vue'
</script>
