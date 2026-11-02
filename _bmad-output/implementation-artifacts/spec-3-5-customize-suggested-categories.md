---
title: 'Personalizar as categorias sugeridas (passo 5 do onboarding)'
type: 'feature'
created: '2026-11-02'
status: 'done'
baseline_commit: eb2e6adadb9220d553089d01bf9ab16464876b6a
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-3-context.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-3-4-suggest-initial-spending-categories.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** As categorias criadas no passo 4 são genéricas, e hoje não há como ajustá-las. O usuário não consegue renomear, trocar a cor ou o ícone, excluir nem criar as suas. Sem isso o orçamento não reflete a vida dele.

**Approach:** O passo 5 "Personalize suas categorias" lista as categorias do usuário agrupadas sob os grupos. Cada uma pode ter o nome editado no próprio lugar, a cor trocada por uma paleta, o ícone trocado por uma lista de ícones financeiros do Lucide e ser excluída com confirmação. Um botão "Adicionar categoria" cria uma categoria nova dentro de um grupo. Cada mudança é salva na hora pela API de categorias, que serve também fora do onboarding (Epic 6).

## Boundaries & Constraints

**Always:**
- Sem migration nova: a V18 já tem todas as colunas necessárias.
- API (`user_id` sempre do JWT; categoria de outro usuário, inexistente ou removida → 404):
  - `POST /api/v1/categories` `{name, colorHex, iconName, parentId}` → 201, com `is_system_default=false`.
  - `PATCH /api/v1/categories/{id}` com qualquer combinação de `name`, `colorHex` e `iconName` → 200.
  - `DELETE /api/v1/categories/{id}` faz soft delete e devolve 204.
- Validação, com 400 e mensagens em pt-BR:
  - nome obrigatório, com strip Unicode, até 50 caracteres;
  - cor do formato `#RRGGBB` e dentro da paleta permitida;
  - ícone dentro da lista permitida;
  - nome único, sem diferença de maiúsculas e minúsculas, entre todas as categorias ativas do usuário, grupos e filhas juntos (**decisão Luiz, 2026-11-02, B**, no teste manual: "Lazer" não pode virar "Moradia" de outro grupo). Repetido → 409 "Já existe uma categoria com esse nome".
- Hierarquia (resolve o item adiado da 3.4):
  - `parentId` precisa ser um grupo ativo do próprio usuário, ou seja, uma categoria sem pai. Senão, 400. A profundidade máxima é 1.
  - Não dá para mudar o pai pelo PATCH.
  - Excluir a última filha ativa do usuário → 409 "Mantenha ao menos uma categoria", porque o orçamento precisa de ao menos um envelope.
- A paleta (cerca de 12 cores) e a lista de ícones (cerca de 24 nomes Lucide, incluindo todos os do catálogo da 3.4) ficam no backend e são devolvidas em `GET /api/v1/categories/options`. O frontend mapeia os nomes para os componentes em `lib/categoryIcons.ts`.
- Ordem determinística (resolve o item adiado da 3.4): grupos e filhas em ordem alfabética do nome em pt-BR (`localeCompare('pt-BR')` no frontend; ORDER BY nome no backend, com o id como desempate).
- `POST /api/v1/onboarding/me/customization` `{skipped}` só vale com `current_step=5`. Grava `categories_customized` ou `customization_skipped`, avança para `current_step=6` e registra o audit `onboarding_customization_completed` `{skipped}`. Com passo ≥ 6 é idempotente; com passo < 5, 409. Pular não desfaz o que já foi salvo.
- `/onboarding` por passo: 1 a 5 como hoje, e ≥ 6 ou concluído vai para `/dashboard` (decisão B estendida até a 3.6). Concluir as categorias (passo 4) leva ao passo 5. Voltar no passo 5 mostra o passo 4 sem mexer no progresso, e o POST idempotente devolve o passo 5.
- Tela "Passo 5 de 8" com os botões Próximo, Voltar e "Pular personalização".
- O nome é editado no lugar e salvo ao sair do campo ou com Enter; Esc cancela. Uma falha volta ao valor anterior e mostra o erro.
- A cor e o ícone são escolhidos num popover acessível por teclado e salvos ao escolher.
- Excluir pede confirmação no `Dialog`.
- Alvo de toque de no mínimo 44px.
- **Decisão (Luiz, 2026-11-02) A:** os grupos também aceitam o `PATCH` de nome, cor e ícone, com as mesmas validações e a mesma unicidade. Na tela, o título de cada grupo tem a mesma edição no lugar e os mesmos popovers das filhas. Um grupo só pode ser excluído quando não tem filhas ativas; com filhas, 409 "Exclua ou mova as categorias deste grupo antes". Excluir um grupo vazio não conta para a regra "Mantenha ao menos uma categoria", que vale só para as filhas. `POST` com `parentId` nulo responde 400, porque criar grupos fica para o Epic 6.

