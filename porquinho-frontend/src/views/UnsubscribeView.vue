<template>
  <main class="flex min-h-screen items-center justify-center bg-white px-4">
    <section class="w-full max-w-md">
      <Card>
        <CardHeader>
          <CardTitle class="text-center text-2xl font-bold text-gray-900">
            Alerta de orçamento por e-mail
          </CardTitle>
        </CardHeader>
        <CardContent class="space-y-4 text-center" aria-live="polite">
          <p v-if="status === 'done'" class="text-gray-900 font-medium">
            Você não receberá mais alertas de orçamento por e-mail.
          </p>
          <p v-else-if="status === 'invalid'" class="text-red-600">
            Este link de descadastro é inválido. Para desligar o alerta, desmarque "Receber alerta de
            orçamento por e-mail" em Configurações da conta.
          </p>
          <template v-else>
            <p class="text-gray-600">
              Quer parar de receber os alertas de orçamento por e-mail? Os outros e-mails da sua conta
              (cobrança, trial e avisos sobre seus dados) continuam.
            </p>
            <p v-if="status === 'error'" class="text-red-600">
              Não foi possível concluir agora. Tente de novo em instantes.
            </p>
            <Button
              class="w-full min-h-[44px] bg-purple-600 hover:bg-purple-700 text-white"
              :disabled="status === 'sending'"
              @click="confirm"
            >
              {{ status === 'sending' ? 'Descadastrando...' : 'Não quero mais receber' }}
            </Button>
          </template>
        </CardContent>
      </Card>
    </section>
  </main>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { unsubscribeBudgetAlert } from '@/services/userPreferencesService'
import { apiErrorStatus } from '@/lib/apiError'

// Botão em vez de descadastrar ao abrir: antivírus de e-mail abrem os links sozinhos
const token = String(useRoute().query.token ?? '')
const status = ref<'ready' | 'sending' | 'done' | 'invalid' | 'error'>(token ? 'ready' : 'invalid')

async function confirm() {
  status.value = 'sending'
  try {
    await unsubscribeBudgetAlert(token)
    status.value = 'done'
  } catch (err) {
    status.value = apiErrorStatus(err) === 400 ? 'invalid' : 'error'
  }
}
</script>
