<template>
  <main class="min-h-screen bg-gray-50 px-4 py-8">
    <div class="mx-auto max-w-3xl space-y-6">
      <div class="flex items-start justify-between gap-4">
        <div>
          <h1 ref="heading" tabindex="-1" class="text-2xl font-semibold text-gray-900 outline-none">
            Cartões de crédito
          </h1>
          <p class="mt-1 text-sm text-gray-600">Os cartões que você usa no dia a dia</p>
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
        data-testid="cards-success"
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
        data-testid="cards-notice"
        class="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900"
      >
        {{ notice }}
      </p>

      <div v-if="loading" class="space-y-2" aria-busy="true" data-testid="cards-skeleton">
        <div class="h-20 animate-pulse rounded-lg bg-gray-100" />
        <div class="h-16 animate-pulse rounded-lg bg-gray-100" />
        <span class="sr-only">Carregando seus cartões...</span>
      </div>

      <div
        v-else-if="loadFailed"
        role="alert"
        data-testid="cards-load-error"
        class="flex flex-col gap-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3 sm:flex-row sm:items-center sm:justify-between"
      >
        <p class="text-sm text-red-800">
          Não foi possível carregar seus cartões. Verifique sua conexão e tente de novo.
        </p>
        <Button
          variant="outline"
          class="min-h-[44px] shrink-0"
          data-testid="cards-retry"
          @click="load"
        >
          Tentar de novo
        </Button>
      </div>

      <div
        v-else-if="cards.length === 0"
        class="space-y-4 rounded-lg border border-gray-200 bg-white p-6 text-center"
        data-testid="cards-empty"
      >
        <p class="text-gray-600">Você ainda não tem cartões cadastrados</p>
        <Button
          class="min-h-[44px] bg-purple-600 text-white hover:bg-purple-700"
          data-testid="open-add-card"
          @click="openDialog"
        >
          <Plus class="h-4 w-4" aria-hidden="true" />
          Adicionar cartão
        </Button>
      </div>

      <template v-else>
        <section
          aria-labelledby="total-limit-label"
          class="flex flex-col gap-3 rounded-lg border border-gray-200 bg-white p-4 sm:flex-row sm:items-center sm:justify-between"
        >
          <div>
            <p id="total-limit-label" class="text-sm text-gray-600">Limite total</p>
            <p class="text-2xl font-semibold text-gray-900" data-testid="cards-total">
              {{ formatBRL(totalCents / 100) }}
            </p>
          </div>
          <Button
            class="min-h-[44px] bg-purple-600 text-white hover:bg-purple-700"
            ref="headerAddButton"
            data-testid="open-add-card"
            @click="openDialog"
          >
            <Plus class="h-4 w-4" aria-hidden="true" />
            Adicionar cartão
          </Button>
        </section>

        <section aria-labelledby="cards-list-heading" class="space-y-2">
          <h2 id="cards-list-heading" class="text-sm font-medium text-gray-900">
            Seus cartões ({{ cards.length }})
          </h2>
          <ul ref="list" class="space-y-2" data-testid="cards-list">
            <li
              v-for="card in cards"
              :key="card.id"
              class="flex items-center gap-3 rounded-lg border border-gray-200 bg-white p-3"
              :data-testid="`card-item-${card.id}`"
            >
              <CreditCardIcon class="h-5 w-5 shrink-0 text-purple-600" aria-hidden="true" />
              <div class="min-w-0 flex-1">
                <p class="truncate font-medium text-gray-900">{{ card.cardName }}</p>
                <p class="truncate text-xs text-gray-500" data-testid="card-details">
                  <template v-if="card.bankName">{{ card.bankName }} · </template>vence dia
                  {{ card.dueDay }}
                </p>
              </div>
              <span
                class="text-sm font-semibold whitespace-nowrap text-gray-900"
                data-testid="card-limit"
              >
                {{ formatBRL(card.cardLimit) }}
              </span>
              <Button
                variant="ghost"
                class="min-h-[44px] min-w-[44px] shrink-0 text-purple-700 hover:bg-purple-50"
                :data-testid="`edit-card-${card.id}`"
                :aria-label="`Editar ${card.cardName}`"
                @click="openEdit(card)"
              >
                <Pencil class="h-4 w-4" aria-hidden="true" />
                Editar
              </Button>
              <Button
                variant="ghost"
                class="min-h-[44px] min-w-[44px] shrink-0 text-red-700 hover:bg-red-50"
                :data-testid="`remove-card-${card.id}`"
                :aria-label="`Remover ${card.cardName}`"
                @click="openRemove(card)"
              >
                <Trash2 class="h-4 w-4" aria-hidden="true" />
                Remover
              </Button>
            </li>
          </ul>
        </section>
      </template>

      <!-- Story 4.4: cartões removidos, com "Restaurar" -->
      <template v-if="!loading && !loadFailed">
        <label
          class="inline-flex min-h-[44px] cursor-pointer items-center gap-2 text-sm text-gray-700"
        >
          <input
            v-model="showRemoved"
            type="checkbox"
            class="h-5 w-5 accent-purple-600"
            data-testid="toggle-removed-cards"
          />
          Mostrar removidos
        </label>

        <section
          v-if="showRemoved"
          aria-labelledby="removed-cards-heading"
          class="space-y-2"
          data-testid="removed-cards"
        >
          <h2 id="removed-cards-heading" class="text-sm font-medium text-gray-900">Removidos</h2>
          <p
            v-if="removedLoading"
            aria-busy="true"
            class="text-sm text-gray-600"
            data-testid="removed-cards-loading"
          >
            Carregando cartões removidos...
          </p>
          <div
            v-else-if="removedLoadFailed"
            role="alert"
            data-testid="removed-cards-load-error"
            class="flex flex-col gap-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3 sm:flex-row sm:items-center sm:justify-between"
          >
            <p class="text-sm text-red-800">Não foi possível carregar os cartões removidos.</p>
            <Button
              variant="outline"
              class="min-h-[44px] shrink-0"
              data-testid="removed-cards-retry"
              @click="loadRemoved()"
            >
              Tentar de novo
            </Button>
          </div>
          <p
            v-else-if="removed.length === 0"
            class="text-sm text-gray-600"
            data-testid="removed-cards-empty"
          >
            Nenhum cartão removido
          </p>
          <ul v-else class="space-y-2" data-testid="removed-cards-list">
            <li
              v-for="card in removed"
              :key="card.id"
              class="flex items-center gap-3 rounded-lg border border-dashed border-gray-300 bg-gray-50 p-3"
              :data-testid="`removed-card-item-${card.id}`"
            >
              <CreditCardIcon class="h-5 w-5 shrink-0 text-gray-400" aria-hidden="true" />
              <div class="min-w-0 flex-1">
                <p class="truncate font-medium text-gray-700">{{ card.cardName }}</p>
                <p class="truncate text-xs text-gray-500">
                  <template v-if="card.bankName">{{ card.bankName }} · </template>vence dia
                  {{ card.dueDay }}
                </p>
              </div>
              <span class="text-sm whitespace-nowrap text-gray-500" data-testid="card-limit">
                {{ formatBRL(card.cardLimit) }}
              </span>
              <Button
                variant="outline"
                class="min-h-[44px] min-w-[44px] shrink-0"
                :data-testid="`restore-card-${card.id}`"
                :aria-label="`Restaurar ${card.cardName}`"
                :disabled="restoringId !== null"
                @click="restore(card)"
              >
                <RotateCcw class="h-4 w-4" aria-hidden="true" />
                {{ restoringId === card.id ? 'Restaurando...' : 'Restaurar' }}
              </Button>
            </li>
          </ul>
        </section>
      </template>
    </div>

    <Dialog :open="dialogOpen" @update:open="onDialogOpenChange">
      <DialogContent
        :show-close-button="false"
        :data-testid="editing ? 'edit-card-dialog' : 'add-card-dialog'"
        @open-auto-focus="onOpenAutoFocus"
        @close-auto-focus="onCloseAutoFocus"
      >
        <DialogHeader>
          <DialogTitle>{{ editing ? 'Editar cartão' : 'Adicionar cartão' }}</DialogTitle>
          <DialogDescription v-if="editing">
            Corrija o nome, o banco, o limite ou o dia em que a fatura vence.
          </DialogDescription>
          <DialogDescription v-else>
            Informe o nome, o banco, o limite e o dia em que a fatura vence.
          </DialogDescription>
        </DialogHeader>
        <p
          v-if="dialogError"
          role="alert"
          data-testid="add-card-error"
          class="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800"
        >
          {{ dialogError }}
        </p>
        <CardForm
          ref="cardForm"
          v-model:submitting="saving"
          @attempt="dialogError = null"
          primary
          :card="editing"
          :submit-label="editing ? 'Salvar alterações' : 'Salvar cartão'"
          submitting-label="Salvando..."
          @created="onCreated"
          @updated="onUpdated"
          @not-found="onNotFound"
          @failed="onFailed"
        />
        <Button
          variant="outline"
          class="min-h-[44px] w-full"
          data-testid="cancel-add-card"
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
        data-testid="remove-card-dialog"
        @close-auto-focus="onRemoveCloseAutoFocus"
      >
        <DialogHeader>
          <DialogTitle>Remover {{ removing?.cardName }}?</DialogTitle>
          <DialogDescription>
            O cartão some da lista, mas o histórico é preservado. Você pode restaurá-lo depois.
          </DialogDescription>
        </DialogHeader>
        <p
          v-if="removeError"
          role="alert"
          data-testid="remove-card-error"
          class="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800"
        >
          {{ removeError }}
        </p>
        <div class="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button
            variant="outline"
            class="min-h-[44px]"
            data-testid="cancel-remove-card"
            :disabled="removeSubmitting"
            @click="removeDialogOpen = false"
          >
            Cancelar
          </Button>
          <Button
            class="min-h-[44px] bg-red-600 text-white hover:bg-red-700"
            data-testid="confirm-remove-card"
            :disabled="removeSubmitting"
            @click="confirmRemove"
          >
            {{ removeSubmitting ? 'Removendo...' : 'Remover' }}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { CreditCard as CreditCardIcon, Pencil, Plus, RotateCcw, Trash2 } from 'lucide-vue-next'
