<template>
  <form class="space-y-4" novalidate data-testid="card-form" @submit.prevent="submit">
    <div class="space-y-1">
      <label for="card-name" class="text-sm font-medium text-gray-900">Nome do cartão</label>
      <Input
        id="card-name"
        ref="nameInput"
        v-model="form.cardName"
        maxlength="100"
        placeholder="Ex.: Nubank Roxinho"
        autocomplete="off"
        class="min-h-[44px]"
        data-testid="card-name-input"
        :aria-invalid="errors.cardName ? 'true' : undefined"
        :aria-describedby="errors.cardName ? 'card-name-error' : undefined"
      />
      <p
        v-if="errors.cardName"
        id="card-name-error"
        class="text-sm text-red-600"
        data-testid="card-name-error"
      >
        {{ errors.cardName }}
      </p>
    </div>

    <div class="space-y-1">
      <label for="card-bank-name" class="text-sm font-medium text-gray-900">
        Banco <span class="font-normal text-gray-500">(opcional)</span>
      </label>
      <Input
        id="card-bank-name"
        v-model="form.bankName"
        list="card-bank-suggestions"
        maxlength="100"
        placeholder="Digite ou escolha um banco"
        autocomplete="off"
        class="min-h-[44px]"
        data-testid="card-bank-name-input"
        :aria-invalid="errors.bankName ? 'true' : undefined"
        :aria-describedby="errors.bankName ? 'card-bank-name-error' : undefined"
      />
      <datalist id="card-bank-suggestions">
        <option v-for="bank in BANKS" :key="bank" :value="bank" />
      </datalist>
      <p v-if="errors.bankName" id="card-bank-name-error" class="text-sm text-red-600">
        {{ errors.bankName }}
      </p>
    </div>

    <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
      <div class="space-y-1">
        <label for="card-limit" class="text-sm font-medium text-gray-900">
          Limite do cartão (R$)
        </label>
        <Input
          id="card-limit"
          v-model="form.cardLimit"
          inputmode="decimal"
          placeholder="0,00"
          autocomplete="off"
          class="min-h-[44px]"
          data-testid="card-limit-input"
          :aria-invalid="errors.cardLimit ? 'true' : undefined"
          :aria-describedby="
            errors.cardLimit ? 'card-limit-error card-limit-hint' : 'card-limit-hint'
          "
        />
        <p
          v-if="errors.cardLimit"
          id="card-limit-error"
          class="text-sm text-red-600"
          data-testid="card-limit-error"
        >
          {{ errors.cardLimit }}
        </p>
        <p id="card-limit-hint" class="text-xs text-gray-500">Ex.: 5.000,00</p>
      </div>

      <div class="space-y-1">
        <label for="card-due-day" class="text-sm font-medium text-gray-900">
          Dia do vencimento
        </label>
        <Input
          id="card-due-day"
          v-model="form.dueDay"
          type="number"
          inputmode="numeric"
          min="1"
          max="31"
          step="1"
          placeholder="Ex.: 10"
          autocomplete="off"
          class="min-h-[44px]"
          data-testid="card-due-day-input"
          :aria-invalid="errors.dueDay ? 'true' : undefined"
          :aria-describedby="
            errors.dueDay ? 'card-due-day-error card-due-day-hint' : 'card-due-day-hint'
          "
        />
        <p
          v-if="errors.dueDay"
          id="card-due-day-error"
          class="text-sm text-red-600"
          data-testid="card-due-day-error"
        >
          {{ errors.dueDay }}
        </p>
        <p id="card-due-day-hint" class="text-xs text-gray-500">
          Dia do mês em que a fatura vence (1 a 31).
        </p>
      </div>
    </div>

    <Button
      type="submit"
      :variant="primary ? 'default' : 'outline'"
      class="min-h-[44px] w-full"
      :class="
        primary
          ? 'bg-purple-600 text-white hover:bg-purple-700'
          : 'border-purple-600 text-purple-700'
      "
      data-testid="add-card-button"
      :disabled="disabled || submitting"
    >
      <Plus class="h-4 w-4" aria-hidden="true" />
      {{ submitting ? submittingLabel : submitLabel }}
    </Button>
  </form>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { Plus } from 'lucide-vue-next'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { BANKS } from '@/lib/banks'
import { parseBRL } from '@/lib/money'
import { createCard } from '@/services/cardService'
import type { CreditCard } from '@/types/creditCard'