**Never:**
- Mover uma categoria para outro grupo, reordenar manualmente, alocar orçamento ou excluir em cascata.
- Biblioteca nova (nada de color picker externo), ou editar as migrations V1 a V18.

## I/O & Edge-Case Matrix

| Cenário | Estado | Resultado | Erro |
|---|---|---|---|
| Renomear | filha ativa | 200; o nome novo aparece na lista | — |
| Nome vazio ou repetido | — | 400 ou 409; o campo volta ao valor anterior com a mensagem | 400/409 |
| Trocar a cor ou o ícone | valor da lista | 200 | — |
| Cor ou ícone fora da lista | — | 400 | 400 |
| Adicionar categoria | grupo ativo do usuário | 201, `is_system_default=false` | — |
| Pai inválido | filha, grupo de outro usuário ou inexistente | 400 | 400 |
| Excluir | confirmado, restam outras | 204; some da lista | — |
| Excluir a última filha | 1 filha ativa | 409 | 409 |
| Categoria de outro usuário | — | PATCH/DELETE → 404 | 404 |
| Renomear um grupo | grupo ativo | 200; o nome não pode repetir o de outra categoria ou grupo | 409 |
| Excluir um grupo | com filhas ativas | 409 | 409 |
| Excluir um grupo | sem filhas ativas | 204 | — |
| Criar sem `parentId` | — | 400 | 400 |
| Próximo ou Pular | passo 5 | `categories_customized` ou `customization_skipped`, passo 6, audit, `/dashboard` | — |
| Repetir o POST | passo ≥ 6 | 200, sem mudança e sem audit | — |

</frozen-after-approval>

## Code Map

- `porquinho-backend/.../service/CategoryService.java`, `CategoryController.java`, `repository/CategoryRepository.java` (`findActiveByUserId`, que hoje ordena por `created_at`) e `entity/Category.java` -- estender com criar, atualizar e remover. Trocar a ordenação por nome e id.
- `porquinho-backend/.../service/CategorySuggestions.java` -- os ícones e as cores do catálogo precisam estar contidos na paleta e na lista de opções. Criar `CategoryOptions` (ou constantes no mesmo arquivo).
- `porquinho-backend/.../dto/CreateAccountRequest.java` -- padrão de strip Unicode e mensagens. `exception/ConflictException`, `BadRequestException` e `GlobalExceptionHandler` já existem.
- `porquinho-backend/.../service/OnboardingService.java` -- `completeCategories`/`applyCategories` como base de `completeCustomization`. `OnboardingController.java` -- novo `POST /me/customization`.
- `porquinho-frontend/src/components/onboarding/CategoriesStep.vue` -- cards, skeleton e `finish()`. `src/lib/categoryIcons.ts` -- mapa de ícones; acrescentar os novos. `components/ui/dialog` -- confirmação de exclusão. Não há componente de popover instalado: usar um `<div role="dialog">` simples ou o `Dialog`.
- `porquinho-frontend/src/views/OnboardingView.vue` -- escolha de tela por passo; acrescentar o passo 5. `services/categoryService.ts` e `onboardingService.ts` -- acrescentar as chamadas.
- Testes modelo: `CategoryOnboardingIntegrationTest`, `CategoryControllerTest`, `CategoriesStep.spec.ts`.

## Tasks & Acceptance

**Execution:**
- [x] Backend -- opções (paleta e ícones), criar, atualizar e remover, regras de hierarquia, unicidade e mínimo de 1, ordenação por nome.
- [x] `OnboardingService.java`, `OnboardingController.java` -- `completeCustomization` e `POST /me/customization`.
- [x] Frontend -- `categoryService.ts`, `CustomizeCategoriesStep.vue` (edição no lugar, popovers, exclusão com confirmação, adicionar), passo 5 no `OnboardingView.vue`, passo 4 → passo 5.
- [x] Testes -- matriz no backend (serviço, controller e integração com PostgreSQL) e Vitest do passo 5 e do serviço.

