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

      <!-- Story 4.3: aviso (não é sucesso) quando o item editado não existe mais -->
      <p
        v-if="notice"
        role="alert"
        data-testid="accounts-notice"
        class="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900"
      >
        {{ notice }}
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
          <div class="flex flex-col gap-2 sm:flex-row">
            <!-- Story 4.5: transferir entre contas próprias (precisa de 2 ou mais contas ativas) -->
            <Button
              v-if="accounts.length >= 2"
              ref="transferButton"
              variant="outline"
              class="min-h-[44px] border-purple-600 text-purple-700"
              data-testid="open-transfer"
              @click="openTransfer"
            >
              <ArrowLeftRight class="h-4 w-4" aria-hidden="true" />
              Transferir
            </Button>
            <Button
              class="min-h-[44px] bg-purple-600 text-white hover:bg-purple-700"
              ref="headerAddButton"
              data-testid="open-add-account"
              @click="openDialog"
            >
              <Plus class="h-4 w-4" aria-hidden="true" />
              Adicionar conta
            </Button>
          </div>
        </section>

        <section aria-labelledby="accounts-list-heading" class="space-y-2">
          <h2 id="accounts-list-heading" class="text-sm font-medium text-gray-900">
            Suas contas ({{ accounts.length }})
          </h2>
          <ul ref="list" class="space-y-2" data-testid="accounts-list">
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
                <p
                  v-if="account.lastReconciliationDate"
                  class="truncate text-xs text-gray-500"
                  data-testid="account-reconciled-at"
                >
                  Reconciliada em {{ formatDateBR(account.lastReconciliationDate) }}
                </p>
              </div>
              <span
                class="text-sm font-semibold whitespace-nowrap"
                :class="account.currentBalance < 0 ? 'text-red-600' : 'text-gray-900'"
                data-testid="account-balance"
              >
                {{ formatBRL(account.currentBalance) }}
              </span>
              <!-- Story 4.7: gráfico do saldo ao longo do tempo -->
              <RouterLink
                :to="`/contas/${account.id}/historico`"
                class="inline-flex min-h-[44px] min-w-[44px] shrink-0 items-center gap-2 rounded-md px-3 text-sm font-medium text-purple-700 hover:bg-purple-50"
                :data-testid="`history-account-${account.id}`"
                :aria-label="`Histórico de ${account.accountName}`"
              >
                <History class="h-4 w-4" aria-hidden="true" />
                Histórico
              </RouterLink>
              <!-- Story 4.6: conferir o saldo com o banco -->
              <Button
                variant="ghost"
                class="min-h-[44px] min-w-[44px] shrink-0 text-purple-700 hover:bg-purple-50"
                :data-testid="`reconcile-account-${account.id}`"
                :aria-label="`Reconciliar ${account.accountName}`"
                @click="openReconcile(account)"
              >
                <Scale class="h-4 w-4" aria-hidden="true" />
                Reconciliar
              </Button>
              <Button
                variant="ghost"
                class="min-h-[44px] min-w-[44px] shrink-0 text-purple-700 hover:bg-purple-50"
                :data-testid="`edit-account-${account.id}`"
                :aria-label="`Editar ${account.accountName}`"
                @click="openEdit(account)"
              >
                <Pencil class="h-4 w-4" aria-hidden="true" />
                Editar
              </Button>
              <Button
                variant="ghost"
                class="min-h-[44px] min-w-[44px] shrink-0 text-red-700 hover:bg-red-50"
                :data-testid="`remove-account-${account.id}`"
                :aria-label="`Remover ${account.accountName}`"
                @click="openRemove(account)"
              >
                <Trash2 class="h-4 w-4" aria-hidden="true" />
                Remover
              </Button>
            </li>
          </ul>
        </section>
      </template>

      <!-- Story 4.5: transferências recentes -->
      <section
        v-if="!loading && !loadFailed"
        aria-labelledby="transfers-heading"
        class="space-y-2"
        data-testid="recent-transfers"
      >
        <h2 id="transfers-heading" class="text-sm font-medium text-gray-900">
          Transferências recentes
        </h2>
        <p
          v-if="transfersLoading"
          aria-busy="true"
          class="text-sm text-gray-600"
          data-testid="transfers-loading"
        >
          Carregando transferências...
        </p>
        <div
          v-else-if="transfersLoadFailed"
          role="alert"
          data-testid="transfers-load-error"
          class="flex flex-col gap-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3 sm:flex-row sm:items-center sm:justify-between"
        >
          <p class="text-sm text-red-800">Não foi possível carregar as transferências.</p>
          <Button
            variant="outline"
            class="min-h-[44px] shrink-0"
            data-testid="transfers-retry"
            @click="loadTransfers()"
          >
            Tentar de novo
          </Button>
        </div>
        <p
          v-else-if="transfers.length === 0"
          class="text-sm text-gray-600"
          data-testid="transfers-empty"
        >
          Nenhuma transferência registrada
        </p>
        <ul v-else class="space-y-2" data-testid="transfers-list">
          <li
            v-for="transfer in transfers"
            :key="transfer.id"
            class="flex items-center gap-3 rounded-lg border border-gray-200 bg-white p-3"
            :data-testid="`transfer-item-${transfer.id}`"
          >
            <ArrowLeftRight class="h-5 w-5 shrink-0 text-purple-600" aria-hidden="true" />
            <div class="min-w-0 flex-1">
              <p class="truncate font-medium text-gray-900" data-testid="transfer-accounts">
                {{ transfer.fromAccountName ?? 'Conta' }} → {{ transfer.toAccountName ?? 'Conta' }}
              </p>
              <p class="truncate text-xs text-gray-500">
                <span data-testid="transfer-date">{{ formatISODateBR(transfer.transferDate) }}</span
                ><template v-if="transfer.description">
                  ·
                  <span data-testid="transfer-description">{{
                    transfer.description
                  }}</span></template
                >
              </p>
            </div>
            <span
              class="text-sm font-semibold whitespace-nowrap text-gray-900"
              data-testid="transfer-amount"
            >
              {{ formatBRL(transfer.amount) }}
            </span>
          </li>
        </ul>
      </section>

      <!-- Story 4.4: contas removidas, com "Restaurar" -->
      <template v-if="!loading && !loadFailed">
        <label
          class="inline-flex min-h-[44px] cursor-pointer items-center gap-2 text-sm text-gray-700"
        >
          <input
            v-model="showRemoved"
            type="checkbox"
            class="h-5 w-5 accent-purple-600"
            data-testid="toggle-removed-accounts"
          />
          Mostrar removidas
        </label>

        <section
          v-if="showRemoved"
          aria-labelledby="removed-accounts-heading"
          class="space-y-2"
          data-testid="removed-accounts"
        >
          <h2 id="removed-accounts-heading" class="text-sm font-medium text-gray-900">Removidas</h2>
          <p
            v-if="removedLoading"
            aria-busy="true"
            class="text-sm text-gray-600"
            data-testid="removed-accounts-loading"
          >
            Carregando contas removidas...
          </p>
          <div
            v-else-if="removedLoadFailed"
            role="alert"
            data-testid="removed-accounts-load-error"
            class="flex flex-col gap-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3 sm:flex-row sm:items-center sm:justify-between"
          >
            <p class="text-sm text-red-800">Não foi possível carregar as contas removidas.</p>
            <Button
              variant="outline"
              class="min-h-[44px] shrink-0"
              data-testid="removed-accounts-retry"
              @click="loadRemoved()"
            >
              Tentar de novo
            </Button>
          </div>
          <p
            v-else-if="removed.length === 0"
            class="text-sm text-gray-600"
            data-testid="removed-accounts-empty"
          >
            Nenhuma conta removida
          </p>
          <ul v-else class="space-y-2" data-testid="removed-accounts-list">
            <li
              v-for="account in removed"
              :key="account.id"
              class="flex items-center gap-3 rounded-lg border border-dashed border-gray-300 bg-gray-50 p-3"
              :data-testid="`removed-account-item-${account.id}`"
            >
              <Landmark class="h-5 w-5 shrink-0 text-gray-400" aria-hidden="true" />
              <div class="min-w-0 flex-1">
                <p class="truncate font-medium text-gray-700">{{ account.accountName }}</p>
                <p class="truncate text-xs text-gray-500">
                  {{ ACCOUNT_TYPE_LABEL[account.accountType]
                  }}<template v-if="account.bankName"> · {{ account.bankName }}</template>
                </p>
              </div>
              <span class="text-sm whitespace-nowrap text-gray-500" data-testid="account-balance">
                {{ formatBRL(account.currentBalance) }}
              </span>
              <Button
                variant="outline"
                class="min-h-[44px] min-w-[44px] shrink-0"
                :data-testid="`restore-account-${account.id}`"
                :aria-label="`Restaurar ${account.accountName}`"
                :disabled="restoringId !== null"
                @click="restore(account)"
              >
                <RotateCcw class="h-4 w-4" aria-hidden="true" />
                {{ restoringId === account.id ? 'Restaurando...' : 'Restaurar' }}
              </Button>
            </li>
          </ul>
        </section>
      </template>
    </div>

    <Dialog :open="dialogOpen" @update:open="onDialogOpenChange">
      <DialogContent
        :show-close-button="false"
        :data-testid="editing ? 'edit-account-dialog' : 'add-account-dialog'"
        @open-auto-focus="onOpenAutoFocus"
        @close-auto-focus="onCloseAutoFocus"
      >
        <DialogHeader>
          <DialogTitle>{{ editing ? 'Editar conta' : 'Adicionar conta' }}</DialogTitle>
          <DialogDescription v-if="editing">
            Corrija o nome, o banco, o tipo ou o saldo que a conta tem hoje.
          </DialogDescription>
          <DialogDescription v-else>
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
          :account="editing"
          :submit-label="editing ? 'Salvar alterações' : 'Salvar conta'"
          submitting-label="Salvando..."
          @created="onCreated"
          @updated="onUpdated"
          @not-found="onNotFound"
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

    <!-- Story 4.4: confirmação da remoção -->
    <Dialog :open="removeDialogOpen" @update:open="onRemoveDialogOpenChange">
      <DialogContent
        :show-close-button="false"
        data-testid="remove-account-dialog"
        @close-auto-focus="onRemoveCloseAutoFocus"
      >
        <DialogHeader>
          <DialogTitle>Remover {{ removing?.accountName }}?</DialogTitle>
          <DialogDescription>
            A conta some da lista, mas o histórico é preservado. Você pode restaurá-la depois.
          </DialogDescription>
        </DialogHeader>
        <p
          v-if="removeError"
          role="alert"
          data-testid="remove-account-error"
          class="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800"
        >
          {{ removeError }}
        </p>
        <div class="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button
            variant="outline"
            class="min-h-[44px]"
            data-testid="cancel-remove-account"
            :disabled="removeSubmitting"
            @click="removeDialogOpen = false"
          >
            Cancelar
          </Button>
          <Button
            class="min-h-[44px] bg-red-600 text-white hover:bg-red-700"
            data-testid="confirm-remove-account"
            :disabled="removeSubmitting"
            @click="confirmRemove"
          >
            {{ removeSubmitting ? 'Removendo...' : 'Remover' }}
          </Button>
        </div>
      </DialogContent>
    </Dialog>

    <!-- Story 4.5: nova transferência -->
    <Dialog :open="transferDialogOpen" @update:open="onTransferDialogOpenChange">
      <DialogContent
        :show-close-button="false"
        data-testid="transfer-dialog"
        @open-auto-focus="onTransferOpenAutoFocus"
        @close-auto-focus="onTransferCloseAutoFocus"
      >
        <DialogHeader>
          <DialogTitle>Nova transferência</DialogTitle>
          <DialogDescription>
            Mova dinheiro entre suas contas. O saldo total não muda.
          </DialogDescription>
        </DialogHeader>
        <p
          v-if="transferError"
          role="alert"
          data-testid="transfer-error"
          class="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800"
        >
          {{ transferError }}
        </p>
        <TransferForm
          ref="transferForm"
          v-model:submitting="transferSubmitting"
          :accounts="accounts"
          @attempt="transferError = null"
          @created="onTransferCreated"
          @failed="onTransferFailed"
        />
        <Button
          variant="outline"
          class="min-h-[44px] w-full"
          data-testid="cancel-transfer"
          :disabled="transferSubmitting"
          @click="transferDialogOpen = false"
        >
          Cancelar
        </Button>
      </DialogContent>
    </Dialog>

    <!-- Story 4.6: reconciliar com o banco -->
    <Dialog :open="reconcileDialogOpen" @update:open="onReconcileDialogOpenChange">
      <DialogContent
        :show-close-button="false"
        data-testid="reconcile-dialog"
        @open-auto-focus="onReconcileOpenAutoFocus"
        @close-auto-focus="onReconcileCloseAutoFocus"
      >
        <DialogHeader>
          <DialogTitle>Reconciliar {{ reconciling?.accountName }}</DialogTitle>
          <DialogDescription>
            Informe o saldo que o banco mostra hoje para conferir com o do porquinho.
          </DialogDescription>
        </DialogHeader>
        <p
          v-if="reconcileError"
          role="alert"
          data-testid="reconcile-error"
          class="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800"
        >
          {{ reconcileError }}
        </p>
        <ReconcileForm
          v-if="reconciling"
          ref="reconcileForm"
          v-model:submitting="reconcileSubmitting"
          :account="reconciling"
          @attempt="reconcileError = null"
          @reconciled="onReconciled"
          @failed="onReconcileFailed"
          @dismiss="reconcileDialogOpen = false"
        />
      </DialogContent>
    </Dialog>
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import {
  ArrowLeftRight,
  History,
  Landmark,
  Pencil,
  Plus,
  RotateCcw,
  Scale,
  Trash2,
} from 'lucide-vue-next'
import AccountForm from '@/components/accounts/AccountForm.vue'
import ReconcileForm from '@/components/accounts/ReconcileForm.vue'
import TransferForm from '@/components/accounts/TransferForm.vue'
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
import {
  deleteAccount,
  listAccounts,
  listDeletedAccounts,
  restoreAccount,
} from '@/services/accountService'
import { listTransfers } from '@/services/transferService'
import type { Account, ReconciliationResult } from '@/types/account'
import type { Transfer } from '@/types/transfer'

