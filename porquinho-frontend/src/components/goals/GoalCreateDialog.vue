<template>
  <Dialog :open="dialogOpen" @update:open="onOpenChange">
    <DialogContent data-testid="goal-dialog" @close-auto-focus="returnFocus">
      <DialogHeader>
        <DialogTitle>Criar meta</DialogTitle>
        <DialogDescription>Data-alvo, aporte mensal e descrição são opcionais.</DialogDescription>
      </DialogHeader>
      <p
        v-if="formError"
        role="alert"
        data-testid="goal-form-error"
        class="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800"
      >
        {{ formError }}
      </p>
      <form class="space-y-4" novalidate data-testid="goal-form" @submit.prevent="save">
        <fieldset class="space-y-2">
          <legend class="text-sm font-medium text-gray-900">Usar modelo</legend>
          <div class="grid grid-cols-2 gap-2">
            <label
              v-for="option in TEMPLATE_OPTIONS"
              :key="option.code"
              class="flex min-h-[44px] cursor-pointer items-center gap-2 rounded-md border px-3 text-sm"
              :class="
                template === option.code
                  ? 'border-purple-600 bg-purple-50 text-purple-900'
                  : 'border-gray-200 text-gray-700'
              "
            >
              <input
                type="radio"
                name="goal-template"
                class="accent-purple-600"
                :value="option.code"
                :checked="template === option.code"
                :data-testid="`goal-template-${option.code || 'CUSTOM'}`"
                @change="selectTemplate(option.code)"
              />
              {{ option.name }}
            </label>
          </div>
        </fieldset>
        <div class="space-y-1">
          <label for="goal-name" class="text-sm font-medium text-gray-900">Nome da meta</label>
          <Input
            id="goal-name"
            v-model="form.name"
            class="min-h-[44px]"
            maxlength="100"
            autocomplete="off"
            placeholder="Ex.: Viagem"
            data-testid="goal-name-input"
            :aria-invalid="errors.name ? 'true' : undefined"
            :aria-describedby="errors.name ? 'goal-name-error' : undefined"
          />
          <p
            v-if="errors.name"
            id="goal-name-error"
            class="text-sm text-red-600"
            data-testid="goal-name-error"
          >
            {{ errors.name }}
          </p>
        </div>

        <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div class="space-y-1">
            <label for="goal-target-amount" class="text-sm font-medium text-gray-900"
              >Valor-alvo (R$)</label
            >
            <Input
              id="goal-target-amount"
              v-model="form.targetAmount"
              class="min-h-[44px]"
              inputmode="decimal"
              placeholder="12.000,00"
              autocomplete="off"
              data-testid="goal-target-amount-input"
              :aria-invalid="errors.targetAmount ? 'true' : undefined"
              :aria-describedby="errors.targetAmount ? 'goal-target-amount-error' : undefined"
            />
            <p
              v-if="errors.targetAmount"
              id="goal-target-amount-error"
              class="text-sm text-red-600"
              data-testid="goal-target-amount-error"
            >
              {{ errors.targetAmount }}
            </p>
          </div>

          <div class="space-y-1">
            <label for="goal-target-date" class="text-sm font-medium text-gray-900">
              Data-alvo <span class="font-normal text-gray-500">(opcional)</span>
            </label>
            <Input
              id="goal-target-date"
              v-model="form.targetDate"
              class="min-h-[44px]"
              type="date"
              :min="toISODate(today)"
              data-testid="goal-target-date-input"
              :aria-invalid="errors.targetDate ? 'true' : undefined"
              :aria-describedby="errors.targetDate ? 'goal-target-date-error' : undefined"
            />
            <p
              v-if="errors.targetDate"
              id="goal-target-date-error"
              class="text-sm text-red-600"
              data-testid="goal-target-date-error"
            >
              {{ errors.targetDate }}
            </p>
          </div>
        </div>

        <div class="space-y-1">
          <label for="goal-monthly-allocation" class="text-sm font-medium text-gray-900">
            Aporte mensal (R$) <span class="font-normal text-gray-500">(opcional)</span>
          </label>
          <Input
            id="goal-monthly-allocation"
            v-model="form.monthlyAllocation"
            class="min-h-[44px]"
            inputmode="decimal"
            placeholder="0,00"
            autocomplete="off"
            data-testid="goal-monthly-allocation-input"
            :aria-invalid="errors.monthlyAllocation ? 'true' : undefined"
            :aria-describedby="
              errors.monthlyAllocation
                ? 'goal-monthly-allocation-error goal-monthly-allocation-hint'
                : 'goal-monthly-allocation-hint'
            "
            @input="allocationEdited = true"
          />
          <p
            v-if="errors.monthlyAllocation"
            id="goal-monthly-allocation-error"
            class="text-sm text-red-600"
            data-testid="goal-monthly-allocation-error"
          >
            {{ errors.monthlyAllocation }}
          </p>
          <p id="goal-monthly-allocation-hint" class="text-xs text-gray-500">
            Calculado pelo valor-alvo e pela data. Você pode mudar.
          </p>
        </div>

        <div class="space-y-1">
          <label for="goal-description" class="text-sm font-medium text-gray-900">
            Descrição <span class="font-normal text-gray-500">(opcional)</span>
          </label>
          <textarea
            id="goal-description"
            v-model="form.description"
            rows="3"
            maxlength="500"
            class="flex w-full rounded-md border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 focus-visible:ring-2 focus-visible:ring-purple-600 focus-visible:outline-none"
            data-testid="goal-description-input"
            :aria-invalid="errors.description ? 'true' : undefined"
            :aria-describedby="errors.description ? 'goal-description-error' : undefined"
          />
          <p
            v-if="errors.description"
            id="goal-description-error"
            class="text-sm text-red-600"
            data-testid="goal-description-error"
          >
            {{ errors.description }}
          </p>
        </div>

        <DialogFooter>
          <Button
            type="button"
            variant="outline"
            class="min-h-[44px]"
            :disabled="saving"
            data-testid="goal-cancel"
            @click="dialogOpen = false"
          >
            Cancelar
          </Button>
          <Button type="submit" class="min-h-[44px]" :disabled="saving" data-testid="goal-save">
            {{ saving ? 'Salvando...' : 'Salvar' }}
          </Button>
        </DialogFooter>
      </form>
    </DialogContent>
  </Dialog>
