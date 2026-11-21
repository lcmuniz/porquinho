<template>
  <main class="px-4 py-6 md:px-8 md:py-8">
    <div class="mx-auto max-w-4xl space-y-6">
      <div class="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h1 tabindex="-1" class="text-2xl font-bold text-gray-900 md:text-[28px] outline-none">Orçamento</h1>
          <p class="mt-1 text-sm text-gray-600">Quanto você pretende gastar em cada categoria no mês</p>
        </div>
        <div class="flex flex-wrap gap-2 sm:justify-end">
          <RouterLink
            to="/orcamento/comparativo"
            data-testid="comparison-link"
            class="inline-flex min-h-[44px] items-center rounded-[10px] border border-gray-300 bg-white px-4 py-2 text-sm font-semibold text-gray-900 hover:bg-gray-50"
          >
            Planejado × real
          </RouterLink>
          <Button
            variant="outline"
            class="min-h-[44px]"
            data-testid="budget-export"
            @click="exportOpen = true"
          >
            Exportar relatório
          </Button>
        </div>
      </div>

      <!-- Sem a barra de ações (nenhuma categoria), a faixa de sucesso fica no topo -->
      <FeedbackBanner
        v-if="groups.length === 0"
        :message="successMessage"
        :action="successAction"
        testid="budget-success"
        @pause="pauseSuccess"
        @resume="resumeSuccess"
      />

      <p
        v-if="actionError"
        role="alert"
        data-testid="budget-error"
        class="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800"
      >
        {{ actionError }}
      </p>

      <nav aria-label="Mês" class="flex items-center justify-between gap-2">
        <Button variant="outline" class="min-h-[44px]" data-testid="previous-month" aria-label="Mês anterior" @click="changeMonth(-1)">
          <ChevronLeft class="size-5" aria-hidden="true" />
          <span class="hidden sm:inline">Mês anterior</span>
        </Button>
        <h2 class="text-lg font-bold whitespace-nowrap text-gray-900 first-letter:uppercase" data-testid="month-label">
          {{ monthLabel }}
        </h2>
        <Button variant="outline" class="min-h-[44px]" data-testid="next-month" aria-label="Próximo mês" @click="changeMonth(1)">
          <span class="hidden sm:inline">Próximo</span>
          <ChevronRight class="size-5" aria-hidden="true" />
        </Button>
      </nav>

      <div v-if="loading" class="space-y-2" aria-busy="true" data-testid="budget-skeleton">
        <div class="h-32 animate-pulse rounded-lg bg-gray-200" />
        <span class="sr-only">Carregando o orçamento…</span>
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
        <div class="space-y-3 rounded-lg border border-gray-200 bg-white p-4 text-sm md:p-6" data-testid="budget-totals">
          <div class="grid grid-cols-2 gap-4" :class="{ 'sm:grid-cols-3': totalCommitted }">
            <!-- Rótulos com a mesma altura (44px, a do botão de ajuda), para os valores ficarem alinhados -->
            <p class="text-gray-600">
              <span class="flex min-h-11 items-center gap-1">
                Total alocado
                <HelpTip term="alocado" testid="allocated">
                  Alocado é o dinheiro que você separou para cada categoria neste mês, como envelopes. O ideal é que o
                  total não passe do que está disponível nas contas.
                </HelpTip>
              </span>
              <span class="block text-xl font-bold text-gray-900 md:text-2xl" data-testid="total-allocated">{{ formatBRL(totalAllocated) }}</span>
            </p>
            <p class="text-gray-600">
              <span class="flex min-h-11 items-center">Disponível nas contas</span>
              <span class="block text-xl font-bold text-gray-900 md:text-2xl" data-testid="total-available">{{ formatBRL(budget.totalAvailable) }}</span>
            </p>
            <!-- Critique de 20/11/2026: a ajuda do termo fica aqui, uma vez, e não na primeira linha da lista -->
            <p v-if="totalCommitted" class="col-span-2 text-gray-600 sm:col-span-1">
              <span class="flex min-h-11 items-center gap-1">
                Comprometido
                <HelpTip term="comprometido" testid="committed">
                  Comprometido é a soma das parcelas de compras parceladas que ainda vão cair neste mês. Elas já estão
                  confirmadas, então esse dinheiro já tem destino.
                </HelpTip>
              </span>
              <span class="block text-xl font-bold text-gray-900 md:text-2xl" data-testid="total-committed">{{ formatBRL(totalCommitted) }}</span>
            </p>
          </div>
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
          <h3 :id="`group-${group.parent.id}`" class="text-base font-bold text-gray-900">
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
              <div class="flex items-center justify-between gap-3">
                <div class="flex min-w-0 flex-wrap items-center gap-x-3">
                  <label :for="`amount-${child.id}`" class="min-w-0 font-semibold break-words text-gray-900">{{ child.categoryName }}</label>
                  <RouterLink
                    :to="`/orcamento/categorias/${child.id}/historico`"
                    :aria-label="`Histórico de ${child.categoryName}`"
                    class="inline-flex min-h-[44px] items-center text-sm font-medium text-purple-700 underline hover:text-purple-800"
                    :data-testid="`history-${child.id}`"
                  >
                    Histórico
                  </RouterLink>
                </div>
                <div class="w-32 shrink-0 sm:w-40">
                  <Input
                    :id="`amount-${child.id}`"
                    v-model="values[child.id]"
                    inputmode="decimal"
                    autocomplete="off"
                    placeholder="0,00"
                    class="min-h-[44px] scroll-mb-40 text-right lg:scroll-mb-24"
                    :aria-invalid="fieldErrors[child.id] ? 'true' : undefined"
                    :aria-describedby="fieldErrors[child.id] ? `amount-error-${child.id}` : undefined"
                    :data-testid="`amount-${child.id}`"
                    @focus="keepAboveBar"
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
              <div class="flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-gray-700">
                <!-- Critique de 20/11/2026 (3ª): com orçamento, o gasto já está em "R$ X de R$ Y"; comprometido só se houver -->
                <span v-if="byCategory[child.id]!.allocated === null" :data-testid="`spent-${child.id}`">
                  Gasto: {{ formatBRL(byCategory[child.id]!.spent) }}
                </span>
                <span v-if="(byCategory[child.id]!.committed ?? 0) > 0" :data-testid="`committed-${child.id}`">
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
                  <BudgetStatusLabel
                    v-if="byCategory[child.id]!.status"
                    class="text-gray-900"
                    :status="byCategory[child.id]!.status"
                    :remaining="byCategory[child.id]!.remaining"
                    :title="exact(byCategory[child.id]!)"
                    :data-testid="`status-${child.id}`"
                  />
                </div>
              </div>
            </li>
          </ul>
        </section>

        <section
          v-if="removedCategories.length > 0"
          class="space-y-3 rounded-lg border border-gray-200 bg-white p-4"
          aria-labelledby="group-removed"
          data-testid="group-removed"
        >
          <h3 id="group-removed" class="text-sm font-semibold text-gray-900">Categorias excluídas</h3>
          <ul class="space-y-2">
            <li
              v-for="c in removedCategories"
              :key="c.categoryId"
              class="flex flex-wrap justify-between gap-x-4 text-sm text-gray-700"
              :data-testid="`removed-${c.categoryId}`"
            >
              <span class="font-medium text-gray-900">{{ c.removedCategoryName }}</span>
              <span>{{ formatBRL(c.spent) }} de {{ formatBRL(c.allocated ?? 0) }}</span>
            </li>
          </ul>
        </section>

        <!-- novo-design: barra de ações fixa; no celular fica acima da barra de navegação inferior -->
        <div
          v-if="groups.length > 0"
          class="sticky bottom-16 z-20 -mx-4 grid grid-cols-[minmax(0,1fr)_auto] gap-2 border-t border-gray-200 bg-white/95 px-4 py-3 backdrop-blur sm:flex sm:items-center sm:justify-end md:-mx-8 md:px-8 lg:bottom-0"
          data-testid="budget-actions"
        >
          <p v-if="dirty" class="col-span-2 text-sm font-semibold text-amber-800 sm:mr-auto" data-testid="unsaved-changes">
            Alterações não salvas
            <!-- Critique de 20/11/2026 (3ª): a dica fica na barra, à vista; no celular ela vai no item do menu "⋯" -->
            <span
              v-if="!closedMonth"
              id="reallocate-hint"
              class="hidden font-normal text-gray-600 sm:block"
              data-testid="reallocate-hint"
            >
              Salve antes de mover dinheiro
            </span>
          </p>
          <!-- Critique de 20/11/2026: a confirmação (com "Desfazer") aparece aqui, junto do botão que salvou -->
          <FeedbackBanner
            ref="feedback"
            class="col-span-2 sm:mr-auto"
            :message="successMessage"
            :action="successAction"
            testid="budget-success"
            @pause="pauseSuccess"
            @resume="resumeSuccess"
          />
          <Button
            v-if="!closedMonth"
            ref="reallocateButton"
            type="button"
            variant="outline"
            class="hidden min-h-[44px] sm:inline-flex"
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
            :class="closedMonth ? 'col-span-2' : 'hidden sm:inline-flex'"
            :disabled="saving || repeating"
            data-testid="repeat-previous"
            @click="repeatPrevious"
          >
            <span class="sm:hidden">Repetir mês anterior</span>
            <span class="hidden sm:inline">Repetir orçamento do mês anterior</span>
          </Button>
          <Button
            type="submit"
            class="min-h-[44px]"
            :class="{ 'col-span-2': closedMonth }"
            :disabled="saving || repeating || !dirty"
            data-testid="save-budget"
          >
            {{ saving ? 'Salvando…' : 'Salvar orçamento' }}
          </Button>
          <!-- Critique de 20/11/2026 (3ª): no celular, Mover e Repetir vão para o "⋯" e a barra fica numa linha -->
          <DropdownMenu v-if="!closedMonth" :modal="false">
            <DropdownMenuTrigger as-child>
              <Button
                ref="moreButton"
                type="button"
                variant="outline"
                class="min-h-[44px] min-w-[44px] sm:hidden"
                aria-label="Mais ações do orçamento"
                data-testid="budget-more"
              >
                <MoreHorizontal class="size-5" aria-hidden="true" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" class="min-w-56">
              <DropdownMenuItem
                class="min-h-[44px] flex-col items-start gap-0"
                :disabled="saving || repeating || dirty || !canReallocate"
                data-testid="menu-reallocate"
                @select="openReallocate"
              >
                Mover dinheiro
                <span v-if="dirty" class="text-xs text-gray-600">Salve o orçamento antes</span>
              </DropdownMenuItem>
              <DropdownMenuItem
                class="min-h-[44px]"
                :disabled="saving || repeating"
                data-testid="menu-repeat"
                @select="repeatPrevious"
              >
                Repetir mês anterior
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
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

    <BudgetExportDialog
      v-model:open="exportOpen"
      :initial-month="month"
      @exported="setSuccess('Relatório de orçamento exportado')"
    />

  </main>
