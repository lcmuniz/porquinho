
## Deferred from: code review of 1-7-request-permanent-account-deletion-lgpd-compliance (2026-05-15)

- @Transactional(propagation=NEVER) em requestDeletion — defesa futura caso codigo externo tente envolver a chamada em outra transacao. Atualmente o controller nao envolve, entao nao ha risco real.
- JWT blacklist TTL pode ser 0 ou negativo se token esta no ultimo segundo de validade — janela de exposicao <1min, atacante precisaria de timing perfeito.
- DataExport.findAllByUserId sem stream/paginacao — risco teorico de OOM se retencao for aumentada para anos. Atualmente 24h de retencao mantem lista pequena.
- Rate limit so por userId permite que um atacante com credencials valida bloqueie o usuario legitimo por 24h. Mitigacao real exige rate limit adicional por IP — fora do escopo do MVP.

## Deferred from: code review of 2-1-automatically-activate-30-day-free-trial (2026-10-31)

- source_spec: `_bmad-output/implementation-artifacts/2-1-automatically-activate-30-day-free-trial.md`
  summary: Testes de contexto Spring quebrados no H2 — V6/V7/V8 usam `PRIMARY KEY DEFAULT`, `TIMESTAMPTZ` e índice parcial; `data_exports` TIMESTAMPTZ vs `LocalDateTime`; perfil test sem `JavaMailSender`.
  evidence: 19 erros já no baseline 7c989a1 (MigrationTest, UserRepositoryTest, SecurityConfigTest, PorquinhoBackendApplicationTests). Bloqueia testes de integração da 2.1 (filtro 402 na cadeia real, `insertWithId` com google_id, backfill V9). Corrigir sem alterar checksum de migrations aplicadas (ex.: migrations H2-específicas via `flyway.locations` no perfil test, ou Testcontainers PostgreSQL).
- source_spec: `_bmad-output/implementation-artifacts/2-1-automatically-activate-30-day-free-trial.md`
  summary: Frontend não trata 402 (interceptor em `services/api.ts` só trata 401) nem oferece CTA no badge "Trial expirado".
  evidence: Natural na Story 2.2 (tela de assinatura); hoje nenhuma rota retorna 402.
- source_spec: `_bmad-output/implementation-artifacts/2-1-automatically-activate-30-day-free-trial.md`
  summary: Login Google de usuário que já existe por email com id diferente do sub Google mantém o id do usuário email.
  evidence: maybe-false — se o Supabase vincula identidades do mesmo email no mesmo auth user, o sub é igual e não há problema. Verificar configuração de identity linking no projeto Supabase.
- source_spec: `_bmad-output/implementation-artifacts/2-1-automatically-activate-30-day-free-trial.md`
  summary: Usuário soft-deletado (aguardando purge) que re-registra com o mesmo sub recebe 500 (violação de PK em `insertWithId`) em vez de erro claro.
  evidence: Pré-existente no caminho email; `findById` filtra `deleted_at` via `@Where`. Retornar 409/410 usando `findByIdIncludingDeleted`.
- source_spec: `_bmad-output/implementation-artifacts/2-1-automatically-activate-30-day-free-trial.md`
  summary: [RESOLVIDO 2026-10-31] `/login` e `/password-reset/completed` ainda usam `@AuthenticationPrincipal String`, que resolve null com `JwtAuthenticationToken`.
  evidence: Em produção o audit `password_reset_completed` é silenciosamente pulado. Trocar por `Authentication.getName()` como feito nos endpoints de registro.

## Deferred from: code review of 2-2-subscribe-to-monthly-plan-after-trial (2026-10-31)

- source_spec: `_bmad-output/implementation-artifacts/2-2-subscribe-to-monthly-plan-after-trial.md`
  summary: `checkout.session.completed` reentregue após cancelamento/expiração pode reativar a assinatura.
  evidence: maybe-false hoje (CANCELLED/EXPIRED inalcançáveis); tratar na 2.4 com dedupe por `event.id` ou checagem de status.
