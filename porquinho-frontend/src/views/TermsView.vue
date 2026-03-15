<template>
  <main class="min-h-screen bg-white px-4 py-8">
    <div class="mx-auto max-w-3xl">
      <h1 class="mb-6 text-3xl font-bold text-gray-900">Termos de Uso</h1>

      <div class="prose prose-gray max-w-none">
        <p class="text-gray-600">
          <strong>Última atualização:</strong> {{ currentDate }}
        </p>

        <section class="mt-8">
          <h2 class="text-2xl font-semibold text-gray-900">1. Aceitação dos Termos</h2>
          <p class="mt-2 text-gray-700">
            Ao utilizar o Porquinho, você concorda com estes Termos de Uso. Se você não concordar,
            não utilize nosso serviço.
          </p>
        </section>

        <section class="mt-6">
          <h2 class="text-2xl font-semibold text-gray-900">2. Descrição do Serviço</h2>
          <p class="mt-2 text-gray-700">
            O Porquinho é uma plataforma de gestão financeira pessoal que permite aos usuários
            organizar suas finanças, categorizar transações e definir metas financeiras.
          </p>
        </section>

        <section class="mt-6">
          <h2 class="text-2xl font-semibold text-gray-900">3. Conta de Usuário</h2>
          <p class="mt-2 text-gray-700">
            Você é responsável por manter a confidencialidade de suas credenciais de acesso e
            por todas as atividades realizadas em sua conta.
          </p>
        </section>

        <section class="mt-6">
          <h2 class="text-2xl font-semibold text-gray-900">4. Privacidade e Proteção de Dados</h2>
          <p class="mt-2 text-gray-700">
            Seus dados são protegidos de acordo com nossa
            <router-link to="/privacy" class="text-purple-600 hover:text-purple-700 underline">
              Política de Privacidade (LGPD)
            </router-link>.
          </p>
        </section>

        <section class="mt-6">
          <h2 class="text-2xl font-semibold text-gray-900">5. Uso Aceitável</h2>
          <p class="mt-2 text-gray-700">
            Você concorda em não usar o serviço para atividades ilegais, fraudulentas ou que
            violem os direitos de terceiros.
          </p>
        </section>

        <section class="mt-6">
          <h2 class="text-2xl font-semibold text-gray-900">6. Limitação de Responsabilidade</h2>
          <p class="mt-2 text-gray-700">
            O Porquinho não se responsabiliza por decisões financeiras tomadas com base nas
            informações fornecidas pela plataforma.
          </p>
        </section>

        <section class="mt-6">
          <h2 class="text-2xl font-semibold text-gray-900">7. Modificações dos Termos</h2>
          <p class="mt-2 text-gray-700">
            Reservamo-nos o direito de modificar estes termos a qualquer momento. Notificaremos
            os usuários sobre mudanças significativas.
          </p>
        </section>

        <section class="mt-6">
          <h2 class="text-2xl font-semibold text-gray-900">8. Contato</h2>
          <p class="mt-2 text-gray-700">
            Para questões sobre estes Termos de Uso, entre em contato através do email:
            <a href="mailto:suporte@porquinho.com" class="text-purple-600 hover:text-purple-700 underline">
              suporte@porquinho.com
            </a>
          </p>
        </section>
      </div>

      <div class="mt-8">
        <router-link
          to="/register"
          class="text-purple-600 hover:text-purple-700 underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-purple-600"
        >
          ← Voltar para cadastro
        </router-link>
      </div>
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const currentDate = computed(() => {
  return new Date().toLocaleDateString('pt-BR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  })
})
</script>
