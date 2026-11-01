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

        <template v-if="isActiveMonthly">
          <p data-testid="already-subscribed" class="text-gray-700">
            Sua assinatura está ativa. Obrigado por apoiar o Porquinho!
          </p>
          <Button
            variant="outline"
            class="min-h-[44px]"
            data-testid="cancel-button"
            @click="openCancelDialog"
          >
            Cancelar assinatura
          </Button>
        </template>
        <template v-else-if="isPaymentFailed">
          <p role="alert" data-testid="payment-failed-notice" class="text-gray-700">
            Não conseguimos cobrar sua última mensalidade. Vamos tentar de novo automaticamente e seu
            acesso continua enquanto isso.
          </p>
          <p class="text-sm text-gray-600">
            Para resolver agora, pague a fatura no Stripe com o cartão cadastrado ou com outro cartão.
          </p>
          <div class="flex flex-col gap-3 sm:flex-row">
            <Button
              v-if="subscription?.paymentUpdateUrl"
              class="min-h-[44px]"
              data-testid="update-payment-button"
              @click="updatePayment"
            >
              Atualizar pagamento
            </Button>
            <Button
              variant="outline"
              class="min-h-[44px]"
              data-testid="cancel-button"
              @click="openCancelDialog"
            >
              Cancelar assinatura
            </Button>
          </div>
        </template>
        <template v-else-if="isCancelledWithAccess">
          <p role="status" data-testid="cancelled-notice" class="text-gray-700">
            Assinatura cancelada — ativa até {{ accessUntil }}. Nenhuma nova cobrança será feita.
          </p>
          <p class="text-sm text-gray-600">
            Reativando agora, nada é cobrado hoje: a próxima cobrança acontece em {{ accessUntil }}.
          </p>
          <Button
            class="min-h-[44px]"
            data-testid="reactivate-button"
            :disabled="reactivating"
            @click="reactivate"
          >
            {{ reactivating ? 'Reativando...' : 'Reativar assinatura' }}
          </Button>
        </template>
        <template v-else-if="returnStatus !== 'sucesso'">
          <p
            v-if="cancelledNow"
            role="status"
            data-testid="cancelled-now-notice"
            class="text-gray-700"
          >
            Assinatura cancelada. Seu acesso foi encerrado e nenhuma nova cobrança será feita.
          </p>
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
            {{ loading ? 'Redirecionando...' : isFormerSubscriber ? 'Reativar assinatura' : 'Assinar' }}
          </button>
        </template>

        <p v-if="error" role="alert" data-testid="checkout-error" class="text-sm text-red-700">
          {{ error }}
        </p>
        <p v-if="cancelError" role="alert" data-testid="cancel-error" class="text-sm text-red-700">
          {{ cancelError }}
        </p>
        <p
          v-if="reactivateError"
          role="alert"
          data-testid="reactivate-error"
          class="text-sm text-red-700"
        >
          {{ reactivateError }}
        </p>
      </section>

      <Dialog v-model:open="showCancelDialog">
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Cancelar assinatura?</DialogTitle>
            <DialogDescription data-testid="cancel-dialog-description">
              <template v-if="isPaymentFailed">
                Tem certeza? Como o último pagamento falhou, o acesso termina agora. Não haverá novas
                tentativas de cobrança.
              </template>
              <template v-else>
                Tem certeza? Você perde o acesso ao fim do período atual ({{ accessUntil }}). Nenhuma
                nova cobrança será feita.
              </template>
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              variant="outline"
              class="min-h-[44px]"
              :disabled="cancelling"
              @click="showCancelDialog = false"
            >
              Manter assinatura
            </Button>
            <Button
              variant="destructive"
              class="min-h-[44px]"
              data-testid="confirm-cancel-button"
              :disabled="cancelling"
              @click="confirmCancel"
            >
              {{ cancelling ? 'Cancelando...' : 'Cancelar assinatura' }}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import TrialBadge from '@/components/subscription/TrialBadge.vue'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { formatDateBR } from '@/lib/date'
import {
  cancelSubscription,
  createCheckout,
  getMySubscription,
  reactivateSubscription,
} from '@/services/subscriptionService'
import type { Subscription } from '@/types/subscription'