- source_spec: `_bmad-output/implementation-artifacts/2-2-subscribe-to-monthly-plan-after-trial.md`
  summary: Reaproveitar `stripe_customer_id` (`setCustomer`) em vez de `customer_email` ao reassinar.
  evidence: Cada reassinatura cria Customer novo; relevante na 2.5 (reativação).
- source_spec: `_bmad-output/implementation-artifacts/2-2-subscribe-to-monthly-plan-after-trial.md`
  summary: Badge e CTA "Assinar" para MONTHLY CANCELLED/EXPIRED.
  evidence: Estados só passam a existir nas Stories 2.4–2.7.
- source_spec: `_bmad-output/implementation-artifacts/2-2-subscribe-to-monthly-plan-after-trial.md`
  summary: Teste concorrente (Testcontainers) do lock pessimista em `findByUserIdForUpdate`.
  evidence: Sem teste, remover `@Lock` passa; consequência limitada a audit/e-mail duplicados.

## Deferred from: code review of 2-3-send-trial-expiration-reminders (2026-10-31)

- source_spec: `_bmad-output/implementation-artifacts/2-3-send-trial-expiration-reminders.md`
  summary: Job de lembretes sem execução de recuperação no startup; backend fora às 9h pode perder o lembrete de 1 dia.
  evidence: Só cron 09:00 SP; query exige trial_end > now. Opção: rodar também em ApplicationReadyEvent (idempotente).
- source_spec: `_bmad-output/implementation-artifacts/2-3-send-trial-expiration-reminders.md`
  summary: Nenhum job vira status TRIAL→EXPIRED após o fim do trial.
  evidence: Funcionalmente ok (hasAccess calcula na leitura); relevante para relatórios/2.6–2.7.
- source_spec: `_bmad-output/implementation-artifacts/2-3-send-trial-expiration-reminders.md`
  summary: EmailService loga e-mail completo do destinatário (dado pessoal, LGPD).
  evidence: Padrão em todos os métodos de envio; trocar por userId ou e-mail mascarado.

## Deferred from: code review of 2-4-self-service-subscription-cancellation (2026-10-31)

- source_spec: `_bmad-output/implementation-artifacts/2-4-self-service-subscription-cancellation.md`
  summary: Tratar `customer.subscription.updated` (desfazer cancelamento fora do app; atualizar `next_billing_date` nas renovações).
  evidence: Hoje só `checkout.session.completed` e `customer.subscription.deleted`; natural na 2.5 (reativação) e 2.6 (recorrência).
- source_spec: `_bmad-output/implementation-artifacts/2-4-self-service-subscription-cancellation.md`
  summary: Reconciliar quando o Stripe aceita `cancel_at_period_end` mas o commit local falha (idempotency key / desfazer).
  evidence: Janela pequena; hoje a linha fica ACTIVE e o Stripe para de cobrar.
- source_spec: `_bmad-output/implementation-artifacts/2-4-self-service-subscription-cancellation.md`
  summary: IP do audit atrás de proxy (usar X-Forwarded-For como AuthController) em SubscriptionController e DataExportController.
  evidence: `getRemoteAddr` grava o IP do proxy em produção.
- source_spec: `_bmad-output/implementation-artifacts/2-4-self-service-subscription-cancellation.md`
  summary: Endpoint de webhook de produção no painel Stripe deve assinar `checkout.session.completed` e `customer.subscription.deleted`.
  evidence: Sem isso nenhuma linha vira EXPIRED (acesso termina pelo hasAccess mesmo assim).
- source_spec: `_bmad-output/implementation-artifacts/2-4-self-service-subscription-cancellation.md`
  summary: ACTIVE→EXPIRED por cobranças recusadas sem e-mail/aviso ao usuário.
  evidence: Escopo da Story 2.7.

