<template>
  <div>
    <nav aria-label="Ir para a camada" class="flex gap-2 overflow-x-auto">
      <button
        v-for="layer in layers"
        :key="layer.id"
        type="button"
        :data-testid="`go-to-${layer.id}`"
        class="inline-flex min-h-[44px] shrink-0 items-center rounded-full border border-gray-300 bg-white px-4 text-sm font-semibold text-gray-700 hover:border-gray-400 hover:text-gray-900"
        @click="goTo(layer.id)"
      >
        {{ layer.title }}
      </button>
    </nav>

    <div class="divide-y divide-gray-200">
      <section
        v-for="layer in layers"
        :id="layer.id"
        :key="layer.id"
        :aria-labelledby="`${layer.id}-heading`"
        tabindex="-1"
        class="scroll-mt-32 py-8 focus:outline-none md:py-10"
      >
        <div class="flex items-center gap-1">
          <h2 :id="`${layer.id}-heading`" class="text-xl font-bold text-gray-900">
            {{ layer.title }}
          </h2>
          <HelpTip v-if="layer.help" :term="layer.title" :testid="layer.id">{{ layer.help }}</HelpTip>
        </div>
        <DashboardLayer1 v-if="layer.id === 'layer-overview'" :refresh="refresh" @loaded="onLoaded" />
        <DashboardLayer2 v-else-if="layer.id === 'layer-tactical'" :refresh="refresh" @loaded="onLoaded" />
        <DashboardLayer3 v-else @transaction-saved="onTransactionSaved" @loaded="onLoaded" />
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import DashboardLayer1 from './DashboardLayer1.vue'
import DashboardLayer2 from './DashboardLayer2.vue'
import DashboardLayer3 from './DashboardLayer3.vue'
import HelpTip from '@/components/common/HelpTip.vue'

const emit = defineEmits<{ 'transaction-saved': []; loaded: [] }>()

// Story 7.6 (Q1=B): uma edição na Camada 3 faz as Camadas 1 e 2 buscarem de novo; o semáforo fica com a view.
// Retro Epic 7 A4: sem remontar, os dados antigos ficam na tela até os novos chegarem
const refresh = ref(0)
function onTransactionSaved() {
  refresh.value++
  emit('transaction-saved')
}

// Retro Epic 7 A4: `loaded` só depois da primeira carga das 3 camadas
let pendingLayers = 3
function onLoaded() {
  if (--pendingLayers === 0) emit('loaded')
}

// Story 7.1: as 3 camadas do Dashboard GPS; o conteúdo real chega nas stories 7.2–7.6
const layers: { id: string; title: string; help?: string }[] = [
  {
    id: 'layer-overview',
    title: 'Visão geral',
  },
  {
    id: 'layer-tactical',
    title: 'Visão tática',
    // Critique de 20/11/2026: só o título que não se explica sozinho tem ajuda, e sem a palavra "camada"
    help: 'Para onde o seu dinheiro está indo: o gasto de cada categoria no mês e como ele mudou nos últimos 12 meses.',
  },
  {
    id: 'layer-details',
    title: 'Detalhes',
  },
]

// Sem `behavior`: herda o scroll-behavior do CSS, que respeita o movimento reduzido.
// O foco vai para a seção para o Tab seguir dentro da camada escolhida.
function goTo(id: string) {
  const el = document.getElementById(id)
  el?.scrollIntoView()
  el?.focus({ preventScroll: true })
}
</script>
