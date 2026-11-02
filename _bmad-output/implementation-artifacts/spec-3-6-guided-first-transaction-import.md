---
title: 'Importação guiada das primeiras transações (passo 6 do onboarding, versão sem upload)'
type: 'feature'
created: '2026-11-02'
status: 'done'
baseline_commit: 26f2097e23ca0766108132f6162dac4975e02221
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-3-context.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-3-5-customize-suggested-categories.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Depois de personalizar as categorias (passo 5), o usuário vai direto ao dashboard. O épico pede o passo 6 "Importe suas transações", com upload de OFX/CSV, processamento e prévia classificada pela IA, mas nada disso existe: a importação, o parser e a classificação são do Epic 5.

**Approach:** **Decisão (Luiz, 2026-11-02) A:** entregar agora o passo 6 sem upload. A tela "Importe suas transações" explica como baixar o extrato em OFX ou CSV nos 5 bancos suportados, avisa que a importação chega em breve e oferece um único botão, "Importar depois", que avança o onboarding. A área de upload, a barra de progresso, a prévia e o registro de `first_import_completed` ficam para o Epic 5, que vai estender esta tela.

## Boundaries & Constraints

**Always:**
- Sem migration nova.
- `POST /api/v1/onboarding/me/import` (sem corpo) só vale com `current_step=6`. Grava `import_skipped`, avança para `current_step=7` e registra o audit `onboarding_import_completed` `{skipped: true}`. Com passo ≥ 7 é idempotente: 200, sem mudança e sem audit. Com passo < 6, 409. `user_id` sempre do JWT.
- `/onboarding` por passo: 1 a 5 como hoje, 6 importação, ≥ 7 ou concluído vai para `/dashboard` (decisão B estendida até a 3.7). Concluir o passo 5 leva ao passo 6. Voltar no passo 6 mostra o passo 5 (que continua editável) sem mexer no progresso; o Próximo dele usa o POST idempotente e devolve o passo 6.
- Tela "Passo 6 de 8", título "Importe suas transações" e o texto "Baixe o extrato do seu banco em formato OFX ou CSV e faça upload aqui".
- Instruções por banco (Nubank, Banco do Brasil, Itaú, Bradesco, Caixa) em seções expansíveis nativas (`<details>`/`<summary>`), com passos curtos e genéricos para exportar o extrato em OFX ou CSV pelo app ou internet banking. Sem links externos (não há páginas de ajuda próprias; links de bancos mudam sem aviso).
- Aviso visível: "A importação de arquivos chega em breve. Por enquanto, siga para o próximo passo — você poderá importar depois."
- Botões: "Importar depois" (avança) e Voltar. Mesmo padrão de `finish()` dos passos anteriores (guard de `busy`, `finishing` só zerado no erro, toast em falha).
- Alvo de toque de no mínimo 44px, navegável por teclado, tudo em pt-BR.

**Never:**
- Upload, área de arrastar e soltar, parser, tabela `transactions`, prévia ou classificação (Epic 5).
- Gravar `first_import_completed` nesta story.
- Biblioteca nova ou editar migrations V1 a V18.

## I/O & Edge-Case Matrix

| Cenário | Estado | Resultado | Erro |
|---|---|---|---|
| Abrir `/onboarding` | passo 6 | "Passo 6 de 8", "Importe suas transações", 5 bancos | — |
| Importar depois | passo 6 | `import_skipped`, passo 7, audit, `/dashboard` | — |
| Repetir o POST | passo ≥ 7 | 200, sem mudança e sem audit | — |
| Passo < 6 | — | 409 | 409 |
| Voltar | passo 6 | mostra o passo 5; Próximo volta ao passo 6 sem mudar o progresso | — |
| Falha da API | rede/5xx | toast de erro; continua na tela | — |

</frozen-after-approval>

## Code Map