</template>

<script setup lang="ts">
import { ChevronLeft, ChevronRight, MoreHorizontal } from 'lucide-vue-next'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useFeedback } from '@/composables/useFeedback'
import { useEventListener } from '@vueuse/core'
import { onBeforeRouteLeave, RouterLink, useRoute, useRouter } from 'vue-router'
import BudgetExportDialog from '@/components/budget/BudgetExportDialog.vue'
import BudgetStatusLabel from '@/components/budget/BudgetStatusLabel.vue'
import FeedbackBanner from '@/components/common/FeedbackBanner.vue'
import HelpTip from '@/components/common/HelpTip.vue'
import ReallocateForm from '@/components/budget/ReallocateForm.vue'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { statusOf } from '@/lib/budgetStatus'
import { currentMonth, formatMonthBR, monthFromQuery, shiftMonth } from '@/lib/date'
import { formatBRL, formatBRLInput, parseBRL } from '@/lib/money'
import { getBudget, saveBudget } from '@/services/budgetService'
import { listCategories } from '@/services/categoryService'
import type { Budget, BudgetAllocationInput, CategoryBudget } from '@/types/budget'
import type { Category } from '@/types/category'
import { apiErrorDetail, apiErrorStatus } from '@/lib/apiError'
import type { ApiError } from '@/lib/apiError'

