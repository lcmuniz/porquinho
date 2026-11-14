<template>
  <main class="min-h-screen bg-gray-50 px-4 py-8">
    <div class="mx-auto max-w-3xl space-y-6">
      <div class="flex items-start justify-between gap-4">
        <div>
          <h1 ref="heading" tabindex="-1" class="text-2xl font-semibold text-gray-900 outline-none">
            Metas
          </h1>
          <p class="mt-1 text-sm text-gray-600">
            Seus objetivos financeiros e quanto falta para cada um
          </p>
        </div>
        <div class="flex flex-wrap justify-end gap-2">
          <RouterLink
            v-if="activeGoals.length >= 2"
            to="/metas/ajustar"
            data-testid="adjust-allocations"
            class="inline-flex min-h-[44px] items-center rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100"
          >
            Ajustar alocações
          </RouterLink>
          <Button
            ref="addButton"
            class="min-h-[44px]"
            :disabled="loading || loadFailed"
            data-testid="add-goal"
            @click="createDialog?.open()"
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
      <p
        v-if="actionError"
        role="alert"
        data-testid="goals-action-error"
        class="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800"
      >
        {{ actionError }}
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

      <template v-else>
        <div
          class="space-y-2 rounded-lg border border-gray-200 bg-white p-4 text-sm"
          data-testid="goals-total"
        >
          <p class="text-gray-900">
            Total alocado em metas:
            <span class="font-medium">{{ formatBRL(totalAllocated) }}/mês</span>
          </p>
          <p
            v-if="overAvailable"
            role="alert"
            data-testid="goals-over-available"
            class="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-amber-900"
          >
            Suas metas somam mais do que você tem disponível
          </p>
        </div>

        <p
          v-if="activeGoals.length === 0"
          class="rounded-lg border border-gray-200 bg-white p-4 text-center text-sm text-gray-600"
          data-testid="goals-no-active"
        >
          Nenhuma meta ativa. Use Criar meta para definir uma nova.
        </p>

        <ul class="space-y-3">
          <GoalCard
            v-for="goal in activeGoals"
            :key="goal.id"
            :goal="goal"
            :chart="progress[goal.id]"
            @edit-allocation="amountDialog?.open('allocation', goal, $event)"
            @contribute="amountDialog?.open('contribution', goal, $event)"
            @toggle-chart="toggleChart(goal.id)"
            @complete="openComplete(goal, $event)"
          />
        </ul>

        <!-- Story 8.7: somente leitura; só "Ver evolução" e "Reabrir meta" -->
        <section v-if="completedGoals.length > 0" class="space-y-3" data-testid="completed-goals">
          <h2 class="text-lg font-semibold text-gray-900">Metas concluídas</h2>
          <ul class="space-y-3">
            <li
              v-for="goal in completedGoals"
              :key="goal.id"
              class="space-y-3 rounded-lg border border-gray-200 bg-white p-4"
              :data-testid="`completed-goal-${goal.id}`"
            >
              <div class="flex items-center gap-2">
                <CircleCheck class="h-5 w-5 text-green-600" aria-hidden="true" />
                <h3 class="min-w-0 font-medium break-words text-gray-900">{{ goal.name }}</h3>
                <span
                  class="shrink-0 rounded-full bg-green-50 px-2 py-0.5 text-xs font-medium text-green-800"
                  data-testid="goal-completed-badge"
                >
                  Concluída
                </span>
              </div>
              <p class="text-sm text-gray-700" data-testid="goal-completed-at">
                Concluída em {{ formatDateBR(goal.completedAt) }}
              </p>
              <p class="text-sm text-gray-700" data-testid="goal-final-amount">
                Valor final guardado: {{ formatBRL(goal.currentAmount) }}
              </p>
              <div class="flex flex-wrap gap-2">
                <Button
                  variant="outline"
                  class="min-h-[44px]"
                  :aria-expanded="goal.id in progress ? 'true' : 'false'"
                  :aria-controls="`goal-chart-${goal.id}`"
                  data-testid="toggle-chart"
                  @click="toggleChart(goal.id)"
                >
                  {{ goal.id in progress ? 'Ocultar evolução' : 'Ver evolução' }}
                </Button>
                <RouterLink
                  :to="`/metas/${goal.id}/historico`"
                  :aria-label="`Histórico de ${goal.name}`"
                  data-testid="goal-history-link"
                  class="inline-flex min-h-[44px] items-center rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100"
                >
                  Histórico
                </RouterLink>
                <Button
                  variant="outline"
                  class="min-h-[44px]"
                  :aria-label="`Reabrir meta ${goal.name}`"
                  :disabled="reopening !== null"
                  data-testid="reopen-goal"
                  @click="reopen(goal)"
                >
                  Reabrir meta
                </Button>
              </div>
              <GoalChartPanel v-if="goal.id in progress" :goal="goal" :state="progress[goal.id]!" />
            </li>
          </ul>
        </section>
      </template>
    </div>

    <GoalCreateDialog
      ref="createDialog"
      :focus-fallback="addButton?.$el ?? heading"
      @created="onCreated"
    />
    <GoalAmountDialog ref="amountDialog" :focus-fallback="heading" @saved="onAmountSaved" />
    <GoalCompleteDialog ref="completeDialog" :focus-fallback="heading" @completed="onCompleted" />
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { CircleCheck } from 'lucide-vue-next'
import { Button } from '@/components/ui/button'
import GoalAmountDialog from '@/components/goals/GoalAmountDialog.vue'
import GoalCard from '@/components/goals/GoalCard.vue'
import GoalChartPanel from '@/components/goals/GoalChartPanel.vue'
import GoalCompleteDialog from '@/components/goals/GoalCompleteDialog.vue'
import GoalCreateDialog from '@/components/goals/GoalCreateDialog.vue'
import { formatDateBR } from '@/lib/date'
import { formatBRL } from '@/lib/money'
import { getGoalProgress, getGoalsSummary, listGoals, reopenGoal } from '@/services/goalService'
import type { Goal, GoalProgressPoint, GoalsSummary } from '@/types/goal'

