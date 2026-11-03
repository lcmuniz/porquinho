---
title: 'Página de contas bancárias com cadastro de novas contas'
type: 'feature'
created: '2026-11-03'
status: 'done'
baseline_commit: 1bd933a902723384eaf617b0e244aea8f2aa3634
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-3-context.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-3-2-add-bank-accounts-during-setup.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Contas bancárias só podem ser cadastradas no passo 2 do onboarding. Depois dele, o usuário não tem onde ver as contas, o saldo total nem cadastrar uma conta nova.

**Approach:** Criar a página `/contas` com a lista das contas ativas, o saldo total e o botão "Adicionar conta", que abre um diálogo com o mesmo formulário do passo 2. A API `/api/v1/accounts` da Story 3.2 já faz tudo o que é preciso; esta story é só de frontend.

## Boundaries & Constraints

**Always:**
- Sem mudança no backend nem migration. Usar `GET` e `POST /api/v1/accounts` como estão (a validação, a segregação por `user_id` e o filtro `deleted_at IS NULL` já existem e têm teste).
- Rota `/contas` (`requiresAuth`), view `AccountsView.vue`, título "Contas bancárias". O dashboard ganha um link "Contas" na navegação existente.
- Lista: nome, banco, tipo ("Conta corrente"/"Poupança") e saldo atual em R$; saldo negativo em vermelho, como no passo 2. Ordem da API (mais antiga primeiro). Estado vazio: "Você ainda não tem contas cadastradas" com o botão de adicionar.
- Saldo total: soma dos `currentBalance` das contas ativas, calculada em centavos inteiros (sem erro de ponto flutuante), exibida em R$ e em vermelho se negativa.
- "Adicionar conta" abre um `Dialog` com os campos nome, banco (texto com `<datalist>` de `lib/banks.ts`), tipo e saldo atual no formato brasileiro. Salvar chama `POST /accounts`; a conta aparece na lista e no total sem recarregar, o diálogo fecha e aparece "Conta adicionada com sucesso" (`role="status"`).
- Erros: 400 com `errors` aparece no campo; outra falha mostra a mensagem do servidor ou "Não foi possível salvar a conta. Tente novamente." no diálogo, que continua aberto com os dados. Falha ao carregar a lista mostra aviso com "Tentar de novo".
- O formulário de conta é extraído do `AccountsStep.vue` para um componente reutilizável (`components/accounts/AccountForm.vue`), usado pelo passo 2 e pelo diálogo. O comportamento e os testes do passo 2 continuam iguais.
- Alvos de toque de 44px, `label` em todos os campos, erros por `aria-describedby`, foco no primeiro campo ao abrir o diálogo.

**Never:**
- Editar, remover ou desativar contas (Stories 4.3 e 4.4), transferências (4.5), cartões (4.2).
- Menu ou layout global novo; biblioteca nova.

## I/O & Edge-Case Matrix

| Cenário | Estado | Resultado | Erro |
|---|---|---|---|
| Abrir `/contas` | 2 contas | lista com as 2 e o total | — |
| Abrir `/contas` | nenhuma conta | estado vazio com o botão | — |
| Adicionar conta válida | — | 201; aparece na lista, total atualizado, diálogo fecha, mensagem de sucesso | — |
| Nome vazio ou saldo inválido | — | erro no campo; diálogo aberto | 400 |
| Falha de rede ao salvar | — | mensagem no diálogo; dados preservados | 5xx |
| Saldo total negativo | contas somam -150,00 | total em vermelho | — |
| Soma com centavos | 0,10 + 0,20 | R$ 0,30 | — |
| Falha ao carregar | — | aviso e "Tentar de novo" | — |

</frozen-after-approval>

## Code Map

- `porquinho-frontend/src/components/onboarding/AccountsStep.vue` -- formulário atual (campos, `lib/money.ts`, erros por campo, `datalist`); extrair para `components/accounts/AccountForm.vue`.
- `porquinho-frontend/src/services/accountService.ts` -- `listAccounts`, `createAccount`; reutilizar.
- `porquinho-frontend/src/components/ui/dialog` -- diálogo (shadcn).
- `porquinho-frontend/src/router/index.ts` -- nova rota protegida, no padrão de `/dashboard`.
- `porquinho-frontend/src/views/DashboardView.vue` -- link "Contas" na `nav` existente.
- Testes modelo: `components/onboarding/__tests__/AccountsStep.spec.ts`, `views/__tests__/DashboardView.spec.ts`.