/**
 * Story 6.3: orçamento do mês por categoria filha, agrupado por grupo. O gasto e o comprometido vêm do backend; a
 * barra usa o valor salvo. O total alocado acompanha o que está digitado, e o aviso de excesso não impede salvar.
 * Story 6.4: restante e status vêm do backend (refletem o salvo); o status tem cor, ícone e rótulo, nunca só cor.
 * Story 6.5: "Mover dinheiro" abre um diálogo; fica bloqueado com valores não salvos (o sucesso recarrega os valores
 * do backend e apagaria o que foi digitado) e some em mês fechado.
 * Story 6.7: chip com mensagem por status, estouradas (e seus grupos) no topo com fundo vermelho, percentual real ao
 * lado da barra e resumo por status. Tudo usa o status salvo: digitar sem salvar não reordena.
 */
const LOAD_FAILED = 'Não foi possível carregar o orçamento.'
const SAVE_FAILED = 'Não foi possível salvar o orçamento. Verifique sua conexão e tente novamente.'
const REPEAT_FAILED = 'Não foi possível carregar o orçamento do mês anterior.'
const REALLOCATE_FAILED = 'Não foi possível mover o dinheiro. Verifique sua conexão e tente novamente.'
const INVALID_AMOUNT = 'Informe um valor válido, zero ou maior'

