<template>
  <main class="min-h-screen bg-white px-4 py-8">
    <div class="mx-auto flex max-w-xl flex-col gap-8">
      <OnboardingStepIndicator :step="1" />

      <!-- Carregando: skeleton em vez de spinner -->
      <div v-if="loading" data-testid="onboarding-skeleton" class="space-y-4" aria-busy="true">
        <div class="mx-auto h-16 w-16 animate-pulse rounded-full bg-gray-100" />
        <div class="mx-auto h-8 w-3/4 animate-pulse rounded bg-gray-100" />
        <div class="h-24 animate-pulse rounded-lg bg-gray-100" />
        <span class="sr-only">Carregando...</span>
      </div>

      <section
        v-else
        aria-live="polite"
        class="space-y-6"
        :data-testid="`onboarding-screen-${screen}`"
      >
        <!-- 0. Boas-vindas -->
        <template v-if="screen === 0">
          <div class="flex justify-center">
            <div class="flex h-20 w-20 items-center justify-center rounded-full bg-purple-100">
              <PiggyBank class="h-10 w-10 text-purple-600" aria-hidden="true" />
            </div>
          </div>
          <div class="space-y-3 text-center">
            <h1 ref="heading" tabindex="-1" class="text-3xl font-bold text-gray-900 outline-none">
              Bem-vindo ao porquinho!
            </h1>
            <p class="text-gray-600">
              Antes de começar, vamos mostrar em 1 minuto a ideia que faz o porquinho funcionar: o
              orçamento por envelope.
            </p>
          </div>
        </template>

        <!-- 1. O que é o envelope -->
        <template v-else-if="screen === 1">
          <h1 ref="heading" tabindex="-1" class="text-2xl font-bold text-gray-900 outline-none">
            O que é o orçamento por envelope?
          </h1>
          <p class="text-gray-600">
            Imagine que você recebe o salário e separa o dinheiro em envelopes: um para a moradia,
            um para o mercado, um para o lazer. Cada real ganha um destino antes de ser gasto.
          </p>
          <figure
            class="rounded-lg bg-purple-50 p-6"
            aria-label="Diagrama: o salário é dividido em três envelopes"
          >
            <div class="flex flex-col items-center gap-3">
              <div class="flex items-center gap-2 rounded-lg bg-white px-4 py-2 shadow-sm">
                <Wallet class="h-5 w-5 text-green-600" aria-hidden="true" />
                <span class="font-semibold text-gray-900">Salário: R$ 3.000</span>
              </div>
              <ArrowDown class="h-5 w-5 text-purple-400" aria-hidden="true" />
              <ul class="grid w-full grid-cols-3 gap-3">
                <li
                  v-for="envelope in exampleEnvelopes"
                  :key="envelope.name"
                  class="flex flex-col items-center gap-1 rounded-lg bg-white p-3 text-center shadow-sm"
                >
                  <Mail class="h-6 w-6 text-purple-600" aria-hidden="true" />
                  <span class="text-sm font-medium text-gray-900">{{ envelope.name }}</span>
                  <span class="text-xs text-gray-600">{{ envelope.amount }}</span>
                </li>
              </ul>
            </div>
          </figure>
        </template>

        <!-- 2. Como funciona no app -->
        <template v-else-if="screen === 2">
          <h1 ref="heading" tabindex="-1" class="text-2xl font-bold text-gray-900 outline-none">
            Como funciona no porquinho
          </h1>
          <p class="text-gray-600">
            Aqui, cada categoria de gasto é um envelope. Quando você gasta, o valor sai do envelope
            certo e você vê na hora quanto ainda sobra.
          </p>
          <figure
            class="space-y-4 rounded-lg bg-purple-50 p-6"
            aria-label="Diagrama: um gasto de R$ 120 no mercado deixa R$ 680 no envelope"
          >
            <div class="flex items-center justify-between text-sm">
              <span class="flex items-center gap-2 font-medium text-gray-900">
                <ShoppingCart class="h-5 w-5 text-purple-600" aria-hidden="true" />
                Envelope Mercado
              </span>
              <span class="text-gray-600">R$ 800</span>
            </div>
            <div class="flex items-center gap-2 text-sm text-gray-700">
              <ArrowRight class="h-4 w-4 text-red-500" aria-hidden="true" />
              Compra no supermercado: <span class="font-semibold text-red-600">- R$ 120</span>
            </div>
            <div>
              <div class="h-3 w-full overflow-hidden rounded-full bg-white">
                <div class="h-full w-[85%] rounded-full bg-green-500" />
              </div>
              <p class="mt-2 text-sm font-semibold text-green-700">Ainda sobram R$ 680</p>
            </div>
          </figure>
        </template>

        <!-- 3. Por que funciona -->
        <template v-else-if="screen === 3">
          <h1 ref="heading" tabindex="-1" class="text-2xl font-bold text-gray-900 outline-none">
            Por que funciona?
          </h1>
          <p class="text-gray-600">
            Você decide para onde o dinheiro vai antes de gastar, e não descobre no fim do mês.
          </p>
          <ul class="space-y-3">
            <li
              v-for="reason in reasons"
              :key="reason.title"
              class="flex items-start gap-3 rounded-lg bg-purple-50 p-4"
            >
              <component
                :is="reason.icon"
                class="mt-0.5 h-5 w-5 shrink-0 text-purple-600"
                aria-hidden="true"
              />
              <div>
                <p class="font-medium text-gray-900">{{ reason.title }}</p>
                <p class="text-sm text-gray-600">{{ reason.text }}</p>
              </div>
            </li>
          </ul>
        </template>

        <!-- 4. Pronto para começar -->
        <template v-else>
          <div class="flex justify-center">
            <div class="flex h-16 w-16 items-center justify-center rounded-full bg-green-100">
              <Rocket class="h-8 w-8 text-green-600" aria-hidden="true" />
            </div>
          </div>
          <h1
            ref="heading"
            tabindex="-1"
            class="text-center text-2xl font-bold text-gray-900 outline-none"
          >
            Pronto para começar?
          </h1>
          <p class="text-center text-gray-600">
            Agora vamos montar sua conta juntos, em poucos passos:
          </p>
          <ul class="space-y-2">
            <li
              v-for="item in nextSteps"
              :key="item.text"
              class="flex items-center gap-3 rounded-lg border border-gray-200 p-3 text-gray-700"
            >
              <component
                :is="item.icon"
                class="h-5 w-5 shrink-0 text-purple-600"
                aria-hidden="true"
              />
              {{ item.text }}
            </li>
          </ul>
        </template>

        <!-- Telas do tutorial: 1 de 4 ... 4 de 4 -->
        <div v-if="screen > 0" class="flex justify-center gap-2" aria-hidden="true">
          <span
            v-for="n in TUTORIAL_SCREENS"
            :key="n"
            class="h-2 w-2 rounded-full"
            :class="n <= screen ? 'bg-purple-600' : 'bg-purple-100'"
          />
        </div>
        <p v-if="screen > 0" class="sr-only">Tela {{ screen }} de {{ TUTORIAL_SCREENS }}</p>
      </section>

      <nav v-if="!loading" class="flex flex-col gap-3" aria-label="Navegação do tutorial">
        <div class="flex gap-3">
          <Button
            v-if="screen > 0"
            variant="outline"
            class="min-h-[44px] flex-1"
            data-testid="back-button"
            :disabled="saving"
            @click="back"
          >
            <ChevronLeft class="h-4 w-4" aria-hidden="true" />
            Voltar
          </Button>
          <Button
            class="min-h-[44px] flex-1 bg-purple-600 text-white hover:bg-purple-700"
            data-testid="next-button"
            :disabled="saving"
            @click="next"
          >
            {{ saving && isLastScreen ? 'Salvando...' : 'Próximo' }}
            <ChevronRight class="h-4 w-4" aria-hidden="true" />
          </Button>
        </div>
        <Button
          variant="ghost"
          class="min-h-[44px] text-gray-600"
          data-testid="skip-button"
          :disabled="saving"
          @click="showSkipDialog = true"
        >
          Pular tutorial
        </Button>
      </nav>

      <Dialog v-model:open="showSkipDialog">
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Pular o tutorial?</DialogTitle>
            <DialogDescription>
              Você pode seguir direto para a configuração da sua conta. O tutorial leva só 1 minuto.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              variant="outline"
              class="min-h-[44px]"
              data-testid="cancel-skip-button"
              :disabled="saving"
              @click="showSkipDialog = false"
            >
              Ver o tutorial
            </Button>
            <Button
              class="min-h-[44px] bg-purple-600 text-white hover:bg-purple-700"
              data-testid="confirm-skip-button"
              :disabled="saving"
              @click="finish(true)"
            >
              {{ saving ? 'Salvando...' : 'Pular' }}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <!-- Toast de erro (não há biblioteca de toast no projeto) -->
      <div
        v-if="toast"
        role="alert"
        data-testid="onboarding-error"
        class="fixed inset-x-4 bottom-4 z-50 mx-auto max-w-md rounded-lg bg-red-600 px-4 py-3 text-sm text-white shadow-lg"
      >
        {{ toast }}
      </div>
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowDown,
  ArrowRight,
  ChevronLeft,
  ChevronRight,
  CreditCard,
  Eye,
  Landmark,
  Lightbulb,
  Mail,
  PiggyBank,
  Rocket,
  ShieldCheck,
  ShoppingCart,
  Tags,
  Target,
  Upload,
  Wallet,
} from 'lucide-vue-next'
import OnboardingStepIndicator from '@/components/onboarding/OnboardingStepIndicator.vue'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { completeTutorial, getMyOnboarding } from '@/services/onboardingService'

