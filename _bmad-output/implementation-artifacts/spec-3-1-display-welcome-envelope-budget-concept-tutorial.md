---
title: 'Boas-vindas e tutorial do orçamento por envelope (passo 1 do onboarding)'
type: 'feature'
created: '2026-11-01'
status: 'done'
baseline_commit: 180988e852b832cbf9aeb88d5c8d4bc3c873dba9
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-3-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Depois do cadastro ou do login, o usuário cai em `/onboarding`, que hoje é só um placeholder ("será implementado no Epic 3"). Ninguém explica o orçamento por envelope, e não há onde guardar o progresso do onboarding, que as Stories 3.2–3.8 vão usar.

**Approach:** Criar a tabela `onboarding_progress` (uma linha por usuário, criada na primeira leitura) e uma API para ler o progresso e concluir ou pular o tutorial. O `/onboarding` passa a ter a tela "Bem-vindo ao porquinho!", seguida de um tutorial de 4 telas com diagramas simples, indicador "Passo 1 de 8" e botões Próximo e Pular tutorial.

## Boundaries & Constraints

**Always:**
- Migration **V15** (a V7 citada no épico já existe). `onboarding_progress`: `id` UUID PK, `user_id` UUID FK UNIQUE, `current_step` INT NOT NULL DEFAULT 1, `steps_completed` TEXT com array JSON (padrão `audit_logs.metadata`), `is_completed` BOOLEAN NOT NULL DEFAULT false, `created_at`, `updated_at` (TIMESTAMP WITH TIME ZONE, compatível com o H2 dos testes).
- `GET /api/v1/onboarding/me` cria a linha (passo 1) se ela não existir e devolve `{currentStep, stepsCompleted, completed}`. `POST /api/v1/onboarding/me/tutorial` `{skipped}` grava `tutorial_viewed` ou `tutorial_skipped` e avança `current_step` para 2. É idempotente: repetir não duplica nem volta passo.
- `user_id` sempre vem do JWT.
- Audit `onboarding_tutorial_completed` com `{skipped}`, para medir a taxa de conclusão (não há ferramenta de analytics).
- LGPD: apagar `onboarding_progress` no `purgeUser`; exportar no `DataExportService` e entrar no `assertExportsEveryColumn`.
- Pular pede confirmação ("Pular o tutorial?"). Confirmar avança como "skipped".
- Tela inteira em pt-BR, com linguagem simples. Botões com no mínimo 44px e navegáveis por teclado. Ícones do `lucide-vue-next`, que já está instalado. Diagramas em SVG/Tailwind inline, sem imagens externas.
- Se `completed=true`, o `/onboarding` redireciona para `/dashboard`.
- **Decisão (Luiz, 2026-11-01) B:** enquanto os passos 2–8 não existem, o `/onboarding` com `current_step ≥ 2` redireciona para `/dashboard`, e concluir ou pular o tutorial também leva ao `/dashboard`. Assim, o login (que manda para `/onboarding`) cai no dashboard para quem já fez o tutorial. A Story 3.2 troca esse destino pelo passo 2.

**Never:**
- Telas dos passos 2–8 (Stories 3.2–3.8).
- Guard que obrigue o onboarding em outras rotas.
- Biblioteca nova, imagens externas ou editar migrations já aplicadas.

## I/O & Edge-Case Matrix

| Cenário | Estado | Resultado | Erro |
|---|---|---|---|
| 1º acesso | sem linha | linha criada, passo 1; mostra boas-vindas | — |
| Concluir tutorial | passo 1 | `tutorial_viewed`, passo 2, audit | — |
| Pular | passo 1, confirma | `tutorial_skipped`, passo 2, audit | — |
| Repetir POST | passo ≥ 2 | sem mudança e sem novo audit; 200 com estado atual | — |
| Onboarding concluído ou passo ≥ 2 | `completed=true` ou `current_step ≥ 2` | `/onboarding` → `/dashboard` | — |
| Corrida no 1º GET | duas abas | uma linha (UNIQUE `user_id`) | conflito → relê |
| API falha | rede/5xx | toast de erro; continua na tela | — |

</frozen-after-approval>

## Code Map

- `porquinho-frontend/src/views/OnboardingView.vue` -- placeholder atual; vira o fluxo do passo 1. Cadastro (`RegisterView.vue:248`), Google (`AuthCallbackView.vue:75`) e login (`LoginView.vue:98`) já mandam para `/onboarding`.
- `porquinho-frontend/src/router/index.ts:77` -- rota `/onboarding` (requiresAuth) já existe.
- `porquinho-frontend/src/services/subscriptionService.ts` -- modelo para criar `onboardingService.ts` (axios `api`).
- `porquinho-frontend/src/views/__tests__/SubscriptionView.spec.ts` -- padrão de testes com Vitest e mocks de serviço.
- `porquinho-backend/src/main/java/com/porquinho/controller/SubscriptionController.java` -- padrão de controller: `@AuthenticationPrincipal Jwt`, `/me`, IP via `request.getRemoteAddr()`.
- `porquinho-backend/src/main/java/com/porquinho/entity/Subscription.java`, `repository/SubscriptionRepository.java` -- padrão de entidade e repositório (pacotes por camada, não `onboarding/`).
- `porquinho-backend/src/main/resources/db/migration/V9__create_subscriptions_table.sql` -- padrão de migration compatível com H2 (DEFAULT antes da PK, sem índice parcial).
- `porquinho-backend/src/main/java/com/porquinho/service/AccountDeletionService.java:314-323` -- `purgeUser`: acrescentar `onboardingProgressRepository.deleteByUserId` antes do `userRepository.deleteByIdNative`.
- `porquinho-backend/src/main/java/com/porquinho/service/DataExportService.java` -- `collectUserData`: nova seção `onboarding_progress` e `exported_tables`. O teste usa o `assertExportsEveryColumn` do `DataExportServiceTest`.
- `porquinho-backend/src/main/java/com/porquinho/service/AuditLogService.java` -- `log(event, userId, ip, json)`.
- `porquinho-backend/src/main/java/com/porquinho/config/SubscriptionAccessFilter.java` -- `/api/v1/onboarding` **não** fica isento: exige acesso, e o trial já está ativo.

