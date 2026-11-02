<template>
  <div class="flex flex-col gap-8">
    <section class="space-y-6" data-testid="categories-step">
      <div class="space-y-3">
        <h1 ref="heading" tabindex="-1" class="text-2xl font-bold text-gray-900 outline-none">
          Categorias de gastos sugeridas
        </h1>
        <p v-if="readOnly" class="text-gray-600" data-testid="categories-read-only">
          Suas categorias já foram criadas. Para renomear, trocar, excluir ou adicionar, toque em
          Próximo e personalize-as.
        </p>
        <p v-else class="text-gray-600">
          Cada categoria é um envelope do seu orçamento. Já separamos as mais comuns: desmarque as
          que não fazem sentido para você. Depois você pode ajustar nomes, cores e ícones.
        </p>
      </div>

      <div
        v-if="loadingSuggestions"
        class="space-y-3"
        aria-busy="true"
        data-testid="categories-skeleton"
      >
        <div class="h-5 w-1/3 animate-pulse rounded bg-gray-100" />
        <div class="grid grid-cols-1 gap-2 sm:grid-cols-2">
          <div v-for="n in 4" :key="n" class="h-14 animate-pulse rounded-lg bg-gray-100" />
        </div>
        <span class="sr-only">Carregando as sugestões...</span>
      </div>

      <div
        v-else-if="loadFailed"
        class="space-y-3 rounded-lg border border-gray-200 p-4 text-center"
        data-testid="categories-load-failed"
      >
        <p class="text-sm text-gray-600">Não foi possível carregar as sugestões.</p>
        <Button
          variant="outline"
          class="min-h-[44px]"
          data-testid="retry-button"
          @click="loadSuggestions"
        >
          Tentar de novo
        </Button>
      </div>

      <div v-else class="space-y-6">
        <fieldset
          v-for="group in groups"
          :key="group.key"
          class="space-y-2"
          :data-testid="`category-group-${group.key}`"
        >
          <legend class="mb-2 flex items-center gap-2 text-sm font-semibold text-gray-900">
            <component
              :is="categoryIcon(group.iconName)"
              class="h-4 w-4"
              :style="{ color: group.colorHex }"
              aria-hidden="true"
            />
            {{ group.name }}
          </legend>
          <div class="grid grid-cols-1 gap-2 sm:grid-cols-2">
            <label
              v-for="suggestion in group.suggestions"
              :key="suggestion.key"
              :for="`category-${suggestion.key}`"
              class="flex min-h-[44px] cursor-pointer items-center gap-3 rounded-lg border p-3 transition-colors focus-within:ring-2 focus-within:ring-purple-500"
              :class="
                selected.has(suggestion.key)
                  ? 'border-purple-300 bg-purple-50'
                  : 'border-gray-200 bg-white'
              "
              :data-testid="`category-card-${suggestion.key}`"
            >
              <input
                :id="`category-${suggestion.key}`"
                type="checkbox"
                class="h-5 w-5 shrink-0 accent-purple-600"
                :checked="selected.has(suggestion.key)"
                :disabled="finishing || readOnly"
                :data-testid="`category-checkbox-${suggestion.key}`"
                @change="toggle(suggestion.key)"
              />
              <span
                class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full"
                :style="{ backgroundColor: `${suggestion.colorHex}1A` }"
                aria-hidden="true"
              >
                <component
                  :is="categoryIcon(suggestion.iconName)"
                  class="h-4 w-4"
                  :style="{ color: suggestion.colorHex }"
                />
              </span>
              <span class="font-medium text-gray-900">{{ suggestion.name }}</span>
            </label>
          </div>
        </fieldset>
      </div>
    </section>

    <nav class="flex flex-col gap-3" aria-label="Navegação do passo de categorias">
      <div class="flex gap-3">
        <Button
          variant="outline"
          class="min-h-[44px] flex-1"
          data-testid="back-button"
          :disabled="finishing"
          @click="emit('back')"
        >
          <ChevronLeft class="h-4 w-4" aria-hidden="true" />
          Voltar
        </Button>
        <Button
          class="min-h-[44px] flex-1 bg-purple-600 text-white hover:bg-purple-700"
          data-testid="next-button"
          :disabled="!canFinish"
          :aria-describedby="showNoneHint ? 'categories-none-hint' : undefined"
          @click="finish"
        >
          {{ finishing ? 'Salvando...' : 'Próximo' }}
          <ChevronRight class="h-4 w-4" aria-hidden="true" />
        </Button>
      </div>
      <p
        v-if="showNoneHint"
        id="categories-none-hint"
        class="text-center text-sm text-gray-600"
        aria-live="polite"
        data-testid="categories-none-hint"
      >
        Escolha ao menos uma categoria
      </p>
    </nav>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ChevronLeft, ChevronRight } from 'lucide-vue-next'
