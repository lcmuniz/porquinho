<template>
  <main class="min-h-screen bg-gray-50 px-4 py-8">
    <div class="mx-auto max-w-3xl space-y-6">
      <div class="flex items-start justify-between gap-4">
        <div>
          <h1 ref="heading" tabindex="-1" class="text-2xl font-semibold text-gray-900 outline-none">
            Ajustar alocações
          </h1>
          <p class="mt-1 text-sm text-gray-600">Quanto guardar por mês em cada meta</p>
        </div>
        <RouterLink
          to="/metas"
          data-testid="back-to-goals"
          class="inline-flex min-h-[44px] items-center rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100"
        >
          Voltar às metas
        </RouterLink>
      </div>

      <!-- Sempre no DOM: leitores de tela só anunciam mudanças numa região live que já existe -->
      <p
        role="status"
        data-testid="allocations-status"
        :class="
          statusMessage
            ? 'rounded-lg border border-gray-200 bg-white px-4 py-3 text-sm text-gray-800'
            : 'sr-only'
        "
      >
        {{ statusMessage }}
      </p>

      <div v-if="loading" class="space-y-2" aria-busy="true" data-testid="allocations-skeleton">
        <div class="h-32 animate-pulse rounded-lg bg-gray-200" />
        <span class="sr-only">Carregando suas metas...</span>
      </div>

      <div
        v-else-if="loadFailed"
        class="space-y-3 rounded-lg border border-gray-200 bg-white p-4 text-center"
        data-testid="allocations-load-failed"
      >
        <p class="text-sm text-gray-600">Não foi possível carregar suas metas.</p>
        <Button variant="outline" class="min-h-[44px]" data-testid="retry-button" @click="load">
          Tentar de novo
        </Button>
      </div>

      <p
        v-else-if="goals.length === 0"
        class="rounded-lg border border-gray-200 bg-white p-4 text-center text-sm text-gray-600"
        data-testid="allocations-empty"
      >
        Você não tem metas ativas.
      </p>

      <form
        v-else
        class="space-y-4"
        novalidate
        data-testid="allocations-form"
        @submit.prevent="save"
      >
        <p
          v-if="formError"
          role="alert"
          data-testid="allocations-form-error"
          class="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800"
        >
          {{ formError }}
        </p>

        <ul class="space-y-3">
          <li
            v-for="goal in goals"
            :key="goal.id"
            class="space-y-2 rounded-lg border border-gray-200 bg-white p-4"
            :data-testid="`allocation-${goal.id}`"
          >
            <h2 class="font-medium break-words text-gray-900">{{ goal.name }}</h2>
            <div class="grid grid-cols-1 gap-3 text-sm sm:grid-cols-2">
              <p class="text-gray-700" data-testid="allocation-before">
                Antes:
                {{
                  goal.monthlyAllocation === null
                    ? 'sem alocação'
                    : `${formatBRL(goal.monthlyAllocation)}/mês`
                }}
              </p>
              <div class="space-y-1">
                <label :for="`allocation-input-${goal.id}`" class="font-medium text-gray-900">
                  Depois (R$)
                </label>
                <Input
                  :id="`allocation-input-${goal.id}`"
                  v-model="texts[goal.id]"
                  class="min-h-[44px]"
                  inputmode="decimal"
                  placeholder="0,00"
                  autocomplete="off"
                  data-testid="allocation-input"
                  :aria-invalid="errors[goal.id] ? 'true' : undefined"
                  :aria-describedby="`allocation-projection-${goal.id}${errors[goal.id] ? ` allocation-error-${goal.id}` : ''}`"
                />
                <p
                  v-if="errors[goal.id]"
                  :id="`allocation-error-${goal.id}`"
                  class="text-red-600"
                  data-testid="allocation-error"
                >
                  {{ errors[goal.id] }}
                </p>
              </div>
            </div>
            <p
              :id="`allocation-projection-${goal.id}`"
              class="text-sm text-gray-700"
              data-testid="allocation-projection"
            >
              {{
                projectionText(
                  goalProjection({ ...goal, monthlyAllocation: preview(goal) }, thisMonth),
                )
              }}
            </p>
          </li>
        </ul>

        <div
          class="space-y-2 rounded-lg border border-gray-200 bg-white p-4 text-sm"
          data-testid="allocations-total"
        >
          <p class="text-gray-900" data-testid="total-before">
            Total antes: <span class="font-medium">{{ formatBRL(totalBefore) }}/mês</span>
          </p>
          <p class="text-gray-900" data-testid="total-after">
            Total depois: <span class="font-medium">{{ formatBRL(totalAfter) }}/mês</span>
          </p>
          <p
            v-if="overAvailable"
            role="alert"
            data-testid="allocations-over-available"
            class="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-amber-900"
          >
            Suas metas somam mais do que você tem disponível
          </p>
        </div>

        <div class="flex justify-end">
          <Button
            type="submit"
            class="min-h-[44px]"
            :disabled="saving"
            data-testid="allocations-save"
          >
            {{ saving ? 'Salvando...' : 'Salvar alterações' }}
          </Button>
        </div>
      </form>
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { currentMonth } from '@/lib/date'
import { goalProjection, projectionText } from '@/lib/goalPace'
import { formatBRL, formatBRLInput, parseBRL } from '@/lib/money'
import { getGoalsSummary, listGoals, updateGoalAllocations } from '@/services/goalService'
import type { Goal, GoalAllocationItem, GoalsSummary } from '@/types/goal'

