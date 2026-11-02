<template>
  <div class="flex flex-col gap-8">
    <!-- Transição discreta só na entrada; com prefers-reduced-motion não há classe de transição -->
    <Transition
      enter-active-class="motion-safe:transition-opacity motion-safe:duration-200"
      enter-from-class="motion-safe:opacity-0"
    >
      <section :key="screen" class="space-y-6" :data-testid="`dashboard-tour-screen-${screen}`">
        <!-- 0. Visão geral: 3 camadas empilhadas e a rolagem vertical -->
        <template v-if="screen === 0">
          <h1 ref="heading" tabindex="-1" class="text-2xl font-bold text-gray-900 outline-none">
            Conheça o Dashboard GPS
          </h1>
          <p class="text-gray-600">
            O dashboard é organizado em 3 camadas, uma embaixo da outra. Cada uma responde a uma
            pergunta sobre o seu dinheiro.
          </p>
          <figure
            class="rounded-lg bg-purple-50 p-6"
            aria-label="Diagrama: as 3 camadas do dashboard empilhadas (Onde estou?, Para onde vou? e Preciso ajustar?). Role verticalmente para navegar entre camadas"
            data-testid="tour-layers-diagram"
          >
            <div class="flex items-stretch gap-4" aria-hidden="true">
              <ol class="flex flex-1 flex-col gap-3">
                <li
                  v-for="layer in LAYERS"
                  :key="layer.number"
                  class="flex items-center gap-3 rounded-lg bg-white p-3 shadow-sm"
                >
                  <span
                    class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-purple-600 text-sm font-semibold text-white"
                    aria-hidden="true"
                  >
                    {{ layer.number }}
                  </span>
                  <div>
                    <p class="text-xs text-gray-500">Camada {{ layer.number }}</p>
                    <p class="font-medium text-gray-900">{{ layer.question }}</p>
                  </div>
                </li>
              </ol>
              <!-- Seta de rolagem -->
              <div class="flex flex-col items-center justify-between py-2" aria-hidden="true">
                <ChevronsDown class="h-5 w-5 text-purple-400" />
                <div class="w-1 flex-1 rounded-full bg-purple-200" />
                <ChevronsDown class="h-5 w-5 text-purple-600" />
              </div>
            </div>
            <figcaption
              aria-hidden="true"
              class="mt-4 flex items-center justify-center gap-2 text-sm font-medium text-purple-800"
              data-testid="tour-scroll-hint"
            >
              <ArrowDown class="h-4 w-4 shrink-0" aria-hidden="true" />
              Role verticalmente para navegar entre camadas
            </figcaption>
          </figure>
        </template>

        <!-- 1. Camada 1: onde estou? -->
        <template v-else-if="screen === 1">
          <p class="text-sm font-medium text-purple-700">Camada 1</p>
          <h1 ref="heading" tabindex="-1" class="text-2xl font-bold text-gray-900 outline-none">
            Onde estou?
          </h1>
          <p class="text-gray-600">5 métricas essenciais, 30 segundos para clareza total</p>
          <figure
            class="rounded-lg bg-purple-50 p-6"
            aria-label="Ilustração esquemática: 5 cartões de métrica"
            data-testid="tour-metrics-illustration"
          >
            <ul class="grid grid-cols-2 gap-3 sm:grid-cols-3" aria-hidden="true">
              <li
                v-for="n in 5"
                :key="n"
                data-testid="tour-metric-card"
                class="space-y-2 rounded-lg bg-white p-3 shadow-sm"
                :class="n === 1 ? 'col-span-2 sm:col-span-1' : ''"
              >
                <p class="text-xs text-gray-500">Métrica {{ n }}</p>
                <div class="h-4 w-3/4 rounded bg-purple-200" />
                <div class="h-2 w-1/2 rounded bg-gray-100" />
              </li>
            </ul>
          </figure>
        </template>

        <!-- 2. Camada 2: para onde vou? -->
        <template v-else-if="screen === 2">
          <p class="text-sm font-medium text-purple-700">Camada 2</p>
          <h1 ref="heading" tabindex="-1" class="text-2xl font-bold text-gray-900 outline-none">
            Para onde vou?
          </h1>
          <p class="text-gray-600">Gráficos por categoria e tendências</p>
          <figure
            class="rounded-lg bg-purple-50 p-6"
            aria-label="Ilustração esquemática: um gráfico por categoria e um gráfico de tendência"
            data-testid="tour-charts-illustration"
          >
            <div class="grid grid-cols-2 gap-3" aria-hidden="true">
              <div class="flex flex-col items-center gap-2 rounded-lg bg-white p-3 shadow-sm">
                <svg viewBox="0 0 36 36" class="h-20 w-20 -rotate-90 fill-none">
                  <circle cx="18" cy="18" r="14" class="stroke-purple-100" stroke-width="6" />
                  <circle
                    cx="18"
                    cy="18"
                    r="14"
                    class="stroke-purple-600"
                    stroke-width="6"
                    stroke-dasharray="40 88"
                  />
                  <circle
                    cx="18"
                    cy="18"
                    r="14"
                    class="stroke-purple-400"
                    stroke-width="6"
                    stroke-dasharray="25 88"
                    stroke-dashoffset="-40"
                  />
                </svg>
                <span class="text-xs text-gray-500">Por categoria</span>
              </div>
              <div class="flex flex-col items-center gap-2 rounded-lg bg-white p-3 shadow-sm">
                <svg viewBox="0 0 60 36" class="h-20 w-full fill-none">
                  <line x1="2" y1="34" x2="58" y2="34" class="stroke-gray-200" stroke-width="1" />
                  <polyline
                    points="2,28 14,24 26,26 38,16 50,12 58,6"
                    class="stroke-purple-600"
                    stroke-width="2"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                </svg>
                <span class="text-xs text-gray-500">Tendência</span>
              </div>
            </div>
          </figure>
        </template>

        <!-- 3. Camada 3: preciso ajustar? -->
        <template v-else-if="screen === 3">
          <p class="text-sm font-medium text-purple-700">Camada 3</p>
          <h1 ref="heading" tabindex="-1" class="text-2xl font-bold text-gray-900 outline-none">
            Preciso ajustar?
          </h1>
          <p class="text-gray-600">Todas as transações, filtros e edição</p>
          <figure
            class="space-y-3 rounded-lg bg-purple-50 p-6"
            aria-label="Ilustração esquemática: uma lista de transações com filtros e edição"
            data-testid="tour-list-illustration"
          >
            <div class="flex gap-2" aria-hidden="true">
              <span
                v-for="n in 3"
                :key="n"
                class="flex items-center gap-1 rounded-full bg-white px-3 py-1 text-xs text-gray-600 shadow-sm"
              >
                <ListFilter class="h-3 w-3" />
                Filtro
              </span>
            </div>
            <ul class="space-y-2" aria-hidden="true">
              <li
                v-for="n in 4"
                :key="n"
                data-testid="tour-list-row"
                class="flex items-center gap-3 rounded-lg bg-white p-3 shadow-sm"
              >
                <div class="h-6 w-6 shrink-0 rounded-full bg-purple-200" />
                <div class="flex-1 space-y-1">
                  <div class="h-2 w-2/3 rounded bg-gray-200" />
                  <div class="h-2 w-1/3 rounded bg-gray-100" />
                </div>
                <Pencil class="h-4 w-4 shrink-0 text-purple-600" />
              </li>
            </ul>
          </figure>
        </template>

        <!-- 4. Fechamento: check-in semanal -->
        <template v-else>
          <div class="flex justify-center">
            <div class="flex h-16 w-16 items-center justify-center rounded-full bg-green-100">
              <CalendarCheck class="h-8 w-8 text-green-600" aria-hidden="true" />
            </div>
          </div>
          <h1
            ref="heading"
            tabindex="-1"
            class="text-center text-2xl font-bold text-gray-900 outline-none"
          >
            Pronto para usar o dashboard
          </h1>
          <p
            class="rounded-lg bg-green-50 p-4 text-center text-lg font-semibold text-green-800"
            data-testid="tour-weekly-checkin"
          >
            Seu check-in semanal leva apenas 5 minutos
          </p>
          <p class="text-center text-gray-600">
            Uma vez por semana, role pelas 3 camadas: veja onde você está, confira para onde vai e
            ajuste o que for preciso.
          </p>
        </template>
      </section>
    </Transition>

    <!-- Telas do tour: 1 de 5 ... 5 de 5 (fora da seção com key, para ser um único elemento estável) -->
    <div class="-mt-2 flex justify-center gap-2" aria-hidden="true">
      <span
        v-for="n in TOUR_SCREENS"
        :key="n"
        class="h-2 w-2 rounded-full"
        :class="n <= screen + 1 ? 'bg-purple-600' : 'bg-purple-100'"
      />
    </div>
    <p class="sr-only" data-testid="tour-screen-counter">
      Tela {{ screen + 1 }} de {{ TOUR_SCREENS }}
    </p>

    <nav class="flex gap-3" aria-label="Navegação do tour do Dashboard GPS">
      <Button
        variant="outline"
        class="min-h-[44px] flex-1"
        data-testid="back-button"
        :disabled="finishing"
        @click="back"
      >
        <ChevronLeft class="h-4 w-4" aria-hidden="true" />
        Voltar
      </Button>
      <Button
        class="min-h-[44px] flex-1 bg-purple-600 text-white hover:bg-purple-700"
        data-testid="next-button"
        :disabled="finishing"
        @click="next"
      >
        {{ finishing ? 'Salvando...' : 'Próximo' }}
        <ChevronRight class="h-4 w-4" aria-hidden="true" />
      </Button>
    </nav>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import {
  ArrowDown,
  CalendarCheck,
  ChevronLeft,
  ChevronRight,
  ChevronsDown,
  ListFilter,
  Pencil,
} from 'lucide-vue-next'
import { Button } from '@/components/ui/button'
import { completeDashboardTour } from '@/services/onboardingService'
import type { OnboardingProgress } from '@/types/onboarding'