- source_spec: `_bmad-output/implementation-artifacts/2-5-reactivate-cancelled-subscription.md`
  summary: Checkout antigo concluído enquanto a linha está MONTHLY/CANCELLED com acesso cria segunda assinatura paga sobreposta à antiga (cancelamento agendado).
  evidence: `activateMonthly` só cancela a assinatura duplicada quando a linha está ACTIVE; `createCheckout` bloqueia novos checkouts nesse estado, mas uma sessão aberta antes (no trial) ainda pode ser concluída. Pré-existente desde a 2.4.

- source_spec: `_bmad-output/implementation-artifacts/2-6-process-recurring-monthly-payments.md`
  summary: Em produção, o webhook endpoint do Stripe (test e live) precisa assinar `invoice.paid`, além de `checkout.session.completed` e `customer.subscription.deleted`.
  evidence: Só o comando `stripe listen` do `.env.example` foi atualizado; sem o evento no endpoint, nenhum pagamento recorrente é registrado.

- source_spec: `_bmad-output/implementation-artifacts/2-6-process-recurring-monthly-payments.md`
  summary: Decidir a retenção de `billing_transactions` no purge da conta. Hoje a tabela é apagada; a legislação fiscal pode exigir guardar registros de cobrança por cerca de 5 anos (LGPD art. 16, I), por exemplo anonimizando `user_id`.
  evidence: O purge (Story 1.7 + 2.6) faz hard delete da tabela, e `user_id NOT NULL` impede anonimizar sem uma migration. É uma questão jurídica e de produto.

- source_spec: `_bmad-output/implementation-artifacts/2-7-handle-payment-failures-with-retry-notifications.md`
  summary: Deploy: configurar no Stripe Dashboard (test e live) Billing → Revenue recovery → Retries com agendamento customizado de 2 novas tentativas (3 e 10 dias depois da falha) e, após a última falha, **cancelar a assinatura**; o webhook endpoint precisa assinar também `invoice.payment_failed`.
  evidence: Sem o cancelamento ao final, o Stripe deixa a assinatura `past_due`/`unpaid`, nenhum `customer.subscription.deleted` chega e a linha fica em PAYMENT_FAILED com acesso indefinidamente (decisão 2A). Só o `.env.example` documenta isso hoje.


- source_spec: `_bmad-output/implementation-artifacts/spec-retro-epic2-f1-stop-billing-on-account-deletion.md`
  summary: Teste de integração com transação Spring real para os fluxos com `afterCommit` (exclusão de conta, cancel, reactivate, activate): chamar o Stripe só depois do commit externo e nunca em rollback.
  evidence: Os testes simulam a sincronização à mão (`runAfterCommit`) ou usam mocks; uma mudança de propagação (`REQUIRES_NEW`) passaria despercebida. Achado do Verification Gap (medium, unverified em produção).

- source_spec: `_bmad-output/planning-artifacts/prd/saas-web-app-specific-requirements.md` (Experiência de conversão)
  summary: Implementar a retenção de dados de quem não converte: o PRD diz que os dados ficam guardados por mais 30 dias depois que o trial (ou a assinatura) acaba sem pagamento. Falta definir o que acontece no fim desse prazo (purge como na Story 1.7? aviso por e-mail antes?) e criar o job.
  evidence: Reconciliação do Epic 2 (2026-11-04, decisão do Luiz: fica como trabalho a fazer). Hoje nada é apagado quando o trial vence: os dados ficam sem prazo, o usuário só toma 402 e ainda pode exportar ou excluir a conta. Considerar junto com a retenção de `billing_transactions` (item acima).