## Tasks & Acceptance

**Execution:**
- [x] `AccountForm.vue` extraído; `AccountsStep.vue` passa a usá-lo sem mudar comportamento.
- [x] `AccountsView.vue` -- lista, total, estado vazio, diálogo de adicionar, mensagens.
- [x] Rota `/contas` e link no dashboard.
- [x] Testes Vitest -- view (lista, total em centavos, vazio, adicionar, 400, falha, carregar de novo), `AccountForm`, link no dashboard; testes do `AccountsStep` continuam passando.

**Acceptance Criteria:**
- Dado um usuário logado com contas, quando ele abre `/contas`, então vê a lista das contas ativas e o saldo total.
- Dado que ele clica em "Adicionar conta", preenche "Inter" com R$ 500,00 e salva, então a conta aparece na lista, o total soma R$ 500,00 e aparece "Conta adicionada com sucesso".

## Spec Change Log

## Review Triage Log

Revisão em 3 camadas (Blind Hunter, Edge Case Hunter, Verification Gap) — 2026-11-03. Parte da saída do Edge Case Hunter chegou comprimida e o hash expirou; os achados legíveis estão abaixo.

| # | Origem | Achado | Veredito | Evidência | Rota |
|---|---|---|---|---|---|
| 1 | Blind, Edge | Foco cai no `body` ao fechar o diálogo; no estado vazio o botão que abriu some | medium | `Dialog` controlado sem `DialogTrigger` | patch |
| 2 | Blind | Link "Contas" dentro de "Configurações da conta" é ambíguo | low | `DashboardView.vue` nav | patch (rótulo "Contas bancárias") |
| 3 | Blind, Edge | Mensagem de sucesso nunca some | low | Só limpa ao reabrir | patch |
| 4 | Blind | Comentário sobre o bloqueio durante o carregamento da lista apagado | low | Refatoração do `AccountsStep` | patch |
| 5 | Blind | Erro de tipo não chega aos radios (`aria-invalid`) | low | Só o `fieldset` tem `aria-describedby` | patch |
| 6 | VG, Blind | Bloqueio do passo 2 durante o envio sem teste | medium | `adding` agora vem do `v-model:submitting` | patch |
| 7 | VG, Blind | Proteções do diálogo durante o envio sem teste | medium | Nenhum teste com POST pendente | patch |
| 8 | Blind | "Salvando..." e `disabled` do botão sem teste | low | Teste só confere o rótulo em repouso | patch |
| 9 | VG | Rota `/contas` sem teste | low | Nenhuma rota do projeto tem teste | defer |
| 10 | Blind | Conta nova vai para o fim da lista | false | A API ordena da mais antiga para a mais nova | reject |
| 11 | Blind | `ApiError` duplicado | low | Já adiado na retro do Epic 3 (D3) | reject (duplicado) |
| 12 | Blind | Botões empilhados no diálogo | low | Cosmético | reject |
| 13 | Blind | `focus()` depende do `$el` do `Input` | low | Componente atual renderiza o `<input>` na raiz | reject |
| 14 | Edge | Total vira NaN se `currentBalance` vier nulo | false | Coluna `NOT NULL`; API sempre devolve número | reject |
| 15 | Edge | Sair da página durante o POST não confirma a criação | low | A conta aparece ao voltar | reject |
| 16 | Edge | Fechar o diálogo depois de uma falha perde o que foi digitado | low | Fechamento explícito do usuário | reject |

## Verification

**Commands:**
- `cd porquinho-frontend && npx vitest run` -- 0 falhas
- `cd porquinho-frontend && npx oxlint . && npx eslint . && npx vue-tsc --build` -- 0 erros (os mesmos checks do Frontend CI)
- `cd porquinho-backend && ./mvnw test` -- 0 falhas (sem mudanças esperadas; parar o backend de dev antes)

**Manual checks:**
- Abrir `/contas` pelo link do dashboard, adicionar uma conta e conferir lista, total e mensagem; voltar ao dashboard.
