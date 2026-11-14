<template>
  <Dialog :open="open" @update:open="onOpenChange">
    <DialogContent data-testid="edit-dialog" @close-auto-focus="emit('close-auto-focus', $event)">
      <DialogHeader>
        <DialogTitle>{{ creating ? 'Adicionar transação' : 'Editar transação' }}</DialogTitle>
        <DialogDescription>
          {{
            creating
              ? 'Registre um gasto em dinheiro ou um lançamento que nenhum extrato trouxe.'
              : 'Corrija data, descrição, valor, tipo, categoria ou onde a transação está.'
          }}
        </DialogDescription>
      </DialogHeader>
      <p
        v-if="formError"
        role="alert"
        data-testid="edit-error"
        class="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800"
      >
        {{ formError }}
      </p>
      <form class="space-y-4" novalidate data-testid="edit-form" @submit.prevent="save">
        <div class="space-y-1">
          <label for="transaction-date" class="text-sm font-medium text-gray-900">Data</label>
          <input
            id="transaction-date"
            v-model="form.date"
            type="date"
            :class="FIELD_CLASS"
            :aria-describedby="isFutureDate ? 'transaction-date-warning' : undefined"
            data-testid="edit-date"
          />
          <p
            v-if="isFutureDate"
            id="transaction-date-warning"
            class="text-sm text-amber-800"
            data-testid="future-date-warning"
          >
            Esta data ainda não chegou. Confira se é isso mesmo.
          </p>
        </div>
        <div class="space-y-1">
          <label for="transaction-description" class="text-sm font-medium text-gray-900">Descrição</label>
          <input
            id="transaction-description"
            v-model="form.description"
            maxlength="500"
            :class="FIELD_CLASS"
            data-testid="edit-description"
          />
        </div>
        <div class="space-y-1">
          <label for="transaction-amount" class="text-sm font-medium text-gray-900">Valor (R$)</label>
          <input
            id="transaction-amount"
            v-model="form.amount"
            inputmode="decimal"
            :class="FIELD_CLASS"
            data-testid="edit-amount"
          />
        </div>
        <div class="space-y-1">
          <label for="transaction-type" class="text-sm font-medium text-gray-900">Tipo</label>
          <select
            id="transaction-type"
            v-model="form.type"
            :class="FIELD_CLASS"
            :disabled="isInstallment"
            :aria-describedby="isInstallment ? 'installment-locked' : undefined"
            data-testid="edit-type"
          >
            <option value="DEBIT">Saída (débito)</option>
            <option value="CREDIT">Entrada (crédito)</option>
          </select>
        </div>
        <div class="space-y-1">
          <label for="transaction-category" class="text-sm font-medium text-gray-900">Categoria</label>
          <select
            id="transaction-category"
            v-model="form.categoryId"
            :class="FIELD_CLASS"
            :disabled="optionsLoading"
            data-testid="edit-category"
          >
            <option value="" :disabled="creating">{{ creating ? 'Escolha a categoria' : 'Sem categoria' }}</option>
            <optgroup v-for="group in categoryGroups" :key="group.id" :label="group.name">
              <option v-for="category in group.children" :key="category.id" :value="category.id">
                {{ category.categoryName }}
              </option>
            </optgroup>
          </select>
        </div>
        <div class="space-y-1">
          <label for="transaction-target" class="text-sm font-medium text-gray-900">Conta ou cartão</label>
          <select
            id="transaction-target"
            v-model="form.target"
            :class="FIELD_CLASS"
            :disabled="optionsLoading || isInstallment"
            :aria-describedby="isInstallment ? 'installment-locked' : undefined"
            data-testid="edit-target"
          >
            <option value="" disabled>Escolha a conta ou o cartão</option>
            <!-- Destino removido continua aceito enquanto não mudar -->
            <option v-if="removedTargetLabel" :value="form.target">{{ removedTargetLabel }}</option>
            <optgroup v-if="accounts.length" label="Contas">
              <option v-for="account in accounts" :key="account.id" :value="`account:${account.id}`">
                {{ account.accountName }}
              </option>
            </optgroup>
            <optgroup v-if="cards.length" label="Cartões">
              <option v-for="card in cards" :key="card.id" :value="`card:${card.id}`">{{ card.cardName }}</option>
            </optgroup>
          </select>
          <p
            v-if="creating && !optionsLoading && accounts.length === 0"
            class="text-sm text-gray-600"
            data-testid="wallet-hint"
          >
            Para gastos em dinheiro, cadastre uma conta Carteira em
            <RouterLink to="/contas" class="font-medium text-purple-700 underline">Contas</RouterLink>.
          </p>
          <p v-if="isInstallment" id="installment-locked" class="text-sm text-gray-600" data-testid="installment-locked">
            Numa parcela, a conta ou o cartão e o tipo não mudam.
          </p>
        </div>
        <DialogFooter>
          <Button
            type="button"
            variant="outline"
            class="min-h-[44px]"
            :disabled="saving"
            data-testid="edit-cancel"
            @click="emit('update:open', false)"
          >
            Cancelar
          </Button>
          <Button type="submit" class="min-h-[44px]" :disabled="saving || optionsLoading" data-testid="edit-save">
            {{ saving ? 'Salvando...' : 'Salvar' }}
          </Button>
        </DialogFooter>
      </form>
    </DialogContent>
  </Dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { todaySaoPaulo } from '@/lib/date'
