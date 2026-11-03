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
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { CreditCard as CreditCardIcon, Pencil, Plus } from 'lucide-vue-next'
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
import { listCards } from '@/services/cardService'
import type { CreditCard } from '@/types/creditCard'

/**
 * Story 4.2: cartões ativos, limite total e cadastro de cartão novo pelo diálogo.
 * O limite total é a base do crédito disponível, que depende das faturas (Epic 5).
 * Story 4.3: o botão "Editar" de cada cartão abre o mesmo diálogo com o formulário preenchido.
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

onMounted(load)
onBeforeUnmount(() => clearTimeout(successTimer))
</script>