- source_spec: `_bmad-output/implementation-artifacts/spec-3-1-display-welcome-envelope-budget-concept-tutorial.md`
  summary: Teste concorrente (duas threads, PostgreSQL real) provando que o lock de `findByUserIdForUpdate` em `OnboardingService.applyTutorial` grava um único audit `onboarding_tutorial_completed`.
  evidence: Revisão da 3.1 (Verification Gap); hoje a idempotência só é testada em sequência e não há precedente de teste concorrente no repositório.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-4-suggest-initial-spending-categories.md`
  summary: Ordem determinística de `GET /api/v1/categories` (pai antes das filhas, ordem do catálogo), por exemplo com uma coluna `sort_order`, antes de a Story 3.5 listar as categorias.
  evidence: Revisão da 3.4: `created_at` vem de chamadas separadas e o desempate é por UUID aleatório.
  status: resolvido na Story 3.5 (ordem por nome e id; pai = grupo ativo do mesmo usuário, profundidade 1, grupo com filhas não pode ser excluído).

- source_spec: `_bmad-output/implementation-artifacts/spec-3-4-suggest-initial-spending-categories.md`
  summary: Integridade da hierarquia de categorias ao editar (Story 3.5/Epic 6): pai do mesmo usuário, profundidade máxima e o que acontece com as filhas quando o pai é excluído (soft delete).
  evidence: Revisão da 3.4: o schema só impede auto-referência; hoje só o catálogo cria pais.
  status: resolvido na Story 3.5 (ordem por nome e id; pai = grupo ativo do mesmo usuário, profundidade 1, grupo com filhas não pode ser excluído).

- source_spec: `_bmad-output/implementation-artifacts/spec-3-4-suggest-initial-spending-categories.md`
  summary: Teste HTTP do handler de `BadRequestException` (400 com `detail`) quando um endpoint passar a depender só da validação no serviço.
  evidence: Revisão da 3.4 (Verification Gap): hoje os 400 param na validação do DTO.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-6-guided-first-transaction-import.md`
  summary: Epic 5 — ligar o upload (Stories 5.1–5.3) ao passo 6 do onboarding (`ImportStep.vue`): área de arrastar e soltar, barra de progresso, prévia classificada e gravação de `first_import_completed` em `POST /api/v1/onboarding/me/import` (corpo opcional).
  evidence: Decisão A da 3.6: o passo 6 foi entregue sem upload porque a importação ainda não existia.
  status: incluído no Epic 5 (Story 5.1, revisão de 2026-11-04).

- source_spec: `_bmad-output/implementation-artifacts/spec-4-3-edit-account-and-card-information.md`
  summary: Story 4.7 — ordenação determinística do `account_balance_history` para eventos no mesmo instante (coluna sequencial ou relógio injetado) e controle de concorrência na edição de contas/cartões (`@Version` com 409). — ordenação resolvida na 4.7 (snapshot_date, created_at, id); `@Version` nas contas resolvido na retro do Epic 4 (X1, V24, 409). Cartões continuam sem controle de concorrência (a última gravação vence).
  evidence: Revisão da 4.3: o desempate é por UUID; dois PUT simultâneos fazem a última gravação vencer.

- source_spec: `_bmad-output/implementation-artifacts/spec-4-5-record-internal-transfers-between-own-accounts.md`
  summary: Injetar `Clock` em `NotPastInSaoPaulo` e `NotFutureInSaoPaulo` e testar a virada do dia em São Paulo × UTC; corrigir `formatDateBR` (`lib/date.ts`), que mostra datas AAAA-MM-DD um dia antes no fuso de São Paulo.
  evidence: Revisão da 4.5 (Verification Gap) e relato do implementador da 4.5.

## Story 4.6 → Epic 5

- Reconciliação não cria transação de ajuste (decisão A, 2026-11-03). Quando a tabela `transactions` existir, decidir se cada evento de `reconciliation_history` com diferença ≠ 0 vira uma transação com a categoria "Ajuste de Reconciliação". — decidido na revisão do Epic 5 (2026-11-04, D9): continua sem transação de ajuste.

