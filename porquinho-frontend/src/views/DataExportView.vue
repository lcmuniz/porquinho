<template>
  <main class="min-h-screen bg-gray-50 px-4 py-8">
    <div class="mx-auto max-w-3xl space-y-6">
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-semibold text-gray-900">Exportar meus dados</h1>
          <p class="mt-1 text-sm text-gray-600">
            Exporte todos os seus dados em um ZIP com arquivos JSON (direito LGPD)
          </p>
        </div>
        <Button
          variant="outline"
          class="min-h-[44px]"
          @click="goBack"
          aria-label="Voltar para o dashboard"
        >
          Voltar
        </Button>
      </div>

      <!-- Loading state -->
      <div v-if="isLoading" class="flex items-center justify-center py-12" role="status" aria-label="Carregando status da exportação">
        <div class="h-8 w-8 animate-spin rounded-full border-4 border-purple-200 border-t-purple-600" />
        <span class="sr-only">Carregando...</span>
      </div>

      <!-- Error state -->
      <Card v-else-if="error" class="border-red-200 bg-red-50">
        <CardContent class="p-6 text-center">
          <p class="text-red-700">{{ error }}</p>
          <Button
            variant="outline"
            class="mt-4 min-h-[44px]"
            @click="loadStatus"
          >
            Tentar novamente
          </Button>
        </CardContent>
      </Card>

      <!-- Main content -->
      <template v-else>
        <!-- No previous export / idle state -->
        <Card v-if="!exportData || exportData.status === 'EXPIRED' || exportData.status === 'DOWNLOADED'" class="bg-white">
          <CardHeader>
            <CardTitle class="text-lg font-semibold text-gray-900">Solicitar exportação</CardTitle>
          </CardHeader>
          <CardContent class="space-y-4">
            <p class="text-sm text-gray-600">
              Você pode exportar todos os seus dados pessoais armazenados no Porquinho.
              A exportação inclui perfil, contas, cartões, transações (inclusive as excluídas), categorias,
              orçamentos, metas e registros de atividade.
            </p>
            <p class="text-sm text-gray-500">
              O arquivo será um ZIP com arquivos JSON, protegido pela senha que você definir, e ficará
              disponível para download por 24 horas.
            </p>
            <Button
              class="min-h-[44px]"
              :disabled="isRequesting"
              @click="showConfirmDialog = true"
              aria-label="Solicitar exportação de dados"
            >
              Exportar Meus Dados
            </Button>
          </CardContent>
        </Card>

        <!-- Processing state -->
        <Card v-else-if="exportData.status === 'PROCESSING'" class="bg-white">
          <CardHeader>
            <CardTitle class="text-lg font-semibold text-gray-900">Exportação em andamento</CardTitle>
          </CardHeader>
          <CardContent class="space-y-4">
            <div class="flex items-center gap-3">
              <div class="h-5 w-5 animate-spin rounded-full border-2 border-purple-200 border-t-purple-600" />
              <p class="text-sm text-gray-600">
                Seus dados estão sendo coletados e preparados para download...
              </p>
            </div>
            <p class="text-xs text-gray-500">
              Solicitado em: {{ formatDate(exportData.createdAt) }}
            </p>
          </CardContent>
        </Card>

        <!-- Failed state -->
        <Card v-else-if="exportData.status === 'FAILED'" class="border-red-200 bg-red-50">
          <CardHeader>
            <CardTitle class="text-lg font-semibold text-red-900">Falha na exportação</CardTitle>
          </CardHeader>
          <CardContent class="space-y-4">
            <p class="text-sm text-red-700" role="alert">Não foi possível gerar o export. Tente de novo.</p>
            <Button
              class="min-h-[44px]"
              :disabled="isRequesting"
              @click="showConfirmDialog = true"
              aria-label="Solicitar exportação de dados"
            >
              Tentar de novo
            </Button>
          </CardContent>
        </Card>

        <!-- Ready state -->
        <Card v-else-if="exportData.status === 'READY'" class="border-green-200 bg-green-50">
          <CardHeader>
            <CardTitle class="text-lg font-semibold text-green-900">Exportação pronta!</CardTitle>
          </CardHeader>
          <CardContent class="space-y-4">
            <p class="text-sm text-green-700">
              Seu arquivo ZIP está pronto para download. Use a senha que você definiu para abri-lo.
            </p>
            <p class="text-xs text-gray-600">
              Expira em: {{ formatDate(exportData.expiresAt!) }}
            </p>
            <Button
              class="min-h-[44px]"
              @click="handleDownload"
              aria-label="Baixar exportação de dados"
            >
              Baixar arquivo
            </Button>
            <p class="text-xs text-gray-500">
              O link expira após 24 horas ou após o primeiro download.
            </p>
          </CardContent>
        </Card>
      </template>

      <!-- Confirm dialog -->
      <Dialog :open="showConfirmDialog" @update:open="onDialogOpenChange">
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Confirmar exportação de dados</DialogTitle>
            <DialogDescription>
              Isso vai exportar TODOS os seus dados. Continuar?
            </DialogDescription>
          </DialogHeader>
          <form class="space-y-4" @submit.prevent="handleRequestExport">
            <p class="text-sm text-gray-600">
              Defina uma senha para o arquivo ZIP. Ele abre no 7-Zip, WinRAR ou Keka, e não no extrator
              nativo do Windows nem no do macOS. A senha não fica guardada: se esquecer, peça outro export.
            </p>
            <div class="space-y-2">
              <label for="export-password" class="text-sm font-medium text-gray-900">Senha do arquivo</label>
              <Input
                id="export-password"
                v-model="password"
                type="password"
                autocomplete="new-password"
                :maxlength="MAX_PASSWORD_LENGTH"
                :aria-invalid="passwordHelpError"
                aria-describedby="export-password-help"
                class="min-h-[44px]"
              />
            </div>
            <div class="space-y-2">
              <label for="export-password-confirm" class="text-sm font-medium text-gray-900">Confirmar senha</label>
              <Input
                id="export-password-confirm"
                v-model="passwordConfirm"
                type="password"
                autocomplete="new-password"
                :maxlength="MAX_PASSWORD_LENGTH"
                :aria-invalid="passwordHelpError"
                aria-describedby="export-password-help"
                class="min-h-[44px]"
              />
            </div>
            <p id="export-password-help" class="text-xs" :class="passwordHelpError ? 'text-red-600' : 'text-gray-500'" aria-live="polite">
              {{ passwordHelp }}
            </p>
            <DialogFooter>
              <Button type="button" variant="outline" class="min-h-[44px]" @click="onDialogOpenChange(false)">
                Cancelar
              </Button>
              <Button
                type="submit"
                class="min-h-[44px]"
                :disabled="isRequesting || !passwordValid"
              >
                {{ isRequesting ? 'Solicitando...' : 'Exportar' }}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  </main>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { requestExport, getExportStatus } from '@/services/dataExportService'