const route = useRoute()
const router = useRouter()
// o mês mostrado fica na URL (?mes=AAAA-MM): recarregar ou compartilhar abre no mesmo mês
const month = ref(monthFromQuery(route.query.mes))
watch(month, (mes) => void router.replace({ query: { ...route.query, mes } }))
const budget = ref<Budget | null>(null)
const categories = ref<Category[]>([])
const values = ref<Record<string, string>>({})
const loading = ref(true)
const loadError = ref<string | null>(null)
const saving = ref(false)
const repeating = ref(false)
const actionError = ref<string | null>(null)
const { successMessage, successAction, setSuccess, pauseSuccess, resumeSuccess } = useFeedback()
const feedback = ref<InstanceType<typeof FeedbackBanner> | null>(null)
const notice = ref<string | null>(null)
const reallocateOpen = ref(false)
const exportOpen = ref(false)
const reallocating = ref(false)
const reallocateError = ref<string | null>(null)
const reallocateForm = ref<InstanceType<typeof ReallocateForm> | null>(null)
const reallocateButton = ref<InstanceType<typeof Button> | null>(null)
const moreButton = ref<InstanceType<typeof Button> | null>(null)

const monthLabel = computed(() => formatMonthBR(month.value))

// Filhas excluídas com orçamento de um mês fechado: só leitura, mas entram no total
const removedCategories = computed(() => (budget.value?.categories ?? []).filter((c) => c.removedCategoryName))
const activeBudgets = computed(() => (budget.value?.categories ?? []).filter((c) => !c.removedCategoryName))

const byCategory = computed<Record<string, CategoryBudget>>(() =>
  Object.fromEntries(activeBudgets.value.map((c) => [c.categoryId, c])),
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

/**
 * Soma do comprometido das categorias mostradas; null em mês fechado (o backend não manda comprometido). Zero não
 * aparece no quadro: uma coluna "R$ 0,00" não diz nada.
 */
const totalCommitted = computed(() => {
  const values = groups.value.flatMap((g) => g.children).map((c) => byCategory.value[c.id]!.committed)
  // soma em centavos inteiros, para não acumular erro de ponto flutuante
  return values.some((v) => v !== null) ? values.reduce<number>((sum, v) => sum + Math.round((v ?? 0) * 100), 0) / 100 : null
})

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
    Object.values(values.value).reduce(
      (cents, text) => {
        const value = text.trim() ? parseBRL(text) : null
        return value !== null && value >= 0 ? cents + Math.round(value * 100) : cents
      },
      removedCategories.value.reduce((cents, c) => cents + Math.round((c.allocated ?? 0) * 100), 0),
    ) / 100,
)

const overAvailable = computed(
  () => !!budget.value && totalAllocated.value > 0 && totalAllocated.value > budget.value.totalAvailable,
)

// AAAA-MM compara como texto
const closedMonth = computed(() => month.value < currentMonth())

