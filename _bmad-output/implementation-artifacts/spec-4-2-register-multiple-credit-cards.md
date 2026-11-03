---
title: 'Página de cartões de crédito com cadastro de novos cartões'
type: 'feature'
created: '2026-11-03'
status: 'done'
baseline_commit: 3098198764767588ed1c65f3f5eb6b6ab3a4bc19
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/spec-4-1-register-multiple-bank-accounts.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-3-3-add-credit-cards-during-setup.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Cartões de crédito só podem ser cadastrados no passo 3 do onboarding. Depois dele, o usuário não tem onde ver os cartões nem cadastrar um novo.

**Approach:** Repetir o desenho da Story 4.1 para cartões: página `/cartoes` com a lista dos cartões ativos, o limite total e o botão "Adicionar cartão", que abre um diálogo com o mesmo formulário do passo 3. A API `/api/v1/credit-cards` da Story 3.3 já faz tudo; esta story é só de frontend.

## Boundaries & Constraints

**Always:**
- Sem mudança no backend nem migration. Usar `GET` e `POST /api/v1/credit-cards` como estão.
- Rota `/cartoes` (`requiresAuth`), view `CardsView.vue`, título "Cartões de crédito". O dashboard ganha o link "Cartões de crédito" ao lado de "Contas bancárias".
- Lista: nome, banco, limite em R$ e "vence dia X", na ordem da API. Estado vazio: "Você ainda não tem cartões cadastrados" com o botão de adicionar.
- Limite total: soma dos `cardLimit` dos cartões ativos, em centavos inteiros, exibida em R$. É a base do crédito disponível, que depende das faturas (Epic 5); não calcular crédito disponível aqui.
- "Adicionar cartão" abre um `Dialog` com os campos nome, banco (com `<datalist>` de `lib/banks.ts`), limite no formato brasileiro e dia de vencimento (1–31). Salvar chama `POST /credit-cards`; o cartão aparece na lista e no total sem recarregar, o diálogo fecha e aparece "Cartão adicionado com sucesso" (`role="status"`, some depois de alguns segundos ou ao recarregar a lista).
- Erros, foco, travas do diálogo durante o envio e acessibilidade iguais aos da Story 4.1 (incluindo os ajustes da revisão dela): 400 no campo; outra falha no diálogo aberto com os dados; falha ao carregar com "Tentar de novo"; foco ao fechar num alvo estável.
- O formulário de cartão é extraído do `CardsStep.vue` para `components/cards/CardForm.vue`, no mesmo formato do `AccountForm.vue`, usado pelo passo 3 e pelo diálogo. O comportamento e os testes do passo 3 continuam iguais.

**Never:**
- Editar, remover ou desativar cartões (4.3 e 4.4), fatura, dia de fechamento ou crédito disponível.
- Menu ou layout global novo; biblioteca nova.

## I/O & Edge-Case Matrix

| Cenário | Estado | Resultado | Erro |
|---|---|---|---|
| Abrir `/cartoes` | 2 cartões | lista com os 2 e o limite total | — |
| Abrir `/cartoes` | nenhum cartão | estado vazio com o botão | — |
| Adicionar cartão válido | — | 201; aparece na lista, total atualizado, diálogo fecha, mensagem | — |
| Dia 0 ou 32, limite inválido, nome vazio | — | erro no campo; diálogo aberto | 400 |
| Falha de rede ao salvar | — | mensagem no diálogo; dados preservados | 5xx |
| Soma com centavos | 0,10 + 0,20 | R$ 0,30 | — |
| Falha ao carregar | — | aviso e "Tentar de novo" | — |

</frozen-after-approval>

## Code Map

- `porquinho-frontend/src/views/AccountsView.vue`, `components/accounts/AccountForm.vue` e seus testes -- modelo direto (Story 4.1).
- `porquinho-frontend/src/components/onboarding/CardsStep.vue` -- formulário atual; extrair para `components/cards/CardForm.vue`.
- `porquinho-frontend/src/services/cardService.ts` -- `listCards`, `createCard`; reutilizar.
- `porquinho-frontend/src/router/index.ts`, `views/DashboardView.vue` -- rota e link.
- Testes modelo: `AccountsView.spec.ts`, `AccountForm.spec.ts`, `CardsStep.spec.ts`, `DashboardView.spec.ts`.