import CardForm from '@/components/cards/CardForm.vue'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { formatBRL } from '@/lib/money'
import { deleteCard, listCards, listDeletedCards, restoreCard } from '@/services/cardService'
import type { CreditCard } from '@/types/creditCard'

/**
 * Story 4.2: cartões ativos, limite total e cadastro de cartão novo pelo diálogo.
 * O limite total é a base do crédito disponível, que depende das faturas (Epic 5).
 * Story 4.3: o botão "Editar" de cada cartão abre o mesmo diálogo com o formulário preenchido.
 * Story 4.4: "Remover" com confirmação (sem mínimo de cartões); "Mostrar removidos" lista os removidos com
 * "Restaurar".
 */
const cards = ref<CreditCard[]>([])
const loading = ref(true)
const loadFailed = ref(false)
const dialogOpen = ref(false)
const saving = ref(false)
const dialogError = ref<string | null>(null)
const successMessage = ref<string | null>(null)
/** Story 4.3: aviso de item que não existe mais (404 ao editar); some na próxima ação. */
const notice = ref<string | null>(null)
const cardForm = ref<InstanceType<typeof CardForm> | null>(null)
const heading = ref<HTMLElement | null>(null)
const list = ref<HTMLElement | null>(null)
/** Story 4.3: item em edição (null no diálogo de adicionar). Continua definido depois de fechar, para o foco voltar ao botão dele. */
const editing = ref<CreditCard | null>(null)
const headerAddButton = ref<{ $el?: HTMLElement } | null>(null)

