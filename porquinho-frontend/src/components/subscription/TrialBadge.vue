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
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { Subscription } from '@/types/subscription'

const props = defineProps<{ subscription: Subscription }>()

const expired = computed(
  () => !props.subscription.hasAccess || props.subscription.daysRemaining <= 0,
)

const label = computed(() => {
  if (expired.value) return 'Trial expirado'
  const days = props.subscription.daysRemaining
  return days === 1 ? 'Trial: 1 dia restante' : `Trial: ${days} dias restantes`
})
</script>
