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
        <Button variant="outline" class="min-h-[44px] shrink-0" data-testid="continue-later" @click="continueLater">
          Continuar depois
        </Button>
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
              class="min-h-[44px]"
              :disabled="selected.size === 0"
              data-testid="change-category"
              @click="openBulkDialog"
            >
              Alterar categoria
              <template v-if="selected.size > 0">({{ selected.size }})</template>
            </Button>
            <Button class="min-h-[44px]" data-testid="complete-import" @click="showCompleteDialog = true">
              Concluir importação
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
              class="flex items-start gap-1 px-2 py-3 sm:px-4"
              :data-testid="`transaction-${row.id}`"
            >
              <label
                v-if="row.kind === 'REGULAR'"
                class="flex size-11 shrink-0 cursor-pointer items-center justify-center"
              >
                <input
                  type="checkbox"
                  class="h-5 w-5 accent-purple-600"
                  :checked="selected.has(row.id)"
                  :aria-label="`Selecionar ${row.description}`"
                  :data-testid="`select-${row.id}`"
                  @change="toggleRow(row.id)"
                />
              </label>
              <div class="min-w-0 flex-1 space-y-1">
                <div class="flex items-baseline justify-between gap-3">
                  <span class="min-w-0 text-sm font-medium break-words text-gray-900" data-testid="row-description">
                    {{ row.description }}
                  </span>
                  <span class="shrink-0 text-sm font-semibold text-gray-900" data-testid="row-amount">
                    {{ formatSignedBRL(signedAmount(row)) }}
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
                    v-if="row.reviewStatus !== 'PENDING' && group.key !== 'reviewed'"
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
              </div>
              <Button
                v-if="row.kind === 'REGULAR'"
                variant="ghost"
                class="min-h-[44px] shrink-0 text-purple-700"
                :aria-label="`Editar ${row.description}`"
                :data-testid="`edit-${row.id}`"
                @click="openEditDialog(row)"
              >
                Editar
              </Button>
            </li>
          </ul>
        </section>
      </template>

      <Dialog :open="showCategoryDialog" @update:open="onCategoryDialogChange">
        <DialogContent data-testid="category-dialog">
          <DialogHeader>
            <DialogTitle>{{ editingRow ? 'Editar categoria' : 'Alterar categoria' }}</DialogTitle>
            <DialogDescription>
              <template v-if="editingRow?.isInstallment">
                Aqui você troca a categoria desta transação. As parcelas futuras desta compra recebem a mesma categoria.
              </template>
              <template v-else-if="editingRow">Aqui você troca só a categoria desta transação.</template>
              <template v-else>
                {{ plural(editingIds.length, 'transação selecionada recebe', 'transações selecionadas recebem') }}
                a categoria escolhida.
              </template>
            </DialogDescription>
          </DialogHeader>
          <dl v-if="editingRow" class="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm" data-testid="category-dialog-row">
            <dt class="text-gray-600">Data</dt>
            <dd class="text-gray-900" data-testid="dialog-date">{{ formatLocalDateBR(editingRow.date) }}</dd>
            <dt class="text-gray-600">Descrição</dt>
            <dd class="break-words text-gray-900" data-testid="dialog-description">{{ editingRow.description }}</dd>
            <dt class="text-gray-600">Valor</dt>
            <dd class="text-gray-900" data-testid="dialog-amount">{{ formatSignedBRL(signedAmount(editingRow)) }}</dd>
            <dt class="text-gray-600">Categoria atual</dt>
            <dd class="text-gray-900" data-testid="dialog-current-category">
              {{ categoryLabel(editingRow) || 'Não classificado' }}
            </dd>
          </dl>
          <form class="space-y-4" novalidate data-testid="category-form" @submit.prevent="saveCategory">
            <div class="space-y-1">
              <label for="review-category" class="text-sm font-medium text-gray-900">Nova categoria</label>
              <select
                id="review-category"
                v-model="chosenCategoryId"
                :class="SELECT_CLASS"
                :disabled="isLoadingCategories || isSavingCategory"
                data-testid="category-select"
              >
                <option value="" disabled>{{ isLoadingCategories ? 'Carregando...' : 'Escolha a categoria' }}</option>
                <optgroup v-for="group in categoryGroups" :key="group.id" :label="group.name">
                  <option v-for="category in group.children" :key="category.id" :value="category.id">
                    {{ category.categoryName }}
                  </option>
                </optgroup>
              </select>
            </div>
            <div
              v-if="categoriesFailed"
              role="alert"
              class="flex items-center justify-between gap-3 text-sm text-red-600"
              data-testid="categories-error"
            >
              Não foi possível carregar suas categorias.
              <Button type="button" variant="outline" class="min-h-[44px]" @click="loadCategories">
                Tentar de novo
              </Button>
            </div>
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                class="min-h-[44px]"
                :disabled="isSavingCategory"
                data-testid="category-cancel"
                @click="showCategoryDialog = false"
              >
                Cancelar
              </Button>
              <Button
                type="submit"
                class="min-h-[44px]"
                :disabled="!canSaveCategory"
                data-testid="category-save"
              >
                {{ isSavingCategory ? 'Salvando...' : 'Salvar' }}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog :open="showCompleteDialog" @update:open="onCompleteDialogChange">
        <DialogContent data-testid="complete-dialog">
          <DialogHeader>
            <DialogTitle>Concluir importação</DialogTitle>
            <DialogDescription>
              <template v-if="transactionImport?.creditCardId">
                As transações entram no cartão e não alteram o saldo de nenhuma conta.
              </template>
              <template v-else>
                Só as transações posteriores ao último ajuste do saldo alteram o saldo da conta.
              </template>
              Depois de concluir, não dá para desfazer.
            </DialogDescription>
          </DialogHeader>
          <ul class="list-disc space-y-1 pl-5 text-sm text-gray-900" data-testid="complete-summary">
            <li data-testid="complete-unreviewed">
              {{ plural(unreviewedCount, 'transação segue sem revisão', 'transações seguem sem revisão') }}
            </li>
            <li data-testid="complete-uncategorized">
              {{ plural(uncategorizedCount, 'transação segue sem categoria', 'transações seguem sem categoria') }}
            </li>
          </ul>
          <DialogFooter>
            <Button
              variant="outline"
              class="min-h-[44px]"
              :disabled="isCompleting"
              data-testid="complete-cancel"
              @click="showCompleteDialog = false"
            >
              Cancelar
            </Button>
            <Button class="min-h-[44px]" :disabled="isCompleting" data-testid="complete-confirm" @click="finishImport">
              {{ isCompleting ? 'Concluindo...' : 'Concluir' }}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

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
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeftRight, Check, CircleCheck, CreditCard, Flag, TriangleAlert, UserCheck } from 'lucide-vue-next'
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
import { installmentLabel, pixLabel } from '@/lib/transactionLabels'
import { listCategories } from '@/services/categoryService'
import { completeImport as completeOnboardingImport, getMyOnboarding } from '@/services/onboardingService'
import {
  approveHighConfidence,
  completeTransactionImport,
  discardImport,
  getImport,
  listImports,
  listImportTransactions,
  updateImportCategory,
} from '@/services/transactionImportService'
import type { Category } from '@/types/category'
import type { ImportTransaction, TransactionImport } from '@/types/transactionImport'

