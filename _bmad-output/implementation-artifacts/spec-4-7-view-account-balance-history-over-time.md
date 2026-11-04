---
title: 'Histórico de saldo da conta ao longo do tempo'
type: 'feature'
created: '2026-11-04'
status: 'done'
baseline_commit: cd36b17
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/spec-4-6-reconcile-account-balance-with-bank.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-4-3-edit-account-and-card-information.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** A tabela `account_balance_history` (V20) já registra cada mudança de saldo (criação, edição, transferência, reconciliação), mas o usuário não tem como ver como o saldo de uma conta evoluiu.

**Approach:** `GET /api/v1/accounts/{id}/balance-history?range=…` devolve os pontos do período. A nova página `/contas/:id/historico`, aberta pelo botão "Histórico" de cada conta em `/contas`, mostra um gráfico de linha do saldo, um seletor de período e uma tabela com os mesmos dados (alternativa textual, NFR49).

## Boundaries & Constraints

**Always:**
- Sem migration. A tabela e o índice composto (`account_id`, `snapshot_date`) da V20 bastam.
- `GET /api/v1/accounts/{id}/balance-history?range=30d|3m|6m|1y|all` (padrão `3m`) → 200 com:
  - `{ account: {id, accountName, currentBalance}, points: [{date, balance, event}] }`;
  - `event` em `initial`, `edit`, `transfer`, `reconciliation`, `transaction`.
- Regras do endpoint:
  - Pontos em ordem crescente de `snapshot_date`, com desempate por `created_at` e depois `id`. Isso resolve o item da 4.3 adiado para esta story; dois eventos da mesma conta no mesmo instante não acontecem, porque cada operação grava uma linha por conta.
  - O período é contado a partir de agora em `America/Sao_Paulo`.
  - O último ponto anterior ao início do período entra como primeiro ponto, para a linha não começar do nada.
  - `range` inválido → 400 "Período inválido".
- A consulta filtra sempre por `user_id` do JWT (NFR15). Conta inexistente ou de outro usuário → 404 "Conta não encontrada". Conta removida continua consultável (o histórico é preservado, Story 4.4).
- Desempenho (NFR10): uma consulta indexada sem paginação, até 1000+ pontos, e o gráfico renderiza em menos de 1 segundo com 1000 pontos.
- Frontend:
  - Cada conta ativa em `/contas` ganha o botão "Histórico" (rótulo acessível com o nome, 44px), que leva a `/contas/:id/historico` (`requiresAuth`).
  - Página "Histórico de saldo — <nome>" com link "Voltar para contas", seletor de período (botões "30 dias", "3 meses", "6 meses", "1 ano", "Tudo", com `aria-pressed`) e o gráfico de linha:
    - eixo X com as datas e eixo Y em R$;
    - um marcador por evento, com cor/forma por tipo e legenda;
    - ao passar o mouse ou focar com o teclado num ponto, aparecem data (dd/mm/aaaa), saldo e tipo do evento ("Saldo inicial", "Edição", "Transferência", "Reconciliação", "Transação").
  - Abaixo do gráfico, a tabela "Data | Evento | Saldo", da mais recente para a mais antiga.
  - Estados: carregando; erro com "Tentar de novo"; 404 com "Esta conta não existe mais" e o link de volta; período sem pontos com "Nenhuma movimentação neste período".
  - Saldos negativos em vermelho na tabela.
- **Decisão (Luiz, 2026-11-04) A:** gráfico em SVG feito à mão (`components/accounts/BalanceChart.vue`), sem biblioteca nova. O Epic 7 decide se precisa de uma biblioteca de gráficos.

**Never:**
- Biblioteca nova.
- Gravar novas linhas de histórico (as Stories 4.3–4.6 já gravam; transações chegam no Epic 5).
- Histórico de cartões, comparação entre contas, exportar o gráfico.
- Editar migrations V1 a V23.

## I/O & Edge-Case Matrix

| Cenário | Estado | Resultado | Erro |
|---|---|---|---|
| Abrir histórico | conta com initial, edit, transfer, reconciliation | gráfico com 4 pontos e tabela com 4 linhas | — |
| Período 30 dias | último evento há 2 meses | 1 ponto (o último antes do período) | — |
| `range=abc` | — | 400 "Período inválido" | 400 |
| Conta de outro usuário | — | 404 | 404 |
| Conta removida | — | 200 com o histórico | — |
| 1000 pontos | — | resposta única; gráfico < 1 s | — |
| Saldo negativo | −50,00 | linha abaixo do zero; tabela em vermelho | — |

</frozen-after-approval>

## Code Map

