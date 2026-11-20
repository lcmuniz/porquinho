<template>
  <div class="min-h-screen bg-gray-50 lg:pl-62">
    <!-- Desktop: sidebar fixa -->
    <aside
      class="fixed inset-y-0 left-0 z-30 hidden w-62 flex-col border-r border-gray-200 bg-white lg:flex"
      aria-label="Menu principal"
    >
      <RouterLink to="/dashboard" class="flex h-16 items-center gap-2.5 px-5" aria-label="porquinho, ir para o início">
        <PiggyBank class="size-7 text-purple-600" aria-hidden="true" />
        <span class="text-lg font-bold tracking-tight text-gray-900">porquinho</span>
      </RouterLink>
      <nav aria-label="Navegação principal" class="flex flex-1 flex-col gap-6 overflow-y-auto px-3 py-4">
        <ul v-for="(group, gi) in groups" :key="gi" class="space-y-1">
          <li v-for="item in group" :key="item.to">
            <RouterLink
              :to="item.to"
              :data-testid="item.testid"
              class="flex min-h-11 items-center gap-3 rounded-[10px] px-3 text-[15px] font-medium text-gray-700 transition-colors hover:bg-gray-100 hover:text-gray-900"
              :class="isActive(item.to) && 'bg-purple-50 text-purple-700 hover:bg-purple-50 hover:text-purple-700'"
              :aria-current="isActive(item.to) ? 'page' : undefined"
            >
              <component :is="item.icon" class="size-5 shrink-0" aria-hidden="true" />
              {{ item.label }}
            </RouterLink>
          </li>
        </ul>
      </nav>
      <div class="space-y-1 border-t border-gray-200 px-3 py-3">
        <RouterLink
          v-for="item in footerItems"
          :key="item.to"
          :to="item.to"
          :data-testid="item.testid"
          class="flex min-h-11 items-center gap-3 rounded-[10px] px-3 text-[15px] font-medium text-gray-700 transition-colors hover:bg-gray-100 hover:text-gray-900"
          :class="isActive(item.to) && 'bg-purple-50 text-purple-700'"
          :aria-current="isActive(item.to) ? 'page' : undefined"
        >
          <component :is="item.icon" class="size-5 shrink-0" aria-hidden="true" />
          {{ item.label }}
        </RouterLink>
        <button
          type="button"
          data-testid="logout"
          class="flex min-h-11 w-full items-center gap-3 rounded-[10px] px-3 text-left text-[15px] font-medium text-gray-700 transition-colors hover:bg-gray-100 hover:text-gray-900"
          @click="logout"
        >
          <LogOut class="size-5 shrink-0" aria-hidden="true" />
          Sair
        </button>
      </div>
    </aside>

    <!-- Mobile: topbar -->
    <header class="sticky top-0 z-30 flex h-14 items-center justify-between border-b border-gray-200 bg-white/95 px-4 backdrop-blur lg:hidden">
      <RouterLink to="/dashboard" class="flex items-center gap-2" aria-label="porquinho, ir para o início">
        <PiggyBank class="size-6 text-purple-600" aria-hidden="true" />
        <span class="text-base font-bold tracking-tight text-gray-900">porquinho</span>
      </RouterLink>
      <RouterLink
        to="/configuracoes"
        class="inline-flex size-11 items-center justify-center rounded-[10px] text-gray-700 hover:bg-gray-100"
        aria-label="Configurações"
      >
        <Settings class="size-5" aria-hidden="true" />
      </RouterLink>
    </header>

    <div class="pb-24 lg:pb-0">
      <slot />
    </div>

    <!-- Mobile: barra inferior com 5 destinos -->
    <nav
      aria-label="Navegação principal"
      class="fixed inset-x-0 bottom-0 z-30 border-t border-gray-200 bg-white pb-[env(safe-area-inset-bottom)] lg:hidden"
    >
      <ul class="grid grid-cols-5">
        <li v-for="item in bottomItems" :key="item.to">
          <RouterLink
            :to="item.to"
            class="flex min-h-16 flex-col items-center justify-center gap-1 text-[13px] font-medium text-gray-600"
            :class="isActive(item.to) && 'text-purple-700'"
            :aria-current="isActive(item.to) ? 'page' : undefined"
          >
            <component :is="item.icon" class="size-6" aria-hidden="true" />
            {{ item.short }}
          </RouterLink>
        </li>
        <li>
          <button
            ref="moreButton"
            type="button"
            class="flex min-h-16 w-full flex-col items-center justify-center gap-1 text-[13px] font-medium text-gray-600"
            :class="(moreOpen || moreActive) && 'text-purple-700'"
            :aria-expanded="moreOpen"
            aria-controls="more-sheet"
            @click="moreOpen = !moreOpen"
          >
            <Menu class="size-6" aria-hidden="true" />
            Mais
          </button>
        </li>
      </ul>
    </nav>

    <!-- Folha "Mais" -->
    <div v-if="moreOpen" class="fixed inset-0 z-40 lg:hidden" @keydown.esc="moreOpen = false">
      <div class="absolute inset-0 bg-gray-900/40" aria-hidden="true" @click="moreOpen = false" />
      <div
        id="more-sheet"
        role="dialog"
        aria-modal="true"
        aria-label="Mais opções"
        @keydown.tab="trapFocus"
        class="absolute inset-x-0 bottom-0 rounded-t-2xl bg-white px-4 pt-3 pb-[calc(1rem+env(safe-area-inset-bottom))] shadow-[0_-8px_32px_rgb(15_23_42/0.18)]"
      >
        <div class="mx-auto mb-3 h-1.5 w-10 rounded-full bg-gray-300" aria-hidden="true" />
        <ul class="space-y-1">
          <li v-for="item in moreItems" :key="item.to">
            <RouterLink
              :to="item.to"
              class="flex min-h-12 items-center gap-3 rounded-[10px] px-3 text-base font-medium text-gray-900 hover:bg-gray-100"
              :class="isActive(item.to) && 'bg-purple-50 text-purple-700'"
              @click="moreOpen = false"
            >
              <component :is="item.icon" class="size-5 shrink-0 text-gray-600" aria-hidden="true" />
              {{ item.label }}
            </RouterLink>
          </li>
          <li>
            <button
              type="button"
              class="flex min-h-12 w-full items-center gap-3 rounded-[10px] px-3 text-left text-base font-medium text-gray-900 hover:bg-gray-100"
              @click="logout"
            >
              <LogOut class="size-5 shrink-0 text-gray-600" aria-hidden="true" />
              Sair
            </button>
          </li>
        </ul>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import {
  ArrowLeftRight,
  CreditCard,
  FileUp,
  Goal,
  House,
  Landmark,
  LogOut,
  Menu,
  PiggyBank,
  Settings,
  ShieldCheck,
  Tags,
  Wallet,
} from 'lucide-vue-next'
import { getAdminAccess } from '@/services/adminService'
import { supabase } from '@/services/supabase'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const daily = [
  { to: '/dashboard', label: 'Início', short: 'Início', icon: House, testid: 'dashboard-link' },
  { to: '/transacoes', label: 'Transações', short: 'Transações', icon: ArrowLeftRight, testid: 'transactions-link' },
  { to: '/orcamento', label: 'Orçamento', short: 'Orçamento', icon: Wallet, testid: 'budget-link' },
  { to: '/metas', label: 'Metas', short: 'Metas', icon: Goal, testid: 'goals-link' },
]
const structure = [
  { to: '/contas', label: 'Contas bancárias', icon: Landmark, testid: 'accounts-link' },
  { to: '/cartoes', label: 'Cartões de crédito', icon: CreditCard, testid: 'cards-link' },
  { to: '/categorias', label: 'Categorias', icon: Tags, testid: 'categories-link' },
  { to: '/importar', label: 'Importar extrato', icon: FileUp, testid: 'import-link' },
]

