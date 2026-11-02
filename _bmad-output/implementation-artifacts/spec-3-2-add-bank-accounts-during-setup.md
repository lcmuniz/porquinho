---
title: 'Adicionar contas bancárias no setup (passo 2 do onboarding)'
type: 'feature'
created: '2026-11-02'
status: 'done'
baseline_commit: e8c284cff926e0ab540bb0372158c348e3251c26
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-3-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Depois do tutorial (passo 1), o usuário vai direto ao dashboard sem nenhuma conta bancária. Não existe a tabela `accounts`, que o Epic 4 (contas), o Epic 5 (importação) e a Story 3.6 vão usar.

**Approach:** Criar a tabela `accounts` e uma API REST de contas (`/api/v1/accounts`) que sirva também fora do onboarding. O `/onboarding` passa a mostrar o passo 2 "Adicione suas contas bancárias" para quem está em `current_step=2`: um formulário para adicionar contas uma a uma, a lista das contas já salvas e os botões Próximo, Voltar e Pular por agora.

## Boundaries & Constraints

**Always:**
- Migration **V16** `accounts`: `id` UUID PK, `user_id` FK indexada, `account_name` VARCHAR(100) NOT NULL, `bank_name` VARCHAR(100) NULL, `account_type` VARCHAR(20) CHECK (`CHECKING`, `SAVINGS`), `initial_balance` e `current_balance` DECIMAL(15,2) NOT NULL, `created_at`, `updated_at`, `deleted_at`.
- `POST /api/v1/accounts` → 201. Valida: nome obrigatório (trim, até 100); banco opcional (até 100); tipo obrigatório; saldo com até 2 casas e dentro do DECIMAL(15,2). `current_balance` = `initial_balance` na criação. `GET /api/v1/accounts` lista só as do usuário com `deleted_at IS NULL`, da mais antiga para a mais nova. `user_id` sempre vem do JWT.
- `POST /api/v1/onboarding/me/accounts` `{skipped}` só vale com `current_step=2`. Sem pular, exige ao menos 1 conta ativa (409 caso contrário). Pulando sem nenhuma conta, cria "Minha Conta Principal" (CHECKING, saldo 0); pulando com contas, só avança. Grava `accounts_added` ou `accounts_skipped`, avança para `current_step=3` e registra o audit `onboarding_accounts_completed` `{skipped, accounts}`. Com passo ≥ 3, é idempotente (200, sem mudança). Com passo 1, responde 409.
- `/onboarding` escolhe a tela pelo progresso: passo 1 → tutorial; passo 2 → contas; passo ≥ 3 ou concluído → `/dashboard`. Isso estende a decisão B da 3.1 até a Story 3.3 existir. Concluir o tutorial leva ao passo 2, e não mais ao dashboard. Voltar no passo 2 mostra o tutorial de novo sem mexer no progresso; ao terminá-lo, o POST idempotente devolve o passo 2.
- Indicador "Passo 2 de 8". Banco é texto livre com sugestões (`<datalist>` com os principais bancos do Brasil). Saldo digitado no formato brasileiro ("1.234,56") e exibido em R$ (`Intl.NumberFormat('pt-BR')`).
- LGPD: `accounts` entra no `purgeUser` (antes do usuário) e no export, coberta pelo `assertExportsEveryColumn`.
- Erros em pt-BR com a correção sugerida (ex.: "Informe o nome da conta").
- **Decisão (Luiz, 2026-11-02) 1A:** saldo inicial negativo é permitido (cheque especial) e é exibido em vermelho.
- **Decisão (Luiz, 2026-11-02) 2A:** a lista do passo 2 tem o botão "Remover", com `DELETE /api/v1/accounts/{id}` → 204 (soft delete, só contas do próprio usuário; conta de outro usuário ou inexistente → 404). A conta removida some da lista e não conta para o mínimo de 1.

**Never:**
- Editar conta, transferências, saldo total ou a página de contas do Epic 4.
- Cartões (3.3), biblioteca nova de máscara ou moeda, ou editar migrations aplicadas.

## I/O & Edge-Case Matrix

