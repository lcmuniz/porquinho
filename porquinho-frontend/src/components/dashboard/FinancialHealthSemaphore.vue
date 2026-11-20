<template>
  <div
    class="z-10 rounded-lg border px-4 py-2 transition-colors md:px-5 lg:sticky lg:top-0"
    :class="health && !error && !noBudget ? look.band : 'border-gray-200 bg-white'"
  >
    <!-- novo-design: a faixa do semáforo é o único elemento "ousado" do dashboard; a cor do fundo acompanha o estado -->
    <div
      v-if="error"
      role="alert"
      data-testid="health-error"
      class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between"
    >
      <p class="text-sm text-gray-700">Não foi possível carregar a saúde financeira</p>
      <Button variant="outline" class="min-h-[44px]" data-testid="health-error-retry" @click="retry">Tentar de novo</Button>
    </div>
    <!-- novo-design: sem orçamento não há o que avaliar; o semáforo não elogia o vazio -->
    <div
      v-else-if="health && noBudget"
      data-testid="health-empty"
      class="flex flex-col gap-1 py-1.5 sm:flex-row sm:items-center sm:gap-3"
    >
      <p class="flex items-center gap-3 text-base font-bold text-gray-900">
        <CircleDashed class="size-6 shrink-0 text-gray-500" aria-hidden="true" />
        Saúde financeira: sem orçamento
      </p>
      <RouterLink to="/orcamento" class="ml-9 text-[15px] font-semibold text-purple-700 underline sm:ml-auto">
        Definir orçamento do mês
      </RouterLink>
    </div>
    <template v-else-if="health">
      <button
        type="button"
        data-testid="health-toggle"
        :aria-expanded="open"
        aria-controls="health-detail"
        class="inline-flex min-h-[44px] w-full items-center gap-3 text-left text-base font-bold text-gray-900 md:text-[17px]"
        @click="open = !open"
      >
        <component :is="look.icon" class="size-6 shrink-0" :class="look.color" aria-hidden="true" />
        <span>Saúde Financeira: {{ look.label }}</span>
        <ChevronDown
          class="ml-auto size-5 shrink-0 text-gray-600 transition-transform"
          :class="{ 'rotate-180': open }"
          aria-hidden="true"
        />
      </button>
      <p v-show="!open" class="mb-1.5 ml-9 text-[15px] text-gray-800" data-testid="health-summary">{{ summary }}</p>
      <ul v-show="open" id="health-detail" data-testid="health-detail" class="mb-2 ml-9 space-y-1 text-[15px] text-gray-800">
        <li v-for="line in detail" :key="line">{{ line }}</li>
      </ul>
    </template>
    <p v-else role="status" data-testid="health-loading" class="text-sm text-gray-600">Carregando saúde financeira…</p>
  </div>
</template>

<script setup lang="ts">
import { Button } from '@/components/ui/button'
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { ChevronDown, CircleCheck, CircleDashed, OctagonAlert, TriangleAlert } from 'lucide-vue-next'
import { getFinancialHealth } from '@/services/dashboardService'
import { plural } from '@/lib/plural'
import { STATUS } from '@/lib/budgetStatus'
import type { FinancialHealth, HealthStatus } from '@/types/dashboard'

// Story 7.2: sempre com ícone e texto junto da cor. A cor do ícone vem de STATUS (amarelo no 700, 3:1 pela WCAG 1.4.11)
const LOOKS = {
  HEALTHY: { label: 'Saudável', color: STATUS.HEALTHY.text, icon: CircleCheck, band: 'border-green-200 bg-green-50' },
  ATTENTION: { label: 'Atenção', color: STATUS.LOW.text, icon: TriangleAlert, band: 'border-amber-200 bg-amber-50' },
  CRITICAL: { label: 'Crítico', color: STATUS.OVERSPENT.text, icon: OctagonAlert, band: 'border-red-200 bg-red-50' },
} satisfies Record<HealthStatus, unknown>

// Retro Epic 7 A4: `refresh` busca de novo sem voltar ao "Carregando…"; `loaded` avisa a primeira carga (a view restaura o scroll)
const props = defineProps<{ refresh?: number }>()
const emit = defineEmits<{ loaded: [] }>()
const health = ref<FinancialHealth | null>(null)
const error = ref(false)
const open = ref(false)

const look = computed(() => LOOKS[health.value!.status])
const noBudget = computed(() => {
  const h = health.value!
  // conta negativa ou meta atrasada continuam aparecendo mesmo sem orçamento
  return h.healthyCategories + h.lowCategories + h.overspentCategories + h.negativeAccounts + h.goalsBehind === 0
})

const detail = computed(() => {
  const h = health.value!
  const lines =
    h.healthyCategories + h.lowCategories + h.overspentCategories === 0
      ? ['Nenhuma categoria com orçamento neste mês']
      : [
          [
            plural(h.healthyCategories, 'categoria saudável', 'categorias saudáveis'),
            `${h.lowCategories} com saldo baixo`,
            plural(h.overspentCategories, 'estourada', 'estouradas'),
          ].join(', '),
        ]
  if (h.negativeAccounts > 0) {
    lines.push(plural(h.negativeAccounts, 'conta com saldo negativo', 'contas com saldo negativo'))
  }
  if (h.goalsBehind > 0) lines.push(plural(h.goalsBehind, 'meta atrasada', 'metas atrasadas'))
  return lines
})

// Fechado, o resumo mostra o que puxou o status (meta atrasada, conta negativa, categoria apertada), não só as categorias
const summary = computed(() => {
  const h = health.value!
  const reasons = detail.value.filter((_, i) => i > 0 || h.lowCategories + h.overspentCategories > 0)
  return (reasons.length ? reasons : detail.value).join('; ')
})

// Sem keep-alive: cada volta ao dashboard remonta e busca de novo
async function load() {
  try {
    health.value = await getFinancialHealth()
    error.value = false
  } catch {
    error.value = true
  }
}

/** Retro Epic 7 A6/F11: volta ao "Carregando…" e busca de novo. */
function retry() {
  error.value = false
  void load()
}

onMounted(async () => {
  await load()
  emit('loaded')
})
watch(() => props.refresh, load)
</script>
