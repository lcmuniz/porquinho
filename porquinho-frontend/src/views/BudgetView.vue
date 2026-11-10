<template>
  <main class="min-h-screen bg-gray-50 px-4 py-8">
    <div class="mx-auto max-w-3xl space-y-6">
      <div class="flex items-start justify-between gap-4">
        <div>
          <h1 tabindex="-1" class="text-2xl font-semibold text-gray-900 outline-none">Orçamento</h1>
          <p class="mt-1 text-sm text-gray-600">Quanto você pretende gastar em cada categoria no mês</p>
        </div>
        <RouterLink
          to="/dashboard"
          data-testid="back-to-dashboard"
          class="inline-flex min-h-[44px] items-center rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100"
        >
          Voltar ao dashboard
        </RouterLink>
      </div>

      <!-- Sempre no DOM: leitores de tela só anunciam mudanças numa região live que já existe -->
      <p
        role="status"
        data-testid="budget-success"
        :class="
          successMessage
            ? 'rounded-lg border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-800'
            : 'sr-only'
        "
      >
        {{ successMessage }}
      </p>

      <p
        v-if="actionError"
        role="alert"
        data-testid="budget-error"
        class="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800"
      >
        {{ actionError }}
      </p>

      <nav aria-label="Mês" class="flex items-center justify-between gap-2">
        <Button variant="outline" class="min-h-[44px]" data-testid="previous-month" @click="changeMonth(-1)">
          ‹ Mês anterior
        </Button>
        <h2 class="text-base font-semibold text-gray-900 first-letter:uppercase" data-testid="month-label">
          {{ monthLabel }}
        </h2>
        <Button variant="outline" class="min-h-[44px]" data-testid="next-month" @click="changeMonth(1)">
          Próximo ›
        </Button>
      </nav>

      <div v-if="loading" class="space-y-2" aria-busy="true" data-testid="budget-skeleton">
        <div class="h-32 animate-pulse rounded-lg bg-gray-200" />
        <span class="sr-only">Carregando o orçamento...</span>
      </div>

      <div
        v-else-if="loadError"
        role="alert"
        class="flex flex-col gap-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3 sm:flex-row sm:items-center sm:justify-between"
        data-testid="budget-load-error"
      >
        <p class="text-sm text-red-800">{{ loadError }}</p>
        <Button variant="outline" class="min-h-[44px]" @click="load">Tentar de novo</Button>
      </div>

      <form v-else-if="budget" class="space-y-6" novalidate data-testid="budget-form" @submit.prevent="save">
        <div class="space-y-1 rounded-lg border border-gray-200 bg-white p-4 text-sm" data-testid="budget-totals">
          <p class="text-gray-900">
            Total alocado: <span class="font-semibold" data-testid="total-allocated">{{ formatBRL(totalAllocated) }}</span>
          </p>
          <p class="text-gray-900">
            Total disponível nas contas:
            <span class="font-semibold" data-testid="total-available">{{ formatBRL(budget.totalAvailable) }}</span>
          </p>
          <p
            v-if="overAvailable"
            role="alert"
            data-testid="over-available"
            class="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-amber-900"
          >
            Você está alocando mais do que tem disponível
          </p>
        </div>

        <p v-if="notice" role="status" data-testid="budget-notice" class="text-sm text-gray-700">{{ notice }}</p>

        <p
          v-if="groups.length === 0"
          class="rounded-lg border border-gray-200 bg-white p-4 text-center text-sm text-gray-600"
          data-testid="budget-empty"
        >
          Você ainda não tem categorias para orçar.
        </p>

        <section
          v-for="group in groups"
          :key="group.parent.id"
          class="space-y-3 rounded-lg border border-gray-200 bg-white p-4"
          :aria-labelledby="`group-${group.parent.id}`"
          :data-testid="`group-${group.parent.id}`"
        >
          <h3 :id="`group-${group.parent.id}`" class="text-sm font-semibold text-gray-900">
            {{ group.parent.categoryName }}
          </h3>
          <ul class="space-y-4">
            <li
              v-for="child in group.children"
              :key="child.id"
              class="space-y-2"
              :data-testid="`category-${child.id}`"
            >
              <div class="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                <label :for="`amount-${child.id}`" class="font-medium text-gray-900">{{ child.categoryName }}</label>
                <div class="sm:w-40">
                  <Input
                    :id="`amount-${child.id}`"
                    v-model="values[child.id]"
                    inputmode="decimal"
                    placeholder="0,00"
                    class="min-h-[44px]"
                    :aria-invalid="fieldErrors[child.id] ? 'true' : undefined"
                    :aria-describedby="fieldErrors[child.id] ? `amount-error-${child.id}` : undefined"
                    :data-testid="`amount-${child.id}`"
                  />
                </div>
              </div>
              <p
                v-if="fieldErrors[child.id]"
                :id="`amount-error-${child.id}`"
                class="text-sm text-red-700"
                :data-testid="`amount-error-${child.id}`"
              >
                {{ fieldErrors[child.id] }}
              </p>
              <div class="flex flex-wrap gap-x-4 gap-y-1 text-sm text-gray-700">
                <span :data-testid="`spent-${child.id}`">Gasto: {{ formatBRL(byCategory[child.id]!.spent) }}</span>
                <span v-if="byCategory[child.id]!.committed !== null" :data-testid="`committed-${child.id}`">
                  Comprometido: {{ formatBRL(byCategory[child.id]!.committed!) }}
                </span>
              </div>
              <p
                v-if="byCategory[child.id]!.allocated === null"
                class="text-sm text-gray-500"
                :data-testid="`no-budget-${child.id}`"
              >
                Sem orçamento definido
              </p>
              <div v-else class="space-y-1">
                <div
                  role="progressbar"
                  aria-valuemin="0"
                  aria-valuemax="100"
                  :aria-valuenow="progress(byCategory[child.id]!)"
                  :aria-label="`Gasto de ${child.categoryName}`"
                  class="h-2 w-full overflow-hidden rounded-full bg-gray-200"
                  :data-testid="`progress-${child.id}`"
                >
                  <div
                    class="h-full"
                    :class="statusOf(byCategory[child.id]!)?.bar ?? 'bg-gray-600'"
                    :style="{ width: `${progress(byCategory[child.id]!)}%` }"
                    :data-testid="`progress-bar-${child.id}`"
                  />
                </div>
                <div class="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-gray-700">
                  <p :data-testid="`progress-text-${child.id}`">
                    {{ formatBRL(byCategory[child.id]!.spent) }} de {{ formatBRL(byCategory[child.id]!.allocated!) }}
                    · Restante: {{ formatBRL(byCategory[child.id]!.remaining ?? 0) }}
                  </p>
                  <span
                    v-if="statusOf(byCategory[child.id]!)"
                    class="inline-flex items-center gap-1 font-medium text-gray-900"
                    :data-testid="`status-${child.id}`"
                  >
                    <component
                      :is="statusOf(byCategory[child.id]!)!.icon"
                      class="size-4"
                      :class="statusOf(byCategory[child.id]!)!.text"
                      aria-hidden="true"
                    />
                    {{ statusOf(byCategory[child.id]!)!.label }}
                  </span>
                </div>
              </div>
            </li>
          </ul>
        </section>

        <div v-if="groups.length > 0" class="flex flex-col gap-2 sm:flex-row sm:justify-end">
          <Button
            type="button"
            variant="outline"
            class="min-h-[44px]"
            :disabled="saving || repeating"
            data-testid="repeat-previous"
            @click="repeatPrevious"
          >
            Repetir orçamento do mês anterior
          </Button>
          <Button type="submit" class="min-h-[44px]" :disabled="saving || repeating" data-testid="save-budget">
            {{ saving ? 'Salvando...' : 'Salvar orçamento' }}
          </Button>
        </div>
      </form>
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { CircleAlert, CircleCheck, TriangleAlert } from 'lucide-vue-next'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { formatBRL, formatBRLInput, parseBRL } from '@/lib/money'
import { getBudget, saveBudget } from '@/services/budgetService'
import { listCategories } from '@/services/categoryService'
import type { Budget, BudgetStatus, CategoryBudget } from '@/types/budget'
import type { Category } from '@/types/category'

