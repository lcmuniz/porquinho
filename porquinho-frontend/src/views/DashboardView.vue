<template>
  <main class="min-h-screen bg-gray-50 px-4 py-8">
    <div class="mx-auto max-w-3xl space-y-8">
      <div class="flex items-start justify-between gap-4">
        <div>
          <div class="flex flex-wrap items-center gap-3">
            <h1 class="text-2xl font-semibold text-gray-900">Dashboard</h1>
            <TrialBadge v-if="subscription" :subscription="subscription" />
            <RouterLink
              v-if="subscription?.planType === 'TRIAL'"
              to="/assinatura"
              data-testid="subscribe-cta"
              class="text-sm font-medium text-gray-700 underline hover:text-gray-900"
            >
              Assinar
            </RouterLink>
          </div>
          <p class="text-gray-600">Em desenvolvimento — Epic 7</p>
        </div>
        <button
          type="button"
          class="inline-flex min-h-[44px] items-center rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100"
          @click="logout"
        >
          Sair
        </button>
      </div>

      <section aria-labelledby="account-settings-heading" class="space-y-3">
        <h2
          id="account-settings-heading"
          class="text-lg font-semibold text-gray-900"
        >
          Configuracoes da conta
        </h2>
        <nav aria-label="Configuracoes da conta" class="flex flex-col gap-3 sm:flex-row sm:flex-wrap">
          <RouterLink
            to="/auth/sessions"
            class="inline-flex min-h-[44px] items-center rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100"
          >
            Sessoes ativas
          </RouterLink>
          <RouterLink
            to="/auth/data-export"
            class="inline-flex min-h-[44px] items-center rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100"
          >
            Exportar meus dados
          </RouterLink>
        </nav>
      </section>

      <section aria-labelledby="danger-zone-heading" class="space-y-3">
        <h2
          id="danger-zone-heading"
          class="text-lg font-semibold text-red-700"
        >
          Zona de perigo
        </h2>
        <nav aria-label="Zona de perigo" class="flex flex-col gap-3 sm:flex-row sm:flex-wrap">
          <RouterLink
            to="/auth/account-deletion"
            class="inline-flex min-h-[44px] items-center rounded-lg border border-red-300 px-4 py-2 text-sm font-medium text-red-700 hover:bg-red-50"
          >
            Excluir minha conta
          </RouterLink>
        </nav>
      </section>
    </div>
  </main>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import TrialBadge from '@/components/subscription/TrialBadge.vue'
import { getMySubscription } from '@/services/subscriptionService'
import { supabase } from '@/services/supabase'
import { useAuthStore } from '@/stores/auth'
import type { Subscription } from '@/types/subscription'

const subscription = ref<Subscription | null>(null)
const router = useRouter()
const authStore = useAuthStore()

async function logout() {
  // O guard do router usa a chave 'user' do localStorage: limpar primeiro garante a ida ao /login
  authStore.clearUser()
  try {
    await supabase.auth.signOut()
  } catch (err) {
    console.warn('Supabase signOut failed', err)
  }
  await router.push('/login')
}

onMounted(async () => {
  try {
    subscription.value = await getMySubscription()
  } catch {
    // AC5: falha ou 404 esconde o badge, o dashboard continua funcionando
    subscription.value = null
  }
})
</script>
