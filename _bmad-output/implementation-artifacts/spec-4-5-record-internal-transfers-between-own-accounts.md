---
title: 'Registrar transferências entre contas próprias'
type: 'feature'
created: '2026-11-03'
status: 'done'
baseline_commit: 15f3d2b9331e261f7258e1039948c335331abeaa
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/spec-4-4-deactivate-or-remove-accounts-and-cards.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-4-3-edit-account-and-card-information.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Quando o usuário move dinheiro entre as próprias contas (por exemplo, da corrente para a poupança), não há como registrar. Editar os dois saldos à mão perde o vínculo entre eles e polui o histórico com duas edições soltas.

**Approach:** Criar a tabela `transfers` e `POST /api/v1/transfers`, que debita a conta de origem e credita a de destino na mesma transação, gravando uma linha `transfer` no histórico de cada conta. Na página `/contas`, o botão "Transferir" abre um diálogo, e uma seção "Transferências recentes" lista o que foi registrado.

## Boundaries & Constraints

**Always:**
- Migration **V22** `transfers`: `id` UUID PK, `user_id` FK indexada, `from_account_id` e `to_account_id` FK → `accounts(id)`, `amount` DECIMAL(15,2) NOT NULL CHECK > 0, `transfer_date` DATE NOT NULL, `description` VARCHAR(200) NULL, `created_at`, `deleted_at`; CHECK `from_account_id <> to_account_id`. Índices em `user_id` e nas duas FKs de conta. Comentários em pt-BR. O número V12 do épico já existe.
- `POST /api/v1/transfers` `{fromAccountId, toAccountId, amount, transferDate, description}` → 201. Validação em pt-BR: contas obrigatórias e diferentes ("Escolha contas diferentes"); valor obrigatório, > 0, até 2 casas, dentro de DECIMAL(15,2); data obrigatória, não pode ser futura (hoje em `America/Sao_Paulo` é aceito, reaproveitando `@NotPastInSaoPaulo` invertido ou um validador irmão); descrição opcional, strip/limpeza única, até 200.
- As duas contas precisam ser contas ativas do próprio usuário; senão, 404 "Conta não encontrada". As duas linhas de conta são travadas (`PESSIMISTIC_WRITE`) sempre na mesma ordem (por `id`), para duas transferências cruzadas não travarem uma à outra.
- Na mesma transação: `current_balance` da origem diminui e o do destino aumenta; uma linha `transfer` no `account_balance_history` de cada conta com o novo saldo; a linha em `transfers`; o audit `transfer_created` `{id, amount}`. Saldo negativo resultante é permitido (decisão 1A da Story 3.2).
- `GET /api/v1/transfers` lista as transferências ativas do usuário, da mais recente para a mais antiga (`transfer_date` DESC, `created_at` DESC), com os nomes das contas (inclusive de contas removidas, para o histórico continuar legível).
- LGPD: `transfers` no `purgeUser` (antes de `accounts` e do usuário) e no export (`assertExportsEveryColumn`).
- Frontend, em `/contas`:
  - Botão "Transferir" no topo, visível com 2 ou mais contas ativas. Abre um `Dialog` "Nova transferência" com: conta de origem e de destino (`<select>` nativo com as contas ativas, mostrando nome e saldo), valor no formato brasileiro, data (`<input type="date">`, padrão hoje em São Paulo, `max` hoje) e descrição.
  - Salvar atualiza o saldo das duas contas na lista (o total não muda), fecha o diálogo e mostra "Transferência registrada" na região de status. Erros 400 no campo; 404 ou outra falha no diálogo, que continua aberto. Travas durante o envio, foco e acessibilidade no padrão das Stories 4.1–4.4.
  - Seção "Transferências recentes": data, "Origem → Destino", valor e descrição. Estado vazio: "Nenhuma transferência registrada". Atualizada depois de cada transferência.
- Transferência não afeta categorias nem orçamento (não existem ainda; o Epic 6 deve ignorá-las).

**Never:**
- Editar ou excluir transferência, transferência para cartão (pagamento de fatura), tabela `transactions` (Epic 5).
- Biblioteca nova; editar migrations V1 a V21.

## I/O & Edge-Case Matrix

| Cenário | Estado | Resultado | Erro |
|---|---|---|---|
| Transferir R$ 200,00 de Nubank para Inter | ambas ativas | 201; Nubank −200, Inter +200; 2 linhas `transfer` no histórico; audit; "Transferência registrada" | — |
| Mesma conta nos dois lados | — | 400 "Escolha contas diferentes" | 400 |
| Valor 0, negativo ou com 3 casas | — | 400 no campo | 400 |
| Data futura | amanhã | 400 no campo | 400 |
| Conta de outro usuário ou removida | — | 404; nenhum saldo muda | 404 |
| Saldo da origem fica negativo | Nubank 100, transfere 150 | permitido; Nubank −50 em vermelho | — |
| Menos de 2 contas ativas | 1 conta | botão "Transferir" oculto | — |
| Listar | 2 transferências | mais recente primeiro, com os nomes das contas | — |

</frozen-after-approval>

## Code Map

