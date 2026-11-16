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

      <!-- Story 4.3: aviso (não é sucesso) quando o item editado não existe mais ou mudou em outra tela -->
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
                <span data-testid="transfer-date">{{ formatLocalDateBR(transfer.transferDate) }}</span
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
          @conflict="onConflict"
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
import { nextTick, onMounted, ref, watch } from 'vue'
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
import { useAccountList } from '@/composables/useAccountList'
import { useFeedback } from '@/composables/useFeedback'
import { useFormDialog } from '@/composables/useFormDialog'
import { useSequencedList } from '@/composables/useSequencedList'
import { ACCOUNT_TYPE_LABEL } from '@/lib/accountTypes'
import { formatDateBR, formatLocalDateBR } from '@/lib/date'
import { formatBRL } from '@/lib/money'
import { deleteAccount, listDeletedAccounts, restoreAccount } from '@/services/accountService'
import { listTransfers } from '@/services/transferService'
import type { Account, ReconciliationResult } from '@/types/account'
import { apiErrorStatus } from '@/lib/apiError'
import type { ApiError } from '@/lib/apiError'

/**
 * Story 4.1: contas ativas, saldo total e cadastro de conta nova pelo diálogo.
 * Story 4.3: o botão "Editar" de cada conta abre o mesmo diálogo com o formulário preenchido.
 * Story 4.4: "Remover" com confirmação; "Mostrar removidas" lista as removidas com "Restaurar".
 * Story 4.5: "Transferir" (com 2 ou mais contas) abre o diálogo de transferência; a seção "Transferências
 * recentes" lista as transferências registradas.
 * Story 4.6: "Reconciliar" de cada conta abre o diálogo de reconciliação com o banco; a conta mostra a data da última.
 * Retro do Epic 4: as cargas de contas têm guarda de sequência, o 404 de qualquer ação passa por `onAccountNotFound`
 * e o 409 da edição (a conta mudou em outra tela) fecha o diálogo, avisa e recarrega as contas. Item 19: listas,
 * avisos e a máquina de diálogo vêm de composables (`useAccountList`, `useSequencedList`, `useFeedback`,
 * `useFormDialog`); aqui ficam só os fluxos.
 */
const loading = ref(true)
const loadFailed = ref(false)
const accountForm = ref<InstanceType<typeof AccountForm> | null>(null)
const transferForm = ref<InstanceType<typeof TransferForm> | null>(null)
const reconcileForm = ref<InstanceType<typeof ReconcileForm> | null>(null)
const heading = ref<HTMLElement | null>(null)
const list = ref<HTMLElement | null>(null)
const headerAddButton = ref<{ $el?: HTMLElement } | null>(null)
const transferButton = ref<{ $el?: HTMLElement } | null>(null)
/** Conta do diálogo (null no de adicionar). Continua definida depois de fechar, para o foco voltar ao botão dela. */
const editing = ref<Account | null>(null)
const removing = ref<Account | null>(null)
const reconciling = ref<Account | null>(null)
const restoringId = ref<string | null>(null)
/** Story 4.4: seção de removidas (carregada ao marcar o seletor). */
const showRemoved = ref(false)

const { accounts, totalCents, fetchAccounts, discardPendingAccountLoads, insertByCreation } = useAccountList()
const { successMessage, notice, setSuccess, clearFeedback } = useFeedback()
const {
  items: removed,
  loading: removedLoading,
  loadFailed: removedLoadFailed,
  load: loadRemoved,
  discard: discardPendingRemovedLoad,
} = useSequencedList(listDeletedAccounts)
const {
  items: transfers,
  loading: transfersLoading,
  loadFailed: transfersLoadFailed,
  load: loadTransfers,
} = useSequencedList(listTransfers)

/** Alvo que sempre existe: o botão do topo (há contas) ou o título. */
const fallbackFocus = () => headerAddButton.value?.$el ?? heading.value
/** O botão `prefix` da conta, se ela ainda estiver na lista. */
const accountButton = (prefix: string, account: Account | null) =>
  account ? list.value?.querySelector<HTMLElement>(`[data-testid="${prefix}-${account.id}"]`) : null

