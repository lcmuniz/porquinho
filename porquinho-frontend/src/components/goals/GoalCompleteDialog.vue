<template>
  <Dialog :open="dialogOpen" @update:open="onOpenChange">
    <DialogContent data-testid="complete-dialog" @close-auto-focus="returnFocus">
      <DialogHeader>
        <DialogTitle>Marcar como concluída</DialogTitle>
        <DialogDescription>Tem certeza? Esta meta será arquivada</DialogDescription>
      </DialogHeader>
      <p
        v-if="error"
        role="alert"
        data-testid="complete-error"
        class="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800"
      >
        {{ error }}
      </p>
      <DialogFooter>
        <Button
          type="button"
          variant="outline"
          class="min-h-[44px]"
          :disabled="saving"
          data-testid="complete-cancel"
          @click="dialogOpen = false"
        >
          Cancelar
        </Button>
        <Button
          type="button"
          class="min-h-[44px]"
          :disabled="saving"
          data-testid="complete-confirm"
          @click="confirm"
        >
          {{ saving ? 'Salvando...' : 'Marcar como concluída' }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>

<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { completeGoal } from '@/services/goalService'
import type { Goal } from '@/types/goal'

/** Story 8.7: confirmação antes de concluir manualmente (vale mesmo abaixo do alvo). */
const props = defineProps<{ focusFallback: HTMLElement | null }>()
const emit = defineEmits<{ completed: [goal: Goal] }>()

const SAVE_FAILED = 'Não foi possível salvar. Verifique sua conexão e tente novamente.'

interface ApiError {
  response?: { data?: { detail?: string } }
}

const target = ref<Goal | null>(null)
const dialogOpen = ref(false)
const saving = ref(false)
const error = ref<string | null>(null)
let trigger: HTMLElement | null = null

function open(goal: Goal, event: Event) {
  trigger = event.currentTarget as HTMLElement | null
  target.value = goal
  error.value = null
  dialogOpen.value = true
}

function onOpenChange(value: boolean) {
  if (!value && !saving.value) dialogOpen.value = false
}

function returnFocus(event: Event) {
  event.preventDefault()
  // Concluída, o botão sai da tela com o card: o foco vai para o título
  void nextTick(() => (trigger?.isConnected ? trigger : props.focusFallback)?.focus())
}

async function confirm() {
  const goal = target.value
  if (!goal || saving.value) return
  saving.value = true
  error.value = null
  try {
    const updated = await completeGoal(goal.id)
    dialogOpen.value = false
    emit('completed', updated)
  } catch (err) {
    error.value = (err as ApiError)?.response?.data?.detail ?? SAVE_FAILED
  } finally {
    saving.value = false
  }
}

defineExpose({ open })
</script>
