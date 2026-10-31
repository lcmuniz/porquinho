# Epic 2 Context: Subscription & Billing System

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Implementar o modelo de monetização do porquinho: trial gratuito de 30 dias ativado automaticamente no cadastro, seguido de assinatura mensal obrigatória (sem tier gratuito permanente). O usuário assina, cancela e reativa por conta própria, recebe lembretes antes do fim do trial e avisos sobre falhas de pagamento, e o sistema cobra a mensalidade de forma recorrente com retry automático. É a fonte de receita do produto (metas: 15% de conversão trial→pago, churn mensal < 5%, 1.000 pagantes em 12 meses) e define quem tem acesso ao app.

## Stories

- Story 2.1: Ativar automaticamente trial gratuito de 30 dias (done)
- Story 2.2: Assinar plano mensal após o trial
- Story 2.3: Enviar lembretes de expiração do trial
- Story 2.4: Cancelamento self-service da assinatura
- Story 2.5: Reativar assinatura cancelada
- Story 2.6: Processar pagamentos mensais recorrentes
- Story 2.7: Tratar falhas de pagamento com retry e notificações

## Requirements & Constraints

- O trial dá acesso completo a todas as features do MVP, sem limite de transações, contas ou categorias e sem marca d'água.
- Há um plano único, com cobrança mensal. O preço ainda não foi definido (referência: ~R$ 42/mês). Plano anual com desconto fica para depois do MVP.
- O status da assinatura é verificado em toda requisição autenticada. O acesso continua até `trial_end_date` (trial) ou `next_billing_date` (cancelada) e é revogado quando o status vira `expired` ou `suspended`.
- Os lembretes saem 7 dias e 1 dia antes do fim do trial, às 9h no fuso do usuário (UTC se o fuso for desconhecido). Ninguém recebe lembrete depois de assinar.
- Cobranças recorrentes rodam diariamente às 2h UTC. O ciclo é de 30 dias (`next_billing_date += 30 dias`).
- Retry de falha: tentativa inicial, retry após 3 dias e segundo retry após mais 7 (3 tentativas em 10 dias). Se todas falharem, o status vira `suspended` e o acesso é revogado. O usuário pode tentar de novo ou trocar o método de pagamento a qualquer momento.
- O cancelamento não gera nova cobrança e precisa ser propagado ao Stripe. A reativação cobra o primeiro mês na hora e pede um novo método se o salvo estiver inválido.
- Confiabilidade: a integração Stripe deve processar pagamentos com 99,9% de confiabilidade, e os webhooks de pagamento são processados com retry automático em caso de falha. Falhas do Stripe devem ser tratadas sem quebrar a aplicação.
- Cada transição (ativação, cancelamento, reativação, lembrete, sucesso/falha/retry de pagamento) gera um e-mail transacional e um registro no audit log.
- Os dados do usuário ficam preservados na conversão e por mais 30 dias se ele não converter.
- O app não processa dados de cartão diretamente: só guarda o `payment_method_id` tokenizado do Stripe, o que mantém o projeto fora do escopo PCI DSS.

## Technical Decisions

- Backend em Spring Boot com Stripe Java SDK. Checkout via página hospedada do Stripe. O estado da assinatura é confirmado por webhooks do Stripe, não pelo redirect.
- Organização: pacote backend `subscription/` e componentes frontend em `components/subscription/`. A API REST fica em `/api/v1/*`, e os erros seguem RFC 7807 (`ProblemDetail`).
- Dados: a tabela `subscriptions` (criada na 2.1) tem um registro por usuário. Status planejados ao longo do épico: `active`, `cancelled`, `expired`, `payment_failed`, `suspended`. A 2.4 e a 2.7 ainda precisam acrescentar `cancellation_date` e `failed_payment_attempts`. A 2.6 cria a nova tabela `billing_transactions` (subscription_id, amount, currency, status, payment_method, stripe_transaction_id, created_at).
- Os números de migration citados no épico (V5/V6) estão desatualizados, porque esses números já existem. Use a próxima versão Flyway livre.
- Convenções de tabela: colunas de auditoria (`created_at/updated_at/created_by/updated_by`), soft delete (`deleted_at/deleted_by`), FK `user_id` indexada, e todo acesso filtrado pelo usuário autenticado.
- Dinheiro: `DECIMAL(15,2)` no banco e number (não string) no JSON. Datas: `Instant` em UTC, serializadas em ISO 8601. O fuso do usuário chega pelo header `X-User-Timezone`.
- Auditoria via anotação `@Auditable` / `AuditAspect`, gravando no audit log.
- Os jobs agendados (lembretes, cobrança, retries) são diários. Eles precisam ser idempotentes e não podem reenviar e-mail nem cobrar duas vezes.

## UX & Interaction Patterns

- Badge no dashboard reflete o estado da assinatura: "Trial: X dias restantes", "Assinatura ativa", "Cancelada (ativa até [data])" e um banner "Pagamento falhou" com CTA para atualizar o método de pagamento.
- O CTA de assinatura durante o trial deve ser discreto, não agressivo. Quando o trial expira, o usuário vê uma tela de upgrade amigável.
- O cancelamento pede confirmação em modal avisando que o acesso termina no fim do período pago.
- Falhas de pagamento mostram mensagem de erro com opção de tentar de novo. Se o pagamento falhar durante o trial, o trial continua normalmente.

## Cross-Story Dependencies

- A 2.1 (concluída) é a base de todas as outras: tabela `subscriptions`, criação no cadastro (Epic 1) e verificação de acesso por requisição.
- A 2.2 traz a integração Stripe (checkout, webhook, `payment_method_id` salvo), que a 2.4, a 2.5, a 2.6 e a 2.7 reutilizam.
- A 2.6 dispara a lógica de retry da 2.7 quando um pagamento falha. As duas compartilham `billing_transactions` e o tratamento de webhooks.
- A 2.3, a 2.6 e a 2.7 dependem de uma infraestrutura de jobs agendados e de envio de e-mail transacional. Nenhum documento de planejamento define o provedor de e-mail.
- O acesso revogado (`expired`/`suspended`) afeta todos os épicos de funcionalidade, e o onboarding (Epic 3) começa logo após o cadastro, já com o trial ativo.
