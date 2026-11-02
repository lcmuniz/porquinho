---
title: 'Adicionar cartões de crédito no setup (passo 3 do onboarding)'
type: 'feature'
created: '2026-11-02'
status: 'done'
baseline_commit: 9492e40c671eb01fe8ca9fb5af62dbe5ac7ce1db
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-3-context.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-3-2-add-bank-accounts-during-setup.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Depois das contas (passo 2), o usuário vai direto ao dashboard, sem cadastrar cartões de crédito. Não existe a tabela `credit_cards`, que o Epic 4 e a importação vão usar.

**Approach:** Repetir o desenho da Story 3.2 para cartões: tabela `credit_cards`, API REST `/api/v1/credit-cards` (listar, criar, remover) que sirva também fora do onboarding, e o passo 3 "Adicione seus cartões de crédito" no `/onboarding`. Cartões são opcionais.

## Boundaries & Constraints

**Always:**
- Migration **V17** `credit_cards`: `id` UUID PK, `user_id` FK indexada, `card_name` VARCHAR(100) NOT NULL, `bank_name` VARCHAR(100) NULL, `card_limit` DECIMAL(15,2) NOT NULL, `due_day` INT NOT NULL CHECK 1–31, `created_at`, `updated_at`, `deleted_at`.
- `POST /api/v1/credit-cards` → 201. Validação: nome obrigatório (strip, até 100); banco opcional; limite obrigatório, **≥ 0**, até 2 casas, dentro de DECIMAL(15,2); dia de vencimento inteiro de 1 a 31. `GET` lista só os cartões ativos do usuário, do mais antigo para o mais novo. `DELETE /{id}` faz soft delete → 204, e cartão de outro usuário, inexistente ou já removido → 404. `user_id` sempre do JWT.
- `POST /api/v1/onboarding/me/cards` `{skipped}` só vale com `current_step=3`. Grava `cards_added` quando há ao menos 1 cartão ativo e `skipped=false`, e `cards_skipped` nos outros casos. Próximo sem cartões é permitido e grava `cards_skipped`. Avança para `current_step=4` e registra o audit `onboarding_cards_completed` `{skipped, cards}`. Com passo ≥ 4 é idempotente (200, sem mudança). Com passo < 3, responde 409. Pular não cria cartão nenhum.
- `/onboarding` por passo: 1 tutorial, 2 contas, 3 cartões, ≥ 4 ou concluído → `/dashboard` (decisão B estendida até a Story 3.4). Concluir as contas leva ao passo 3. Voltar no passo 3 mostra as contas sem mexer no progresso; Próximo ali usa o POST idempotente e devolve o passo 3.
- Mesmo padrão de UI, erros (ProblemDetail 400 com `errors` por campo) e `lib/money.ts` da Story 3.2. O dia de vencimento é um campo numérico 1–31. A lista mostra nome, banco, limite em R$, "vence dia X" e o botão Remover.
- Indicador "Passo 3 de 8". LGPD: `credit_cards` no `purgeUser` (antes do usuário) e no export (`assertExportsEveryColumn`).

**Never:**
- Dia de fechamento, fatura, limite disponível, edição de cartão ou página de cartões do Epic 4.
- Biblioteca nova, editar migrations aplicadas ou mudar o comportamento do passo 2.

## I/O & Edge-Case Matrix

| Cenário | Estado | Resultado | Erro |
|---|---|---|---|
| Adicionar cartão | passo 3, dados válidos | 201; aparece na lista | — |
| Dia 0, 32 ou não inteiro | — | 400 "Informe um dia entre 1 e 31" | — |
| Limite negativo ou inválido | — | 400 com mensagem no campo | — |
| Próximo com cartões | ≥ 1 ativo | `cards_added`, passo 4, audit, `/dashboard` | — |
| Próximo ou Pular sem cartões | 0 ativos | `cards_skipped`, passo 4, nenhum cartão criado | — |
| Repetir POST | passo ≥ 4 | 200, sem mudança e sem audit | — |
| Passo 1 ou 2 | — | 409 | — |
| Cartão de outro usuário | — | nunca listado; DELETE → 404 | 404 |

</frozen-after-approval>

## Code Map

- Story 3.2 (`git show 9492e40`) -- modelo de ponta a ponta. Copiar o desenho de `entity/Account.java`, `repository/AccountRepository.java`, `service/AccountService.java`, `controller/AccountController.java` e `dto/CreateAccountRequest.java` (strip Unicode no construtor do record, mensagens em pt-BR) e de `AccountResponse`.
- `porquinho-backend/.../service/OnboardingService.java` -- `completeAccounts`/`applyAccounts` (proxy `self`, `findByUserIdForUpdate`, audit, idempotência por passo) como base de `completeCards`. `OnboardingController.java` -- novo `POST /me/cards`.
- `porquinho-backend/.../exception/GlobalExceptionHandler.java` -- handlers de 400 já existentes; não mexer.
- `porquinho-backend/.../service/AccountDeletionService.java` (`purgeUser`) e `DataExportService.java` -- acrescentar `credit_cards`; testes em `AccountDeletionServiceTest` (InOrder) e `DataExportServiceTest`.
- `porquinho-frontend/src/components/onboarding/AccountsStep.vue` -- modelo do `CardsStep.vue`, incluindo os ajustes da revisão da 3.2: guard de `loadingList`, `listFailed`, 400 sem campo conhecido, `aria-describedby` com o erro e 404 no Remover.
- `porquinho-frontend/src/views/OnboardingView.vue` -- escolha de tela por passo; acrescentar o passo 3. `src/lib/money.ts`, `services/accountService.ts` e `onboardingService.ts` -- reutilizar; criar `cardService.ts` e `completeCards`.
- Testes modelo: `AccountServiceTest`, `AccountControllerTest`, `AccountOnboardingIntegrationTest` e `__tests__/AccountsStep.spec.ts`.

