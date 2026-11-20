<script setup lang="ts">
import { computed } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import SkipLink from '@/components/common/SkipLink.vue'
import AppShell from '@/components/common/AppShell.vue'

const route = useRoute()
// Telas logadas ganham a navegação; onboarding é um fluxo guiado e fica sem ela
const withShell = computed(() => route.meta.requiresAuth === true && route.name !== 'onboarding')
</script>

<template>
  <SkipLink target="#main-content" />
  <AppShell v-if="withShell">
    <!-- Cada tela tem o seu <main> (um por página); este wrapper só é o alvo do link "Pular para o conteúdo" -->
    <div id="main-content" tabindex="-1" class="outline-none">
      <RouterView />
    </div>
  </AppShell>
  <div v-else id="main-content" tabindex="-1" class="outline-none">
    <RouterView />
  </div>
</template>
