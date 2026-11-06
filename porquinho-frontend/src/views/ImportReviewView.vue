<template>
  <main class="min-h-screen bg-gray-50 px-4 py-8">
    <div class="mx-auto max-w-3xl space-y-6">
      <div class="flex items-start justify-between gap-4">
        <div class="min-w-0">
          <h1 class="text-2xl font-semibold text-gray-900">Vamos revisar</h1>
          <p v-if="transactionImport" class="mt-1 truncate text-sm text-gray-600" data-testid="review-target">
            {{ transactionImport.targetName ?? '—' }} · {{ transactionImport.fileName }}
          </p>
          <div role="status" class="mt-1 text-sm text-green-700" data-testid="review-status-region">
            <span v-if="successMessage" data-testid="review-success">{{ successMessage }}</span>
          </div>
        </div>
        <RouterLink
          to="/importar"
          class="inline-flex min-h-[44px] shrink-0 items-center rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100"
          data-testid="continue-later"
        >
          Continuar depois
        </RouterLink>
      </div>

      <div v-if="loading" class="space-y-2" aria-busy="true" data-testid="review-skeleton">
        <div class="h-32 animate-pulse rounded-lg bg-gray-200" />
        <span class="sr-only">Carregando as transações...</span>
      </div>

      <div
        v-else-if="notFound"
        role="alert"
        class="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900"
        data-testid="review-not-found"
      >
        Esta importação não existe mais.
      </div>

      <div
        v-else-if="loadFailed"
        role="alert"
        class="flex flex-col gap-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3 sm:flex-row sm:items-center sm:justify-between"
        data-testid="review-load-error"
      >
        <p class="text-sm text-red-800">
          Não foi possível carregar a revisão. Verifique sua conexão e tente de novo.
        </p>
        <Button variant="outline" class="min-h-[44px] shrink-0" data-testid="review-retry" @click="load">
          Tentar de novo
        </Button>
      </div>

      <div
        v-else-if="transactionImport && transactionImport.status !== 'REVIEW'"
        role="status"
        class="rounded-lg border border-gray-200 bg-white px-4 py-3 text-sm text-gray-700"
        data-testid="review-closed"
      >
        Esta importação não está em revisão.
      </div>

      <template v-else-if="transactionImport">
        <section class="space-y-3 rounded-lg border border-gray-200 bg-white p-4">
          <p class="text-sm font-medium text-gray-900" data-testid="review-count">{{ countLabel }}</p>
          <div class="flex flex-wrap gap-3">
            <Button
              class="min-h-[44px]"
              :disabled="pendingHigh === 0 || isApproving"
              data-testid="approve-all"
              @click="approveAll"
            >
              {{ isApproving ? 'Aprovando...' : 'Aprovar todas de alta confiança' }}
            </Button>
            <Button
              variant="outline"
              class="min-h-[44px] text-red-600 hover:bg-red-50 hover:text-red-700"
              data-testid="discard"
              @click="openDiscardDialog"
            >
              Descartar importação
            </Button>
          </div>
          <p v-if="actionError" class="text-sm text-red-600" role="alert" data-testid="review-action-error">
            {{ actionError }}
          </p>
        </section>

        <p
          v-if="transactions.length === 0"
          class="rounded-lg border border-gray-200 bg-white p-6 text-center text-gray-600"
          data-testid="review-empty"
        >
          Nenhuma transação para revisar.
        </p>

        <section
          v-for="group in visibleGroups"
          :key="group.key"
          :aria-labelledby="`review-group-${group.key}`"
          class="space-y-2"
          :data-testid="`group-${group.key}`"
        >
          <h2 :id="`review-group-${group.key}`" class="text-sm font-medium text-gray-900">
            {{ group.title }} ({{ group.rows.length }})
          </h2>
          <ul class="divide-y divide-gray-100 rounded-lg border border-gray-200 bg-white">
            <li
              v-for="row in group.rows"
              :key="row.id"
              class="space-y-1 px-4 py-3"
              :data-testid="`transaction-${row.id}`"
            >
              <div class="flex items-baseline justify-between gap-3">
                <span class="min-w-0 text-sm font-medium break-words text-gray-900" data-testid="row-description">
                  {{ row.description }}
                </span>
                <span class="shrink-0 text-sm font-semibold text-gray-900" data-testid="row-amount">
                  {{ formatSignedBRL(row.type === 'DEBIT' ? -row.amount : row.amount) }}
                </span>
              </div>
              <p class="text-xs text-gray-600">
                <span data-testid="row-date">{{ formatLocalDateBR(row.date) }}</span>
                <template v-if="categoryLabel(row)">
                  · <span data-testid="row-category">{{ categoryLabel(row) }}</span>
                </template>
              </p>
              <div class="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs">
                <span
                  v-if="indicator(row)"
                  class="inline-flex items-center gap-1 font-medium"
                  :class="indicator(row)?.class"
                  data-testid="row-confidence"
                >
                  <component :is="indicator(row)?.icon" class="size-4" aria-hidden="true" />
                  {{ indicator(row)?.text }}
                </span>
                <span
                  v-if="row.reviewStatus !== 'PENDING'"
                  class="inline-flex items-center gap-1 font-medium text-green-700"
                  data-testid="row-approved"
                >
                  <Check class="size-4" aria-hidden="true" />
                  {{ row.reviewStatus === 'APPROVED' ? 'Aprovada' : 'Confirmada' }}
                </span>
                <span v-if="row.isPix" class="inline-flex items-center gap-1 text-gray-700" data-testid="row-pix">
                  <ArrowLeftRight class="size-4" aria-hidden="true" />
                  {{ pixLabel(row) }}
                </span>
                <span
                  v-if="installmentLabel(row)"
                  class="inline-flex items-center gap-1 text-gray-700"
                  data-testid="row-installment"
                >
                  <CreditCard class="size-4" aria-hidden="true" />
                  {{ installmentLabel(row) }}
                </span>
              </div>
            </li>
          </ul>
        </section>
      </template>

      <Dialog :open="showDiscardDialog" @update:open="onDiscardDialogChange">
        <DialogContent data-testid="discard-dialog">
          <DialogHeader>
            <DialogTitle>Descartar importação</DialogTitle>
            <DialogDescription>
              As transações deste arquivo serão removidas e nada entra nas suas contas. Você pode enviar o mesmo arquivo
              de novo depois.
            </DialogDescription>
          </DialogHeader>
          <p
            v-if="laterDuplicates.length > 0"
            role="alert"
            class="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900"
            data-testid="discard-warning"
          >
            Arquivos enviados depois deste tiveram lançamentos ignorados como duplicatas:
            {{ laterDuplicates.join(', ') }}. Depois de descartar, envie esses arquivos de novo para recuperar os
            lançamentos.
          </p>
          <DialogFooter>
            <Button
              variant="outline"
              class="min-h-[44px]"
              :disabled="isDiscarding"
              data-testid="discard-cancel"
              @click="showDiscardDialog = false"
            >
              Cancelar
            </Button>
            <Button
              variant="destructive"
              class="min-h-[44px]"
              :disabled="isDiscarding || isCheckingDiscard"
              data-testid="discard-confirm"
              @click="discard"
            >
              {{ isDiscarding ? 'Descartando...' : 'Descartar' }}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch, type Component } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ArrowLeftRight, Check, CircleCheck, CreditCard, Flag, TriangleAlert } from 'lucide-vue-next'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { formatLocalDateBR } from '@/lib/date'
