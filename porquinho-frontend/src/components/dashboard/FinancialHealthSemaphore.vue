<template>
  <div class="sticky top-0 z-10 rounded-lg border border-gray-200 bg-white px-4 py-3 shadow-sm">
    <p v-if="error" role="alert" data-testid="health-error" class="text-sm text-gray-700">
      Não foi possível carregar a saúde financeira
    </p>
    <template v-else-if="health">
      <button
        type="button"
        data-testid="health-toggle"
        :aria-expanded="open"
        aria-controls="health-detail"
        class="inline-flex min-h-[44px] w-full items-center gap-2 text-left text-sm font-semibold text-gray-900"
        @click="open = !open"
      >
        <component :is="look.icon" class="h-5 w-5 shrink-0" :class="look.color" aria-hidden="true" />
        <span>Saúde Financeira: {{ look.label }}</span>
        <ChevronDown
          class="ml-auto h-4 w-4 shrink-0 transition-transform"
          :class="{ 'rotate-180': open }"
          aria-hidden="true"
        />
      </button>
      <ul v-show="open" id="health-detail" data-testid="health-detail" class="mt-2 space-y-1 text-sm text-gray-700">
        <li v-for="line in detail" :key="line">{{ line }}</li>
      </ul>
    </template>
    <p v-else data-testid="health-loading" class="text-sm text-gray-600">Carregando saúde financeira…</p>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ChevronDown, CircleCheck, OctagonAlert, TriangleAlert } from 'lucide-vue-next'
import { getFinancialHealth } from '@/services/dashboardService'
import { plural } from '@/lib/plural'
import { STATUS } from '@/lib/budgetStatus'
import type { FinancialHealth, HealthStatus } from '@/types/dashboard'

// Story 7.2: sempre com ícone e texto junto da cor. A cor do ícone vem de STATUS (amarelo no 700, 3:1 pela WCAG 1.4.11)
const LOOKS = {
  HEALTHY: { label: 'Saudável', color: STATUS.HEALTHY.text, icon: CircleCheck },
  ATTENTION: { label: 'Atenção', color: STATUS.LOW.text, icon: TriangleAlert },
  CRITICAL: { label: 'Crítico', color: STATUS.OVERSPENT.text, icon: OctagonAlert },
} satisfies Record<HealthStatus, unknown>

// Retro Epic 7 A4: `refresh` busca de novo sem voltar ao "Carregando…"; `loaded` avisa a primeira carga (a view restaura o scroll)
const props = defineProps<{ refresh?: number }>()
const emit = defineEmits<{ loaded: [] }>()
const health = ref<FinancialHealth | null>(null)
const error = ref(false)
const open = ref(false)

const look = computed(() => LOOKS[health.value!.status])

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

// Sem keep-alive: cada volta ao dashboard remonta e busca de novo
async function load() {
  try {
    health.value = await getFinancialHealth()
    error.value = false
  } catch {
    error.value = true
  }
}

onMounted(async () => {
  await load()
  emit('loaded')
})
watch(() => props.refresh, load)
</script>