/**
 * Story 6.3: orçamento do mês por categoria filha, agrupado por grupo. O gasto e o comprometido vêm do backend; a
 * barra usa o valor salvo. O total alocado acompanha o que está digitado, e o aviso de excesso não impede salvar.
 * Story 6.4: restante e status vêm do backend (refletem o salvo); o status tem cor, ícone e rótulo, nunca só cor.
 */
const SUCCESS_TIMEOUT_MS = 5000
const LOAD_FAILED = 'Não foi possível carregar o orçamento.'
const SAVE_FAILED = 'Não foi possível salvar o orçamento. Verifique sua conexão e tente novamente.'
const REPEAT_FAILED = 'Não foi possível carregar o orçamento do mês anterior.'
const INVALID_AMOUNT = 'Informe um valor válido, zero ou maior'

interface ApiError {
  response?: { status?: number; data?: { detail?: string } }
}

/** "AAAA-MM" do mês atual em São Paulo (en-CA formata a data como AAAA-MM-DD). */
function currentMonth(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo' }).format(new Date()).slice(0, 7)
}

function shiftMonth(value: string, delta: number): string {
  const [year, monthNumber] = value.split('-').map(Number)
  const date = new Date(Date.UTC(year!, monthNumber! - 1 + delta, 1))
  return `${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, '0')}`
}

