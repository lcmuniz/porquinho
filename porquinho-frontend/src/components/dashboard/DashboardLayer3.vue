<template>
  <div ref="root" class="mt-4 space-y-4">
    <form
      class="grid grid-cols-1 gap-3 rounded-lg border border-gray-200 bg-white p-4 md:grid-cols-2 xl:grid-cols-4"
      data-testid="details-filters"
      @submit.prevent
    >
      <div class="space-y-1">
        <label for="details-period" class="text-sm font-medium text-gray-900">Período</label>
        <select id="details-period" v-model="stored.preset" :class="SELECT_CLASS" data-testid="details-period">
          <option value="this-month">Este mês</option>
          <option value="last-month">Mês passado</option>
          <option value="last-3-months">Últimos 3 meses</option>
          <option value="custom">Personalizado</option>
        </select>
      </div>
      <div class="space-y-1">
        <label for="details-categories" class="text-sm font-medium text-gray-900">Categorias</label>
        <MultiSelect
          id="details-categories"
          v-model="stored.categoryIds"
          label="Categorias"
          testid="details-categories"
          :options="categoryOptions"
        />
      </div>
      <div class="space-y-1">
        <label for="details-targets" class="text-sm font-medium text-gray-900">Contas e cartões</label>
        <MultiSelect
          id="details-targets"
          v-model="stored.targets"
          label="Contas e cartões"
          testid="details-targets"
          :options="targetOptions"
        />
      </div>
      <div class="space-y-1">
        <label for="details-q" class="text-sm font-medium text-gray-900">Buscar na descrição</label>
        <Input id="details-q" v-model="qInput" type="search" class="min-h-[44px]" data-testid="details-q" />
      </div>
      <div v-if="stored.preset === 'custom'" class="grid grid-cols-2 gap-3 md:col-span-2">
        <div class="space-y-1">
          <label for="details-from" class="text-sm font-medium text-gray-900">De</label>
          <Input
            id="details-from"
            v-model="stored.from"
            type="date"
            class="min-h-[44px]"
            :aria-invalid="!period"
            :aria-describedby="!period ? 'details-period-error' : undefined"
            data-testid="details-from"
          />
        </div>
        <div class="space-y-1">
          <label for="details-to" class="text-sm font-medium text-gray-900">Até</label>
          <Input
            id="details-to"
            v-model="stored.to"
            type="date"
            class="min-h-[44px]"
            :aria-invalid="!period"
            :aria-describedby="!period ? 'details-period-error' : undefined"
            data-testid="details-to"
          />
        </div>
        <p
          v-if="!period"
          id="details-period-error"
          role="alert"
          class="col-span-2 text-sm text-red-700"
          data-testid="details-period-error"
        >
          Escolha datas válidas, com o início até o fim.
        </p>
      </div>
    </form>

    <div class="flex flex-wrap items-center justify-between gap-3">
      <div class="md:hidden">
        <label for="details-sort" class="sr-only">Ordenar por</label>
        <select id="details-sort" v-model="sortChoice" :class="SELECT_CLASS" data-testid="details-sort">
          <option value="date,desc">Data: mais recentes</option>
          <option value="date,asc">Data: mais antigas</option>
          <option value="amount,desc">Valor: maior primeiro</option>
          <option value="amount,asc">Valor: menor primeiro</option>
          <option value="category,asc">Categoria: A-Z</option>
          <option value="category,desc">Categoria: Z-A</option>
        </select>
      </div>
      <Button variant="outline" class="ml-auto min-h-[44px]" data-testid="details-export" @click="exportOpen = true">
        <Download class="size-4" aria-hidden="true" />
        Exportar CSV
      </Button>
    </div>

    <!-- Sempre no DOM: leitores de tela só anunciam mudanças numa região live que já existe -->
    <p
      role="status"
      data-testid="details-success"
      :class="successMessage ? 'rounded-lg border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-800' : 'sr-only'"
    >
      {{ successMessage }}
    </p>

    <div
      v-if="error"
      role="alert"
      class="flex flex-col gap-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3 sm:flex-row sm:items-center sm:justify-between"
      data-testid="details-error"
    >
      <p class="text-sm text-red-800">Não foi possível carregar as transações.</p>
      <Button variant="outline" class="min-h-[44px]" @click="load">Tentar de novo</Button>
    </div>
    <p v-else-if="!details" role="status" data-testid="details-loading" class="text-sm text-gray-600">
      {{ filter ? 'Carregando…' : '' }}
    </p>
    <p v-else-if="details.items.length === 0" class="text-sm text-gray-600" data-testid="details-empty">
      Nenhuma transação encontrada
    </p>
    <template v-else>
      <table class="hidden w-full table-fixed border-collapse rounded-lg bg-white text-sm md:table" data-testid="details-table">
        <caption class="sr-only">Transações filtradas</caption>
        <thead>
          <tr class="border-b border-gray-200 text-left text-gray-600">
            <th scope="col" class="w-28 px-3 py-2 font-medium" :aria-sort="ariaSort('date')">
              <button type="button" class="min-h-[44px] font-medium hover:text-gray-900" data-testid="sort-date" @click="sortBy('date')">
                Data {{ arrow('date') }}
              </button>
            </th>
            <th scope="col" class="px-3 py-2 font-medium">Descrição</th>
            <th scope="col" class="w-32 px-3 py-2 text-right font-medium" :aria-sort="ariaSort('amount')">
              <button type="button" class="min-h-[44px] font-medium hover:text-gray-900" data-testid="sort-amount" @click="sortBy('amount')">
                Valor {{ arrow('amount') }}
              </button>
            </th>
            <th scope="col" class="px-3 py-2 font-medium" :aria-sort="ariaSort('category')">
              <button type="button" class="min-h-[44px] font-medium hover:text-gray-900" data-testid="sort-category" @click="sortBy('category')">
                Categoria {{ arrow('category') }}
              </button>
            </th>
            <th scope="col" class="px-3 py-2 font-medium">Conta</th>
            <th scope="col" class="w-20 px-3 py-2 font-medium">Status</th>
            <th scope="col" class="w-24 px-3 py-2 font-medium"><span class="sr-only">Ações</span></th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="row in details.items"
            :key="row.id"
            class="border-b border-gray-100 align-middle"
            :data-testid="`details-row-${row.id}`"
          >
            <td class="px-3 py-2 text-gray-600">{{ formatLocalDateBR(row.date) }}</td>
            <td class="px-3 py-2 break-words text-gray-900">{{ row.description }}</td>
            <td class="px-3 py-2 text-right font-semibold" :class="row.type === 'CREDIT' ? 'text-green-700' : 'text-gray-900'">
              {{ formatSignedBRL(signedAmount(row)) }}
            </td>
            <td class="px-3 py-2 break-words text-gray-700">{{ categoryLabel(row) }}</td>
            <td class="px-3 py-2 break-words text-gray-700">{{ targetName(row, accounts, cards) }}</td>
            <td class="px-3 py-2 text-gray-700">
              <span class="flex gap-2">
                <span v-if="row.isPix" :title="pixLabel(row)" data-testid="row-pix">
                  <ArrowLeftRight class="size-4" aria-hidden="true" />
                  <span class="sr-only">{{ pixLabel(row) }}</span>
                </span>
                <span v-if="installmentLabel(row)" :title="installmentLabel(row)" data-testid="row-installment">
                  <CreditCard class="size-4" aria-hidden="true" />
                  <span class="sr-only">{{ installmentLabel(row) }}</span>
                </span>
              </span>
            </td>
            <td class="px-3 py-2">
              <Button
                variant="ghost"
                class="min-h-[44px] text-purple-700"
                :aria-label="`Editar ${row.description}`"
                :data-testid="`details-edit-${row.id}`"
                @click="openEdit(row)"
              >
                Editar
              </Button>
            </td>
          </tr>
        </tbody>
      </table>

      <ul class="space-y-3 md:hidden" data-testid="details-cards">
        <li
          v-for="row in details.items"
          :key="row.id"
          class="min-w-0 space-y-1 rounded-lg border border-gray-200 bg-white p-4"
          :data-testid="`details-card-${row.id}`"
        >
          <div class="flex items-baseline justify-between gap-3">
            <span class="text-sm text-gray-600">{{ formatLocalDateBR(row.date) }}</span>
            <span class="font-semibold" :class="row.type === 'CREDIT' ? 'text-green-700' : 'text-gray-900'">
              {{ formatSignedBRL(signedAmount(row)) }}
            </span>
          </div>
          <p class="break-words font-medium text-gray-900">{{ row.description }}</p>
          <p class="break-words text-sm text-gray-700">{{ categoryLabel(row) }} · {{ targetName(row, accounts, cards) }}</p>
          <p v-if="row.isPix" class="flex items-center gap-1 text-sm text-gray-700">
            <ArrowLeftRight class="size-4 shrink-0" aria-hidden="true" />{{ pixLabel(row) }}
          </p>
          <p v-if="installmentLabel(row)" class="flex items-center gap-1 text-sm text-gray-700">
            <CreditCard class="size-4 shrink-0" aria-hidden="true" />{{ installmentLabel(row) }}
          </p>
          <Button
            variant="ghost"
            class="min-h-[44px] px-0 text-purple-700"
            :aria-label="`Editar ${row.description}`"
            :data-testid="`details-card-edit-${row.id}`"
            @click="openEdit(row)"
          >
            Editar
          </Button>
        </li>
      </ul>

      <nav aria-label="Páginas das transações" class="flex flex-wrap items-center justify-between gap-3">
        <p class="text-sm text-gray-600" data-testid="details-page">
          {{ plural(details.totalItems, 'transação', 'transações') }} · página {{ page + 1 }} de {{ details.totalPages }}
        </p>
        <div class="flex gap-2">
          <Button variant="outline" class="min-h-[44px]" :disabled="loading || page === 0" data-testid="details-previous" @click="goTo(page - 1)">
            ‹ Anterior
          </Button>
          <Button
            variant="outline"
            class="min-h-[44px]"
            :disabled="loading || page + 1 >= details.totalPages"
            data-testid="details-next"
            @click="goTo(page + 1)"
          >
            Próxima ›
          </Button>
        </div>
      </nav>
    </template>

    <TransactionFormDialog
      v-model:open="editOpen"
      :row="current"
      :accounts="accounts"
      :cards="cards"
      :categories="categories"
      :options-loading="optionsLoading"
      :options-failed="optionsFailed"
      @saved="onSaved"
      @stale="load"
      @close-auto-focus="returnFocus"
    />
    <TransactionExportDialog
      v-model:open="exportOpen"
      :accounts="accounts"
      :cards="cards"
      :categories="categories"
      :initial="exportInitial"
      @exported="showSuccess(`Relatório exportado: ${plural($event, 'transação', 'transações')}`)"
    />
  </div>