/**
 * Story 4.1: contas ativas, saldo total e cadastro de conta nova pelo diálogo.
 * Story 4.3: o botão "Editar" de cada conta abre o mesmo diálogo com o formulário preenchido.
 * Story 4.4: "Remover" com confirmação; "Mostrar removidas" lista as removidas com "Restaurar".
 * Story 4.5: "Transferir" (com 2 ou mais contas) abre o diálogo de transferência; a seção "Transferências
 * recentes" lista as transferências registradas.
 * Story 4.6: "Reconciliar" de cada conta abre o diálogo de reconciliação com o banco; a conta mostra a data da última.
 */
const accounts = ref<Account[]>([])
const loading = ref(true)
const loadFailed = ref(false)
const dialogOpen = ref(false)
const saving = ref(false)
const dialogError = ref<string | null>(null)
const successMessage = ref<string | null>(null)
/** Story 4.3: aviso de item que não existe mais (404 ao editar); some na próxima ação. */
const notice = ref<string | null>(null)
const accountForm = ref<InstanceType<typeof AccountForm> | null>(null)
const heading = ref<HTMLElement | null>(null)
const list = ref<HTMLElement | null>(null)
/** Story 4.3: item em edição (null no diálogo de adicionar). Continua definido depois de fechar, para o foco voltar ao botão dele. */
const editing = ref<Account | null>(null)
const headerAddButton = ref<{ $el?: HTMLElement } | null>(null)

