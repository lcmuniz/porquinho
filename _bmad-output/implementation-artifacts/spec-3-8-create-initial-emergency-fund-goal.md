---
title: 'Criar a meta inicial de Reserva de Emergência e concluir o onboarding (passo 8)'
type: 'feature'
created: '2026-11-02'
status: 'done'
baseline_commit: fc066f5c9dfbf1cf69081eb45e4d39eb1c66fe6c
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-3-context.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-3-3-add-credit-cards-during-setup.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-3-7-explain-dashboard-gps-3-layer-navigation.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** O onboarding para no passo 7 e nunca é marcado como concluído (`is_completed` fica falso). O usuário também sai sem nenhuma meta financeira, e não existe a tabela `goals`, que o Epic 8 vai usar.

**Approach:** Criar a tabela `goals` e uma API de metas (`/api/v1/goals`) que sirva também fora do onboarding. O passo 8, último, "Crie sua primeira meta: Reserva de Emergência", traz um formulário pré-preenchido, uma prévia do progresso e os botões para criar a meta e concluir, ou pular. Os dois caminhos marcam o onboarding como concluído e levam ao dashboard com a mensagem "Parabéns! Sua conta está configurada".

## Boundaries & Constraints

**Always:**
- Migration **V19** `goals`: `id` UUID PK, `user_id` FK indexada, `goal_name` VARCHAR(100) NOT NULL, `description` TEXT NULL (já usada pela Story 8.1), `target_amount` DECIMAL(15,2) NOT NULL CHECK > 0, `current_amount` DECIMAL(15,2) NOT NULL DEFAULT 0, `target_date` DATE NULL, `monthly_allocation` DECIMAL(15,2) NULL CHECK ≥ 0, `is_completed` BOOLEAN NOT NULL DEFAULT false, `created_at`, `updated_at`, `deleted_at`. O número V11 citado no épico já existe.
- `POST /api/v1/goals` `{name, targetAmount, targetDate, monthlyAllocation}` → 201, com `current_amount=0`. Validação em pt-BR: nome obrigatório (strip, até 100); valor-alvo obrigatório, > 0, até 2 casas, dentro de DECIMAL(15,2); data opcional e não pode estar no passado (hoje em `America/Sao_Paulo` é aceito); aporte mensal opcional, ≥ 0, até 2 casas. `GET /api/v1/goals` lista as metas ativas do usuário, da mais antiga para a mais nova. `user_id` sempre do JWT.
- `POST /api/v1/onboarding/me/finish` `{skipped}` só vale com `current_step=8`. Sem pular, exige ao menos 1 meta ativa (409 caso contrário) e grava `emergency_fund_created`; pulando, grava `emergency_fund_skipped` e não cria meta. Nos dois casos marca `is_completed=true` e registra o audit `onboarding_completed` `{skipped, goals}` (é a métrica de conclusão do NFR74). Com o onboarding já concluído é idempotente: 200, sem mudança e sem audit. Com passo < 8, 409. `current_step` continua 8.
- `/onboarding` por passo: 1 a 7 como hoje, 8 meta, concluído vai para `/dashboard`. Concluir o passo 7 leva ao passo 8. Voltar no passo 8 mostra o tour do passo 7 (última tela) sem mexer no progresso; o Próximo dele usa o POST idempotente e devolve o passo 8.
- Tela "Passo 8 de 8", título "Crie sua primeira meta: Reserva de Emergência". Campos:
  - Nome, pré-preenchido com "Reserva de Emergência".
  - Valor-alvo, sugerido R$ 25.000,00, no formato brasileiro (`lib/money.ts`).
  - Data-alvo, `<input type="date">` nativo, sugerida para 12 meses a partir de hoje.
  - Aporte mensal, calculado automaticamente como valor-alvo ÷ meses até a data-alvo (meses de calendário, mínimo 1, arredondado para cima no centavo) e editável. Depois que o usuário edita o aporte, mudar valor ou data não o sobrescreve mais.
