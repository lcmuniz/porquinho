<template>
  <div class="min-h-screen bg-background">
    <ColorPalette />
    <hr class="border-border" />
    <TypographyShowcase />
    <hr class="border-border" />
    <SpacingShowcase />
    <hr class="border-border" />
    <AccessibilityShowcase />
  </div>
</template>

<script setup lang="ts">
import ColorPalette from '@/components/ColorPalette.vue'
import TypographyShowcase from '@/components/TypographyShowcase.vue'
import SpacingShowcase from '@/components/SpacingShowcase.vue'
import AccessibilityShowcase from '@/components/AccessibilityShowcase.vue'
</script>