import { formatSignedBRL } from '@/lib/money'
import { plural } from '@/lib/plural'
import {
  approveHighConfidence,
  discardImport,
  getImport,
  listImports,
  listImportTransactions,
} from '@/services/transactionImportService'
import type { ImportTransaction, TransactionImport } from '@/types/transactionImport'

/** Story 5.9: revisão de uma importação. A edição e a conclusão chegam na Story 5.10. */
type GroupKey = 'high' | 'medium' | 'low' | 'card-payment'

const HIGH_CONFIDENCE = 80
const MEDIUM_CONFIDENCE = 50

const GROUP_TITLES: Record<GroupKey, string> = {
  high: 'Alta confiança',
  medium: 'Média confiança',
  low: 'Baixa confiança',
  'card-payment': 'Pagamento de fatura',
}

const ACTION_FAILED = 'Não foi possível concluir a ação. Tente de novo.'

interface ApiError {
  response?: { status?: number; data?: { detail?: string } }
}

const route = useRoute()
const router = useRouter()
const transactionImport = ref<TransactionImport | null>(null)
const transactions = ref<ImportTransaction[]>([])
const loading = ref(true)
const loadFailed = ref(false)
const notFound = ref(false)
const isApproving = ref(false)
const isDiscarding = ref(false)
const showDiscardDialog = ref(false)
const isCheckingDiscard = ref(false)
/** Decisão 5A: arquivos que ignoraram lançamentos como duplicatas das linhas que o descarte apaga. */
const laterDuplicates = ref<string[]>([])
const actionError = ref<string | null>(null)
const successMessage = ref<string | null>(null)

