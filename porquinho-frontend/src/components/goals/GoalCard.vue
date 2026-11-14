<template>
  <li
    class="space-y-3 rounded-lg border border-gray-200 bg-white p-4"
    :data-testid="`goal-${goal.id}`"
  >
    <div class="flex items-center gap-2">
      <Target class="h-5 w-5 text-purple-600" aria-hidden="true" />
      <h2 class="min-w-0 font-medium break-words text-gray-900">{{ goal.name }}</h2>
      <span
        v-if="goal.template"
        class="shrink-0 rounded-full bg-purple-50 px-2 py-0.5 text-xs font-medium text-purple-800"
        data-testid="goal-template-badge"
      >
        Template: {{ templateName(goal.template) }}
      </span>
    </div>
    <p v-if="goal.description" class="text-sm break-words whitespace-pre-line text-gray-600">
      {{ goal.description }}
    </p>
    <div
      class="relative h-3 w-full overflow-hidden rounded-full bg-gray-100"
      role="progressbar"
      :aria-valuenow="percent"
      aria-valuemin="0"
      aria-valuemax="100"
      :aria-label="`Progresso de ${goal.name}: ${percent}%`"
    >
      <div
        class="h-full rounded-full"
        :class="PACE_STYLE[pace].bar"
        :style="{ width: `${percent}%` }"
        data-testid="goal-progress-fill"
      />
      <!-- Marcos de 25/50/75% -->
      <span
        v-for="mark in [25, 50, 75]"
        :key="mark"
        class="absolute inset-y-0 w-px bg-gray-400"
        :style="{ left: `${mark}%` }"
        aria-hidden="true"
        data-testid="goal-progress-mark"
      />
    </div>
    <div class="flex flex-wrap items-center justify-between gap-2 text-sm">
      <p class="text-gray-700" data-testid="goal-progress-text">
        {{ formatBRL(goal.currentAmount) }} de {{ formatBRL(goal.targetAmount) }} ({{ percent }}%)
      </p>
      <p class="font-medium" :class="PACE_STYLE[pace].text" data-testid="goal-pace">
        {{ PACE_LABEL[pace] }}
      </p>
    </div>
    <p class="text-sm text-gray-700" data-testid="goal-projection">
      {{ projectionText(projection) }}
    </p>
    <p v-if="suggestion" class="text-sm text-gray-700" data-testid="goal-suggestion">
      {{ suggestion }}
    </p>
    <p v-if="goal.targetDate" class="text-sm text-gray-600">
      Data-alvo: {{ formatLocalDateBR(goal.targetDate) }}
    </p>
    <p v-if="goal.monthlyAllocation !== null" class="text-sm text-gray-600">
      Aporte mensal: {{ formatBRL(goal.monthlyAllocation) }}/mês
    </p>
    <div class="flex flex-wrap gap-2">
      <Button
        variant="outline"
        class="min-h-[44px]"
        :aria-label="`Editar alocação de ${goal.name}`"
        data-testid="edit-allocation"
        @click="emit('edit-allocation', $event)"
      >
        Editar alocação
      </Button>
      <Button
        variant="outline"
        class="min-h-[44px]"
        :aria-label="`Aportar em ${goal.name}`"
        data-testid="contribute"
        @click="emit('contribute', $event)"
      >
        Aportar
      </Button>
      <Button
        variant="outline"
        class="min-h-[44px]"
        :aria-expanded="chart ? 'true' : 'false'"
        :aria-controls="`goal-chart-${goal.id}`"
        data-testid="toggle-chart"
        @click="emit('toggle-chart')"
      >
        {{ chart ? 'Ocultar evolução' : 'Ver evolução' }}
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
        :aria-label="`Marcar ${goal.name} como concluída`"
        data-testid="complete-goal"
        @click="emit('complete', $event)"
      >
        Marcar como concluída
      </Button>
    </div>
    <GoalChartPanel v-if="chart" :goal="goal" :state="chart" />
  </li>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { Target } from 'lucide-vue-next'
import { Button } from '@/components/ui/button'
import GoalChartPanel from '@/components/goals/GoalChartPanel.vue'
import { currentMonth, formatLocalDateBR } from '@/lib/date'
import { goalPace, goalProjection, PACE_LABEL, projectionText, type GoalPace } from '@/lib/goalPace'
import { GOAL_TEMPLATES } from '@/lib/goalPlan'
import { formatBRL } from '@/lib/money'
import type { Goal, GoalProgressPoint, GoalTemplate } from '@/types/goal'

/**
 * Card de meta ativa. Story 8.3: barra com marcos e cor de ritmo. Story 8.4: projeção do mês de conclusão e
 * aporte sugerido para o prazo, calculados no front a partir da meta. Story 8.6: selo do modelo.
 */
const props = defineProps<{
  goal: Goal
  /** Série do gráfico aberto; undefined quando fechado. */
  chart?: GoalProgressPoint[] | 'loading' | 'error'
}>()
const emit = defineEmits<{
  'edit-allocation': [event: Event]
  contribute: [event: Event]
  'toggle-chart': []
  complete: [event: Event]
}>()

const PACE_STYLE: Record<GoalPace, { bar: string; text: string }> = {
  'on-track': { bar: 'bg-green-500', text: 'text-green-700' },
  late: { bar: 'bg-yellow-500', text: 'text-yellow-700' },
  'very-late': { bar: 'bg-red-500', text: 'text-red-700' },
}
const thisMonth = currentMonth()

const percent = computed(() => {
  const { currentAmount, targetAmount } = props.goal
  if (!(targetAmount > 0)) return 0
  return Math.min(100, Math.floor((currentAmount / targetAmount) * 100))
})
const pace = computed(() => goalPace(props.goal, thisMonth))
const projection = computed(() => goalProjection(props.goal, thisMonth))
const suggestion = computed(() => {
  const p = projection.value
  if (p.kind === 'reached' || !p.suggestion) return null
  const { kind, amount } = p.suggestion
  return kind === 'monthly'
    ? `Aumente alocação para ${formatBRL(amount)}/mês para atingir meta no prazo`
    : `O prazo desta meta já chegou. Aporte ${formatBRL(amount)} para concluí-la`
})

function templateName(code: GoalTemplate): string {
  return GOAL_TEMPLATES.find((t) => t.code === code)?.name ?? code
}
</script>
