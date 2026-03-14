<script setup lang="ts">
defineProps<{
  target: string
}>()
</script>

<template>
  <a :href="target" class="skip-link">
    <slot>Pular para o conteudo principal</slot>
  </a>
</template>
