<template>
  <PopoverRoot v-model:open="open">
    <!-- Botão próprio (e não PopoverTrigger, que troca o id): o <label for> de quem usa precisa achar o campo -->
    <PopoverAnchor as-child>
    <button
      :id="id"
      ref="button"
      type="button"
      aria-haspopup="dialog"
      :aria-expanded="open"
      :disabled="disabled"
      :aria-describedby="describedby"
      :aria-invalid="invalid ? 'true' : undefined"
      :data-testid="testid"
      :data-value="modelValue"
      @click="open = !open"
      class="flex h-11 w-full min-w-0 items-center justify-between gap-2 rounded-[10px] border border-input bg-card px-3 text-left text-base tabular-nums hover:bg-gray-50 focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50 focus-visible:outline-none aria-invalid:border-destructive disabled:cursor-not-allowed disabled:opacity-50 md:text-[15px]"
    >
      <span :class="modelValue ? 'text-gray-900' : 'text-gray-500'">{{ display }}</span>
      <CalendarDays class="size-5 shrink-0 text-gray-600" aria-hidden="true" />
    </button>
    </PopoverAnchor>
    <!-- Só montado aberto: o portal montado junto com um diálogo dispara o FocusScope dele e rouba o foco inicial -->
    <PopoverPortal v-if="open">
      <PopoverContent
        :side-offset="4"
        align="start"
        class="z-50 rounded-lg border border-gray-200 bg-white p-3 shadow-lg"
        :data-testid="testid ? `${testid}-picker` : undefined"
        @close-auto-focus="returnFocus"
      >
        <MonthPickerRoot
          v-slot="{ grid }"
          v-model="month"
          locale="pt-BR"
          :default-placeholder="placeholder"
          initial-focus
        >
          <MonthPickerHeader class="flex items-center justify-between gap-2">
            <MonthPickerPrev :class="NAV_CLASS" aria-label="Ano anterior">
              <ChevronLeft class="size-5" aria-hidden="true" />
            </MonthPickerPrev>
            <MonthPickerHeading class="text-sm font-medium text-gray-900" />
            <MonthPickerNext :class="NAV_CLASS" aria-label="Próximo ano">
              <ChevronRight class="size-5" aria-hidden="true" />
            </MonthPickerNext>
          </MonthPickerHeader>
          <MonthPickerGrid class="mt-2 w-full border-collapse">
            <MonthPickerGridBody>
              <MonthPickerGridRow v-for="(months, index) in grid.rows" :key="`linha-${index}`" class="flex">
                <MonthPickerCell v-for="item in months" :key="item.toString()" :date="item">
                  <MonthPickerCellTrigger
                    :month="item"
                    class="flex h-10 w-16 items-center justify-center rounded-md text-sm text-gray-900 capitalize outline-none hover:bg-purple-50 focus-visible:ring-2 focus-visible:ring-purple-600 data-[selected]:bg-purple-700 data-[selected]:text-white data-[today]:font-bold"
                  />
                </MonthPickerCell>
              </MonthPickerGridRow>
            </MonthPickerGridBody>
          </MonthPickerGrid>
        </MonthPickerRoot>
      </PopoverContent>
    </PopoverPortal>
  </PopoverRoot>
</template>

<script setup lang="ts">
import { CalendarDate, today, getLocalTimeZone } from '@internationalized/date'
import { CalendarDays, ChevronLeft, ChevronRight } from 'lucide-vue-next'
import {
  MonthPickerCell,
  MonthPickerCellTrigger,
  MonthPickerGrid,
  MonthPickerGridBody,
  MonthPickerGridRow,
  MonthPickerHeader,
  MonthPickerHeading,
  MonthPickerNext,
  MonthPickerPrev,
  MonthPickerRoot,
  PopoverContent,
  PopoverPortal,
  PopoverAnchor,
  PopoverRoot,
  type DateValue,
} from 'reka-ui'
import { computed, ref } from 'vue'

/**
 * Campo de mês sempre em mm/aaaa. O `<input type="month">` nativo segue o idioma do navegador (e o Safari nem tem
 * seletor de mês). O v-model continua em AAAA-MM ('' sem mês). O botão aceita `<label for>` pelo `id`.
 */
const props = defineProps<{
  modelValue: string
  id?: string
  describedby?: string
  invalid?: boolean
  disabled?: boolean
  testid?: string
}>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const NAV_CLASS =
  'inline-flex size-10 items-center justify-center rounded-md text-gray-700 hover:bg-gray-100 focus-visible:ring-2 focus-visible:ring-purple-600 focus-visible:outline-none'

const open = ref(false)
const button = ref<HTMLButtonElement | null>(null)

function returnFocus(event: Event) {
  event.preventDefault()
  button.value?.focus()
}

function parseMonth(value: string): CalendarDate | undefined {
  const match = /^(\d{4})-(\d{2})$/.exec(value)
  if (!match) return undefined
  const monthNumber = Number(match[2])
  return monthNumber >= 1 && monthNumber <= 12 ? new CalendarDate(Number(match[1]), monthNumber, 1) : undefined
}

const month = computed<DateValue | undefined>({
  get: () => parseMonth(props.modelValue),
  set: (value) => {
    if (!value) return
    emit('update:modelValue', `${value.year}-${String(value.month).padStart(2, '0')}`)
    open.value = false
  },
})
const placeholder = computed(() => month.value ?? today(getLocalTimeZone()))
const display = computed(() => {
  const value = parseMonth(props.modelValue)
  return value ? `${String(value.month).padStart(2, '0')}/${value.year}` : 'mm/aaaa'
})
</script>
