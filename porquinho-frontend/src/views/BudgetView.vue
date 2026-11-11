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
          <p v-if="summary" class="text-gray-900" data-testid="status-summary">{{ summary }}</p>
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
              :class="{ '-mx-2 rounded-md bg-red-50 p-2': byCategory[child.id]!.status === 'OVERSPENT' }"
              :data-testid="`category-${child.id}`"
            >
              <div class="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                <div class="flex items-center gap-3">
                  <label :for="`amount-${child.id}`" class="font-medium text-gray-900">{{ child.categoryName }}</label>
                  <RouterLink
                    :to="`/orcamento/categorias/${child.id}/historico`"
                    :aria-label="`Histórico de ${child.categoryName}`"
                    class="inline-flex min-h-[44px] items-center text-sm font-medium text-purple-700 underline hover:text-purple-800"
                    :data-testid="`history-${child.id}`"
                  >
                    Histórico
                  </RouterLink>
                </div>
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
                <div class="flex items-center gap-2">
                  <div
                    role="progressbar"
                    aria-valuemin="0"
                    aria-valuemax="100"
                    :aria-valuenow="progress(byCategory[child.id]!)"
                    :aria-valuetext="percent(byCategory[child.id]!) !== null ? `${percent(byCategory[child.id]!)}%` : undefined"
                    :aria-label="`Gasto de ${child.categoryName}`"
                    :title="exact(byCategory[child.id]!)"
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
                  <span
                    v-if="percent(byCategory[child.id]!) !== null"
                    class="shrink-0 text-sm font-medium text-gray-900"
                    :data-testid="`percent-${child.id}`"
                  >
                    {{ percent(byCategory[child.id]!) }}%
                  </span>
                </div>
                <div class="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-gray-700">
                  <p :data-testid="`progress-text-${child.id}`">
                    {{ formatBRL(byCategory[child.id]!.spent) }} de {{ formatBRL(byCategory[child.id]!.allocated!) }}
                    · Restante: {{ formatBRL(byCategory[child.id]!.remaining ?? 0) }}
                  </p>
                  <span
                    v-if="statusOf(byCategory[child.id]!)"
                    class="inline-flex items-center gap-1 font-medium text-gray-900"
                    :title="exact(byCategory[child.id]!)"
                    :data-testid="`status-${child.id}`"
                  >
                    <component
                      :is="statusOf(byCategory[child.id]!)!.icon"
                      class="size-4"
                      :class="statusOf(byCategory[child.id]!)!.text"
                      aria-hidden="true"
                    />
                    {{ statusOf(byCategory[child.id]!)!.label(byCategory[child.id]!.remaining ?? 0) }}
                  </span>
                </div>
              </div>
            </li>
          </ul>
        </section>

        <div v-if="groups.length > 0" class="flex flex-col gap-2 sm:flex-row sm:justify-end">
          <Button
            v-if="!closedMonth"
            ref="reallocateButton"
            type="button"
            variant="outline"
            class="min-h-[44px]"
            :disabled="saving || repeating || dirty || !canReallocate"
            :aria-describedby="dirty ? 'reallocate-hint' : undefined"
            data-testid="open-reallocate"
            @click="openReallocate"
          >
            Mover dinheiro
          </Button>
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
        <p
          v-if="groups.length > 0 && !closedMonth && dirty"
          id="reallocate-hint"
          class="text-right text-sm text-gray-600"
          data-testid="reallocate-hint"
        >
          Salve o orçamento antes de mover dinheiro
        </p>
      </form>
    </div>

    <!-- Story 6.5: mover dinheiro entre categorias -->
    <Dialog :open="reallocateOpen" @update:open="onReallocateOpenChange">
      <DialogContent
        :show-close-button="false"
        data-testid="reallocate-dialog"
        @open-auto-focus="onReallocateOpenAutoFocus"
        @close-auto-focus="onReallocateCloseAutoFocus"
      >
        <DialogHeader>
          <DialogTitle>Mover dinheiro</DialogTitle>
          <DialogDescription>Tire dinheiro de uma categoria e coloque em outra. O total alocado não muda.</DialogDescription>
        </DialogHeader>
        <p
          v-if="reallocateError"
          role="alert"
          data-testid="reallocate-error"
          class="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800"
        >
          {{ reallocateError }}
        </p>
        <ReallocateForm
          ref="reallocateForm"
          v-model:submitting="reallocating"
          :month="month"
          :options="reallocateOptions"
          @attempt="reallocateError = null"
          @moved="onMoved"
          @failed="(detail) => (reallocateError = detail ?? REALLOCATE_FAILED)"
        />
        <Button
          variant="outline"
          class="min-h-[44px] w-full"
          data-testid="cancel-reallocate"
          :disabled="reallocating"
          @click="reallocateOpen = false"
        >
          Cancelar
        </Button>
      </DialogContent>
    </Dialog>
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import ReallocateForm from '@/components/budget/ReallocateForm.vue'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { statusOf } from '@/lib/budgetStatus'
import { currentMonth, formatMonthBR, shiftMonth } from '@/lib/date'
import { formatBRL, formatBRLInput, parseBRL } from '@/lib/money'
import { getBudget, saveBudget } from '@/services/budgetService'
import { listCategories } from '@/services/categoryService'
import type { Budget, CategoryBudget } from '@/types/budget'
import type { Category } from '@/types/category'

