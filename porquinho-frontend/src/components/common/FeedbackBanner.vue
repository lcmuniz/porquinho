<template>
  <!-- A região live fica sempre no DOM (leitores de tela só anunciam mudanças numa região que já existe); a ação fica fora dela -->
  <div
    ref="root"
    :tabindex="message ? -1 : undefined"
    :class="message ? 'flex min-h-[46px] flex-wrap items-center justify-between gap-x-4 rounded-lg border border-green-200 bg-green-50 px-4 text-sm text-green-800 outline-none' : 'contents'"
    :data-testid="`${testid}-banner`"
    @mouseenter="emit('pause')"
    @mouseleave="emit('resume')"
    @focusin="emit('pause')"
    @focusout="emit('resume')"
  >
    <!-- Ícone + texto, nunca só a cor (PRODUCT.md) -->
    <p role="status" :data-testid="testid" :class="message ? 'flex items-center gap-2 py-3' : 'sr-only'">
      <CircleCheck v-if="message" class="size-4 shrink-0 text-green-700" aria-hidden="true" />{{ message }}
    </p>
    <button
      v-if="message && action"
      ref="actionButton"
      type="button"
      class="-mr-2 min-h-[44px] rounded-md px-2 font-semibold text-green-900 underline underline-offset-2 hover:bg-green-100 focus-visible:ring-[3px] focus-visible:ring-ring/50 focus-visible:outline-none"
      :data-testid="`${testid}-action`"
      @click="action.run()"
    >
      {{ action.label }}
    </button>
  </div>
</template>

<script setup lang="ts">
import { CircleCheck } from 'lucide-vue-next'
import { ref } from 'vue'
import type { FeedbackAction } from '@/composables/useFeedback'

/** Critique de 20/11/2026: a faixa verde de sucesso, com uma ação opcional como "Desfazer". */
defineProps<{ message: string | null; action?: FeedbackAction | null; testid: string }>()
const emit = defineEmits<{ pause: []; resume: [] }>()

const root = ref<HTMLElement | null>(null)
const actionButton = ref<HTMLButtonElement | null>(null)
/** Leva o foco para a ação ou, sem ação, para a própria faixa (o leitor de tela lê a mensagem). */
defineExpose({ focus: () => (actionButton.value ?? root.value)?.focus() })
</script>