// Compara o valor, não o texto: "400" num campo salvo como "400,00" não é edição; texto inválido conta como edição
const dirty = computed(() =>
  activeBudgets.value.some((c) => {
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
    data.categories
      .filter((c) => !c.removedCategoryName)
      .map((c) => [c.categoryId, c.allocated === null ? '' : formatBRLInput(c.allocated)]),
  )
}

/** Sequência das cargas: só a resposta da carga mais recente é aplicada. */
let request = 0

async function load() {
  const currentRequest = ++request
  loading.value = true
  loadError.value = null
  actionError.value = null
  setSuccess(null)
  notice.value = null
  try {
    const [data, list] = await Promise.all([getBudget(month.value), listCategories()])
    if (currentRequest !== request) return
    categories.value = list
    fill(data)
  } catch (err) {
    if (currentRequest !== request) return
    budget.value = null
    loadError.value = apiErrorStatus(err) === 400 ? apiErrorDetail(err, LOAD_FAILED) : LOAD_FAILED
  } finally {
    if (currentRequest === request) loading.value = false
  }
}

function changeMonth(delta: number) {
  month.value = shiftMonth(month.value, delta)
  void load()
}

/** O navegador não rola um campo focado que ainda "está na tela", mesmo coberto pela barra fixa; o nearest respeita o scroll-mb. */
function keepAboveBar(event: FocusEvent) {
  ;(event.target as HTMLElement).scrollIntoView({ block: 'nearest' })
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

/** As alocações salvas do mês mostrado (as de categorias excluídas não vão no salvar). */
function savedAllocations(data: Budget): BudgetAllocationInput[] {
  return data.categories
    .filter((c) => !c.removedCategoryName && c.allocated !== null)
    .map((c) => ({ categoryId: c.categoryId, amount: c.allocated! }))
}

/** Grava `allocations` no mês mostrado; devolve false se o mês mudou ou deu erro (o erro fica na tela). */
async function persist(allocations: BudgetAllocationInput[]): Promise<boolean> {
  const target = month.value
  saving.value = true
  try {
    const data = await saveBudget(target, budget.value!.version, allocations)
    if (target !== month.value) return false
    fill(data)
    notice.value = null
    return true
  } catch (err) {
    const e = err as ApiError
    const status = e.response?.status
    actionError.value = status === 400 || status === 409 ? (e.response?.data?.detail ?? SAVE_FAILED) : SAVE_FAILED
    return false
  } finally {
    saving.value = false
  }
}

async function save() {
  if (saving.value || repeating.value || !budget.value) return
  actionError.value = null
  if (Object.values(fieldErrors.value).some(Boolean)) return
  const allocations = Object.entries(values.value)
    .filter(([, text]) => text.trim())
    .map(([categoryId, text]) => ({ categoryId, amount: parseBRL(text)! }))
  const previous = savedAllocations(budget.value)
  const target = month.value
  setSuccess(null)
  if (await persist(allocations)) {
    setSuccess('Orçamento salvo', { label: 'Desfazer', run: () => void undoSave(target, previous) })
    focusFeedback()
  }
}

/** Volta as alocações de antes do último salvamento (o "Desfazer" da faixa de sucesso). */
async function undoSave(target: string, previous: BudgetAllocationInput[]) {
  if (saving.value || !budget.value || target !== month.value) return
  setSuccess(null)
  actionError.value = null
  if (await persist(previous)) setSuccess('Alterações desfeitas')
  focusFeedback()
}

/** O "Salvar" fica desabilitado depois de gravar: o foco vai para a faixa de sucesso, ao lado dele. */
function focusFeedback() {
  void nextTick(() => feedback.value?.focus())
}

// Digitar de novo tira a confirmação: o "Desfazer" apagaria o que está sendo digitado
watch(dirty, (changed) => changed && setSuccess(null))

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
  void nextTick(() => {
    // no celular o botão fica escondido (a ação está no "⋯"): o foco volta para o "⋯"
    const button = reallocateButton.value?.$el as HTMLElement | undefined
    const target = button?.offsetParent ? button : (moreButton.value?.$el as HTMLElement | undefined)
    ;(target ?? button)?.focus()
  })
}

function onMoved(data: Budget, message: string) {
  reallocateOpen.value = false
  fill(data)
  setSuccess(message)
}

onMounted(load)

// Não perder valores digitados sem salvar ao trocar de tela ou fechar a aba
onBeforeRouteLeave(() => !dirty.value || window.confirm('Há alterações não salvas no orçamento. Sair mesmo assim?'))
useEventListener(window, 'beforeunload', (e: BeforeUnloadEvent) => {
  if (dirty.value) e.preventDefault()
})
</script>