## Tasks & Acceptance

**Execution:**
- [x] `V15__create_onboarding_progress_table.sql` -- tabela e comentários.
- [x] `entity/OnboardingProgress.java`, `repository/OnboardingProgressRepository.java` -- entidade e repositório (`findByUserId`, `deleteByUserId`).
- [x] `service/OnboardingService.java` -- `getOrCreate`, `completeTutorial(userId, skipped, ip)`, com a matriz acima.
- [x] `controller/OnboardingController.java` + `dto/OnboardingProgressResponse.java` -- os 2 endpoints.
- [x] `AccountDeletionService.java`, `DataExportService.java` -- purge e export.
- [x] `services/onboardingService.ts`, `views/OnboardingView.vue` -- boas-vindas, 4 telas (o que é o envelope, como funciona no app, por que funciona, "pronto para começar"), indicador "Passo 1 de 8", Próximo, Voltar e Pular com confirmação.
- [x] Testes -- serviço, controller, purge e export no backend; view no Vitest (avançar, pular com confirmação, redirect quando concluído, erro de API).

**Acceptance Criteria:**
- Dado um usuário recém-cadastrado, quando ele abre `/onboarding`, então vê "Bem-vindo ao porquinho!", o indicador "Passo 1 de 8" e os botões Próximo e Pular tutorial, e existe uma linha `onboarding_progress` com `current_step=1` e `is_completed=false`.
- Dado o tutorial na última tela, quando ele clica em Próximo, então `current_step=2`, `steps_completed` contém `tutorial_viewed` e ele é levado ao `/dashboard`.

## Spec Change Log

## Review Triage Log

Revisão em 3 camadas (Blind Hunter, Edge Case Hunter, Verification Gap) — 2026-11-01.

| # | Origem | Achado | Veredito | Evidência | Rota |
|---|---|---|---|---|---|
| 1 | Blind, VG | A releitura após conflito de UNIQUE no 1º GET não é testada no PostgreSQL real fora de transação de teste | medium | Teste unitário usa mock; o de integração é `@Transactional` e nunca provoca o conflito | patch |
| 2 | Blind, VG | `finish()`: falha do `router.push` aparece como "não foi possível salvar" | low | push e POST no mesmo try; correção trivial | patch |
| 3 | Edge | Dois cliques no mesmo tick disparam dois POSTs | low | `:disabled` só vale após re-render; guard de 1 linha | patch |
| 4 | VG | Contrato do `onboardingService.ts` (URL e `{skipped}`) sem teste | medium | Serviço sempre mockado; `{skip}` gravaria "viewed" sem erro | patch |
| 5 | Blind, VG | Nome do teste `purgeDeletesOnboardingProgressBeforeTheUser` não descreve o que verifica | low | Só prova que a FK bloqueia; correção direta | patch |
| 6 | VG | Lock pessimista contra audit duplicado sem teste concorrente | low | Sem precedente de teste concorrente; botão desabilitado e guard do item 3 | defer |
| 7 | Blind | IP do audit atrás de proxy (`getRemoteAddr`) | low | Já registrado em `deferred-work.md` (IP atrás de proxy em vários controllers) | reject (duplicado) |
| 8 | Blind | Fallback captura qualquer `DataIntegrityViolationException` (FK de usuário inexistente → 500) | low | Exige JWT válido de usuário purgado; sessões são revogadas na exclusão | reject |
| 9 | Blind, Edge | `steps_completed` malformado ou `null` derruba GET e export; falta CHECK em `current_step` | low | Só o app grava a coluna, via `addStep`; V15 já aplicada (CHECK exigiria V16) | reject |
| 10 | Blind | Comentários da V15 citam H2, mas os testes usam Testcontainers | low | Padrão herdado da V9; V15 aplicada, não editável | reject |
| 11 | Blind | Redirecionamento ignora `current_step` (passo 5 voltaria ao 2) | low | Decisão B: só passo 1 existe; a 3.2 mapeia passo → rota | reject |
| 12 | Blind | Trial expirado fica preso no onboarding | false | Interceptor 402 em `api.ts:66` leva a `/assinatura` | reject |
| 13 | Blind | Lacunas de teste no controller (401, `skipped: null`, JSON malformado, IP) | low | 401 coberto pelo filtro de segurança; `null` → viewed é o contrato | reject |
| 14 | Blind | Front sem teste de foco/toast/timer; anúncio duplo no leitor de tela | low | Cosmético; comportamento principal coberto | reject |
| 15 | Edge | `router.replace` tardio depois de sair da view | low | Janela de ms no 1º carregamento | reject |
| 16 | Edge | Fechar o diálogo com Esc durante o salvamento | low | A navegação acontece igual; sem perda de dado | reject |

## Verification

**Commands:**
- `cd porquinho-backend && ./mvnw test` -- 0 falhas
- `cd porquinho-frontend && npx vitest run && npx vue-tsc --noEmit -p tsconfig.app.json` -- 0 falhas

**Manual checks:**
- Fazer login em dev → `/onboarding` mostra as boas-vindas e o tutorial; pular pede confirmação; a linha aparece no Supabase de dev.