import type { DataExportResponse } from '@/types/dataExport'
import { api } from '@/services/api'

const router = useRouter()

const exportData = ref<DataExportResponse | null>(null)
const isLoading = ref(false)
const isRequesting = ref(false)
const error = ref<string | null>(null)
const showConfirmDialog = ref(false)
const password = ref('')
const passwordConfirm = ref('')

const MIN_PASSWORD_LENGTH = 8
const MAX_PASSWORD_LENGTH = 128
// same rule as the backend (@NotBlank + @Size(8, 128)): spaces alone do not count
const passwordTooShort = computed(
  () => password.value.length > 0 && password.value.trim().length < MIN_PASSWORD_LENGTH,
)
const passwordTooLong = computed(() => password.value.length > MAX_PASSWORD_LENGTH)
const passwordValid = computed(
  () =>
    password.value.trim().length >= MIN_PASSWORD_LENGTH &&
    !passwordTooLong.value &&
    password.value === passwordConfirm.value,
)
const passwordHelpError = computed(
  () =>
    passwordTooShort.value ||
    passwordTooLong.value ||
    (passwordConfirm.value.length > 0 && password.value !== passwordConfirm.value),
)
const passwordHelp = computed(() => {
  if (passwordTooShort.value) {
    return `A senha precisa ter pelo menos ${MIN_PASSWORD_LENGTH} caracteres.`
  }
  if (passwordTooLong.value) {
    return `A senha pode ter no máximo ${MAX_PASSWORD_LENGTH} caracteres.`
  }
  if (passwordConfirm.value.length > 0 && password.value !== passwordConfirm.value) {
    return 'As senhas não conferem.'
  }
  return `Mínimo de ${MIN_PASSWORD_LENGTH} caracteres. Repita a senha para confirmar.`
})

function onDialogOpenChange(open: boolean) {
  showConfirmDialog.value = open
  if (!open) {
    // the password lives only while the dialog is open
    password.value = ''
    passwordConfirm.value = ''
  }
}

let pollingInterval: ReturnType<typeof setInterval> | null = null

async function loadStatus() {
  isLoading.value = true
  error.value = null
  try {
    exportData.value = await getExportStatus()
    startPollingIfNeeded()
  } catch (e) {
    error.value = 'Não foi possível carregar o status da exportação. Tente novamente.'
  } finally {
    isLoading.value = false
  }
}

async function handleRequestExport() {
  if (!passwordValid.value || isRequesting.value) return
  isRequesting.value = true
  error.value = null
  try {
    exportData.value = await requestExport(password.value)
    onDialogOpenChange(false)
    startPollingIfNeeded()
  } catch (e: any) {
    if (e?.response?.status === 429) {
      error.value = 'Limite de exportação atingido. Tente novamente em 1 hora.'
    } else {
      error.value = 'Não foi possível solicitar a exportação. Tente novamente.'
    }
    onDialogOpenChange(false)
  } finally {
    isRequesting.value = false
  }
}

function handleDownload() {
  if (!exportData.value?.downloadUrl) return
  const baseUrl = api.defaults.baseURL || ''
  window.open(baseUrl + exportData.value.downloadUrl, '_blank')
  // After download, refresh status
  setTimeout(loadStatus, 2000)
}

function startPollingIfNeeded() {
  stopPolling()
  if (exportData.value?.status === 'PROCESSING') {
    pollingInterval = setInterval(async () => {
      try {
        exportData.value = await getExportStatus()
        if (exportData.value?.status !== 'PROCESSING') {
          stopPolling()
        }
      } catch {
        // Ignore polling errors
      }
    }, 5000)
  }
}

function stopPolling() {
  if (pollingInterval) {
    clearInterval(pollingInterval)
    pollingInterval = null
  }
}

function formatDate(isoString: string): string {
  const date = new Date(isoString)
  return date.toLocaleString('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function goBack() {
  router.push('/dashboard')
}

onMounted(loadStatus)
onUnmounted(stopPolling)
</script>