// Story 10.1: o link "Admin" só aparece para admin; falha na leitura esconde o link
const isAdmin = ref(false)
onMounted(async () => {
  try {
    isAdmin.value = (await getAdminAccess()).admin
  } catch {
    isAdmin.value = false
  }
})

const settingsItem = { to: '/configuracoes', label: 'Configurações', icon: Settings, testid: 'settings-link' }
const adminItem = { to: '/admin', label: 'Admin', icon: ShieldCheck, testid: 'admin-link' }

const groups = [daily, structure]
const footerItems = computed(() => (isAdmin.value ? [adminItem, settingsItem] : [settingsItem]))
const bottomItems = daily
const moreItems = computed(() => [...structure, ...footerItems.value])

function isActive(to: string) {
  return route.path === to || route.path.startsWith(`${to}/`)
}
const moreActive = computed(() => moreItems.value.some((i) => isActive(i.to)))

const moreOpen = ref(false)
const moreButton = ref<HTMLButtonElement>()
watch(moreOpen, async (open) => {
  if (!open) {
    // ao fechar, o foco volta ao botão "Mais"
    moreButton.value?.focus()
    return
  }
  await nextTick()
  // foco entra na folha para quem navega por teclado
  document.querySelector<HTMLElement>('#more-sheet a, #more-sheet button')?.focus()
})

// Tab/Shift+Tab ciclam dentro da folha (aria-modal)
function trapFocus(e: KeyboardEvent) {
  const items = [...document.querySelectorAll<HTMLElement>('#more-sheet a, #more-sheet button')]
  const first = items[0]
  const last = items[items.length - 1]
  if (!first || !last) return
  if (e.shiftKey && document.activeElement === first) {
    e.preventDefault()
    last.focus()
  } else if (!e.shiftKey && document.activeElement === last) {
    e.preventDefault()
    first.focus()
  }
}
watch(
  () => route.path,
  () => (moreOpen.value = false),
)

async function logout() {
  // O guard do router usa a chave 'user' do localStorage: limpar primeiro garante a ida ao /login
  authStore.clearUser()
  try {
    await supabase.auth.signOut()
  } catch (err) {
    console.warn('Supabase signOut failed', err)
  }
  await router.push('/login')
}
</script>