interface ApiError {
  response?: { status?: number; data?: { detail?: string } }
}

/** Story 4.4: conta em remoção. Continua definida depois de fechar, para o foco voltar ao botão dela. */
const removing = ref<Account | null>(null)
const removeDialogOpen = ref(false)
const removeSubmitting = ref(false)
const removeError = ref<string | null>(null)
/** Story 4.4: seção de removidas (carregada ao marcar o seletor). */
const showRemoved = ref(false)
const removed = ref<Account[]>([])
const removedLoading = ref(false)
const removedLoadFailed = ref(false)
const restoringId = ref<string | null>(null)
/** Story 4.5: diálogo e seção de transferências. */
const transferDialogOpen = ref(false)
const transferSubmitting = ref(false)
const transferError = ref<string | null>(null)
const transferForm = ref<InstanceType<typeof TransferForm> | null>(null)
const transferButton = ref<{ $el?: HTMLElement } | null>(null)
const transfers = ref<Transfer[]>([])
const transfersLoading = ref(false)
const transfersLoadFailed = ref(false)
/** Story 4.6: conta em reconciliação. Continua definida depois de fechar, para o foco voltar ao botão dela. */
const reconciling = ref<Account | null>(null)
const reconcileDialogOpen = ref(false)
const reconcileSubmitting = ref(false)
const reconcileError = ref<string | null>(null)
const reconcileForm = ref<InstanceType<typeof ReconcileForm> | null>(null)

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
  notice.value = null
  loading.value = true
  loadFailed.value = false
  try {
    accounts.value = await listAccounts()
  } catch {
    loadFailed.value = true
  } finally {
    loading.value = false
  }
  if (showRemoved.value && !loadFailed.value) void loadRemoved()
  if (!loadFailed.value) void loadTransfers()
}

