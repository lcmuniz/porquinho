---
title: 'Sugerir categorias de gastos iniciais (passo 4 do onboarding)'
type: 'feature'
created: '2026-11-02'
status: 'done'
baseline_commit: f6c3aa1e38d2f0d6eddae59c30361021731938fe
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-3-context.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-3-3-add-credit-cards-during-setup.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Depois dos cartões (passo 3), o usuário ainda não tem nenhuma categoria (envelope). Sem categorias não existe orçamento, e criar tudo do zero afasta quem está começando. A tabela `categories` também não existe, e os Epics 5 (classificação) e 6 (orçamento) dependem dela.

**Approach:** Criar a tabela `categories`, com hierarquia via `parent_category_id` e `is_system_default`, e o passo 4 "Categorias de gastos sugeridas". O passo mostra 10 sugestões em 3 grupos, todas marcadas, cada uma em um card com checkbox, nome, ícone e cor. Ao clicar em Próximo, as marcadas são criadas para o usuário e o progresso avança. Personalizar fica para a Story 3.5.

## Boundaries & Constraints

**Always:**
- Migration **V18** `categories`: `id` UUID PK, `user_id` FK indexada, `category_name` VARCHAR(50) NOT NULL, `parent_category_id` UUID FK → `categories(id)` NULL, `color_hex` VARCHAR(7) NOT NULL, `icon_name` VARCHAR(50) NOT NULL, `is_system_default` BOOLEAN NOT NULL DEFAULT false, `created_at`, `updated_at`, `deleted_at`.
- Sugestões fixas (código do backend, única fonte), cada uma com uma chave estável (`key`), nome, ícone Lucide e cor `#RRGGBB` de uma paleta. **Essenciais:** Moradia, Alimentação, Transporte, Saúde. **Variáveis:** Lazer, Educação, Vestuário, Assinaturas. **Financeiro:** Investimentos, Reserva de Emergência.
- `GET /api/v1/categories/suggestions` devolve os grupos e as sugestões. `GET /api/v1/categories` lista as categorias ativas do usuário. `user_id` sempre vem do JWT.
- `POST /api/v1/onboarding/me/categories` `{keys: [...]}` só vale com `current_step=4`. Exige ao menos 1 chave válida (400 se vazio ou com chave desconhecida, ignorando repetidas). Cria as categorias escolhidas com `is_system_default=true`, grava `categories_suggested`, avança para `current_step=5` e registra o audit `onboarding_categories_completed` `{count}` (filhas criadas). Tudo na mesma transação. Com passo ≥ 5 é idempotente: devolve 200 e não cria nada. Com passo < 4, responde 409.
- `/onboarding` por passo: 1 tutorial, 2 contas, 3 cartões, 4 categorias, ≥ 5 ou concluído → `/dashboard` (decisão B estendida até a Story 3.5). Concluir os cartões leva ao passo 4. Voltar no passo 4 mostra os cartões sem mexer no progresso.
- Tela "Passo 4 de 8": sem botão Pular (o mínimo é 1). Próximo fica desabilitado sem nenhuma marcada, com a dica "Escolha ao menos uma categoria". Cards acessíveis: checkbox nativo com label, navegável por teclado, alvo de no mínimo 44px. Ícones do `lucide-vue-next`, mapeados por nome.
- **Decisão (Luiz, 2026-11-02) A:** os 3 grupos viram categorias-pai no banco (`parent_category_id` nulo, `is_system_default=true`, com ícone e cor próprios definidos no catálogo). As escolhidas são criadas como filhas do seu grupo. Um grupo só é criado se tiver ao menos uma filha marcada. A tela mostra os grupos como títulos, sem checkbox próprio.
- LGPD: `categories` no `purgeUser` (antes do usuário; a FK para o pai exige apagar todas as linhas do usuário juntas) e no export (`assertExportsEveryColumn`).

**Never:**
- Renomear, trocar cor ou ícone, criar categoria livre ou excluir (Story 3.5 e Epic 6). Alocação de orçamento ou saldo.
- Biblioteca nova, ou editar migrations aplicadas.

## I/O & Edge-Case Matrix

| Cenário | Estado | Resultado | Erro |
|---|---|---|---|
| Todas marcadas | passo 4 | 3 grupos + 10 filhas criados, `categories_suggested`, passo 5, audit, `/dashboard` | — |
| Algumas desmarcadas | passo 4, Moradia e Lazer | grupos Essenciais e Variáveis + 2 filhas; Financeiro não é criado | — |
| Nenhuma marcada | — | Próximo desabilitado; a API responde 400 | 400 |
| Chave desconhecida | `keys: ["xyz"]` | 400, nada criado | 400 |
| Repetir o POST | passo ≥ 5 | 200, nada criado de novo, sem audit | — |
| Passo < 4 | — | 409 | 409 |
| Categorias de outro usuário | — | nunca listadas | — |

</frozen-after-approval>

## Code Map

