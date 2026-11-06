<template>
  <div class="space-y-6">
    <div v-if="isLoading" class="space-y-3" role="status" aria-label="Carregando">
      <div class="h-32 animate-pulse rounded-lg bg-gray-200" />
      <div class="h-20 animate-pulse rounded-lg bg-gray-200" />
    </div>

    <Card v-else-if="loadError" class="border-red-200 bg-red-50">
      <CardContent class="p-6 text-center">
        <p class="text-red-700" data-testid="load-error">{{ loadError }}</p>
        <Button variant="outline" class="mt-4 min-h-[44px]" @click="load">Tentar novamente</Button>
      </CardContent>
    </Card>

    <template v-else>
      <Card v-if="!hasTargets" class="bg-white" data-testid="no-targets">
        <CardContent class="space-y-3 p-6">
          <p class="text-sm text-gray-700">
            Para importar um extrato, cadastre antes a conta ou o cartão de destino.
          </p>
          <div class="flex flex-wrap gap-3">
            <RouterLink to="/contas" class="text-sm font-medium text-purple-700 underline">Cadastrar conta</RouterLink>
            <RouterLink to="/cartoes" class="text-sm font-medium text-purple-700 underline">Cadastrar cartão</RouterLink>
          </div>
        </CardContent>
      </Card>

      <Card v-else class="bg-white">
        <CardHeader>
          <CardTitle class="text-lg font-semibold text-gray-900">Enviar arquivo</CardTitle>
        </CardHeader>
        <CardContent>
          <form class="space-y-4" novalidate data-testid="import-form" @submit.prevent="submit">
            <div class="space-y-1">
              <label for="import-target" class="text-sm font-medium text-gray-900">Destino</label>
              <select
                id="import-target"
                v-model="targetKey"
                :class="SELECT_CLASS"
                data-testid="import-target-select"
                :disabled="isUploading"
              >
                <option value="" disabled>Escolha a conta ou o cartão</option>
                <optgroup v-if="accounts.length" label="Contas">
                  <option v-for="account in accounts" :key="account.id" :value="`account:${account.id}`">
                    {{ account.accountName }}
                  </option>
                </optgroup>
                <optgroup v-if="cards.length" label="Cartões de crédito">
                  <option v-for="card in cards" :key="card.id" :value="`card:${card.id}`">
                    {{ card.cardName }}
                  </option>
                </optgroup>
              </select>
            </div>

            <div
              class="flex flex-col items-center justify-center gap-3 rounded-lg border-2 border-dashed px-4 py-8 text-center"
              :class="isDragging ? 'border-purple-600 bg-purple-50' : 'border-gray-300 bg-gray-50'"
              data-testid="drop-zone"
              @dragover.prevent="isDragging = true"
              @dragleave.prevent="isDragging = false"
              @drop.prevent="onDrop"
            >
              <p class="text-sm text-gray-700">Arraste o arquivo .ofx ou .csv para cá ou</p>
              <Button
                type="button"
                variant="outline"
                class="min-h-[44px]"
                :disabled="isUploading"
                data-testid="choose-file"
                @click="fileInput?.click()"
              >
                Escolher arquivo
              </Button>
              <input
                ref="fileInput"
                type="file"
                accept=".ofx,.csv"
                class="sr-only"
                aria-label="Arquivo do extrato"
                data-testid="file-input"
                @change="onFileChange"
              />
              <p v-if="selectedFile" class="text-sm font-medium text-gray-900" data-testid="selected-file">
                {{ selectedFile.name }} ({{ formatSize(selectedFile.size) }})
              </p>
              <p class="text-xs text-gray-500">Formato OFX ou CSV, até 10MB.</p>
            </div>

            <div v-if="isUploading" class="space-y-1" data-testid="upload-progress">
              <div
                class="h-2 w-full overflow-hidden rounded-full bg-gray-200"
                role="progressbar"
                aria-label="Progresso do envio"
                :aria-valuenow="progress"
                aria-valuemin="0"
                aria-valuemax="100"
              >
                <div class="h-full bg-purple-600 transition-all" :style="{ width: `${progress}%` }" />
              </div>
              <p class="text-xs text-gray-600">Enviando... {{ progress }}%</p>
            </div>

            <p v-if="formError" class="text-sm text-red-600" role="alert" data-testid="form-error">{{ formError }}</p>
            <p v-if="successMessage" class="text-sm text-green-700" role="status" data-testid="success-message">
              {{ successMessage }}
            </p>

            <Button type="submit" class="min-h-[44px]" :disabled="isUploading" data-testid="submit-import">
              {{ isUploading ? 'Enviando...' : 'Enviar arquivo' }}
            </Button>
          </form>
        </CardContent>
      </Card>

      <section aria-labelledby="recent-imports-title" class="space-y-3">
        <h2 id="recent-imports-title" class="text-lg font-semibold text-gray-900">Importações recentes</h2>
        <p v-if="imports.length === 0" class="text-sm text-gray-600" data-testid="no-imports">
          Nenhum extrato enviado ainda.
        </p>
        <ul v-else class="divide-y divide-gray-200 rounded-lg border border-gray-200 bg-white" data-testid="import-list">
          <li v-for="item in imports" :key="item.id" class="space-y-1 px-4 py-3" :data-testid="`import-${item.id}`">
            <div class="flex flex-wrap items-baseline justify-between gap-2">
              <span class="truncate text-sm font-medium text-gray-900">{{ item.fileName }}</span>
              <span class="text-xs text-gray-500">{{ formatDate(item.createdAt) }}</span>
            </div>
            <p class="text-xs text-gray-600">
              {{ item.creditCardId ? 'Cartão' : 'Conta' }}: {{ item.targetName ?? '—' }}
            </p>
            <p class="text-xs text-gray-700" data-testid="import-status">{{ statusLabel(item) }}</p>
            <RouterLink
              v-if="item.status === 'REVIEW'"
              :to="`/importar/${item.id}/revisao`"
              class="inline-flex min-h-[44px] items-center text-sm font-medium text-purple-700 underline"
              :data-testid="`review-link-${item.id}`"
            >
              Revisar
            </RouterLink>
            <p
              v-if="item.status === 'FAILED' && item.errorMessage"
              class="text-xs text-red-600"
              data-testid="import-error"
            >
              {{ item.errorMessage }}
            </p>
          </li>
        </ul>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { plural } from '@/lib/plural'