/** AAAA-MM-DD → dd/mm/aaaa (data de calendário, sem fuso). */
function formatISODateBR(iso: string): string {
  const [year, month, day] = iso.split('-')
  return year && month && day ? `${day}/${month}/${year}` : iso
}

const DATE_BR = new Intl.DateTimeFormat('pt-BR', {
  timeZone: 'America/Sao_Paulo',
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
})

/** Instante ISO → dd/mm/aaaa no fuso de São Paulo. */
function formatDateBR(iso: string): string {
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? iso : DATE_BR.format(date)
}

/** Sequência das cargas de transferências: só a resposta da carga mais recente é aplicada. */
let transfersRequest = 0

/** Story 4.5: carrega a seção. `quiet` recarrega sem trocar a lista pelo "Carregando...". */
async function loadTransfers(quiet = false) {
  const request = ++transfersRequest
  if (!quiet) transfersLoading.value = true
  transfersLoadFailed.value = false
  try {
    const result = await listTransfers()
    if (request === transfersRequest) transfers.value = result
  } catch {
    if (request === transfersRequest) transfersLoadFailed.value = true
  } finally {
    if (request === transfersRequest) transfersLoading.value = false
  }
}

/** Story 4.5: abre o diálogo "Nova transferência". */
function openTransfer() {
  transferError.value = null
  setSuccess(null)
  notice.value = null
  transferDialogOpen.value = true
}

