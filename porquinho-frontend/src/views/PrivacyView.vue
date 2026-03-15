<template>
  <main class="min-h-screen bg-white px-4 py-8">
    <div class="mx-auto max-w-3xl">
      <h1 class="mb-6 text-3xl font-bold text-gray-900">
        Política de Privacidade e Proteção de Dados (LGPD)
      </h1>

      <div class="prose prose-gray max-w-none">
        <p class="text-gray-600">
          <strong>Última atualização:</strong> {{ currentDate }}
        </p>

        <section class="mt-8">
          <h2 class="text-2xl font-semibold text-gray-900">1. Introdução</h2>
          <p class="mt-2 text-gray-700">
            Esta Política de Privacidade descreve como o Porquinho coleta, usa, armazena e
            protege seus dados pessoais, em conformidade com a Lei Geral de Proteção de Dados
            (LGPD - Lei nº 13.709/2018).
          </p>
        </section>

        <section class="mt-6">
          <h2 class="text-2xl font-semibold text-gray-900">2. Dados Coletados</h2>
          <p class="mt-2 text-gray-700">Coletamos os seguintes dados pessoais:</p>
          <ul class="mt-2 ml-6 list-disc text-gray-700">
            <li>Nome e endereço de e-mail (através do Google OAuth ou cadastro direto)</li>
            <li>Informações financeiras que você voluntariamente insere na plataforma</li>
            <li>Dados de uso e navegação para melhorar nosso serviço</li>
            <li>Endereço IP e informações de dispositivo para segurança</li>
          </ul>
        </section>

        <section class="mt-6">
          <h2 class="text-2xl font-semibold text-gray-900">3. Finalidade do Tratamento</h2>
          <p class="mt-2 text-gray-700">Seus dados são utilizados para:</p>
          <ul class="mt-2 ml-6 list-disc text-gray-700">
            <li>Fornecer os serviços de gestão financeira pessoal</li>
            <li>Autenticar e gerenciar sua conta</li>
            <li>Melhorar a experiência do usuário</li>
            <li>Cumprir obrigações legais e regulatórias</li>
            <li>Garantir a segurança da plataforma</li>
          </ul>
        </section>

        <section class="mt-6">
          <h2 class="text-2xl font-semibold text-gray-900">4. Base Legal</h2>
          <p class="mt-2 text-gray-700">
            O tratamento de seus dados pessoais é baseado no seu consentimento, na execução de
            contrato e no cumprimento de obrigação legal, conforme Art. 7º da LGPD.
          </p>
        </section>

        <section class="mt-6">
          <h2 class="text-2xl font-semibold text-gray-900">5. Compartilhamento de Dados</h2>
          <p class="mt-2 text-gray-700">
            Não vendemos seus dados pessoais. Compartilhamos dados apenas quando necessário com:
          </p>
          <ul class="mt-2 ml-6 list-disc text-gray-700">
            <li>Provedores de serviços essenciais (Google OAuth, hospedagem)</li>
            <li>Autoridades governamentais quando exigido por lei</li>
          </ul>
        </section>

        <section class="mt-6">
          <h2 class="text-2xl font-semibold text-gray-900">6. Armazenamento e Segurança</h2>
          <p class="mt-2 text-gray-700">
            Seus dados são armazenados em servidores seguros com criptografia. Implementamos
            medidas técnicas e organizacionais para proteger seus dados contra acesso não
            autorizado, perda ou destruição.
          </p>
        </section>

        <section class="mt-6">
          <h2 class="text-2xl font-semibold text-gray-900">7. Seus Direitos (LGPD)</h2>
          <p class="mt-2 text-gray-700">Você tem o direito de:</p>
          <ul class="mt-2 ml-6 list-disc text-gray-700">
            <li>Confirmação da existência de tratamento dos seus dados</li>
            <li>Acesso aos dados pessoais</li>
            <li>Correção de dados incompletos, inexatos ou desatualizados</li>
            <li>Anonimização, bloqueio ou eliminação de dados desnecessários</li>
            <li>Portabilidade dos dados a outro fornecedor</li>
            <li>Eliminação dos dados tratados com seu consentimento</li>
            <li>Revogação do consentimento</li>
          </ul>
          <p class="mt-2 text-gray-700">
            Para exercer seus direitos, acesse as configurações da sua conta ou entre em contato
            através do email:
            <a href="mailto:privacidade@porquinho.com" class="text-purple-600 hover:text-purple-700 underline">
              privacidade@porquinho.com
            </a>
          </p>
        </section>

        <section class="mt-6">
          <h2 class="text-2xl font-semibold text-gray-900">8. Retenção de Dados</h2>
          <p class="mt-2 text-gray-700">
            Mantemos seus dados pelo tempo necessário para fornecer nossos serviços e cumprir
            obrigações legais. Após solicitação de exclusão, seus dados são mantidos por 30 dias
            (período de recuperação) e então permanentemente excluídos.
          </p>
        </section>

        <section class="mt-6">
          <h2 class="text-2xl font-semibold text-gray-900">9. Cookies</h2>
          <p class="mt-2 text-gray-700">
            Utilizamos cookies essenciais para autenticação e funcionamento da plataforma.
            Você pode gerenciar cookies nas configurações do seu navegador.
          </p>
        </section>

        <section class="mt-6">
          <h2 class="text-2xl font-semibold text-gray-900">10. Alterações na Política</h2>
          <p class="mt-2 text-gray-700">
            Esta política pode ser atualizada periodicamente. Notificaremos você sobre mudanças
            significativas através do email cadastrado.
          </p>
        </section>

        <section class="mt-6">
          <h2 class="text-2xl font-semibold text-gray-900">11. Encarregado de Dados (DPO)</h2>
          <p class="mt-2 text-gray-700">
            Para questões relacionadas à proteção de dados pessoais, entre em contato com nosso
            Encarregado de Dados:
          </p>
          <p class="mt-2 text-gray-700">
            <a href="mailto:dpo@porquinho.com" class="text-purple-600 hover:text-purple-700 underline">
              dpo@porquinho.com
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