## Tasks & Acceptance

**Execution:**
- [x] `CardForm.vue` extraído; `CardsStep.vue` passa a usá-lo sem mudar comportamento.
- [x] `CardsView.vue` -- lista, limite total, estado vazio, diálogo, mensagens.
- [x] Rota `/cartoes` e link no dashboard.
- [x] Testes Vitest -- view (mesma cobertura do `AccountsView.spec.ts`), `CardForm`, link; testes do `CardsStep` continuam passando.

**Acceptance Criteria:**
- Dado um usuário logado com cartões, quando ele abre `/cartoes`, então vê a lista dos cartões ativos e o limite total.
- Dado que ele adiciona "Inter Gold" com limite R$ 3.000,00 e vencimento dia 15, então o cartão aparece na lista com "vence dia 15", o total soma R$ 3.000,00 e aparece "Cartão adicionado com sucesso".

## Spec Change Log

## Review Triage Log

Revisão em 3 camadas (Blind Hunter, Edge Case Hunter, Verification Gap) — 2026-11-03. Os patches 1 a 4 também valem para `AccountForm`/`AccountsView` (Story 4.1), para os dois continuarem iguais.

| # | Origem | Achado | Veredito | Evidência | Rota |
|---|---|---|---|---|---|
| 1 | Blind | Região `status` só entra no DOM com o texto; leitores de tela podem não anunciar | low | `<p v-if="successMessage" role="status">` | patch (4.1 e 4.2) |
| 2 | Blind | Alerta antigo do diálogo continua junto com erros de validação local | low | `dialogError` só limpa quando `saving` muda | patch (4.1 e 4.2) |
| 3 | Edge | Dois envios no mesmo tick passam pelo guard do `defineModel` | low | Valor local só atualiza após render do pai | patch (4.1 e 4.2) |
| 4 | Edge | 400 com campos conhecidos e `detail` geral perde a mensagem | low | `failed` só sai sem chave conhecida | patch (4.1 e 4.2) |
| 5 | VG, Blind | Bloqueio do passo 3 durante o envio sem teste | medium | `adding` vem do `v-model:submitting` | patch |
| 6 | Blind | Ramos de validação do `CardForm` sem teste | low | Nome/banco longos, limite vazio/negativo, 400 só com chaves desconhecidas | patch |
| 7 | Blind | Clique fora e reabrir após Cancelar sem teste | low | Outro caminho de `onDialogOpenChange` | patch |
| 8 | Blind | IDs fixos no formulário | low | Uma instância por página | reject (mesmo veredito da 4.1) |
| 9 | Blind | `focus()` depende do `$el` do `Input` | low | Mesmo veredito da 4.1 | reject |
| 10 | Blind | Cartão novo no fim da lista | false | API ordena do mais antigo para o mais novo | reject |
| 11 | Blind, VG | Rota `/cartoes` sem teste | low | Nenhuma rota tem teste | defer (com o item da 4.1) |
| 12 | Blind | Sem foco no primeiro campo inválido | low | Mesmo padrão dos passos anteriores | reject |
| 13 | Blind | Regra do dia 31 em meses curtos | low | Regra de fatura do Epic 5 | reject |
| 14 | Blind | Teste dos 5 s indireto | low | Funciona; fake timers no meio do teste | reject |
| 15 | Edge | Campos de 44px mudam a altura no passo 3 | low | Mudança intencional (alvo de toque); mesma da 4.1 | reject |

## Verification

**Commands:**
- `cd porquinho-frontend && npx vitest run` -- 0 falhas
- `cd porquinho-frontend && npx oxlint . && npx eslint . && npx vue-tsc --build` -- 0 erros (checks do Frontend CI)

**Manual checks:**
- Abrir `/cartoes` pelo link do dashboard, adicionar um cartão e conferir lista, total e mensagem.
