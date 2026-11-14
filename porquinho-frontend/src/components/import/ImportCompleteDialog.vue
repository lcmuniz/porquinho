<template>
  <Dialog :open="dialogOpen" @update:open="onOpenChange">
    <DialogContent data-testid="complete-dialog">
      <DialogHeader>
        <DialogTitle>Concluir importação</DialogTitle>
        <DialogDescription>
          <template v-if="toCard">As transações entram no cartão e não alteram o saldo de nenhuma conta.</template>
          <template v-else>Só as transações posteriores ao último ajuste do saldo alteram o saldo da conta.</template>
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
          @click="dialogOpen = false"
        >
          Cancelar
        </Button>
        <Button class="min-h-[44px]" :disabled="isCompleting" data-testid="complete-confirm" @click="finish">
          {{ isCompleting ? 'Concluindo...' : 'Concluir' }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { routeAfterCompletion } from '@/lib/importExit'
import { plural } from '@/lib/plural'
import { completeTransactionImport } from '@/services/transactionImportService'

/** Confirmação da conclusão da importação, com o resumo do que segue sem revisão (Story 5.10). */
const props = defineProps<{
  importId: string
  toCard: boolean
  unreviewedCount: number
  uncategorizedCount: number
}>()
const emit = defineEmits<{ failed: [err: unknown] }>()

const router = useRouter()
const dialogOpen = ref(false)
const isCompleting = ref(false)

function open() {
  dialogOpen.value = true
}

/** Com o POST em andamento o diálogo não fecha: a conclusão não tem volta. */
function onOpenChange(value: boolean) {
  if (!isCompleting.value) dialogOpen.value = value
}

async function finish() {
  if (isCompleting.value) return
  isCompleting.value = true
  const id = props.importId
  try {
    await completeTransactionImport(id)
    const destination = await routeAfterCompletion(id)
    dialogOpen.value = false
    await router.push(destination)
  } catch (err) {
    dialogOpen.value = false
    emit('failed', err)
  } finally {
    isCompleting.value = false
  }
}

defineExpose({ open })
</script>