/**
 * Story 6.3: orçamento do mês por categoria filha, agrupado por grupo. O gasto e o comprometido vêm do backend; a
 * barra usa o valor salvo. O total alocado acompanha o que está digitado, e o aviso de excesso não impede salvar.
 * Story 6.4: restante e status vêm do backend (refletem o salvo); o status tem cor, ícone e rótulo, nunca só cor.
 * Story 6.5: "Mover dinheiro" abre um diálogo; fica bloqueado com valores não salvos (o sucesso recarrega os valores
 * do backend e apagaria o que foi digitado) e some em mês fechado.
 * Story 6.7: chip com mensagem por status, estouradas (e seus grupos) no topo com fundo vermelho, percentual real ao
 * lado da barra e resumo por status. Tudo usa o status salvo: digitar sem salvar não reordena.
 */
const SUCCESS_TIMEOUT_MS = 5000
const LOAD_FAILED = 'Não foi possível carregar o orçamento.'
const SAVE_FAILED = 'Não foi possível salvar o orçamento. Verifique sua conexão e tente novamente.'
const REPEAT_FAILED = 'Não foi possível carregar o orçamento do mês anterior.'
const REALLOCATE_FAILED = 'Não foi possível mover o dinheiro. Verifique sua conexão e tente novamente.'
const INVALID_AMOUNT = 'Informe um valor válido, zero ou maior'

interface ApiError {
  response?: { status?: number; data?: { detail?: string } }
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
const reallocateOpen = ref(false)
const reallocating = ref(false)
const reallocateError = ref<string | null>(null)
const reallocateForm = ref<InstanceType<typeof ReallocateForm> | null>(null)
const reallocateButton = ref<InstanceType<typeof Button> | null>(null)

const monthLabel = computed(() => formatMonthBR(month.value))

const byCategory = computed<Record<string, CategoryBudget>>(() =>
  Object.fromEntries((budget.value?.categories ?? []).map((c) => [c.categoryId, c])),
)

const byName = (a: Category, b: Category) =>
  a.categoryName.localeCompare(b.categoryName, 'pt-BR', { sensitivity: 'base' }) || a.id.localeCompare(b.id)

const overspent = (c: Category) => byCategory.value[c.id]?.status === 'OVERSPENT'
// Estouradas primeiro, depois por nome
const overspentFirst = (a: Category, b: Category) => Number(overspent(b)) - Number(overspent(a)) || byName(a, b)

// Só as filhas que o backend devolveu (ativas); grupos sem filhas não aparecem. Grupos com estourada vêm antes
const groups = computed(() =>
  categories.value
    .filter((c) => c.parentCategoryId === null)
    .sort(byName)
    .map((parent) => ({
      parent,
      children: categories.value
        .filter((c) => c.parentCategoryId === parent.id && byCategory.value[c.id])
        .sort(overspentFirst),
    }))
    .filter((g) => g.children.length > 0)
    .sort((a, b) => Number(overspent(b.children[0]!)) - Number(overspent(a.children[0]!))),
)

// "X categorias saudáveis, Y baixas, Z estouradas"; some quando nada tem orçamento
const summary = computed(() => {
  const count = { HEALTHY: 0, LOW: 0, OVERSPENT: 0 }
  for (const c of groups.value.flatMap((g) => g.children)) {
    const status = byCategory.value[c.id]!.status
    if (status) count[status]++
  }
  if (count.HEALTHY + count.LOW + count.OVERSPENT === 0) return null
  const plural = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`
  return [
    plural(count.HEALTHY, 'categoria saudável', 'categorias saudáveis'),
    plural(count.LOW, 'baixa', 'baixas'),
    plural(count.OVERSPENT, 'estourada', 'estouradas'),
  ].join(', ')
})

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

// AAAA-MM compara como texto
const closedMonth = computed(() => month.value < currentMonth())

// Compara o valor, não o texto: "400" num campo salvo como "400,00" não é edição; texto inválido conta como edição
const dirty = computed(() =>
  (budget.value?.categories ?? []).some((c) => {
    const text = (values.value[c.categoryId] ?? '').trim()
    return (text ? (parseBRL(text) ?? text) : null) !== c.allocated
  }),
)

const reallocateOptions = computed(() =>
  groups.value.flatMap((g) =>
    g.children.map((c) => ({ id: c.id, name: c.categoryName, remaining: byCategory.value[c.id]!.remaining })),
  ),
)

// Precisa de um destino diferente e de uma origem com restante > 0
const canReallocate = computed(
  () => reallocateOptions.value.length >= 2 && reallocateOptions.value.some((o) => (o.remaining ?? 0) > 0),
)

function progress(c: CategoryBudget): number {
  if (!c.allocated) return c.spent > 0 ? 100 : 0
  return Math.min(100, Math.round((c.spent / c.allocated) * 100))
}

// Percentual real, pode passar de 100; sem alocado não há base
function percent(c: CategoryBudget): number | null {
  return c.allocated ? Math.round((c.spent / c.allocated) * 100) : null
}

function exact(c: CategoryBudget): string {
  return `${formatBRL(c.spent)} de ${formatBRL(c.allocated ?? 0)}`
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

function openReallocate() {
  reallocateError.value = null
  actionError.value = null
  notice.value = null
  reallocateOpen.value = true
}

function onReallocateOpenChange(open: boolean) {
  // Não fecha no meio do envio (Esc ou clique fora)
  if (!open && reallocating.value) return
  reallocateOpen.value = open
}

function onReallocateOpenAutoFocus(event: Event) {
  event.preventDefault()
  if (reallocateForm.value) reallocateForm.value.focus()
  else void nextTick(() => reallocateForm.value?.focus())
}

function onReallocateCloseAutoFocus(event: Event) {
  event.preventDefault()
  void nextTick(() => (reallocateButton.value?.$el as HTMLElement | undefined)?.focus())
}

function onMoved(data: Budget, message: string) {
  reallocateOpen.value = false
  fill(data)
  setSuccess(message)
}

onMounted(load)
onBeforeUnmount(() => clearTimeout(successTimer))
</script>
