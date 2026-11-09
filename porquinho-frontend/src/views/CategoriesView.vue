<template>
  <main class="min-h-screen bg-gray-50 px-4 py-8">
    <div class="mx-auto max-w-3xl space-y-6">
      <div class="flex items-start justify-between gap-4">
        <div>
          <h1 ref="heading" tabindex="-1" class="text-2xl font-semibold text-gray-900 outline-none">Categorias</h1>
          <p class="mt-1 text-sm text-gray-600">Os envelopes do seu orçamento, organizados por grupo</p>
        </div>
        <div class="flex flex-wrap justify-end gap-2">
          <Button
            ref="addButton"
            class="min-h-[44px]"
            :disabled="loading || loadFailed || groups.length === 0"
            data-testid="add-category"
            @click="openCreate"
          >
            Adicionar categoria
          </Button>
          <RouterLink
            to="/dashboard"
            data-testid="back-to-dashboard"
            class="inline-flex min-h-[44px] items-center rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100"
          >
            Voltar ao dashboard
          </RouterLink>
        </div>
      </div>

      <!-- Sempre no DOM: leitores de tela só anunciam mudanças numa região live que já existe -->
      <p
        role="status"
        data-testid="categories-success"
        :class="
          successMessage
            ? 'rounded-lg border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-800'
            : 'sr-only'
        "
      >
        {{ successMessage }}
      </p>

      <div v-if="loading" class="space-y-2" aria-busy="true" data-testid="categories-skeleton">
        <div class="h-32 animate-pulse rounded-lg bg-gray-200" />
        <span class="sr-only">Carregando suas categorias...</span>
      </div>

      <div
        v-else-if="loadFailed"
        class="space-y-3 rounded-lg border border-gray-200 bg-white p-4 text-center"
        data-testid="categories-load-failed"
      >
        <p class="text-sm text-gray-600">Não foi possível carregar suas categorias.</p>
        <Button variant="outline" class="min-h-[44px]" data-testid="retry-button" @click="load">
          Tentar de novo
        </Button>
      </div>

      <p
        v-else-if="groups.length === 0"
        class="rounded-lg border border-gray-200 bg-white p-4 text-center text-sm text-gray-600"
        data-testid="categories-empty"
      >
        Você ainda não tem grupos de categorias. Conclua o onboarding para criá-los.
      </p>

      <div v-else class="space-y-6">
        <section
          v-for="group in groups"
          :key="group.parent.id"
          class="space-y-2 rounded-lg border border-gray-200 bg-white p-4"
          :aria-labelledby="`group-${group.parent.id}`"
          :data-testid="`group-${group.parent.id}`"
        >
          <h2 :id="`group-${group.parent.id}`" class="flex items-center gap-2 text-sm font-semibold text-gray-900">
            <component
              :is="categoryIcon(group.parent.iconName)"
              class="h-4 w-4"
              :style="{ color: group.parent.colorHex }"
              aria-hidden="true"
            />
            {{ group.parent.categoryName }}
          </h2>
          <ul class="space-y-2">
            <li
              v-for="child in group.children"
              :key="child.id"
              class="flex min-h-[44px] items-center gap-3"
              :data-testid="`category-${child.id}`"
            >
              <span
                class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full"
                :style="{ backgroundColor: `${child.colorHex}1A` }"
                aria-hidden="true"
              >
                <component :is="categoryIcon(child.iconName)" class="h-5 w-5" :style="{ color: child.colorHex }" />
              </span>
              <span class="font-medium text-gray-900">{{ child.categoryName }}</span>
            </li>
          </ul>
          <p v-if="group.children.length === 0" class="text-sm text-gray-500">Nenhuma categoria neste grupo</p>
        </section>
      </div>
    </div>

    <Dialog :open="dialogOpen" @update:open="onOpenChange">
      <DialogContent data-testid="category-dialog" @close-auto-focus="returnFocus">
        <DialogHeader>
          <DialogTitle>Adicionar categoria</DialogTitle>
          <DialogDescription>Cor e ícone são opcionais: sem eles, a categoria usa a cor do grupo e o ícone de pasta.</DialogDescription>
        </DialogHeader>
        <p
          v-if="formError"
          role="alert"
          data-testid="category-form-error"
          class="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800"
        >
          {{ formError }}
        </p>
        <form class="space-y-4" novalidate data-testid="category-form" @submit.prevent="save">
          <div class="space-y-1">
            <label for="category-name" class="text-sm font-medium text-gray-900">Nome</label>
            <input
              id="category-name"
              v-model="form.name"
              maxlength="50"
              placeholder="Ex.: Pets"
              :class="FIELD_CLASS"
              :aria-invalid="nameError ? 'true' : undefined"
              :aria-describedby="nameError ? 'category-name-error' : undefined"
              data-testid="category-name"
            />
            <p
              v-if="nameError"
              id="category-name-error"
              role="alert"
              class="text-sm text-red-600"
              data-testid="category-name-error"
            >
              {{ nameError }}
            </p>
          </div>

          <div class="space-y-1">
            <label for="category-group" class="text-sm font-medium text-gray-900">Grupo</label>
            <select id="category-group" v-model="form.parentId" :class="FIELD_CLASS" data-testid="category-group">
              <option v-for="group in groups" :key="group.parent.id" :value="group.parent.id">
                {{ group.parent.categoryName }}
              </option>
            </select>
          </div>

          <fieldset class="space-y-2">
            <legend class="text-sm font-medium text-gray-900">Cor</legend>
            <div class="flex flex-wrap gap-1">
              <button
                type="button"
                class="min-h-[44px] rounded-md border px-3 text-sm focus:ring-2 focus:ring-purple-500 focus:outline-none"
                :class="form.colorHex === '' ? 'border-purple-500 bg-purple-50' : 'border-gray-200'"
                :aria-pressed="form.colorHex === ''"
                data-testid="color-option-auto"
                @click="form.colorHex = ''"
              >
                Automática
              </button>
              <button
                v-for="color in options.colors"
                :key="color"
                type="button"
                class="flex h-11 w-11 items-center justify-center rounded-full focus:ring-2 focus:ring-purple-500 focus:ring-offset-1 focus:outline-none"
                :aria-label="categoryColorLabel(color)"
                :aria-pressed="form.colorHex === color"
                :data-testid="`color-option-${color}`"
                @click="form.colorHex = color"
              >
                <span
                  class="flex h-7 w-7 items-center justify-center rounded-full"
                  :style="{ backgroundColor: color }"
                  aria-hidden="true"
                >
                  <Check v-if="form.colorHex === color" class="h-4 w-4 text-white" />
                </span>
              </button>
            </div>
          </fieldset>

          <fieldset class="space-y-2">
            <legend class="text-sm font-medium text-gray-900">Ícone</legend>
            <div class="flex flex-wrap gap-1">
              <button
                type="button"
                class="min-h-[44px] rounded-md border px-3 text-sm focus:ring-2 focus:ring-purple-500 focus:outline-none"
                :class="form.iconName === '' ? 'border-purple-500 bg-purple-50' : 'border-gray-200'"
                :aria-pressed="form.iconName === ''"
                data-testid="icon-option-default"
                @click="form.iconName = ''"
              >
                Padrão
              </button>
              <button
                v-for="icon in pickableIcons"
                :key="icon"
                type="button"
                class="flex h-11 w-11 items-center justify-center rounded-md border focus:ring-2 focus:ring-purple-500 focus:outline-none"
                :class="form.iconName === icon ? 'border-purple-500 bg-purple-50' : 'border-transparent hover:bg-gray-50'"
                :aria-label="categoryIconLabel(icon)"
                :aria-pressed="form.iconName === icon"
                :data-testid="`icon-option-${icon}`"
                @click="form.iconName = icon"
              >
                <component :is="categoryIcon(icon)" class="h-5 w-5" :style="{ color: previewColor }" aria-hidden="true" />
              </button>
            </div>
          </fieldset>

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              class="min-h-[44px]"
              :disabled="saving"
              data-testid="category-cancel"
              @click="dialogOpen = false"
            >
              Cancelar
            </Button>
            <Button type="submit" class="min-h-[44px]" :disabled="saving" data-testid="category-save">
              {{ saving ? 'Salvando...' : 'Salvar' }}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { Check } from 'lucide-vue-next'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { categoryColorLabel, categoryIcon, categoryIconLabel } from '@/lib/categoryIcons'
