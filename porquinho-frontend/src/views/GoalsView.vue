<template>
  <main class="min-h-screen bg-gray-50 px-4 py-8">
    <div class="mx-auto max-w-3xl space-y-6">
      <div class="flex items-start justify-between gap-4">
        <div>
          <h1 ref="heading" tabindex="-1" class="text-2xl font-semibold text-gray-900 outline-none">Metas</h1>
          <p class="mt-1 text-sm text-gray-600">Seus objetivos financeiros e quanto falta para cada um</p>
        </div>
        <div class="flex flex-wrap justify-end gap-2">
          <Button
            ref="addButton"
            class="min-h-[44px]"
            :disabled="loading || loadFailed"
            data-testid="add-goal"
            @click="openCreate"
          >
            Criar meta
          </Button>
          <RouterLink
            to="/dashboard"
            data-testid="back-to-dashboard"
            class="inline-flex min-h-[44px] items-center rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100"
          >
            Voltar ao dashboard
          </RouterLink>
        </div>
      </div>

      <!-- Sempre no DOM: leitores de tela só anunciam mudanças numa região live que já existe -->
      <p
        role="status"
        data-testid="goals-success"
        :class="
          successMessage
            ? 'rounded-lg border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-800'
            : 'sr-only'
        "
      >
        {{ successMessage }}
      </p>

      <div v-if="loading" class="space-y-2" aria-busy="true" data-testid="goals-skeleton">
        <div class="h-32 animate-pulse rounded-lg bg-gray-200" />
        <span class="sr-only">Carregando suas metas...</span>
      </div>

      <div
        v-else-if="loadFailed"
        class="space-y-3 rounded-lg border border-gray-200 bg-white p-4 text-center"
        data-testid="goals-load-failed"
      >
        <p class="text-sm text-gray-600">Não foi possível carregar suas metas.</p>
        <Button variant="outline" class="min-h-[44px]" data-testid="retry-button" @click="load">
          Tentar de novo
        </Button>
      </div>

      <p
        v-else-if="goals.length === 0"
        class="rounded-lg border border-gray-200 bg-white p-4 text-center text-sm text-gray-600"
        data-testid="goals-empty"
      >
        Você ainda não tem metas. Use Criar meta para definir a primeira.
      </p>

      <ul v-else class="space-y-3">
        <li
          v-for="goal in goals"
          :key="goal.id"
          class="space-y-3 rounded-lg border border-gray-200 bg-white p-4"
          :data-testid="`goal-${goal.id}`"
        >
          <div class="flex items-center gap-2">
            <Target class="h-5 w-5 text-purple-600" aria-hidden="true" />
            <h2 class="min-w-0 font-medium break-words text-gray-900">{{ goal.name }}</h2>
          </div>
          <p v-if="goal.description" class="text-sm break-words whitespace-pre-line text-gray-600">{{ goal.description }}</p>
          <div
            class="h-3 w-full overflow-hidden rounded-full bg-gray-100"
            role="progressbar"
            :aria-valuenow="percent(goal)"
            aria-valuemin="0"
            aria-valuemax="100"
            :aria-label="`Progresso de ${goal.name}: ${percent(goal)}%`"
          >
            <div class="h-full rounded-full bg-green-500" :style="{ width: `${percent(goal)}%` }" />
          </div>
          <p class="text-sm text-gray-700" data-testid="goal-progress-text">
            {{ formatBRL(goal.currentAmount) }} de {{ formatBRL(goal.targetAmount) }} ({{ percent(goal) }}%)
          </p>
          <p v-if="goal.targetDate" class="text-sm text-gray-600">
            Data-alvo: {{ formatLocalDateBR(goal.targetDate) }}
          </p>
          <p v-if="goal.monthlyAllocation !== null" class="text-sm text-gray-600">
            Aporte mensal: {{ formatBRL(goal.monthlyAllocation) }}
          </p>
        </li>
      </ul>
    </div>

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
            <p v-if="errors.name" id="goal-name-error" class="text-sm text-red-600" data-testid="goal-name-error">
              {{ errors.name }}
            </p>
          </div>

          <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div class="space-y-1">
              <label for="goal-target-amount" class="text-sm font-medium text-gray-900">Valor-alvo (R$)</label>
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
  </main>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { Target } from 'lucide-vue-next'
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
import { formatLocalDateBR } from '@/lib/date'
import {
  GOAL_FIELDS,
  monthsUntil,
  parseISODate,
  suggestedMonthlyAllocation,
  todayInSaoPaulo,
  toISODate,
  validateGoalForm,
  type GoalField,
} from '@/lib/goalPlan'
import { formatBRL, formatBRLInput, parseBRL } from '@/lib/money'
import { createGoal, listGoals } from '@/services/goalService'
import type { Goal } from '@/types/goal'

