<template>
  <div class="flex flex-col gap-8">
    <section class="space-y-6" data-testid="customize-categories-step">
      <div class="space-y-3">
        <h1 ref="heading" tabindex="-1" class="text-2xl font-bold text-gray-900 outline-none">
          Personalize suas categorias
        </h1>
        <p class="text-gray-600">
          Deixe os envelopes com a sua cara: toque no nome para renomear, no ícone para trocar a cor
          e o ícone, ou crie categorias novas. Tudo é salvo na hora.
        </p>
      </div>

      <div v-if="loading" class="space-y-3" aria-busy="true" data-testid="customize-skeleton">
        <div class="h-5 w-1/3 animate-pulse rounded bg-gray-100" />
        <div v-for="n in 4" :key="n" class="h-14 animate-pulse rounded-lg bg-gray-100" />
        <span class="sr-only">Carregando suas categorias...</span>
      </div>

      <div
        v-else-if="loadFailed"
        class="space-y-3 rounded-lg border border-gray-200 p-4 text-center"
        data-testid="customize-load-failed"
      >
        <p class="text-sm text-gray-600">Não foi possível carregar suas categorias.</p>
        <Button variant="outline" class="min-h-[44px]" data-testid="retry-button" @click="load">
          Tentar de novo
        </Button>
      </div>

      <div v-else class="space-y-6">
        <section
          v-for="group in groups"
          :key="group.parent.id"
          class="space-y-2"
          :aria-label="`Grupo ${group.parent.categoryName}`"
          :data-testid="`customize-group-${group.parent.id}`"
        >
          <CategoryItem
            :category="group.parent"
            :options="options!"
            kind="group"
            :can-delete="group.children.length === 0"
            :disabled="finishing"
            @updated="replace"
            @pending="track"
            @delete="askDelete"
          />

          <ul class="space-y-2">
            <li v-for="child in group.children" :key="child.id">
              <CategoryItem
                :category="child"
                :options="options!"
                kind="child"
                can-delete
                :disabled="finishing"
                @updated="replace"
                @pending="track"
                @delete="askDelete"
              />
            </li>
          </ul>

          <form
            v-if="addingTo === group.parent.id"
            class="space-y-2 rounded-lg border border-dashed border-purple-300 p-3"
            :data-testid="`add-form-${group.parent.id}`"
            @submit.prevent="submitNew(group.parent)"
          >
            <label
              :for="`new-category-${group.parent.id}`"
              class="text-sm font-medium text-gray-900"
            >
              Nome da nova categoria em {{ group.parent.categoryName }}
            </label>
            <input
              :id="`new-category-${group.parent.id}`"
              v-model="newName"
              type="text"
              maxlength="50"
              class="min-h-[44px] w-full rounded-md border border-gray-300 px-3 focus:border-purple-500 focus:ring-2 focus:ring-purple-500 focus:outline-none"
              placeholder="Ex.: Pets"
              :aria-invalid="addError ? 'true' : undefined"
              :aria-describedby="addError ? 'add-category-error' : undefined"
              :disabled="adding || finishing"
              data-testid="new-category-name"
              @keydown.esc.prevent="cancelAdd"
            />
            <p
              v-if="addError"
              id="add-category-error"
              role="alert"
              class="text-sm text-red-600"
              data-testid="add-category-error"
            >
              {{ addError }}
            </p>
            <div class="flex gap-2">
              <Button
                type="button"
                variant="outline"
                class="min-h-[44px] flex-1"
                data-testid="cancel-add-button"
                :disabled="adding"
                @click="cancelAdd"
              >
                Cancelar
              </Button>
              <Button
                type="submit"
                class="min-h-[44px] flex-1 bg-purple-600 text-white hover:bg-purple-700"
                data-testid="save-add-button"
                :disabled="adding || finishing"
              >
                {{ adding ? 'Salvando...' : 'Adicionar' }}
              </Button>
            </div>
          </form>
          <Button
            v-else
            type="button"
            variant="ghost"
            class="min-h-[44px] w-full justify-start text-purple-700"
            :data-testid="`add-category-${group.parent.id}`"
            :disabled="finishing"
            @click="startAdd(group.parent.id)"
          >
            <Plus class="h-4 w-4" aria-hidden="true" />
            Adicionar categoria
          </Button>
        </section>
      </div>
    </section>

    <nav class="flex flex-col gap-3" aria-label="Navegação do passo de personalização">
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
          :disabled="finishing || loading"
          @click="finish(false)"
        >
          {{ finishing && !skipping ? 'Salvando...' : 'Próximo' }}
          <ChevronRight class="h-4 w-4" aria-hidden="true" />
        </Button>
      </div>
      <Button
        variant="ghost"
        class="min-h-[44px] text-gray-600"
        data-testid="skip-button"
        :disabled="finishing"
        @click="finish(true)"
      >
        Pular personalização
      </Button>
    </nav>

    <Dialog :open="pendingDelete !== null" @update:open="(open) => !open && closeDelete()">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Excluir "{{ pendingDelete?.categoryName }}"?</DialogTitle>
          <DialogDescription>
            {{
              pendingDelete?.parentCategoryId
                ? 'A categoria sai do seu orçamento. Você pode criá-la de novo depois.'
                : 'O grupo vazio sai da sua lista.'
            }}
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button
            variant="outline"
            class="min-h-[44px]"
            data-testid="cancel-delete-button"
            :disabled="deleting"
            @click="closeDelete"
          >
            Cancelar
          </Button>
          <Button
            class="min-h-[44px] bg-red-600 text-white hover:bg-red-700"
            data-testid="confirm-delete-button"
            :disabled="deleting"
            @click="confirmDelete"
          >
            {{ deleting ? 'Excluindo...' : 'Excluir' }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { ChevronLeft, ChevronRight, Plus } from 'lucide-vue-next'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import CategoryItem from '@/components/onboarding/CategoryItem.vue'
import { cleanCategoryName } from '@/lib/categoryName'
import {
  createCategory,
  deleteCategory,
  getCategoryOptions,
  listCategories,
} from '@/services/categoryService'
import { completeCustomization } from '@/services/onboardingService'
import type { Category, CategoryOptions } from '@/types/category'
import type { OnboardingProgress } from '@/types/onboarding'

/**
 * Story 3.5: passo 5 do onboarding. Lista as categorias do usuário agrupadas e em ordem alfabética (pt-BR).
 * Renomear, trocar cor/ícone, excluir (com confirmação) e adicionar são salvos na hora pela API de categorias;
 * Próximo e "Pular personalização" só registram o passo e avançam (pular não desfaz o que já foi salvo).
 */
const emit = defineEmits<{
  back: []
  done: [progress: OnboardingProgress]
  error: [message: string]
}>()

const categories = ref<Category[]>([])
const options = ref<CategoryOptions | null>(null)
const loading = ref(true)
const loadFailed = ref(false)
const finishing = ref(false)
const skipping = ref(false)
const heading = ref<HTMLElement | null>(null)

const addingTo = ref<string | null>(null)
const newName = ref('')
const addError = ref('')
const adding = ref(false)

const pendingDelete = ref<Category | null>(null)
const deleting = ref(false)

/** Requisições de edição em andamento (true = deu certo): Próximo espera por elas antes de avançar. */
const pending = new Set<Promise<boolean>>()

const byName = (a: Category, b: Category) =>
  a.categoryName.localeCompare(b.categoryName, 'pt-BR', { sensitivity: 'base' }) ||
  a.id.localeCompare(b.id)

const groups = computed(() =>
  categories.value
    .filter((c) => c.parentCategoryId === null)
    .sort(byName)
    .map((parent) => ({
      parent,
      children: categories.value.filter((c) => c.parentCategoryId === parent.id).sort(byName),
    })),
)

interface ApiError {
  response?: { status?: number; data?: { detail?: string } }
}

function apiDetail(err: unknown): string | undefined {
  return (err as ApiError)?.response?.data?.detail
}

async function load() {
  loading.value = true
  loadFailed.value = false
  try {
    const [list, opts] = await Promise.all([listCategories(), getCategoryOptions()])
    categories.value = list
    options.value = opts
  } catch {
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

function replace(updated: Category) {
  categories.value = categories.value.map((c) => (c.id === updated.id ? updated : c))
}

function track(request: Promise<unknown>) {
  const settled = request.then(
    () => true,
    () => false,
  )
  pending.add(settled)
  settled.finally(() => pending.delete(settled))
}

async function startAdd(parentId: string) {
  addingTo.value = parentId
  newName.value = ''
  addError.value = ''
  await nextTick()
  document.getElementById(`new-category-${parentId}`)?.focus()
}

function cancelAdd() {
  addingTo.value = null
  addError.value = ''
}

async function submitNew(parent: Category) {
  if (adding.value || finishing.value) return
  const name = cleanCategoryName(newName.value)
  if (!name) {
    addError.value = 'Informe o nome da categoria'
    return
  }
  adding.value = true
  addError.value = ''
  // Começa com a cor e o ícone do grupo; dá para trocar logo depois
  const request = createCategory({
    name,
    colorHex: parent.colorHex,
    iconName: parent.iconName,
    parentId: parent.id,
  })
  track(request)
  try {
    categories.value = [...categories.value, await request]
    addingTo.value = null
  } catch (err) {
    addError.value =
      apiDetail(err) ??
      'Não foi possível criar a categoria. Verifique sua conexão e tente novamente.'
  } finally {
    adding.value = false
  }
}

function askDelete(category: Category) {
  pendingDelete.value = category
}

function closeDelete() {
  if (deleting.value) return
  pendingDelete.value = null
}

async function confirmDelete() {
  const target = pendingDelete.value
  if (!target || deleting.value) return
  deleting.value = true
  const request = deleteCategory(target.id)
  track(request)
  try {
    await request
    categories.value = categories.value.filter((c) => c.id !== target.id)
  } catch (err) {
    if ((err as ApiError)?.response?.status === 404) {
      // Já tinha sido excluída (outra aba, por exemplo): só tira da lista
      categories.value = categories.value.filter((c) => c.id !== target.id)
    } else {
      emit(
        'error',
        apiDetail(err) ?? 'Não foi possível excluir. Verifique sua conexão e tente novamente.',
      )
    }
  } finally {
    deleting.value = false
    pendingDelete.value = null
  }
}

async function finish(skipped: boolean) {
  // Duplo clique no mesmo tick: o botão ainda não foi desabilitado
  if (finishing.value) return
  finishing.value = true
  skipping.value = skipped
  // Um nome salvo ao sair do campo (o clique em Próximo tira o foco) termina antes de avançar
  const saved = await Promise.all([...pending])
  if (saved.includes(false)) {
    // Uma alteração falhou (a mensagem aparece no item): o usuário fica no passo para conferir
    finishing.value = false
    emit('error', 'Uma alteração não foi salva. Confira a mensagem na lista e tente de novo.')
    return
  }
  let progress: OnboardingProgress
  try {
    progress = await completeCustomization(skipped)
  } catch (err) {
    // Só libera os botões no erro: no sucesso a tela troca, e liberar antes permitiria outro POST
    finishing.value = false
    emit(
      'error',
      apiDetail(err) ??
        'Não foi possível salvar seu progresso. Verifique sua conexão e tente novamente.',
    )
    return
  }
  emit('done', progress)
}

onMounted(async () => {
  heading.value?.focus()
  await load()
})
</script>
