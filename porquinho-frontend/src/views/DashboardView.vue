<template>
  <main class="min-h-screen bg-gray-50 px-4 py-8">
    <div class="mx-auto max-w-6xl space-y-8">
      <FinancialHealthSemaphore :key="healthKey" />

      <div class="flex items-start justify-between gap-4">
        <div>
          <div class="flex flex-wrap items-center gap-3">
            <h1 class="text-2xl font-semibold text-gray-900">Dashboard</h1>
            <TrialBadge v-if="subscription" :subscription="subscription" />
            <RouterLink
              v-if="showSubscribeCta"
              to="/assinatura"
              data-testid="subscribe-cta"
              class="text-sm font-medium text-gray-700 underline hover:text-gray-900"
            >
              {{ isFormerSubscriber ? 'Reativar' : 'Assinar' }}
            </RouterLink>
          </div>
        </div>
        <button
          type="button"
          class="inline-flex min-h-[44px] items-center rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100"
          @click="logout"
        >
          Sair
        </button>
      </div>

      <!-- Story 3.8: fim do onboarding (sem confete: só a mensagem) -->
      <div
        v-if="showOnboardingDone"
        role="status"
        data-testid="onboarding-done-message"
        class="flex items-start justify-between gap-3 rounded-lg border border-green-200 bg-green-50 px-4 py-3"
      >
        <div class="flex items-start gap-3 text-sm text-green-800">
          <PartyPopper class="mt-0.5 h-5 w-5 shrink-0 text-green-600" aria-hidden="true" />
          <div>
            <p class="font-semibold">Parabéns! Sua conta está configurada</p>
            <p>Agora é só acompanhar seu dinheiro por aqui.</p>
          </div>
        </div>
        <button
          type="button"
          data-testid="onboarding-done-close"
          class="inline-flex min-h-[44px] min-w-[44px] shrink-0 items-center justify-center rounded-lg text-green-800 hover:bg-green-100"
          aria-label="Fechar mensagem"
          @click="showOnboardingDone = false"
        >
          <X class="h-4 w-4" aria-hidden="true" />
        </button>
      </div>

      <div
        v-if="subscription?.status === 'PAYMENT_FAILED'"
        role="alert"
        data-testid="payment-failed-banner"
        class="flex flex-col gap-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3 sm:flex-row sm:items-center sm:justify-between"
      >
        <div class="text-sm text-red-800">
          <p class="font-semibold">Pagamento falhou</p>
          <p>
            Não conseguimos cobrar sua mensalidade. Vamos tentar de novo nos próximos dias; atualize o
            pagamento para não perder o acesso.
          </p>
        </div>
        <!-- Abre direto a fatura no Stripe (cartão salvo ou novo); sem link, cai na tela de assinatura -->
        <button
          v-if="subscription.paymentUpdateUrl"
          type="button"
          data-testid="payment-failed-cta"
          class="inline-flex min-h-[44px] shrink-0 items-center justify-center rounded-lg bg-red-700 px-4 py-2 text-sm font-medium text-white hover:bg-red-800"
          @click="updatePayment"
        >
          Atualizar pagamento
        </button>
        <RouterLink
          v-else
          to="/assinatura"
          data-testid="payment-failed-cta"
          class="inline-flex min-h-[44px] shrink-0 items-center justify-center rounded-lg bg-red-700 px-4 py-2 text-sm font-medium text-white hover:bg-red-800"
        >
          Atualizar pagamento
        </RouterLink>
      </div>

      <!-- Story 7.6 (Q1=B): editar na Camada 3 recarrega o semáforo -->
      <DashboardGps @transaction-saved="healthKey++" />

      <section aria-labelledby="account-settings-heading" class="space-y-3">
        <h2
          id="account-settings-heading"
          class="text-lg font-semibold text-gray-900"
        >
          Configuracoes da conta
        </h2>
        <nav aria-label="Configuracoes da conta" class="flex flex-col gap-3 sm:flex-row sm:flex-wrap">
          <RouterLink
            to="/contas"
            data-testid="accounts-link"
            class="inline-flex min-h-[44px] items-center rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100"
          >
            Contas bancárias
          </RouterLink>
          <RouterLink
            to="/cartoes"
            data-testid="cards-link"
            class="inline-flex min-h-[44px] items-center rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100"
          >
            Cartões de crédito
          </RouterLink>
          <RouterLink
            to="/importar"
            data-testid="import-link"
            class="inline-flex min-h-[44px] items-center rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100"
          >
            Importar extrato
          </RouterLink>
          <RouterLink
            to="/transacoes"
            data-testid="transactions-link"
            class="inline-flex min-h-[44px] items-center rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100"
          >
            Transações
          </RouterLink>
          <RouterLink
            to="/categorias"
            data-testid="categories-link"
            class="inline-flex min-h-[44px] items-center rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100"
          >
            Categorias
          </RouterLink>
          <RouterLink
            to="/orcamento"
            data-testid="budget-link"
            class="inline-flex min-h-[44px] items-center rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100"
          >
            Orçamento
          </RouterLink>
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