interface ApiError {
  response?: { status?: number; data?: { detail?: string } }
}

/** Story 4.4: cartão em remoção. Continua definido depois de fechar, para o foco voltar ao botão dele. */
const removing = ref<CreditCard | null>(null)
const removeDialogOpen = ref(false)
const removeSubmitting = ref(false)
const removeError = ref<string | null>(null)
/** Story 4.4: seção de removidos (carregada ao marcar o seletor). */
const showRemoved = ref(false)
const removed = ref<CreditCard[]>([])
const removedLoading = ref(false)
const removedLoadFailed = ref(false)
const restoringId = ref<string | null>(null)

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
  cards.value.reduce((sum, card) => sum + Math.round(card.cardLimit * 100), 0),
)

async function load() {
  setSuccess(null)
  notice.value = null
  loading.value = true
  loadFailed.value = false
  try {
    cards.value = await listCards()
  } catch {
    loadFailed.value = true
  } finally {
    loading.value = false
  }
  if (showRemoved.value && !loadFailed.value) void loadRemoved()
}

function openDialog() {
  editing.value = null
  dialogError.value = null
  setSuccess(null)
  notice.value = null
  dialogOpen.value = true
}

/** Story 4.3: abre o diálogo em modo edição, com o formulário preenchido. */
function openEdit(card: CreditCard) {
  editing.value = card
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
  if (cardForm.value) cardForm.value.focus()
  else void nextTick(() => cardForm.value?.focus())
}

/** Ao fechar, o foco vai para um alvo que sempre existe: o botão do topo (há cartões) ou o título. */
function onCloseAutoFocus(event: Event) {
  event.preventDefault()
  void nextTick(() => {
    // Na edição, o foco volta para o botão "Editar" do item (se ele ainda estiver na lista)
    const editButton = editing.value
      ? list.value?.querySelector<HTMLElement>(`[data-testid="edit-card-${editing.value.id}"]`)
      : null
    ;(editButton ?? headerAddButton.value?.$el ?? heading.value)?.focus()
  })
}

function onCreated(created: CreditCard) {
  cards.value.push(created)
  dialogOpen.value = false
  setSuccess('Cartão adicionado com sucesso')
}