| Cenário | Estado | Resultado | Erro |
|---|---|---|---|
| Adicionar conta | passo 2, dados válidos | 201; conta aparece na lista; saldo atual = inicial | — |
| Nome vazio ou saldo inválido | — | 400; mensagem no campo | — |
| Próximo com contas | ≥ 1 conta | `accounts_added`, passo 3, audit, `/dashboard` | — |
| Próximo sem contas | 0 contas | botão desabilitado; a API responde 409 se chamada | 409 |
| Pular sem contas | 0 contas | cria "Minha Conta Principal" com saldo 0, `accounts_skipped`, passo 3 | — |
| Pular com contas | ≥ 1 conta | só avança, `accounts_skipped` | — |
| Repetir o POST | passo ≥ 3 | 200, sem mudança e sem audit | — |
| Passo 1 | tutorial pendente | 409 | — |
| Conta de outro usuário | — | nunca listada; DELETE → 404 | 404 |
| Saldo negativo | "-150,00" | aceito; exibido em vermelho | — |
| Remover conta | conta própria | 204; some da lista; Próximo desabilita se zerar | — |

</frozen-after-approval>

## Code Map

- `porquinho-frontend/src/views/OnboardingView.vue` -- passo 1 (3.1). `NEXT_ROUTE` e o redirect `currentStep >= 2` mudam para a escolha de tela por passo. Extrair o tutorial ou criar `components/onboarding/AccountsStep.vue` para o passo 2.
- `porquinho-frontend/src/components/onboarding/OnboardingStepIndicator.vue` -- reutilizar com `:step="2"`.
- `porquinho-frontend/src/components/ui/input`, `ui/select`, `ui/button` -- componentes shadcn já instalados. `src/lib/date.ts` é o modelo para criar `src/lib/money.ts` (parse e format em BRL).
- `porquinho-frontend/src/services/onboardingService.ts` -- acrescentar `completeAccounts(skipped)`. Criar `services/accountService.ts`.
- `porquinho-backend/.../service/OnboardingService.java` -- padrão de `completeTutorial`/`applyTutorial` (proxy `self`, `findByUserIdForUpdate`, audit e idempotência) para o novo `completeAccounts`.
- `porquinho-backend/.../entity/Subscription.java`, `controller/SubscriptionController.java`, `dto/*Request.java` com `@Valid` (`AuthController.java:114`) -- padrões de entidade, controller e validação.
- `porquinho-backend/.../service/AccountDeletionService.java` (`purgeUser`, que apaga `onboarding_progress` antes do usuário) e `DataExportService.java` (seção por tabela, `exported_tables`) -- acrescentar `accounts`. Os testes usam `assertExportsEveryColumn` (`DataExportServiceTest`).
- `porquinho-backend/.../exception/ConflictException.java` (409) e `GlobalExceptionHandler.java` -- 400 de validação: conferir se sai como ProblemDetail com a mensagem do campo e, se não sair, adicionar o handler.

## Tasks & Acceptance

**Execution:**
- [x] `V16__create_accounts_table.sql` -- tabela, índice em `user_id` e comentários.
- [x] `entity/Account.java`, `repository/AccountRepository.java`, `service/AccountService.java`, `controller/AccountController.java`, DTOs -- criar, listar e remover (soft delete).
- [x] `OnboardingService.java`, `OnboardingController.java` -- `completeAccounts` e `POST /me/accounts`.
- [x] `AccountDeletionService.java`, `DataExportService.java` -- purge e export.
- [x] Frontend -- `lib/money.ts`, `accountService.ts`, `onboardingService.ts`, passo 2 em `/onboarding` (formulário, lista, Próximo, Voltar, Pular), tutorial → passo 2.
- [x] Testes -- matriz no backend (serviço, controller e integração com PostgreSQL); Vitest do passo 2, de `money.ts` e do serviço.

**Acceptance Criteria:**
- Dado um usuário que concluiu o tutorial, quando ele abre `/onboarding`, então vê "Passo 2 de 8" e "Adicione suas contas bancárias".
- Dado que ele adicionou "Nubank" com R$ 1.234,56, quando clica em Próximo, então a conta existe com saldo inicial e atual 1234.56, o progresso fica no passo 3 com `accounts_added` e ele é levado ao `/dashboard`.

