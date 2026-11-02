<template>
  <div class="flex flex-col gap-8">
    <section class="space-y-6" data-testid="accounts-step">
      <div class="space-y-3">
        <h1 ref="heading" tabindex="-1" class="text-2xl font-bold text-gray-900 outline-none">
          Adicione suas contas bancárias
        </h1>
        <p class="text-gray-600">
          Cadastre as contas onde seu dinheiro está hoje, como conta corrente e poupança. Você pode
          adicionar uma de cada vez.
        </p>
      </div>

      <form
        class="space-y-4 rounded-lg border border-gray-200 p-4"
        novalidate
        data-testid="account-form"
        @submit.prevent="addAccount"
      >
        <div class="space-y-1">
          <label for="account-name" class="text-sm font-medium text-gray-900">Nome da conta</label>
          <Input
            id="account-name"
            v-model="form.accountName"
            maxlength="100"
            placeholder="Ex.: Nubank"
            autocomplete="off"
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
              v-for="option in TYPE_OPTIONS"
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
              />
              {{ option.label }}
            </label>
          </div>
          <p v-if="errors.accountType" class="text-sm text-red-600">{{ errors.accountType }}</p>
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
          variant="outline"
          class="min-h-[44px] w-full border-purple-600 text-purple-700"
          data-testid="add-account-button"
          :disabled="busy || loadingList"
        >
          <Plus class="h-4 w-4" aria-hidden="true" />
          {{ adding ? 'Adicionando...' : 'Adicionar conta' }}
        </Button>
      </form>

      <div v-if="loadingList" class="space-y-2" aria-busy="true" data-testid="accounts-skeleton">
        <div class="h-14 animate-pulse rounded-lg bg-gray-100" />
        <span class="sr-only">Carregando suas contas...</span>
      </div>
      <div v-else class="space-y-2">
        <h2 class="text-sm font-medium text-gray-900">Suas contas ({{ accounts.length }})</h2>
        <p v-if="accounts.length === 0" class="text-sm text-gray-500" data-testid="accounts-empty">
          Nenhuma conta adicionada ainda.
        </p>
        <ul v-else class="space-y-2" data-testid="accounts-list" aria-live="polite">
          <li
            v-for="account in accounts"
            :key="account.id"
            class="flex items-center gap-3 rounded-lg border border-gray-200 p-3"
            :data-testid="`account-item-${account.id}`"
          >
            <Landmark class="h-5 w-5 shrink-0 text-purple-600" aria-hidden="true" />
            <div class="min-w-0 flex-1">
              <p class="truncate font-medium text-gray-900">{{ account.accountName }}</p>
              <p class="truncate text-xs text-gray-500">
                {{ TYPE_LABEL[account.accountType]
                }}<template v-if="account.bankName"> · {{ account.bankName }}</template>
              </p>
            </div>
            <span
              class="text-sm font-semibold whitespace-nowrap"
              :class="account.currentBalance < 0 ? 'text-red-600' : 'text-gray-900'"
              data-testid="account-balance"
            >
              {{ formatBRL(account.currentBalance) }}
            </span>
            <Button
              variant="ghost"
              class="min-h-[44px] text-gray-600"
              :data-testid="`remove-account-${account.id}`"
              :aria-label="`Remover a conta ${account.accountName}`"
              :disabled="busy"
              @click="removeAccount(account)"
            >
              <Trash2 class="h-4 w-4" aria-hidden="true" />
              Remover
            </Button>
          </li>
        </ul>
      </div>
    </section>

    <nav class="flex flex-col gap-3" aria-label="Navegação do passo de contas">
      <div class="flex gap-3">
        <Button
          variant="outline"
          class="min-h-[44px] flex-1"
          data-testid="back-button"
          :disabled="busy"
          @click="emit('back')"
        >
          <ChevronLeft class="h-4 w-4" aria-hidden="true" />
          Voltar
        </Button>
        <Button
          class="min-h-[44px] flex-1 bg-purple-600 text-white hover:bg-purple-700"
          data-testid="next-button"
          :disabled="busy || (accounts.length === 0 && !listFailed)"
          @click="finish(false)"
        >
          {{ finishing === 'next' ? 'Salvando...' : 'Próximo' }}
          <ChevronRight class="h-4 w-4" aria-hidden="true" />
        </Button>
      </div>
      <p v-if="!loadingList && accounts.length === 0" class="text-center text-xs text-gray-500">
        Adicione ao menos uma conta para continuar. Se pular, criamos a "Minha Conta Principal" com
        saldo zero para você.
      </p>
      <Button
        variant="ghost"
        class="min-h-[44px] text-gray-600"
        data-testid="skip-button"
        :disabled="busy"
        @click="finish(true)"
      >
        {{ finishing === 'skip' ? 'Salvando...' : 'Pular por agora' }}
      </Button>
    </nav>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ChevronLeft, ChevronRight, Landmark, Plus, Trash2 } from 'lucide-vue-next'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { BANKS } from '@/lib/banks'
