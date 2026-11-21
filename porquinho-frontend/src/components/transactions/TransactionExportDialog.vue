<template>
  <Dialog :open="open" @update:open="onOpenChange">
    <DialogContent data-testid="export-dialog" @close-auto-focus="returnFocus">
      <DialogHeader>
        <DialogTitle>Exportar transações</DialogTitle>
        <DialogDescription>Gere um CSV para abrir no Excel com as transações confirmadas do período.</DialogDescription>
      </DialogHeader>
      <p
        v-if="failed"
        role="alert"
        data-testid="export-error"
        class="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800"
      >
        Não foi possível exportar. Tente de novo.
      </p>
      <form class="space-y-4" novalidate data-testid="export-form" @submit.prevent="generate">
        <div class="space-y-1">
          <label for="export-period" class="text-sm font-medium text-gray-900">Período</label>
          <select id="export-period" v-model="form.preset" :class="SELECT_CLASS" data-testid="export-period">
            <option value="this-month">Este mês</option>
            <option value="last-3-months">Últimos 3 meses</option>
            <option value="last-year">Último ano</option>
            <option value="all">Tudo</option>
            <option value="custom">Personalizado</option>
          </select>
        </div>
        <div v-if="form.preset === 'custom'" class="grid grid-cols-2 gap-3">
          <div class="space-y-1">
            <label id="export-from-label" class="text-sm font-medium text-gray-900">De</label>
            <DateInput
              labelledby="export-from-label"
              v-model="form.from"
              :invalid="!period"
              :describedby="!period ? 'export-period-help' : undefined"
              testid="export-from"
            />
          </div>
          <div class="space-y-1">
            <label id="export-to-label" class="text-sm font-medium text-gray-900">Até</label>
            <DateInput
              labelledby="export-to-label"
              v-model="form.to"
              :invalid="!period"
              :describedby="!period ? 'export-period-help' : undefined"
              testid="export-to"
            />
          </div>
          <p v-if="!period" id="export-period-help" class="col-span-2 text-sm text-red-700" data-testid="export-period-help">
            Escolha datas válidas, com o início até o fim.
          </p>
        </div>
        <div class="space-y-1">
          <label for="export-targets" class="text-sm font-medium text-gray-900">Contas e cartões</label>
          <MultiSelect
            id="export-targets"
            v-model="form.targets"
            label="Contas e cartões"
            testid="export-targets"
            :options="targetOptions"
          />
        </div>
        <div class="space-y-1">
          <label for="export-categories" class="text-sm font-medium text-gray-900">Categorias</label>
          <MultiSelect
            id="export-categories"
            v-model="form.categoryIds"
            label="Categorias"
            testid="export-categories"
            :options="categoryOptions"
          />
        </div>
        <label class="flex min-h-[44px] cursor-pointer items-center gap-2 text-sm text-gray-900">
          <input
            v-model="form.includeFutureInstallments"
            type="checkbox"
            class="size-4 accent-purple-600"
            data-testid="export-future"
          />
          Incluir parcelas futuras
        </label>
        <label class="flex min-h-[44px] cursor-pointer items-center gap-2 text-sm text-gray-900">
          <input v-model="form.includeDeleted" type="checkbox" class="size-4 accent-purple-600" data-testid="export-deleted" />
          Incluir excluídas
        </label>
        <DialogFooter>
          <Button
            type="button"
            variant="outline"
            class="min-h-[44px]"
            :disabled="exporting"
            data-testid="export-cancel"
            @click="emit('update:open', false)"
          >
            Cancelar
          </Button>
          <Button
            type="submit"
            class="min-h-[44px]"
            :disabled="!period || exporting"
            :aria-describedby="!period ? 'export-period-help' : undefined"
            data-testid="export-submit"
          >
            {{ exporting ? 'Gerando…' : 'Gerar CSV' }}
          </Button>
        </DialogFooter>
      </form>
    </DialogContent>
  </Dialog>
