<template>
  <main class="px-4 py-6 md:px-8 md:py-8">
    <div class="mx-auto max-w-3xl space-y-6">
      <h1 class="text-2xl font-bold text-gray-900 md:text-[28px]">Configurações</h1>

      <section aria-labelledby="notifications-heading" class="rounded-lg border border-gray-200 bg-white p-4 md:p-6">
        <h2 id="notifications-heading" class="text-base font-semibold text-gray-900">Avisos</h2>
        <!-- Pré-Epic 10: chave global do alerta de orçamento; some se a leitura falhar (ex.: sem acesso, 402) -->
        <label
          v-if="budgetAlertEmail !== null"
          class="mt-3 flex min-h-[44px] items-center gap-3 text-[15px] text-gray-900"
        >
          <input
            :checked="budgetAlertEmail"
            type="checkbox"
            class="size-5 accent-purple-600"
            data-testid="budget-alert-email"
            :disabled="savingPreferences"
            @change="toggleBudgetAlert(($event.target as HTMLInputElement).checked)"
          />
          Receber alerta de orçamento por e-mail
        </label>
        <p v-else class="mt-2 text-sm text-gray-600">Não foi possível carregar suas preferências.</p>
        <p v-if="preferencesError" role="alert" class="mt-2 text-sm text-red-700" data-testid="budget-alert-email-error">
          {{ preferencesError }}
        </p>
      </section>

      <section aria-labelledby="account-settings-heading" class="rounded-lg border border-gray-200 bg-white">
        <h2 id="account-settings-heading" class="px-4 pt-4 text-base font-semibold text-gray-900 md:px-6 md:pt-6">
          Sua conta
        </h2>
        <ul class="mt-2 divide-y divide-gray-200">
          <li v-for="link in accountLinks" :key="link.to">
            <RouterLink
              :to="link.to"
              class="flex min-h-14 items-center gap-3 px-4 text-[15px] font-medium text-gray-900 hover:bg-gray-50 md:px-6"
            >
              <component :is="link.icon" class="size-5 shrink-0 text-gray-600" aria-hidden="true" />
              <span class="flex-1">{{ link.label }}</span>
              <ChevronRight class="size-5 text-gray-400" aria-hidden="true" />
            </RouterLink>
          </li>
        </ul>
      </section>

      <section aria-labelledby="danger-zone-heading" class="rounded-lg border border-red-200 bg-white p-4 md:p-6">
        <h2 id="danger-zone-heading" class="text-base font-semibold text-red-700">Zona de perigo</h2>
        <p class="mt-1 text-sm text-gray-600">Excluir a conta apaga todos os seus dados de forma permanente.</p>
        <RouterLink
          to="/auth/account-deletion"
          class="mt-4 inline-flex min-h-[44px] items-center rounded-[10px] border border-red-300 px-4 text-sm font-semibold text-red-700 hover:bg-red-50"
        >
          Excluir minha conta
        </RouterLink>
      </section>
    </div>
  </main>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ChevronRight, Download, MonitorSmartphone, Receipt } from 'lucide-vue-next'
import { getMyPreferences, updateMyPreferences } from '@/services/userPreferencesService'

const accountLinks = [
  { to: '/assinatura', label: 'Assinatura', icon: Receipt },
  { to: '/auth/sessions', label: 'Sessões ativas', icon: MonitorSmartphone },
  { to: '/auth/data-export', label: 'Exportar meus dados', icon: Download },
]

// Pré-Epic 10: null enquanto carrega ou se a leitura falhou (o checkbox fica escondido)
const budgetAlertEmail = ref<boolean | null>(null)
const savingPreferences = ref(false)
const preferencesError = ref('')

async function loadPreferences() {
  try {
    budgetAlertEmail.value = (await getMyPreferences()).budgetAlertEmail
  } catch {
    budgetAlertEmail.value = null
  }
}

async function toggleBudgetAlert(enabled: boolean) {
  const previous = budgetAlertEmail.value
  budgetAlertEmail.value = enabled
  savingPreferences.value = true
  preferencesError.value = ''
  try {
    budgetAlertEmail.value = (await updateMyPreferences({ budgetAlertEmail: enabled })).budgetAlertEmail
  } catch {
    budgetAlertEmail.value = previous
    preferencesError.value = 'Não foi possível salvar a preferência. Tente de novo.'
  } finally {
    savingPreferences.value = false
  }
}

onMounted(loadPreferences)
</script>
