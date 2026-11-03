<template>
  <main class="min-h-screen bg-gray-50 px-4 py-8">
    <div class="mx-auto max-w-3xl space-y-6">
      <div class="flex items-start justify-between gap-4">
        <div>
          <h1 ref="heading" tabindex="-1" class="text-2xl font-semibold text-gray-900 outline-none">
            Contas bancárias
          </h1>
          <p class="mt-1 text-sm text-gray-600">Onde seu dinheiro está hoje</p>
        </div>
        <RouterLink
          to="/dashboard"
          data-testid="back-to-dashboard"
          class="inline-flex min-h-[44px] items-center rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100"
        >
          Voltar ao dashboard
        </RouterLink>
      </div>

      <!-- Sempre no DOM: leitores de tela só anunciam mudanças numa região live que já existe -->
      <p
        role="status"
        data-testid="accounts-success"
        :class="
          successMessage
            ? 'rounded-lg border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-800'
            : 'sr-only'
        "
      >
        {{ successMessage }}
      </p>

      <div v-if="loading" class="space-y-2" aria-busy="true" data-testid="accounts-skeleton">
        <div class="h-20 animate-pulse rounded-lg bg-gray-100" />
        <div class="h-16 animate-pulse rounded-lg bg-gray-100" />
        <span class="sr-only">Carregando suas contas...</span>
      </div>

      <div
        v-else-if="loadFailed"
        role="alert"
        data-testid="accounts-load-error"
        class="flex flex-col gap-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3 sm:flex-row sm:items-center sm:justify-between"
      >
        <p class="text-sm text-red-800">
          Não foi possível carregar suas contas. Verifique sua conexão e tente de novo.
        </p>
        <Button
          variant="outline"
          class="min-h-[44px] shrink-0"
          data-testid="accounts-retry"
          @click="load"
        >
          Tentar de novo
        </Button>
      </div>

      <div
        v-else-if="accounts.length === 0"
        class="space-y-4 rounded-lg border border-gray-200 bg-white p-6 text-center"
        data-testid="accounts-empty"
      >
        <p class="text-gray-600">Você ainda não tem contas cadastradas</p>
        <Button
          class="min-h-[44px] bg-purple-600 text-white hover:bg-purple-700"
          data-testid="open-add-account"
          @click="openDialog"
        >
          <Plus class="h-4 w-4" aria-hidden="true" />
          Adicionar conta
        </Button>
      </div>

      <template v-else>
        <section
          aria-labelledby="total-balance-label"
          class="flex flex-col gap-3 rounded-lg border border-gray-200 bg-white p-4 sm:flex-row sm:items-center sm:justify-between"
        >
          <div>
            <p id="total-balance-label" class="text-sm text-gray-600">Saldo total</p>
            <p
              class="text-2xl font-semibold"
              :class="totalCents < 0 ? 'text-red-600' : 'text-gray-900'"
              data-testid="accounts-total"
            >
              {{ formatBRL(totalCents / 100) }}
            </p>
          </div>
          <Button
            class="min-h-[44px] bg-purple-600 text-white hover:bg-purple-700"
            ref="headerAddButton"
            data-testid="open-add-account"
            @click="openDialog"
          >
            <Plus class="h-4 w-4" aria-hidden="true" />
            Adicionar conta
          </Button>
        </section>

        <section aria-labelledby="accounts-list-heading" class="space-y-2">
          <h2 id="accounts-list-heading" class="text-sm font-medium text-gray-900">
            Suas contas ({{ accounts.length }})
          </h2>
          <ul class="space-y-2" data-testid="accounts-list">
            <li
              v-for="account in accounts"
              :key="account.id"
              class="flex items-center gap-3 rounded-lg border border-gray-200 bg-white p-3"
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
            </li>
          </ul>
        </section>
      </template>
    </div>

    <Dialog :open="dialogOpen" @update:open="onDialogOpenChange">
      <DialogContent
        :show-close-button="false"
        data-testid="add-account-dialog"
        @open-auto-focus="onOpenAutoFocus"
        @close-auto-focus="onCloseAutoFocus"
      >
        <DialogHeader>
          <DialogTitle>Adicionar conta</DialogTitle>
          <DialogDescription>
            Informe o nome, o banco, o tipo e o saldo que a conta tem hoje.
          </DialogDescription>
        </DialogHeader>
        <p
          v-if="dialogError"
          role="alert"
          data-testid="add-account-error"
          class="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800"
        >
          {{ dialogError }}
        </p>
        <AccountForm
          ref="accountForm"
          v-model:submitting="saving"
          @attempt="dialogError = null"
          primary
          submit-label="Salvar conta"
          submitting-label="Salvando..."
          @created="onCreated"
          @failed="onFailed"
        />
        <Button
          variant="outline"
          class="min-h-[44px] w-full"
          data-testid="cancel-add-account"
          :disabled="saving"
          @click="dialogOpen = false"
        >
          Cancelar
        </Button>
      </DialogContent>
    </Dialog>
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { Landmark, Plus } from 'lucide-vue-next'
import AccountForm from '@/components/accounts/AccountForm.vue'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { ACCOUNT_TYPE_LABEL } from '@/lib/accountTypes'
import { formatBRL } from '@/lib/money'
import { listAccounts } from '@/services/accountService'
import type { Account } from '@/types/account'