/** Revisão de uma importação (Story 5.9), com a edição das categorias e a conclusão (Story 5.10). */
type GroupKey = 'high' | 'medium' | 'low' | 'reviewed' | 'card-payment'

const HIGH_CONFIDENCE = 80
const MEDIUM_CONFIDENCE = 50

const GROUP_TITLES: Record<GroupKey, string> = {
  high: 'Alta confiança',
  medium: 'Média confiança',
  low: 'Baixa confiança',
  reviewed: 'Revisadas por você',
  'card-payment': 'Pagamento de fatura',
}

const ACTION_FAILED = 'Não foi possível concluir a ação. Tente de novo.'

const SELECT_CLASS =
  'flex min-h-[44px] w-full rounded-md border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 focus-visible:ring-2 focus-visible:ring-purple-600 focus-visible:outline-none'

/** Passo do onboarding em que a importação é a primeira do usuário. */
const ONBOARDING_IMPORT_STEP = 6

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
const selected = ref(new Set<string>())
const showCategoryDialog = ref(false)
/** A linha do "Editar"; null no "Alterar categoria" das selecionadas. */
const editingRow = ref<ImportTransaction | null>(null)
const editingIds = ref<string[]>([])
const chosenCategoryId = ref('')
const categories = ref<Category[]>([])
const isLoadingCategories = ref(false)
const categoriesFailed = ref(false)
const isSavingCategory = ref(false)
const showCompleteDialog = ref(false)
const isCompleting = ref(false)
/** Sem as categorias carregadas, o select não mostra o que seria gravado. */
const canSaveCategory = computed(
  () =>
    !!chosenCategoryId.value && !isSavingCategory.value && !isLoadingCategories.value && !categoriesFailed.value,
)