function onTransferDialogOpenChange(open: boolean) {
  // Não fecha no meio do envio (Esc ou clique fora)
  if (!open && transferSubmitting.value) return
  transferDialogOpen.value = open
}

function onTransferOpenAutoFocus(event: Event) {
  event.preventDefault()
  if (transferForm.value) transferForm.value.focus()
  else void nextTick(() => transferForm.value?.focus())
}

/** Ao fechar, o foco volta ao "Transferir" (se ainda visível) ou ao botão do topo/título. */
function onTransferCloseAutoFocus(event: Event) {
  event.preventDefault()
  void nextTick(() => {
    ;(transferButton.value?.$el ?? headerAddButton.value?.$el ?? heading.value)?.focus()
  })
}

/**
 * Story 4.5: recarrega as contas do servidor (saldos depois de uma transferência, ou uma conta que sumiu). Uma
 * falha mantém a lista atual e avisa, sem esconder a página.
 */
async function refreshAccounts() {
  try {
    accounts.value = await listAccounts()
  } catch {
    notice.value = 'Não foi possível atualizar os saldos. Recarregue a página.'
  }
}

/** Story 4.5: fecha o diálogo e recarrega os saldos das contas e a seção (o total não muda). */
function onTransferCreated() {
  transferDialogOpen.value = false
  setSuccess('Transferência registrada')
  void refreshAccounts()
  void loadTransfers(true)
}

/**
 * 404 "Conta não encontrada" e outras falhas ficam no diálogo, que continua aberto. No 404 as contas são
 * recarregadas, para a conta removida sair das opções.
 */
function onTransferFailed(detail: string | undefined, status?: number) {
  transferError.value = detail ?? 'Não foi possível registrar a transferência. Tente novamente.'
  if (status === 404) void refreshAccounts()
}

/** Story 4.6: abre o diálogo "Reconciliar <nome>". */
function openReconcile(account: Account) {
  reconciling.value = account
  reconcileError.value = null
  setSuccess(null)
  notice.value = null
  reconcileDialogOpen.value = true
}

function onReconcileDialogOpenChange(open: boolean) {
  // Não fecha no meio do envio (Esc ou clique fora)
  if (!open && reconcileSubmitting.value) return
  reconcileDialogOpen.value = open
}

