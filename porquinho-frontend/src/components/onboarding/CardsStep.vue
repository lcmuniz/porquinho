<template>
  <div class="flex flex-col gap-8">
    <section class="space-y-6" data-testid="cards-step">
      <div class="space-y-3">
        <h1 ref="heading" tabindex="-1" class="text-2xl font-bold text-gray-900 outline-none">
          Adicione seus cartões de crédito
        </h1>
        <p class="text-gray-600">
          Cadastre os cartões que você usa no dia a dia. Assim o porquinho já sabe de onde vêm as
          compras quando você importar a fatura. Se não usa cartão, é só seguir.
        </p>
      </div>

      <!-- Bloqueado enquanto a lista carrega: a resposta tardia da lista sobrescreveria o cartão recém-adicionado -->
      <CardForm
        v-model:submitting="adding"
        class="rounded-lg border border-gray-200 p-4"
        :disabled="removing || finishing !== null || loadingList"
        @created="onCreated"
        @failed="onFailed"
      />

      <div v-if="loadingList" class="space-y-2" aria-busy="true" data-testid="cards-skeleton">
        <div class="h-14 animate-pulse rounded-lg bg-gray-100" />
        <span class="sr-only">Carregando seus cartões...</span>
      </div>
      <div v-else class="space-y-2">
        <h2 class="text-sm font-medium text-gray-900">Seus cartões ({{ cards.length }})</h2>
        <p v-if="cards.length === 0" class="text-sm text-gray-500" data-testid="cards-empty">
          Nenhum cartão adicionado ainda.
        </p>
        <ul v-else class="space-y-2" data-testid="cards-list" aria-live="polite">
          <li
            v-for="card in cards"
            :key="card.id"
            class="flex items-center gap-3 rounded-lg border border-gray-200 p-3"
            :data-testid="`card-item-${card.id}`"
          >
            <CreditCardIcon class="h-5 w-5 shrink-0 text-purple-600" aria-hidden="true" />
            <div class="min-w-0 flex-1">
              <p class="truncate font-medium text-gray-900">{{ card.cardName }}</p>
              <p class="truncate text-xs text-gray-500" data-testid="card-details">
                <template v-if="card.bankName">{{ card.bankName }} · </template>vence dia
                {{ card.dueDay }}
              </p>
            </div>
            <span
              class="text-sm font-semibold whitespace-nowrap text-gray-900"
              data-testid="card-limit"
            >
              {{ formatBRL(card.cardLimit) }}
            </span>
            <Button
              variant="ghost"
              class="min-h-[44px] text-gray-600"
              :data-testid="`remove-card-${card.id}`"
              :aria-label="`Remover o cartão ${card.cardName}`"
              :disabled="busy"
              @click="removeCard(card)"
            >
              <Trash2 class="h-4 w-4" aria-hidden="true" />
              Remover
            </Button>
          </li>
        </ul>
      </div>
    </section>

    <nav class="flex flex-col gap-3" aria-label="Navegação do passo de cartões">
      <div class="flex gap-3">
        <Button
          variant="outline"
          class="min-h-[44px] flex-1"
          data-testid="back-button"
          :disabled="busy"
          @click="emit('back')"
        >
          <ChevronLeft class="h-4 w-4" aria-hidden="true" />
          Voltar
        </Button>
        <Button
          class="min-h-[44px] flex-1 bg-purple-600 text-white hover:bg-purple-700"
          data-testid="next-button"
          :disabled="busy"
          @click="finish(false)"
        >
          {{ finishing === 'next' ? 'Salvando...' : 'Próximo' }}
          <ChevronRight class="h-4 w-4" aria-hidden="true" />
        </Button>
      </div>
      <p v-if="!loadingList && cards.length === 0" class="text-center text-xs text-gray-500">
        Os cartões são opcionais. Você pode cadastrá-los depois.
      </p>
      <Button
        variant="ghost"
        class="min-h-[44px] text-gray-600"
        data-testid="skip-button"
        :disabled="busy"
        @click="finish(true)"
      >
        {{ finishing === 'skip' ? 'Salvando...' : 'Pular por agora' }}
      </Button>
    </nav>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ChevronLeft, ChevronRight, CreditCard as CreditCardIcon, Trash2 } from 'lucide-vue-next'
import CardForm from '@/components/cards/CardForm.vue'
import { Button } from '@/components/ui/button'
import { formatBRL } from '@/lib/money'
import { deleteCard, listCards } from '@/services/cardService'
import { completeCards } from '@/services/onboardingService'
import type { CreditCard } from '@/types/creditCard'
import type { OnboardingProgress } from '@/types/onboarding'

/**
 * Story 3.3: passo 3 do onboarding. Adiciona cartões um a um, lista e remove; Próximo, Voltar e Pular.
 * Cartões são opcionais: Próximo sem cartões é permitido (o backend grava `cards_skipped`).
 */
const emit = defineEmits<{
  back: []
  done: [progress: OnboardingProgress]
  error: [message: string]
}>()

const cards = ref<CreditCard[]>([])
const loadingList = ref(true)
const adding = ref(false)
const removing = ref(false)
const finishing = ref<'next' | 'skip' | null>(null)
const heading = ref<HTMLElement | null>(null)

const busy = computed(() => adding.value || removing.value || finishing.value !== null)

interface ApiError {
  response?: { status?: number; data?: { detail?: string; errors?: Record<string, string> } }
}

function apiDetail(err: unknown): string | undefined {
  return (err as ApiError)?.response?.data?.detail
}

function onCreated(created: CreditCard) {
  cards.value.push(created)
}

function onFailed(detail: string | undefined) {
  emit(
    'error',
    detail ?? 'Não foi possível adicionar o cartão. Verifique sua conexão e tente novamente.',
  )
}

async function removeCard(card: CreditCard) {
  if (busy.value) return
  removing.value = true
  try {
    await deleteCard(card.id)
    cards.value = cards.value.filter((c) => c.id !== card.id)
  } catch (err) {
    if ((err as ApiError)?.response?.status === 404) {
      // Já tinha sido removido (outra aba): só tira da lista
      cards.value = cards.value.filter((c) => c.id !== card.id)
    } else {
      emit('error', 'Não foi possível remover o cartão. Verifique sua conexão e tente novamente.')
    }
  } finally {
    removing.value = false
  }
}

async function finish(skipped: boolean) {
  // Duplo clique no mesmo tick: o botão ainda não foi desabilitado
  if (busy.value) return
  finishing.value = skipped ? 'skip' : 'next'
  let progress: OnboardingProgress
  try {
    progress = await completeCards(skipped)
  } catch (err) {
    // Só libera os botões no erro: no sucesso a tela troca, e liberar antes permitiria outro POST
    finishing.value = null
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
  try {
    cards.value = await listCards()
  } catch {
    emit('error', 'Não foi possível carregar seus cartões. Você ainda pode adicionar novos.')
  } finally {
    loadingList.value = false
  }
})
</script>
