<template>
  <main class="min-h-screen bg-gray-50 px-4 py-8">
    <div class="mx-auto max-w-3xl space-y-6">
      <div class="flex items-center justify-between gap-4">
        <div>
          <h1 class="text-2xl font-semibold text-gray-900">Importar extrato</h1>
          <p class="mt-1 text-sm text-gray-600">
            Envie o extrato OFX do seu banco para uma conta ou um cartão.
          </p>
        </div>
        <RouterLink
          to="/dashboard"
          class="inline-flex min-h-[44px] items-center rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100"
        >
          Voltar
        </RouterLink>
      </div>

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
                <p class="text-sm text-gray-700">Arraste o arquivo .ofx para cá ou</p>
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
                  accept=".ofx"
                  class="sr-only"
                  aria-label="Arquivo do extrato"
                  data-testid="file-input"
                  @change="onFileChange"
                />
                <p v-if="selectedFile" class="text-sm font-medium text-gray-900" data-testid="selected-file">
                  {{ selectedFile.name }} ({{ formatSize(selectedFile.size) }})
                </p>
                <p class="text-xs text-gray-500">Formato OFX, até 10MB.</p>
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
              <p class="text-xs text-gray-700" data-testid="import-status">{{ statusLabel(item.status) }}</p>
            </li>
          </ul>
        </section>
      </template>
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { listAccounts } from '@/services/accountService'
import { listCards } from '@/services/cardService'
import { listImports, uploadImport } from '@/services/transactionImportService'
import type { Account } from '@/types/account'
import type { CreditCard } from '@/types/creditCard'
import type { ImportTarget, TransactionImport, TransactionImportStatus } from '@/types/transactionImport'

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

/** Decisão 1A da Story 5.1: a leitura das transações chega na Story 5.3. */
const STATUS_LABELS: Record<TransactionImportStatus, string> = {
  PROCESSING: 'Recebido — a leitura das transações chega na próxima etapa',
  REVIEW: 'Aguardando revisão',
  COMPLETED: 'Concluída',
  FAILED: 'Falhou',
  DISCARDED: 'Descartada',
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

async function load() {
  isLoading.value = true
  loadError.value = null
  try {
    const [accountList, cardList, importList] = await Promise.all([listAccounts(), listCards(), listImports()])
    accounts.value = accountList
    cards.value = cardList
    imports.value = importList
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

/** Validação no cliente, antes de enviar: extensão .ofx e até 10MB. */
function fileProblem(file: File): string | null {
  if (!file.name.toLowerCase().endsWith('.ofx')) {
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
    successMessage.value = MESSAGES.uploaded
    selectedFile.value = null
    if (fileInput.value) fileInput.value.value = ''
  } catch (e) {
    formError.value = (e as ApiError)?.response?.data?.detail ?? MESSAGES.uploadFailed
  } finally {
    isUploading.value = false
  }
}

function statusLabel(status: TransactionImportStatus): string {
  return STATUS_LABELS[status] ?? status
}

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