**Acceptance Criteria:**
- Dado um usuário que concluiu o passo 4, quando ele abre `/onboarding`, então vê "Passo 5 de 8", "Personalize suas categorias" e as categorias dele agrupadas e em ordem alfabética.
- Dado que ele renomeia "Lazer" para "Diversão", troca a cor de Moradia e adiciona "Pets" em Essenciais, quando clica em Próximo, então essas mudanças estão no banco, o progresso fica no passo 6 com `categories_customized` e ele é levado ao `/dashboard`.

## Spec Change Log

- 2026-11-02 — teste manual: nome repetido em outro grupo era aceito ("Lazer" → "Moradia"). Luiz escolheu B: unicidade entre todas as categorias ativas do usuário. Ajustados `CategoryService.ensureUniqueName` e os testes de integração.

## Review Triage Log

Revisão em 3 camadas (Blind Hunter, Edge Case Hunter, Verification Gap) — 2026-11-02.

| # | Origem | Achado | Veredito | Evidência | Rota |
|---|---|---|---|---|---|
| 1 | Blind, Edge, VG | Unicidade de nome e regra da última filha sem serialização: requisições simultâneas gravam duplicatas ou zeram as filhas | medium | Checagens em memória após leitura sem lock; correção com `PESSIMISTIC_WRITE` nas categorias do usuário, sem migration | patch |
| 2 | Blind, Edge | Nome só com U+200B/U+FEFF aceito; NFC e NFD passam como nomes diferentes | low | `cleanName` só remove `\s`/`\p{Z}`; `equalsIgnoreCase` sem normalização | patch |
| 3 | Blind, Edge | Cliques rápidos no seletor disparam PATCHes cuja resposta fora de ordem sobrescreve a tela | low | `choose` sem guarda de requisição pendente | patch |
| 4 | Blind | Watch de `categoryName` apaga texto digitado durante o salvamento | low | `draft.value = name` sem checar foco | patch |
| 5 | Blind | Rename que falha some ao clicar em Próximo | medium | `track` engole a rejeição e `finish` avança | patch |
| 6 | Edge | Formulário de adicionar ativo durante `finish` | low | Criação fora do snapshot de pendentes | patch |
| 7 | Edge | DELETE com 404 deixa item fantasma | low | `confirmDelete` só mostra erro | patch |
| 8 | Blind | Cores sem nome para leitor de tela; popover não fecha ao sair com Tab | low | Rótulo "Cor N de M"; só `pointerdown` e Esc fecham | patch |
| 9 | Blind, Edge | Voltar ao passo 4 mostra checkboxes que não têm efeito | medium | POST idempotente ignora a nova seleção | patch (modo somente leitura) |
| 10 | VG | Mapa de ícones do front sem teste contra `CategoryOptions.ICONS` | medium | Nenhum spec de `categoryIcons.ts`; fallback `Tag` silencioso | patch |
| 11 | Blind | "Saude" e "Saúde" convivem | low | Nomes distintos são aceitáveis; a intenção pede só sem diferença de maiúsculas | reject |
| 12 | Blind | Texto digitado em "Adicionar" se perde ao avançar sem confirmar | low | Padrão comum de formulário; o usuário vê o campo aberto | reject |
| 13 | Blind | Create, update e delete de categoria sem audit; DELETE sem gancho para lançamentos futuros | low | Mesmo veredito da 3.2/3.3; referências chegam no Epic 6 | reject |
| 14 | Blind | Próximo e Pular habilitados com `loadFailed` | low | Avançar não perde dados; as categorias continuam salvas | reject |
| 15 | Blind | Rótulos "Formatura"/"Talheres" diferem do nome da categoria | low | Rótulo descreve o ícone, não a categoria | reject |
| 16 | Edge | Ícone ou cor do grupo fora da lista bloqueia a criação | low | Teste garante que o catálogo cabe nas opções; grupos novos só no Epic 6 | reject |
| 17 | Blind | Ordem da API depende do collation do banco | low | A tela reordena com `localeCompare('pt-BR')` | reject |

## Verification

**Commands:**
- `cd porquinho-backend && ./mvnw test` -- 0 falhas. Antes, parar o backend de dev (`lsof -ti :8080 | xargs kill`) e conferir que a porta ficou livre.
- `cd porquinho-frontend && npx vitest run && npx vue-tsc --noEmit -p tsconfig.app.json` -- 0 falhas

**Manual checks:**
- Com o usuário de teste (hoje no passo 5): renomear, trocar cor e ícone, adicionar, excluir, avançar.
