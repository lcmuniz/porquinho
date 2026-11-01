<template>
  <span
    v-if="subscription.planType === 'TRIAL'"
    role="status"
    data-testid="trial-badge"
    :class="[
      'inline-flex items-center rounded-full px-3 py-1 text-sm font-medium',
      expired ? 'bg-red-100 text-red-800' : 'bg-green-100 text-green-800',
    ]"
  >
    {{ label }}
  </span>
  <span
    v-else-if="subscription.planType === 'MONTHLY' && subscription.status === 'ACTIVE'"
    role="status"
    data-testid="subscription-active-badge"
    class="inline-flex items-center rounded-full bg-green-100 px-3 py-1 text-sm font-medium text-green-800"
  >
    Assinatura ativa
  </span>
  <span
    v-else-if="cancelledWithAccess"
    role="status"
    data-testid="subscription-cancelled-badge"
    class="inline-flex items-center rounded-full bg-yellow-100 px-3 py-1 text-sm font-medium text-yellow-800"
  >
    Assinatura cancelada (ativa até {{ formatDateBR(subscription.nextBillingDate) }})
  </span>
  <span
    v-else-if="subscription.status === 'PAYMENT_FAILED'"
    role="status"
    data-testid="subscription-payment-failed-badge"
    class="inline-flex items-center rounded-full bg-red-100 px-3 py-1 text-sm font-medium text-red-800"
  >
    Pagamento falhou
  </span>
  <span
    v-else-if="subscription.status === 'SUSPENDED'"
    role="status"
    data-testid="subscription-suspended-badge"
    class="inline-flex items-center rounded-full bg-red-100 px-3 py-1 text-sm font-medium text-red-800"
  >
    Assinatura suspensa
  </span>
  <span
    v-else-if="subscription.planType === 'MONTHLY'"
    role="status"
    data-testid="subscription-expired-badge"
    class="inline-flex items-center rounded-full bg-red-100 px-3 py-1 text-sm font-medium text-red-800"
  >
    Assinatura expirada
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { formatDateBR } from '@/lib/date'
import type { Subscription } from '@/types/subscription'

const props = defineProps<{ subscription: Subscription }>()

const expired = computed(
  () => !props.subscription.hasAccess || props.subscription.daysRemaining <= 0,
)

// Cancelada mas ainda dentro do período pago; passado o período vira "expirada" mesmo antes do webhook
const cancelledWithAccess = computed(
  () =>
    props.subscription.planType === 'MONTHLY' &&
    props.subscription.status === 'CANCELLED' &&
    props.subscription.hasAccess,
)

const label = computed(() => {
  if (expired.value) return 'Trial expirado'
  const days = props.subscription.daysRemaining
  return days === 1 ? 'Trial: 1 dia restante' : `Trial: ${days} dias restantes`
})
</script>
