---
title: 'Story 6.1: criar categorias de gasto personalizadas'
type: 'feature'
created: '2026-11-09'
status: 'done'
baseline_commit: 'f86fbe9ebc05b34ee7a6627135c0da42a53ad7a0'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-6-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Fora do onboarding (passo 5) o usuário não tem onde ver nem criar categorias. A API de criação (Story 3.5) exige cor, ícone e grupo, e o épico pede cor e ícone opcionais.

**Approach:** Criar a página `/categorias` com a lista de grupos e categorias e um modal "Adicionar categoria" (nome e grupo obrigatórios; cor e ícone opcionais). Estender o `POST /api/v1/categories` atual para aplicar os padrões quando cor ou ícone não vierem.

## Boundaries & Constraints

**Always:** usar o endpoint, as validações e as mensagens de `CategoryService` que já existem (nome limpo com `cleanName`, até 50 caracteres, único por usuário sem diferenciar maiúsculas, trava `findActiveByUserIdForUpdate`). Usuário sempre vindo do JWT. Erros em RFC 7807. Ícone padrão `folder`. A cor padrão é a do grupo escolhido, que já está na paleta. `folder` entra em `CategoryOptions.ICONS` e em `categoryIcons.ts` (com o teste de sincronia). Sem limite de quantidade. Toast "Categoria criada" ao salvar. Os seletores de classificação continuam lendo `GET /api/v1/categories` ao abrir a tela, então uma categoria filha nova já aparece neles.

**Never:** sem migration nova. Sem editar, excluir nem mover categorias nesta página: editar e excluir já existem no onboarding, e mover/hierarquia é da 6.2. Não mudar `requireChildCategory`, ou seja, transações continuam aceitando só categoria filha. Não tocar no onboarding além de reaproveitar código.

**Decisão (Luiz, 2026-11-09):** o grupo é obrigatório no modal, com o primeiro grupo (em ordem de nome) já selecionado. Toda categoria criada aqui é filha e aparece nos seletores de classificação. Criar grupo novo fica para a 6.2.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Só nome e grupo | `{name:"Pets", parentId:G}` | 201; cor = cor de G, ícone `folder`, `systemDefault=false` | N/A |
| Tudo informado | nome, cor e ícone da paleta, grupo | 201 com os valores enviados (cor em maiúsculas) | N/A |
| Nome repetido | "pets" com "Pets" ativa | 409 `DUPLICATE_NAME_MESSAGE`, exibida no campo nome | modal continua aberto |
| Nome vazio ou só espaços | `"  "` | 400 `NAME_REQUIRED_MESSAGE`; o frontend bloqueia antes | idem |
| Cor ou ícone fora da lista | `colorHex:"#000000"` | 400 com a mensagem atual | idem |
| Sem grupo | `parentId` ausente | 400 `PARENT_REQUIRED_MESSAGE` (comportamento atual) | idem |
| Grupo de outro usuário, removido ou filho | `parentId` inválido | 400 `PARENT_INVALID_MESSAGE` | idem |

</frozen-after-approval>

## Code Map

- `porquinho-backend/.../dto/CreateCategoryRequest.java` -- hoje `@NotBlank` em `colorHex` e `iconName`; passam a ser opcionais (os `@AssertTrue` já aceitam nulo ou vazio).
- `porquinho-backend/.../service/CategoryService.java#create` -- valida cor e ícone só quando vierem; o padrão é `parent.getColorHex()` e `CategoryOptions.DEFAULT_ICON`.
- `porquinho-backend/.../service/CategoryOptions.java` -- `ICONS` + `"folder"`, constante `DEFAULT_ICON`.
- `porquinho-backend/src/test/.../CategoryControllerTest.java`, `CategoryCustomizationIntegrationTest.java` -- testes de create que hoje esperam 400 sem cor ou ícone devem ser ajustados.
- `porquinho-frontend/src/lib/categoryIcons.ts` + `lib/__tests__/categoryIcons.spec.ts` -- adicionar `folder: Folder`.
- `porquinho-frontend/src/types/category.ts` -- `colorHex?` e `iconName?` em `CreateCategoryInput`.
- `porquinho-frontend/src/components/onboarding/CategoryItem.vue` -- já tem o seletor de cor e ícone. Reaproveitar o markup ou extrair um componente se ficar menor.
- `porquinho-frontend/src/components/onboarding/CustomizeCategoriesStep.vue` -- agrupamento `groups` (pai → filhas por nome), carregamento, retry e tratamento de `apiDetail`. Reaproveitar o padrão.
- `porquinho-frontend/src/components/ui/dialog/` -- modal.
- `porquinho-frontend/src/router/index.ts`, `views/DashboardView.vue` -- rota autenticada e link, como `/transacoes`.

## Tasks & Acceptance

