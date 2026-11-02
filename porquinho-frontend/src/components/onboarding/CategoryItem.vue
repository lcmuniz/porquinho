<template>
  <div
    ref="root"
    class="relative"
    :data-testid="`category-item-${category.id}`"
    @keydown.esc="closePicker(true)"
  >
    <div
      class="flex min-h-[44px] items-center gap-2 rounded-lg"
      :class="kind === 'child' ? 'border border-gray-200 bg-white p-2' : ''"
    >
      <button
        ref="pickerTrigger"
        type="button"
        class="flex h-11 w-11 shrink-0 items-center justify-center rounded-full focus:ring-2 focus:ring-purple-500 focus:outline-none"
        :style="{ backgroundColor: `${category.colorHex}1A` }"
        :aria-label="`Trocar cor e ícone de ${category.categoryName}`"
        aria-haspopup="dialog"
        :aria-expanded="pickerOpen"
        :disabled="disabled"
        :data-testid="`category-picker-trigger-${category.id}`"
        @click="pickerOpen ? closePicker(false) : openPicker()"
      >
        <component
          :is="categoryIcon(category.iconName)"
          :class="kind === 'group' ? 'h-4 w-4' : 'h-5 w-5'"
          :style="{ color: category.colorHex }"
          aria-hidden="true"
        />
      </button>

      <input
        v-model="draft"
        type="text"
        maxlength="50"
        class="min-h-[44px] min-w-0 flex-1 rounded-md border border-transparent bg-transparent px-2 text-gray-900 hover:border-gray-200 focus:border-purple-500 focus:ring-2 focus:ring-purple-500 focus:outline-none"
        :class="kind === 'group' ? 'text-sm font-semibold' : 'font-medium'"
        :aria-label="
          kind === 'group'
            ? `Nome do grupo ${category.categoryName}`
            : `Nome da categoria ${category.categoryName}`
        "
        :aria-invalid="error ? 'true' : undefined"
        :aria-describedby="error ? errorId : undefined"
        :disabled="disabled"
        :data-testid="`category-name-${category.id}`"
        @keydown.enter.prevent="($event.target as HTMLInputElement).blur()"
        @keydown.esc.stop="cancelName($event)"
        @focus="editing = true"
        @blur="commitName"
      />

      <button
        v-if="canDelete"
        type="button"
        class="flex h-11 w-11 shrink-0 items-center justify-center rounded-md text-gray-500 hover:bg-red-50 hover:text-red-600 focus:ring-2 focus:ring-purple-500 focus:outline-none"
        :aria-label="
          kind === 'group'
            ? `Excluir o grupo ${category.categoryName}`
            : `Excluir ${category.categoryName}`
        "
        :disabled="disabled"
        :data-testid="`category-delete-${category.id}`"
        @click="emit('delete', category)"
      >
        <Trash2 class="h-4 w-4" aria-hidden="true" />
      </button>
    </div>

    <p
      v-if="error"
      :id="errorId"
      role="alert"
      class="mt-1 text-sm text-red-600"
      :data-testid="`category-error-${category.id}`"
    >
      {{ error }}
    </p>

    <div
      v-if="pickerOpen"
      role="dialog"
      :aria-label="`Cor e ícone de ${category.categoryName}`"
      class="absolute left-0 z-20 mt-2 w-72 max-w-[calc(100vw-2rem)] space-y-4 rounded-lg border border-gray-200 bg-white p-4 shadow-lg"
      :aria-busy="choosing"
      :data-testid="`category-picker-${category.id}`"
      @focusout="onPickerFocusOut"
    >
      <fieldset class="space-y-2">
        <legend class="text-sm font-semibold text-gray-900">Cor</legend>
        <div class="grid grid-cols-7 gap-1">
          <button
            v-for="color in options.colors"
            :key="color"
            type="button"
            class="flex h-11 w-11 items-center justify-center rounded-full focus:ring-2 focus:ring-purple-500 focus:ring-offset-1 focus:outline-none aria-disabled:cursor-wait aria-disabled:opacity-50"
            :aria-label="categoryColorLabel(color)"
            :aria-disabled="choosing ? 'true' : undefined"
            :aria-pressed="sameColor(color, category.colorHex)"
            :data-testid="`color-option-${color}`"
            @click="choose({ colorHex: color })"
          >
            <span
              class="flex h-7 w-7 items-center justify-center rounded-full"
              :style="{ backgroundColor: color }"
              aria-hidden="true"
            >
              <Check v-if="sameColor(color, category.colorHex)" class="h-4 w-4 text-white" />
            </span>
          </button>
        </div>
      </fieldset>

      <fieldset class="space-y-2">
        <legend class="text-sm font-semibold text-gray-900">Ícone</legend>
        <div class="grid grid-cols-6 gap-1">
          <button
            v-for="icon in options.icons"
            :key="icon"
            type="button"
            class="flex h-11 w-11 items-center justify-center rounded-md border focus:ring-2 focus:ring-purple-500 focus:outline-none aria-disabled:cursor-wait aria-disabled:opacity-50"
            :class="
              icon === category.iconName
                ? 'border-purple-500 bg-purple-50'
                : 'border-transparent hover:bg-gray-50'
            "
            :aria-label="categoryIconLabel(icon)"
            :aria-disabled="choosing ? 'true' : undefined"
            :aria-pressed="icon === category.iconName"
            :data-testid="`icon-option-${icon}`"
            @click="choose({ iconName: icon })"
          >
            <component
              :is="categoryIcon(icon)"
              class="h-5 w-5"
              :style="{ color: category.colorHex }"
              aria-hidden="true"
            />
          </button>
        </div>
      </fieldset>

      <div class="flex justify-end">
        <button
          type="button"
          class="min-h-[44px] rounded-md px-3 text-sm font-medium text-purple-700 hover:bg-purple-50 focus:ring-2 focus:ring-purple-500 focus:outline-none"
          :data-testid="`category-picker-close-${category.id}`"
          @click="closePicker(true)"
        >
          Fechar
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { Check, Trash2 } from 'lucide-vue-next'
import { categoryColorLabel, categoryIcon, categoryIconLabel } from '@/lib/categoryIcons'
import { cleanCategoryName } from '@/lib/categoryName'
import { updateCategory } from '@/services/categoryService'
import type { Category, CategoryOptions, UpdateCategoryInput } from '@/types/category'