/**
 * Story 8.1: metas ativas do usuário (ordem de criação) e criação de outra meta, sem limite. O aporte mensal é
 * sugerido pelo valor-alvo e pela data até o usuário editá-lo; a validação é a mesma do onboarding.
 */
const SUCCESS_TIMEOUT_MS = 5000
const SAVE_FAILED = 'Não foi possível criar a meta. Verifique sua conexão e tente novamente.'

const goals = ref<Goal[]>([])
const loading = ref(true)
const loadFailed = ref(false)
const heading = ref<HTMLElement | null>(null)
const addButton = ref<{ $el: HTMLElement } | null>(null)

const dialogOpen = ref(false)
const saving = ref(false)
const today = ref(todayInSaoPaulo())
const form = reactive({ name: '', targetAmount: '', targetDate: '', monthlyAllocation: '', description: '' })
const errors = reactive<Partial<Record<GoalField, string>>>({})
/** Depois que o usuário mexe no aporte, mudar valor ou data não o sobrescreve mais. */
const allocationEdited = ref(false)
const formError = ref<string | null>(null)
const successMessage = ref<string | null>(null)

watch(
  () => [parseBRL(form.targetAmount), parseISODate(form.targetDate)] as const,
  ([target, date]) => {
    if (allocationEdited.value || target === null || target <= 0 || date === null) return
    form.monthlyAllocation = formatBRLInput(suggestedMonthlyAllocation(target, monthsUntil(today.value, date)))
  },
)

function percent(goal: Goal): number {
  if (!(goal.targetAmount > 0)) return 0
  return Math.min(100, Math.floor((goal.currentAmount / goal.targetAmount) * 100))
}

interface ApiError {
  response?: { status?: number; data?: { detail?: string; errors?: Record<string, string> } }
}

async function load() {
  loading.value = true
  loadFailed.value = false
  try {
    goals.value = await listGoals()
  } catch {
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

let successTimer: ReturnType<typeof setTimeout> | undefined

function setSuccess(message: string) {
  clearTimeout(successTimer)
  successMessage.value = message
  successTimer = setTimeout(() => (successMessage.value = null), SUCCESS_TIMEOUT_MS)
}

function clearErrors() {
  for (const key of Object.keys(errors) as GoalField[]) delete errors[key]
}

function openCreate() {
  Object.assign(form, { name: '', targetAmount: '', targetDate: '', monthlyAllocation: '', description: '' })
  today.value = todayInSaoPaulo()
  allocationEdited.value = false
  clearErrors()
  formError.value = null
  dialogOpen.value = true
}

/** Com o POST em andamento o diálogo não fecha: fechar pareceria cancelar uma criação que já vai acontecer. */
function onOpenChange(open: boolean) {
  if (!saving.value) dialogOpen.value = open
}

function returnFocus(event: Event) {
  event.preventDefault()
  void nextTick(() => (addButton.value?.$el ?? heading.value)?.focus())
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
    const created = await createGoal(result.values)
    goals.value = [...goals.value, created]
    dialogOpen.value = false
    setSuccess('Meta criada')
  } catch (err) {
    const response = (err as ApiError)?.response
    const known = Object.entries(response?.data?.errors ?? {}).filter(([key]) =>
      GOAL_FIELDS.includes(key as GoalField),
    )
    if (response?.status === 400 && known.length > 0) Object.assign(errors, Object.fromEntries(known))
    else formError.value = response?.data?.detail ?? SAVE_FAILED
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  heading.value?.focus()
  await load()
})

onBeforeUnmount(() => clearTimeout(successTimer))
</script>
