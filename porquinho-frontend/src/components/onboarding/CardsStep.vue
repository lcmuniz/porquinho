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

      <form
        class="space-y-4 rounded-lg border border-gray-200 p-4"
        novalidate
        data-testid="card-form"
        @submit.prevent="addCard"
      >
        <div class="space-y-1">
          <label for="card-name" class="text-sm font-medium text-gray-900">Nome do cartão</label>
          <Input
            id="card-name"
            v-model="form.cardName"
            maxlength="100"
            placeholder="Ex.: Nubank Roxinho"
            autocomplete="off"
            data-testid="card-name-input"
            :aria-invalid="errors.cardName ? 'true' : undefined"
            :aria-describedby="errors.cardName ? 'card-name-error' : undefined"
          />
          <p
            v-if="errors.cardName"
            id="card-name-error"
            class="text-sm text-red-600"
            data-testid="card-name-error"
          >
            {{ errors.cardName }}
          </p>
        </div>

        <div class="space-y-1">
          <label for="card-bank-name" class="text-sm font-medium text-gray-900">
            Banco <span class="font-normal text-gray-500">(opcional)</span>
          </label>
          <Input
            id="card-bank-name"
            v-model="form.bankName"
            list="card-bank-suggestions"
            maxlength="100"
            placeholder="Digite ou escolha um banco"
            autocomplete="off"
            data-testid="card-bank-name-input"
            :aria-invalid="errors.bankName ? 'true' : undefined"
            :aria-describedby="errors.bankName ? 'card-bank-name-error' : undefined"
          />
          <datalist id="card-bank-suggestions">
            <option v-for="bank in BANKS" :key="bank" :value="bank" />
          </datalist>
          <p v-if="errors.bankName" id="card-bank-name-error" class="text-sm text-red-600">
            {{ errors.bankName }}
          </p>
        </div>

        <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div class="space-y-1">
            <label for="card-limit" class="text-sm font-medium text-gray-900">
              Limite do cartão (R$)
            </label>
            <Input
              id="card-limit"
              v-model="form.cardLimit"
              inputmode="decimal"
              placeholder="0,00"
              autocomplete="off"
              data-testid="card-limit-input"
              :aria-invalid="errors.cardLimit ? 'true' : undefined"
              :aria-describedby="
                errors.cardLimit ? 'card-limit-error card-limit-hint' : 'card-limit-hint'
              "
            />
            <p
              v-if="errors.cardLimit"
              id="card-limit-error"
              class="text-sm text-red-600"
              data-testid="card-limit-error"
            >
              {{ errors.cardLimit }}
            </p>
            <p id="card-limit-hint" class="text-xs text-gray-500">Ex.: 5.000,00</p>
          </div>

          <div class="space-y-1">
            <label for="card-due-day" class="text-sm font-medium text-gray-900">
              Dia do vencimento
            </label>
            <Input
              id="card-due-day"
              v-model="form.dueDay"
              type="number"
              inputmode="numeric"
              min="1"
              max="31"
              step="1"
              placeholder="Ex.: 10"
              autocomplete="off"
              data-testid="card-due-day-input"
              :aria-invalid="errors.dueDay ? 'true' : undefined"
              :aria-describedby="
                errors.dueDay ? 'card-due-day-error card-due-day-hint' : 'card-due-day-hint'
              "
            />
            <p
              v-if="errors.dueDay"
              id="card-due-day-error"
              class="text-sm text-red-600"
              data-testid="card-due-day-error"
            >
              {{ errors.dueDay }}
            </p>
            <p id="card-due-day-hint" class="text-xs text-gray-500">
              Dia do mês em que a fatura vence (1 a 31).
            </p>
          </div>
        </div>

        <Button
          type="submit"
          variant="outline"
          class="min-h-[44px] w-full border-purple-600 text-purple-700"
          data-testid="add-card-button"
          :disabled="busy || loadingList"
        >
          <Plus class="h-4 w-4" aria-hidden="true" />
          {{ adding ? 'Adicionando...' : 'Adicionar cartão' }}
        </Button>
      </form>

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
import { computed, onMounted, reactive, ref } from 'vue'
import {
  ChevronLeft,
  ChevronRight,
  CreditCard as CreditCardIcon,
  Plus,
  Trash2,
} from 'lucide-vue-next'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { BANKS } from '@/lib/banks'
import { formatBRL, parseBRL } from '@/lib/money'
import { createCard, deleteCard, listCards } from '@/services/cardService'
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

const DUE_DAY_MESSAGE = 'Informe um dia entre 1 e 31'

type Field = 'cardName' | 'bankName' | 'cardLimit' | 'dueDay'
const FIELDS: Field[] = ['cardName', 'bankName', 'cardLimit', 'dueDay']

const form = reactive({
  cardName: '',
  bankName: '',
  cardLimit: '',
  // <input type="number">: o v-model pode devolver número ou texto (vazio)
  dueDay: '' as string | number,
})
const errors = reactive<Partial<Record<Field, string>>>({})
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

function clearErrors() {
  for (const key of Object.keys(errors) as Field[]) delete errors[key]
}

/** Validação local, com as mesmas regras e mensagens do backend. */
function validate(): { cardLimit: number; dueDay: number } | null {
  clearErrors()
  const name = form.cardName.trim()
  if (!name) errors.cardName = 'Informe o nome do cartão'
  else if (name.length > 100) errors.cardName = 'Use no máximo 100 caracteres no nome do cartão'

  if (form.bankName.trim().length > 100) {
    errors.bankName = 'Use no máximo 100 caracteres no nome do banco'
  }

  let cardLimit: number | null = null
  if (!form.cardLimit.trim()) {
    errors.cardLimit = 'Informe o limite do cartão (use 0,00 se não souber)'
  } else {
    cardLimit = parseBRL(form.cardLimit)
    if (cardLimit === null) {
      errors.cardLimit = 'Digite o limite no formato 5.000,00 (até 2 casas depois da vírgula)'
    } else if (cardLimit < 0) {
      errors.cardLimit = 'O limite não pode ser negativo'
    }
  }

  const dayText = String(form.dueDay ?? '').trim()
  const dueDay = /^\d{1,2}$/.test(dayText) ? Number(dayText) : NaN
  if (!(dueDay >= 1 && dueDay <= 31)) errors.dueDay = DUE_DAY_MESSAGE

  if (Object.keys(errors).length > 0 || cardLimit === null) return null
  return { cardLimit, dueDay }
}

async function addCard() {
  // Durante o carregamento inicial a resposta da lista sobrescreveria o cartão recém-adicionado
  if (busy.value || loadingList.value) return
  const values = validate()
  if (values === null) return

  adding.value = true
  try {
    const created = await createCard({
      cardName: form.cardName.trim(),
      bankName: form.bankName.trim() || null,
      cardLimit: values.cardLimit,
      dueDay: values.dueDay,
    })
    cards.value.push(created)
    form.cardName = ''
    form.bankName = ''
    form.cardLimit = ''
    form.dueDay = ''
  } catch (err) {
    const fieldErrors = (err as ApiError)?.response?.data?.errors ?? {}
    const known = Object.entries(fieldErrors).filter(([key]) => FIELDS.includes(key as Field))
    if ((err as ApiError)?.response?.status === 400 && known.length > 0) {
      Object.assign(errors, Object.fromEntries(known))
    } else {
      emit(
        'error',
        apiDetail(err) ??
          'Não foi possível adicionar o cartão. Verifique sua conexão e tente novamente.',
      )
    }
  } finally {
    adding.value = false
  }
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
