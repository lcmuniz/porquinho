<script setup lang="ts">
import { RouterView } from 'vue-router'
import SkipLink from '@/components/common/SkipLink.vue'
</script>

<template>
  <SkipLink target="#main-content" />
  <header>
    <nav aria-label="Navegacao principal">
      <!-- RouterLink navigation will be added in auth epic -->
    </nav>
  </header>
  <main id="main-content">
    <RouterView />
  </main>
  <footer aria-label="Rodape">
    <!-- Footer content will be added in future epics -->
  </footer>
</template>
