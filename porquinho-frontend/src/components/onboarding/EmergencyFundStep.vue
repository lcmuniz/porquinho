<template>
  <div class="flex flex-col gap-8">
    <section class="space-y-6" data-testid="emergency-fund-step">
      <div class="space-y-3">
        <h1 ref="heading" tabindex="-1" class="text-2xl font-bold text-gray-900 outline-none">
          Crie sua primeira meta: Reserva de Emergência
        </h1>
        <p class="text-gray-600">
          Uma reserva de emergência cobre imprevistos, como uma conta inesperada ou um mês sem
          renda. Já deixamos uma sugestão; ajuste os valores ao seu momento.
        </p>
      </div>

      <form
        class="space-y-4 rounded-lg border border-gray-200 p-4"
        novalidate
        data-testid="goal-form"
        @submit.prevent="createAndFinish"
      >
        <div class="space-y-1">
          <label for="goal-name" class="text-sm font-medium text-gray-900">Nome da meta</label>
          <Input
            class="min-h-[44px]"
            id="goal-name"
            v-model="form.name"
            maxlength="100"
            autocomplete="off"
            data-testid="goal-name-input"
            :disabled="goalCreated"
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
            <label for="goal-target-amount" class="text-sm font-medium text-gray-900">
              Valor-alvo (R$)
            </label>
            <Input
              class="min-h-[44px]"
              id="goal-target-amount"
              v-model="form.targetAmount"
              inputmode="decimal"
              placeholder="25.000,00"
              autocomplete="off"
              data-testid="goal-target-amount-input"
              :disabled="goalCreated"
              :aria-invalid="errors.targetAmount ? 'true' : undefined"
              :aria-describedby="
                errors.targetAmount
                  ? 'goal-target-amount-error goal-target-amount-hint'
                  : 'goal-target-amount-hint'
              "
            />
            <p
              v-if="errors.targetAmount"
              id="goal-target-amount-error"
              class="text-sm text-red-600"
              data-testid="goal-target-amount-error"
            >
              {{ errors.targetAmount }}
            </p>
            <p id="goal-target-amount-hint" class="text-xs text-gray-500">
              Uma boa referência é de 3 a 6 meses dos seus gastos.
            </p>
          </div>

          <div class="space-y-1">
            <label for="goal-target-date" class="text-sm font-medium text-gray-900">
              Data-alvo <span class="font-normal text-gray-500">(opcional)</span>
            </label>
            <Input
              class="min-h-[44px]"
              id="goal-target-date"
              v-model="form.targetDate"
              type="date"
              :min="todayISO"
              data-testid="goal-target-date-input"
              :disabled="goalCreated"
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
            class="min-h-[44px]"
            id="goal-monthly-allocation"
            v-model="form.monthlyAllocation"
            inputmode="decimal"
            placeholder="0,00"
            autocomplete="off"
            data-testid="goal-monthly-allocation-input"
            :disabled="goalCreated"
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
      </form>

      <!-- Prévia: a meta começa em 0% -->
      <div class="space-y-3 rounded-lg bg-purple-50 p-4" data-testid="goal-preview">
        <div class="flex items-center gap-2">
          <Target class="h-5 w-5 text-purple-600" aria-hidden="true" />
          <h2 class="font-medium text-gray-900">{{ form.name.trim() || 'Sua meta' }}</h2>
        </div>
        <div
          class="h-3 w-full overflow-hidden rounded-full bg-white"
          role="progressbar"
          aria-valuenow="0"
          aria-valuemin="0"
          aria-valuemax="100"
          aria-label="Progresso da meta: 0%"
          data-testid="goal-progress"
        >
          <div class="h-full w-0 rounded-full bg-green-500" />
        </div>
        <p class="text-sm text-gray-700">
          <span class="font-semibold">0%</span> · {{ formatBRL(0) }} de {{ previewTarget }}
        </p>
        <p class="text-sm text-gray-700" data-testid="goal-forecast" aria-live="polite">
          {{ forecastText }}
        </p>
      </div>
      <p
        v-if="goalCreated"
        role="status"
        class="rounded-lg border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-800"
        data-testid="goal-saved-message"
      >
        Sua meta já foi salva. Toque em Concluir para terminar.
      </p>
    </section>

    <nav class="flex flex-col gap-3" aria-label="Navegação do passo da meta">
      <div class="flex gap-3">
        <Button
          variant="outline"
          class="min-h-[44px] flex-1"
          data-testid="back-button"
          :disabled="busy || loadingGoals"
          @click="emit('back')"
        >
          <ChevronLeft class="h-4 w-4" aria-hidden="true" />
          Voltar
        </Button>
        <Button
          class="min-h-[44px] flex-1 bg-purple-600 text-white hover:bg-purple-700"
          data-testid="create-goal-button"
          :disabled="busy || loadingGoals"
          @click="createAndFinish"
        >
          {{
            finishing === 'create'
              ? 'Salvando...'
              : goalCreated
                ? 'Concluir'
                : 'Criar meta e concluir'
          }}
        </Button>
      </div>
      <Button
        v-if="!goalCreated"
        variant="ghost"
        class="min-h-[44px] text-gray-600"
        data-testid="skip-button"
        :disabled="busy || loadingGoals"
        @click="skip"
      >
        {{ finishing === 'skip' ? 'Salvando...' : 'Pular por agora' }}
      </Button>
    </nav>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ChevronLeft, Target } from 'lucide-vue-next'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { cleanCategoryName as cleanName } from '@/lib/categoryName'