- `porquinho-backend/.../service/AccountService.java`, `repository/AccountRepository.java` -- `findActiveByIdForUpdate`, gravação no histórico (`AccountBalanceHistory`), padrão de audit da Story 4.4.
- `porquinho-backend/.../validation/NotPastInSaoPaulo.java` -- modelo para a validação "não futura".
- `porquinho-backend/.../service/AccountDeletionService.java`, `DataExportService.java` -- acrescentar `transfers`.
- Criar `entity/Transfer.java`, `repository/TransferRepository.java`, `service/TransferService.java`, `controller/TransferController.java`, `dto/CreateTransferRequest.java`, `dto/TransferResponse.java`.
- `porquinho-frontend/src/views/AccountsView.vue` -- botão, diálogo e seção. Criar `services/transferService.ts`, `types/transfer.ts`, `components/accounts/TransferForm.vue`. `lib/goalPlan.ts` -- `todayInSaoPaulo`.
- Testes modelo: `AccountServiceTest`, `AccountControllerTest`, `AccountConcurrencyIntegrationTest`, `AccountOnboardingIntegrationTest`, `AccountDeletionServiceTest`, `DataExportServiceTest`, `AccountsView.spec.ts`, `AccountForm.spec.ts`.

## Tasks & Acceptance

**Execution:**
- [ ] `V22__create_transfers_table.sql` -- tabela, CHECKs, índices e comentários.
- [ ] Backend -- entidade, repositório, serviço (locks ordenados, saldos, histórico, audit), controller e DTOs; purge e export.
- [ ] Frontend -- serviço, `TransferForm.vue`, botão e diálogo em `/contas`, seção "Transferências recentes".
- [ ] Testes -- matriz no backend (serviço, controller, integração com PostgreSQL incluindo atomicidade e duas transferências cruzadas concorrentes sem deadlock) e Vitest do formulário e da página.

**Acceptance Criteria:**
- Dado Nubank com R$ 1.234,56 e Inter com R$ 600,00, quando o usuário transfere R$ 200,00 de Nubank para Inter, então a lista mostra Nubank R$ 1.034,56 e Inter R$ 800,00, o total não muda, aparece "Transferência registrada", a seção mostra "Nubank → Inter R$ 200,00", e no banco existem a linha em `transfers`, uma linha `transfer` no histórico de cada conta e o audit `transfer_created`.
- Dado uma transferência inválida (mesma conta, valor 0 ou data futura), quando o usuário tenta salvar, então o erro aparece no campo e nenhum saldo muda.

## Spec Change Log

## Review Triage Log

Revisão em 3 camadas (Blind Hunter, Edge Case Hunter, Verification Gap) — 2026-11-03.

| # | Origem | Achado | Veredito | Evidência | Rota |
|---|---|---|---|---|---|
| 1 | Blind | Nova tentativa depois de falha de rede duplica a transferência | medium | Sem chave de idempotência; o formulário mantém os dados | patch (id gerado no cliente como PK) |
| 2 | Blind | Snapshots `transfer` sem vínculo com a transferência (a 4.7 não acha a `transfer_date`) | low | V22 ainda não aplicada | patch (`transfer_id` no histórico) |
| 3 | Blind | "Transferências recentes" sem limite nem índice para a ordenação | low | Lista cresce sem fim | patch (20 mais recentes + índice composto) |
| 4 | Blind | Nomes de conta desatualizados depois de editar/remover/restaurar | low | Lista só recarrega em `load()` | patch |
| 5 | Blind | Serviço lança 500 com valor de 3 casas ou negativo sem `@Valid` | low | `setScale(UNNECESSARY)` | patch (guarda no serviço) |
| 6 | Edge | 404 deixa a conta removida nos selects | low | Sem recarga das contas | patch |
| 7 | Edge | Saldos depois da transferência calculados localmente | low | Saldo mudado em outra aba fica errado | patch (recarrega do servidor) |
| 8 | Edge | "Hoje" fixado ao abrir o formulário | low | Diálogo aberto depois da meia-noite | patch |
| 9 | VG | Teto do saldo só testado no destino | medium | Tirar o `abs()` da origem passa nos testes | patch |
| 10 | Blind | JSDoc diz que a validação local é igual à do backend | low | Falta o limite de 13 dígitos | patch (texto) |
| 11 | VG | Fuso de `@NotFutureInSaoPaulo` sem teste determinístico | low | Mesma lacuna do `@NotPastInSaoPaulo`; exige `Clock` injetável | defer |
| 12 | Blind | Banco não garante que as contas são do mesmo usuário (FK composta) | low | O serviço trava e valida as duas contas | reject |
| 13 | Blind | DTO importa `CategoryService.cleanName` | low | Mesmo padrão de `CreateGoalRequest` e das contas | reject |
| 14 | Blind | Seção aparece com 0 ou 1 conta | low | Estado vazio explica | reject |
| 15 | Blind | `data_version` do export não mudou | low | Mesmo veredito da 3.2 | reject |
| 16 | Blind | Faltam testes de 401 e da corrida transferência × remoção | low | 401 pela segurança global; o lock serializa as duas | reject |

## Verification

**Commands:**
- `cd porquinho-backend && ./mvnw test` -- 0 falhas. Antes, parar o backend de dev e conferir que a porta 8080 ficou livre, para o devtools não aplicar uma V22 intermediária no Supabase.
- `cd porquinho-frontend && npx vitest run && npx oxlint . && npx eslint . && npx vue-tsc --build` -- 0 falhas e 0 erros.

**Manual checks:**
- Restaurar Inter (removida no teste da 4.4) para ter 2 contas; transferir entre elas; conferir saldos, seção e as linhas no Supabase de dev.
