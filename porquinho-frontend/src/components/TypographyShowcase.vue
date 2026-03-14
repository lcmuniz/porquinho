<template>
  <section class="p-6 space-y-8">
    <h2 class="text-2xl font-semibold text-foreground">Sistema Tipografico</h2>

    <!-- Type Scale -->
    <div>
      <h3 class="text-xl font-semibold text-foreground mb-4">Escala Tipografica (Inter)</h3>
      <div class="space-y-3">
        <div v-for="scale in typeScales" :key="scale.class" class="flex items-baseline gap-4">
          <span class="text-sm font-medium text-muted-foreground w-20 shrink-0">{{ scale.class }}</span>
          <span class="text-muted-foreground text-xs w-16 shrink-0">{{ scale.size }}</span>
          <span :class="scale.class" class="text-foreground font-normal">Porquinho</span>
        </div>
      </div>
    </div>

    <!-- Font Weights -->
    <div>
      <h3 class="text-xl font-semibold text-foreground mb-4">Pesos Tipograficos</h3>
      <div class="space-y-3">
        <div v-for="weight in fontWeights" :key="weight.value" class="flex items-center gap-4">
          <span class="text-sm font-medium text-muted-foreground w-28 shrink-0">
            {{ weight.class }} ({{ weight.value }})
          </span>
          <span :class="['text-xl', 'text-foreground', weight.class]">Porquinho App</span>
        </div>
      </div>
    </div>

    <!-- Dashboard GPS Hierarchy -->
    <div>
      <h3 class="text-xl font-semibold text-foreground mb-4">Hierarquia Dashboard GPS</h3>
      <div class="bg-card border border-border rounded-lg p-6 space-y-4">
        <div>
          <span class="text-xs text-muted-foreground block mb-1">Semaforo Status — text-2xl font-semibold</span>
          <p class="text-2xl font-semibold text-foreground">Saude Financeira: Boa</p>
        </div>
        <div>
          <span class="text-xs text-muted-foreground block mb-1">Grafico Titulo — text-xl font-semibold</span>
          <p class="text-xl font-semibold text-foreground">Crescimento Patrimonial</p>
        </div>
        <div>
          <span class="text-xs text-muted-foreground block mb-1">Valores de Metricas — text-4xl font-bold</span>
          <p class="text-4xl font-bold text-foreground">R$ 12.450</p>
        </div>
        <div>
          <span class="text-xs text-muted-foreground block mb-1">Labels de Metricas — text-sm font-medium</span>
          <p class="text-sm font-medium text-muted-foreground">Patrimonio Liquido</p>
        </div>
        <div>
          <span class="text-xs text-muted-foreground block mb-1">Respostas GPS — text-base font-normal</span>
          <p class="text-base font-normal text-foreground">Suas despesas estao dentro do orcamento este mes.</p>
        </div>
        <div>
          <span class="text-xs text-muted-foreground block mb-1">Recomendacoes — text-base font-medium</span>
          <p class="text-base font-medium text-foreground">Considere alocar mais para sua reserva de emergencia.</p>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
const typeScales = [
  { class: 'text-xs', size: '12px' },
  { class: 'text-sm', size: '14px' },
  { class: 'text-base', size: '16px' },
  { class: 'text-lg', size: '18px' },
  { class: 'text-xl', size: '20px' },
  { class: 'text-2xl', size: '24px' },
  { class: 'text-3xl', size: '30px' },
  { class: 'text-4xl', size: '36px' },
  { class: 'text-5xl', size: '48px' },
]

const fontWeights = [
  { class: 'font-normal', value: '400' },
  { class: 'font-medium', value: '500' },
  { class: 'font-semibold', value: '600' },
  { class: 'font-bold', value: '700' },
]
</script>
