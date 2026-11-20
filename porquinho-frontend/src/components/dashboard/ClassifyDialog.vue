<template>
  <Dialog :open="open" @update:open="onOpenChange">
    <DialogContent
      data-testid="classify-dialog"
      @open-auto-focus="focusSelect"
      @close-auto-focus="emit('close-auto-focus', $event)"
    >
      <DialogHeader>
        <DialogTitle>Classificar</DialogTitle>
        <DialogDescription>
          Escolha a categoria de <span class="font-medium text-gray-900">{{ row?.description }}</span>.
        </DialogDescription>
      </DialogHeader>
      <form class="space-y-4" novalidate data-testid="classify-form" @submit.prevent="save">
        <div class="space-y-1">
          <label for="classify-category" class="text-sm font-medium text-gray-900">Categoria</label>
          <select
            id="classify-category"
            ref="select"
            v-model="categoryId"
            :class="SELECT_CLASS"
            :disabled="optionsLoading"
            data-testid="classify-category"
          >
            <option value="" disabled>Escolha uma categoria</option>
            <optgroup v-for="group in groups" :key="group.id" :label="group.name">
              <option v-for="category in group.children" :key="category.id" :value="category.id">
                {{ category.categoryName }}
              </option>
            </optgroup>
          </select>
        </div>
        <p v-if="error" role="alert" class="text-sm text-red-700" data-testid="classify-error">{{ error }}</p>
        <DialogFooter class="sm:items-center sm:justify-between">
          <button
            type="button"
            class="min-h-[44px] self-start text-sm font-medium text-purple-700 underline hover:text-purple-800"
            data-testid="classify-edit-all"
            @click="emit('edit-all')"
          >
            Editar tudo
          </button>
          <div class="flex flex-col-reverse gap-2 sm:flex-row">
            <Button
              type="button"
              variant="outline"
              class="min-h-[44px]"
              :disabled="saving"
              data-testid="classify-cancel"
              @click="emit('update:open', false)"
            >
              Cancelar
            </Button>
            <Button type="submit" class="min-h-[44px]" :disabled="saving || !categoryId" data-testid="classify-save">
              {{ saving ? 'Salvando…' : 'Salvar' }}
            </Button>
          </div>
        </DialogFooter>
      </form>
    </DialogContent>
  </Dialog>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { apiErrorDetail } from '@/lib/apiError'
import { SELECT_CLASS } from '@/lib/formField'
import { categoryOptionGroups } from '@/lib/transactionLabels'
import { batchUpdateCategory } from '@/services/transactionService'
import type { Category } from '@/types/category'
import type { Transaction } from '@/types/transaction'

/**
 * Critique de 20/11/2026 (3ª): classificar é a correção mais repetida do ritual, então pede só a categoria. O resto da
 * transação continua em "Editar tudo", que abre o formulário completo.
 */
const props = defineProps<{
  open: boolean
  row: Transaction | null
  categories: Category[]
  optionsLoading: boolean
}>()
const emit = defineEmits<{
  'update:open': [open: boolean]
  saved: [payload: { message: string }]
  'edit-all': []
  'close-auto-focus': [event: Event]
}>()

const groups = computed(() => categoryOptionGroups(props.categories))
const categoryId = ref('')
const saving = ref(false)
const error = ref<string | null>(null)
const select = ref<HTMLSelectElement | null>(null)

watch(
  () => props.open,
  (open) => {
    if (!open) return
    categoryId.value = ''
    error.value = null
  },
)

/** O foco vai direto para a única decisão do diálogo, e não para o primeiro botão. */
function focusSelect(event: Event) {
  event.preventDefault()
  void nextTick(() => select.value?.focus())
}

function onOpenChange(open: boolean) {
  if (!saving.value) emit('update:open', open)
}

async function save() {
  if (!props.row || !categoryId.value || saving.value) return
  saving.value = true
  error.value = null
  try {
    await batchUpdateCategory([props.row.id], categoryId.value)
    emit('update:open', false)
    emit('saved', { message: 'Transação classificada' })
  } catch (err) {
    error.value = apiErrorDetail(err, 'Não foi possível classificar. Tente de novo.')
  } finally {
    saving.value = false
  }
}
</script>
