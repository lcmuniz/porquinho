<template>
  <form class="space-y-4" novalidate data-testid="transfer-form" @submit.prevent="submit">
    <div class="space-y-1">
      <label for="transfer-from" class="text-sm font-medium text-gray-900">Conta de origem</label>
      <select
        id="transfer-from"
        ref="fromSelect"
        v-model="form.fromAccountId"
        :class="SELECT_CLASS"
        data-testid="transfer-from-select"
        :aria-invalid="errors.fromAccountId ? 'true' : undefined"
        :aria-describedby="errors.fromAccountId ? 'transfer-from-error' : undefined"
      >
        <option v-for="account in accounts" :key="account.id" :value="account.id">
          {{ optionLabel(account) }}
        </option>
      </select>
      <p
        v-if="errors.fromAccountId"
        id="transfer-from-error"
        class="text-sm text-red-600"
        data-testid="transfer-from-error"
      >
        {{ errors.fromAccountId }}
      </p>
    </div>

    <div class="space-y-1">
      <label for="transfer-to" class="text-sm font-medium text-gray-900">Conta de destino</label>
      <select
        id="transfer-to"
        v-model="form.toAccountId"
        :class="SELECT_CLASS"
        data-testid="transfer-to-select"
        :aria-invalid="errors.toAccountId ? 'true' : undefined"
        :aria-describedby="errors.toAccountId ? 'transfer-to-error' : undefined"
      >
        <option v-for="account in accounts" :key="account.id" :value="account.id">
          {{ optionLabel(account) }}
        </option>
      </select>
      <p
        v-if="errors.toAccountId"
        id="transfer-to-error"
        class="text-sm text-red-600"
        data-testid="transfer-to-error"
      >
        {{ errors.toAccountId }}
      </p>
    </div>

    <div class="space-y-1">
      <label for="transfer-amount" class="text-sm font-medium text-gray-900">Valor (R$)</label>
      <Input
        id="transfer-amount"
        v-model="form.amount"
        inputmode="decimal"
        placeholder="0,00"
        autocomplete="off"
        class="min-h-[44px]"
        data-testid="transfer-amount-input"
        :aria-invalid="errors.amount ? 'true' : undefined"
        :aria-describedby="errors.amount ? 'transfer-amount-error' : undefined"
      />
      <p
        v-if="errors.amount"
        id="transfer-amount-error"
        class="text-sm text-red-600"
        data-testid="transfer-amount-error"
      >
        {{ errors.amount }}
      </p>
    </div>

    <div class="space-y-1">
      <label for="transfer-date" class="text-sm font-medium text-gray-900">Data</label>
      <input
        id="transfer-date"
        v-model="form.transferDate"
        type="date"
        :max="todayISO()"
        :class="SELECT_CLASS"
        data-testid="transfer-date-input"
        :aria-invalid="errors.transferDate ? 'true' : undefined"
        :aria-describedby="errors.transferDate ? 'transfer-date-error' : undefined"
      />
      <p
        v-if="errors.transferDate"
        id="transfer-date-error"
        class="text-sm text-red-600"
        data-testid="transfer-date-error"
      >
        {{ errors.transferDate }}
      </p>
    </div>

    <div class="space-y-1">
      <label for="transfer-description" class="text-sm font-medium text-gray-900">
        Descrição <span class="font-normal text-gray-500">(opcional)</span>
      </label>
      <Input
        id="transfer-description"
        v-model="form.description"
        maxlength="200"
        placeholder="Ex.: Reserva do mês"
        autocomplete="off"
        class="min-h-[44px]"
        data-testid="transfer-description-input"
        :aria-invalid="errors.description ? 'true' : undefined"
        :aria-describedby="errors.description ? 'transfer-description-error' : undefined"
      />
      <p
        v-if="errors.description"
        id="transfer-description-error"
        class="text-sm text-red-600"
        data-testid="transfer-description-error"
      >
        {{ errors.description }}
      </p>
    </div>

    <Button
      type="submit"
      class="min-h-[44px] w-full bg-purple-600 text-white hover:bg-purple-700"
      data-testid="save-transfer-button"
      :disabled="disabled || submitting"
    >
      {{ submitting ? 'Salvando...' : 'Transferir' }}
    </Button>
  </form>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { toISODate, todayInSaoPaulo } from '@/lib/goalPlan'
import { formatBRL, parseBRL } from '@/lib/money'
import { createTransfer } from '@/services/transferService'
import type { Account } from '@/types/account'
import type { Transfer } from '@/types/transfer'

/**
 * Formulário de transferência entre contas próprias (Story 4.5). Valida localmente o que dá para conferir antes de
 * enviar (contas escolhidas e diferentes, valor > 0 com até 2 casas, data até hoje em São Paulo, descrição até 200
 * caracteres), com as mensagens do backend; o limite de 13 dígitos do valor e as contas do usuário só o backend
 * confere. Chama `POST /transfers` e mostra os erros 400 por campo. Outras falhas (404 "Conta não encontrada",
 * rede) vão para o pai em `failed`, com o `detail` da API, e os dados digitados ficam no formulário.
 *
 * Idempotência: o id da transferência é gerado uma vez por montagem (o diálogo monta o formulário a cada abertura)
 * e reaproveitado nas novas tentativas, então reenviar depois de uma falha de rede não duplica a transferência.
 */