/**
 * Story 8.5: um campo de alocação por meta ativa, total antes/depois, aviso de disponível e a projeção da 8.4
 * recalculada com o valor digitado. "Salvar alterações" manda só as metas alteradas num PUT (tudo ou nada).
 */
const SAVE_FAILED = 'Não foi possível salvar. Verifique sua conexão e tente novamente.'

const router = useRouter()
const heading = ref<HTMLElement | null>(null)
const goals = ref<Goal[]>([])
const loading = ref(true)
const loadFailed = ref(false)
const summary = ref<GoalsSummary | null>(null)
const texts = reactive<Record<string, string>>({})
const errors = reactive<Record<string, string>>({})
const formError = ref<string | null>(null)
const statusMessage = ref<string | null>(null)
const saving = ref(false)
const thisMonth = currentMonth()

const cents = (value: number | null) => Math.round((value ?? 0) * 100)

/** Valor digitado válido, ou a alocação atual enquanto o campo está vazio ou inválido. */
function preview(goal: Goal): number | null {
  const value = parseBRL(texts[goal.id] ?? '')
  return value !== null && value >= 0 ? value : goal.monthlyAllocation
}

// Somas em centavos: em float, 0,10 + 0,20 passaria de 0,30
const totalBefore = computed(
  () => goals.value.reduce((sum, g) => sum + cents(g.monthlyAllocation), 0) / 100,
)
const totalAfter = computed(() => goals.value.reduce((sum, g) => sum + cents(preview(g)), 0) / 100)
const overAvailable = computed(
  () => !!summary.value && totalAfter.value > 0 && totalAfter.value > summary.value.totalAvailable,
)

/** Mesmas mensagens do diálogo "Editar alocação" (8.2). null = sem alteração (vazio numa meta sem alocação). */
function validate(goal: Goal): number | string | null {
  const text = texts[goal.id] ?? ''
  if (!text.trim()) return goal.monthlyAllocation === null ? null : 'Informe o aporte mensal'
  const value = parseBRL(text)
  if (value === null) return 'Informe um valor válido, com até 2 casas decimais'
  if (value < 0) return 'O aporte mensal não pode ser negativo'
  return value
}

interface ApiError {
  response?: { status?: number; data?: { detail?: string; errors?: Record<string, string> } }
}

async function load() {
  loading.value = true
  loadFailed.value = false
  try {
    // Sem await: o resumo só controla o aviso
    void getGoalsSummary().then(
      (s) => (summary.value = s),
      () => (summary.value = null),
    )
    // Story 8.7: concluídas são somente leitura e ficam fora dos totais
    goals.value = (await listGoals()).filter((g) => !g.completed)
    for (const g of goals.value)
      texts[g.id] = g.monthlyAllocation === null ? '' : formatBRLInput(g.monthlyAllocation)
  } catch {
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

/** Leva o foco ao primeiro campo com erro (ordem das metas), para ele não ficar fora da tela nem mudo. */
async function focusFirstError() {
  await nextTick()
  const goal = goals.value.find((g) => errors[g.id])
  if (goal) document.getElementById(`allocation-input-${goal.id}`)?.focus()
}

async function save() {
  if (saving.value) return
  for (const key of Object.keys(errors)) delete errors[key]
  formError.value = null
  statusMessage.value = null
  const items: GoalAllocationItem[] = []
  for (const goal of goals.value) {
    const value = validate(goal)
    if (typeof value === 'string') errors[goal.id] = value
    else if (
      value !== null &&
      (goal.monthlyAllocation === null || cents(value) !== cents(goal.monthlyAllocation))
    )
      items.push({ goalId: goal.id, monthlyAllocation: value })
  }
  if (Object.keys(errors).length > 0) return focusFirstError()
  if (items.length === 0) {
    statusMessage.value = 'Nenhuma alteração para salvar'
    return
  }
  saving.value = true
  try {
    await updateGoalAllocations(items)
    await router.push({ name: 'goals', state: { allocationsUpdated: true } })
  } catch (err) {
    const response = (err as ApiError)?.response
    // 400 do bean validation vem como errors["items[i].monthlyAllocation"]
    let mapped = false
    for (const [key, message] of Object.entries(response?.data?.errors ?? {})) {
      const index = /^items\[(\d+)]\.monthlyAllocation$/.exec(key)?.[1]
      const item = index === undefined ? undefined : items[Number(index)]
      if (response?.status === 400 && item) {
        errors[item.goalId] = message
        mapped = true
      }
    }
    if (mapped) await focusFirstError()
    else formError.value = response?.data?.detail ?? SAVE_FAILED
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  heading.value?.focus()
  await load()
})
</script>