import {
  addMonths,
  formatMonthYear,
  monthsUntil,
  parseISODate,
  projectedCompletion,
  suggestedMonthlyAllocation,
  todayInSaoPaulo,
  toISODate,
} from '@/lib/goalPlan'
import { formatBRL, formatBRLInput, parseBRL } from '@/lib/money'
import { createGoal, listGoals } from '@/services/goalService'
import { finishOnboarding } from '@/services/onboardingService'
import type { OnboardingProgress } from '@/types/onboarding'

/**
 * Story 3.8: passo 8, o último do onboarding. Formulário pré-preenchido da Reserva de Emergência (R$ 25.000,00,
 * data em 12 meses e aporte calculado), prévia em 0% com a previsão de conclusão, "Criar meta e concluir"
 * (POST /goals e depois /finish) e "Pular por agora" (só /finish com skipped=true). Se a meta foi criada e o
 * /finish falhou (ou o usuário já tem meta ativa, ao recarregar a página), o formulário fica só leitura com a
 * meta salva, o Pular some e o botão vira "Concluir", que chama só o /finish.
 */
const emit = defineEmits<{
  back: []
  done: [progress: OnboardingProgress]
  error: [message: string]
}>()

const DEFAULT_NAME = 'Reserva de Emergência'
const DEFAULT_TARGET = 25000
const DEFAULT_MONTHS = 12

type Field = 'name' | 'targetAmount' | 'targetDate' | 'monthlyAllocation'
const FIELDS: Field[] = ['name', 'targetAmount', 'targetDate', 'monthlyAllocation']

// "Hoje" em São Paulo, como no backend (NotPastInSaoPaulo)
const today = todayInSaoPaulo()
const todayISO = toISODate(today)
const defaultDate = addMonths(today, DEFAULT_MONTHS)

const form = reactive({
  name: DEFAULT_NAME,
  targetAmount: formatBRLInput(DEFAULT_TARGET),
  targetDate: toISODate(defaultDate),
  monthlyAllocation: formatBRLInput(
    suggestedMonthlyAllocation(DEFAULT_TARGET, monthsUntil(today, defaultDate)),
  ),
})
const errors = reactive<Partial<Record<Field, string>>>({})
/** Depois que o usuário mexe no aporte, mudar valor ou data não o sobrescreve mais. */
const allocationEdited = ref(false)
/** A meta já existe (criada aqui ou antes): nova tentativa chama só o /finish. */
const goalCreated = ref(false)
/** Enquanto a lista de metas carrega, os botões ficam desabilitados para não criar meta repetida. */
const loadingGoals = ref(true)
const finishing = ref<'create' | 'skip' | null>(null)
const heading = ref<HTMLElement | null>(null)

const busy = computed(() => finishing.value !== null)

const parsedTarget = computed(() => parseBRL(form.targetAmount))
const parsedAllocation = computed(() => parseBRL(form.monthlyAllocation))
const parsedDate = computed(() => parseISODate(form.targetDate))

watch([parsedTarget, parsedDate], ([target, date]) => {
  if (allocationEdited.value || target === null || target <= 0 || date === null) return
  form.monthlyAllocation = formatBRLInput(
    suggestedMonthlyAllocation(target, monthsUntil(today, date)),
  )
})

const previewTarget = computed(() => {
  const target = parsedTarget.value
  return target !== null && target > 0 ? formatBRL(target) : formatBRL(0)
})

const forecastText = computed(() => {
  const target = parsedTarget.value
  const allocation = parsedAllocation.value
  if (target !== null && target > 0 && allocation !== null && allocation > 0) {
    const date = projectedCompletion(today, target, allocation)
    if (date === 'too-far') return 'Previsão de conclusão: daqui a mais de 100 anos'
    if (date) return `Previsão de conclusão: ${formatMonthYear(date)}`
  }
  const date = parsedDate.value
  if (date) return `Previsão de conclusão: ${formatMonthYear(date)}`
  return 'Informe um aporte mensal ou uma data-alvo para ver a previsão.'
})

