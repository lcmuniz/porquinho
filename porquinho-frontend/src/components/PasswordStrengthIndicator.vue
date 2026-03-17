<template>
  <div class="space-y-2">
    <div class="h-2 w-full bg-gray-200 rounded-full overflow-hidden">
      <div
        :class="strengthBarClass"
        class="h-full transition-all duration-300"
        :style="{ width: strengthWidth }"
      ></div>
    </div>
    <p :class="strengthTextClass" class="text-sm font-medium">
      {{ strengthText }}
    </p>
    <ul class="text-xs text-gray-600 space-y-1">
      <li :class="{ 'text-green-600': hasMinLength }">
        {{ hasMinLength ? '✓' : '○' }} Mínimo de 8 caracteres
      </li>
      <li :class="{ 'text-green-600': hasUppercase }">
        {{ hasUppercase ? '✓' : '○' }} Pelo menos 1 letra maiúscula
      </li>
      <li :class="{ 'text-green-600': hasLowercase }">
        {{ hasLowercase ? '✓' : '○' }} Pelo menos 1 letra minúscula
      </li>
      <li :class="{ 'text-green-600': hasNumber }">
        {{ hasNumber ? '✓' : '○' }} Pelo menos 1 número
      </li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  password: string
}>()

const hasMinLength = computed(() => props.password.length >= 8)
const hasUppercase = computed(() => /[A-Z]/.test(props.password))
const hasLowercase = computed(() => /[a-z]/.test(props.password))
const hasNumber = computed(() => /[0-9]/.test(props.password))

const requirementsMet = computed(() => {
  return [hasMinLength.value, hasUppercase.value, hasLowercase.value, hasNumber.value].filter(
    Boolean
  ).length
})

const strengthText = computed(() => {
  if (props.password.length === 0) return 'Digite uma senha'
  if (requirementsMet.value <= 2) return 'Senha fraca'
  if (requirementsMet.value === 3) return 'Senha média'
  return 'Senha forte'
})

const strengthWidth = computed(() => {
  if (props.password.length === 0) return '0%'
  if (requirementsMet.value <= 2) return '33%'
  if (requirementsMet.value === 3) return '66%'
  return '100%'
})

const strengthBarClass = computed(() => {
  if (requirementsMet.value <= 2) return 'bg-red-600'
  if (requirementsMet.value === 3) return 'bg-yellow-500'
  return 'bg-green-600'
})

const strengthTextClass = computed(() => {
  if (props.password.length === 0) return 'text-gray-500'
  if (requirementsMet.value <= 2) return 'text-red-600'
  if (requirementsMet.value === 3) return 'text-yellow-600'
  return 'text-green-600'
})
</script>