/** Story 4.1: contas ativas, saldo total e cadastro de conta nova pelo diálogo. */
const accounts = ref<Account[]>([])
const loading = ref(true)
const loadFailed = ref(false)
const dialogOpen = ref(false)
const saving = ref(false)
const dialogError = ref<string | null>(null)
const successMessage = ref<string | null>(null)
const accountForm = ref<InstanceType<typeof AccountForm> | null>(null)
const heading = ref<HTMLElement | null>(null)
const headerAddButton = ref<{ $el?: HTMLElement } | null>(null)

/** A mensagem de sucesso some sozinha depois de alguns segundos. */
const SUCCESS_TIMEOUT_MS = 5000
let successTimer: ReturnType<typeof setTimeout> | undefined

function setSuccess(message: string | null) {
  clearTimeout(successTimer)
  successMessage.value = message
  if (message) {
    successTimer = setTimeout(() => (successMessage.value = null), SUCCESS_TIMEOUT_MS)
  }
}

/** Soma em centavos inteiros: 0,10 + 0,20 dá exatamente 0,30. */
const totalCents = computed(() =>
  accounts.value.reduce((sum, account) => sum + Math.round(account.currentBalance * 100), 0),
)

async function load() {
  setSuccess(null)
  loading.value = true
  loadFailed.value = false
  try {
    accounts.value = await listAccounts()
  } catch {
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

function openDialog() {
  dialogError.value = null
  setSuccess(null)
  dialogOpen.value = true
}

function onDialogOpenChange(open: boolean) {
  // Não fecha no meio do envio (Esc ou clique fora)
  if (!open && saving.value) return
  dialogOpen.value = open
}

function onOpenAutoFocus(event: Event) {
  event.preventDefault()
  if (accountForm.value) accountForm.value.focus()
  else void nextTick(() => accountForm.value?.focus())
}

/** Ao fechar, o foco vai para um alvo que sempre existe: o botão do topo (há contas) ou o título. */
function onCloseAutoFocus(event: Event) {
  event.preventDefault()
  void nextTick(() => (headerAddButton.value?.$el ?? heading.value)?.focus())
}

function onCreated(created: Account) {
  accounts.value.push(created)
  dialogOpen.value = false
  setSuccess('Conta adicionada com sucesso')
}

function onFailed(detail: string | undefined) {
  dialogError.value = detail ?? 'Não foi possível salvar a conta. Tente novamente.'
}

onMounted(load)
onBeforeUnmount(() => clearTimeout(successTimer))
</script>