function onReconcileOpenAutoFocus(event: Event) {
  event.preventDefault()
  if (reconcileForm.value) reconcileForm.value.focus()
  else void nextTick(() => reconcileForm.value?.focus())
}

/** Ao fechar, o foco volta ao "Reconciliar" da conta (se ela continua na lista) ou ao botão do topo/título. */
function onReconcileCloseAutoFocus(event: Event) {
  event.preventDefault()
  void nextTick(() => {
    const reconcileButton = reconciling.value
      ? list.value?.querySelector<HTMLElement>(
          `[data-testid="reconcile-account-${reconciling.value.id}"]`,
        )
      : null
    ;(reconcileButton ?? headerAddButton.value?.$el ?? heading.value)?.focus()
  })
}

/**
 * Story 4.6: aplica a conta devolvida pelo servidor (saldo e data novos), fecha o diálogo e recarrega as contas do
 * servidor; o total é recalculado pelo computed.
 */
function onReconciled(result: ReconciliationResult) {
  const index = accounts.value.findIndex((x) => x.id === result.account.id)
  if (index >= 0) accounts.value.splice(index, 1, result.account)
  reconcileDialogOpen.value = false
  setSuccess('Saldo reconciliado')
  void refreshAccounts()
}

/**
 * 404: a conta foi removida ou não é do usuário; o diálogo fecha, aparece o aviso e as contas são recarregadas.
 * Outras falhas ficam no diálogo, que continua aberto.
 */
function onReconcileFailed(detail: string | undefined, status?: number) {
  if (status === 404) {
    const id = reconciling.value?.id
    accounts.value = accounts.value.filter((x) => x.id !== id)
    reconcileDialogOpen.value = false
    notice.value = 'Esta conta não existe mais'
    void refreshAccounts()
    return
  }
  reconcileError.value = detail ?? 'Não foi possível reconciliar a conta. Tente novamente.'
}

function openDialog() {
  editing.value = null
  dialogError.value = null
  setSuccess(null)
  notice.value = null
  dialogOpen.value = true
}

/** Story 4.3: abre o diálogo em modo edição, com o formulário preenchido. */
function openEdit(account: Account) {
  editing.value = account
  dialogError.value = null
  setSuccess(null)
  notice.value = null
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
  void nextTick(() => {
    // Na edição, o foco volta para o botão "Editar" do item (se ele ainda estiver na lista)
    const editButton = editing.value
      ? list.value?.querySelector<HTMLElement>(`[data-testid="edit-account-${editing.value.id}"]`)
      : null
    ;(editButton ?? headerAddButton.value?.$el ?? heading.value)?.focus()
  })
}

function onCreated(created: Account) {
  accounts.value.push(created)
  dialogOpen.value = false
  setSuccess('Conta adicionada com sucesso')
}

/** Story 4.3: o item editado é substituído na lista (o total é recalculado pelo computed). */
function onUpdated(updated: Account) {
  const index = accounts.value.findIndex((x) => x.id === updated.id)
  if (index >= 0) accounts.value.splice(index, 1, updated)
  dialogOpen.value = false
  setSuccess('Informações atualizadas')
  // Story 4.5: os nomes das contas nas transferências acompanham a edição
  void loadTransfers(true)
}

/** Story 4.3: 404 ao salvar — o item foi removido ou não é do usuário; sai da lista. */
function onNotFound() {
  const id = editing.value?.id
  accounts.value = accounts.value.filter((x) => x.id !== id)
  dialogOpen.value = false
  setSuccess(null)
  notice.value = 'Esta conta não existe mais'
}

function onFailed(detail: string | undefined) {
  dialogError.value = detail ?? 'Não foi possível salvar a conta. Tente novamente.'
}

/** Story 4.4: abre a confirmação da remoção. */
function openRemove(account: Account) {
  removing.value = account
  removeError.value = null
  setSuccess(null)
  notice.value = null
  removeDialogOpen.value = true
}

function onRemoveDialogOpenChange(open: boolean) {
  // Não fecha no meio do envio (Esc ou clique fora)
  if (!open && removeSubmitting.value) return
  removeDialogOpen.value = open
}

