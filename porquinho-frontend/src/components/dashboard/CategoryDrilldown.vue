<template>
  <Dialog :open="category !== null" @update:open="!$event && emit('close')">
    <DialogContent class="max-h-[85vh] overflow-y-auto" data-testid="drilldown-dialog">
      <DialogHeader>
        <DialogTitle>{{ category?.name }}</DialogTitle>
        <DialogDescription>Gastos de {{ formatMonthBR(month) }}</DialogDescription>
      </DialogHeader>

      <p v-if="error" role="alert" class="text-sm text-gray-700" data-testid="drilldown-error">
        Não foi possível carregar as transações
      </p>
      <p v-else-if="!rows" role="status" class="text-sm text-gray-600" data-testid="drilldown-loading">
        Carregando…
      </p>
      <p v-else-if="!rows.length" class="text-sm text-gray-700" data-testid="drilldown-empty">
        Nenhuma transação neste mês
      </p>
      <ul v-else class="divide-y divide-gray-200 text-sm" data-testid="drilldown-list">
        <li v-for="t in rows" :key="t.id" class="flex items-start gap-3 py-2">
          <span class="shrink-0 text-gray-600">{{ formatLocalDateBR(t.date) }}</span>
          <span class="min-w-0 flex-1 break-words text-gray-900">{{ t.description }}</span>
          <span class="shrink-0 font-medium text-gray-900">{{ formatBRL(t.amount) }}</span>
        </li>
      </ul>
    </DialogContent>
  </Dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { listTransactions } from '@/services/transactionService'
import { formatLocalDateBR, formatMonthBR } from '@/lib/date'
import { formatBRL } from '@/lib/money'
import type { CategorySpending } from '@/types/dashboard'
import type { Transaction } from '@/types/transaction'

/**
 * Story 7.5 (decisão Q1=A): transações da categoria no mês corrente, pelo GET /transactions?month= filtrado aqui com a
 * regra de gasto do Epic 6 (débito REGULAR, sem parcela futura). `categoryId` null lista as sem categoria. O Dialog
 * fecha com Esc e devolve o foco a quem o abriu.
 */
const props = defineProps<{ category: CategorySpending | null; month: string }>()
const emit = defineEmits<{ close: [] }>()

const rows = ref<Transaction[] | null>(null)
const error = ref(false)

watch(
  () => props.category,
  async (category) => {
    // ao fechar, mantém o conteúdo durante a animação de saída do Dialog
    if (!category) return
    rows.value = null
    error.value = false
    try {
      const all = await listTransactions(props.month)
      if (props.category !== category) return
      rows.value = all.filter(
        (t) =>
          t.type === 'DEBIT' &&
          t.kind === 'REGULAR' &&
          !t.isFutureInstallment &&
          (t.category?.id ?? null) === category.categoryId,
      )
    } catch {
      if (props.category === category) error.value = true
    }
  },
)
</script>