import { formatBRL, parseBRL } from '@/lib/money'
import { createAccount, deleteAccount, listAccounts } from '@/services/accountService'
import { completeAccounts } from '@/services/onboardingService'
import type { Account, AccountType } from '@/types/account'
import type { OnboardingProgress } from '@/types/onboarding'

/** Story 3.2: passo 2 do onboarding. Adiciona contas uma a uma, lista e remove; Próximo, Voltar e Pular. */
const emit = defineEmits<{
  back: []
  done: [progress: OnboardingProgress]
  error: [message: string]
}>()

const TYPE_LABEL: Record<AccountType, string> = {
  CHECKING: 'Conta corrente',
  SAVINGS: 'Poupança',
}

const TYPE_OPTIONS = (Object.keys(TYPE_LABEL) as AccountType[]).map((value) => ({
  value,
  label: TYPE_LABEL[value],
}))

type Field = 'accountName' | 'bankName' | 'accountType' | 'initialBalance'
const FIELDS: Field[] = ['accountName', 'bankName', 'accountType', 'initialBalance']

const form = reactive({
  accountName: '',
  bankName: '',
  accountType: 'CHECKING' as AccountType,
  initialBalance: '',
})
const errors = reactive<Partial<Record<Field, string>>>({})
const accounts = ref<Account[]>([])
const loadingList = ref(true)
/** A lista não carregou: o usuário pode já ter contas, então Próximo fica liberado e o backend decide (409). */
const listFailed = ref(false)
const adding = ref(false)
const removing = ref(false)
const finishing = ref<'next' | 'skip' | null>(null)
const heading = ref<HTMLElement | null>(null)

const busy = computed(() => adding.value || removing.value || finishing.value !== null)

interface ApiError {
  response?: { status?: number; data?: { detail?: string; errors?: Record<string, string> } }
}

function apiDetail(err: unknown): string | undefined {
  return (err as ApiError)?.response?.data?.detail
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
    errors.initialBalance = 'Informe o saldo inicial (use 0,00 se não souber)'
  } else {
    balance = parseBRL(form.initialBalance)
    if (balance === null) {
      errors.initialBalance = 'Digite o saldo no formato 1.234,56 (até 2 casas depois da vírgula)'
    }
  }
  return Object.keys(errors).length === 0 ? balance : null
}

async function addAccount() {
  // Durante o carregamento inicial a resposta da lista sobrescreveria a conta recém-adicionada
  if (busy.value || loadingList.value) return
  const balance = validate()
  if (balance === null) return

  adding.value = true
  try {
    const created = await createAccount({
      accountName: form.accountName.trim(),
      bankName: form.bankName.trim() || null,
      accountType: form.accountType,
      initialBalance: balance,
    })
    accounts.value.push(created)
    form.accountName = ''
    form.bankName = ''
    form.accountType = 'CHECKING'
    form.initialBalance = ''
  } catch (err) {
    const fieldErrors = (err as ApiError)?.response?.data?.errors ?? {}
    const known = Object.entries(fieldErrors).filter(([key]) => FIELDS.includes(key as Field))
    if ((err as ApiError)?.response?.status === 400 && known.length > 0) {
      Object.assign(errors, Object.fromEntries(known))
    } else {
      emit(
        'error',
        apiDetail(err) ??
          'Não foi possível adicionar a conta. Verifique sua conexão e tente novamente.',
      )
    }
  } finally {
    adding.value = false
  }
}

async function removeAccount(account: Account) {
  if (busy.value) return
  removing.value = true
  try {
    await deleteAccount(account.id)
    accounts.value = accounts.value.filter((a) => a.id !== account.id)
  } catch (err) {
    if ((err as ApiError)?.response?.status === 404) {
      // Já tinha sido removida (outra aba): só tira da lista
      accounts.value = accounts.value.filter((a) => a.id !== account.id)
    } else {
      emit('error', 'Não foi possível remover a conta. Verifique sua conexão e tente novamente.')
    }
  } finally {
    removing.value = false
  }
}

async function finish(skipped: boolean) {
  // Duplo clique no mesmo tick: o botão ainda não foi desabilitado
  if (busy.value) return
  finishing.value = skipped ? 'skip' : 'next'
  let progress: OnboardingProgress
  try {
    progress = await completeAccounts(skipped)
  } catch (err) {
    // Só libera os botões no erro: no sucesso a tela troca, e liberar antes permitiria outro POST
    finishing.value = null
    emit(
      'error',
      apiDetail(err) ??
        'Não foi possível salvar seu progresso. Verifique sua conexão e tente novamente.',
    )
    return
  }
  emit('done', progress)
}

onMounted(async () => {
  heading.value?.focus()
  try {
    accounts.value = await listAccounts()
  } catch {
    listFailed.value = true
    emit('error', 'Não foi possível carregar suas contas. Você ainda pode adicionar novas.')
  } finally {
    loadingList.value = false
  }
})
</script>