import { listAccounts } from '@/services/accountService'
import { listCards } from '@/services/cardService'
import { getImport, listImports, uploadImport } from '@/services/transactionImportService'
import type { Account } from '@/types/account'
import type { CreditCard } from '@/types/creditCard'
import type { BankCode, ImportTarget, TransactionImport, TransactionImportStatus } from '@/types/transactionImport'

/**
 * Envio do extrato (Stories 5.1 a 5.8): destino, zona de envio, progresso, lista das importações e consulta a cada 2 s.
 * Usado na /importar e no passo 6 do onboarding (Story 5.10). `imports-change` entrega a lista sempre que ela muda.
 */
const emit = defineEmits<{
  'imports-change': [imports: TransactionImport[]]
}>()

const SELECT_CLASS =
  'flex min-h-[44px] w-full rounded-md border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 focus-visible:ring-2 focus-visible:ring-purple-600 focus-visible:outline-none'

/** Mesmo limite do backend (Spring multipart e nginx). */
const MAX_FILE_SIZE = 10 * 1024 * 1024

const MESSAGES = {
  targetRequired: 'Escolha a conta ou o cartão de destino',
  fileRequired: 'Escolha o arquivo do extrato',
  tooLarge: 'Arquivo muito grande (máximo 10MB)',
  unsupported: 'Formato de arquivo não suportado',
  uploaded: 'Arquivo enviado, processando...',
  uploadFailed: 'Não foi possível enviar o arquivo. Tente novamente.',
  loadFailed: 'Não foi possível carregar suas contas e cartões. Tente novamente.',
}

/** NFR8: progresso consultado a cada 2 s. */
const POLL_INTERVAL_MS = 2000

/** Story 5.4 (decisão 2A): sem progresso real; as contagens só aparecem no fim. */
const READING = 'lendo as transações...'

