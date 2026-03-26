<template>
  <main class="min-h-screen bg-gray-50 px-4 py-8">
    <div class="mx-auto max-w-3xl space-y-6">
      <h1 class="text-2xl font-semibold text-gray-900">Dashboard</h1>
      <p class="text-gray-600">Em desenvolvimento — Epic 7</p>
      <nav aria-label="Configuracoes da conta">
        <RouterLink
          to="/auth/sessions"
          class="inline-flex min-h-[44px] items-center rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100"
        >
          Sessoes ativas
        </RouterLink>
      </nav>
    </div>
  </main>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router'
</script>