- Prévia: barra de progresso em 0% e "Previsão de conclusão: <mês/ano>" calculada pelo aporte (valor-alvo ÷ aporte, arredondado para cima, em meses a partir de hoje). Sem aporte, a prévia mostra a data-alvo, se houver.
- Botões: "Criar meta e concluir" (POST `/goals` e depois POST `/finish` com `skipped=false`), "Pular por agora" (só `/finish` com `skipped=true`) e Voltar. Se a meta for criada e o `/finish` falhar, uma nova tentativa não cria outra meta: chama só o `/finish`. Erros 400 aparecem no campo; falhas, no toast. Mesmo padrão de `finish()` dos passos anteriores.
- Ao concluir, o usuário vai para `/dashboard?onboarding=done`, e o dashboard mostra a mensagem "Parabéns! Sua conta está configurada" (alerta acessível, sem biblioteca de confete) e limpa o parâmetro da URL.
- LGPD: `goals` no `purgeUser` (antes do usuário) e no export (`assertExportsEveryColumn`).
- Alvo de toque de no mínimo 44px, campos com `label` e erros ligados por `aria-describedby`.

**Never:**
- Página de metas, edição, aportes, `goal_allocations` ou várias metas no onboarding (Epic 8).
- Confete ou biblioteca nova; editar migrations V1 a V18.

## I/O & Edge-Case Matrix

| Cenário | Estado | Resultado | Erro |
|---|---|---|---|
| Abrir `/onboarding` | passo 8 | "Passo 8 de 8", formulário pré-preenchido, prévia 0% | — |
| Criar meta e concluir | dados válidos | meta criada, `emergency_fund_created`, `is_completed=true`, audit, `/dashboard` com a mensagem | — |
| Valor ≤ 0, data no passado, aporte negativo | — | 400 no campo | 400 |
| Pular por agora | passo 8 | nenhuma meta, `emergency_fund_skipped`, `is_completed=true`, `/dashboard` com a mensagem | — |
| `/finish` sem pular e sem meta | 0 metas | 409 | 409 |
| Meta criada, `/finish` falha, tenta de novo | — | só o `/finish` é chamado; uma meta | — |
| Repetir `/finish` | concluído | 200, sem mudança e sem audit | — |
| Passo < 8 | — | 409 | 409 |
| Abrir `/onboarding` | concluído | `/dashboard` | — |
| Meta de outro usuário | — | nunca listada | — |

</frozen-after-approval>

## Code Map

- Story 3.3 (`git show f6c3aa1`) -- modelo de ponta a ponta para tabela, entidade, repositório, serviço, controller, DTO com validação de valores (`CreateCreditCardRequest`), LGPD e testes.
- `porquinho-backend/.../service/OnboardingService.java` -- `completeAccounts` (exige ao menos 1 item sem pular) e `completeDashboardTour` como base de `finish`; `OnboardingProgress.setCompleted`.
- `porquinho-backend/.../service/AccountDeletionService.java` e `DataExportService.java` -- acrescentar `goals`.
- `porquinho-frontend/src/components/onboarding/CardsStep.vue` -- formulário com `lib/money.ts`, erros por campo e `finish()`. Criar `EmergencyFundStep.vue`.
- `porquinho-frontend/src/views/OnboardingView.vue` -- passo 8, Voltar para a última tela do tour, redirect com `?onboarding=done`. `DashboardView.vue` -- mensagem de sucesso.
- `services/onboardingService.ts` -- `finishOnboarding(skipped)`; criar `services/goalService.ts`.
- Testes modelo: `CreditCardServiceTest`, `CreditCardControllerTest`, `CreditCardOnboardingIntegrationTest`, `CardsStep.spec.ts`, `OnboardingView.spec.ts`, `AccountDeletionServiceTest`, `DataExportServiceTest`.

## Tasks & Acceptance

**Execution:**
- [x] `V19__create_goals_table.sql` -- tabela, CHECKs, índice e comentários.
- [x] Backend metas -- entidade, repositório, serviço, controller e DTOs (listar, criar).
- [x] `OnboardingService.java`, `OnboardingController.java` -- `finish` e `POST /me/finish`.
- [x] `AccountDeletionService.java`, `DataExportService.java` -- purge e export.
- [x] Frontend -- `goalService.ts`, `finishOnboarding`, `EmergencyFundStep.vue`, passo 8 no `OnboardingView.vue`, tour → passo 8, mensagem no `DashboardView.vue`.
- [x] Testes -- matriz no backend (serviço, controller e integração com PostgreSQL) e Vitest do passo 8 (cálculo do aporte e da previsão incluídos), do serviço, da view e do dashboard.

