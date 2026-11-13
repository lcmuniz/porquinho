<template>
  <PopoverRoot>
    <PopoverTrigger
      :id="id"
      :aria-label="`${label}: ${summary}`"
      :data-testid="testid"
      class="flex min-h-[44px] w-full items-center justify-between gap-2 rounded-md border border-gray-200 bg-white px-3 py-2 text-left text-sm text-gray-900 focus-visible:ring-2 focus-visible:ring-purple-600 focus-visible:outline-none"
    >
      <span class="min-w-0 truncate">{{ summary }}</span>
      <ChevronDown class="size-4 shrink-0 text-gray-500" aria-hidden="true" />
    </PopoverTrigger>
    <PopoverContent
      align="start"
      :side-offset="4"
      class="z-50 max-h-80 w-[min(20rem,calc(100vw-2rem))] overflow-y-auto rounded-md border border-gray-200 bg-white p-2 shadow-lg"
      :data-testid="`${testid}-options`"
    >
      <fieldset>
        <legend class="sr-only">{{ label }}</legend>
        <template v-for="group in groups" :key="group.name">
          <p v-if="group.name" class="px-2 pt-2 pb-1 text-xs font-semibold text-gray-500 uppercase">{{ group.name }}</p>
          <label
            v-for="option in group.options"
            :key="option.value"
            class="flex min-h-[44px] cursor-pointer items-center gap-2 rounded px-2 text-sm text-gray-900 hover:bg-gray-50"
          >
            <input
              type="checkbox"
              class="size-4 accent-purple-600"
              :value="option.value"
              :checked="modelValue.includes(option.value)"
              :data-testid="`${testid}-option-${option.value}`"
              @change="toggle(option.value)"
            />
            {{ option.label }}
          </label>
        </template>
      </fieldset>
      <button
        v-if="modelValue.length"
        type="button"
        class="mt-1 min-h-[44px] w-full rounded px-2 text-left text-sm font-medium text-purple-700 hover:bg-gray-50"
        :data-testid="`${testid}-clear`"
        @click="emit('update:modelValue', [])"
      >
        Limpar seleção
      </button>
    </PopoverContent>
  </PopoverRoot>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ChevronDown } from 'lucide-vue-next'
import { PopoverContent, PopoverRoot, PopoverTrigger } from 'reka-ui'

/** Story 7.6: seleção múltipla com checkboxes nativos num Popover. Nada marcado = "Todas". */
export interface MultiSelectOption {
  value: string
  label: string
  group?: string
}

const props = defineProps<{
  id: string
  label: string
  testid: string
  options: MultiSelectOption[]
  modelValue: string[]
}>()

const emit = defineEmits<{ 'update:modelValue': [value: string[]] }>()

const groups = computed(() => {
  const result: { name: string; options: MultiSelectOption[] }[] = []
  for (const option of props.options) {
    const name = option.group ?? ''
    let group = result.find((g) => g.name === name)
    if (!group) result.push((group = { name, options: [] }))
    group.options.push(option)
  }
  return result
})

const summary = computed(() => {
  const selected = props.options.filter((o) => props.modelValue.includes(o.value))
  if (selected.length === 0) return 'Todas'
  if (selected.length === 1) return selected[0]!.label
  return `${selected.length} selecionadas`
})

function toggle(value: string) {
  const next = props.modelValue.includes(value)
    ? props.modelValue.filter((v) => v !== value)
    : [...props.modelValue, value]
  emit('update:modelValue', next)
}
</script>