- `porquinho-backend/.../repository/AccountBalanceHistoryRepository.java`, `entity/AccountBalanceHistory.java` -- consulta por conta, usuário e período, mais o último ponto antes do início.
- `porquinho-backend/.../controller/AccountController.java` ou um controller novo, `service/AccountService.java`, `repository/AccountRepository.java` -- conta do usuário, inclusive removida.
- Criar `dto/BalanceHistoryResponse.java`.
- `porquinho-frontend/src/router/index.ts`, `views/AccountsView.vue` -- rota e botão. Criar `views/AccountHistoryView.vue` e `components/accounts/BalanceChart.vue`. `services/accountService.ts`, `types/account.ts`, `lib/money.ts`.
- Testes modelo: `ReconciliationServiceTest`, `ReconciliationControllerTest`, `ReconciliationIntegrationTest`, `AccountsView.spec.ts`, `ReconcileForm.spec.ts`.

## Tasks & Acceptance

**Execution:**
- [x] Backend -- consulta, serviço (período, ponto anterior, 404, ordenação), rota e DTO.
- [x] Frontend -- serviço, rota, botão "Histórico", página, gráfico, seletor e tabela.
- [x] Testes -- a matriz no backend (serviço, controller, integração com PostgreSQL incluindo período, ponto anterior, conta de outro usuário e 1000 pontos) e Vitest da página e do gráfico (tooltip, teclado, estados).

**Acceptance Criteria:**
- Dado Nubank criada com R$ 1.000,00, editada para R$ 1.200,00, com uma transferência de R$ 200,00 e uma reconciliação para R$ 1.050,00, quando o usuário clica em "Histórico", então vê o gráfico com 4 pontos e a tabela com "Reconciliação R$ 1.050,00" no topo, e ao focar um ponto vê a data, o saldo e o tipo.
- Dado o período "30 dias" sem eventos recentes, então o gráfico mostra o último saldo conhecido como único ponto.

## Spec Change Log

## Review Triage Log

Revisão em 3 camadas (Blind Hunter, Edge Case Hunter, Verification Gap) — 2026-11-04. Parte da saída do Edge Case Hunter chegou comprimida e o hash expirou; os achados legíveis estão abaixo.

| # | Origem | Achado | Veredito | Evidência | Rota |
|---|---|---|---|---|---|
| 1 | Blind, Edge | Ponto anterior ao período estica o eixo X; "30 dias" fica igual a "Tudo" | medium | `xDomain` vai do ponto mais antigo | patch (`periodStart` e domínio até agora) |
| 2 | Blind | Linha reta entre eventos inventa saldos; a linha para no último evento | medium | O saldo é constante entre eventos | patch (linha em degrau até hoje) |
| 3 | Blind, Edge | Evento desconhecido quebra o gráfico; `transaction` com a mesma forma de `initial` | low | Rótulo tem `??`, marcador não | patch |
| 4 | Blind | Cada ponto é uma parada de Tab (1000+) | medium | `tabindex="0"` em todos | patch (roving tabindex) |
| 5 | Blind, Edge | Spread em `Math.min`/`Math.max` estoura com muitos pontos | low | Limite de argumentos | patch |
| 6 | Blind, Edge | Chave duplicada e rótulos repetidos no eixo Y | low | Ticks arredondados colidem | patch |
| 7 | Blind, Edge | Troca de `:id` na rota não recarrega; `notFound` nunca volta a falso; id malformado cai no erro genérico | low | `load` só no `onMounted` | patch |
| 8 | VG | `getBalanceHistory` (URL e `range`) sem teste | medium | A view mocka o serviço | patch |
| 9 | VG | Rota `/contas/:id/historico` sem teste | low | Nenhuma rota tem teste | defer (com o item da 4.1) |
| 10 | Edge | `range` inválido em conta de outro usuário dá 400, não 404 | low | Nenhum dado vaza | reject |
| 11 | Blind | Contas removidas sem link para o histórico | low | A spec pede o botão só nas ativas | reject |
| 12 | Blind | "Tudo" sem teto de pontos | low | Volume por conta pequeno (NFR10 cita 1000+) | reject |
| 13 | Blind | Data sem hora na tabela e no tooltip | low | A spec pede dd/mm/aaaa | reject |
| 14 | Blind | Período não fica na URL | low | Intenção não pede | reject |
| 15 | Blind | Testes de desempenho com limite fixo de tempo | low | Passam no CI; limite folgado | reject |
| 16 | Blind | Sem teste de 401 e de id malformado no controller | low | Segurança global; o front trata o 400 (item 7) | reject |

## Verification

**Commands:**
- `cd porquinho-backend && ./mvnw test` -- 0 falhas. Antes, parar o backend de dev e conferir que a porta 8080 está livre.
- `cd porquinho-frontend && npx vitest run && npx oxlint . && npx eslint . && npx vue-tsc --build` -- 0 falhas e 0 erros.

**Manual checks:**
- Abrir o histórico da conta reconciliada na 4.6, trocar o período, passar o mouse e usar Tab nos pontos; conferir a tabela.
