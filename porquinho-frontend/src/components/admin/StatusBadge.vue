<template>
  <span class="rounded-full px-2 py-0.5 text-xs font-medium" :class="ADMIN_STATUS[status].badge">
    {{ ADMIN_STATUS[status].label }}
  </span>
</template>

<script setup lang="ts">
import { ADMIN_STATUS } from '@/lib/adminStatus'
import type { HealthStatus } from '@/types/admin'

defineProps<{ status: HealthStatus }>()
</script>
