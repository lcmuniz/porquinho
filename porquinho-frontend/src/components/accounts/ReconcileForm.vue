<template>
  <form class="space-y-4" novalidate data-testid="reconcile-form" @submit.prevent="submit">
    <p class="text-sm text-gray-700" data-testid="reconcile-current-balance">
      Saldo no porquinho:
      <span
        class="font-semibold"
        :class="account.currentBalance < 0 ? 'text-red-600' : 'text-gray-900'"
        >{{ formatBRL(account.currentBalance) }}</span
      >
    </p>

    <div class="space-y-1">
      <label for="reconcile-bank-balance" class="text-sm font-medium text-gray-900">
        Saldo no banco (R$)
      </label>
      <Input
        id="reconcile-bank-balance"
        ref="bankInput"
        v-model="form.bankBalance"
        inputmode="decimal"
        placeholder="0,00"
        autocomplete="off"
        class="min-h-[44px]"
        data-testid="reconcile-bank-balance-input"
        :aria-invalid="errors.bankBalance ? 'true' : undefined"
        :aria-describedby="
          errors.bankBalance
            ? 'reconcile-bank-balance-error reconcile-difference'
            : 'reconcile-difference'
        "
      />
      <p
        v-if="errors.bankBalance"
        id="reconcile-bank-balance-error"
        class="text-sm text-red-600"
        data-testid="reconcile-bank-balance-error"
      >
        {{ errors.bankBalance }}
      </p>
    </div>

    <p
      id="reconcile-difference"
      class="text-sm text-gray-700"
      aria-live="polite"
      data-testid="reconcile-difference"
    >
      Diferença:
      <span
        class="font-semibold"
        :class="differenceCents !== null && differenceCents < 0 ? 'text-red-600' : 'text-gray-900'"
        data-testid="reconcile-difference-value"
        >{{ differenceCents === null ? '—' : formatSignedBRL(differenceCents / 100) }}</span
      >
    </p>

    <div class="space-y-1">
      <label for="reconcile-reason" class="text-sm font-medium text-gray-900">
        Motivo <span class="font-normal text-gray-500">(opcional)</span>
      </label>
      <Input
        id="reconcile-reason"
        v-model="form.reason"
        maxlength="200"
        placeholder="Ex.: Tarifa não registrada"
        autocomplete="off"
        class="min-h-[44px]"
        data-testid="reconcile-reason-input"
        :aria-invalid="errors.reason ? 'true' : undefined"
        :aria-describedby="errors.reason ? 'reconcile-reason-error' : undefined"
      />
      <p
        v-if="errors.reason"
        id="reconcile-reason-error"
        class="text-sm text-red-600"
        data-testid="reconcile-reason-error"
      >
        {{ errors.reason }}
      </p>
    </div>

    <template v-if="differenceCents !== null && differenceCents !== 0">
      <p class="text-sm font-medium text-gray-900" data-testid="reconcile-question">
        Deseja ajustar o saldo?
      </p>
      <div class="flex flex-col gap-2 sm:flex-row">
        <Button
          type="submit"
          class="min-h-[44px] flex-1 bg-purple-600 text-white hover:bg-purple-700"
          data-testid="reconcile-adjust-button"
          :disabled="disabled || submitting"
        >
          {{ submitting ? 'Salvando...' : 'Ajustar saldo' }}
        </Button>
        <Button
          type="button"
          variant="outline"
          class="min-h-[44px] flex-1"
          data-testid="reconcile-later-button"
          :disabled="submitting"
          @click="emit('dismiss')"
        >
          Investigar depois
        </Button>
      </div>
    </template>
    <template v-else>
      <Button
        type="submit"
        class="min-h-[44px] w-full bg-purple-600 text-white hover:bg-purple-700"
        data-testid="reconcile-confirm-button"
        :disabled="disabled || submitting"
      >
        {{ submitting ? 'Salvando...' : 'Confirmar reconciliação' }}
      </Button>
      <!-- Com diferença ≠ 0, "Investigar depois" já fecha sem mudar nada -->
      <Button
        type="button"
        variant="outline"
        class="min-h-[44px] w-full"
        data-testid="cancel-reconcile"
        :disabled="submitting"
        @click="emit('dismiss')"
      >
        Cancelar
      </Button>
    </template>
  </form>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { formatBRL, formatSignedBRL, parseBRL } from '@/lib/money'
import { reconcileAccount } from '@/services/accountService'
import type { Account, ReconciliationResult } from '@/types/account'