const {
  open: dialogOpen,
  submitting: saving,
  error: dialogError,
  onOpenChange: onDialogOpenChange,
  onOpenAutoFocus,
  onCloseAutoFocus,
} = useFormDialog({
  form: () => accountForm.value,
  // Na edição, o foco volta para o "Editar" da conta
  returnFocus: () => accountButton('edit-account', editing.value) ?? fallbackFocus(),
})
const {
  open: removeDialogOpen,
  submitting: removeSubmitting,
  error: removeError,
  onOpenChange: onRemoveDialogOpenChange,
  onCloseAutoFocus: onRemoveCloseAutoFocus,
} = useFormDialog({ returnFocus: () => accountButton('remove-account', removing.value) ?? fallbackFocus() })
const {
  open: transferDialogOpen,
  submitting: transferSubmitting,
  error: transferError,
  onOpenChange: onTransferDialogOpenChange,
  onOpenAutoFocus: onTransferOpenAutoFocus,
  onCloseAutoFocus: onTransferCloseAutoFocus,
} = useFormDialog({ form: () => transferForm.value, returnFocus: () => transferButton.value?.$el ?? fallbackFocus() })
const {
  open: reconcileDialogOpen,
  submitting: reconcileSubmitting,
  error: reconcileError,
  onOpenChange: onReconcileDialogOpenChange,
  onOpenAutoFocus: onReconcileOpenAutoFocus,
  onCloseAutoFocus: onReconcileCloseAutoFocus,
} = useFormDialog({
  form: () => reconcileForm.value,
  returnFocus: () => accountButton('reconcile-account', reconciling.value) ?? fallbackFocus(),
})

/** Sequência das cargas da página inteira (montagem e "Tentar de novo"). */
let loadRequest = 0

async function load() {
  const request = ++loadRequest
  clearFeedback()
  loading.value = true
  loadFailed.value = false
  try {
    await fetchAccounts()
  } catch {
    if (request === loadRequest) loadFailed.value = true
  } finally {
    if (request === loadRequest) loading.value = false
  }
  if (request !== loadRequest) return
  if (showRemoved.value && !loadFailed.value) void loadRemoved()
  if (!loadFailed.value) void loadTransfers()
}

/**
 * Story 4.5: recarrega as contas do servidor (saldos depois de uma transferência, ou uma conta que sumiu). Uma
 * falha mantém a lista atual e avisa, sem esconder a página.
 */
async function refreshAccounts() {
  try {
    await fetchAccounts()
  } catch {
    notice.value = 'Não foi possível atualizar os saldos. Recarregue a página.'
  }
}

/**
 * Retro do Epic 4: caminho único do 404 de editar, remover, restaurar, transferir e reconciliar — a conta mudou em
 * outra aba ou sessão. Tira a conta das duas listas (quando se sabe qual é), mostra o aviso (quando há; a
 * transferência mostra o erro no próprio diálogo) e recarrega as listas sem esconder a página (uma falha na recarga
 * mantém o que já está na tela). Quem chama fecha o próprio diálogo.
 */
function onAccountNotFound(id?: string, message?: string) {
  if (id) {
    discardPendingAccountLoads()
    accounts.value = accounts.value.filter((x) => x.id !== id)
    // Uma carga de removidas em andamento leu o servidor antes do 404: é descartada (e recarregada abaixo)
    discardPendingRemovedLoad()
    removed.value = removed.value.filter((x) => x.id !== id)
  }
  if (message) {
    setSuccess(null)
    notice.value = message
  }
  if (showRemoved.value) void loadRemoved(true)
  fetchAccounts().catch(() => {})
}

// --- Adicionar e editar (Stories 4.1 e 4.3) ---

function openDialog() {
  openEdit(null)
}

function openEdit(account: Account | null) {
  editing.value = account
  dialogError.value = null
  clearFeedback()
  dialogOpen.value = true
}

function onCreated(created: Account) {
  discardPendingAccountLoads()
  accounts.value.push(created)
  dialogOpen.value = false
  setSuccess('Conta adicionada com sucesso')
}

/** Story 4.3: o item editado é substituído na lista (o total é recalculado pelo computed). */
function onUpdated(updated: Account) {
  const index = accounts.value.findIndex((x) => x.id === updated.id)
  if (index >= 0) {
    discardPendingAccountLoads()
    accounts.value.splice(index, 1, updated)
  }
  dialogOpen.value = false
  setSuccess('Informações atualizadas')
  // Story 4.5: os nomes das contas nas transferências acompanham a edição
  void loadTransfers(true)
}

/** Story 4.3: 404 ao salvar — o item foi removido ou não é do usuário; sai da lista. */
function onNotFound() {
  dialogOpen.value = false
  onAccountNotFound(editing.value?.id, 'Esta conta não existe mais')
}

/**
 * Retro do Epic 4: 409 ao salvar — a conta mudou em outra tela (transferência, reconciliação ou outra edição) depois
 * de o formulário abrir. Nada foi gravado; o diálogo fecha, o aviso mostra a mensagem do servidor e as contas são
 * recarregadas (uma falha na recarga é somada ao aviso). O foco volta ao "Editar" da conta (`onCloseAutoFocus`); se ela
 * sumiu na recarga, vai para o botão do topo ou o título.
 */