</template>

<script setup lang="ts">
import { nextTick, reactive, ref, watch } from 'vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  addMonths,
  GOAL_FIELDS,
  GOAL_TEMPLATES,
  monthsUntil,
  parseISODate,
  suggestedMonthlyAllocation,
  todayInSaoPaulo,
  toISODate,
  validateGoalForm,
  type GoalField,
} from '@/lib/goalPlan'
import { formatBRLInput, parseBRL } from '@/lib/money'
import { createGoal } from '@/services/goalService'
import type { Goal, GoalTemplate } from '@/types/goal'

/**
 * Story 8.1: criação de meta, sem limite. O aporte mensal é sugerido pelo valor-alvo e pela data até o usuário
 * editá-lo; a validação é a mesma do onboarding.
 * Story 8.6: "Usar modelo" preenche os campos; o modelo vai no POST mesmo editado.
 */
const props = defineProps<{ focusFallback: HTMLElement | null }>()
const emit = defineEmits<{ created: [goal: Goal] }>()

const SAVE_FAILED = 'Não foi possível criar a meta. Verifique sua conexão e tente novamente.'

interface ApiError {
  response?: { status?: number; data?: { detail?: string; errors?: Record<string, string> } }
}

const dialogOpen = ref(false)
const saving = ref(false)
const today = ref(todayInSaoPaulo())
const form = reactive({
  name: '',
  targetAmount: '',
  targetDate: '',
  monthlyAllocation: '',
  description: '',
})
const errors = reactive<Partial<Record<GoalField, string>>>({})
/** Story 8.6: '' é "Personalizada" (sem modelo no POST). */
const template = ref<GoalTemplate | ''>('')
const TEMPLATE_OPTIONS = [{ code: '' as const, name: 'Personalizada' }, ...GOAL_TEMPLATES]
/** Depois que o usuário mexe no aporte, mudar valor ou data não o sobrescreve mais. */
const allocationEdited = ref(false)
const formError = ref<string | null>(null)

watch(
  () => [parseBRL(form.targetAmount), parseISODate(form.targetDate)] as const,
  ([target, date]) => {
    if (allocationEdited.value || target === null || target <= 0 || date === null) return
    form.monthlyAllocation = formatBRLInput(
      suggestedMonthlyAllocation(target, monthsUntil(today.value, date)),
    )
  },
)

function clearErrors() {
  for (const key of Object.keys(errors) as GoalField[]) delete errors[key]
}

function open() {
  template.value = ''
  today.value = todayInSaoPaulo()
  applyTemplate()
  formError.value = null
  dialogOpen.value = true
}

function selectTemplate(code: GoalTemplate | '') {
  template.value = code
  applyTemplate()
}

/** Story 8.6: o modelo substitui o que estava digitado; "Personalizada" limpa o formulário. */
function applyTemplate() {
  const chosen = GOAL_TEMPLATES.find((t) => t.code === template.value)
  allocationEdited.value = false
  clearErrors()
  if (!chosen) {
    Object.assign(form, {
      name: '',
      targetAmount: '',
      targetDate: '',
      monthlyAllocation: '',
      description: '',
    })
    return
  }
  const date = addMonths(today.value, chosen.months)
  // Calculado aqui: o watch do aporte não dispara se valor e data já forem os mesmos
  Object.assign(form, {
    name: chosen.name,
    targetAmount: formatBRLInput(chosen.targetAmount),
    targetDate: toISODate(date),
    monthlyAllocation: formatBRLInput(
      suggestedMonthlyAllocation(chosen.targetAmount, monthsUntil(today.value, date)),
    ),
    description: chosen.description,
  })
}

/** Com o POST em andamento o diálogo não fecha: fechar pareceria cancelar uma criação que já vai acontecer. */
function onOpenChange(value: boolean) {
  if (!saving.value) dialogOpen.value = value
}

function returnFocus(event: Event) {
  event.preventDefault()
  void nextTick(() => props.focusFallback?.focus())
}

async function save() {
  if (saving.value) return
  clearErrors()
  formError.value = null
  const result = validateGoalForm(form, today.value)
  Object.assign(errors, result.errors)
  if (result.values === null) return
  saving.value = true
  try {
    const created = await createGoal(
      template.value ? { ...result.values, template: template.value } : result.values,
    )
    dialogOpen.value = false
    emit('created', created)
  } catch (err) {
    const response = (err as ApiError)?.response
    const known = Object.entries(response?.data?.errors ?? {}).filter(([key]) =>
      GOAL_FIELDS.includes(key as GoalField),
    )
    if (response?.status === 400 && known.length > 0)
      Object.assign(errors, Object.fromEntries(known))
    else formError.value = response?.data?.detail ?? SAVE_FAILED
  } finally {
    saving.value = false
  }
}

defineExpose({ open })
</script>
