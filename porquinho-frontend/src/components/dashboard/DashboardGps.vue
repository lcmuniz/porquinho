<template>
  <div>
    <nav aria-label="Ir para a camada" class="flex flex-col gap-3 sm:flex-row sm:flex-wrap">
      <button
        v-for="layer in layers"
        :key="layer.id"
        type="button"
        :data-testid="`go-to-${layer.id}`"
        class="inline-flex min-h-[44px] items-center rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100"
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
        class="scroll-mt-24 py-12 focus:outline-none"
      >
        <h2 :id="`${layer.id}-heading`" class="text-lg font-semibold text-gray-900">
          {{ layer.title }}
        </h2>
        <DashboardLayer1 v-if="layer.id === 'layer-overview'" :key="`overview-${refresh}`" />
        <DashboardLayer2 v-else-if="layer.id === 'layer-tactical'" :key="`tactical-${refresh}`" />
        <DashboardLayer3 v-else @transaction-saved="onTransactionSaved" />
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import DashboardLayer1 from './DashboardLayer1.vue'
import DashboardLayer2 from './DashboardLayer2.vue'
import DashboardLayer3 from './DashboardLayer3.vue'

const emit = defineEmits<{ 'transaction-saved': [] }>()

// Story 7.6 (Q1=B): uma edição na Camada 3 remonta as Camadas 1 e 2, que buscam de novo; o semáforo fica com a view
const refresh = ref(0)
function onTransactionSaved() {
  refresh.value++
  emit('transaction-saved')
}

// Story 7.1: as 3 camadas do Dashboard GPS; o conteúdo real chega nas stories 7.2–7.6
const layers = [
  { id: 'layer-overview', title: 'Visão geral' },
  { id: 'layer-tactical', title: 'Visão tática' },
  { id: 'layer-details', title: 'Detalhes' },
] as const

// Sem `behavior`: herda o scroll-behavior do CSS, que respeita o movimento reduzido.
// O foco vai para a seção para o Tab seguir dentro da camada escolhida.
function goTo(id: string) {
  const el = document.getElementById(id)
  el?.scrollIntoView()
  el?.focus({ preventScroll: true })
}
</script>