</template>

<script setup lang="ts">
import { SELECT_CLASS } from '@/lib/formField'
import { Input } from '@/components/ui/input'
import { computed, nextTick, onMounted, ref } from 'vue'
import { useFeedback } from '@/composables/useFeedback'
import { ArrowLeftRight, CreditCard, Download } from 'lucide-vue-next'
import { Button } from '@/components/ui/button'
import MultiSelect from '@/components/common/MultiSelect.vue'
import TransactionExportDialog, { type ExportInitial } from '@/components/transactions/TransactionExportDialog.vue'
import TransactionFormDialog from '@/components/transactions/TransactionFormDialog.vue'
import { useDashboardDetails } from '@/composables/useDashboardDetails'
import { formatLocalDateBR } from '@/lib/date'
import { formatSignedBRL } from '@/lib/money'
import { plural } from '@/lib/plural'
import { categoryLabel, installmentLabel, pixLabel, signedAmount, targetName } from '@/lib/transactionLabels'
import type { Transaction } from '@/types/transaction'

/**
 * Story 7.6: a Camada 3, a lista completa das transações confirmadas com filtros (guardados por usuário no
 * localStorage), ordenação, páginas de 50 e edição pelo diálogo de /transacoes. Story 9.1: "Exportar CSV" abre o
 * diálogo de export com o filtro da tela (sem a busca textual).
 */