/**
 * Story 3.5: uma categoria (ou o título de um grupo) no passo 5. O nome é editado no lugar e salvo ao sair
 * do campo ou com Enter; Esc cancela. A cor e o ícone são escolhidos num popover e salvos ao escolher.
 * Uma falha volta ao valor anterior e mostra o erro logo abaixo. Enquanto uma cor ou um ícone está sendo
 * salvo, as opções ficam indisponíveis (evita PATCHes fora de ordem).
 */
const props = defineProps<{
  category: Category
  options: CategoryOptions
  kind: 'group' | 'child'
  canDelete: boolean
  disabled?: boolean
}>()

const emit = defineEmits<{
  updated: [category: Category]
  pending: [request: Promise<unknown>]
  delete: [category: Category]
}>()

const NAME_REQUIRED = 'Informe o nome da categoria'
const SAVE_FAILED = 'Não foi possível salvar. Verifique sua conexão e tente novamente.'

const draft = ref(props.category.categoryName)
const error = ref('')
const pickerOpen = ref(false)
/** O campo do nome está com o foco: o nome vindo do pai não sobrescreve o que o usuário digita. */
const editing = ref(false)
/** Uma cor ou um ícone está sendo salvo. */
const choosing = ref(false)
const root = ref<HTMLElement | null>(null)
const pickerTrigger = ref<HTMLButtonElement | null>(null)
const errorId = computed(() => `category-error-${props.category.id}`)
let cancelling = false

watch(
  () => props.category.categoryName,
  (name) => {
    if (!editing.value) draft.value = name
  },
)

interface ApiError {
  response?: { data?: { detail?: string } }
}

function apiDetail(err: unknown): string | undefined {
  return (err as ApiError)?.response?.data?.detail
}

function sameColor(a: string, b: string): boolean {
  return a.toUpperCase() === b.toUpperCase()
}

async function save(input: UpdateCategoryInput): Promise<boolean> {
  const request = updateCategory(props.category.id, input)
  emit('pending', request)
  try {
    emit('updated', await request)
    error.value = ''
    return true
  } catch (err) {
    error.value = apiDetail(err) ?? SAVE_FAILED
    return false
  }
}

function cancelName(event: KeyboardEvent) {
  cancelling = true
  draft.value = props.category.categoryName
  error.value = ''
  ;(event.target as HTMLInputElement).blur()
}

async function commitName() {
  editing.value = false
  if (cancelling) {
    cancelling = false
    return
  }
  const previous = props.category.categoryName
  const name = cleanCategoryName(draft.value)
  if (name === previous) {
    draft.value = previous
    return
  }
  if (!name) {
    draft.value = previous
    error.value = NAME_REQUIRED
    return
  }
  draft.value = name
  // Se o usuário voltou ao campo enquanto salvava, o que ele digita não é sobrescrito
  if (!(await save({ name })) && !editing.value) {
    draft.value = previous
  }
}

async function choose(input: UpdateCategoryInput) {
  if (choosing.value) return
  if (input.colorHex && sameColor(input.colorHex, props.category.colorHex)) return
  if (input.iconName && input.iconName === props.category.iconName) return
  choosing.value = true
  try {
    await save(input)
  } finally {
    choosing.value = false
  }
}

/** O foco saiu do popover (Tab para fora): fecha. Ir para o botão que o abriu não fecha (o clique alterna). */
function onPickerFocusOut(event: FocusEvent) {
  const next = event.relatedTarget as Node | null
  const popover = event.currentTarget as HTMLElement
  if (next && !popover.contains(next) && next !== pickerTrigger.value) closePicker(false)
}

function onOutsidePointer(event: PointerEvent) {
  if (root.value && !root.value.contains(event.target as Node)) closePicker(false)
}

async function openPicker() {
  pickerOpen.value = true
  document.addEventListener('pointerdown', onOutsidePointer)
  await nextTick()
  // Foco na primeira opção do popover, para escolher pelo teclado
  root.value?.querySelector<HTMLButtonElement>('[role="dialog"] button')?.focus()
}

function closePicker(returnFocus: boolean) {
  if (!pickerOpen.value) return
  pickerOpen.value = false
  document.removeEventListener('pointerdown', onOutsidePointer)
  if (returnFocus) pickerTrigger.value?.focus()
}

onBeforeUnmount(() => document.removeEventListener('pointerdown', onOutsidePointer))
</script>