/** Sem categoria, ou sem confiança numa linha REGULAR (dados anteriores à Story 5.8). */
function isUnclassified(row: ImportTransaction): boolean {
  return row.category === null || row.confidence === null
}

function groupOf(row: ImportTransaction): GroupKey {
  if (row.kind === 'CARD_PAYMENT') return 'card-payment'
  if (isUnclassified(row)) return 'low'
  const confidence = row.confidence ?? 0
  if (confidence >= HIGH_CONFIDENCE) return 'high'
  return confidence >= MEDIUM_CONFIDENCE ? 'medium' : 'low'
}

const groups = computed(() =>
  (Object.keys(GROUP_TITLES) as GroupKey[]).map((key) => ({
    key,
    title: GROUP_TITLES[key],
    rows: transactions.value.filter((row) => groupOf(row) === key),
  })),
)

const visibleGroups = computed(() => groups.value.filter((group) => group.rows.length > 0))

function countOf(key: GroupKey): number {
  return groups.value.find((group) => group.key === key)?.rows.length ?? 0
}

const countLabel = computed(() => {
  const high = plural(countOf('high'), 'transação de alta confiança', 'transações de alta confiança')
  const review = plural(countOf('medium') + countOf('low'), 'precisa de revisão', 'precisam de revisão')
  return `${high}, ${review}`
})

/** Linhas que o "Aprovar todas" ainda alteraria. */
const pendingHigh = computed(
  () => transactions.value.filter((row) => groupOf(row) === 'high' && row.reviewStatus === 'PENDING').length,
)

function categoryLabel(row: ImportTransaction): string {
  if (row.kind === 'CARD_PAYMENT' || !row.category) return ''
  return row.category.group ? `${row.category.group} › ${row.category.name}` : row.category.name
}

/** Ícone e texto, nunca só a cor (WCAG). CARD_PAYMENT não tem indicador. */
function indicator(row: ImportTransaction): { icon: Component; text: string; class: string } | null {
  const group = groupOf(row)
  if (group === 'card-payment') return null
  if (isUnclassified(row)) return { icon: Flag, text: 'Não classificado', class: 'text-red-700' }
  const text = `${GROUP_TITLES[group]} (${row.confidence}%)`
  if (group === 'high') return { icon: CircleCheck, text, class: 'text-green-700' }
  if (group === 'medium') return { icon: TriangleAlert, text, class: 'text-yellow-700' }
  return { icon: Flag, text, class: 'text-red-700' }
}

function pixLabel(row: ImportTransaction): string {
  const details = row.pixDetails
  if (!details) return 'Pix'
  const name = details.counterpart_name
  if (details.direction === 'SENT') return name ? `Pix enviado para ${name}` : 'Pix enviado'
  return name ? `Pix recebido de ${name}` : 'Pix recebido'
}

