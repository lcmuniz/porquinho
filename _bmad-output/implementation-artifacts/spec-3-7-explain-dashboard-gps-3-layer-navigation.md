---
title: 'Explicar a navegação em 3 camadas do Dashboard GPS (passo 7 do onboarding)'
type: 'feature'
created: '2026-11-02'
status: 'done'
baseline_commit: 773d8cb68f16cc79bb820f52e8a1b97a7b6c6ad1
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-3-context.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-3-6-guided-first-transaction-import.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Depois do passo 6, o usuário vai direto ao dashboard sem saber como ele funciona. O Dashboard GPS (Epic 7) é a principal funcionalidade do produto e se organiza em 3 camadas navegadas por rolagem vertical; ninguém explica isso.

**Approach:** O passo 7 "Conheça o Dashboard GPS" é um tutorial curto, no mesmo desenho do tutorial do passo 1 (telas com Próximo e Voltar, diagramas em SVG/Tailwind inline). Ele apresenta as 3 camadas com uma ilustração esquemática de cada uma e um diagrama da rolagem vertical entre elas. Ao final, o progresso avança.

## Boundaries & Constraints

**Always:**
- Sem migration nova.
- `POST /api/v1/onboarding/me/dashboard-tour` (sem corpo) só vale com `current_step=7`. Grava `dashboard_gps_explained`, avança para `current_step=8` e registra o audit `onboarding_dashboard_tour_completed`. Com passo ≥ 8 é idempotente: 200, sem mudança e sem audit. Com passo < 7, 409. `user_id` sempre do JWT.
- `/onboarding` por passo: 1 a 6 como hoje, 7 tour do dashboard, ≥ 8 ou concluído vai para `/dashboard` (decisão B estendida até a 3.8). Concluir o passo 6 leva ao passo 7. Voltar na primeira tela do passo 7 mostra o passo 6 sem mexer no progresso; o "Importar depois" dele usa o POST idempotente e devolve o passo 7.
- Indicador "Passo 7 de 8" em todas as telas do tour, como no passo 1.
- Telas, todas em pt-BR:
  1. **"Conheça o Dashboard GPS"** — diagrama das 3 camadas empilhadas com a seta de rolagem e a dica "Role verticalmente para navegar entre camadas".
  2. **Camada 1 — "Onde estou?"** — "5 métricas essenciais, 30 segundos para clareza total", com ilustração esquemática de 5 cartões de métrica.
  3. **Camada 2 — "Para onde vou?"** — "Gráficos por categoria e tendências", com ilustração esquemática de gráficos.
  4. **Camada 3 — "Preciso ajustar?"** — "Todas as transações, filtros e edição", com ilustração esquemática de uma lista.
  5. **Fechamento** — destaque "Seu check-in semanal leva apenas 5 minutos" e o botão "Próximo" que conclui o passo.
- As ilustrações são esquemáticas (formas e rótulos genéricos, sem valores reais e sem capturas de tela), em SVG/Tailwind inline. Animação só com transição CSS discreta, desligada com `prefers-reduced-motion`.
- Sem botão Pular: o tour é curto e não tem nada a configurar.
- Mesmo padrão de `finish()` dos passos anteriores (guard, `finishing` só zerado no erro, toast em falha com `||` no `detail`).
- Botões com no mínimo 44px, navegáveis por teclado; foco vai para o título a cada tela.

**Never:**
- Construir o dashboard real ou dados de exemplo (Epic 7).
- Imagens externas, GIFs, biblioteca nova ou editar migrations V1 a V18.

## I/O & Edge-Case Matrix

| Cenário | Estado | Resultado | Erro |
|---|---|---|---|
| Abrir `/onboarding` | passo 7 | "Passo 7 de 8", tela 1 do tour | — |
| Próximo nas telas 1 a 4 | — | avança uma tela, sem chamar a API | — |
| Voltar na tela 1 | passo 7 | mostra o passo 6; "Importar depois" volta ao passo 7 sem mudar o progresso | — |
| Próximo na tela 5 | passo 7 | `dashboard_gps_explained`, passo 8, audit, `/dashboard` | — |
| Repetir o POST | passo ≥ 8 | 200, sem mudança e sem audit | — |
| Passo < 7 | — | 409 | 409 |
| Falha da API | rede/5xx | toast de erro; continua na tela 5 | — |

</frozen-after-approval>

## Code Map