// Story 3.1: passo 1 do onboarding = boas-vindas (tela 0) + tutorial de 4 telas.
// Decisão B (2026-11-01): enquanto os passos 2–8 não existem, o destino depois do tutorial é o
// dashboard. A Story 3.2 troca NEXT_ROUTE pelo passo 2.
const NEXT_ROUTE = '/dashboard'
const TUTORIAL_SCREENS = 4
const TOAST_MS = 5000

const exampleEnvelopes = [
  { name: 'Moradia', amount: 'R$ 1.500' },
  { name: 'Mercado', amount: 'R$ 800' },
  { name: 'Lazer', amount: 'R$ 300' },
]

const reasons = [
  {
    icon: Eye,
    title: 'Você sabe quanto pode gastar',
    text: 'Cada envelope mostra o que sobra, sem precisar fazer contas.',
  },
  {
    icon: ShieldCheck,
    title: 'Menos surpresas no fim do mês',
    text: 'As contas importantes já têm dinheiro separado.',
  },
  {
    icon: Lightbulb,
    title: 'Decisões mais fáceis',
    text: 'Quer gastar mais em lazer? Basta tirar de outro envelope.',
  },
]

const nextSteps = [
  { icon: Landmark, text: 'Cadastrar suas contas bancárias' },
  { icon: CreditCard, text: 'Adicionar seus cartões de crédito' },
  { icon: Tags, text: 'Escolher seus envelopes (categorias)' },
  { icon: Upload, text: 'Importar seu extrato' },
  { icon: Target, text: 'Criar sua reserva de emergência' },
]