<script lang="ts">
// Story 7.1: fora do setup para sobreviver à desmontagem da view
let savedScrollY = 0
</script>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { onBeforeRouteLeave, RouterLink, useRoute, useRouter } from 'vue-router'
import { PartyPopper, X } from 'lucide-vue-next'
import TrialBadge from '@/components/subscription/TrialBadge.vue'
import DashboardGps from '@/components/dashboard/DashboardGps.vue'
import FinancialHealthSemaphore from '@/components/dashboard/FinancialHealthSemaphore.vue'
import { getMySubscription } from '@/services/subscriptionService'
import { supabase } from '@/services/supabase'
import { useAuthStore } from '@/stores/auth'
import type { Subscription } from '@/types/subscription'

const subscription = ref<Subscription | null>(null)
const healthKey = ref(0)
// Trial, ou assinatura expirada/suspensa/cancelada já sem acesso (Stories 2.4 e 2.7)
const showSubscribeCta = computed(() => {
  const sub = subscription.value
  if (!sub) return false
  return (
    sub.planType === 'TRIAL' ||
    sub.status === 'EXPIRED' ||
    sub.status === 'SUSPENDED' ||
    (sub.status === 'CANCELLED' && !sub.hasAccess)
  )
})
// Story 2.5: ex-assinante (expirada, suspensa ou cancelada sem acesso) vê "Reativar"
const isFormerSubscriber = computed(
  () => subscription.value?.planType === 'MONTHLY' && showSubscribeCta.value,
)

// Story 2.7: página da fatura que falhou no Stripe; o webhook invoice.paid reativa a assinatura
function updatePayment() {
  const url = subscription.value?.paymentUpdateUrl
  if (url) window.location.assign(url)
}
const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

// Story 3.8: o onboarding acabou de ser concluído (/dashboard?onboarding=done)
const showOnboardingDone = ref(false)
if (route.query.onboarding === 'done') {
  showOnboardingDone.value = true
  // Limpa o parâmetro: recarregar a página não repete a mensagem
  const query = { ...route.query }
  delete query.onboarding
  void router.replace({ path: route.path, query })
}

async function logout() {
  // O guard do router usa a chave 'user' do localStorage: limpar primeiro garante a ida ao /login
  authStore.clearUser()
  try {
    await supabase.auth.signOut()
  } catch (err) {
    console.warn('Supabase signOut failed', err)
  }
  await router.push('/login')
  // A saída disparou o leave guard: o próximo login na mesma aba abre no topo
  savedScrollY = 0
}

// Story 7.1: volta na mesma altura ao retornar ao dashboard; F5 recarrega o módulo e zera
onBeforeRouteLeave(() => {
  savedScrollY = window.scrollY
})

onMounted(async () => {
  try {
    subscription.value = await getMySubscription()
  } catch {
    // AC5: falha ou 404 esconde o badge, o dashboard continua funcionando
    subscription.value = null
  }
  // Restaura depois do banner de pagamento, que muda a altura da página
  await nextTick()
  window.scrollTo({ top: savedScrollY, behavior: 'instant' })
})
</script>
