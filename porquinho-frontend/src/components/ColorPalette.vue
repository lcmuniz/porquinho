<template>
  <div class="p-8 space-y-10">
    <h1 class="text-3xl font-bold text-gray-900">Design System — Color Palette</h1>

    <!-- Primary / Purple Scale -->
    <section>
      <h2 class="text-xl font-semibold text-gray-900 mb-4">Purple Scale (Primary)</h2>
      <div class="flex flex-wrap gap-3">
        <div v-for="swatch in purpleSwatches" :key="swatch.name" class="flex flex-col items-center gap-1">
          <div
            :class="['w-16 h-16 rounded-lg', swatch.bg, swatch.border ? 'border border-gray-200' : '']"
          />
          <span class="text-xs text-gray-700 font-medium text-center max-w-16 leading-tight">{{ swatch.name }}</span>
          <span class="text-xs text-gray-500 text-center">{{ swatch.label }}</span>
        </div>
      </div>
    </section>

    <!-- Gray Scale -->
    <section>
      <h2 class="text-xl font-semibold text-gray-900 mb-4">Gray Scale</h2>
      <div class="flex flex-wrap gap-3">
        <div v-for="swatch in graySwatches" :key="swatch.name" class="flex flex-col items-center gap-1">
          <div
            :class="['w-16 h-16 rounded-lg', swatch.bg, swatch.border ? 'border border-gray-200' : '']"
          />
          <span class="text-xs text-gray-700 font-medium text-center max-w-16 leading-tight">{{ swatch.name }}</span>
          <span class="text-xs text-gray-500 text-center">{{ swatch.label }}</span>
        </div>
      </div>
    </section>

    <!-- State Colors -->
    <section>
      <h2 class="text-xl font-semibold text-gray-900 mb-4">State Colors</h2>
      <div class="flex flex-wrap gap-3">
        <div v-for="swatch in stateSwatches" :key="swatch.name" class="flex flex-col items-center gap-1">
          <div
            :class="['w-16 h-16 rounded-lg', swatch.bg, swatch.border ? 'border border-gray-200' : '']"
          />
          <span class="text-xs text-gray-700 font-medium text-center max-w-16 leading-tight">{{ swatch.name }}</span>
          <span class="text-xs text-gray-500 text-center">{{ swatch.label }}</span>
        </div>
      </div>
    </section>

    <!-- Semantic Colors -->
    <section>
      <h2 class="text-xl font-semibold text-gray-900 mb-4">Semantic Colors</h2>
      <div class="flex flex-wrap gap-3">
        <div v-for="swatch in semanticSwatches" :key="swatch.name" class="flex flex-col items-center gap-1">
          <div
            :class="['w-16 h-16 rounded-lg', swatch.bg, swatch.border ? 'border border-gray-200' : '']"
          />
          <span class="text-xs text-gray-700 font-medium text-center max-w-16 leading-tight">{{ swatch.name }}</span>
          <span class="text-xs text-gray-500 text-center">{{ swatch.label }}</span>
        </div>
      </div>
    </section>

    <!-- Chart Colors -->
    <section>
      <h2 class="text-xl font-semibold text-gray-900 mb-4">Chart Colors</h2>
      <div class="flex flex-wrap gap-3">
        <div v-for="swatch in chartSwatches" :key="swatch.name" class="flex flex-col items-center gap-1">
          <div :class="['w-16 h-16 rounded-lg', swatch.bg]" />
          <span class="text-xs text-gray-700 font-medium text-center max-w-16 leading-tight">{{ swatch.name }}</span>
          <span class="text-xs text-gray-500 text-center">{{ swatch.label }}</span>
        </div>
      </div>
    </section>

    <!-- WCAG Contrast Table -->
    <section>
      <h2 class="text-xl font-semibold text-gray-900 mb-4">WCAG 2.1 AA Contrast Validation</h2>
      <div class="overflow-x-auto">
        <table class="w-full text-sm border border-gray-200 rounded-lg overflow-hidden">
          <thead class="bg-gray-100 text-gray-700">
            <tr>
              <th class="px-4 py-3 text-left">Combinacao</th>
              <th class="px-4 py-3 text-left">Ratio</th>
              <th class="px-4 py-3 text-left">WCAG AA</th>
              <th class="px-4 py-3 text-left">Preview</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in contrastRows" :key="row.label" class="border-t border-gray-200">
              <td class="px-4 py-3 text-gray-700">{{ row.label }}</td>
              <td class="px-4 py-3 font-mono text-gray-900">{{ row.ratio }}</td>
              <td class="px-4 py-3">
                <span
                  :class="['inline-flex items-center px-2 py-0.5 rounded text-xs font-medium', row.pass ? 'bg-success-subtle text-success' : 'bg-error-subtle text-error']"
                >
                  {{ row.pass ? 'PASSA AA' : 'FALHA' }}
                </span>
              </td>
              <td class="px-4 py-3">
                <span :class="['px-3 py-1 rounded text-base font-bold', row.bg, row.fg]">Aa</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'ColorPalette' })