const router = useRouter()
const loading = ref(true)
const saving = ref(false)
const screen = ref(0)
const showSkipDialog = ref(false)
const toast = ref<string | null>(null)
const heading = ref<HTMLElement | null>(null)

const isLastScreen = computed(() => screen.value === TUTORIAL_SCREENS)

let toastTimer: ReturnType<typeof setTimeout> | null = null

function showError(message: string) {
  toast.value = message
  if (toastTimer) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => (toast.value = null), TOAST_MS)
}

// Leitores de tela e teclado: a cada tela nova o foco vai para o título
watch(screen, async () => {
  await nextTick()
  heading.value?.focus()
})

function back() {
  if (screen.value > 0) screen.value--
}

function next() {
  if (isLastScreen.value) {
    finish(false)
  } else {
    screen.value++
  }
}

async function finish(skipped: boolean) {
  // Duplo clique no mesmo tick: o botão ainda não foi desabilitado
  if (saving.value) return
  saving.value = true
  try {
    await completeTutorial(skipped)
  } catch {
    showSkipDialog.value = false
    showError('Não foi possível salvar seu progresso. Verifique sua conexão e tente novamente.')
    return
  } finally {
    saving.value = false
  }
  // Fora do catch: uma falha de navegação não é falha ao salvar
  showSkipDialog.value = false
  await router.push(NEXT_ROUTE)
}

onMounted(async () => {
  try {
    const progress = await getMyOnboarding()
    if (progress.completed || progress.currentStep >= 2) {
      await router.replace(NEXT_ROUTE)
      return
    }
  } catch {
    // Sem o progresso o tutorial ainda pode ser visto; salvar no fim tenta de novo
    showError(
      'Não foi possível carregar seu progresso. Você pode continuar o tutorial normalmente.',
    )
  }
  loading.value = false
})

onUnmounted(() => {
  if (toastTimer) clearTimeout(toastTimer)
})
</script>