/** Sem categoria, ou sem confiança numa linha REGULAR (dados anteriores à Story 5.8). */
function isUnclassified(row: ImportTransaction): boolean {
  return row.category === null || row.confidence === null
}

function groupOf(row: ImportTransaction): GroupKey {
  if (row.kind === 'CARD_PAYMENT') return 'card-payment'
  if (row.source === 'USER') return 'reviewed'
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

/** CARD_PAYMENT fica de fora: não tem categoria nem revisão. */
const regularRows = computed(() => transactions.value.filter((row) => row.kind === 'REGULAR'))
const unreviewedCount = computed(() => regularRows.value.filter((row) => row.reviewStatus === 'PENDING').length)
const uncategorizedCount = computed(() => regularRows.value.filter((row) => row.category === null).length)

/** Grupos sem categoria filha ficam de fora: grupo não é categoria de transação. */
const categoryGroups = computed(() => {
  const byName = (a: Category, b: Category) => a.categoryName.localeCompare(b.categoryName, 'pt-BR')
  return categories.value
    .filter((category) => category.parentCategoryId === null)
    .sort(byName)
    .map((parent) => ({
      id: parent.id,
      name: parent.categoryName,
      children: categories.value.filter((category) => category.parentCategoryId === parent.id).sort(byName),
    }))
    .filter((group) => group.children.length > 0)
})

function signedAmount(row: ImportTransaction): number {
  return row.type === 'DEBIT' ? -row.amount : row.amount
}

function categoryLabel(row: ImportTransaction): string {
  if (row.kind === 'CARD_PAYMENT' || !row.category) return ''
  return row.category.group ? `${row.category.group} › ${row.category.name}` : row.category.name
}

/** Ícone e texto, nunca só a cor (WCAG). CARD_PAYMENT não tem indicador. */
function indicator(row: ImportTransaction): { icon: Component; text: string; class: string } | null {
  const group = groupOf(row)
  if (group === 'card-payment') return null
  if (group === 'reviewed') return { icon: UserCheck, text: 'Revisada por você', class: 'text-purple-700' }
  if (isUnclassified(row)) return { icon: Flag, text: 'Não classificado', class: 'text-red-700' }
  const text = `${GROUP_TITLES[group]} (${row.confidence}%)`
  if (group === 'high') return { icon: CircleCheck, text, class: 'text-green-700' }
  if (group === 'medium') return { icon: TriangleAlert, text, class: 'text-yellow-700' }
  return { icon: Flag, text, class: 'text-red-700' }
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
    const selectable = new Set(rows.filter((row) => row.kind === 'REGULAR').map((row) => row.id))
    selected.value = new Set([...selected.value].filter((id) => selectable.has(id)))
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

function toggleRow(id: string) {
  if (!selected.value.delete(id)) selected.value.add(id)
}

/** Sequência das cargas de categorias: só a da abertura mais recente do diálogo é aplicada. */
let categoryRequest = 0

async function loadCategories() {
  const current = ++categoryRequest
  isLoadingCategories.value = true
  categoriesFailed.value = false
  try {
    const list = await listCategories()
    if (current !== categoryRequest) return
    categories.value = list
    const chosen = list.find((category) => category.id === chosenCategoryId.value)
    // Categoria atual removida, ou um grupo: não é uma escolha válida
    if (!chosen || chosen.parentCategoryId === null) chosenCategoryId.value = ''
  } catch {
    if (current === categoryRequest) categoriesFailed.value = true
  } finally {
    if (current === categoryRequest) isLoadingCategories.value = false
  }
}

function openCategoryDialog(row: ImportTransaction | null, ids: string[]) {
  editingRow.value = row
  editingIds.value = ids
  chosenCategoryId.value = row?.category?.id ?? ''
  showCategoryDialog.value = true
  void loadCategories()
}

function openEditDialog(row: ImportTransaction) {
  openCategoryDialog(row, [row.id])
}

function openBulkDialog() {
  openCategoryDialog(null, [...selected.value])
}

/** Com o PATCH em andamento o diálogo não fecha: fechar pareceria cancelar uma alteração que já vai acontecer. */
function onCategoryDialogChange(open: boolean) {
  if (!isSavingCategory.value) showCategoryDialog.value = open
}

/** 404 aqui é uma linha ou uma categoria que saiu de cena, e não a importação: a recarga mostra o que sobrou. */
async function handleCategoryError(err: unknown) {
  const response = (err as ApiError)?.response
  if (response?.status !== 404) {
    await handleActionError(err)
    return
  }
  actionError.value = response.data?.detail ?? ACTION_FAILED
  await load()
}

async function saveCategory() {
  if (!canSaveCategory.value) return
  actionError.value = null
  successMessage.value = null
  isSavingCategory.value = true
  try {
    const result = await updateImportCategory(String(route.params.id), editingIds.value, chosenCategoryId.value)
    showCategoryDialog.value = false
    for (const id of editingIds.value) selected.value.delete(id)
    await load()
    successMessage.value = plural(result.affectedTransactions, 'categoria alterada', 'categorias alteradas')
  } catch (err) {
    showCategoryDialog.value = false
    await handleCategoryError(err)
  } finally {
    isSavingCategory.value = false
  }
}

/** Com o POST em andamento o diálogo não fecha: a conclusão não tem volta. */
function onCompleteDialogChange(open: boolean) {
  if (!isCompleting.value) showCompleteDialog.value = open
}

/** Sem conseguir ler o progresso, vale a /importar. */
async function isOnOnboardingImportStep(): Promise<boolean> {
  try {
    const onboarding = await getMyOnboarding()
    return !onboarding.completed && onboarding.currentStep === ONBOARDING_IMPORT_STEP
  } catch {
    return false
  }
}

/** A /importar não tem caminho de volta ao onboarding: quem veio do passo 6 retorna para lá. */
async function exitRoute(): Promise<string> {
  return (await isOnOnboardingImportStep()) ? '/onboarding' : '/importar'
}

async function continueLater() {
  await router.push(await exitRoute())
}

/**
 * No passo 6 do onboarding, registra a primeira importação e volta para lá. Se o POST falhar, o "Continuar" do passo 6
 * resolve; a importação já está concluída e não falha por causa do onboarding.
 */
async function routeAfterCompletion(id: string): Promise<string> {
  if (!(await isOnOnboardingImportStep())) return '/importar'
  try {
    await completeOnboardingImport(id)
  } catch {
    // o passo 6 mostra "Continuar", porque a importação já está concluída
  }
  return '/onboarding'
}

async function finishImport() {
  if (isCompleting.value) return
  actionError.value = null
  successMessage.value = null
  isCompleting.value = true
  const id = String(route.params.id)
  try {
    await completeTransactionImport(id)
    const destination = await routeAfterCompletion(id)
    showCompleteDialog.value = false
    await router.push(destination)
  } catch (err) {
    showCompleteDialog.value = false
    await handleActionError(err)
  } finally {
    isCompleting.value = false
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
    const destination = await exitRoute()
    showDiscardDialog.value = false
    await router.push(destination)
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
    showCategoryDialog.value = false
    showCompleteDialog.value = false
    selected.value = new Set()
    discardCheck++
    isCheckingDiscard.value = false
    actionError.value = null
    successMessage.value = null
    void load()
  },
)
</script>