const month = ref(currentMonth())
const budget = ref<Budget | null>(null)
const categories = ref<Category[]>([])
const values = ref<Record<string, string>>({})
const loading = ref(true)
const loadError = ref<string | null>(null)
const saving = ref(false)
const repeating = ref(false)
const actionError = ref<string | null>(null)
const successMessage = ref<string | null>(null)
const notice = ref<string | null>(null)

const monthLabel = computed(() => {
  const [year, monthNumber] = month.value.split('-').map(Number)
  return new Intl.DateTimeFormat('pt-BR', { month: 'long', year: 'numeric', timeZone: 'UTC' }).format(
    Date.UTC(year!, monthNumber! - 1, 1),
  )
})

const byCategory = computed<Record<string, CategoryBudget>>(() =>
  Object.fromEntries((budget.value?.categories ?? []).map((c) => [c.categoryId, c])),
)

const byName = (a: Category, b: Category) =>
  a.categoryName.localeCompare(b.categoryName, 'pt-BR', { sensitivity: 'base' }) || a.id.localeCompare(b.id)

// Só as filhas que o backend devolveu (ativas); grupos sem filhas não aparecem
const groups = computed(() =>
  categories.value
    .filter((c) => c.parentCategoryId === null)
    .sort(byName)
    .map((parent) => ({
      parent,
      children: categories.value
        .filter((c) => c.parentCategoryId === parent.id && byCategory.value[c.id])
        .sort(byName),
    }))
    .filter((g) => g.children.length > 0),
)

const fieldErrors = computed<Record<string, string | null>>(() =>
  Object.fromEntries(
    Object.entries(values.value).map(([id, text]) => {
      if (!text.trim()) return [id, null]
      const value = parseBRL(text)
      return [id, value === null || value < 0 ? INVALID_AMOUNT : null]
    }),
  ),
)

// Soma em centavos: em float, 0,10 + 0,20 passaria de 0,30 e acenderia o aviso de excesso à toa
const totalAllocated = computed(
  () =>
    Object.values(values.value).reduce((cents, text) => {
      const value = text.trim() ? parseBRL(text) : null
      return value !== null && value >= 0 ? cents + Math.round(value * 100) : cents
    }, 0) / 100,
)

const overAvailable = computed(
  () => !!budget.value && totalAllocated.value > 0 && totalAllocated.value > budget.value.totalAvailable,
)