// Após o retorno do Checkout o webhook pode levar alguns segundos: recarrega até virar MONTHLY/ACTIVE
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
// Story 2.4: cancelada e ainda dentro do período pago (Story 2.5: reativa sem Checkout)
const isCancelledWithAccess = computed(
  () =>
    subscription.value?.planType === 'MONTHLY' &&
    subscription.value.status === 'CANCELLED' &&
    subscription.value.hasAccess,
)
// Story 2.7: cobrança recusada; o Stripe tenta de novo e o usuário pode pagar a fatura agora
const isPaymentFailed = computed(() => subscription.value?.status === 'PAYMENT_FAILED')
// Story 2.5: já foi assinante e perdeu o acesso (expirada, suspensa, ou cancelada com o período
// encerrado): volta pelo Checkout, com o rótulo "Reativar assinatura"
const isFormerSubscriber = computed(
  () =>
    subscription.value?.planType === 'MONTHLY' &&
    subscription.value.status !== 'ACTIVE' &&
    !isPaymentFailed.value &&
    !isCancelledWithAccess.value,
)

// Página da fatura no Stripe: paga na hora (cartão salvo ou novo); o webhook invoice.paid reativa
function updatePayment() {
  const url = subscription.value?.paymentUpdateUrl
  if (url) window.location.assign(url)
}
const accessUntil = computed(() => formatDateBR(subscription.value?.nextBillingDate))

const showCancelDialog = ref(false)
const cancelling = ref(false)
const cancelError = ref<string | null>(null)
// Story 2.7: cancelada durante PAYMENT_FAILED, com o acesso encerrado na hora (vira EXPIRED)
const cancelledNow = ref(false)

function openCancelDialog() {
  cancelError.value = null
  showCancelDialog.value = true
}

async function confirmCancel() {
  cancelling.value = true
  cancelError.value = null
  // Em PAYMENT_FAILED o cancelamento é imediato: o sucesso aparece como EXPIRED, não CANCELLED
  const immediate = isPaymentFailed.value
  const cancelled = () =>
    immediate ? subscription.value?.status === 'EXPIRED' : isCancelledWithAccess.value
  try {
    subscription.value = await cancelSubscription()
    showCancelDialog.value = false
    cancelledNow.value = immediate
  } catch (err) {
    const status = (err as { response?: { status?: number } }).response?.status
    showCancelDialog.value = false
    if (status === 409) {
      // Já cancelada (ex.: outra aba): mostra o estado atual
      await loadSubscription()
    } else if (status === 503) {
      // O backend só marca CANCELLED depois que o Stripe aceita: 503 garante que nada mudou
      cancelError.value =
        'Não foi possível cancelar agora. Nada foi alterado; tente novamente em alguns minutos.'
    } else {
      // Timeout/sem resposta/outro erro: o cancelamento pode ter acontecido; mostra o estado real
      await loadSubscription()
      if (cancelled()) {
        cancelledNow.value = immediate
      } else {
        cancelError.value =
          'Não foi possível confirmar o cancelamento. Verifique o status e tente novamente.'
      }
    }
  } finally {
    cancelling.value = false
  }
}

const reactivating = ref(false)
const reactivateError = ref<string | null>(null)

async function reactivate() {
  reactivating.value = true
  reactivateError.value = null
  cancelError.value = null
  try {
    subscription.value = await reactivateSubscription()
  } catch (err) {
    const status = (err as { response?: { status?: number } }).response?.status
    if (status === 409) {
      // Já reativada (outra aba) ou o Stripe já encerrou a assinatura: mostra o estado atual
      await loadSubscription()
      if (!isActiveMonthly.value) {
        reactivateError.value =
          'Não foi possível reativar esta assinatura. Assine novamente para voltar a ter acesso.'
      }
    } else if (status === 503) {
      // O backend só marca ACTIVE depois que o Stripe aceita: 503 garante que nada mudou
      reactivateError.value =
        'Não foi possível reativar agora. Nada foi alterado; tente novamente em alguns minutos.'
    } else {
      // Timeout/sem resposta/outro erro: a reativação pode ter acontecido; mostra o estado real
      await loadSubscription()
      if (!isActiveMonthly.value) {
        reactivateError.value =
          'Não foi possível confirmar a reativação. Verifique o status e tente novamente.'
      }
    }
  } finally {
    reactivating.value = false
  }
}

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
    // Mantém o estado anterior (na carga inicial já é null): um reload que falha não pode sumir com a tela
  }
}

function pollUntilMonthly(attempt = 1) {
  // MONTHLY/EXPIRED ou SUSPENDED (assinando de novo) continua até o webhook ativar
  if (isActiveMonthly.value || attempt > POLL_MAX_ATTEMPTS) return
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
