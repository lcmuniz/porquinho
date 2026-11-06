<template>
  <div class="flex flex-col gap-8">
    <section class="space-y-6" data-testid="import-step">
      <div class="space-y-3">
        <h1 ref="heading" tabindex="-1" class="text-2xl font-bold text-gray-900 outline-none">
          Importe suas transações
        </h1>
        <p class="text-gray-600">
          Baixe o extrato do seu banco em formato OFX ou CSV e faça upload aqui
        </p>
      </div>

      <ImportUploader @imports-change="imports = $event" />

      <div class="space-y-2">
        <h2 class="text-sm font-medium text-gray-900">Como baixar o extrato no seu banco</h2>
        <ul class="space-y-2" data-testid="bank-instructions">
          <li v-for="bank in BANK_GUIDES" :key="bank.key">
            <details
              class="group rounded-lg border border-gray-200"
              :data-testid="`bank-guide-${bank.key}`"
            >
              <summary
                class="flex min-h-[44px] cursor-pointer list-none items-center justify-between gap-3 rounded-lg px-4 py-2 font-medium text-gray-900 focus-visible:ring-2 focus-visible:ring-purple-600 focus-visible:outline-none [&::-webkit-details-marker]:hidden"
              >
                <span class="flex items-center gap-3">
                  <Landmark class="h-5 w-5 shrink-0 text-purple-600" aria-hidden="true" />
                  {{ bank.name }}
                </span>
                <ChevronDown
                  class="h-4 w-4 shrink-0 text-gray-500 transition-transform group-open:rotate-180"
                  aria-hidden="true"
                />
              </summary>
              <ol class="list-decimal space-y-1 px-4 pt-1 pb-4 pl-9 text-sm text-gray-700">
                <li v-for="(stepText, i) in bank.steps" :key="i">{{ stepText }}</li>
              </ol>
            </details>
          </li>
        </ul>
        <p class="text-xs text-gray-500">
          Os nomes dos menus podem mudar de um banco para outro. Se não achar a opção, procure por
          "extrato" e "exportar" no app ou no internet banking.
        </p>
      </div>
    </section>

    <div class="flex flex-wrap gap-3">
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
        :variant="completedImport ? 'outline' : 'default'"
        class="min-h-[44px] flex-1"
        :class="completedImport ? '' : 'bg-purple-600 text-white hover:bg-purple-700'"
        data-testid="import-later-button"
        :disabled="finishing"
        @click="finish()"
      >
        {{ finishing ? 'Salvando...' : 'Importar depois' }}
        <ChevronRight class="h-4 w-4" aria-hidden="true" />
      </Button>
      <Button
        v-if="completedImport"
        class="min-h-[44px] flex-1 bg-purple-600 text-white hover:bg-purple-700"
        data-testid="import-continue-button"
        :disabled="finishing"
        @click="finish(completedImport.id)"
      >
        {{ finishing ? 'Salvando...' : 'Continuar' }}
        <ChevronRight class="h-4 w-4" aria-hidden="true" />
      </Button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ChevronDown, ChevronLeft, ChevronRight, Landmark } from 'lucide-vue-next'
import ImportUploader from '@/components/import/ImportUploader.vue'
import { Button } from '@/components/ui/button'
import { completeImport } from '@/services/onboardingService'
import type { OnboardingProgress } from '@/types/onboarding'
import type { TransactionImport } from '@/types/transactionImport'

/**
 * Passo 6 do onboarding (Stories 3.6 e 5.10). Explica como baixar o extrato em OFX ou CSV nos 5 bancos suportados e
 * usa o mesmo envio da /importar. A revisão, ao concluir, faz o POST e volta para cá já no passo 7. "Continuar" aparece
 * quando já há importação concluída: cobre a falha desse POST e a importação sem nada novo (5.5, decisão 1A), que nem
 * passa pela revisão. "Importar depois" avança o passo sem importar (o backend grava `import_skipped`).
 */
const emit = defineEmits<{
  back: []
  done: [progress: OnboardingProgress]
  error: [message: string]
}>()

interface BankGuide {
  key: string
  name: string
  steps: string[]
}

// Passos curtos e genéricos, sem links externos: os menus e links dos bancos mudam sem aviso.
const BANK_GUIDES: BankGuide[] = [
  {
    key: 'nubank',
    name: 'Nubank',
    steps: [
      'Abra o app do Nubank e entre na sua conta.',
      'Toque na área da conta (ou do cartão) e procure a opção de extrato.',
      'Escolha o período que deseja e peça para exportar ou enviar o extrato.',
      'Selecione o formato OFX ou CSV e salve o arquivo.',
    ],
  },
  {
    key: 'banco-do-brasil',
    name: 'Banco do Brasil',
    steps: [
      'Acesse o app ou o internet banking do Banco do Brasil.',
      'Vá em Extrato da conta corrente.',
      'Escolha o período e procure a opção de salvar ou exportar.',
      'Selecione o formato OFX ou CSV e baixe o arquivo.',
    ],
  },
  {
    key: 'itau',
    name: 'Itaú',
    steps: [
      'Acesse o internet banking do Itaú (pelo navegador é mais fácil).',
      'Abra o extrato da sua conta.',
      'Escolha o período e clique na opção de salvar ou exportar.',
      'Selecione o formato OFX ou CSV e baixe o arquivo.',
    ],
  },
  {
    key: 'bradesco',
    name: 'Bradesco',
    steps: [
      'Acesse o internet banking do Bradesco.',
      'Abra Saldos e extratos e escolha o extrato da conta.',
      'Defina o período e procure a opção de exportar ou salvar.',
      'Selecione o formato OFX ou CSV e baixe o arquivo.',
    ],
  },
  {
    key: 'caixa',
    name: 'Caixa',
    steps: [
      'Acesse o internet banking da Caixa.',
      'Abra o extrato da sua conta.',
      'Escolha o período e procure a opção de exportar ou salvar.',
      'Selecione o formato OFX ou CSV e baixe o arquivo.',
    ],
  },
]

const finishing = ref(false)
const heading = ref<HTMLElement | null>(null)
const imports = ref<TransactionImport[]>([])

const completedImport = computed(() => imports.value.find((item) => item.status === 'COMPLETED'))

interface ApiError {
  response?: { data?: { detail?: string } }
}

async function finish(importId?: string) {
  // Duplo clique no mesmo tick: o botão ainda não foi desabilitado
  if (finishing.value) return
  finishing.value = true
  let progress: OnboardingProgress
  try {
    progress = await (importId ? completeImport(importId) : completeImport())
  } catch (err) {
    // Só libera os botões no erro: no sucesso a tela troca, e liberar antes permitiria outro POST
    finishing.value = false
    emit(
      'error',
      (err as ApiError)?.response?.data?.detail ||
        'Não foi possível salvar seu progresso. Verifique sua conexão e tente novamente.',
    )
    return
  }
  emit('done', progress)
}

onMounted(() => {
  heading.value?.focus()
})
</script>