/** Story 4.3: o item editado é substituído na lista (o total é recalculado pelo computed). */
function onUpdated(updated: CreditCard) {
  const index = cards.value.findIndex((x) => x.id === updated.id)
  if (index >= 0) cards.value.splice(index, 1, updated)
  dialogOpen.value = false
  setSuccess('Informações atualizadas')
}

/** Story 4.3: 404 ao salvar — o item foi removido ou não é do usuário; sai da lista. */
function onNotFound() {
  const id = editing.value?.id
  cards.value = cards.value.filter((x) => x.id !== id)
  dialogOpen.value = false
  setSuccess(null)
  notice.value = 'Este cartão não existe mais'
}

function onFailed(detail: string | undefined) {
  dialogError.value = detail ?? 'Não foi possível salvar o cartão. Tente novamente.'
}

/** Story 4.4: abre a confirmação da remoção. */
function openRemove(card: CreditCard) {
  removing.value = card
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

/** Ao fechar, o foco volta ao "Remover" do cartão (se ele continua na lista) ou ao botão do topo/título. */
function onRemoveCloseAutoFocus(event: Event) {
  event.preventDefault()
  void nextTick(() => {
    const removeButton = removing.value
      ? list.value?.querySelector<HTMLElement>(`[data-testid="remove-card-${removing.value.id}"]`)
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
    await deleteCard(target.id)
    cards.value = cards.value.filter((x) => x.id !== target.id)
    // A seção de removidos é recarregada para seguir a ordem do servidor
    if (showRemoved.value) void loadRemoved(true)
    removeDialogOpen.value = false
    setSuccess('Cartão removido')
  } catch (err) {
    const response = (err as ApiError)?.response
    if (response?.status === 404) {
      cards.value = cards.value.filter((x) => x.id !== target.id)
      removeDialogOpen.value = false
      notice.value = 'Este cartão já foi removido ou não existe mais'
      void refreshAfterNotFound()
    } else {
      removeError.value =
        response?.data?.detail ?? 'Não foi possível remover o cartão. Tente novamente.'
    }
  } finally {
    removeSubmitting.value = false
  }
}

/** Sequência das cargas: só a resposta da carga mais recente é aplicada. */
let removedRequest = 0

/** Story 4.4: carrega a seção de removidos. `quiet` recarrega sem trocar a lista pelo "Carregando...". */
async function loadRemoved(quiet = false) {
  const request = ++removedRequest
  if (!quiet) removedLoading.value = true
  removedLoadFailed.value = false
  try {
    const result = await listDeletedCards()
    if (request === removedRequest) removed.value = result
  } catch {
    if (request === removedRequest) removedLoadFailed.value = true
  } finally {
    if (request === removedRequest) removedLoading.value = false
  }
}

/**
 * Story 4.4: 404 ao remover ou restaurar — o item mudou em outra aba ou sessão. Recarrega as listas ativa e de
 * removidos sem esconder a página; uma falha aqui mantém o que já está na tela.
 */
async function refreshAfterNotFound() {
  if (showRemoved.value) void loadRemoved(true)
  try {
    cards.value = await listCards()
  } catch {
    // Mantém a lista atual; o aviso já foi mostrado
  }
}

watch(showRemoved, (show) => {
  if (show) void loadRemoved()
})

/** Volta para a lista ativa na posição da criação (a API ordena do mais antigo para o mais novo). */
function insertByCreation(card: CreditCard) {
  const created = Date.parse(card.createdAt)
  const index = cards.value.findIndex((x) => Date.parse(x.createdAt) > created)
  if (index < 0) cards.value.push(card)
  else cards.value.splice(index, 0, card)
}

/** Story 4.4: restaura o cartão; ele sai da seção de removidos e volta à lista e ao total. */
async function restore(card: CreditCard) {
  if (restoringId.value) return
  restoringId.value = card.id
  setSuccess(null)
  notice.value = null
  try {
    const restored = await restoreCard(card.id)
    removed.value = removed.value.filter((x) => x.id !== card.id)
    insertByCreation(restored)
    setSuccess('Cartão restaurado')
    // O botão "Restaurar" some: o foco vai para o "Editar" do cartão restaurado
    void nextTick(() =>
      list.value?.querySelector<HTMLElement>(`[data-testid="edit-card-${restored.id}"]`)?.focus(),
    )
  } catch (err) {
    if ((err as ApiError)?.response?.status === 404) {
      removed.value = removed.value.filter((x) => x.id !== card.id)
      notice.value = 'Este cartão já foi restaurado ou não existe mais'
      void refreshAfterNotFound()
    } else {
      notice.value = 'Não foi possível restaurar o cartão. Tente novamente.'
    }
  } finally {
    restoringId.value = null
  }
}

onMounted(load)
onBeforeUnmount(() => clearTimeout(successTimer))
</script>