// Retro Epic 7 A4: `loaded` avisa a primeira carga (a view restaura o scroll)
const emit = defineEmits<{ 'transaction-saved': []; loaded: [] }>()

const {
  stored,
  qInput,
  accounts,
  cards,
  categories,
  optionsLoading,
  optionsFailed,
  details,
  loading,
  error,
  page,
  period,
  filter,
  sortChoice,
  categoryOptions,
  targetOptions,
  ariaSort,
  arrow,
  sortBy,
  load,
  goTo,
  loadOptions,
} = useDashboardDetails()

const root = ref<HTMLElement | null>(null)
const { successMessage, setSuccess: showSuccess } = useFeedback()
const current = ref<Transaction | null>(null)
const editOpen = ref(false)
const exportOpen = ref(false)

/** O diálogo herda o período (o atalho equivalente ou como personalizado), as categorias e as contas/cartões. */
const exportInitial = computed<ExportInitial>(() => {
  const s = stored.value
  const preset = s.preset === 'this-month' || s.preset === 'last-3-months' ? s.preset : 'custom'
  return {
    preset,
    from: period.value?.from ?? s.from,
    to: period.value?.to ?? s.to,
    categoryIds: s.categoryIds,
    targets: s.targets,
  }
})

function openEdit(row: Transaction) {
  current.value = row
  editOpen.value = true
}

/** O foco volta ao "Editar" visível da linha (tabela no desktop, card no mobile), se ela ainda estiver na página. */
function returnFocus(event: Event) {
  event.preventDefault()
  void nextTick(() => {
    const id = current.value?.id
    const buttons = id
      ? root.value?.querySelectorAll<HTMLElement>(
          `[data-testid="details-edit-${id}"], [data-testid="details-card-edit-${id}"]`,
        )
      : undefined
    const visible = [...(buttons ?? [])].find((b) => b.offsetParent !== null) ?? buttons?.[0]
    visible?.focus()
  })
}

async function onSaved({ message }: { message: string }) {
  emit('transaction-saved')
  await load()
  showSuccess(message)
}

onMounted(async () => {
  // As opções primeiro: o filtro salvo é limpo antes da primeira consulta
  await loadOptions()
  await load()
  emit('loaded')
})
</script>
