<template>
  <main class="min-h-screen bg-gray-50 px-4 py-8">
    <div class="mx-auto max-w-3xl space-y-6">
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-semibold text-gray-900">Exportar meus dados</h1>
          <p class="mt-1 text-sm text-gray-600">
            Exporte todos os seus dados em formato JSON (direito LGPD)
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
      <div v-if="isLoading" class="flex items-center justify-center py-12" role="status" aria-label="Carregando status da exportacao">
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
            <CardTitle class="text-lg font-semibold text-gray-900">Solicitar exportacao</CardTitle>
          </CardHeader>
          <CardContent class="space-y-4">
            <p class="text-sm text-gray-600">
              Voce pode exportar todos os seus dados pessoais armazenados no Porquinho.
              A exportacao inclui seu perfil, sessoes e registros de atividade.
            </p>
            <p class="text-sm text-gray-500">
              O arquivo sera gerado em formato JSON e ficara disponivel para download por 24 horas.
            </p>
            <Button
              class="min-h-[44px]"
              :disabled="isRequesting"
              @click="showConfirmDialog = true"
              aria-label="Solicitar exportacao de dados"
            >
              Exportar Meus Dados
            </Button>
          </CardContent>
        </Card>

        <!-- Processing state -->
        <Card v-else-if="exportData.status === 'PROCESSING'" class="bg-white">
          <CardHeader>
            <CardTitle class="text-lg font-semibold text-gray-900">Exportacao em andamento</CardTitle>
          </CardHeader>
          <CardContent class="space-y-4">
            <div class="flex items-center gap-3">
              <div class="h-5 w-5 animate-spin rounded-full border-2 border-purple-200 border-t-purple-600" />
              <p class="text-sm text-gray-600">
                Seus dados estao sendo coletados e preparados para download...
              </p>
            </div>
            <p class="text-xs text-gray-500">
              Solicitado em: {{ formatDate(exportData.createdAt) }}
            </p>
          </CardContent>
        </Card>

        <!-- Ready state -->
        <Card v-else-if="exportData.status === 'READY'" class="border-green-200 bg-green-50">
          <CardHeader>
            <CardTitle class="text-lg font-semibold text-green-900">Exportacao pronta!</CardTitle>
          </CardHeader>
          <CardContent class="space-y-4">
            <p class="text-sm text-green-700">
              Seu arquivo de dados esta pronto para download.
            </p>
            <p class="text-xs text-gray-600">
              Expira em: {{ formatDate(exportData.expiresAt!) }}
            </p>
            <Button
              class="min-h-[44px]"
              @click="handleDownload"
              aria-label="Baixar exportacao de dados"
            >
              Baixar arquivo
            </Button>
            <p class="text-xs text-gray-500">
              O link expira apos 24 horas ou apos o primeiro download.
            </p>
          </CardContent>
        </Card>
      </template>

      <!-- Confirm dialog -->
      <Dialog :open="showConfirmDialog" @update:open="showConfirmDialog = $event">
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Confirmar exportacao de dados</DialogTitle>
            <DialogDescription>
              Deseja exportar todos os seus dados pessoais? O arquivo sera gerado em formato JSON e ficara disponivel por 24 horas.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" class="min-h-[44px]" @click="showConfirmDialog = false">
              Cancelar
            </Button>
            <Button
              class="min-h-[44px]"
              :disabled="isRequesting"
              @click="handleRequestExport"
            >
              {{ isRequesting ? 'Solicitando...' : 'Confirmar' }}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  </main>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
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

let pollingInterval: ReturnType<typeof setInterval> | null = null

async function loadStatus() {
  isLoading.value = true
  error.value = null
  try {
    exportData.value = await getExportStatus()
    startPollingIfNeeded()
  } catch (e) {
    error.value = 'Nao foi possivel carregar o status da exportacao. Tente novamente.'
  } finally {
    isLoading.value = false
  }
}

async function handleRequestExport() {
  isRequesting.value = true
  error.value = null
  try {
    exportData.value = await requestExport()
    showConfirmDialog.value = false
    startPollingIfNeeded()
  } catch (e: any) {
    if (e?.response?.status === 429) {
      error.value = 'Limite de exportacao atingido. Tente novamente em 1 hora.'
    } else {
      error.value = 'Nao foi possivel solicitar a exportacao. Tente novamente.'
    }
    showConfirmDialog.value = false
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