const STATUS_LABELS: Record<TransactionImportStatus, string> = {
  PROCESSING: 'Recebido — identificando o banco...',
  REVIEW: 'Aguardando revisão',
  COMPLETED: 'Concluída',
  FAILED: 'Falhou',
  DISCARDED: 'Descartada — nenhuma transação foi importada',
}

/** Story 5.3: OTHER é um OFX de banco fora dos cinco suportados (decisão 2A). No CSV o layout já define o banco (Story 5.2). */
const BANK_LABELS: Record<BankCode, string> = {
  NUBANK: 'Nubank',
  BANCO_DO_BRASIL: 'Banco do Brasil',
  ITAU: 'Itaú',
  BRADESCO: 'Bradesco',
  CAIXA: 'Caixa',
  OTHER: 'Banco não identificado',
}

const accounts = ref<Account[]>([])
const cards = ref<CreditCard[]>([])
const imports = ref<TransactionImport[]>([])
const isLoading = ref(true)
const loadError = ref<string | null>(null)

const targetKey = ref('')
const selectedFile = ref<File | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const isDragging = ref(false)
const isUploading = ref(false)
const progress = ref(0)
const formError = ref<string | null>(null)
const successMessage = ref<string | null>(null)

const hasTargets = computed(() => accounts.value.length > 0 || cards.value.length > 0)

watch(imports, (list) => emit('imports-change', list))

async function load() {
  isLoading.value = true
  loadError.value = null
  try {
    const [accountList, cardList, importList] = await Promise.all([listAccounts(), listCards(), listImports()])
    accounts.value = accountList
    cards.value = cardList
    imports.value = importList
    schedulePoll()
  } catch {
    loadError.value = MESSAGES.loadFailed
  } finally {
    isLoading.value = false
  }
}

function selectFile(file: File | undefined) {
  successMessage.value = null
  formError.value = null
  selectedFile.value = file ?? null
  if (file) {
    formError.value = fileProblem(file)
  }
}

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  selectFile(input.files?.[0])
}

function onDrop(event: DragEvent) {
  isDragging.value = false
  selectFile(event.dataTransfer?.files?.[0])
}

/** Validação no cliente, antes de enviar: extensão .ofx ou .csv (Story 5.2) e até 10MB. */
function fileProblem(file: File): string | null {
  const name = file.name.toLowerCase()
  if (!name.endsWith('.ofx') && !name.endsWith('.csv')) {
    return MESSAGES.unsupported
  }
  if (file.size > MAX_FILE_SIZE) {
    return MESSAGES.tooLarge
  }
  if (file.size === 0) {
    return MESSAGES.unsupported
  }
  return null
}

function target(): ImportTarget | null {
  const [kind, id] = targetKey.value.split(':')
  if (!id) return null
  return kind === 'card' ? { kind: 'card', id } : { kind: 'account', id }
}

interface ApiError {
  response?: { status?: number; data?: { detail?: string } }
}

async function submit() {
  successMessage.value = null
  const chosen = target()
  if (!chosen) {
    formError.value = MESSAGES.targetRequired
    return
  }
  if (!selectedFile.value) {
    formError.value = MESSAGES.fileRequired
    return
  }
  const problem = fileProblem(selectedFile.value)
  if (problem) {
    formError.value = problem
    return
  }
  formError.value = null
  isUploading.value = true
  progress.value = 0
  try {
    const created = await uploadImport(selectedFile.value, chosen, (percent) => {
      progress.value = percent
    })
    imports.value = [created, ...imports.value.filter((i) => i.id !== created.id)]
    schedulePoll()
    successMessage.value = MESSAGES.uploaded
    selectedFile.value = null
    if (fileInput.value) fileInput.value.value = ''
  } catch (e) {
    formError.value = (e as ApiError)?.response?.data?.detail ?? MESSAGES.uploadFailed
  } finally {
    isUploading.value = false
  }
}

/**
 * Story 5.5 (decisão 2A): sufixos de duplicatas e transferências já registradas, só quando a contagem passa de 0.
 */