import { Button } from '@/components/ui/button'
import { categoryIcon } from '@/lib/categoryIcons'
import { getCategorySuggestions, listCategories } from '@/services/categoryService'
import { completeCategories } from '@/services/onboardingService'
import type { CategorySuggestionGroup } from '@/types/category'
import type { OnboardingProgress } from '@/types/onboarding'

/**
 * Story 3.4: passo 4 do onboarding. Mostra as categorias sugeridas em 3 grupos, todas marcadas;
 * Próximo cria as marcadas (filhas do seu grupo) e avança. Sem Pular: o mínimo é 1 categoria.
 * Personalizar (nome, cor, ícone, excluir, criar) é o passo 5 (Story 3.5).
 *
 * `readOnly` (Voltar a partir do passo 5): as categorias já existem, então os checkboxes ficam desabilitados e
 * marcam só as sugestões que o usuário tem (pelo nome). Próximo usa o POST idempotente, que não cria nada e
 * devolve o passo 5.
 */
const props = defineProps<{ readOnly?: boolean }>()

const emit = defineEmits<{
  back: []
  done: [progress: OnboardingProgress]
  error: [message: string]
}>()

const groups = ref<CategorySuggestionGroup[]>([])
const selected = ref<Set<string>>(new Set())
const loadingSuggestions = ref(true)
const loadFailed = ref(false)
const finishing = ref(false)
const heading = ref<HTMLElement | null>(null)

const ready = computed(() => !loadingSuggestions.value && !loadFailed.value)
const showNoneHint = computed(() => ready.value && selected.value.size === 0)
const canFinish = computed(() => ready.value && selected.value.size > 0 && !finishing.value)

interface ApiError {
  response?: { status?: number; data?: { detail?: string } }
}

function apiDetail(err: unknown): string | undefined {
  return (err as ApiError)?.response?.data?.detail
}

function toggle(key: string) {
  const next = new Set(selected.value)
  if (next.has(key)) next.delete(key)
  else next.add(key)
  selected.value = next
}

async function loadSuggestions() {
  loadingSuggestions.value = true
  loadFailed.value = false
  try {
    groups.value = await getCategorySuggestions()
    const all = groups.value.flatMap((g) => g.suggestions)
    if (props.readOnly) {
      // Só leitura: marca as que o usuário criou; sem conseguir ler, mantém todas (o POST não cria nada)
      const mine = await listCategories().catch(() => null)
      const names = new Set(mine?.map((c) => c.categoryName))
      const owned = all.filter((s) => names.has(s.name)).map((s) => s.key)
      selected.value = new Set(mine && owned.length > 0 ? owned : all.map((s) => s.key))
    } else {
      // Todas marcadas por padrão
      selected.value = new Set(all.map((s) => s.key))
    }
  } catch {
    // Só o aviso na própria tela (com "Tentar de novo"); um toast ficaria visível após a nova tentativa
    loadFailed.value = true
  } finally {
    loadingSuggestions.value = false
  }
}

async function finish() {
  // Duplo clique no mesmo tick: o botão ainda não foi desabilitado
  if (!canFinish.value) return
  finishing.value = true
  // Na ordem do catálogo, para o POST ser previsível
  const keys = groups.value
    .flatMap((g) => g.suggestions.map((s) => s.key))
    .filter((key) => selected.value.has(key))
  let progress: OnboardingProgress
  try {
    progress = await completeCategories(keys)
  } catch (err) {
    // Só libera os botões no erro: no sucesso a tela troca, e liberar antes permitiria outro POST
    finishing.value = false
    emit(
      'error',
      apiDetail(err) ??
        'Não foi possível salvar suas categorias. Verifique sua conexão e tente novamente.',
    )
    return
  }
  emit('done', progress)
}

onMounted(async () => {
  heading.value?.focus()
  await loadSuggestions()
})
</script>