**Acceptance Criteria:**
- Dado um usuário que concluiu o passo 7, quando ele abre `/onboarding`, então vê "Passo 8 de 8", "Crie sua primeira meta: Reserva de Emergência", os campos pré-preenchidos (R$ 25.000,00, data em 12 meses, aporte calculado) e a prévia em 0%.
- Dado que ele clica em "Criar meta e concluir", então existe a meta com `current_amount=0`, o progresso tem `emergency_fund_created` e `is_completed=true`, existe o audit `onboarding_completed`, e ele vê no dashboard "Parabéns! Sua conta está configurada"; abrir `/onboarding` depois leva ao dashboard.

## Spec Change Log

## Review Triage Log

Revisão em 3 camadas (Blind Hunter, Edge Case Hunter, Verification Gap) — 2026-11-02. Parte da saída do Edge Case Hunter chegou comprimida e o hash expirou; os achados legíveis coincidem com os do Blind Hunter.

| # | Origem | Achado | Veredito | Evidência | Rota |
|---|---|---|---|---|---|
| 1 | Blind, Edge | Meta duplicada ao voltar ou recarregar depois de criar a meta e o `/finish` falhar | medium | `goalCreated` só em memória; `listGoals` não é chamado ao montar | patch |
| 2 | Blind, Edge, VG | "Pular por agora" depois de criar a meta grava `emergency_fund_skipped` | medium | Distorce a métrica do NFR74 | patch (front esconde; back grava `created` com meta ativa) |
| 3 | Blind | Campos travados sem explicação depois de criar a meta | low | Nenhuma mensagem visível | patch |
| 4 | Blind, Edge | "Hoje" do front (aparelho) diferente do back (São Paulo) | low | 400 inesperado perto da meia-noite fora do fuso | patch |
| 5 | Blind, Edge | Nome só com U+200B/U+FEFF aceito | low | Mesmo caso corrigido na 3.5 | patch |
| 6 | Blind | Mensagem de parabéns com `role="alert"` | low | Sucesso deve ser `status` | patch |
| 7 | Blind, VG | `projectedCompletion` testada e não usada | low | Lógica duplicada | patch |
| 8 | Blind | `LAST_TOUR_SCREEN = 4` copiado à mão | low | Diverge em silêncio se o tour mudar | patch |
| 9 | VG | Previsão acima de 1200 meses sem teste | medium | Remover o guard quebraria o render sem falha de teste | patch |
| 10 | VG | Campos desabilitados depois de criar a meta sem teste | medium | Teste da nova tentativa não olha `disabled` | patch |
| 11 | Blind | `GoalService.create` lança 500 com 3 casas sem validação do controller | low | `RoundingMode.UNNECESSARY`; o Epic 8 vai reutilizar | patch |
| 12 | Blind, Edge | Sem limite de metas nem paginação | low | NFR30 pede metas ilimitadas | reject |
| 13 | Blind | Criar meta sem audit | low | Mesmo veredito de contas, cartões e categorias | reject |
| 14 | Blind | `/finish` aceita qualquer meta ativa | low | Intenção: "exige ao menos 1 meta ativa" | reject |
| 15 | Blind | Foco não vai ao primeiro campo inválido | low | Mesmo padrão dos passos 2 e 3; erros ligados por `aria-describedby` | reject |
| 16 | Blind | Aporte maior que o valor-alvo permitido | low | Previsão de 1 mês é correta; nada a impedir | reject |
| 17 | Blind | `@NotPastInSaoPaulo` sem `Clock` injetável | low | Teste da virada do dia é marginal | reject |

## Verification

**Commands:**
- `cd porquinho-backend && ./mvnw test` -- 0 falhas. Antes, parar o backend de dev (`lsof -ti :8080 | xargs kill` e `pgrep -f PorquinhoBackendApplication | xargs kill`) e conferir que a porta ficou livre, para o devtools não aplicar uma V19 intermediária no Supabase.
- `cd porquinho-frontend && npx vitest run && npx vue-tsc --noEmit -p tsconfig.app.json` -- 0 falhas

**Manual checks:**
- Com o usuário de teste (hoje no passo 8): ajustar valor e data, ver o aporte e a previsão mudarem, criar a meta, ver a mensagem no dashboard e a linha em `goals` no Supabase de dev.
