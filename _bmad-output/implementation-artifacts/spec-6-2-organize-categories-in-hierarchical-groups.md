---
title: 'Story 6.2: organizar categorias em grupos'
type: 'feature'
created: '2026-11-09'
status: 'done'
baseline_commit: '72b640199d28e5a2c5f5bca52ab58933b2e3d023'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-6-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Em `/categorias` o usuário só cria categorias dentro dos grupos que vieram do onboarding. Ele não consegue criar um grupo novo, mover uma categoria para outro grupo nem recolher grupos na lista.

**Approach:** Estender os endpoints atuais: `POST /api/v1/categories` sem `parentId` cria um grupo, e `PATCH /api/v1/categories/{id}` aceita `parentId` para mover uma categoria. Na página, o modal ganha a opção "Nenhum (criar como grupo)", cada categoria ganha uma ação de mover e cada grupo pode ser recolhido e expandido.

## Boundaries & Constraints

**Always:**
- Hierarquia de 1 nível, ou seja, grupo → categoria (decisão do Luiz). O "até 3 níveis" do épico fica fora.
- Só categorias filhas podem ser movidas, e o destino tem de ser um grupo ativo do mesmo usuário. Um grupo nunca ganha pai. Com isso, autorreferência e ciclo ficam impossíveis.
- Ao mover, a categoria pega a cor do novo grupo só quando a cor atual é igual à do grupo antigo, e no mesmo PATCH não veio `colorHex` (decisão do Luiz). Uma cor escolhida à mão é preservada. Sem coluna nova.
- Reaproveitar a trava `findActiveByUserIdForUpdate`, `cleanName`, a regra de nome único e as mensagens de `CategoryService`. Usuário vem do JWT e erros seguem RFC 7807.
- Grupo criado sem cor recebe a primeira cor da paleta que nenhum grupo ativo usa (se todas estiverem em uso, a primeira da paleta). Sem ícone, recebe `folder`. Vale `systemDefault=false`.
- Lista: grupos começam expandidos. O botão do cabeçalho tem `aria-expanded` e `aria-controls`. As filhas aparecem indentadas.

**Decisões (Luiz, 2026-11-09):**
- Para mover, cada categoria tem um botão "Mover" que abre um modal pequeno com o seletor de grupo e o botão "Salvar". O foco volta para o botão ao fechar.
- Transações continuam aceitando só categorias filhas. `requireChildCategory`, os seletores e o classificador não mudam.

**Never:** sem migration. Alocação no grupo e totais acumulados ficam para 6.3/6.4/6.9, e o dashboard para o Epic 7. Esta página não ganha editar nem excluir nome, cor ou ícone. Não mexer no onboarding.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Criar grupo | POST `{name:"Casa"}` | 201, `parentCategoryId=null`, cor = primeira livre, ícone `folder` | N/A |
| Grupo com nome repetido | "casa" com "Casa" ativa | 409 `DUPLICATE_NAME_MESSAGE` | modal aberto |
| Mover com cor herdada | filha com cor = cor de G1, PATCH `{parentId:G2}` | 200, pai G2, cor = cor de G2 | N/A |
| Mover com cor própria | cor ≠ cor de G1 | 200, cor mantida | N/A |
| Mover e trocar cor juntos | `{parentId:G2, colorHex:X}` | cor X | N/A |
| Mover para o mesmo grupo | `parentId` = pai atual | 200 sem mudança | N/A |
| Mover um grupo | id de grupo + `parentId` | 400 `GROUP_CANNOT_MOVE_MESSAGE` ("Grupos não podem ficar dentro de outro grupo") | N/A |
| Destino inválido | outro usuário, removido, filha ou o próprio id | 400 `PARENT_INVALID_MESSAGE` | N/A |
| Mover a última filha do grupo | grupo fica vazio | 200; o grupo vazio continua na lista | N/A |

</frozen-after-approval>

## Code Map

- `porquinho-backend/.../service/CategoryService.java` -- `create`: `parentId` nulo cria grupo (hoje lança `PARENT_REQUIRED_MESSAGE`). `update`: novo parâmetro `parentId` com a regra de cor. Atualizar o javadoc da classe ("moving and creating groups come in Epic 6"). `NOTHING_TO_UPDATE_MESSAGE` passa a citar o grupo.
- `porquinho-backend/.../dto/CreateCategoryRequest.java` -- remover `@NotNull` de `parentId`.
- `porquinho-backend/.../dto/UpdateCategoryRequest.java` -- `UUID parentId` e `isAnyField` considerando esse campo.
- `porquinho-backend/.../controller/CategoryController.java` -- repassar `parentId` no PATCH e atualizar os javadocs.
- `porquinho-backend/.../entity/Category.java` -- `setParentCategoryId` já existe.
- `porquinho-backend/.../service/TransactionCore.java#requireChildCategory` -- não mudar.
- `porquinho-frontend/src/components/onboarding/CustomizeCategoriesStep.vue` -- usa o PATCH sem `parentId`; continua funcionando.
- Testes: `CategoryControllerTest.java` (linha ~184 espera 400 sem `parentId`), `CategoryCustomizationIntegrationTest.java:278` (`PARENT_REQUIRED_MESSAGE`), `CategoryConcurrencyIntegrationTest.java`.
- `porquinho-frontend/src/types/category.ts` -- `parentId?` em `CreateCategoryInput` e `UpdateCategoryInput`.
- `porquinho-frontend/src/services/categoryService.ts` -- `createCategory` e `updateCategory` já existem. Sem mudança, só o tipo.
- `porquinho-frontend/src/views/CategoriesView.vue` -- `groups` computed, modal de criação (6.1). Usar o mesmo `Dialog` e o mesmo tratamento de `ApiError`.
- `porquinho-frontend/src/views/__tests__/CategoriesView.spec.ts` -- ampliar.