- source_spec: `_bmad-output/implementation-artifacts/spec-5-1-upload-ofx-files-from-5-brazilian-banks.md`
  summary: Importações criadas pela Story 5.1 ficam em `PROCESSING` sem arquivo guardado (decisão 1A); a Story 5.3 precisa dar destino a essas linhas (marcar `FAILED` com mensagem pedindo novo envio, ou permitir descartar).
  evidence: Sem o arquivo, a 5.3 não tem o que processar nas linhas antigas, e a lista em `/importar` mostraria "Recebido — a leitura das transações chega na próxima etapa" para sempre. O check manual do nginx (`client_max_body_size 11m`, envio de 2MB pelo proxy) também continua sem teste automatizado.

- source_spec: `_bmad-output/implementation-artifacts/spec-5-4-parse-transactions-with-robust-error-handling.md`
  summary: A varredura de órfãs marca FAILED toda importação PROCESSING criada há mais de 15 min, sem heartbeat; quando a 5.8 (IA em lotes) alongar o processamento, importações grandes ainda rodando serão derrubadas.
  evidence: `failProcessingCreatedBefore` usa `createdAt`; hoje a leitura de 1 ano leva < 2 s, então só vira problema com a IA (5.8). Trocar por `updated_at` atualizado a cada lote.
- source_spec: `_bmad-output/implementation-artifacts/spec-5-5-detect-and-flag-duplicate-transactions.md`
  summary: O teste de importações simultâneas (Story 5.5) não força a sobreposição das duas transações; uma regressão no advisory lock passaria verde.
  evidence: `twoImportsOfTheSameFileAtOnceSaveItOnlyOnce` não sincroniza as threads; fechar exige um repositório espião que segure a 1ª importação entre a consulta e o saveAll.
- source_spec: `_bmad-output/implementation-artifacts/spec-5-5-detect-and-flag-duplicate-transactions.md`
  summary: O passo 1 da detecção de duplicatas (mesmo external_id) não confere valor nem tipo; um banco que reutilize FITID descartaria lançamento real (medium, não verificado).
  evidence: Resolver com um extrato real que tenha FITID repetido para lançamentos distintos na mesma janela de datas.
- source_spec: `_bmad-output/implementation-artifacts/spec-5-5-detect-and-flag-duplicate-transactions.md`
  summary: Ao implementar o descarte de importação (Story 5.9), apagar ou excluir logicamente as transações dela; senão continuam como candidatas de duplicata (medium, não verificado).
  evidence: `findActiveBy*Between` filtra só `deleted_at IS NULL`, sem olhar o status da importação de origem.
- source_spec: `_bmad-output/implementation-artifacts/spec-5-5-detect-and-flag-duplicate-transactions.md`
  summary: Na Story 5.13 (transação manual), tomar o mesmo advisory lock por destino da importação, para a detecção de duplicatas ver a gravação concorrente.
  evidence: `lockTarget` só é chamado pela importação.
- source_spec: `_bmad-output/implementation-artifacts/spec-5-7-detect-installment-purchases-and-split-across-months.md`
  summary: O descarte de importação da 5.9 precisa tratar as parcelas confirmadas por outra importação (decisão 2A da 5.7), sem apagar a parcela real nem deixar a confirmação de um arquivo descartado.
  evidence: A futura confirmada fica com o import_id da importação que a criou; um descarte por import_id apagaria a parcela trazida pelo arquivo seguinte, e descartar a importação nova não volta a linha a futura.
- source_spec: `_bmad-output/implementation-artifacts/spec-5-7-detect-installment-purchases-and-split-across-months.md`
  summary: Conferir com extratos reais se algum banco usa marcadores "PARCELA 03 DE 10" / "03 DE 10" ou "3/10" de um dígito no fim da descrição do cartão.
  evidence: Não verificado (sem arquivo real, D6). Se usarem, as compras entram como lançamento comum, sem futuras (medium). Resolve com um export real de fatura de cada banco.
