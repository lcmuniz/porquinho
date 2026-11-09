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
            :disabled="loading || loadFailed"
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
        Você ainda não tem grupos de categorias. Use Adicionar categoria para criar o primeiro grupo.
      </p>

      <div v-else class="space-y-6">
        <section
          v-for="group in groups"
          :key="group.parent.id"
          class="space-y-2 rounded-lg border border-gray-200 bg-white p-4"
          :aria-labelledby="`group-${group.parent.id}`"
          :data-testid="`group-${group.parent.id}`"
        >
          <h2 :id="`group-${group.parent.id}`" class="text-sm font-semibold text-gray-900">
            <button
              type="button"
              class="flex min-h-[44px] w-full items-center gap-2 rounded-md text-left focus-visible:ring-2 focus-visible:ring-purple-600 focus-visible:outline-none"
              :aria-expanded="!collapsed.has(group.parent.id)"
              :aria-controls="`group-items-${group.parent.id}`"
              :data-testid="`group-toggle-${group.parent.id}`"
              @click="toggle(group.parent.id)"
            >
              <ChevronRight v-if="collapsed.has(group.parent.id)" class="h-4 w-4 text-gray-500" aria-hidden="true" />
              <ChevronDown v-else class="h-4 w-4 text-gray-500" aria-hidden="true" />
              <component
                :is="categoryIcon(group.parent.iconName)"
                class="h-4 w-4"
                :style="{ color: group.parent.colorHex }"
                aria-hidden="true"
              />
              {{ group.parent.categoryName }}
            </button>
          </h2>
          <div v-show="!collapsed.has(group.parent.id)" :id="`group-items-${group.parent.id}`">
            <ul class="space-y-2 pl-6">
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
                <span class="flex-1 font-medium text-gray-900">{{ child.categoryName }}</span>
                <Button
                  variant="outline"
                  class="min-h-[44px]"
                  :disabled="groups.length < 2"
                  :aria-label="`Mover ${child.categoryName}`"
                  :data-testid="`move-${child.id}`"
                  @click="openMove(child)"
                >
                  Mover
                </Button>
              </li>
            </ul>
            <p v-if="group.children.length === 0" class="pl-6 text-sm text-gray-500">Nenhuma categoria neste grupo</p>
          </div>
        </section>
      </div>
    </div>

    <Dialog :open="dialogOpen" @update:open="onOpenChange">
      <DialogContent data-testid="category-dialog" @close-auto-focus="returnFocus">
        <DialogHeader>
          <DialogTitle>{{ form.parentId === '' ? 'Adicionar grupo' : 'Adicionar categoria' }}</DialogTitle>
          <DialogDescription>{{ createDescription }}</DialogDescription>
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
              <option value="">Nenhum (criar como grupo)</option>
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

    <Dialog :open="moveOpen" @update:open="onMoveOpenChange">
      <DialogContent data-testid="move-dialog" @close-auto-focus="returnMoveFocus">
        <DialogHeader>
          <DialogTitle>Mover {{ moving?.categoryName }}</DialogTitle>
          <DialogDescription>Escolha o grupo de destino.</DialogDescription>
        </DialogHeader>
        <p
          v-if="moveError"
          role="alert"
          data-testid="move-error"
          class="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800"
        >
          {{ moveError }}
        </p>
        <form class="space-y-4" novalidate data-testid="move-form" @submit.prevent="move">
          <div class="space-y-1">
            <label for="move-group" class="text-sm font-medium text-gray-900">Grupo</label>
            <select id="move-group" v-model="moveTarget" :class="FIELD_CLASS" data-testid="move-group">
              <option v-for="group in moveTargets" :key="group.parent.id" :value="group.parent.id">
                {{ group.parent.categoryName }}
              </option>
            </select>
          </div>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              class="min-h-[44px]"
              :disabled="saving"
              data-testid="move-cancel"
              @click="moveOpen = false"
            >
              Cancelar
            </Button>
            <Button type="submit" class="min-h-[44px]" :disabled="saving" data-testid="move-save">
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
import { Check, ChevronDown, ChevronRight } from 'lucide-vue-next'
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
import { createCategory, getCategoryOptions, listCategories, updateCategory } from '@/services/categoryService'
import type { Category, CategoryOptions } from '@/types/category'