/**
 * Formulário de cartão de crédito (Stories 3.3 e 4.2): valida localmente, chama
 * `POST /credit-cards` e mostra os erros 400 por campo. Outras falhas vão para o pai em `failed`
 * (com o `detail` da API, se houver), e os dados digitados ficam no formulário.
 */
const props = withDefaults(
  defineProps<{
    /** Bloqueia o envio (ex.: lista ainda carregando ou outra ação em andamento). */
    disabled?: boolean
    submitLabel?: string
    submittingLabel?: string
    /** Botão de envio em destaque (diálogo) em vez de contorno (passo 3). */
    primary?: boolean
  }>(),
  {
    disabled: false,
    submitLabel: 'Adicionar cartão',
    submittingLabel: 'Adicionando...',
    primary: false,
  },
)

const emit = defineEmits<{
  created: [card: CreditCard]
  failed: [detail: string | undefined]
  /** Toda tentativa de envio, antes da validação (o pai limpa o erro anterior). */
  attempt: []
}>()

/** Envio em andamento; o pai pode acompanhar com `v-model:submitting`. */
const submitting = defineModel<boolean>('submitting', { default: false })

const DUE_DAY_MESSAGE = 'Informe um dia entre 1 e 31'

type Field = 'cardName' | 'bankName' | 'cardLimit' | 'dueDay'
const FIELDS: Field[] = ['cardName', 'bankName', 'cardLimit', 'dueDay']

const form = reactive({
  cardName: '',
  bankName: '',
  cardLimit: '',
  // <input type="number">: o v-model pode devolver número ou texto (vazio)
  dueDay: '' as string | number,
})
const errors = reactive<Partial<Record<Field, string>>>({})
const nameInput = ref<{ $el?: HTMLInputElement } | null>(null)

interface ApiError {
  response?: { status?: number; data?: { detail?: string; errors?: Record<string, string> } }
}

function clearErrors() {
  for (const key of Object.keys(errors) as Field[]) delete errors[key]
}

/** Validação local, com as mesmas regras e mensagens do backend. */
function validate(): { cardLimit: number; dueDay: number } | null {
  clearErrors()
  const name = form.cardName.trim()
  if (!name) errors.cardName = 'Informe o nome do cartão'
  else if (name.length > 100) errors.cardName = 'Use no máximo 100 caracteres no nome do cartão'

  if (form.bankName.trim().length > 100) {
    errors.bankName = 'Use no máximo 100 caracteres no nome do banco'
  }

  let cardLimit: number | null = null
  if (!form.cardLimit.trim()) {
    errors.cardLimit = 'Informe o limite do cartão (use 0,00 se não souber)'
  } else {
    cardLimit = parseBRL(form.cardLimit)
    if (cardLimit === null) {
      errors.cardLimit = 'Digite o limite no formato 5.000,00 (até 2 casas depois da vírgula)'
    } else if (cardLimit < 0) {
      errors.cardLimit = 'O limite não pode ser negativo'
    }
  }

  const dayText = String(form.dueDay ?? '').trim()
  const dueDay = /^\d{1,2}$/.test(dayText) ? Number(dayText) : NaN
  if (!(dueDay >= 1 && dueDay <= 31)) errors.dueDay = DUE_DAY_MESSAGE

  if (Object.keys(errors).length > 0 || cardLimit === null) return null
  return { cardLimit, dueDay }
}

function reset() {
  form.cardName = ''
  form.bankName = ''
  form.cardLimit = ''
  form.dueDay = ''
  clearErrors()
}

/**
 * Trava local do envio: com `v-model:submitting`, o valor do modelo só muda quando o pai
 * re-renderiza, então dois envios no mesmo tick passariam pela checagem.
 */
let inFlight = false

async function submit() {
  if (props.disabled || submitting.value || inFlight) return
  emit('attempt')
  const values = validate()
  if (values === null) return

  inFlight = true
  submitting.value = true
  try {
    const created = await createCard({
      cardName: form.cardName.trim(),
      bankName: form.bankName.trim() || null,
      cardLimit: values.cardLimit,
      dueDay: values.dueDay,
    })
    reset()
    emit('created', created)
  } catch (err) {
    const response = (err as ApiError)?.response
    const entries = Object.entries(response?.data?.errors ?? {})
    const known = entries.filter(([key]) => FIELDS.includes(key as Field))
    if (response?.status === 400 && known.length > 0) {
      Object.assign(errors, Object.fromEntries(known))
      // Há erros de campos que o formulário não mostra: a mensagem geral não pode se perder
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
  nameInput.value?.$el?.focus()
}

defineExpose({ focus })
</script>