/**
 * Story 8.1: metas ativas do usuário (ordem de criação) e criação de outra meta, sem limite.
 * Story 8.2: editar a alocação mensal, aportar e o total alocado no topo, com aviso acima do disponível.
 * Story 8.3: "Ver evolução" abre o gráfico no card e só então busca a série.
 * Story 8.5: "Ajustar alocações" (com 2+ metas) abre /metas/ajustar, que volta com "Alocações atualizadas".
 * Story 8.7: concluídas (no aporte que atinge o alvo ou com confirmação) vão para "Metas concluídas", somente
 * leitura e fora dos totais; "Reabrir meta" as devolve às ativas com a alocação anterior.
 * Retro Epic 8 A3: diálogos e card ativo ficam em components/goals; a view cuida da lista e das mensagens.
 */
const SUCCESS_TIMEOUT_MS = 5000
const ACTION_FAILED = 'Não foi possível salvar. Verifique sua conexão e tente novamente.'

const goals = ref<Goal[]>([])
const loading = ref(true)
const loadFailed = ref(false)
const heading = ref<HTMLElement | null>(null)
const addButton = ref<{ $el: HTMLElement } | null>(null)
const createDialog = ref<InstanceType<typeof GoalCreateDialog> | null>(null)
const amountDialog = ref<InstanceType<typeof GoalAmountDialog> | null>(null)
const completeDialog = ref<InstanceType<typeof GoalCompleteDialog> | null>(null)
const successMessage = ref<string | null>(null)
const actionError = ref<string | null>(null)
const reopening = ref<string | null>(null)

/** Story 8.2: null quando o GET falhou; aí só o aviso some, a lista continua. */
const summary = ref<GoalsSummary | null>(null)

const activeGoals = computed(() => goals.value.filter((g) => !g.completed))
const completedGoals = computed(() => goals.value.filter((g) => g.completed))

// Soma em centavos: em float, 0,10 + 0,20 passaria de 0,30 e acenderia o aviso à toa
const totalAllocated = computed(
  () =>
    activeGoals.value.reduce(
      (cents, g) => cents + Math.round((g.monthlyAllocation ?? 0) * 100),
      0,
    ) / 100,
)
const overAvailable = computed(
  () =>
    !!summary.value &&
    totalAllocated.value > 0 &&
    totalAllocated.value > summary.value.totalAvailable,
)

/** Story 8.3: série dos gráficos abertos, por id da meta. Sem a chave, o gráfico está fechado. */
const progress = reactive<Record<string, GoalProgressPoint[] | 'loading' | 'error'>>({})

async function loadProgress(id: string) {
  // Recarga com o gráfico aberto mantém a série antiga na tela até a nova chegar
  if (!Array.isArray(progress[id])) progress[id] = 'loading'
  try {
    const { points } = await getGoalProgress(id)
    // Fechado enquanto carregava: não reabre
    if (id in progress) progress[id] = points
  } catch {
    if (id in progress) progress[id] = 'error'
  }
}

function toggleChart(id: string) {
  if (id in progress) delete progress[id]
  else void loadProgress(id)
}

interface ApiError {
  response?: { data?: { detail?: string } }
}

async function load() {
  loading.value = true
  loadFailed.value = false
  try {
    // Sem await: a lista não espera o resumo, que só controla o aviso
    void getGoalsSummary().then(
      (s) => (summary.value = s),
      () => (summary.value = null),
    )
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

function replaceGoal(updated: Goal) {
  goals.value = goals.value.map((g) => (g.id === updated.id ? updated : g))
}

function onCreated(created: Goal) {
  goals.value = [...goals.value, created]
  setSuccess('Meta criada')
}

function onAmountSaved(kind: 'allocation' | 'contribution', updated: Goal) {
  replaceGoal(updated)
  // A alocação só muda a projeção, que vem da prop goal; a série real só muda com aporte
  if (kind === 'contribution' && updated.id in progress) void loadProgress(updated.id)
  setSuccess(
    kind === 'allocation'
      ? `Alocação atualizada para ${updated.name}`
      : updated.completed
        ? `Parabéns! Você completou a meta ${updated.name}!`
        : `Aporte registrado em ${updated.name}`,
  )
}

function openComplete(goal: Goal, event: Event) {
  actionError.value = null
  completeDialog.value?.open(goal, event)
}

function onCompleted(updated: Goal) {
  replaceGoal(updated)
  setSuccess(`Meta ${updated.name} concluída`)
}

async function reopen(goal: Goal) {
  if (reopening.value) return
  reopening.value = goal.id
  actionError.value = null
  try {
    replaceGoal(await reopenGoal(goal.id))
    setSuccess('Meta reaberta')
    // O botão some com o card concluído: o foco não pode cair no body
    void nextTick(() => heading.value?.focus())
  } catch (err) {
    actionError.value = (err as ApiError)?.response?.data?.detail ?? ACTION_FAILED
  } finally {
    reopening.value = null
  }
}

onMounted(async () => {
  heading.value?.focus()
  // Story 8.5: a tela de ajuste volta para cá com esse estado; limpo para não repetir ao recarregar
  if (window.history.state?.allocationsUpdated) {
    setSuccess('Alocações atualizadas')
    window.history.replaceState({ ...window.history.state, allocationsUpdated: null }, '')
  }
  await load()
})

onBeforeUnmount(() => clearTimeout(successTimer))
</script>