/**
 * Story 3.7: passo 7 do onboarding, um tour curto de 5 telas pelo Dashboard GPS (visão geral das 3 camadas,
 * uma tela por camada e o fechamento com o check-in semanal). As ilustrações são esquemáticas, sem valores
 * reais. Próximo nas telas 1 a 4 só troca de tela; na última conclui o passo (o backend grava
 * `dashboard_gps_explained`). Voltar na primeira tela devolve ao passo 6. Sem Pular: não há nada a configurar.
 */
const emit = defineEmits<{
  back: []
  done: [progress: OnboardingProgress]
  error: [message: string]
}>()

const TOUR_SCREENS = 5

const LAYERS = [
  { number: 1, question: 'Onde estou?' },
  { number: 2, question: 'Para onde vou?' },
  { number: 3, question: 'Preciso ajustar?' },
]

const screen = ref(0)
const finishing = ref(false)
const heading = ref<HTMLElement | null>(null)

interface ApiError {
  response?: { data?: { detail?: string } }
}

function back() {
  if (screen.value === 0) {
    emit('back')
  } else {
    screen.value--
  }
}

function next() {
  if (screen.value < TOUR_SCREENS - 1) {
    screen.value++
  } else {
    finish()
  }
}

async function finish() {
  // Duplo clique no mesmo tick: o botão ainda não foi desabilitado
  if (finishing.value) return
  finishing.value = true
  let progress: OnboardingProgress
  try {
    progress = await completeDashboardTour()
  } catch (err) {
    // Só libera os botões no erro: no sucesso a tela troca, e liberar antes permitiria outro POST
    finishing.value = false
    emit(
      'error',
      (err as ApiError)?.response?.data?.detail ||
        'Não foi possível salvar seu progresso. Verifique sua conexão e tente novamente.',
    )
    return
  }
  emit('done', progress)
}

// Leitores de tela e teclado: a cada tela nova o foco vai para o título
watch(screen, async () => {
  await nextTick()
  heading.value?.focus()
})

onMounted(() => {
  heading.value?.focus()
})
</script>