**Execution:**
- [x] `CategoryOptions.java` -- incluir `folder` e `DEFAULT_ICON` -- ícone padrão do épico.
- [x] `CreateCategoryRequest.java`, `CategoryService.java` -- cor e ícone opcionais com os padrões; atualizar o javadoc -- AC de padrões.
- [x] Testes backend (controller + integração) -- cobrir a matriz e ajustar os casos antigos.
- [x] `categoryIcons.ts` (+ spec), `types/category.ts` -- sincronia de ícones e tipo opcional.
- [x] `views/CategoriesView.vue` (novo) -- lista agrupada (cor, ícone e nome), botão "Adicionar categoria", modal com nome, cor ("Automática" por padrão), ícone ("Padrão" por padrão) e grupo (obrigatório, primeiro grupo pré-selecionado). Erros da API exibidos no modal, toast ao salvar, foco devolvido ao botão ao fechar.
- [x] `router/index.ts`, `DashboardView.vue` -- rota `/categorias` e link "Categorias".
- [x] `views/__tests__/CategoriesView.spec.ts` -- lista, criação com e sem cor/ícone, 409 no campo nome, nome vazio bloqueado.

**Acceptance Criteria:**
- Given estou em `/categorias`, when crio uma categoria, then ela aparece na lista no seu grupo sem recarregar a página, e o modal fecha com o toast "Categoria criada".
- Given criei uma categoria, when abro `/transacoes` ou a revisão de importação, then ela está nos seletores de categoria.
- Given o modal aberto, when navego só pelo teclado, then todos os campos e opções são alcançáveis, cada seletor tem rótulo e o erro é anunciado (`role="alert"`).

## Implementation Notes

## Spec Change Log

## Review Triage Log

| # | Layer | Finding | Verdict | Evidence | Route |
|---|-------|---------|---------|----------|-------|
| 1 | blind, edge (×2), vgap-other | Sem grupos: botão abre modal com grupo vazio, POST com `parentId ''` e página em branco | medium | O router só tem guard de auth, então um usuário antes do passo 4 do onboarding chega a `/categorias` com zero grupos | patch |
| 2 | blind | Erro vai para o campo nome via `detail.includes('nome')` | low | Real, mas só falha se as mensagens mudarem; a correção pede um contrato novo (campo no RFC 7807) | rejeitado (low, não é correção direta) |
| 3 | blind | "Padrão" e "Pasta" na grade de ícones dão o mesmo resultado | low | `folder` está em `options.icons` e o modal lista todos; correção é remover da grade | patch |
| 4 | blind | DialogDescription não cita o ícone padrão | low | O texto só fala da cor | patch |
| 5 | blind | Cor do grupo herdada sem validar pode ser nula ou fora da paleta | false | `color_hex` é NOT NULL com CHECK de formato; grupos vêm do catálogo (cores na paleta) e o update valida a paleta | rejeitado |
| 6 | blind | Faltam testes de controller para cor/ícone em branco e inválidos | false | Branco é coberto no teste de integração (`" "`, `""`); inválido continua em `createWithInvalidFieldsIs400WithoutCallingTheService` | rejeitado |
| 7 | blind | Faltam testes de foco, de modal travado durante o POST e de voltar ao padrão | low | Comportamentos existem no código; ausência de teste não quebra uso comum | rejeitado (low) |
| 8 | blind | 400 de grupo removido em outra aba não recarrega a lista | low | Mesmo padrão do onboarding; raro | rejeitado (low) |
| 9 | blind | `byName`/`groups`/`ApiError` copiados do onboarding | low | Duplicação real, já rastreada como D5/item 6 da retro do Epic 5; extração vai além de correção direta | rejeitado (low) |
| 10 | blind | `saving.value = false` redundante em `save()` | low | O `finally` já zera; deleção direta | patch |
| 11 | blind | Mensagem inline em vez de toast | false | O projeto não tem componente de toast; `AccountsView.vue:22` usa o mesmo `<p role="status">` | rejeitado |
| 12 | edge | Timer de sucesso criado depois do unmount | low | Só se sair da página durante o POST; timer de 5 s inofensivo | rejeitado (low) |
| 13 | edge | 5xx sem `detail` mostra "Verifique sua conexão" | low | Mesmo texto genérico de outras telas; raro | rejeitado (low) |
| 14 | vgap | Link `categories-link` do Dashboard sem teste | gap | `DashboardView.spec.ts` testa cada link de navegação, menos este | patch |
| 15 | vgap | Rota `/categorias` e `requiresAuth` sem teste | gap | `router/__tests__/index.spec.ts` só cobre a rota da 5.9 | patch |

## Verification

**Commands:**
- `cd porquinho-backend && ./mvnw test -Dtest='Category*'` -- expected: verde
- `cd porquinho-frontend && npm run lint && npx vue-tsc --build && npx vitest run` -- expected: sem erros