- `porquinho-backend/.../service/OnboardingService.java` -- `completeCustomization`/`applyCustomization` (proxy `self`, `findByUserIdForUpdate`, audit, idempotência por passo) como base de `completeImport`. `OnboardingController.java` -- novo `POST /me/import`.
- `porquinho-frontend/src/components/onboarding/CustomizeCategoriesStep.vue` e `CardsStep.vue` -- padrão de `finish()`, botões e `emit('done' | 'back' | 'error')`. Criar `ImportStep.vue`.
- `porquinho-frontend/src/lib/banks.ts` -- nomes dos bancos; reutilizar se cobrir os 5.
- `porquinho-frontend/src/views/OnboardingView.vue` -- escolha de tela por passo; acrescentar o passo 6 e o Voltar para o 5. `services/onboardingService.ts` -- `completeImport()`.
- Testes modelo: `OnboardingServiceTest`, `OnboardingControllerTest`, `CategoryCustomizationIntegrationTest` (parte do passo 5), `CustomizeCategoriesStep.spec.ts`, `OnboardingView.spec.ts`, `onboardingService.spec.ts`.

## Tasks & Acceptance

**Execution:**
- [x] `OnboardingService.java`, `OnboardingController.java` -- `completeImport` e `POST /me/import`.
- [x] Frontend -- `completeImport`, `ImportStep.vue`, passo 6 no `OnboardingView.vue`, passo 5 → passo 6, Voltar 6 → 5.
- [x] Testes -- matriz no backend (serviço, controller e integração com PostgreSQL) e Vitest do `ImportStep`, do serviço e do fluxo na view.

**Acceptance Criteria:**
- Dado um usuário que concluiu o passo 5, quando ele abre `/onboarding`, então vê "Passo 6 de 8", "Importe suas transações", as instruções dos 5 bancos e o aviso de que a importação chega em breve.
- Dado o passo 6, quando ele clica em "Importar depois", então o progresso fica no passo 7 com `import_skipped`, existe o audit `onboarding_import_completed` e ele é levado ao `/dashboard`.

## Spec Change Log

## Review Triage Log

Revisão em 3 camadas (Blind Hunter, Edge Case Hunter, Verification Gap) — 2026-11-02. O Verification Gap não encontrou lacunas.

| # | Origem | Achado | Veredito | Evidência | Rota |
|---|---|---|---|---|---|
| 1 | Blind | Retorno antecipado com `completed=true` sem teste | low | Todos os testes usam `completed=false` | patch |
| 2 | Blind | Comentário afirma serialização de POSTs concorrentes que nenhum teste exercita | low | Teste de integração é `@Transactional`; lock igual aos outros passos | patch (comentário) |
| 3 | Blind | Nome do teste de conflito promete passos 1 a 5, mas só cobre 1 e 5 | low | Unitário cobre todos; correção direta | patch |
| 4 | Blind | Aviso estático como live region; `<nav>` em volta de dois botões | low | `role="status"` em conteúdo fixo | patch |
| 5 | Blind | Teste de duplo clique não confere o segundo botão nem um único `done` | low | Correção direta | patch |
| 6 | Blind | `busy` redundante; `:key` usa o nome em vez da chave | low | Limpeza | patch |
| 7 | Edge | `detail` vazio gera toast em branco | low | `??` mantém string vazia | patch |
| 8 | Blind | Subtítulo diz "faça upload aqui" sem upload | low | Texto exigido pela intenção; o aviso logo abaixo esclarece; o Epic 5 o torna verdadeiro | reject |
| 9 | Blind | 409 em aba antiga deixa o usuário preso só com o toast | low | Mesmo veredito da 3.3/3.4 | reject |
| 10 | Blind | `steps_completed` comparado como string no teste | low | Coluna é `TEXT` (V15), não `jsonb` | reject |
| 11 | Blind | Endpoint sem corpo não acomoda o Epic 5 | low | Acrescentar corpo opcional depois não quebra o cliente | reject |
| 12 | Blind | Instruções dos bancos genéricas e parecidas | low | A intenção pede passos curtos e genéricos, sem links | reject |
| 13 | Edge | Falha de navegação depois do POST deixa os botões travados | low | Mesmo veredito da 3.4 | reject |

## Verification

**Commands:**
- `cd porquinho-backend && ./mvnw test` -- 0 falhas. Antes, parar o backend de dev (`lsof -ti :8080 | xargs kill`) e conferir que a porta ficou livre.
- `cd porquinho-frontend && npx vitest run && npx vue-tsc --noEmit -p tsconfig.app.json` -- 0 falhas

**Manual checks:**
- Com o usuário de teste (hoje no passo 6): `/onboarding` mostra o passo 6; abrir as instruções, Voltar ao passo 5 e retornar, "Importar depois" leva ao dashboard.