function ignoredParts(item: TransactionImport): string[] {
  const parts: string[] = []
  if (item.duplicatesFound > 0) {
    parts.push(plural(item.duplicatesFound, 'duplicata detectada e ignorada', 'duplicatas detectadas e ignoradas'))
  }
  if (item.transfersFound > 0) {
    parts.push(plural(item.transfersFound, 'transferência já registrada', 'transferências já registradas'))
  }
  return parts
}

/** Linhas puladas na leitura, no formato "(N linhas com erro)" (Story 5.4, decisão 2A); vazio quando não há. */
function skippedSuffix(item: TransactionImport): string {
  return item.skippedRows > 0 ? ` (${plural(item.skippedRows, 'linha com erro', 'linhas com erro')})` : ''
}

/**
 * Story 5.3: banco detectado durante o PROCESSING. Story 5.4 (decisões 1A e 2A): lidas as transações, a importação vai
 * para REVIEW com as contagens (transações gravadas e linhas puladas). Story 5.5: duplicatas e transferências já
 * registradas no mesmo rótulo (decisão 2A); sem nada novo, a importação já chega COMPLETED (decisão 1A). Story 5.8:
 * com linhas enviadas à IA, o PROCESSING mostra o progresso da classificação. Story 5.10: concluída, as contagens ficam.
 */
function statusLabel(item: TransactionImport): string {
  if (item.status === 'PROCESSING' && item.classificationTotal > 0) {
    return `Classificando transações com IA... ${item.classifiedCount} de ${item.classificationTotal}`
  }
  if (item.status === 'PROCESSING' && item.bankName) {
    const bank = BANK_LABELS[item.bankName] ?? item.bankName
    return item.bankName === 'OTHER' ? `${bank} — ${READING}` : `Banco detectado: ${bank} — ${READING}`
  }
  if (item.status === 'REVIEW') {
    const read = plural(item.importedTransactions, 'transação', 'transações')
    const ignored = ignoredParts(item).map((part) => ` · ${part}`).join('')
    return `Pronta para revisão: ${read}${skippedSuffix(item)}${ignored}`
  }
  if (item.status === 'COMPLETED' && item.importedTransactions === 0 && item.duplicatesFound + item.transfersFound > 0) {
    return `Nenhuma transação nova${skippedSuffix(item)} — ${ignoredParts(item).join(' · ')}`
  }
  if (item.status === 'COMPLETED' && item.importedTransactions > 0) {
    const read = plural(item.importedTransactions, 'transação', 'transações')
    const ignored = ignoredParts(item).map((part) => ` · ${part}`).join('')
    return `Concluída: ${read}${skippedSuffix(item)}${ignored}`
  }
  return STATUS_LABELS[item.status] ?? item.status
}

/** Em processamento (com ou sem banco): a consulta segue até a importação sair de PROCESSING. */
function isPending(item: TransactionImport): boolean {
  return item.status === 'PROCESSING'
}

let pollTimer: ReturnType<typeof setTimeout> | null = null
let unmounted = false

/** Agenda a próxima consulta (2 s) se houver importação na etapa; nunca duas consultas ao mesmo tempo. */
function schedulePoll() {
  if (unmounted || pollTimer !== null || !imports.value.some(isPending)) return
  pollTimer = setTimeout(poll, POLL_INTERVAL_MS)
}

async function poll() {
  const pending = imports.value.filter(isPending)
  const results = await Promise.all(
    pending.map((item) =>
      getImport(item.id).then(
        (fresh) => ({ id: item.id, fresh, gone: false }),
        (e) => ({ id: item.id, fresh: null, gone: (e as ApiError)?.response?.status === 404 }),
      ),
    ),
  )
  if (unmounted) return
  for (const { id, fresh, gone } of results) {
    if (gone) {
      imports.value = imports.value.filter((i) => i.id !== id)
    } else if (fresh) {
      imports.value = imports.value.map((i) => (i.id === id ? fresh : i))
    }
  }
  pollTimer = null
  schedulePoll()
}

onUnmounted(() => {
  unmounted = true
  if (pollTimer !== null) clearTimeout(pollTimer)
  pollTimer = null
})

function formatSize(bytes: number): string {
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.ceil(bytes / 1024))} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1).replace('.', ',')} MB`
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

onMounted(load)
</script>
