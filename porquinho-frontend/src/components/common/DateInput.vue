<template>
  <DatePickerRoot
    v-model="date"
    v-model:open="open"
    locale="pt-BR"
    :week-starts-on="0"
    :min-value="toDate(min)"
    :max-value="toDate(max)"
    :disabled="disabled"
    close-on-select
  >
    <DatePickerField
      v-slot="{ segments }"
      :aria-labelledby="labelledby"
      :aria-describedby="describedby"
      :aria-invalid="invalid ? 'true' : undefined"
      :data-testid="testid"
      :data-value="modelValue"
      class="flex h-11 w-full min-w-0 items-center rounded-[10px] border border-input bg-card pr-1 pl-3 text-base tabular-nums focus-within:border-ring focus-within:ring-[3px] focus-within:ring-ring/50 aria-invalid:border-destructive data-[disabled]:cursor-not-allowed data-[disabled]:opacity-50 md:text-[15px]"
    >
      <div class="flex flex-1 items-center">
        <template v-for="item in segments" :key="item.part">
          <DatePickerInput v-if="item.part === 'literal'" :part="item.part" class="text-gray-500">
            {{ item.value }}
          </DatePickerInput>
          <DatePickerInput
            v-else
            :part="item.part"
            class="rounded px-0.5 text-gray-900 outline-none focus:bg-purple-100 data-[placeholder]:text-gray-500"
            :data-testid="testid ? `${testid}-${item.part}` : undefined"
          >
            {{ item.value }}
          </DatePickerInput>
        </template>
      </div>
      <DatePickerTrigger
        class="inline-flex size-11 shrink-0 items-center justify-center rounded-md text-gray-600 hover:bg-gray-100 hover:text-gray-900 focus-visible:ring-2 focus-visible:ring-purple-600 focus-visible:outline-none"
        aria-label="Abrir calendário"
        :data-testid="testid ? `${testid}-calendar` : undefined"
      >
        <CalendarDays class="size-5" aria-hidden="true" />
      </DatePickerTrigger>
    </DatePickerField>

    <!-- Só montado aberto: o portal montado junto com um diálogo dispara o FocusScope dele e rouba o foco inicial -->
    <DatePickerContent
      v-if="open"
      :side-offset="4"
      align="end"
      class="z-50 rounded-lg border border-gray-200 bg-white p-3 shadow-lg"
    >
      <DatePickerCalendar v-slot="{ weekDays, grid }">
        <DatePickerHeader class="flex items-center justify-between gap-2">
          <DatePickerPrev :class="NAV_CLASS" aria-label="Mês anterior">
            <ChevronLeft class="size-5" aria-hidden="true" />
          </DatePickerPrev>
          <DatePickerHeading class="text-sm font-medium text-gray-900 first-letter:uppercase" />
          <DatePickerNext :class="NAV_CLASS" aria-label="Próximo mês">
            <ChevronRight class="size-5" aria-hidden="true" />
          </DatePickerNext>
        </DatePickerHeader>
        <DatePickerGrid v-for="month in grid" :key="month.value.toString()" class="mt-2 w-full border-collapse">
          <DatePickerGridHead>
            <DatePickerGridRow class="flex">
              <DatePickerHeadCell v-for="day in weekDays" :key="day" class="w-10 text-xs font-normal text-gray-600">
                {{ day }}
              </DatePickerHeadCell>
            </DatePickerGridRow>
          </DatePickerGridHead>
          <DatePickerGridBody>
            <DatePickerGridRow v-for="(weekDates, index) in month.rows" :key="`semana-${index}`" class="flex">
              <DatePickerCell v-for="weekDate in weekDates" :key="weekDate.toString()" :date="weekDate">
                <DatePickerCellTrigger
                  :day="weekDate"
                  :month="month.value"
                  class="flex size-10 items-center justify-center rounded-md text-sm text-gray-900 tabular-nums outline-none hover:bg-purple-50 focus-visible:ring-2 focus-visible:ring-purple-600 data-[disabled]:pointer-events-none data-[disabled]:text-gray-300 data-[outside-view]:text-gray-400 data-[selected]:bg-purple-700 data-[selected]:text-white data-[today]:font-bold"
                />
              </DatePickerCell>
            </DatePickerGridRow>
          </DatePickerGridBody>
        </DatePickerGrid>
      </DatePickerCalendar>
    </DatePickerContent>
  </DatePickerRoot>
</template>

<script setup lang="ts">
import { parseDate, type CalendarDate } from '@internationalized/date'
import { CalendarDays, ChevronLeft, ChevronRight } from 'lucide-vue-next'
import {
  DatePickerCalendar,
  DatePickerCell,
  DatePickerCellTrigger,
  DatePickerContent,
  DatePickerField,
  DatePickerGrid,
  DatePickerGridBody,
  DatePickerGridHead,
  DatePickerGridRow,
  DatePickerHeadCell,
  DatePickerHeader,
  DatePickerHeading,
  DatePickerInput,
  DatePickerNext,
  DatePickerPrev,
  DatePickerRoot,
  DatePickerTrigger,
  type DateValue,
} from 'reka-ui'
import { computed, ref } from 'vue'

/**
 * Campo de data sempre em dd/mm/aaaa. O `<input type="date">` nativo segue o idioma do navegador (mm/dd/aaaa num
 * navegador em inglês), mesmo com a página em pt-BR. O v-model continua em AAAA-MM-DD ('' sem data), como antes.
 * O rótulo vem de fora: passe `labelledby` com o id do rótulo (sem `for`: o reka põe o id num input escondido).
 */
const props = defineProps<{
  modelValue: string
  labelledby?: string
  describedby?: string
  invalid?: boolean
  disabled?: boolean
  /** AAAA-MM-DD */
  min?: string
  /** AAAA-MM-DD */
  max?: string
  testid?: string
}>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const NAV_CLASS =
  'inline-flex size-10 items-center justify-center rounded-md text-gray-700 hover:bg-gray-100 focus-visible:ring-2 focus-visible:ring-purple-600 focus-visible:outline-none'

const open = ref(false)

function toDate(value: string | undefined): CalendarDate | undefined {
  if (!value) return undefined
  try {
    return parseDate(value)
  } catch {
    return undefined
  }
}

const date = computed<DateValue | undefined>({
  get: () => toDate(props.modelValue),
  set: (value) => emit('update:modelValue', value ? value.toString().slice(0, 10) : ''),
})
</script>