## Tasks & Acceptance

**Execution:**
- [x] `CreateCategoryRequest.java`, `UpdateCategoryRequest.java`, `CategoryService.java`, `CategoryController.java` -- criar grupo e mover categoria conforme a matriz.
- [x] Testes backend -- matriz completa no teste de integração; controller com PATCH `parentId` e POST sem `parentId`; ajustar os casos antigos.
- [x] `types/category.ts` -- `parentId` opcional no create e no update.
- [x] `views/CategoriesView.vue` -- opção "Nenhum (criar como grupo)" no seletor de grupo (o texto de cor do modal muda junto), mensagem "Grupo criado", grupos recolhíveis e botão "Mover" com modal, com a mensagem "Categoria movida para [grupo]". Liberar "Adicionar categoria" mesmo sem grupos (o 6.1 bloqueava) e trocar o texto do estado vazio.
- [x] `views/__tests__/CategoriesView.spec.ts` -- criar grupo, mover (sucesso e erro), recolher e expandir com `aria-expanded`.

**Acceptance Criteria:**
- Given estou em `/categorias`, when movo uma categoria, then ela aparece no novo grupo sem recarregar a página, com a cor atualizada conforme a regra.
- Given crio um grupo, when abro o modal de novo, then o grupo aparece como opção e posso criar categorias nele.
- Given recolho um grupo, when leio a página com leitor de tela, then o botão anuncia "recolhido" e as filhas ficam ocultas.
- Given movi uma categoria, when abro `/transacoes`, then ela aparece no seletor sob o novo grupo.

## Implementation Notes

## Spec Change Log

## Review Triage Log

| # | Layer | Finding | Verdict | Evidence | Route |
|---|-------|---------|---------|----------|-------|
| 1 | blind | Corrida ao criar o primeiro grupo sem nenhuma categoria ativa: `FOR UPDATE` não trava zero linhas, então dois POSTs simultâneos geram nome repetido | low | Real (não há índice único). Só ocorre com duas abas ao mesmo tempo antes do onboarding; o botão já bloqueia clique duplo. A correção pede trava nova | rejeitado (low, não é correção direta) |
| 2 | blind, edge (×3) | Modal Mover oferece o grupo atual (PATCH inútil e mensagem falsa "movida") e o botão aparece com um só grupo | low | `openMove` pré-seleciona o pai atual; o select lista todos os grupos | patch |
| 3 | blind, edge | Prévia de cor ao criar grupo mostra `#64748B`, mas o backend grava a primeira cor livre | low | `previewColor` cai no fallback quando `form.parentId` é vazio | patch |
| 4 | blind | Título do modal continua "Adicionar categoria" ao criar grupo | low | Só a descrição muda | patch |
| 5 | blind | POST sem `parentId` cria grupo sem aviso; faltaria regressão do onboarding | false | `CustomizeCategoriesStep.vue` sempre envia `parentId`; nenhum dano concreto apontado | rejeitado |
| 6 | blind | Testes de front faltando: fallback `MOVE_FAILED`, foco ao cancelar, bloqueio durante save | low | Comportamento existe; falta só teste | rejeitado (low) |
| 7 | blind | Lacunas de teste no backend: controller encadeado, mover e renomear com 409, cor com o mesmo pai | low | A matriz está coberta no teste de integração | rejeitado (low) |
| 8 | blind | Filha não herda a cor do novo grupo se o grupo antigo foi recolorido depois | low | Consequência da decisão "sem coluna nova" do Luiz | rejeitado (decisão de intent) |
| 9 | blind | `collapsed` não é limpo nem persiste | low | Inofensivo | rejeitado (low) |
| 10 | edge | Criar categoria num grupo recolhido deixa ela oculta | low | `save()` não expande; `move()` expande | patch |
| 11 | edge | `{parentId:null}` no PATCH dá 400 com mensagem confusa | false | Mover para "nenhum" está fora do intent (grupo nunca vira filha nem vice-versa); a mensagem cita os campos aceitos | rejeitado |
| 12 | vgap | Expansão do destino recolhido ao mover sem teste | gap | Nenhum teste recolhe o destino antes de mover | patch |

## Verification

**Commands:**
- `cd porquinho-backend && ./mvnw test -Dtest='Category*'` -- expected: verde
- `cd porquinho-frontend && npm run lint && npx vue-tsc --build && npx vitest run` -- expected: sem erros
