---
title: 'Correções rápidas do deferred-work (IP do audit, e-mail no log, re-registro, lembrete no startup, lotes no saldo)'
type: 'bugfix'
created: '2026-11-09'
status: 'done'
route: 'oneshot'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Seis itens simples do `deferred-work.md`: (1) o audit grava o IP do nginx, e não o do cliente, em quase todos os controllers; (2) o `EmailService` grava no log o e-mail completo do destinatário (LGPD); (3) `formatDateBR` mostraria `AAAA-MM-DD` um dia antes; (4) usuário soft-deletado que se registra de novo com o mesmo sub recebe 500; (5) o job de lembretes de trial só roda às 9h e perde o lembrete de 1 dia se o backend estiver fora nesse horário; (6) `markInBalance` falha com mais de cerca de 32 mil ids.

**Approach:** (1) `server.forward-headers-strategy: native` no `application.yml`. O Tomcat passa a confiar no nginx (IP privado da rede Docker) e `getRemoteAddr()` devolve o IP do cliente em todos os pontos. Os helpers manuais de X-Forwarded-For de `AuthController` e `AccountDeletionController` passam a usar `getRemoteAddr()`, porque ler o primeiro valor do cabeçalho permite que o cliente forje o IP. (2) Mascarar o e-mail nos logs. (3) Nenhum chamador passa data sem hora (todos passam um instante com fuso), e as datas sem hora já usam `formatLocalDateBR`. Sem código; marcar como resolvido. (4) 409 antes do `insertWithId` quando o id existe, inclusive soft-deletado. (5) Rodar o job também em `ApplicationReadyEvent`; ele é idempotente pelas colunas `sent_at`. (6) Gravar em lotes de ids.

</frozen-after-approval>

## Implementation Notes

- Decisão (5): no startup, o lembrete sai na hora em que o backend sobe, mesmo que não seja 9h. É melhor que perder o de 1 dia.
- Decisão (4): mensagem "Esta conta está em exclusão. Aguarde a conclusão para se cadastrar de novo."
- Decisão (2): a máscara mantém a 1ª letra e o domínio (`l***@gmail.com`).
- Arquivos: `application.yml`, `docker-compose.yml`, `AuthController`, `AccountDeletionController` (helpers de IP passam a usar `getRemoteAddr()`, e o `AccountDeletionController` mantém o corte em 45), `EmailService` (+ `maskEmail`), `AuthService` (+ `rejectIfDeletionPending`), `TrialReminderService` (+ `sendMissedRemindersAtStartup`, `@Async`), `TransactionImportReviewService` (lotes de 10 mil). Testes novos em `EmailServiceTest`, `AuthServiceTest` e `TrialReminderServiceTest`.
- Item (3): sem código, marcado como resolvido no `deferred-work.md`.
- Suíte do backend: 1374 testes, 0 falhas.

## Review Triage Log

- `deferred-work.md` não fechava os seis itens: **medium**, corrigido (patch) ao finalizar.
- Porta 8080 publicada no host: com userland-proxy, a origem vira o gateway 172.x, que o Tomcat trata como proxy confiável, e um X-Forwarded-For forjado (inclusive longo) seria aceito: **medium**, corrigido (patch) com `127.0.0.1:8080:8080` no `docker-compose.yml`. Antes, o `AuthController` já confiava em qualquer XFF.
- `e.getMessage()` de exceção SMTP pode trazer o endereço: **medium**, corrigido (patch). Loga só a classe.
- O job no startup segura a readiness: **low**, corrigido (patch) com `@Async` (uma anotação).
- O 409 "conta em exclusão" não chega ao usuário: **medium**, adiado (defer). É pré-existente no frontend, que engolia o 500 do mesmo jeito.
- Sem teste de integração do RemoteIpValve: **low**, rejeitado. É comportamento do Spring Boot com uma linha de config, e o teste pediria um servidor real com porta.
- Sem teste do lote de `markInBalance` acima de 10 mil: **low**, rejeitado. O laço é simples, os 43 testes da conclusão cobrem o lote único, e passar de 10 mil linhas no teste de integração pesaria a suíte.
- Sem teste do caminho feliz do startup: **low**, rejeitado. O método só delega ao job, que já tem teste.
- Wrapper de uma linha em `AuthController` e corte de 45 só num controller: **low**, rejeitado. Com o backend atrás do nginx, `getRemoteAddr()` é sempre um IP real.