/**
 * Formulário de reconciliação do saldo com o banco (Story 4.6). Mostra o saldo da tela e a diferença enquanto o
 * usuário digita; quem decide a diferença gravada é o servidor, a partir do saldo que ele tem. Com diferença ≠ 0
 * pergunta "Deseja ajustar o saldo?" ("Ajustar saldo" envia, "Investigar depois" emite `dismiss`); com diferença 0
 * os botões são "Confirmar reconciliação" e "Cancelar" (também emite `dismiss`). Erros 400 aparecem no campo; outras falhas (404, rede) vão para o pai em
 * `failed`, com o `detail` da API e o status.
 */
const props = withDefaults(
  defineProps<{
    account: Account
    /** Bloqueia o envio. */
    disabled?: boolean
  }>(),
  { disabled: false },
)

const emit = defineEmits<{
  reconciled: [result: ReconciliationResult]
  failed: [detail: string | undefined, status?: number]
  /** Toda tentativa de envio, antes da validação (o pai limpa o erro anterior). */
  attempt: []
  /** "Investigar depois": fecha sem mudar nada. */
  dismiss: []
}>()

/** Envio em andamento; o pai pode acompanhar com `v-model:submitting`. */
const submitting = defineModel<boolean>('submitting', { default: false })

type Field = 'bankBalance' | 'reason'
const FIELDS: Field[] = ['bankBalance', 'reason']

const form = reactive({ bankBalance: '', reason: '' })
const errors = reactive<Partial<Record<Field, string>>>({})
const bankInput = ref<{ $el?: HTMLInputElement } | null>(null)

interface ApiError {
  response?: { status?: number; data?: { detail?: string; errors?: Record<string, string> } }
}

/** Diferença em centavos inteiros (banco − tela); null enquanto o saldo do banco não é um valor válido. */
const differenceCents = computed(() => {
  const bank = parseBRL(form.bankBalance)
  if (bank === null) return null
  return Math.round(bank * 100) - Math.round(props.account.currentBalance * 100)
})

function clearErrors() {
  for (const key of Object.keys(errors) as Field[]) delete errors[key]
}

/** Devolve o saldo do banco quando tudo está válido. */
/** DECIMAL(15,2): a mesma regra e a mesma mensagem do backend. */
const MAX_INTEGER_DIGITS = 13
const TOO_LARGE_MESSAGE =
  'Informe o saldo com até 2 casas decimais e no máximo 13 dígitos antes da vírgula'

function validate(): number | null {
  clearErrors()
  let bank: number | null = null
  if (!form.bankBalance.trim()) {
    errors.bankBalance = 'Informe o saldo no banco'
  } else {
    bank = parseBRL(form.bankBalance)
    const integerDigits = form.bankBalance.split(',')[0]!.replace(/\D/g, '').replace(/^0+/, '')
    if (integerDigits.length > MAX_INTEGER_DIGITS) {
      bank = null
      errors.bankBalance = TOO_LARGE_MESSAGE
    } else if (bank === null) {
      errors.bankBalance = 'Digite o saldo no formato 1.234,56 (até 2 casas depois da vírgula)'
    }
  }
  if (form.reason.trim().length > 200) errors.reason = 'Use no máximo 200 caracteres no motivo'
  return Object.keys(errors).length === 0 ? bank : null
}

/** Trava local: dois envios no mesmo tick passariam pela checagem do modelo. */
let inFlight = false

async function submit() {
  if (props.disabled || submitting.value || inFlight) return
  emit('attempt')
  const bankBalance = validate()
  if (bankBalance === null) return

  inFlight = true
  submitting.value = true
  try {
    const result = await reconcileAccount(props.account.id, {
      bankBalance,
      reason: form.reason.trim() || null,
    })
    emit('reconciled', result)
  } catch (err) {
    const response = (err as ApiError)?.response
    const entries = Object.entries(response?.data?.errors ?? {})
    const known = entries.filter(([key]) => FIELDS.includes(key as Field))
    if (response?.status === 400 && known.length > 0) {
      Object.assign(errors, Object.fromEntries(known))
      // Há erros de campos que o formulário não mostra: a mensagem geral não pode se perder
      if (known.length < entries.length) emit('failed', response.data?.detail, response.status)
    } else {
      emit('failed', response?.data?.detail, response?.status)
    }
  } finally {
    inFlight = false
    submitting.value = false
  }
}

/** Foca o campo do saldo do banco (usado ao abrir o diálogo). */
function focus() {
  bankInput.value?.$el?.focus()
}

defineExpose({ focus })
</script>
