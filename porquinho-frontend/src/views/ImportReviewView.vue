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
              @click="categoryDialog?.open(null, [...selected])"
            >
              Alterar categoria
              <template v-if="selected.size > 0">({{ selected.size }})</template>
            </Button>
            <Button class="min-h-[44px]" data-testid="complete-import" @click="completeDialog?.open()">
              Concluir importação
            </Button>
            <Button
              variant="outline"
              class="min-h-[44px] text-red-600 hover:bg-red-50 hover:text-red-700"
              data-testid="discard"
              @click="discardDialog?.open(transactionImport)"
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
                  <template v-if="row.kind === 'REGULAR' && row.category">
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
                @click="categoryDialog?.open(row, [row.id])"
              >
                Editar
              </Button>
            </li>
          </ul>
        </section>
      </template>

      <ImportCategoryDialog
        ref="categoryDialog"
        :key="`category-${importId}`"
        :import-id="importId"
        @saved="onCategorySaved"
        @failed="handleCategoryError"
      />
      <ImportCompleteDialog
        ref="completeDialog"
        :key="`complete-${importId}`"
        :import-id="importId"
        :to-card="!!transactionImport?.creditCardId"
        :unreviewed-count="unreviewedCount"
        :uncategorized-count="uncategorizedCount"
        @failed="handleActionError"
      />
      <ImportDiscardDialog
        ref="discardDialog"
        :key="`discard-${importId}`"
        :import-id="importId"
        @failed="handleActionError"
      />
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeftRight, Check, CreditCard } from 'lucide-vue-next'
import ImportCategoryDialog from '@/components/import/ImportCategoryDialog.vue'
import ImportCompleteDialog from '@/components/import/ImportCompleteDialog.vue'
import ImportDiscardDialog from '@/components/import/ImportDiscardDialog.vue'
import { Button } from '@/components/ui/button'
import { indicator, useImportReview } from '@/composables/useImportReview'
import { formatLocalDateBR } from '@/lib/date'
import { exitRoute } from '@/lib/importExit'
import { formatSignedBRL } from '@/lib/money'
import { plural } from '@/lib/plural'
import { categoryLabel, installmentLabel, pixLabel, signedAmount } from '@/lib/transactionLabels'
import { approveHighConfidence } from '@/services/transactionImportService'

/** Revisão de uma importação (Story 5.9), com a edição das categorias e a conclusão (Story 5.10). */
const ACTION_FAILED = 'Não foi possível concluir a ação. Tente de novo.'

interface ApiError {
  response?: { status?: number; data?: { detail?: string } }
}

const route = useRoute()
const router = useRouter()
const importId = computed(() => String(route.params.id))
const {
  transactionImport,
  transactions,
  loading,
  loadFailed,
  notFound,
  selected,
  visibleGroups,
  countLabel,
  pendingHigh,
  unreviewedCount,
  uncategorizedCount,
  load,
  reset,
  toggleRow,
} = useImportReview(() => importId.value)
const categoryDialog = ref<InstanceType<typeof ImportCategoryDialog> | null>(null)
const completeDialog = ref<InstanceType<typeof ImportCompleteDialog> | null>(null)
const discardDialog = ref<InstanceType<typeof ImportDiscardDialog> | null>(null)
const isApproving = ref(false)
const actionError = ref<string | null>(null)
const successMessage = ref<string | null>(null)

function showSuccess(message: string) {
  actionError.value = null
  successMessage.value = message
}

/** 409: a importação saiu de REVIEW em outra aba; a tela recarrega e mostra o estado atual. */
async function handleActionError(err: unknown) {
  successMessage.value = null
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
    const result = await approveHighConfidence(importId.value)
    await load()
    showSuccess(plural(result.affectedTransactions, 'transação aprovada', 'transações aprovadas'))
  } catch (err) {
    await handleActionError(err)
  } finally {
    isApproving.value = false
  }
}

async function onCategorySaved(affected: number, ids: string[]) {
  for (const id of ids) selected.value.delete(id)
  await load()
  showSuccess(plural(affected, 'categoria alterada', 'categorias alteradas'))
}

/** 404 aqui é uma linha ou uma categoria que saiu de cena, e não a importação: a recarga mostra o que sobrou. */
async function handleCategoryError(err: unknown) {
  const response = (err as ApiError)?.response
  if (response?.status !== 404) {
    await handleActionError(err)
    return
  }
  successMessage.value = null
  actionError.value = response.data?.detail ?? ACTION_FAILED
  await load()
}

async function continueLater() {
  await router.push(await exitRoute())
}

onMounted(load)

// Outra importação na mesma página (ex.: voltar/avançar no navegador): recarrega do zero. Os diálogos, pela :key
watch(importId, () => {
  reset()
  actionError.value = null
  successMessage.value = null
  void load()
})
</script>