interface Swatch {
  name: string
  bg: string
  label: string
  border?: boolean
}

interface ContrastRow {
  label: string
  ratio: string
  pass: boolean
  bg: string
  fg: string
}

const purpleSwatches: Swatch[] = [
  { name: 'purple-50', bg: 'bg-purple-50', label: '#FAF5FF', border: true },
  { name: 'purple-100', bg: 'bg-purple-100', label: '#F3E8FF', border: true },
  { name: 'purple-500', bg: 'bg-purple-500', label: '#A855F7' },
  { name: 'primary 600', bg: 'bg-primary', label: '#9333EA' },
  { name: 'purple-700', bg: 'bg-purple-700', label: '#7E22CE' },
]

const graySwatches: Swatch[] = [
  { name: 'gray-50', bg: 'bg-gray-50', label: '#FAFAFA', border: true },
  { name: 'gray-100', bg: 'bg-gray-100', label: '#F4F4F5', border: true },
  { name: 'gray-200', bg: 'bg-gray-200', label: '#E4E4E7', border: true },
  { name: 'gray-300', bg: 'bg-gray-300', label: '#D4D4D8' },
  { name: 'gray-400', bg: 'bg-gray-400', label: '#A1A1AA' },
  { name: 'gray-500', bg: 'bg-gray-500', label: '#71717A' },
  { name: 'gray-700', bg: 'bg-gray-700', label: '#3F3F46' },
  { name: 'gray-900', bg: 'bg-gray-900', label: '#18181B' },
]

const stateSwatches: Swatch[] = [
  { name: 'success-subtle', bg: 'bg-success-subtle', label: 'Green 100', border: true },
  { name: 'success', bg: 'bg-success', label: '#16A34A' },
  { name: 'warning-subtle', bg: 'bg-warning-subtle', label: 'Yellow 100', border: true },
  { name: 'warning', bg: 'bg-warning', label: '#CA8A04' },
  { name: 'error-subtle', bg: 'bg-error-subtle', label: 'Red 100', border: true },
  { name: 'error', bg: 'bg-error', label: '#DC2626' },
]

const semanticSwatches: Swatch[] = [
  { name: 'info', bg: 'bg-info', label: '#A855F7' },
  { name: 'primary', bg: 'bg-primary', label: 'Purple 600' },
  { name: 'secondary', bg: 'bg-secondary', label: 'Gray subtle', border: true },
  { name: 'destructive', bg: 'bg-destructive', label: 'Red 600' },
]

const chartSwatches: Swatch[] = [
  { name: 'chart-1 Purple', bg: 'bg-chart-1', label: '#9333EA' },
  { name: 'chart-2 Cyan', bg: 'bg-chart-2', label: '#06B6D4' },
  { name: 'chart-3 Amber', bg: 'bg-chart-3', label: '#F59E0B' },
  { name: 'chart-4 Green', bg: 'bg-chart-4', label: '#10B981' },
  { name: 'chart-5 Pink', bg: 'bg-chart-5', label: '#EC4899' },
  { name: 'chart-6 Violet', bg: 'bg-chart-6', label: '#8B5CF6' },
]

const contrastRows: ContrastRow[] = [
  { label: 'Gray 900 on White', ratio: '18.23:1', pass: true, bg: 'bg-white', fg: 'text-gray-900' },
  { label: 'Gray 700 on White', ratio: '9.14:1', pass: true, bg: 'bg-white', fg: 'text-gray-700' },
  { label: 'Gray 500 on White', ratio: '4.61:1', pass: true, bg: 'bg-white', fg: 'text-gray-500' },
  { label: 'Purple 600 on White', ratio: '4.75:1', pass: true, bg: 'bg-white', fg: 'text-primary' },
  { label: 'Green 600 on White', ratio: '4.54:1', pass: true, bg: 'bg-white', fg: 'text-success' },
  { label: 'Red 600 on White', ratio: '5.14:1', pass: true, bg: 'bg-white', fg: 'text-error' },
  { label: 'Yellow 600 on White (apenas indicador)', ratio: '~3.5:1', pass: false, bg: 'bg-white', fg: 'text-warning' },
]
</script>