import { formatBRLInput, parseBRL } from '@/lib/money'
import { plural } from '@/lib/plural'
import { createTransaction, updateTransaction } from '@/services/transactionService'
import type { Account } from '@/types/account'
import type { Category } from '@/types/category'
import type { CreditCard } from '@/types/creditCard'
import type { Transaction } from '@/types/transaction'

/**
 * Story 5.12: o diálogo de edição de /transacoes; 5.13: o mesmo em modo "Adicionar transação". Story 7.6: saiu da
 * TransactionsView para a Camada 3 do dashboard usar igual. As opções (contas, cartões e categorias) vêm do pai, que
 * também precisa delas para os nomes da lista.
 */
const props = defineProps<{
  open: boolean
  /** A linha em edição; null no modo "Adicionar transação". */
  row: Transaction | null
  creating?: boolean
  accounts: Account[]
  cards: CreditCard[]
  categories: Category[]
  optionsLoading: boolean
  /** As opções não carregaram: com o diálogo aberto, vira o erro do formulário. */
  optionsFailed?: boolean
}>()

const emit = defineEmits<{
  'update:open': [open: boolean]
  /** Salvou: `month` ("AAAA-MM") é o mês da transação criada, para a lista ir até ela. */
  saved: [result: { message: string; month?: string }]
  /** 404/409: a linha mudou ou sumiu, a lista deve recarregar. */
  stale: []
  'close-auto-focus': [event: Event]
}>()

const FIELD_CLASS =
  'flex min-h-[44px] w-full rounded-md border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 focus-visible:ring-2 focus-visible:ring-purple-600 focus-visible:outline-none'

const ACTION_FAILED = 'Não foi possível concluir a ação. Tente de novo.'
const OPTIONS_FAILED = 'Não foi possível carregar suas contas, cartões e categorias.'

interface ApiError {
  response?: { status?: number; data?: { detail?: string } }
}

const saving = ref(false)
const formError = ref<string | null>(null)
const form = reactive({ date: '', description: '', amount: '', type: 'DEBIT', categoryId: '', target: '' })

/** Decisão Q4: numa parcela, destino e tipo ficam como estão. */
const isInstallment = computed(() => !props.creating && !!props.row?.isInstallment)

/** Rótulo do destino atual quando ele já foi removido (fora das listas de ativos). */
const removedTargetLabel = computed(() => {
  const row = props.creating ? null : props.row
  if (!row || props.optionsLoading) return null
  if (row.accountId) return props.accounts.some((a) => a.id === row.accountId) ? null : 'Conta removida'
  return props.cards.some((c) => c.id === row.creditCardId) ? null : 'Cartão removido'
})