## Spec Change Log

## Review Triage Log

Revisão em 3 camadas (Blind Hunter, Edge Case Hunter, Verification Gap) — 2026-11-02. A saída do Edge Case Hunter chegou parcialmente comprimida e o hash expirou: só 6 achados ficaram legíveis.

| # | Origem | Achado | Veredito | Evidência | Rota |
|---|---|---|---|---|---|
| 1 | Blind, Edge | Adicionar conta antes de a lista carregar: a resposta tardia da lista sobrescreve a conta recém-adicionada | medium | `onMounted` atribui `accounts.value = await listAccounts()`; botão de adicionar não olha `loadingList` | patch |
| 2 | Blind, Edge | Falha ao carregar a lista deixa o Próximo desabilitado; só resta pular (grava `accounts_skipped`) | medium | `:disabled="busy \|\| accounts.length === 0"` com lista vazia após erro | patch |
| 3 | Edge | 400 sem chave de campo conhecida não mostra mensagem | low | `Object.assign(errors, fieldErrors)` sem checar as chaves; correção direta | patch |
| 4 | Blind | Erro do saldo fora do `aria-describedby` | low | `AccountsStep.vue:104` aponta só para o hint; correção direta | patch |
| 5 | VG | Ramos 404 e erro do Remover sem teste | medium | Nenhum spec rejeita `deleteAccount` | patch |
| 6 | Edge | Nome só com espaço não separável (U+00A0) passa no `@NotBlank` | low | `trim()` não remove U+00A0; `strip()` é correção direta | patch |
| 7 | Blind | Nome do teste de integração diz FK e não verifica FK | low | Correção direta | patch |
| 8 | Blind | Usuários parados no passo 2 pela 3.1 ficam sem conta | false | Ninguém fica preso: o login leva ao `/onboarding`, que agora mostra o passo 2; produção ainda não existe | reject |
| 9 | Blind, Edge | Corridas entre Próximo/Pular e criar/remover conta em outra aba | low | Exige duas abas simultâneas no onboarding; serializar exige lock compartilhado novo | reject |
| 10 | Blind | Sem limite de contas nem rate limit | low | NFR30 pede contas ilimitadas | reject |
| 11 | Blind | Criar/remover conta sem audit | low | Intenção não pede; audit da conclusão do passo existe | reject |
| 12 | Blind | Handler global de 400 muda o corpo dos endpoints `@Valid` existentes | low | Status igual; nenhuma tela de auth lê o `detail` (VG conferiu) | reject |
| 13 | Blind | Saldo com ponto ("12.34") e menos tipográfico (U+2212) recusados | low | Formato brasileiro é o da intenção; mensagem mostra o formato | reject |
| 14 | Blind | Remover sem confirmação nem desfazer | low | Onboarding sem transações; readicionar é imediato | reject |
| 15 | Blind | Dinheiro como número no JSON | false | Convenção da arquitetura (`DECIMAL` no banco, number no JSON), registrada no contexto do épico | reject |
| 16 | Blind | `data_version` do export não mudou | low | Nenhum consumidor versionado do export | reject |
| 17 | Blind | Faltam testes: banco só com espaços, tipo em minúsculas, POST sem corpo, falha ao pular, pular no tutorial reaberto | low | Casos de contrato menores; caminhos principais cobertos | reject |
| 18 | Blind | Índice só em `user_id`, sem `created_at`/parcial | low | Volume por usuário pequeno | reject |
| 19 | Blind | Nomes de conta duplicados sem aviso | low | Intenção não pede unicidade | reject |

## Verification

**Commands:**
- `cd porquinho-backend && ./mvnw test` -- 0 falhas
- `cd porquinho-frontend && npx vitest run && npx vue-tsc --noEmit -p tsconfig.app.json` -- 0 falhas

**Manual checks:**
- Com o usuário de teste (hoje no passo 2): `/onboarding` mostra o passo 2; adicionar uma conta, avançar e ver a linha no Supabase de dev.
