<template>
  <div :id="`goal-chart-${goal.id}`">
    <p v-if="state === 'loading'" class="py-6 text-center text-sm text-gray-600" aria-busy="true">
      Carregando evolução...
    </p>
    <p
      v-else-if="state === 'error'"
      role="alert"
      class="py-6 text-center text-sm text-gray-600"
      data-testid="goal-chart-error"
    >
      Não foi possível carregar a evolução
    </p>
    <GoalProgressChart v-else :goal="goal" :points="state" />
  </div>
</template>

<script setup lang="ts">
import GoalProgressChart from '@/components/goals/GoalProgressChart.vue'
import type { Goal, GoalProgressPoint } from '@/types/goal'

/** Story 8.3: área do gráfico aberto no card; a série é buscada pela view. */
defineProps<{ goal: Goal; state: GoalProgressPoint[] | 'loading' | 'error' }>()
</script>
