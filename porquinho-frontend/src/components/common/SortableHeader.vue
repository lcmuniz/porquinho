<template>
  <th scope="col" :aria-sort="sort" :data-testid="`sort-${sortId}-header`">
    <button
      type="button"
      class="inline-flex min-h-[44px] items-center gap-1 font-medium hover:text-gray-900"
      :data-testid="`sort-${sortId}`"
      @click="emit('sort')"
    >
      {{ label }}
      <component
        :is="sort === 'ascending' ? ArrowUp : ArrowDown"
        v-if="sort !== 'none'"
        class="size-4"
        aria-hidden="true"
      />
    </button>
  </th>
</template>

<script setup lang="ts">
import { ArrowDown, ArrowUp } from 'lucide-vue-next'

/**
 * Retro Epic 6 (A6/D2): cabeçalho de tabela ordenável com `aria-sort`, botão de 44px e seta da direção (sem seta
 * quando a coluna não é a ordenada). Classes de padding e alinhamento vêm de quem usa.
 */
defineProps<{ label: string; sortId: string; sort: 'ascending' | 'descending' | 'none' }>()
const emit = defineEmits<{ sort: [] }>()
</script>