import { cleanCategoryName } from '@/lib/categoryName'
import { createCategory, getCategoryOptions, listCategories } from '@/services/categoryService'
import type { Category, CategoryOptions } from '@/types/category'

/**
 * Story 6.1: categorias do usuário agrupadas (grupo → filhas, por nome) e criação de uma categoria filha.
 * Cor e ícone são opcionais: vazios não são enviados e o backend usa a cor do grupo e o ícone `folder`.
 */
const FIELD_CLASS =
  'flex min-h-[44px] w-full rounded-md border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 focus-visible:ring-2 focus-visible:ring-purple-600 focus-visible:outline-none'
const SUCCESS_TIMEOUT_MS = 5000
const NAME_REQUIRED = 'Informe o nome da categoria'
const SAVE_FAILED = 'Não foi possível criar a categoria. Verifique sua conexão e tente novamente.'

const categories = ref<Category[]>([])
const options = ref<CategoryOptions>({ colors: [], icons: [] })
const loading = ref(true)
const loadFailed = ref(false)
const heading = ref<HTMLElement | null>(null)
const addButton = ref<{ $el: HTMLElement } | null>(null)

const dialogOpen = ref(false)
const saving = ref(false)
const form = reactive({ name: '', parentId: '', colorHex: '', iconName: '' })
const nameError = ref<string | null>(null)
const formError = ref<string | null>(null)
const successMessage = ref<string | null>(null)