/** Ao fechar, o foco volta ao "Remover" da conta (se ela continua na lista) ou ao botão do topo/título. */
function onRemoveCloseAutoFocus(event: Event) {
  event.preventDefault()
  void nextTick(() => {
    const removeButton = removing.value
      ? list.value?.querySelector<HTMLElement>(
          `[data-testid="remove-account-${removing.value.id}"]`,
        )
      : null
    ;(removeButton ?? headerAddButton.value?.$el ?? heading.value)?.focus()
  })
}

async function confirmRemove() {
  const target = removing.value
  if (!target || removeSubmitting.value) return
  removeSubmitting.value = true
  removeError.value = null
  try {
    await deleteAccount(target.id)
    accounts.value = accounts.value.filter((x) => x.id !== target.id)
    // A seção de removidas é recarregada para seguir a ordem do servidor
    if (showRemoved.value) void loadRemoved(true)
    removeDialogOpen.value = false
    setSuccess('Conta removida')
    void loadTransfers(true)
  } catch (err) {
    const response = (err as ApiError)?.response
    if (response?.status === 404) {
      accounts.value = accounts.value.filter((x) => x.id !== target.id)
      removeDialogOpen.value = false
      notice.value = 'Esta conta já foi removida ou não existe mais'
      void refreshAfterNotFound()
    } else {
      // 409 "Mantenha ao menos uma conta ativa" e outras falhas ficam no diálogo
      removeError.value =
        response?.data?.detail ?? 'Não foi possível remover a conta. Tente novamente.'
    }
  } finally {
    removeSubmitting.value = false
  }
}

/** Sequência das cargas: só a resposta da carga mais recente é aplicada. */
let removedRequest = 0

/** Story 4.4: carrega a seção de removidas. `quiet` recarrega sem trocar a lista pelo "Carregando...". */
async function loadRemoved(quiet = false) {
  const request = ++removedRequest
  if (!quiet) removedLoading.value = true
  removedLoadFailed.value = false
  try {
    const result = await listDeletedAccounts()
    if (request === removedRequest) removed.value = result
  } catch {
    if (request === removedRequest) removedLoadFailed.value = true
  } finally {
    if (request === removedRequest) removedLoading.value = false
  }
}

/**
 * Story 4.4: 404 ao remover ou restaurar — o item mudou em outra aba ou sessão. Recarrega as listas ativa e de
 * removidas sem esconder a página; uma falha aqui mantém o que já está na tela.
 */
async function refreshAfterNotFound() {
  if (showRemoved.value) void loadRemoved(true)
  try {
    accounts.value = await listAccounts()
  } catch {
    // Mantém a lista atual; o aviso já foi mostrado
  }
}

watch(showRemoved, (show) => {
  if (show) void loadRemoved()
})

/** Volta para a lista ativa na posição da criação (a API ordena da mais antiga para a mais nova). */
function insertByCreation(account: Account) {
  const created = Date.parse(account.createdAt)
  const index = accounts.value.findIndex((x) => Date.parse(x.createdAt) > created)
  if (index < 0) accounts.value.push(account)
  else accounts.value.splice(index, 0, account)
}

/** Story 4.4: restaura a conta; ela sai da seção de removidas e volta à lista e ao total. */
async function restore(account: Account) {
  if (restoringId.value) return
  restoringId.value = account.id
  setSuccess(null)
  notice.value = null
  try {
    const restored = await restoreAccount(account.id)
    removed.value = removed.value.filter((x) => x.id !== account.id)
    insertByCreation(restored)
    setSuccess('Conta restaurada')
    void loadTransfers(true)
    // O botão "Restaurar" some: o foco vai para o "Editar" da conta restaurada
    void nextTick(() =>
      list.value
        ?.querySelector<HTMLElement>(`[data-testid="edit-account-${restored.id}"]`)
        ?.focus(),
    )
  } catch (err) {
    if ((err as ApiError)?.response?.status === 404) {
      removed.value = removed.value.filter((x) => x.id !== account.id)
      notice.value = 'Esta conta já foi restaurada ou não existe mais'
      void refreshAfterNotFound()
    } else {
      notice.value = 'Não foi possível restaurar a conta. Tente novamente.'
    }
  } finally {
    restoringId.value = null
  }
}

onMounted(load)
onBeforeUnmount(() => clearTimeout(successTimer))
</script>
