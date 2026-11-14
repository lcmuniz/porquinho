<template>
  <Dialog :open="dialogOpen" @update:open="onOpenChange">
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
        <dd class="text-gray-900" data-testid="dialog-current-category">{{ categoryLabel(editingRow) }}</dd>
      </dl>
      <form class="space-y-4" novalidate data-testid="category-form" @submit.prevent="save">
        <div class="space-y-1">
          <label for="review-category" class="text-sm font-medium text-gray-900">Nova categoria</label>
          <select
            id="review-category"
            v-model="chosenCategoryId"
            :class="SELECT_CLASS"
            :disabled="isLoadingCategories || isSaving"
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
          <Button type="button" variant="outline" class="min-h-[44px]" @click="loadCategories">Tentar de novo</Button>
        </div>
        <DialogFooter>
          <Button
            type="button"
            variant="outline"
            class="min-h-[44px]"
            :disabled="isSaving"
            data-testid="category-cancel"
            @click="dialogOpen = false"
          >
            Cancelar
          </Button>
          <Button type="submit" class="min-h-[44px]" :disabled="!canSave" data-testid="category-save">
            {{ isSaving ? 'Salvando...' : 'Salvar' }}
          </Button>
        </DialogFooter>
      </form>
    </DialogContent>
  </Dialog>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
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
import { categoryLabel, categoryOptionGroups, signedAmount } from '@/lib/transactionLabels'
import { listCategories } from '@/services/categoryService'
import { updateImportCategory } from '@/services/transactionImportService'
import type { Category } from '@/types/category'
import type { ImportTransaction } from '@/types/transactionImport'

/** "Editar" de uma linha e "Alterar categoria" das selecionadas na revisão da importação (Story 5.10). */
const props = defineProps<{ importId: string }>()
const emit = defineEmits<{ saved: [affected: number, ids: string[]]; failed: [err: unknown] }>()

const SELECT_CLASS =
  'flex min-h-[44px] w-full rounded-md border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 focus-visible:ring-2 focus-visible:ring-purple-600 focus-visible:outline-none'

const dialogOpen = ref(false)
/** A linha do "Editar"; null no "Alterar categoria" das selecionadas. */
const editingRow = ref<ImportTransaction | null>(null)
const editingIds = ref<string[]>([])
const chosenCategoryId = ref('')
const categories = ref<Category[]>([])
const isLoadingCategories = ref(false)
const categoriesFailed = ref(false)
const isSaving = ref(false)
/** Sem as categorias carregadas, o select não mostra o que seria gravado. */
const canSave = computed(
  () => !!chosenCategoryId.value && !isSaving.value && !isLoadingCategories.value && !categoriesFailed.value,
)
const categoryGroups = computed(() => categoryOptionGroups(categories.value))

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

function open(row: ImportTransaction | null, ids: string[]) {
  editingRow.value = row
  editingIds.value = ids
  chosenCategoryId.value = row?.category?.id ?? ''
  dialogOpen.value = true
  void loadCategories()
}

/** Com o PATCH em andamento o diálogo não fecha: fechar pareceria cancelar uma alteração que já vai acontecer. */
function onOpenChange(value: boolean) {
  if (!isSaving.value) dialogOpen.value = value
}

async function save() {
  if (!canSave.value) return
  isSaving.value = true
  const ids = editingIds.value
  try {
    const result = await updateImportCategory(props.importId, ids, chosenCategoryId.value)
    dialogOpen.value = false
    emit('saved', result.affectedTransactions, ids)
  } catch (err) {
    dialogOpen.value = false
    emit('failed', err)
  } finally {
    isSaving.value = false
  }
}

defineExpose({ open })
</script>