- `porquinho-frontend/src/views/OnboardingView.vue` -- tutorial do passo 1 (telas, indicador de tela, foco, Próximo/Voltar) como modelo; escolha de tela por passo, acrescentar o passo 7. Extrair o tour para `components/onboarding/DashboardTourStep.vue` para não inchar a view.
- `porquinho-frontend/src/components/onboarding/ImportStep.vue` -- padrão de `finish()` e `emit('done' | 'back' | 'error')`.
- `porquinho-backend/.../service/OnboardingService.java` -- `completeImport`/`applyImport` como base de `completeDashboardTour`. `OnboardingController.java` -- novo `POST /me/dashboard-tour`.
- `services/onboardingService.ts` -- `completeDashboardTour()`.
- Testes modelo: `OnboardingServiceTest`, `OnboardingControllerTest`, `OnboardingImportIntegrationTest`, `ImportStep.spec.ts`, `OnboardingView.spec.ts`, `onboardingService.spec.ts`.

## Tasks & Acceptance

**Execution:**
- [x] `OnboardingService.java`, `OnboardingController.java` -- `completeDashboardTour` e `POST /me/dashboard-tour`.
- [x] Frontend -- `completeDashboardTour`, `DashboardTourStep.vue` (5 telas), passo 7 no `OnboardingView.vue`, passo 6 → passo 7, Voltar 7 → 6.
- [x] Testes -- matriz no backend (serviço, controller e integração com PostgreSQL) e Vitest do `DashboardTourStep`, do serviço e do fluxo na view.

**Acceptance Criteria:**
- Dado um usuário que concluiu o passo 6, quando ele abre `/onboarding`, então vê "Passo 7 de 8" e "Conheça o Dashboard GPS" com o diagrama das 3 camadas e a dica de rolagem.
- Dado o tour na última tela, quando ele clica em Próximo, então o progresso fica no passo 8 com `dashboard_gps_explained`, existe o audit `onboarding_dashboard_tour_completed` e ele é levado ao `/dashboard`.

## Spec Change Log

## Review Triage Log

Revisão em 3 camadas (Blind Hunter, Edge Case Hunter, Verification Gap) — 2026-11-02. O Verification Gap não encontrou lacunas.

| # | Origem | Achado | Veredito | Evidência | Rota |
|---|---|---|---|---|---|
| 1 | Blind, Edge | `aria-live` numa `section` recriada a cada tela: anúncio perdido ou em dobro com o foco no título | low | `:key="screen"` remonta a região | patch |
| 2 | Blind | Diagrama da tela 1 lido duas vezes (rótulo da `figure` e conteúdo) | low | Telas 2 a 4 já usam `aria-hidden` | patch |
| 3 | Blind | Ícone de mouse na dica de rolagem não serve no celular | low | Correção direta | patch |
| 4 | Blind | Cores em hex fixas nas ilustrações | low | Resto do componente usa classes Tailwind | patch |
| 5 | Blind | Sem teste HTTP do POST repetido a partir do passo 8 | low | Só o unitário cobre | patch |
| 6 | Blind | Conflitos dos passos 1 e 6 no mesmo teste e no mesmo usuário | low | Correção direta | patch |
| 7 | Blind | Botão final diz "Próximo" mas salva e sai | low | A intenção pede o botão "Próximo" que conclui o passo | reject |
| 8 | Edge, Blind | 409 em aba antiga deixa o usuário só com o toast | low | Mesmo veredito das 3.3, 3.4 e 3.6 | reject |
| 9 | Blind | `ApiError` e mensagem de erro duplicadas em 7 componentes | low | Padrão existente; extrair é refatoração fora do escopo | reject |
| 10 | Blind | Audit sem detalhe, diferente do passo 6 | low | Tour não tem opção de pular; nada a registrar | reject |
| 11 | Blind | Voltar encadeado 7 → 6 → 5 sem teste | low | Cada POST idempotente já tem teste | reject |
| 12 | Blind | Spec e sprint-status fora do diff | false | Diff do workflow cobre só o código | reject |
| 13 | Blind | Destino depois do passo 7 sem TODO para a 3.8 | low | A 3.8 é a próxima story e troca esse destino | reject |

## Verification

**Commands:**
- `cd porquinho-backend && ./mvnw test` -- 0 falhas. Antes, parar o backend de dev (`lsof -ti :8080 | xargs kill`) e conferir que a porta ficou livre.
- `cd porquinho-frontend && npx vitest run && npx vue-tsc --noEmit -p tsconfig.app.json` -- 0 falhas

**Manual checks:**
- Com o usuário de teste (hoje no passo 7): `/onboarding` mostra o tour; percorrer as 5 telas, Voltar ao passo 6 e retornar, Próximo final leva ao dashboard.
