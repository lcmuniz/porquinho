---
title: 'Remover e restaurar contas bancárias e cartões de crédito'
type: 'feature'
created: '2026-11-03'
status: 'done'
baseline_commit: 399ac4c3335551f25777abf2fbebf52c3f742f87
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/spec-4-3-edit-account-and-card-information.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-4-1-register-multiple-bank-accounts.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Nas páginas `/contas` e `/cartoes` não dá para tirar uma conta ou um cartão que o usuário não usa mais. A API já faz soft delete (Stories 3.2 e 3.3), mas sem confirmação, sem auditoria, sem a regra de manter ao menos uma conta e sem como desfazer.

**Approach:** Botão "Remover" com confirmação em cada item das duas páginas, usando o `DELETE` existente acrescido de auditoria e da regra da última conta. Um seletor "Mostrar removidas" lista os itens removidos com o botão "Restaurar".

## Boundaries & Constraints

**Always:**
- `DELETE /api/v1/accounts/{id}` e `DELETE /api/v1/credit-cards/{id}` (já existentes, soft delete → 204) passam a registrar o audit `account_deleted` / `card_deleted` `{id}`. Remover a última conta ativa do usuário → 409 "Mantenha ao menos uma conta ativa" (cartões não têm mínimo).
- `GET /api/v1/accounts?status=deleted` e `GET /api/v1/credit-cards?status=deleted` listam os removidos do usuário, do mais recente para o mais antigo (`deleted_at` DESC). Sem o parâmetro, nada muda.
- `POST /api/v1/accounts/{id}/restore` e `POST /api/v1/credit-cards/{id}/restore` → 200 com o item restaurado (`deleted_at = NULL`), audit `account_restored` / `card_restored`. Item de outro usuário, inexistente ou que não está removido → 404.
- Remover ou restaurar não toca no `account_balance_history` (o histórico é preservado). Transações ainda não existem (Epic 5); quando existirem, a remoção não as apaga.
- Frontend, nas duas páginas:
  - Cada item ativo ganha "Remover" (rótulo acessível com o nome, 44px). Abre um `Dialog` "Remover <nome>?" com o texto "A conta some da lista, mas o histórico é preservado. Você pode restaurá-la depois." (e o equivalente para cartão), e os botões "Cancelar" e "Remover".
  - Confirmar remove o item da lista, recalcula o total e mostra "Conta removida" / "Cartão removido" na região de status. O 409 mostra a mensagem do servidor no diálogo.
  - Seletor "Mostrar removidas"/"Mostrar removidos" (checkbox nativo) carrega e mostra uma seção "Removidas"/"Removidos" com os itens removidos e o botão "Restaurar". Restaurar move o item de volta para a lista ativa e mostra "Conta restaurada" / "Cartão restaurado". Itens removidos não entram no total nem têm Editar.
  - Travas durante o envio, foco e erros no mesmo padrão das Stories 4.1–4.3.
- O botão Remover do onboarding (passos 2 e 3) continua chamando o mesmo `DELETE`.
- **Decisão (Luiz, 2026-11-03) 1B:** a regra da última conta ativa só vale quando o onboarding do usuário está concluído (`onboarding_progress.is_completed = true`). No passo 2 continua permitido remover todas as contas e pular (cria "Minha Conta Principal").
- **Decisão (Luiz, 2026-11-03) 2A:** migration **V21** acrescenta `deleted_by UUID NULL REFERENCES users(id)` em `accounts` e `credit_cards`, com comentário em pt-BR. A remoção grava `deleted_by = user_id` junto com `deleted_at`; a restauração zera os dois. O export LGPD passa a incluir a coluna (`assertExportsEveryColumn`).

**Never:**
- Exclusão definitiva (hard delete), transferências (4.5), reconciliação (4.6).
- Biblioteca nova; editar migrations V1 a V20.

## I/O & Edge-Case Matrix

| Cenário | Estado | Resultado | Erro |
|---|---|---|---|
| Remover conta | 2 contas ativas | 204; some da lista; total recalculado; audit `account_deleted` | — |
| Remover a última conta | 1 conta ativa, onboarding concluído | 409 "Mantenha ao menos uma conta ativa" no diálogo | 409 |
| Remover a última conta no passo 2 | onboarding não concluído | 204, como hoje | — |
| `deleted_by` | remover e restaurar | preenchido com o `user_id` e depois zerado | — |
| Remover cartão | 1 cartão ativo | 204 (sem mínimo); audit `card_deleted` | — |
| Mostrar removidas | 1 removida | seção com o item e "Restaurar" | — |
| Restaurar | item removido | 200; volta à lista ativa e ao total; audit `*_restored` | — |
| Restaurar item ativo, de outro usuário ou inexistente | — | 404 | 404 |
| Histórico de saldo | conta removida e restaurada | linhas do histórico intactas | — |
| Cancelar no diálogo | — | nada muda | — |

</frozen-after-approval>

## Code Map

