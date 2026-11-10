<template>
  <form class="space-y-4" novalidate data-testid="reallocate-form" @submit.prevent="submit">
    <div class="space-y-1">
      <label for="reallocate-from" class="text-sm font-medium text-gray-900">Tirar de</label>
      <select
        id="reallocate-from"
        ref="fromSelect"
        v-model="form.fromCategoryId"
        :class="SELECT_CLASS"
        data-testid="reallocate-from-select"
        :aria-invalid="errors.fromCategoryId ? 'true' : undefined"
        :aria-describedby="errors.fromCategoryId ? 'reallocate-from-error' : undefined"
      >
        <option v-for="option in sources" :key="option.id" :value="option.id">{{ optionLabel(option) }}</option>
      </select>
      <p
        v-if="errors.fromCategoryId"
        id="reallocate-from-error"
        class="text-sm text-red-600"
        data-testid="reallocate-from-error"
      >
        {{ errors.fromCategoryId }}
      </p>
    </div>

    <div class="space-y-1">
      <label for="reallocate-to" class="text-sm font-medium text-gray-900">Colocar em</label>
      <select
        id="reallocate-to"
        v-model="form.toCategoryId"
        :class="SELECT_CLASS"
        data-testid="reallocate-to-select"
        :aria-invalid="errors.toCategoryId ? 'true' : undefined"
        :aria-describedby="errors.toCategoryId ? 'reallocate-to-error' : undefined"
      >
        <option v-for="option in options" :key="option.id" :value="option.id">{{ optionLabel(option) }}</option>
      </select>
      <p
        v-if="errors.toCategoryId"
        id="reallocate-to-error"
        class="text-sm text-red-600"
        data-testid="reallocate-to-error"
      >
        {{ errors.toCategoryId }}
      </p>
    </div>

    <div class="space-y-1">
      <label for="reallocate-amount" class="text-sm font-medium text-gray-900">Valor (R$)</label>
      <Input
        id="reallocate-amount"
        v-model="form.amount"
        inputmode="decimal"
        placeholder="0,00"
        autocomplete="off"
        class="min-h-[44px]"
        data-testid="reallocate-amount-input"
        :aria-invalid="errors.amount ? 'true' : undefined"
        :aria-describedby="errors.amount ? 'reallocate-amount-error' : undefined"
      />
      <p v-if="errors.amount" id="reallocate-amount-error" class="text-sm text-red-600" data-testid="reallocate-amount-error">
        {{ errors.amount }}
      </p>
    </div>

    <Button
      type="submit"
      class="min-h-[44px] w-full bg-purple-600 text-white hover:bg-purple-700"
      data-testid="save-reallocate-button"
      :disabled="submitting"
    >
      {{ submitting ? 'Movendo...' : 'Mover dinheiro' }}
    </Button>
  </form>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { formatBRL, parseBRL } from '@/lib/money'
import { reallocateBudget } from '@/services/budgetService'
import type { Budget } from '@/types/budget'

/**
 * Story 6.5: move dinheiro entre duas categorias filhas do mês. A origem só lista categorias com restante > 0; o
 * restante de cada opção é o do último salvo. Valida localmente o que dá (categorias escolhidas e diferentes, valor
 * > 0 com até 2 casas); o limite do restante e o estouro só o backend confere. Erros 400 de campo ficam no campo;
 * os demais vão para o pai em `failed` com o `detail`, e o que foi digitado fica no formulário.
 */
export interface ReallocateOption {
  id: string
  name: string
  /** Restante do mês; nulo = sem orçamento. */
  remaining: number | null
}

const props = defineProps<{ month: string; options: ReallocateOption[] }>()

const emit = defineEmits<{
  /** Mês recalculado e a mensagem de sucesso. */
  moved: [budget: Budget, message: string]
  failed: [detail: string | undefined]
  /** Toda tentativa de envio, antes da validação (o pai limpa o erro anterior). */
  attempt: []
}>()

const submitting = defineModel<boolean>('submitting', { default: false })

const SELECT_CLASS =
  'flex min-h-[44px] w-full rounded-md border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 focus-visible:ring-2 focus-visible:ring-purple-600 focus-visible:outline-none'

type Field = 'fromCategoryId' | 'toCategoryId' | 'amount'
const FIELDS: Field[] = ['fromCategoryId', 'toCategoryId', 'amount']

// estourada ou com restante 0: o backend sempre recusa
const sources = computed(() => props.options.filter((o) => o.remaining !== null && o.remaining > 0))

const form = reactive({
  fromCategoryId: sources.value[0]?.id ?? '',
  toCategoryId: props.options.find((o) => o.id !== sources.value[0]?.id)?.id ?? '',
  amount: '',
})
const errors = reactive<Partial<Record<Field, string>>>({})
const fromSelect = ref<HTMLSelectElement | null>(null)

interface ApiError {
  response?: { status?: number; data?: { detail?: string; errors?: Record<string, string> } }
}

function optionLabel(option: ReallocateOption): string {
  return option.remaining === null
    ? `${option.name} — sem orçamento`
    : `${option.name} — restante ${formatBRL(option.remaining)}`
}

function nameOf(id: string): string {
  return props.options.find((o) => o.id === id)?.name ?? ''
}

function validate(): number | null {
  for (const key of Object.keys(errors) as Field[]) delete errors[key]
  if (!form.fromCategoryId) errors.fromCategoryId = 'Escolha a categoria de origem'
  if (!form.toCategoryId) errors.toCategoryId = 'Escolha a categoria de destino'
  else if (form.fromCategoryId === form.toCategoryId) errors.toCategoryId = 'Escolha categorias diferentes'

  let amount: number | null = null
  if (!form.amount.trim()) {
    errors.amount = 'Informe o valor'
  } else {
    amount = parseBRL(form.amount)
    if (amount === null) errors.amount = 'Digite o valor no formato 1.234,56 (até 2 casas depois da vírgula)'
    else if (amount <= 0) errors.amount = 'O valor precisa ser maior que zero'
  }
  return Object.keys(errors).length === 0 ? amount : null
}

// Trava local: o modelo `submitting` só muda quando o pai re-renderiza
let inFlight = false

async function submit() {
  if (submitting.value || inFlight) return
  emit('attempt')
  const amount = validate()
  if (amount === null) return

  inFlight = true
  submitting.value = true
  try {
    const data = await reallocateBudget(props.month, {
      fromCategoryId: form.fromCategoryId,
      toCategoryId: form.toCategoryId,
      amount,
    })
    emit('moved', data, `${formatBRL(amount)} movido de ${nameOf(form.fromCategoryId)} para ${nameOf(form.toCategoryId)}`)
  } catch (err) {
    const response = (err as ApiError)?.response
    const entries = Object.entries(response?.data?.errors ?? {})
    const known = entries.filter(([key]) => FIELDS.includes(key as Field))
    if (response?.status === 400 && known.length > 0) {
      Object.assign(errors, Object.fromEntries(known))
      if (known.length < entries.length) emit('failed', response.data?.detail)
    } else {
      emit('failed', response?.data?.detail)
    }
  } finally {
    inFlight = false
    submitting.value = false
  }
}

/** Foca o primeiro campo (usado ao abrir o diálogo). */
function focus() {
  fromSelect.value?.focus()
}

defineExpose({ focus })
</script>
