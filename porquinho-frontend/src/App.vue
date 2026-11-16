<script setup lang="ts">
import { RouterView } from 'vue-router'
import SkipLink from '@/components/common/SkipLink.vue'
</script>

<template>
  <SkipLink target="#main-content" />
  <!-- Cada tela tem o seu <main> (um por página); este wrapper só é o alvo do link "Pular para o conteúdo" -->
  <div id="main-content">
    <RouterView />
  </div>
</template>
