<template>
  <Dialog :open="category !== null" @update:open="(open) => !open && close()">
    <DialogContent
      data-testid="delete-dialog"
      @close-auto-focus="(event: Event) => emit('closeAutoFocus', event)"
    >
      <DialogHeader>
        <DialogTitle>Excluir "{{ category?.categoryName }}"?</DialogTitle>
        <DialogDescription>
          {{
            category?.parentCategoryId
              ? 'A categoria sai do seu orçamento. Você pode criá-la de novo depois.'
              : 'O grupo vazio sai da sua lista.'
          }}
        </DialogDescription>
      </DialogHeader>
      <p
        v-if="error"
        role="alert"
        data-testid="delete-error"
        class="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800"
      >
        {{ error }}
      </p>
      <div v-if="targetLabel" class="space-y-1">
        <label for="delete-target" class="text-sm font-medium text-gray-900">{{
          targetLabel
        }}</label>
        <select
          id="delete-target"
          v-model="target"
          :class="SELECT_CLASS"
          data-testid="delete-target"
          :disabled="deleting"
        >
          <option value="" disabled>Escolha uma categoria</option>
          <option v-for="child in targets" :key="child.id" :value="child.id">
            {{ child.categoryName }}
          </option>
        </select>
      </div>
      <DialogFooter>
        <Button
          variant="outline"
          class="min-h-[44px]"
          data-testid="cancel-delete-button"
          :disabled="deleting"
          @click="close"
        >
          Cancelar
        </Button>
        <Button
          class="min-h-[44px] bg-red-600 text-white hover:bg-red-700"
          data-testid="confirm-delete-button"
          :disabled="!canConfirm"
          @click="confirm"
        >
          {{ deleting ? 'Excluindo…' : 'Excluir' }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { SELECT_CLASS } from '@/lib/formField'
import { formatBRL } from '@/lib/money'
import { apiErrorDetail, apiErrorStatus } from '@/lib/apiError'
import { deleteCategory, getCategoryUsage } from '@/services/categoryService'
import type { Category } from '@/types/category'

/**
 * Pre-10.3: confirmação de exclusão de categoria (filha ou grupo vazio), usada no onboarding e em /categorias.
 * Retro Epic 6 A4: filha com transações ou orçamento do mês atual/futuros pede a filha de destino, que recebe tudo.
 * Aberto enquanto `category` não for null; erro (exceto 404, que conta como excluída) aparece aqui dentro.
 */
const props = defineProps<{ category: Category | null; categories: Category[] }>()
const emit = defineEmits<{
  deleted: [category: Category]
  close: []
  closeAutoFocus: [event: Event]
}>()

const FAILED = 'Não foi possível excluir. Verifique sua conexão e tente novamente.'

/** null = carregando o uso da filha. */
const usage = ref<{ transactionCount: number; allocatedAmount: number } | null>(null)
const target = ref('')
const deleting = ref(false)
const error = ref<string | null>(null)

const targets = computed(() =>
  props.categories
    .filter((c) => c.parentCategoryId !== null && c.id !== props.category?.id)
    .sort(
      (a, b) =>
        a.categoryName.localeCompare(b.categoryName, 'pt-BR', { sensitivity: 'base' }) ||
        a.id.localeCompare(b.id),
    ),
)

const targetLabel = computed(() => {
  const count = usage.value?.transactionCount ?? 0
  const amount = usage.value?.allocatedAmount ?? 0
  const transactions = count === 1 ? '1 transação' : `${count} transações`
  const budget = `${formatBRL(amount)} de orçamento`
  if (count > 0 && amount > 0) return `${transactions} e ${budget} vão para:`
  if (count > 0) return `${transactions} ${count === 1 ? 'vai' : 'vão'} para:`
  if (amount > 0) return `${budget} vai para:`
  return null
})

const canConfirm = computed(
  () =>
    !deleting.value && usage.value !== null && (targetLabel.value === null || target.value !== ''),
)

watch(
  () => props.category,
  async (category) => {
    target.value = ''
    error.value = null
    if (!category) return
    if (category.parentCategoryId === null) {
      usage.value = { transactionCount: 0, allocatedAmount: 0 }
      return
    }
    usage.value = null
    let loaded = { transactionCount: 0, allocatedAmount: 0 }
    try {
      loaded = await getCategoryUsage(category.id)
    } catch {
      // Sem o uso segue sem destino: o 409 do backend avisa se houver transações ou orçamento
    }
    if (props.category?.id === category.id) usage.value = loaded
  },
  { immediate: true },
)

function close() {
  if (!deleting.value) emit('close')
}

async function confirm() {
  const category = props.category
  if (!category || !canConfirm.value) return
  deleting.value = true
  error.value = null
  try {
    await deleteCategory(category.id, target.value || undefined)
    emit('deleted', category)
  } catch (err) {
    // 404: já tinha sido excluída (outra aba, por exemplo)
    if (apiErrorStatus(err) === 404) emit('deleted', category)
    else error.value = apiErrorDetail(err, FAILED)
  } finally {
    deleting.value = false
  }
}
</script>
