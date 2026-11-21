<template>
  <Dialog :open="open" @update:open="onOpenChange">
    <DialogContent data-testid="budget-export-dialog" @close-auto-focus="returnFocus">
      <DialogHeader>
        <DialogTitle>Exportar relatório de orçamento</DialogTitle>
        <DialogDescription>Gere um CSV para abrir no Excel com o alocado e o gasto de cada categoria.</DialogDescription>
      </DialogHeader>
      <p
        v-if="failed"
        role="alert"
        data-testid="budget-export-error"
        class="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800"
      >
        Não foi possível exportar. Tente de novo.
      </p>
      <form class="space-y-4" novalidate data-testid="budget-export-form" @submit.prevent="generate">
        <div class="grid grid-cols-2 gap-3">
          <div class="space-y-1">
            <label for="budget-export-from" class="text-sm font-medium text-gray-900">Mês inicial</label>
            <MonthInput
              id="budget-export-from"
              v-model="form.from"
              :invalid="!valid"
              :describedby="!valid ? 'budget-export-help' : undefined"
              testid="budget-export-from"
            />
          </div>
          <div class="space-y-1">
            <label for="budget-export-to" class="text-sm font-medium text-gray-900">Mês final</label>
            <MonthInput
              id="budget-export-to"
              v-model="form.to"
              :invalid="!valid"
              :describedby="!valid ? 'budget-export-help' : undefined"
              testid="budget-export-to"
            />
          </div>
          <p v-if="!valid" id="budget-export-help" class="col-span-2 text-sm text-red-700" data-testid="budget-export-help">
            Escolha até 12 meses, com o mês final a partir do inicial.
          </p>
        </div>
        <label class="flex min-h-[44px] cursor-pointer items-center gap-2 text-sm text-gray-900">
          <input
            v-model="form.hierarchy"
            type="checkbox"
            class="size-4 accent-purple-600"
            data-testid="budget-export-hierarchy"
          />
          Estrutura hierárquica
        </label>
        <label class="flex min-h-[44px] cursor-pointer items-center gap-2 text-sm text-gray-900">
          <input
            v-model="form.averages"
            type="checkbox"
            class="size-4 accent-purple-600"
            data-testid="budget-export-averages"
          />
          Médias históricas
        </label>
        <DialogFooter>
          <Button
            type="button"
            variant="outline"
            class="min-h-[44px]"
            :disabled="exporting"
            data-testid="budget-export-cancel"
            @click="emit('update:open', false)"
          >
            Cancelar
          </Button>
          <Button
            type="submit"
            class="min-h-[44px]"
            :disabled="!valid || exporting"
            :aria-describedby="!valid ? 'budget-export-help' : undefined"
            data-testid="budget-export-submit"
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
import MonthInput from '@/components/common/MonthInput.vue'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { shiftMonth } from '@/lib/date'
import { downloadBlob } from '@/lib/download'
import { exportBudgetReport } from '@/services/budgetService'

/**
 * Story 9.2: o diálogo do relatório de orçamento em CSV, aberto por /orcamento e /categorias. Começa no mês
 * `initialMonth`, com as duas opções desligadas. O foco entra no primeiro campo e, ao fechar, volta ao botão que abriu.
 */
const props = defineProps<{
  open: boolean
  /** AAAA-MM */
  initialMonth: string
}>()

const emit = defineEmits<{
  'update:open': [open: boolean]
  /** O CSV baixou. */
  exported: []
}>()

const MONTH = /^\d{4}-\d{2}$/
const MAX_MONTHS = 12

const form = reactive({ from: '', to: '', hierarchy: false, averages: false })
const { exporting, failed, returnFocus, onOpenChange, run } = useExportDialog({
  open: () => props.open,
  setOpen: (open) => emit('update:open', open),
  reset: () =>
    Object.assign(form, { from: props.initialMonth, to: props.initialMonth, hierarchy: false, averages: false }),
})

/** De 1 a 12 meses, com o final a partir do inicial (a mesma regra do backend). */
const valid = computed(
  () =>
    MONTH.test(form.from) &&
    MONTH.test(form.to) &&
    form.from <= form.to &&
    form.to <= shiftMonth(form.from, MAX_MONTHS - 1),
)

async function generate() {
  if (!valid.value) return
  const ok = await run(async () => {
    const { from, to, hierarchy, averages } = form
    const blob = await exportBudgetReport({ from, to, hierarchy, averages })
    downloadBlob(blob, from === to ? `orcamento_${from}.csv` : `orcamento_${from}_${to}.csv`)
  })
  if (ok) emit('exported')
}
</script>
