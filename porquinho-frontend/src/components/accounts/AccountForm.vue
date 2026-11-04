<template>
  <form class="space-y-4" novalidate data-testid="account-form" @submit.prevent="submit">
    <div class="space-y-1">
      <label for="account-name" class="text-sm font-medium text-gray-900">Nome da conta</label>
      <Input
        id="account-name"
        ref="nameInput"
        v-model="form.accountName"
        maxlength="100"
        placeholder="Ex.: Nubank"
        autocomplete="off"
        class="min-h-[44px]"
        data-testid="account-name-input"
        :aria-invalid="errors.accountName ? 'true' : undefined"
        :aria-describedby="errors.accountName ? 'account-name-error' : undefined"
      />
      <p
        v-if="errors.accountName"
        id="account-name-error"
        class="text-sm text-red-600"
        data-testid="account-name-error"
      >
        {{ errors.accountName }}
      </p>
    </div>

    <div class="space-y-1">
      <label for="bank-name" class="text-sm font-medium text-gray-900">
        Banco <span class="font-normal text-gray-500">(opcional)</span>
      </label>
      <Input
        id="bank-name"
        v-model="form.bankName"
        list="bank-suggestions"
        maxlength="100"
        placeholder="Digite ou escolha um banco"
        autocomplete="off"
        class="min-h-[44px]"
        data-testid="bank-name-input"
        :aria-invalid="errors.bankName ? 'true' : undefined"
        :aria-describedby="errors.bankName ? 'bank-name-error' : undefined"
      />
      <datalist id="bank-suggestions">
        <option v-for="bank in BANKS" :key="bank" :value="bank" />
      </datalist>
      <p v-if="errors.bankName" id="bank-name-error" class="text-sm text-red-600">
        {{ errors.bankName }}
      </p>
    </div>

    <fieldset class="space-y-2">
      <legend class="text-sm font-medium text-gray-900">Tipo da conta</legend>
      <div class="grid grid-cols-2 gap-3">
        <label
          v-for="option in ACCOUNT_TYPE_OPTIONS"
          :key="option.value"
          class="flex min-h-[44px] cursor-pointer items-center gap-2 rounded-md border px-3 text-sm"
          :class="
            form.accountType === option.value
              ? 'border-purple-600 bg-purple-50 text-purple-900'
              : 'border-gray-200 text-gray-700'
          "
        >
          <input
            v-model="form.accountType"
            type="radio"
            name="account-type"
            class="accent-purple-600"
            :value="option.value"
            :data-testid="`account-type-${option.value}`"
            :aria-invalid="errors.accountType ? 'true' : undefined"
            :aria-describedby="errors.accountType ? 'account-type-error' : undefined"
          />
          {{ option.label }}
        </label>
      </div>
      <p
        v-if="errors.accountType"
        id="account-type-error"
        class="text-sm text-red-600"
        data-testid="account-type-error"
      >
        {{ errors.accountType }}
      </p>
    </fieldset>

    <div class="space-y-1">
      <label for="initial-balance" class="text-sm font-medium text-gray-900">
        Saldo atual da conta (R$)
      </label>
      <Input
        id="initial-balance"
        v-model="form.initialBalance"
        inputmode="decimal"
        placeholder="0,00"
        autocomplete="off"
        class="min-h-[44px]"
        data-testid="initial-balance-input"
        :aria-invalid="errors.initialBalance ? 'true' : undefined"
        :aria-describedby="
          errors.initialBalance
            ? 'initial-balance-error initial-balance-hint'
            : 'initial-balance-hint'
        "
      />
      <p
        v-if="errors.initialBalance"
        id="initial-balance-error"
        class="text-sm text-red-600"
        data-testid="initial-balance-error"
      >
        {{ errors.initialBalance }}
      </p>
      <p id="initial-balance-hint" class="text-xs text-gray-500">
        Ex.: 1.234,56. Se a conta estiver no cheque especial, use o sinal de menos: -150,00.
      </p>
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
      :data-testid="isEdit ? 'save-account-button' : 'add-account-button'"
      :disabled="disabled || submitting"
    >
      <Plus v-if="!isEdit" class="h-4 w-4" aria-hidden="true" />
      {{ submitting ? submittingLabel : submitLabel }}
    </Button>
  </form>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Plus } from 'lucide-vue-next'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { ACCOUNT_TYPE_OPTIONS } from '@/lib/accountTypes'
import { BANKS } from '@/lib/banks'
import { formatBRLInput, parseBRL } from '@/lib/money'
import { createAccount, updateAccount } from '@/services/accountService'
import type { Account, AccountType } from '@/types/account'

/**
 * Formulário de conta bancária (Stories 3.2 e 4.1): valida localmente, chama `POST /accounts`
 * e mostra os erros 400 por campo. Outras falhas vão para o pai em `failed` (com o `detail` da
 * API, se houver), e os dados digitados ficam no formulário.
 *
 * Story 4.3: com a prop `account`, o formulário edita essa conta — começa preenchido com os
 * valores atuais (saldo no formato brasileiro), chama `PUT /accounts/{id}` com o saldo atual e
 * emite `updated`. Um 404 (conta removida ou de outra pessoa) vai para o pai em `not-found`.
 * Retro do Epic 4: o PUT envia a `version` da conta; um 409 (a conta mudou em outra tela) vai
 * para o pai em `conflict`, com o `detail` da API.
 */
