<template>
  <div class="space-y-2" data-testid="onboarding-step-indicator">
    <p class="text-sm font-medium text-purple-700">Passo {{ step }} de {{ total }}</p>
    <div
      class="h-2 w-full overflow-hidden rounded-full bg-purple-100"
      role="progressbar"
      :aria-valuenow="step"
      aria-valuemin="1"
      :aria-valuemax="total"
      :aria-label="`Passo ${step} de ${total}`"
    >
      <div
        class="h-full rounded-full bg-purple-600 transition-all"
        :style="{ width: `${(step / total) * 100}%` }"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
/** Indicador "Passo X de 8" do onboarding guiado (Epic 3; reutilizado pelos passos 2 a 8). */
withDefaults(defineProps<{ step: number; total?: number }>(), { total: 8 })
</script>