</template>

<script setup lang="ts">
import { computed, reactive } from 'vue'
import { useExportDialog } from '@/composables/useExportDialog'
import { Button } from '@/components/ui/button'
import DateInput from '@/components/common/DateInput.vue'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import MultiSelect from '@/components/common/MultiSelect.vue'
import { monthBounds, todaySaoPaulo } from '@/lib/date'
import { downloadBlob } from '@/lib/download'
import { SELECT_CLASS } from '@/lib/formField'
import { categoryFilterOptions, targetFilterOptions } from '@/lib/transactionLabels'
import { exportDetails } from '@/services/dashboardService'
import type { Account } from '@/types/account'
import type { Category } from '@/types/category'
import type { CreditCard } from '@/types/creditCard'

/**
 * Story 9.1: o diálogo de export CSV de transações, aberto pela Camada 3 e por /transacoes. Usa o export da Camada 3
 * sem busca textual e na ordem `date,desc`. O foco entra no primeiro campo e, ao fechar, volta ao botão que abriu.
 */
export type ExportPreset = 'this-month' | 'last-3-months' | 'last-year' | 'all' | 'custom'

export interface ExportInitial {
  preset: ExportPreset
  /** Datas do personalizado ("AAAA-MM-DD"); ignoradas nos atalhos. */
  from: string
  to: string
  categoryIds: string[]
  targets: string[]
}

const props = defineProps<{
  open: boolean
  accounts: Account[]
  cards: CreditCard[]
  categories: Category[]
  initial: ExportInitial
}>()

const emit = defineEmits<{
  'update:open': [open: boolean]
  /** O CSV baixou com `count` transações. */
  exported: [count: number]
}>()

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/

const form = reactive({
  preset: 'this-month' as ExportPreset,
  from: '',
  to: '',
  categoryIds: [] as string[],
  targets: [] as string[],
  includeFutureInstallments: false,
  includeDeleted: false,
})
// Ao abrir, o formulário recomeça com o filtro da tela de origem
const { exporting, failed, returnFocus, onOpenChange, run } = useExportDialog({
  open: () => props.open,
  setOpen: (open) => emit('update:open', open),
  reset: () =>
    Object.assign(form, {
      ...props.initial,
      categoryIds: [...props.initial.categoryIds],
      targets: [...props.initial.targets],
      includeFutureInstallments: false,
      includeDeleted: false,
    }),
})

const categoryOptions = computed(() => categoryFilterOptions(props.categories))
const targetOptions = computed(() => targetFilterOptions(props.accounts, props.cards))

/** O período escolhido, ou null quando o personalizado é inválido. */
const period = computed<{ from: string; to: string } | null>(() => {
  if (form.preset === 'this-month') return { from: monthBounds(0)[0], to: monthBounds(0)[1] }
  if (form.preset === 'last-3-months') return { from: monthBounds(-2)[0], to: monthBounds(0)[1] }
  // os últimos 12 meses com o atual
  if (form.preset === 'last-year') return { from: monthBounds(-11)[0], to: monthBounds(0)[1] }
  if (form.preset === 'all') return { from: '0001-01-01', to: '9999-12-31' }
  if (!ISO_DATE.test(form.from) || !ISO_DATE.test(form.to) || form.from > form.to) return null
  return { from: form.from, to: form.to }
})

async function generate() {
  const p = period.value
  if (!p) return
  let count = 0
  const ok = await run(async () => {
    const result = await exportDetails(
      { ...p, categoryIds: form.categoryIds, targets: form.targets, q: '', sort: 'date', dir: 'desc' },
      { includeFutureInstallments: form.includeFutureInstallments, includeDeleted: form.includeDeleted },
    )
    count = result.count
    downloadBlob(result.blob, `transacoes_${todaySaoPaulo()}.csv`)
  })
  if (ok) emit('exported', count)
}
</script>