const props = withDefaults(
  defineProps<{
    /** Bloqueia o envio (ex.: lista ainda carregando ou outra ação em andamento). */
    disabled?: boolean
    submitLabel?: string
    submittingLabel?: string
    /** Botão de envio em destaque (diálogo) em vez de contorno (passo 2). */
    primary?: boolean
    /** Conta a editar (Story 4.3). Sem ela, o formulário cria uma conta nova. */
    account?: Account | null
  }>(),
  {
    disabled: false,
    submitLabel: 'Adicionar conta',
    submittingLabel: 'Adicionando...',
    primary: false,
    account: null,
  },
)

const emit = defineEmits<{
  created: [account: Account]
  /** Story 4.3: conta editada com sucesso, como a API devolveu. */
  updated: [account: Account]
  /** Story 4.3: a conta editada não existe mais (404). */
  notFound: []
  /** Retro do Epic 4: a conta mudou em outra tela depois de o formulário abrir (409). */
  conflict: [detail: string | undefined]
  failed: [detail: string | undefined]
  /** Toda tentativa de envio, antes da validação (o pai limpa o erro anterior). */
  attempt: []
}>()

/** Envio em andamento; o pai pode acompanhar com `v-model:submitting`. */
const submitting = defineModel<boolean>('submitting', { default: false })

type Field = 'accountName' | 'bankName' | 'accountType' | 'initialBalance'
const FIELDS: Field[] = ['accountName', 'bankName', 'accountType', 'initialBalance']

const form = reactive({
  accountName: '',
  bankName: '',
  accountType: 'CHECKING' as AccountType,
  initialBalance: '',
})
const errors = reactive<Partial<Record<Field, string>>>({})
const isEdit = computed(() => props.account != null)
const nameInput = ref<{ $el?: HTMLInputElement } | null>(null)

interface ApiError {
  response?: { status?: number; data?: { detail?: string; errors?: Record<string, string> } }
}

function clearErrors() {
  for (const key of Object.keys(errors) as Field[]) delete errors[key]
}

/** Validação local, com as mesmas regras e mensagens do backend. */
function validate(): number | null {
  clearErrors()
  const name = form.accountName.trim()
  if (!name) errors.accountName = 'Informe o nome da conta'
  else if (name.length > 100) errors.accountName = 'Use no máximo 100 caracteres no nome da conta'

  if (form.bankName.trim().length > 100) {
    errors.bankName = 'Use no máximo 100 caracteres no nome do banco'
  }

  let balance: number | null = null
  if (!form.initialBalance.trim()) {
    errors.initialBalance = isEdit.value
      ? 'Informe o saldo atual (use 0,00 se não souber)'
      : 'Informe o saldo inicial (use 0,00 se não souber)'
  } else {
    balance = parseBRL(form.initialBalance)
    if (balance === null) {
      errors.initialBalance = 'Digite o saldo no formato 1.234,56 (até 2 casas depois da vírgula)'
    }
  }
  return Object.keys(errors).length === 0 ? balance : null
}

/** Na edição, a API chama o saldo de `currentBalance`; no formulário, o campo é um só. */
function fieldOf(key: string): Field | undefined {
  if (isEdit.value && key === 'currentBalance') return 'initialBalance'
  return FIELDS.includes(key as Field) ? (key as Field) : undefined
}

/** Preenche o formulário com os valores atuais da conta em edição. */
function fill(account: Account) {
  form.accountName = account.accountName
  form.bankName = account.bankName ?? ''
  form.accountType = account.accountType
  form.initialBalance = formatBRLInput(account.currentBalance)
  clearErrors()
}

function reset() {
  form.accountName = ''
  form.bankName = ''
  form.accountType = 'CHECKING'
  form.initialBalance = ''
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
  const balance = validate()
  if (balance === null) return

  inFlight = true
  submitting.value = true
  const editing = props.account
  const fields = {
    accountName: form.accountName.trim(),
    bankName: form.bankName.trim() || null,
    accountType: form.accountType,
  }
  try {
    if (editing) {
      const updated = await updateAccount(editing.id, {
        ...fields,
        currentBalance: balance,
        version: editing.version,
      })
      emit('updated', updated)
    } else {
      const created = await createAccount({ ...fields, initialBalance: balance })
      reset()
      emit('created', created)
    }
  } catch (err) {
    const response = (err as ApiError)?.response
    const entries = Object.entries(response?.data?.errors ?? {})
    const known = entries.flatMap(([key, message]) => {
      const field = fieldOf(key)
      return field ? [[field, message] as const] : []
    })
    if (editing && response?.status === 404) {
      emit('notFound')
    } else if (editing && response?.status === 409) {
      emit('conflict', response.data?.detail)
    } else if (response?.status === 400 && known.length > 0) {
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

watch(
  () => props.account,
  (account) => {
    if (account) fill(account)
    else reset()
  },
  { immediate: true },
)

/** Foca o primeiro campo (usado ao abrir o diálogo). */
function focus() {
  nameInput.value?.$el?.focus()
}

defineExpose({ focus })
</script>