const props = withDefaults(
  defineProps<{
    /** Contas ativas que podem ser origem ou destino. */
    accounts: Account[]
    /** Bloqueia o envio. */
    disabled?: boolean
  }>(),
  { disabled: false },
)

const emit = defineEmits<{
  created: [transfer: Transfer]
  /** `status` é o HTTP da falha, quando houver (o pai recarrega as contas no 404). */
  failed: [detail: string | undefined, status?: number]
  /** Toda tentativa de envio, antes da validação (o pai limpa o erro anterior). */
  attempt: []
}>()

/** Envio em andamento; o pai pode acompanhar com `v-model:submitting`. */
const submitting = defineModel<boolean>('submitting', { default: false })

const SELECT_CLASS =
  'flex min-h-[44px] w-full rounded-md border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 focus-visible:ring-2 focus-visible:ring-purple-600 focus-visible:outline-none'

const SAME_ACCOUNT_MESSAGE = 'Escolha contas diferentes'

type Field = 'fromAccountId' | 'toAccountId' | 'amount' | 'transferDate' | 'description'
const FIELDS: Field[] = ['fromAccountId', 'toAccountId', 'amount', 'transferDate', 'description']

/** Hoje em São Paulo (AAAA-MM-DD): padrão e máximo do campo de data. */
function todayISO(): string {
  return toISODate(todayInSaoPaulo())
}

/** Chave de idempotência desta abertura do formulário. */
const transferId = crypto.randomUUID()

const form = reactive({
  fromAccountId: props.accounts[0]?.id ?? '',
  toAccountId: props.accounts[1]?.id ?? '',
  amount: '',
  transferDate: todayISO(),
  description: '',
})
const errors = reactive<Partial<Record<Field, string>>>({})
const fromSelect = ref<HTMLSelectElement | null>(null)

interface ApiError {
  response?: { status?: number; data?: { detail?: string; errors?: Record<string, string> } }
}

function optionLabel(account: Account): string {
  return `${account.accountName} — ${formatBRL(account.currentBalance)}`
}

function clearErrors() {
  for (const key of Object.keys(errors) as Field[]) delete errors[key]
}

/** Validação local (veja o comentário do componente). Devolve o valor quando tudo está válido. */
function validate(): number | null {
  clearErrors()
  if (!form.fromAccountId) errors.fromAccountId = 'Escolha a conta de origem'
  if (!form.toAccountId) errors.toAccountId = 'Escolha a conta de destino'
  else if (form.fromAccountId === form.toAccountId) errors.toAccountId = SAME_ACCOUNT_MESSAGE

  let amount: number | null = null
  if (!form.amount.trim()) {
    errors.amount = 'Informe o valor da transferência'
  } else {
    amount = parseBRL(form.amount)
    if (amount === null) {
      errors.amount = 'Digite o valor no formato 1.234,56 (até 2 casas depois da vírgula)'
    } else if (amount <= 0) {
      errors.amount = 'O valor precisa ser maior que zero'
    }
  }

  // AAAA-MM-DD compara como texto
  if (!form.transferDate) errors.transferDate = 'Informe a data da transferência'
  else if (form.transferDate > todayISO()) errors.transferDate = 'A data não pode estar no futuro'

  if (form.description.trim().length > 200) {
    errors.description = 'Use no máximo 200 caracteres na descrição'
  }
  return Object.keys(errors).length === 0 ? amount : null
}

/** O erro de contas iguais vem como `differentAccounts`; aparece no campo de destino. */
function fieldOf(key: string): Field | undefined {
  if (key === 'differentAccounts') return 'toAccountId'
  return FIELDS.includes(key as Field) ? (key as Field) : undefined
}

/**
 * Trava local do envio: com `v-model:submitting`, o valor do modelo só muda quando o pai re-renderiza, então
 * dois envios no mesmo tick passariam pela checagem.
 */
let inFlight = false

async function submit() {
  if (props.disabled || submitting.value || inFlight) return
  emit('attempt')
  const amount = validate()
  if (amount === null) return

  inFlight = true
  submitting.value = true
  try {
    const created = await createTransfer({
      id: transferId,
      fromAccountId: form.fromAccountId,
      toAccountId: form.toAccountId,
      amount,
      transferDate: form.transferDate,
      description: form.description.trim() || null,
    })
    emit('created', created)
  } catch (err) {
    const response = (err as ApiError)?.response
    const entries = Object.entries(response?.data?.errors ?? {})
    const known = entries.flatMap(([key, message]) => {
      const field = fieldOf(key)
      return field ? [[field, message] as const] : []
    })
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

/**
 * A lista de contas pode mudar com o diálogo aberto (o pai recarrega depois de um 404): uma conta escolhida que
 * sumiu dá lugar a outra disponível.
 */
watch(
  () => props.accounts,
  (accounts) => {
    const ids = accounts.map((a) => a.id)
    if (!ids.includes(form.fromAccountId)) form.fromAccountId = ids[0] ?? ''
    if (!ids.includes(form.toAccountId)) {
      form.toAccountId = ids.find((id) => id !== form.fromAccountId) ?? ''
    }
  },
)

/** Foca o primeiro campo (usado ao abrir o diálogo). */
function focus() {
  fromSelect.value?.focus()
}

defineExpose({ focus })
</script>