async function onConflict(detail: string | undefined) {
  const id = editing.value?.id
  dialogOpen.value = false
  setSuccess(null)
  const message = detail ?? 'Esta conta foi alterada em outra tela. Abra a edição de novo.'
  notice.value = message
  try {
    await fetchAccounts()
  } catch {
    // O aviso do conflito continua visível, junto com o da recarga
    notice.value = `${message} Não foi possível atualizar os saldos. Recarregue a página.`
    return
  }
  if (accounts.value.some((x) => x.id === id)) return
  await nextTick()
  const active = document.activeElement
  if (!active || active === document.body) fallbackFocus()?.focus()
}

function onFailed(detail: string | undefined) {
  dialogError.value = detail ?? 'Não foi possível salvar a conta. Tente novamente.'
}

// --- Remover e restaurar (Story 4.4) ---

function openRemove(account: Account) {
  removing.value = account
  removeError.value = null
  clearFeedback()
  removeDialogOpen.value = true
}

async function confirmRemove() {
  const target = removing.value
  if (!target || removeSubmitting.value) return
  removeSubmitting.value = true
  removeError.value = null
  try {
    await deleteAccount(target.id)
    discardPendingAccountLoads()
    accounts.value = accounts.value.filter((x) => x.id !== target.id)
    // A seção de removidas é recarregada para seguir a ordem do servidor
    if (showRemoved.value) void loadRemoved(true)
    removeDialogOpen.value = false
    setSuccess('Conta removida')
    void loadTransfers(true)
  } catch (err) {
    const response = (err as ApiError)?.response
    if (response?.status === 404) {
      removeDialogOpen.value = false
      onAccountNotFound(target.id, 'Esta conta já foi removida ou não existe mais')
    } else {
      // 409 "Mantenha ao menos uma conta ativa" e outras falhas ficam no diálogo
      removeError.value =
        response?.data?.detail ?? 'Não foi possível remover a conta. Tente novamente.'
    }
  } finally {
    removeSubmitting.value = false
  }
}

watch(showRemoved, (show) => {
  if (show) void loadRemoved()
})

/** Restaura a conta; ela sai da seção de removidas e volta à lista e ao total. */
async function restore(account: Account) {
  if (restoringId.value) return
  restoringId.value = account.id
  clearFeedback()
  try {
    const restored = await restoreAccount(account.id)
    removed.value = removed.value.filter((x) => x.id !== account.id)
    discardPendingAccountLoads()
    insertByCreation(restored)
    setSuccess('Conta restaurada')
    void loadTransfers(true)
    // O botão "Restaurar" some: o foco vai para o "Editar" da conta restaurada
    void nextTick(() => accountButton('edit-account', restored)?.focus())
  } catch (err) {
    if (apiErrorStatus(err) === 404) {
      onAccountNotFound(account.id, 'Esta conta já foi restaurada ou não existe mais')
    } else {
      notice.value = 'Não foi possível restaurar a conta. Tente novamente.'
    }
  } finally {
    restoringId.value = null
  }
}

// --- Transferir (Story 4.5) ---

function openTransfer() {
  transferError.value = null
  clearFeedback()
  transferDialogOpen.value = true
}

/** Fecha o diálogo e recarrega os saldos das contas e a seção (o total não muda). */
function onTransferCreated() {
  transferDialogOpen.value = false
  setSuccess('Transferência registrada')
  void refreshAccounts()
  void loadTransfers(true)
}

/**
 * 404 "Conta não encontrada" e outras falhas ficam no diálogo, que continua aberto. No 404 as listas são
 * recarregadas pelo caminho comum, para a conta removida sair das opções.
 */
function onTransferFailed(detail: string | undefined, status?: number) {
  transferError.value = detail ?? 'Não foi possível registrar a transferência. Tente novamente.'
  if (status === 404) onAccountNotFound()
}

// --- Reconciliar (Story 4.6) ---

function openReconcile(account: Account) {
  reconciling.value = account
  reconcileError.value = null
  clearFeedback()
  reconcileDialogOpen.value = true
}

/**
 * Aplica a conta devolvida pelo servidor (saldo e data novos), fecha o diálogo e recarrega as contas do servidor; o
 * total é recalculado pelo computed.
 */
function onReconciled(result: ReconciliationResult) {
  const index = accounts.value.findIndex((x) => x.id === result.account.id)
  if (index >= 0) {
    discardPendingAccountLoads()
    accounts.value.splice(index, 1, result.account)
  }
  reconcileDialogOpen.value = false
  setSuccess('Saldo reconciliado')
  void refreshAccounts()
}

/**
 * 404: a conta foi removida ou não é do usuário; o diálogo fecha e o resto segue o caminho comum do 404.
 * Outras falhas ficam no diálogo, que continua aberto.
 */
function onReconcileFailed(detail: string | undefined, status?: number) {
  if (status === 404) {
    reconcileDialogOpen.value = false
    onAccountNotFound(reconciling.value?.id, 'Esta conta não existe mais')
    return
  }
  reconcileError.value = detail ?? 'Não foi possível reconciliar a conta. Tente novamente.'
}

onMounted(load)
</script>