const isFutureDate = computed(() => !!form.date && form.date > todaySaoPaulo())

/** Grupos sem categoria filha ficam de fora: grupo não é categoria de transação. */
const categoryGroups = computed(() => {
  const byName = (a: Category, b: Category) => a.categoryName.localeCompare(b.categoryName, 'pt-BR')
  return props.categories
    .filter((category) => category.parentCategoryId === null)
    .sort(byName)
    .map((parent) => ({
      id: parent.id,
      name: parent.categoryName,
      children: props.categories.filter((category) => category.parentCategoryId === parent.id).sort(byName),
    }))
    .filter((group) => group.children.length > 0)
})

// Ao abrir, o formulário recomeça com a linha (ou vazio, ao adicionar)
watch(
  () => props.open,
  (open) => {
    if (!open) return
    formError.value = props.optionsFailed ? OPTIONS_FAILED : null
    const row = props.row
    if (props.creating || !row) {
      Object.assign(form, { date: todaySaoPaulo(), description: '', amount: '', type: 'DEBIT', categoryId: '', target: '' })
      return
    }
    form.date = row.date
    form.description = row.description
    form.amount = formatBRLInput(row.amount)
    form.type = row.type
    // Categoria removida fica em branco no select, mas é enviada igual: o backend só valida categoria trocada
    form.categoryId = row.category?.id ?? ''
    form.target = row.accountId ? `account:${row.accountId}` : `card:${row.creditCardId}`
  },
  { immediate: true },
)

watch(
  () => props.optionsFailed,
  (failed) => {
    if (failed && props.open) formError.value = OPTIONS_FAILED
  },
)

/** Com o PUT em andamento o diálogo não fecha: fechar pareceria cancelar uma alteração que já vai acontecer. */
function onOpenChange(open: boolean) {
  if (!saving.value) emit('update:open', open)
}

async function save() {
  const row = props.row
  const isCreate = !!props.creating
  if ((!row && !isCreate) || saving.value) return
  const amount = parseBRL(form.amount)
  const description = form.description.trim()
  if (!form.date) return void (formError.value = 'Informe a data')
  if (!description) return void (formError.value = 'Informe a descrição')
  if (amount === null || amount <= 0) return void (formError.value = 'Informe um valor maior que zero')
  if (isCreate && !form.categoryId) return void (formError.value = 'Escolha uma categoria')
  if (!form.target) return void (formError.value = 'Escolha uma conta ou um cartão')
  const [kind, targetId] = form.target.split(':')
  const input = {
    date: form.date,
    description,
    amount,
    type: form.type as Transaction['type'],
    categoryId: form.categoryId || null,
    accountId: kind === 'account' ? targetId! : null,
    creditCardId: kind === 'card' ? targetId! : null,
  }
  formError.value = null
  saving.value = true
  try {
    if (isCreate) {
      await createTransaction({ ...input, categoryId: form.categoryId })
      emit('update:open', false)
      emit('saved', { message: 'Transação adicionada', month: form.date.slice(0, 7) })
    } else {
      const updated = await updateTransaction(row!.id, input)
      // retro do Epic 6 (A1): a nova categoria vai junto para as parcelas futuras da compra
      const futures = (row!.category?.id ?? null) !== input.categoryId ? updated.remainingInstallments : 0
      const message =
        futures === 0
          ? 'Transação atualizada'
          : futures === 1
          ? 'Transação atualizada. A parcela futura também mudou de categoria'
          : `Transação atualizada. As ${plural(futures, 'parcela futura', 'parcelas futuras')} também mudaram de categoria`
      emit('update:open', false)
      emit('saved', { message })
    }
  } catch (err) {
    const response = (err as ApiError)?.response
    if (response?.status === 404 || response?.status === 400 || response?.status === 409) {
      formError.value = response.data?.detail ?? ACTION_FAILED
      if (response.status !== 400) emit('stale')
    } else {
      formError.value = ACTION_FAILED
    }
  } finally {
    saving.value = false
  }
}
</script>
