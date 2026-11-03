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

      <!-- Bloqueado enquanto a lista carrega: a resposta tardia da lista sobrescreveria a conta recém-adicionada -->
      <AccountForm
        v-model:submitting="adding"
        class="rounded-lg border border-gray-200 p-4"
        :disabled="removing || finishing !== null || loadingList"
        @created="onCreated"
        @failed="onFailed"
      />

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
                {{ ACCOUNT_TYPE_LABEL[account.accountType]
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
import { computed, onMounted, ref } from 'vue'
import { ChevronLeft, ChevronRight, Landmark, Trash2 } from 'lucide-vue-next'
import AccountForm from '@/components/accounts/AccountForm.vue'
import { Button } from '@/components/ui/button'
import { ACCOUNT_TYPE_LABEL } from '@/lib/accountTypes'
import { formatBRL } from '@/lib/money'
import { deleteAccount, listAccounts } from '@/services/accountService'
import { completeAccounts } from '@/services/onboardingService'
import type { Account } from '@/types/account'
import type { OnboardingProgress } from '@/types/onboarding'

/** Story 3.2: passo 2 do onboarding. Adiciona contas uma a uma, lista e remove; Próximo, Voltar e Pular. */
const emit = defineEmits<{
  back: []
  done: [progress: OnboardingProgress]
  error: [message: string]
}>()

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

function onCreated(created: Account) {
  accounts.value.push(created)
}

function onFailed(detail: string | undefined) {
  emit(
    'error',
    detail ?? 'Não foi possível adicionar a conta. Verifique sua conexão e tente novamente.',
  )
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