// Green/Yellow/Red 600 na barra; o ícone amarelo usa o 700 para ter contraste 3:1 (WCAG 1.4.11). O rótulo fica escuro
const STATUS: Record<BudgetStatus, { label: string; icon: typeof CircleCheck; bar: string; text: string }> = {
  HEALTHY: { label: 'Saudável', icon: CircleCheck, bar: 'bg-green-600', text: 'text-green-600' },
  LOW: { label: 'Saldo baixo', icon: TriangleAlert, bar: 'bg-yellow-600', text: 'text-yellow-700' },
  OVERSPENT: { label: 'Estourada', icon: CircleAlert, bar: 'bg-red-600', text: 'text-red-600' },
}

function statusOf(c: CategoryBudget) {
  return c.status ? STATUS[c.status] : null
}

function progress(c: CategoryBudget): number {
  if (!c.allocated) return c.spent > 0 ? 100 : 0
  return Math.min(100, Math.round((c.spent / c.allocated) * 100))
}

function fill(data: Budget) {
  budget.value = data
  values.value = Object.fromEntries(
    data.categories.map((c) => [c.categoryId, c.allocated === null ? '' : formatBRLInput(c.allocated)]),
  )
}

let successTimer: ReturnType<typeof setTimeout> | undefined

function setSuccess(message: string) {
  clearTimeout(successTimer)
  successMessage.value = message
  successTimer = setTimeout(() => (successMessage.value = null), SUCCESS_TIMEOUT_MS)
}

/** Sequência das cargas: só a resposta da carga mais recente é aplicada. */
let request = 0

async function load() {
  const currentRequest = ++request
  loading.value = true
  loadError.value = null
  actionError.value = null
  clearTimeout(successTimer)
  successMessage.value = null
  notice.value = null
  try {
    const [data, list] = await Promise.all([getBudget(month.value), listCategories()])
    if (currentRequest !== request) return
    categories.value = list
    fill(data)
  } catch (err) {
    if (currentRequest !== request) return
    budget.value = null
    loadError.value = (err as ApiError).response?.status === 400 ? ((err as ApiError).response?.data?.detail ?? LOAD_FAILED) : LOAD_FAILED
  } finally {
    if (currentRequest === request) loading.value = false
  }
}

function changeMonth(delta: number) {
  month.value = shiftMonth(month.value, delta)
  void load()
}

async function repeatPrevious() {
  if (!budget.value) return
  repeating.value = true
  notice.value = null
  actionError.value = null
  const target = month.value
  try {
    const previous = await getBudget(shiftMonth(target, -1))
    if (target !== month.value) return
    const allocated = previous.categories.filter((c) => c.allocated !== null && byCategory.value[c.categoryId])
    if (allocated.length === 0) {
      notice.value = 'O mês anterior não tem orçamento'
      return
    }
    const next: Record<string, string> = Object.fromEntries(Object.keys(values.value).map((id) => [id, '']))
    for (const c of allocated) next[c.categoryId] = formatBRLInput(c.allocated!)
    values.value = next
  } catch {
    actionError.value = REPEAT_FAILED
  } finally {
    repeating.value = false
  }
}

async function save() {
  if (saving.value || repeating.value || !budget.value) return
  actionError.value = null
  if (Object.values(fieldErrors.value).some(Boolean)) return
  const allocations = Object.entries(values.value)
    .filter(([, text]) => text.trim())
    .map(([categoryId, text]) => ({ categoryId, amount: parseBRL(text)! }))
  const target = month.value
  saving.value = true
  try {
    const data = await saveBudget(target, allocations)
    if (target !== month.value) return
    fill(data)
    notice.value = null
    setSuccess('Orçamento mensal definido')
  } catch (err) {
    const e = err as ApiError
    actionError.value = e.response?.status === 400 ? (e.response.data?.detail ?? SAVE_FAILED) : SAVE_FAILED
  } finally {
    saving.value = false
  }
}

onMounted(load)
onBeforeUnmount(() => clearTimeout(successTimer))
</script>
