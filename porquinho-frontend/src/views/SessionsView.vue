<template>
  <main class="min-h-screen bg-gray-50 px-4 py-8">
    <div class="mx-auto max-w-3xl space-y-6">
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-semibold text-gray-900">Sessoes ativas</h1>
          <p class="mt-1 text-sm text-gray-600">
            Gerencie os dispositivos conectados a sua conta
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
      <div v-if="isLoading" class="flex items-center justify-center py-12" role="status" aria-label="Carregando sessoes">
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
            @click="loadSessions"
          >
            Tentar novamente
          </Button>
        </CardContent>
      </Card>

      <!-- Empty state -->
      <Card v-else-if="sessions.length === 0 && !isLoading" class="bg-white">
        <CardContent class="p-6 text-center">
          <p class="text-gray-600">Nenhuma sessao ativa encontrada.</p>
        </CardContent>
      </Card>

      <!-- Sessions list -->
      <template v-else>
        <div class="flex justify-end">
          <Button
            v-if="otherSessions.length > 0"
            variant="destructive"
            class="min-h-[44px]"
            @click="showRevokeAllDialog = true"
          >
            Encerrar todas as outras sessoes
          </Button>
        </div>

        <div class="grid gap-4 sm:grid-cols-1 lg:grid-cols-2">
          <Card
            v-for="session in sessions"
            :key="session.sessionId"
            class="bg-white"
            :class="{ 'ring-2 ring-purple-300': session.current }"
          >
            <CardHeader class="pb-2">
              <div class="flex items-center justify-between">
                <CardTitle class="text-base font-semibold text-gray-900">
                  {{ parseDeviceInfo(session.deviceInfo) }}
                </CardTitle>
                <span
                  v-if="session.current"
                  class="inline-flex items-center rounded-full bg-green-100 px-2.5 py-0.5 text-xs font-medium text-green-800"
                  role="status"
                >
                  Dispositivo atual
                </span>
              </div>
            </CardHeader>
            <CardContent class="space-y-2">
              <div class="text-sm text-gray-600">
                <p>IP: {{ session.ipAddress || 'Desconhecido' }}</p>
                <p>Ultimo acesso: {{ formatDate(session.lastActivity) }}</p>
                <p>Criada em: {{ formatDate(session.createdAt) }}</p>
              </div>
              <Button
                v-if="!session.current"
                variant="outline"
                size="sm"
                class="mt-2 min-h-[44px] text-red-600 hover:bg-red-50 hover:text-red-700"
                @click="sessionToRevoke = session; showRevokeDialog = true"
              >
                Encerrar sessao
              </Button>
            </CardContent>
          </Card>
        </div>
      </template>

      <!-- Revoke single session dialog -->
      <Dialog :open="showRevokeDialog" @update:open="showRevokeDialog = $event">
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Encerrar sessao</DialogTitle>
            <DialogDescription>
              Tem certeza que deseja encerrar esta sessao? O dispositivo sera desconectado imediatamente.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" class="min-h-[44px]" @click="showRevokeDialog = false">
              Cancelar
            </Button>
            <Button
              variant="destructive"
              class="min-h-[44px]"
              :disabled="isRevoking"
              @click="handleRevokeSession"
            >
              {{ isRevoking ? 'Encerrando...' : 'Confirmar' }}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <!-- Revoke all sessions dialog -->
      <Dialog :open="showRevokeAllDialog" @update:open="showRevokeAllDialog = $event">
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Encerrar todas as outras sessoes</DialogTitle>
            <DialogDescription>
              Tem certeza que deseja encerrar todas as outras sessoes? Todos os outros dispositivos serao desconectados imediatamente.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" class="min-h-[44px]" @click="showRevokeAllDialog = false">
              Cancelar
            </Button>
            <Button
              variant="destructive"
              class="min-h-[44px]"
              :disabled="isRevoking"
              @click="handleRevokeAllOther"
            >
              {{ isRevoking ? 'Encerrando...' : 'Confirmar' }}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  </main>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
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
import { getSessions, revokeSession, revokeAllOtherSessions } from '@/services/sessionService'
import type { Session } from '@/types/session'

const router = useRouter()

const sessions = ref<Session[]>([])
const isLoading = ref(false)
const isRevoking = ref(false)
const error = ref<string | null>(null)
const showRevokeDialog = ref(false)
const showRevokeAllDialog = ref(false)
const sessionToRevoke = ref<Session | null>(null)

const otherSessions = computed(() => sessions.value.filter((s) => !s.current))

async function loadSessions() {
  isLoading.value = true
  error.value = null
  try {
    sessions.value = await getSessions()
  } catch (e) {
    error.value = 'Nao foi possivel carregar as sessoes. Tente novamente.'
  } finally {
    isLoading.value = false
  }
}

async function handleRevokeSession() {
  if (!sessionToRevoke.value) return
  isRevoking.value = true
  try {
    await revokeSession(sessionToRevoke.value.sessionId)
    sessions.value = sessions.value.filter((s) => s.sessionId !== sessionToRevoke.value!.sessionId)
    showRevokeDialog.value = false
    sessionToRevoke.value = null
  } catch (e) {
    error.value = 'Nao foi possivel encerrar a sessao. Tente novamente.'
  } finally {
    isRevoking.value = false
  }
}

async function handleRevokeAllOther() {
  isRevoking.value = true
  try {
    await revokeAllOtherSessions()
    sessions.value = sessions.value.filter((s) => s.current)
    showRevokeAllDialog.value = false
  } catch (e) {
    error.value = 'Nao foi possivel encerrar as sessoes. Tente novamente.'
  } finally {
    isRevoking.value = false
  }
}

function parseDeviceInfo(deviceInfo: string | null): string {
  if (!deviceInfo) return 'Dispositivo desconhecido'
  // Simple parsing of User-Agent string (order matters: specific before generic)
  if (deviceInfo.includes('OPR') || deviceInfo.includes('Opera')) return 'Opera'
  if (deviceInfo.includes('Edg')) return 'Microsoft Edge'
  if (deviceInfo.includes('Chrome')) return 'Google Chrome'
  if (deviceInfo.includes('Firefox')) return 'Mozilla Firefox'
  if (deviceInfo.includes('Safari')) return 'Safari'
  return deviceInfo.length > 50 ? deviceInfo.substring(0, 50) + '...' : deviceInfo
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

onMounted(loadSessions)
</script>