- `porquinho-backend/.../service/AccountService.java`, `CreditCardService.java` -- `delete` existente; acrescentar a regra da última conta, o audit, `listDeleted` e `restore`. `AuditLogService.log`.
- `porquinho-backend/.../controller/AccountController.java`, `CreditCardController.java` -- `?status=deleted` e `POST /{id}/restore`.
- `porquinho-backend/.../repository/AccountRepository.java`, `CreditCardRepository.java` -- consultas de removidos e de contagem de ativas.
- `porquinho-frontend/src/views/AccountsView.vue`, `views/CardsView.vue` -- botão Remover, diálogo de confirmação, seletor e seção de removidos.
- `services/accountService.ts`, `services/cardService.ts` -- `listDeletedAccounts`, `restoreAccount`, `listDeletedCards`, `restoreCard`.
- Testes modelo: `AccountServiceTest`, `AccountControllerTest`, `AccountOnboardingIntegrationTest`, `CreditCard*Test`, `AccountsView.spec.ts`, `CardsView.spec.ts`, `AccountsStep.spec.ts`.

## Tasks & Acceptance

**Execution:**
- [ ] `V21__add_deleted_by_to_accounts_and_credit_cards.sql`; entidades, gravação na remoção e limpeza na restauração; export.
- [ ] Backend -- regra da última conta (só com onboarding concluído), audit na remoção, listagem de removidos e restauração para contas e cartões.
- [ ] Frontend -- serviços, Remover com confirmação, seletor "Mostrar removidas", seção com Restaurar nas duas páginas.
- [ ] Testes -- matriz no backend (serviço, controller, integração com PostgreSQL incluindo o histórico preservado) e Vitest das duas páginas.

**Acceptance Criteria:**
- Dado duas contas ativas, quando o usuário remove "BB" e confirma, então "BB" some da lista, o total é recalculado, aparece "Conta removida" e existe o audit `account_deleted`; ao marcar "Mostrar removidas", "BB" aparece com "Restaurar", e restaurá-la a traz de volta à lista e ao total.
- Dado uma única conta ativa, quando o usuário tenta removê-la, então o diálogo mostra "Mantenha ao menos uma conta ativa" e a conta continua ativa.

## Spec Change Log

## Review Triage Log

Revisão em 3 camadas (Blind Hunter, Edge Case Hunter, Verification Gap) — 2026-11-03.

| # | Origem | Achado | Veredito | Evidência | Rota |
|---|---|---|---|---|---|
| 1 | Edge | PUT concorrente com DELETE grava a linha inteira e "ressuscita" o item; DELETE/restore concorrentes duplicam audit | medium | Hibernate atualiza todas as colunas a partir do estado lido | patch (lock na leitura do item) |
| 2 | Blind | Remover a última conta e concluir o passo 2 podem intercalar e deixar o usuário sem conta | medium | `delete` lê o onboarding sem lock; `applyAccounts` trava o progresso | patch (mesma ordem de locks) |
| 3 | Blind | FK `deleted_by` sem índice (varredura no purge) | low | V21 ainda não aplicada | patch |
| 4 | VG, Blind | Lock da regra da última conta sem teste concorrente | medium | Testes mockados ou sequenciais | patch |
| 5 | VG | Contrato HTTP de `listDeleted*`/`restore*` sem teste | medium | Views mockam o serviço | patch |
| 6 | VG | Ordem do restaurado e remoção com a seção aberta só testados em contas | low | `CardsView.spec` não cobre | patch |
| 7 | Edge | Resposta atrasada de "Mostrar removidas" sobrescreve dados novos; item removido some da seção | low | Sem guarda de sequência | patch |
| 8 | Edge, Blind | 404 ao restaurar/remover (outra aba) tira o item das duas listas | low | Só filtra localmente | patch (recarrega) |
| 9 | Blind | Restore no controller não verifica o IP; falha não-404 do restore sem teste | low | Correção direta | patch |
| 10 | Blind | IP do audit atrás de proxy | low | Já registrado em `deferred-work.md` | reject (duplicado) |
| 11 | Blind | `idMetadata` em `AccountService` usado por cartões | low | Detalhe interno, sem efeito | reject |
| 12 | Blind | Removidas não mostram a data de remoção | low | Intenção não pede | reject |
| 13 | Blind | Duplicação entre `AccountsView` e `CardsView` | low | Mesmo item adiado da retro do Epic 3 (D3) | reject (duplicado) |
| 14 | Blind, Edge | Filtro `status` rígido (vazio, maiúsculas, `active`) | low | Único cliente é o próprio front | reject |
| 15 | Blind | `listDeleted` sem paginação | low | Volume por usuário pequeno (NFR30) | reject |
| 16 | Blind | Teste de purge apaga `audit_logs` à mão | low | O purge real tem teste próprio de ordem (`AccountDeletionServiceTest`) | reject |
| 17 | Blind | Botão Remover da última conta leva a um 409 previsível | low | A mensagem do 409 explica; o front não sabe se o onboarding terminou | reject |
| 18 | Edge | Empate de `createdAt` na reinserção | low | Precisão de microssegundos | reject |
| 19 | Blind | Ordem de imports nos controllers | low | Cosmético | reject |

## Verification

**Commands:**
- `cd porquinho-backend && ./mvnw test` -- 0 falhas. Antes, parar o backend de dev e conferir que a porta 8080 ficou livre, para o devtools não aplicar uma V21 intermediária no Supabase.
- `cd porquinho-frontend && npx vitest run && npx oxlint . && npx eslint . && npx vue-tsc --build` -- 0 falhas e 0 erros.

**Manual checks:**
- Remover e restaurar uma conta e um cartão; tentar remover a última conta; conferir o audit no Supabase de dev.
