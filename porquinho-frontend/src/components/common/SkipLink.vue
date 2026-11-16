<script setup lang="ts">
defineProps<{
  target: string
}>()
</script>

<template>
  <a :href="target" class="skip-link">
    <slot>Pular para o conteúdo principal</slot>
  </a>
</template>
