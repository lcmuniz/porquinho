<template>
  <PopoverRoot>
    <PopoverTrigger as-child>
      <!-- Ícone de 24px; o ::before amplia a área de toque para 44px sem mexer no layout -->
      <button
        type="button"
        class="relative inline-flex size-6 shrink-0 items-center justify-center rounded-full align-middle text-gray-500 before:absolute before:-inset-2.5 hover:text-purple-700 focus-visible:ring-[3px] focus-visible:ring-ring/50 focus-visible:outline-none data-[state=open]:text-purple-700"
        :aria-label="`O que é ${term}?`"
        :data-testid="`help-${testid}`"
      >
        <CircleHelp class="size-4" aria-hidden="true" />
      </button>
    </PopoverTrigger>
    <PopoverPortal>
      <PopoverContent
        side="top"
        :side-offset="6"
        :collision-padding="16"
        :aria-label="term"
        class="z-50 max-w-[min(20rem,calc(100vw-2rem))] rounded-lg border border-gray-200 bg-popover p-3 text-sm leading-relaxed font-normal text-gray-700 shadow-md outline-none data-[state=open]:animate-in data-[state=open]:fade-in-0"
        :data-testid="`help-${testid}-content`"
      >
        <slot />
        <PopoverArrow class="fill-white" />
      </PopoverContent>
    </PopoverPortal>
  </PopoverRoot>
</template>

<script setup lang="ts">
import { CircleHelp } from 'lucide-vue-next'
import { PopoverArrow, PopoverContent, PopoverPortal, PopoverRoot, PopoverTrigger } from 'reka-ui'

/**
 * Pós-critique (20/11/2026): ajuda contextual ao lado de um termo-chave. O Popover abre com clique, Enter ou Espaço,
 * leva o foco para a explicação e fecha com Esc devolvendo o foco ao ícone.
 */
defineProps<{ term: string; testid: string }>()
</script>