- source_spec: `_bmad-output/implementation-artifacts/spec-5-7-detect-installment-purchases-and-split-across-months.md`
  summary: Conferir com extratos reais se a data de cada parcela no arquivo é a data da cobrança ou a da compra original.
  evidence: Não verificado (D6). Se for a da compra, a confirmação leva a parcela para o mês errado e as futuras saem com datas erradas (medium). Resolve com uma fatura real com parcela de cada banco.
- source_spec: `_bmad-output/implementation-artifacts/spec-5-7-detect-installment-purchases-and-split-across-months.md`
  summary: Se um banco repetir a data da compra em todas as parcelas, a janela de 20 dias (4A) abre um grupo novo por mês, com futuras repetidas; no CSV sem id, a 5.5 descarta a parcela como duplicata.
  evidence: Medium ou high, não verificado (D6). Atualiza o impacto da entrada anterior sobre a data da parcela. Resolve com uma fatura real com parcela de cada banco.
- source_spec: `_bmad-output/implementation-artifacts/spec-5-7-detect-installment-purchases-and-split-across-months.md`
  summary: `InstallmentPlanner.clip()` não tem teste; a descrição de 501 caracteres estouraria a coluna e a importação cairia em FAILED.
  evidence: Só ocorre com descrição de cerca de 500 caracteres e marcador de um dígito; improvável nos formatos atuais.

- source_spec: `_bmad-output/implementation-artifacts/spec-5-7-detect-installment-purchases-and-split-across-months.md`
  summary: A ação "remover parcelas futuras desta compra" da 5.12 precisa durar; hoje uma parcela anterior importada depois recria os números removidos.
  evidence: `InstallmentPlanner.plan` só recebe parcelas ativas e cria todo número ausente depois da linha inserida. A remoção ainda não existe no código, então o caso só aparece com a 5.12.

- source_spec: `_bmad-output/implementation-artifacts/spec-5-8-auto-classify-transactions-using-ai.md`
  summary: Medir o NFR7 (lote de 100 em menos de 3 s) com uma `ANTHROPIC_API_KEY` real (decisão 2A).
  evidence: A 5.8 foi testada só contra servidor HTTP local; nenhuma chamada real ao `claude-haiku-5-5` foi feita. Sem chave, tempo de resposta, qualidade da classificação e aceitação do schema pela API seguem sem medição.

- source_spec: `_bmad-output/implementation-artifacts/spec-5-8-auto-classify-transactions-using-ai.md`
  summary: A política de privacidade cita a Anthropic, mas não fala em transferência internacional nem em retenção, e a data de "Última atualização" é sempre o dia de hoje.
  evidence: `PrivacyView.vue` usa `new Date()` em `currentDate`, e a seção 10 promete aviso por e-mail em mudanças significativas, que não existe. Precisa de decisão jurídica antes de ir a produção.
- source_spec: `_bmad-output/implementation-artifacts/spec-5-8-auto-classify-transactions-using-ai.md`
  summary: A 5.9 precisa tratar `classification_confidence` nula de linhas anteriores à 5.8 como "Não classificado".
  evidence: linhas e futuras gravadas antes da 5.8 têm confiança nula e escapam do filtro `< 80`; só `CARD_PAYMENT` deveria ficar nulo.
- source_spec: `_bmad-output/implementation-artifacts/spec-5-8-auto-classify-transactions-using-ai.md`
  summary: Conferir na primeira execução com chave real que `anthropic.api-key` chega ao cliente.
  evidence: nenhum teste exercita o construtor de produção; um erro de digitação deixa tudo "Não classificado", só com um WARN na subida.
- source_spec: `_bmad-output/implementation-artifacts/spec-5-8-auto-classify-transactions-using-ai.md`
  summary: NFR7 medido com chave real depois da revisão da 2A; falta só observar a primeira chamada depois de subir o backend e a precisão em extratos reais.
  evidence: 4 chamadas simultâneas de 25 levaram 3,3 s na primeira rodada (fora da meta) e 2,0–2,2 s nas seguintes. A amostra tinha descrições fictícias e fáceis, então não mede a meta de 85%.