interface ApiError {
  response?: { status?: number; data?: { detail?: string; errors?: Record<string, string> } }
}

function apiDetail(err: unknown): string | undefined {
  return (err as ApiError)?.response?.data?.detail || undefined
}

function clearErrors() {
  for (const key of Object.keys(errors) as Field[]) delete errors[key]
}

/** Validação local, com as mesmas regras e mensagens do backend. */
function validate(): {
  name: string
  targetAmount: number
  targetDate: string | null
  monthlyAllocation: number | null
} | null {
  clearErrors()
  const name = cleanName(form.name)
  if (!name) errors.name = 'Informe o nome da meta'
  else if (name.length > 100) errors.name = 'Use no máximo 100 caracteres no nome da meta'

  let targetAmount: number | null = null
  if (!form.targetAmount.trim()) {
    errors.targetAmount = 'Informe o valor-alvo da meta'
  } else {
    targetAmount = parseBRL(form.targetAmount)
    if (targetAmount === null) {
      errors.targetAmount = 'Digite o valor no formato 25.000,00 (até 2 casas depois da vírgula)'
    } else if (targetAmount <= 0) {
      errors.targetAmount = 'O valor-alvo precisa ser maior que zero'
    }
  }

  let targetDate: string | null = null
  if (form.targetDate) {
    const date = parseISODate(form.targetDate)
    if (date === null) errors.targetDate = 'Escolha uma data válida'
    else if (date < today) errors.targetDate = 'Escolha uma data de hoje em diante'
    else targetDate = form.targetDate
  }

  let monthlyAllocation: number | null = null
  if (form.monthlyAllocation.trim()) {
    monthlyAllocation = parseBRL(form.monthlyAllocation)
    if (monthlyAllocation === null) {
      errors.monthlyAllocation =
        'Digite o aporte no formato 2.083,34 (até 2 casas depois da vírgula)'
    } else if (monthlyAllocation < 0) {
      errors.monthlyAllocation = 'O aporte mensal não pode ser negativo'
    }
  }

  if (Object.keys(errors).length > 0 || targetAmount === null) return null
  return { name, targetAmount, targetDate, monthlyAllocation }
}

async function createAndFinish() {
  // Duplo clique no mesmo tick: o botão ainda não foi desabilitado
  if (busy.value || loadingGoals.value) return
  if (!goalCreated.value) {
    const values = validate()
    if (values === null) return
    finishing.value = 'create'
    try {
      await createGoal(values)
      goalCreated.value = true
    } catch (err) {
      finishing.value = null
      const fieldErrors = (err as ApiError)?.response?.data?.errors ?? {}
      const known = Object.entries(fieldErrors).filter(([key]) => FIELDS.includes(key as Field))
      if ((err as ApiError)?.response?.status === 400 && known.length > 0) {
        Object.assign(errors, Object.fromEntries(known))
      } else {
        emit(
          'error',
          apiDetail(err) ??
            'Não foi possível criar a meta. Verifique sua conexão e tente novamente.',
        )
      }
      return
    }
  }
  finishing.value = 'create'
  await finish(false)
}

async function skip() {
  if (busy.value || loadingGoals.value || goalCreated.value) return
  finishing.value = 'skip'
  await finish(true)
}

async function finish(skipped: boolean) {
  let progress: OnboardingProgress
  try {
    progress = await finishOnboarding(skipped)
  } catch (err) {
    // Só libera os botões no erro: no sucesso a tela troca, e liberar antes permitiria outro POST
    finishing.value = null
    emit(
      'error',
      apiDetail(err) ??
        'Não foi possível concluir o onboarding. Verifique sua conexão e tente novamente.',
    )
    return
  }
  emit('done', progress)
}

onMounted(async () => {
  heading.value?.focus()
  try {
    const [existing] = await listGoals()
    if (existing) {
      // Já há meta ativa (ex.: criada antes de o /finish falhar e a página ser recarregada): mostra-a só leitura
      allocationEdited.value = true
      form.name = existing.name
      form.targetAmount = formatBRLInput(existing.targetAmount)
      form.targetDate = existing.targetDate ?? ''
      form.monthlyAllocation =
        existing.monthlyAllocation === null ? '' : formatBRLInput(existing.monthlyAllocation)
      goalCreated.value = true
    }
  } catch {
    // Sem a lista, segue com o formulário; a proteção contra meta repetida continua na memória da tela
  } finally {
    loadingGoals.value = false
  }
})
</script>