function installmentLabel(row: ImportTransaction): string {
  if (!row.isInstallment || row.installmentNumber === null || row.installmentTotal === null) return ''
  const left = row.installmentTotal - row.installmentNumber
  const progress = `Compra parcelada: ${row.installmentNumber}/${row.installmentTotal}`
  if (left === 0) return `${progress} — última parcela`
  return `${progress} — ${left === 1 ? 'resta' : 'restam'} ${plural(left, 'parcela', 'parcelas')}`
}

/** Sequência das cargas: só a resposta da carga mais recente é aplicada. */
let request = 0

async function load() {
  const current = ++request
  // Recarga com a tela já montada não troca a lista pelo esqueleto: foco e rolagem ficam onde estão
  loading.value = transactionImport.value === null
  loadFailed.value = false
  notFound.value = false
  try {
    const id = String(route.params.id)
    const [found, rows] = await Promise.all([getImport(id), listImportTransactions(id)])
    if (current !== request) return
    transactionImport.value = found
    transactions.value = rows
  } catch (err) {
    if (current !== request) return
    // 400 só vem de um id malformado na URL: para o usuário, é uma importação que não existe
    const status = (err as ApiError)?.response?.status
    if (status === 404 || status === 400) notFound.value = true
    else loadFailed.value = true
  } finally {
    if (current === request) loading.value = false
  }
}

/** 409: a importação saiu de REVIEW em outra aba; a tela recarrega e mostra o estado atual. */
async function handleActionError(err: unknown) {
  const response = (err as ApiError)?.response
  if (response?.status === 404) {
    notFound.value = true
    return
  }
  actionError.value = response?.data?.detail ?? ACTION_FAILED
  if (response?.status === 409) await load()
}

async function approveAll() {
  actionError.value = null
  successMessage.value = null
  isApproving.value = true
  try {
    const result = await approveHighConfidence(String(route.params.id))
    await load()
    successMessage.value = plural(result.affectedTransactions, 'transação aprovada', 'transações aprovadas')
  } catch (err) {
    await handleActionError(err)
  } finally {
    isApproving.value = false
  }
}

/** Decisão 5A: mesmo destino, criada depois desta, com duplicatas e ainda valendo. */
function hasLaterDuplicates(item: TransactionImport, current: TransactionImport): boolean {
  return (
    item.accountId === current.accountId &&
    item.creditCardId === current.creditCardId &&
    new Date(item.createdAt).getTime() > new Date(current.createdAt).getTime() &&
    item.duplicatesFound > 0 &&
    item.status !== 'DISCARDED' &&
    item.status !== 'FAILED'
  )
}

/** Sequência das checagens: só a da abertura mais recente do diálogo é aplicada. */
let discardCheck = 0

async function openDiscardDialog() {
  const current = ++discardCheck
  const opened = transactionImport.value
  laterDuplicates.value = []
  isCheckingDiscard.value = true
  showDiscardDialog.value = true
  try {
    const imports = await listImports()
    if (current !== discardCheck || !opened) return
    laterDuplicates.value = imports.filter((item) => hasLaterDuplicates(item, opened)).map((item) => item.fileName)
  } catch {
    // sem a lista o descarte continua possível, só sem o aviso
  } finally {
    if (current === discardCheck) isCheckingDiscard.value = false
  }
}

/** Com o POST em andamento o diálogo não fecha: fechar pareceria cancelar um descarte que já vai acontecer. */
function onDiscardDialogChange(open: boolean) {
  if (!isDiscarding.value) showDiscardDialog.value = open
}

async function discard() {
  actionError.value = null
  successMessage.value = null
  isDiscarding.value = true
  try {
    await discardImport(String(route.params.id))
    showDiscardDialog.value = false
    await router.push('/importar')
  } catch (err) {
    showDiscardDialog.value = false
    await handleActionError(err)
  } finally {
    isDiscarding.value = false
  }
}

onMounted(load)

// Outra importação na mesma página (ex.: voltar/avançar no navegador): recarrega do zero
watch(
  () => route.params.id,
  () => {
    transactionImport.value = null
    transactions.value = []
    showDiscardDialog.value = false
    discardCheck++
    isCheckingDiscard.value = false
    actionError.value = null
    successMessage.value = null
    void load()
  },
)
</script>
