<template>
  <Dialog :open="dialogOpen" @update:open="onOpenChange">
    <DialogContent data-testid="discard-dialog">
      <DialogHeader>
        <DialogTitle>Descartar importação</DialogTitle>
        <DialogDescription>
          As transações deste arquivo serão removidas e nada entra nas suas contas. Você pode enviar o mesmo arquivo de
          novo depois.
        </DialogDescription>
      </DialogHeader>
      <p
        v-if="laterDuplicates.length > 0"
        role="alert"
        class="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900"
        data-testid="discard-warning"
      >
        Arquivos enviados depois deste tiveram lançamentos ignorados como duplicatas:
        {{ laterDuplicates.join(', ') }}. Depois de descartar, envie esses arquivos de novo para recuperar os
        lançamentos.
      </p>
      <DialogFooter>
        <Button
          variant="outline"
          class="min-h-[44px]"
          :disabled="isDiscarding"
          data-testid="discard-cancel"
          @click="dialogOpen = false"
        >
          Cancelar
        </Button>
        <Button
          variant="destructive"
          class="min-h-[44px]"
          :disabled="isDiscarding || isCheckingDiscard"
          data-testid="discard-confirm"
          @click="discard"
        >
          {{ isDiscarding ? 'Descartando...' : 'Descartar' }}
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
import { exitRoute } from '@/lib/importExit'
import { discardImport, listImports } from '@/services/transactionImportService'
import type { TransactionImport } from '@/types/transactionImport'

/** Confirmação do descarte da importação, com o aviso das duplicatas de arquivos posteriores (Story 5.10). */
const props = defineProps<{ importId: string }>()
const emit = defineEmits<{ failed: [err: unknown] }>()

const router = useRouter()
const dialogOpen = ref(false)
const isDiscarding = ref(false)
const isCheckingDiscard = ref(false)
/** Decisão 5A: arquivos que ignoraram lançamentos como duplicatas das linhas que o descarte apaga. */
const laterDuplicates = ref<string[]>([])

/** Decisão 5A: mesmo destino, criada depois desta, com duplicatas e ainda valendo. */
function hasLaterDuplicates(item: TransactionImport, current: TransactionImport): boolean {
  return (
    item.accountId === current.accountId &&
    item.creditCardId === current.creditCardId &&
    new Date(item.createdAt).getTime() > new Date(current.createdAt).getTime() &&
    item.duplicatesFound > 0 &&
    item.status !== 'DISCARDED' &&
    item.status !== 'FAILED'
  )
}

/** Sequência das checagens: só a da abertura mais recente do diálogo é aplicada. */
let discardCheck = 0

async function open(opened: TransactionImport | null) {
  const current = ++discardCheck
  laterDuplicates.value = []
  isCheckingDiscard.value = true
  dialogOpen.value = true
  try {
    const imports = await listImports()
    if (current !== discardCheck || !opened) return
    laterDuplicates.value = imports.filter((item) => hasLaterDuplicates(item, opened)).map((item) => item.fileName)
  } catch {
    // sem a lista o descarte continua possível, só sem o aviso
  } finally {
    if (current === discardCheck) isCheckingDiscard.value = false
  }
}

/** Com o POST em andamento o diálogo não fecha: fechar pareceria cancelar um descarte que já vai acontecer. */
function onOpenChange(value: boolean) {
  if (!isDiscarding.value) dialogOpen.value = value
}

async function discard() {
  isDiscarding.value = true
  try {
    await discardImport(props.importId)
    const destination = await exitRoute()
    dialogOpen.value = false
    await router.push(destination)
  } catch (err) {
    dialogOpen.value = false
    emit('failed', err)
  } finally {
    isDiscarding.value = false
  }
}

defineExpose({ open })
</script>
