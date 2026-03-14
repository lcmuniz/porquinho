<template>
  <section class="p-6 space-y-8">
    <h2 class="text-2xl font-semibold text-foreground">Espacamento e Layout</h2>

    <!-- Spacing Scale -->
    <div>
      <h3 class="text-xl font-semibold text-foreground mb-4">Escala de Espacamento (base 4px)</h3>
      <div class="space-y-3">
        <div v-for="step in spacingScale" :key="step.px" class="flex items-center gap-4">
          <span class="text-sm font-medium text-muted-foreground w-24 shrink-0">{{ step.class }}</span>
          <span class="text-xs text-muted-foreground w-12 shrink-0">{{ step.px }}</span>
          <div class="bg-primary/20 border border-primary/40 rounded" :style="{ width: step.px, height: '20px' }"></div>
        </div>
      </div>
    </div>

    <!-- Common Usage Patterns -->
    <div>
      <h3 class="text-xl font-semibold text-foreground mb-4">Padroes de Uso Comuns</h3>
      <div class="grid gap-4">
        <div v-for="pattern in spacingPatterns" :key="pattern.name" class="border border-border rounded-lg p-4">
          <div class="flex items-center justify-between mb-2">
            <span class="text-sm font-semibold text-foreground">{{ pattern.name }}</span>
            <code class="text-xs bg-muted text-muted-foreground px-2 py-0.5 rounded">{{ pattern.classes }}</code>
          </div>
          <p class="text-sm text-muted-foreground">{{ pattern.description }}</p>
        </div>
      </div>
    </div>

    <!-- Border Radius -->
    <div>
      <h3 class="text-xl font-semibold text-foreground mb-4">Border Radius</h3>
      <div class="flex flex-wrap gap-6">
        <div v-for="radius in borderRadii" :key="radius.class" class="flex flex-col items-center gap-2">
          <div
            class="w-16 h-16 bg-primary/20 border-2 border-primary/60"
            :class="radius.class"
          ></div>
          <span class="text-xs font-medium text-muted-foreground">{{ radius.class }}</span>
          <span class="text-xs text-muted-foreground">{{ radius.size }}</span>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
const spacingScale = [
  { class: 'space-1', px: '4px' },
  { class: 'space-2', px: '8px' },
  { class: 'space-3', px: '12px' },
  { class: 'space-4', px: '16px' },
  { class: 'space-6', px: '24px' },
  { class: 'space-8', px: '32px' },
  { class: 'space-12', px: '48px' },
  { class: 'space-16', px: '64px' },
]

const spacingPatterns = [
  {
    name: 'Cards / Formularios',
    classes: 'p-6, gap-4',
    description: 'Padding interno 24px, distancia entre elementos 16px. Uso: cards de metricas, formularios.',
  },
  {
    name: 'Dashboard (camadas GPS)',
    classes: 'py-12',
    description: 'Padding vertical 48px entre as camadas do Dashboard GPS. Cria respiro visual entre secoes.',
  },
  {
    name: 'Denso (tabelas)',
    classes: 'p-3, gap-2',
    description: 'Padding 12px, distancia 8px. Uso: linhas de tabela, listas compactas de transacoes.',
  },
  {
    name: 'Espacoso (landing)',
    classes: 'p-8, gap-8',
    description: 'Padding 32px, distancia 32px. Uso: secoes de pagina, containers de hero.',
  },
]

const borderRadii = [
  { class: 'rounded-sm', size: '4px' },
  { class: 'rounded-md', size: '8px' },
  { class: 'rounded-lg', size: '12px' },
  { class: 'rounded-xl', size: '16px' },
  { class: 'rounded-full', size: '9999px' },
]
</script>
