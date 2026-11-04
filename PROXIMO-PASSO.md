# ⚡ Próximos Passos

**Atualizado:** 2026-11-04
**Status do sprint:** `_bmad-output/implementation-artifacts/sprint-status.yaml`

## ✅ Concluído

- **Epic 0**: Fundação (Vue, Spring Boot, PostgreSQL/Flyway, Tailwind/shadcn-vue, Redis, CI/CD, design tokens, acessibilidade)
- **Epic 1**: Autenticação e LGPD (Google OAuth, email/senha, login, recuperação de senha, sessões ativas, exportação de dados, exclusão de conta)
- **Epic 2**: Assinatura e cobrança com Stripe (trial de 30 dias, plano mensal, lembretes, cancelamento, reativação, cobrança recorrente, falhas de pagamento)
- **Epic 3**: Onboarding guiado (envelopes, contas, cartões, categorias, primeira transação, Dashboard GPS, reserva de emergência)
- **Epic 4**: Contas e cartões (cadastro, edição, remoção/restauração, transferências, reconciliação, histórico de saldo)

## 🔜 Próximo

1. **Epic 5: Importação de transações e classificação por IA.** Começar pela Story 5.1 (*upload de OFX de 5 bancos brasileiros*) com `bmad-build` e depois `bmad-code-review`.
   - O saldo alterado por transação deve ser gravado por `BalanceHistoryRecorder`, o único gravador de `account_balance_history`.
2. Pendências abertas das retrospectivas (em `action_items` no sprint-status):
   - **antes de produção:** resync de progresso no 409 do onboarding (Epic 3); checklist de deploy com a configuração do Stripe Dashboard (Epic 2);
   - **refatoração:** dividir `SubscriptionService` e extrair as guardas do `StripeBillingService`; dividir `AccountsView.vue` em composables;
   - **decisão:** retenção de `billing_transactions` no purge (fiscal x LGPD);
   - **documentação:** atualizar o texto dos Epics 2 e 4 e do PRD para refletir o que foi construído.

## 🚀 Rodar localmente

Ver `SETUP-LOCAL.md` e `CONFIGURACAO-ENV.md`.