/**
 * Story 6.1: categorias do usuário agrupadas (grupo → filhas, por nome) e criação de uma categoria filha.
 * Cor e ícone são opcionais: vazios não são enviados e o backend usa a cor do grupo e o ícone `folder`.
 * Story 6.2: criar grupo (sem `parentId`), mover uma filha para outro grupo e recolher/expandir os grupos.
 */
const FIELD_CLASS =
  'flex min-h-[44px] w-full rounded-md border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 focus-visible:ring-2 focus-visible:ring-purple-600 focus-visible:outline-none'
const SUCCESS_TIMEOUT_MS = 5000
const NAME_REQUIRED = 'Informe o nome da categoria'
const SAVE_FAILED = 'Não foi possível criar a categoria. Verifique sua conexão e tente novamente.'
const MOVE_FAILED = 'Não foi possível mover a categoria. Verifique sua conexão e tente novamente.'

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

// Grupos começam expandidos: guarda só os recolhidos
const collapsed = ref(new Set<string>())

const moveOpen = ref(false)
const moving = ref<Category | null>(null)
const moveTarget = ref('')
const moveError = ref<string | null>(null)

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

// Sem grupo, espelha o backend: primeira cor da paleta que nenhum grupo usa, senão a primeira
const previewColor = computed(() => {
  if (form.colorHex) return form.colorHex
  if (form.parentId) return groups.value.find((g) => g.parent.id === form.parentId)?.parent.colorHex ?? '#64748B'
  const used = new Set(groups.value.map((g) => g.parent.colorHex.toUpperCase()))
  return options.value.colors.find((c) => !used.has(c.toUpperCase())) ?? options.value.colors[0] ?? '#64748B'
})

const moveTargets = computed(() => groups.value.filter((g) => g.parent.id !== moving.value?.parentCategoryId))

const createDescription = computed(() =>
  form.parentId
    ? 'Cor e ícone são opcionais: sem eles, a categoria usa a cor do grupo e o ícone de pasta.'
    : 'Cor e ícone são opcionais: sem eles, o grupo recebe uma cor livre da paleta e o ícone de pasta.',
)

function toggle(id: string) {
  const next = new Set(collapsed.value)
  if (!next.delete(id)) next.add(id)
  collapsed.value = next
}

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
      ...(form.parentId && { parentId: form.parentId }),
      ...(form.colorHex && { colorHex: form.colorHex }),
      ...(form.iconName && { iconName: form.iconName }),
    })
    categories.value = [...categories.value, created]
    if (created.parentCategoryId && collapsed.value.has(created.parentCategoryId)) toggle(created.parentCategoryId)
    dialogOpen.value = false
    setSuccess(created.parentCategoryId ? 'Categoria criada' : 'Grupo criado')
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

function openMove(category: Category) {
  moving.value = category
  moveTarget.value = moveTargets.value[0]?.parent.id ?? ''
  moveError.value = null
  moveOpen.value = true
}

function onMoveOpenChange(open: boolean) {
  if (!saving.value) moveOpen.value = open
}

function returnMoveFocus(event: Event) {
  event.preventDefault()
  // depois de mover, a linha é recriada no novo grupo: busca o botão Mover de novo pelo id
  const id = moving.value?.id
  void nextTick(() =>
    (document.querySelector<HTMLElement>(`[data-testid="move-${id}"]`) ?? heading.value)?.focus(),
  )
}

async function move() {
  if (saving.value || !moving.value) return
  moveError.value = null
  saving.value = true
  try {
    const moved = await updateCategory(moving.value.id, { parentId: moveTarget.value })
    categories.value = categories.value.map((c) => (c.id === moved.id ? moved : c))
    // o grupo de destino abre para a categoria (e o foco) ficarem visíveis
    if (moved.parentCategoryId && collapsed.value.has(moved.parentCategoryId)) toggle(moved.parentCategoryId)
    moveOpen.value = false
    const group = groups.value.find((g) => g.parent.id === moved.parentCategoryId)
    setSuccess(`Categoria movida para ${group?.parent.categoryName ?? 'o grupo'}`)
  } catch (err) {
    moveError.value = (err as ApiError)?.response?.data?.detail ?? MOVE_FAILED
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
