<template>
  <Dialog :open="dialogOpen" @update:open="onOpenChange">
    <DialogContent data-testid="amount-dialog" @close-auto-focus="returnFocus">
      <DialogHeader>
        <DialogTitle>{{
          action?.kind === 'allocation' ? 'Editar alocação' : 'Aportar'
        }}</DialogTitle>
        <DialogDescription>
          {{
            action?.kind === 'allocation'
              ? `Quanto guardar por mês em ${action?.goal.name}.`
              : `Quanto você guardou agora em ${action?.goal.name}.`
          }}
        </DialogDescription>
      </DialogHeader>
      <p
        v-if="formError"
        role="alert"
        data-testid="amount-form-error"
        class="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800"
      >
        {{ formError }}
      </p>
      <form class="space-y-4" novalidate data-testid="amount-form" @submit.prevent="save">
        <div class="space-y-1">
          <label for="goal-amount" class="text-sm font-medium text-gray-900">
            {{ action?.kind === 'allocation' ? 'Aporte mensal (R$)' : 'Valor do aporte (R$)' }}
          </label>
          <Input
            id="goal-amount"
            v-model="text"
            class="min-h-[44px]"
            inputmode="decimal"
            placeholder="0,00"
            autocomplete="off"
            data-testid="amount-input"
            :aria-invalid="error ? 'true' : undefined"
            :aria-describedby="error ? 'goal-amount-error' : undefined"
          />
          <p
            v-if="error"
            id="goal-amount-error"
            class="text-sm text-red-600"
            data-testid="amount-error"
          >
            {{ error }}
          </p>
        </div>
        <DialogFooter>
          <Button
            type="button"
            variant="outline"
            class="min-h-[44px]"
            :disabled="saving"
            data-testid="amount-cancel"
            @click="dialogOpen = false"
          >
            Cancelar
          </Button>
          <Button type="submit" class="min-h-[44px]" :disabled="saving" data-testid="amount-save">
            {{ saving ? 'Salvando...' : 'Salvar' }}
          </Button>
        </DialogFooter>
      </form>
    </DialogContent>
  </Dialog>
</template>

<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { formatBRLInput, parseBRL } from '@/lib/money'
import { contributeToGoal, updateGoalAllocation } from '@/services/goalService'
import type { Goal } from '@/types/goal'

/** Story 8.2: um diálogo só para "Editar alocação" e "Aportar"; muda o rótulo, a regra e o endpoint. */
type AmountKind = 'allocation' | 'contribution'

const props = defineProps<{ focusFallback: HTMLElement | null }>()
const emit = defineEmits<{ saved: [kind: AmountKind, goal: Goal] }>()

const SAVE_FAILED = 'Não foi possível salvar. Verifique sua conexão e tente novamente.'

interface ApiError {
  response?: { status?: number; data?: { detail?: string; errors?: Record<string, string> } }
}

// Os textos leem a última ação; ela só muda ao abrir, então o diálogo não troca de texto enquanto fecha
const action = ref<{ kind: AmountKind; goal: Goal } | null>(null)
const dialogOpen = ref(false)
const text = ref('')
const error = ref<string | null>(null)
const formError = ref<string | null>(null)
const saving = ref(false)
let trigger: HTMLElement | null = null

function open(kind: AmountKind, goal: Goal, event: Event) {
  trigger = event.currentTarget as HTMLElement | null
  text.value =
    kind === 'allocation' && goal.monthlyAllocation !== null
      ? formatBRLInput(goal.monthlyAllocation)
      : ''
  error.value = null
  formError.value = null
  action.value = { kind, goal }
  dialogOpen.value = true
}

function onOpenChange(value: boolean) {
  if (!value && !saving.value) dialogOpen.value = false
}

function returnFocus(event: Event) {
  event.preventDefault()
  void nextTick(() => (trigger?.isConnected ? trigger : props.focusFallback)?.focus())
}

function validate(kind: AmountKind, input: string): number | string {
  if (!input.trim())
    return kind === 'allocation' ? 'Informe o aporte mensal' : 'Informe o valor do aporte'
  const value = parseBRL(input)
  if (value === null) return 'Informe um valor válido, com até 2 casas decimais'
  if (kind === 'allocation' && value < 0) return 'O aporte mensal não pode ser negativo'
  if (kind === 'contribution' && value <= 0) return 'O aporte precisa ser maior que zero'
  return value
}

async function save() {
  const current = action.value
  if (!dialogOpen.value || !current || saving.value) return
  error.value = null
  formError.value = null
  const value = validate(current.kind, text.value)
  if (typeof value === 'string') {
    error.value = value
    return
  }
  saving.value = true
  try {
    const updated =
      current.kind === 'allocation'
        ? await updateGoalAllocation(current.goal.id, value)
        : await contributeToGoal(current.goal.id, value)
    dialogOpen.value = false
    emit('saved', current.kind, updated)
  } catch (err) {
    const response = (err as ApiError)?.response
    const field =
      response?.data?.errors?.[current.kind === 'allocation' ? 'monthlyAllocation' : 'amount']
    if (response?.status === 400 && field) error.value = field
    else formError.value = response?.data?.detail ?? SAVE_FAILED
  } finally {
    saving.value = false
  }
}

defineExpose({ open })
</script>