- Stories 3.2 e 3.3 (`git show 9492e40`, `git show f6c3aa1`) -- modelo de ponta a ponta: entidade, repositório, serviço, controller, DTO com validação e mensagens em pt-BR, `CardsStep.vue`/`AccountsStep.vue`, escolha de tela no `OnboardingView.vue`, `onboardingService.ts`.
- `porquinho-backend/.../service/OnboardingService.java` -- `completeCards`/`applyCards` (proxy `self`, `findByUserIdForUpdate`, audit, idempotência por passo) como base de `completeCategories`.
- `porquinho-backend/.../service/AccountDeletionService.java` (`purgeUser`) e `DataExportService.java` -- acrescentar `categories`. Purge com SQL nativo `DELETE FROM categories WHERE user_id = ?` (uma instrução só, então a FK para o pai não bloqueia).
- `porquinho-frontend/src/components/onboarding/CardsStep.vue` -- padrão de `finish()` (guard de `busy`, `finishing` só zerado no erro, `apiDetail`), skeleton e `emit('error')`. Criar `CategoriesStep.vue`.
- `porquinho-frontend/src/components/onboarding/OnboardingStepIndicator.vue` -- `:step="4"`.

## Tasks & Acceptance

**Execution:**
- [x] `V18__create_categories_table.sql` -- tabela, FKs, índice e comentários.
- [x] Backend categorias -- entidade, repositório, catálogo de sugestões, serviço e controller (`GET /categories`, `GET /categories/suggestions`).
- [x] `OnboardingService.java`, `OnboardingController.java` -- `completeCategories` e `POST /me/categories`.
- [x] `AccountDeletionService.java`, `DataExportService.java` -- purge e export.
- [x] Frontend -- `categoryService.ts`, `completeCategories`, `CategoriesStep.vue`, passo 4 no `OnboardingView.vue`, cartões → passo 4.
- [x] Testes -- matriz no backend (serviço, controller e integração com PostgreSQL) e Vitest do `CategoriesStep`, do serviço e do fluxo na view.

**Acceptance Criteria:**
- Dado um usuário que concluiu os cartões, quando ele abre `/onboarding`, então vê "Passo 4 de 8", "Categorias de gastos sugeridas" e as 10 sugestões marcadas nos 3 grupos.
- Dado que ele desmarca Vestuário e Assinaturas, quando clica em Próximo, então existem 3 categorias-pai e 8 filhas dele, todas com `is_system_default=true`, o progresso fica no passo 5 com `categories_suggested` e ele é levado ao `/dashboard`.

## Spec Change Log

## Review Triage Log

Revisão em 3 camadas (Blind Hunter, Edge Case Hunter, Verification Gap) — 2026-11-02.

| # | Origem | Achado | Veredito | Evidência | Rota |
|---|---|---|---|---|---|
| 1 | Blind | Schema aceita categoria pai de si mesma e qualquer texto em `color_hex` | low | V18 ainda não aplicada; CHECK é correção direta | patch |
| 2 | Blind, Edge | `keys` sem limite de tamanho | low | Só `@NotEmpty`; `@Size` é correção direta | patch |
| 3 | Blind | Falha ao carregar sugestões aparece no toast e no bloco inline | low | `loadSuggestions` emite `error` e mostra o bloco; remover o emit | patch |
| 4 | Blind | `CategorySuggestions.groupOf` sem uso | low | Código morto; deletar | patch |
| 5 | Blind, Edge | Ordem de `GET /categories` não determinística (pai antes das filhas, ordem do catálogo) | medium | `created_at` de chamadas separadas com desempate por UUID; nenhum consumidor nesta story | defer |
| 6 | Blind | Hierarquia sem garantia de um nível, de mesmo usuário e de soft delete do pai | medium | Só o catálogo cria pais nesta story; a edição chega na 3.5 | defer |
| 7 | VG | Handler HTTP de `BadRequestException` sem teste | low | Caminho defensivo; o DTO cobre os casos atuais | defer |
| 8 | VG, Blind | Lock contra criação duplicada sem teste concorrente | low | Mesmo item já adiado na 3.1 | reject (duplicado) |
| 9 | Blind | Seleção do passo 4 se perde ao Voltar e avançar | low | Raro; corrigir exige estado no pai | reject |
| 10 | Blind | Validação das chaves em três lugares | low | Comportamento correto; só redundância | reject |
| 11 | Blind | Duplicatas se `createFromSuggestions` for chamado fora do onboarding | low | Único chamador é guardado pelo passo | reject |
| 12 | Blind | Mapa de ícones do front pode divergir do catálogo | low | Fallback `Tag` visível; sem dano | reject |
| 13 | Blind | Audit guarda só `count` | low | Intenção pede `{count}` | reject |
| 14 | Blind | `@GenericGenerator` deprecado | low | Mesmo padrão das outras entidades | reject |
| 15 | Blind | Sem testes de 401 e do filtro de assinatura nos endpoints novos | low | Coberto pela configuração de segurança global | reject |
| 16 | Edge | Sugestões vazias deixam o usuário preso | false | Catálogo fixo no backend nunca vem vazio | reject |
| 17 | Edge | Navegação que falha após o POST deixa os botões travados | low | Exige falha do router depois de salvar | reject |
| 18 | Edge | 409 em aba antiga deixa o usuário só com o toast | low | Mesmo veredito da 3.3 | reject |

## Verification

**Commands:**
- `cd porquinho-backend && ./mvnw test` -- 0 falhas. Antes, parar o backend de dev (`lsof -ti :8080 | xargs kill`) e conferir que a porta ficou livre, para o devtools não aplicar uma V18 intermediária no Supabase.
- `cd porquinho-frontend && npx vitest run && npx vue-tsc --noEmit -p tsconfig.app.json` -- 0 falhas

**Manual checks:**
- Com o usuário de teste (hoje no passo 4): `/onboarding` mostra o passo 4; desmarcar algumas, avançar e conferir as linhas no Supabase de dev.