- source_spec: `_bmad-output/implementation-artifacts/spec-5-9-present-classified-transactions-for-user-review.md`
  summary: Resolvido na 5.9 o descarte de importação (itens da 5.5 e da 5.7 acima): soft delete das linhas, transferência e reversão das parcelas confirmadas (decisão 3A).
  evidence: `TransactionImportReviewService.discard`. Confirmações gravadas antes da V29 não têm `confirmed_by_import_id` e somem com a importação que as criou.
- source_spec: `_bmad-output/implementation-artifacts/spec-5-9-present-classified-transactions-for-user-review.md`
  summary: A parcela transferida no descarte (3A) não atualiza os contadores da importação que a confirmou, que segue contando a linha como duplicata (5.7 – 2A).
  evidence: O rótulo da `/importar` pode ficar uma linha abaixo do que a revisão mostra. Resolver na 5.10, se o resumo final usar `imported_transactions`.
  status: Continua aberto. O rótulo "Concluída" da 5.10 usa `imported_transactions`, e o ajuste do contador no descarte ficou fora da spec.
- source_spec: `_bmad-output/implementation-artifacts/spec-5-9-present-classified-transactions-for-user-review.md`
  summary: A 5.10 precisa tomar o advisory lock do destino ao concluir a importação; o descarte lê o status da importação que confirmou para decidir entre `CONFIRMED` e manter o status da linha.
  evidence: `TransactionRepository.transferConfirmedInstallments`. Sem o lock, uma conclusão simultânea deixaria a linha transferida sem `CONFIRMED`.
  resolution: Feito na 5.10. `TransactionImportReviewService.complete` toma o lock antes do update com guarda.
- source_spec: `_bmad-output/implementation-artifacts/spec-5-9-present-classified-transactions-for-user-review.md`
  summary: Descartar a importação que criou as futuras de uma compra deixa sem futuras a parcela mais antiga trazida depois por outra importação do mesmo grupo (ela não criou futuras porque já existiam).
  evidence: As futuras voltam quando a parcela seguinte for importada, pois `InstallmentPlanner` cria as que faltam no grupo.
- source_spec: `_bmad-output/implementation-artifacts/spec-5-9-present-classified-transactions-for-user-review.md`
  summary: Na 5.10, tratar a parcela transferida no descarte para importação `COMPLETED`: ela fica `CONFIRMED` com `affects_balance = false` e nunca entra no saldo.
  evidence: `transferConfirmedInstallments` só muda `review_status`. Uma importação concluída por `markCompletedWithNothingNew` não passa pela conclusão da 5.10, e a 5.9 não pode mexer em saldo.
  resolution: Fechado na 5.10 sem mudança de código. Parcelas futuras só existem em cartão, e a D3 não mexe em saldo de cartão, então `affects_balance = false` é o valor certo para essa linha.
- source_spec: `_bmad-output/implementation-artifacts/spec-5-9-present-classified-transactions-for-user-review.md`
  summary: A decisão 5A só avisa; os lançamentos ignorados como duplicatas de uma importação descartada dependem do reenvio pelo usuário.
  evidence: Nada liga a entrada ignorada à linha original. `duplicatesFound` também conta parcelas confirmadas (5.7 – 2A), então o aviso pode aparecer sem perda real.
- source_spec: `_bmad-output/implementation-artifacts/spec-5-10-review-and-adjust-ai-classifications.md`
  summary: Uma transferência registrada depois do upload e antes de concluir a importação entra em dobro no saldo.
  evidence: A D4 só procura transferências no processamento do arquivo, e `REFERENCE_EVENTS` exclui `transfer`, então `sumBalanceEffect` soma de novo a linha do extrato. Luiz adiou em 2026-11-06; a reconciliação corrige o saldo. Possível correção: repetir a checagem da D4 ao concluir.
