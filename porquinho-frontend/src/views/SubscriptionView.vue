<template>
  <main class="min-h-screen bg-gray-50 px-4 py-8">
    <div class="mx-auto max-w-xl space-y-6">
      <RouterLink to="/dashboard" class="text-sm text-gray-600 underline hover:text-gray-900">
        Voltar ao dashboard
      </RouterLink>

      <h1 class="text-2xl font-semibold text-gray-900">Assinatura</h1>

      <p
        v-if="returnStatus === 'sucesso'"
        role="status"
        data-testid="checkout-success"
        class="rounded-lg bg-green-100 px-4 py-3 text-sm text-green-800"
      >
        Assinatura confirmada!
        <template v-if="subscription?.planType === 'TRIAL'">
          A primeira cobrança acontece no fim do trial.
        </template>
      </p>
      <p
        v-else-if="returnStatus === 'cancelado'"
        role="status"
        data-testid="checkout-cancelled"
        class="rounded-lg bg-yellow-100 px-4 py-3 text-sm text-yellow-800"
      >
        Assinatura não concluída.
        <template v-if="subscription?.hasAccess">Nada foi cobrado e seu trial continua valendo.</template>
      </p>

      <section
        aria-labelledby="plan-heading"
        class="space-y-4 rounded-lg border border-gray-200 bg-white p-6"
      >
        <h2 id="plan-heading" class="text-lg font-semibold text-gray-900">Plano mensal</h2>
        <p class="text-3xl font-semibold text-gray-900" data-testid="plan-price">
          R$ 19,90<span class="text-base font-normal text-gray-600">/mês</span>
        </p>
        <p class="text-gray-600">
          Acesso completo ao Porquinho. Pagamento com cartão, renovação automática todo mês.
        </p>

        <TrialBadge v-if="subscription" :subscription="subscription" />

        <p v-if="isActiveMonthly" data-testid="already-subscribed" class="text-gray-700">
          Sua assinatura está ativa. Obrigado por apoiar o Porquinho!
        </p>
        <template v-else-if="returnStatus !== 'sucesso'">
          <p v-if="inTrial" class="text-sm text-gray-600">
            Assinando agora, você mantém os dias grátis do trial: a primeira cobrança só acontece
            quando ele terminar.
          </p>
          <button
            type="button"
            data-testid="subscribe-button"
            class="inline-flex min-h-[44px] items-center rounded-lg bg-gray-900 px-6 py-2 text-sm font-medium text-white hover:bg-gray-700 disabled:opacity-60"
            :disabled="loading"
            @click="subscribe"
          >
            {{ loading ? 'Redirecionando...' : 'Assinar' }}
          </button>
        </template>

        <p v-if="error" role="alert" data-testid="checkout-error" class="text-sm text-red-700">
          {{ error }}
        </p>
      </section>
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import TrialBadge from '@/components/subscription/TrialBadge.vue'
import { createCheckout, getMySubscription } from '@/services/subscriptionService'
import type { Subscription } from '@/types/subscription'

// Após o retorno do Checkout o webhook pode levar alguns segundos: recarrega até virar MONTHLY
const POLL_INTERVAL_MS = 2000
const POLL_MAX_ATTEMPTS = 5

const route = useRoute()
const subscription = ref<Subscription | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

const returnStatus = computed(() => route.query.status)
const isActiveMonthly = computed(
  () => subscription.value?.planType === 'MONTHLY' && subscription.value.status === 'ACTIVE',
)
const inTrial = computed(
  () => subscription.value?.planType === 'TRIAL' && subscription.value.hasAccess,
)

async function subscribe() {
  loading.value = true
  error.value = null
  try {
    const { url } = await createCheckout()
    window.location.assign(url)
  } catch (err) {
    const status = (err as { response?: { status?: number } }).response?.status
    error.value =
      status === 409
        ? 'Você já possui uma assinatura ativa.'
        : 'Pagamento indisponível no momento. Tente novamente em alguns minutos.'
    loading.value = false
  }
}

let pollTimer: ReturnType<typeof setTimeout> | null = null

async function loadSubscription() {
  try {
    subscription.value = await getMySubscription()
  } catch {
    subscription.value = null
  }
}

function pollUntilMonthly(attempt = 1) {
  if (subscription.value?.planType === 'MONTHLY' || attempt > POLL_MAX_ATTEMPTS) return
  pollTimer = setTimeout(async () => {
    await loadSubscription()
    pollUntilMonthly(attempt + 1)
  }, POLL_INTERVAL_MS)
}

onMounted(async () => {
  await loadSubscription()
  if (returnStatus.value === 'sucesso') pollUntilMonthly()
})

onUnmounted(() => {
  if (pollTimer) clearTimeout(pollTimer)
})
</script>
