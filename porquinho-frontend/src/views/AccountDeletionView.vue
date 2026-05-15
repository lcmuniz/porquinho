<template>
  <main class="min-h-screen bg-gray-50 px-4 py-8">
    <div class="mx-auto max-w-3xl space-y-6">
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-semibold text-gray-900">Excluir minha conta</h1>
          <p class="mt-1 text-sm text-gray-600">
            Solicite a exclusao permanente da sua conta e dos seus dados (direito LGPD)
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

      <!-- Error state -->
      <Card v-if="error" class="border-red-200 bg-red-50" role="alert">
        <CardContent class="p-6 text-center">
          <p class="text-red-700">{{ error }}</p>
        </CardContent>
      </Card>

      <!-- Main warning card -->
      <Card class="border-red-200 bg-white">
        <CardHeader>
          <CardTitle class="text-lg font-semibold text-red-900">
            Excluir minha conta permanentemente
          </CardTitle>
        </CardHeader>
        <CardContent class="space-y-4">
          <p class="text-sm text-gray-700">
            Esta acao iniciara o processo de exclusao definitiva da sua conta. Os seguintes dados serao removidos:
          </p>
          <ul class="ml-5 list-disc space-y-1 text-sm text-gray-700">
            <li>Seu perfil de usuario (email, autenticacao)</li>
            <li>Todas as suas sessoes ativas (voce sera deslogado de todos os dispositivos)</li>
            <li>Seu historico de auditoria</li>
            <li>Todas as suas exportacoes de dados, incluindo arquivos disponiveis para download</li>
          </ul>
          <p class="rounded-md bg-amber-50 p-3 text-sm text-amber-800">
            Sua conta ficara em periodo de retencao por <strong>30 dias</strong> antes da exclusao definitiva.
            Durante este periodo voce nao podera fazer login.
          </p>
          <p class="text-sm font-semibold text-red-700">
            Apos os 30 dias, a exclusao sera definitiva e nao havera como recuperar a conta.
          </p>
          <Button
            variant="destructive"
            class="min-h-[44px]"
            @click="openConfirmDialog"
            aria-label="Iniciar processo de exclusao da conta"
          >
            Excluir minha conta
          </Button>
        </CardContent>
      </Card>

      <!-- Confirm dialog -->
      <Dialog :open="showConfirmDialog" @update:open="onDialogOpenChange">
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Confirmar exclusao de conta</DialogTitle>
            <DialogDescription>
              Esta acao nao pode ser desfeita apos os 30 dias de retencao. Para confirmar,
              digite a frase abaixo exatamente como aparece.
            </DialogDescription>
          </DialogHeader>
          <div class="space-y-3">
            <label for="confirm-input" class="block text-sm font-medium text-gray-700">
              Digite <span class="font-mono font-semibold">EXCLUIR MINHA CONTA</span> para confirmar:
            </label>
            <Input
              id="confirm-input"
              ref="confirmInputRef"
              v-model="confirmText"
              placeholder="EXCLUIR MINHA CONTA"
              autocomplete="off"
              :disabled="isSubmitting"
              aria-required="true"
            />
          </div>
          <DialogFooter>
            <Button
              variant="outline"
              class="min-h-[44px]"
              :disabled="isSubmitting"
              @click="closeConfirmDialog"
            >
              Cancelar
            </Button>
            <Button
              variant="destructive"
              class="min-h-[44px]"
              :disabled="!isConfirmTextValid || isSubmitting"
              @click="handleConfirmDeletion"
              aria-label="Confirmar exclusao definitiva da conta"
            >
              {{ isSubmitting ? 'Excluindo...' : 'Confirmar exclusao' }}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  </main>
</template>

<script setup lang="ts">
import { ref, computed, nextTick } from 'vue'
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
import { Input } from '@/components/ui/input'
import { requestDeletion } from '@/services/accountDeletionService'
import { useAuthStore } from '@/stores/auth'
import { supabase } from '@/services/supabase'

const CONFIRM_PHRASE = 'EXCLUIR MINHA CONTA'

const router = useRouter()
const authStore = useAuthStore()

const showConfirmDialog = ref(false)
const confirmText = ref('')
const isSubmitting = ref(false)
const error = ref<string | null>(null)
const confirmInputRef = ref<HTMLInputElement | null>(null)

const isConfirmTextValid = computed(() => confirmText.value.trim() === CONFIRM_PHRASE)

function goBack() {
  router.push('/dashboard')
}

async function openConfirmDialog() {
  error.value = null
  confirmText.value = ''
  showConfirmDialog.value = true
  // Focus the input on next tick once the dialog has rendered.
  await nextTick()
  const inputEl = document.getElementById('confirm-input') as HTMLInputElement | null
  inputEl?.focus()
}

function closeConfirmDialog() {
  if (isSubmitting.value) return
  showConfirmDialog.value = false
  confirmText.value = ''
}

function onDialogOpenChange(open: boolean) {
  if (!open) {
    closeConfirmDialog()
  } else {
    showConfirmDialog.value = true
  }
}

async function handleConfirmDeletion() {
  if (!isConfirmTextValid.value || isSubmitting.value) return
  isSubmitting.value = true
  error.value = null
  try {
    await requestDeletion()
    // Logout sequence (order matters):
    // 1. Clear local Pinia state and 'user' from localStorage immediately.
    authStore.clearUser()
    // 2. Leave the protected route before any async work that could fail.
    //    LoginView will display a confirmation message via the ?msg query param.
    await router.push({ path: '/login', query: { msg: 'account-deletion-scheduled' } })
    // 3. Fire-and-forget Supabase sign out so a slow/failed signOut does not block redirect.
    //    Defensive cleanup of Supabase storage if the signOut call fails (e.g. network down):
    //    without this, supabase.auth.getSession() on next visit may resurrect a stale session.
    supabase.auth.signOut().catch((signOutErr) => {
      console.warn('Supabase signOut failed; clearing local Supabase storage as fallback', signOutErr)
      try {
        const keys: string[] = []
        for (let i = 0; i < localStorage.length; i++) {
          const k = localStorage.key(i)
          if (k && k.startsWith('sb-')) keys.push(k)
        }
        keys.forEach((k) => localStorage.removeItem(k))
      } catch {
        // localStorage unavailable (private mode, etc.) - acceptable, user is already routed away.
      }
    })
  } catch (e: any) {
    if (e?.response?.status === 429) {
      error.value = 'Limite atingido. Tente novamente em 24 horas.'
    } else if (e?.response?.status === 404) {
      error.value = 'Sua conta nao foi encontrada. Atualize a pagina.'
    } else {
      error.value = 'Nao foi possivel processar sua solicitacao. Tente novamente.'
    }
    showConfirmDialog.value = false
  } finally {
    isSubmitting.value = false
  }
}
</script>
