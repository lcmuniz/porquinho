<template>
  <span class="inline-flex items-center gap-1 font-medium">
    <component
      :is="info.icon"
      v-if="info"
      class="size-4 shrink-0"
      :class="info.text"
      aria-hidden="true"
    />
    <template v-if="info">{{ info.label(remaining ?? 0) }}</template>
    <slot v-else />
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { STATUS } from '@/lib/budgetStatus'
import type { BudgetStatus } from '@/types/budget'

/**
 * Retro Epic 6 (A6/D2): ícone + texto do status do orçamento, sempre juntos (não só a cor). Sem status, mostra o slot
 * (ex.: "Sem orçamento"). Cor do texto e testids vêm de quem usa.
 */
const props = defineProps<{ status: BudgetStatus | null; remaining: number | null }>()

const info = computed(() => (props.status ? STATUS[props.status] : null))
</script>