## Tasks & Acceptance

**Execution:**
- [x] `V17__create_credit_cards_table.sql` -- tabela, CHECK, índice e comentários.
- [x] Backend cartões -- entidade, repositório, serviço, controller e DTOs (listar, criar, remover).
- [x] `OnboardingService.java`, `OnboardingController.java` -- `completeCards` e `POST /me/cards`.
- [x] `AccountDeletionService.java`, `DataExportService.java` -- purge e export.
- [x] Frontend -- `cardService.ts`, `completeCards`, `CardsStep.vue`, passo 3 no `OnboardingView.vue`, contas → passo 3.
- [x] Testes -- matriz no backend (serviço, controller e integração com PostgreSQL) e Vitest do `CardsStep`, do serviço e do fluxo na view.

**Acceptance Criteria:**
- Dado um usuário que concluiu as contas, quando ele abre `/onboarding`, então vê "Passo 3 de 8" e "Adicione seus cartões de crédito".
- Dado que ele adicionou "Nubank Roxinho" com limite R$ 5.000,00 e vencimento no dia 10, quando clica em Próximo, então o cartão existe com esses valores, o progresso fica no passo 4 com `cards_added` e ele é levado ao `/dashboard`.

## Spec Change Log

## Review Triage Log

Revisão em 3 camadas (Blind Hunter, Edge Case Hunter, Verification Gap) — 2026-11-02. O Verification Gap não encontrou lacunas. O Edge Case Hunter reportou 6 achados, todos triados abaixo.

| # | Origem | Achado | Veredito | Evidência | Rota |
|---|---|---|---|---|---|
| 1 | Blind | `finish()` libera os botões antes da navegação, permitindo outro POST | low | `finally` zerava `finishing` antes do `emit('done')`; correção direta (também no `AccountsStep`) | patch |
| 2 | Blind, Edge | Falha ao carregar a lista esconde cartões existentes; o usuário pode recadastrá-los | low | Só ocorre com falha de rede no carregamento; corrigir exige estado novo com nova tentativa | reject |
| 3 | Blind | Falta `CHECK (card_limit >= 0)` no banco | low | Só o app grava pela validação do DTO; V17 aplicada (exigiria V18) | reject |
| 4 | Blind | Serviço depende da validação do controller | low | Único chamador é o controller | reject |
| 5 | Blind, Edge | Sem limite de cartões, sem paginação, sem audit de criar/remover | low | Mesmo veredito da 3.2 (NFR30; intenção não pede) | reject |
| 6 | Blind | Front não valida o máximo de 13 dígitos; dinheiro como number | low | O 400 do backend aparece no campo; number no JSON é a convenção | reject |
| 7 | Blind | Faltam testes de limite no DTO (`10.0`, máximo, nulos, 401) | low | Casos de contrato menores; matriz coberta | reject |
| 8 | Blind, Edge | Vencimento 29–31 sem regra para meses curtos | low | Sem cálculo de fatura nesta story; é regra do Epic 4 | reject |
| 9 | Blind | `cards_skipped` com cartões quando `skipped=true` | false | É a regra da intenção; o audit traz `{skipped, cards}` | reject |
| 10 | Blind | Sem teste concorrente de `completeCards`; faltam testes de erro na view | low | Concorrência já adiada na 3.1; caminhos principais cobertos | reject |
| 11 | Edge | Dois DELETE simultâneos sobrescrevem `deleted_at` | low | Sem efeito visível ao usuário | reject |
| 12 | Edge | Caracteres de controle em nome/banco | low | Sem caminho de entrada comum (formulário de uma linha) | reject |
| 13 | Edge | 409 do `completeCards` com progresso no passo 1/2 deixa o usuário só com o toast | low | Exige progresso alterado em outra aba no meio do onboarding | reject |

## Verification

**Commands:**
- `cd porquinho-backend && ./mvnw test` -- 0 falhas (com o backend de dev **parado**, para que o devtools não aplique uma V17 intermediária no Supabase)
- `cd porquinho-frontend && npx vitest run && npx vue-tsc --noEmit -p tsconfig.app.json` -- 0 falhas

**Manual checks:**
- Com o usuário de teste (hoje no passo 3): `/onboarding` mostra o passo 3; adicionar, remover e avançar.