const byName = (a: Category, b: Category) =>
  a.categoryName.localeCompare(b.categoryName, 'pt-BR', { sensitivity: 'base' }) || a.id.localeCompare(b.id)

const groups = computed(() =>
  categories.value
    .filter((c) => c.parentCategoryId === null)
    .sort(byName)
    .map((parent) => ({
      parent,
      children: categories.value.filter((c) => c.parentCategoryId === parent.id).sort(byName),
    })),
)

// "Padrão" já é o ícone de pasta: não repete a mesma opção na grade
const pickableIcons = computed(() => options.value.icons.filter((icon) => icon !== 'folder'))

const previewColor = computed(
  () => form.colorHex || groups.value.find((g) => g.parent.id === form.parentId)?.parent.colorHex || '#64748B',
)

interface ApiError {
  response?: { status?: number; data?: { detail?: string } }
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

let successTimer: ReturnType<typeof setTimeout> | undefined

function setSuccess(message: string) {
  clearTimeout(successTimer)
  successMessage.value = message
  successTimer = setTimeout(() => (successMessage.value = null), SUCCESS_TIMEOUT_MS)
}

function openCreate() {
  Object.assign(form, { name: '', parentId: groups.value[0]?.parent.id ?? '', colorHex: '', iconName: '' })
  nameError.value = null
  formError.value = null
  dialogOpen.value = true
}

/** Com o POST em andamento o diálogo não fecha: fechar pareceria cancelar uma criação que já vai acontecer. */
function onOpenChange(open: boolean) {
  if (!saving.value) dialogOpen.value = open
}

function returnFocus(event: Event) {
  event.preventDefault()
  void nextTick(() => (addButton.value?.$el ?? heading.value)?.focus())
}

async function save() {
  if (saving.value) return
  const name = cleanCategoryName(form.name)
  nameError.value = name ? null : NAME_REQUIRED
  formError.value = null
  if (!name) return
  saving.value = true
  try {
    const created = await createCategory({
      name,
      parentId: form.parentId,
      ...(form.colorHex && { colorHex: form.colorHex }),
      ...(form.iconName && { iconName: form.iconName }),
    })
    categories.value = [...categories.value, created]
    dialogOpen.value = false
    setSuccess('Categoria criada')
  } catch (err) {
    const response = (err as ApiError)?.response
    const detail = response?.data?.detail
    // 409 (nome repetido) e erros de nome vão para o campo; o resto aparece no topo do diálogo
    if (detail && (response?.status === 409 || detail.includes('nome'))) nameError.value = detail
    else formError.value = detail ?? SAVE_FAILED
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  heading.value?.focus()
  await load()
})

onBeforeUnmount(() => clearTimeout(successTimer))
</script>
