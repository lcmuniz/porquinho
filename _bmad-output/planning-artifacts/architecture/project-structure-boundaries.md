# Project Structure & Boundaries

## Complete Project Directory Structure

O projeto **porquinho** é organizado como uma arquitetura separada com dois repositórios distintos.

**Veja o documento completo em architecture.md para a estrutura detalhada dos dois projetos:**
- Backend (Spring Boot): Estrutura em camadas (controller/service/repository/entity)
- Frontend (Vue): Estrutura por features (components/views/stores/services)

## Mapeamento Resumido FR → Estrutura

> **Como construído (retro do Epic 2, item 5; atualizado em 2026-11-04):** o backend não usa pacotes por feature (`auth/`, `subscription/`...). Ele usa pacotes por camada em `com.porquinho`: `config`, `controller`, `dto`, `entity`, `exception`, `repository`, `service` e `validation`. O frontend separa `views/` (uma por tela), `components/<feature>/`, `services/` (cliente HTTP por recurso) e `stores/`. Os números de FR seguem `prd/functional-requirements.md` (FR1–FR86). Linhas marcadas como *planejado* ainda não foram construídas.

| FR (PRD) | Backend (`com.porquinho`) | Frontend | Database |
|----------|---------------------------|----------|----------|
| FR1-7 (Conta, autenticação, LGPD) | `AuthController`, `AuthService`, `SessionService`, `DataExportController`/`DataExportService`, `AccountDeletionController`/`AccountDeletionService` | `views/LoginView.vue`, `RegisterView.vue`, `SessionsView.vue`, `DataExportView.vue`, `AccountDeletionView.vue`, `stores/auth.ts` | `users`, `user_sessions`, `audit_logs`, `data_exports`, `account_deletions` |
| FR8-14 (Assinatura e cobrança) | `SubscriptionController`, `StripeWebhookController`, `SubscriptionService`, `StripeBillingService`, `TrialReminderService`, filtro `SubscriptionAccessFilter` (402) | `views/SubscriptionView.vue`, `components/subscription/TrialBadge.vue`, `services/subscriptionService.ts` | `subscriptions`, `billing_transactions` |
| FR15-22 (Onboarding) | `OnboardingController`, `OnboardingService`, `CategoryController`, `GoalController` | `views/OnboardingView.vue`, `components/onboarding/` | `onboarding_progress`, `categories`, `goals` |
| FR23-29 (Contas e cartões) | `AccountController`, `CreditCardController`, `TransferController`, `ReconciliationController`, `BalanceHistoryController`, `BalanceHistoryRecorder` | `views/AccountsView.vue`, `CardsView.vue`, `AccountHistoryView.vue`, `components/accounts/`, `components/cards/` | `accounts`, `credit_cards`, `transfers`, `account_balance_history`, `reconciliation_history` |
| FR30-42 (Importação e classificação IA) — *planejado* | controllers/services de importação e classificação | `views/` + `components/transactions/` | `transactions` |
| FR43-51 (Orçamento por envelope) — *planejado* | idem | `components/budget/` | `categories` (existe) + alocações |
| FR52-69 (Dashboard GPS) — *planejado* | idem + cache Redis | `views/DashboardView.vue` (existe), `components/dashboard/` | Aggregation queries |
| FR70-78 (Metas) — *parcial* (meta inicial no onboarding) | `GoalController`, `GoalService` | `services/goalService.ts` | `goals` |
| FR79-81 (Relatórios e export) — *planejado* (export LGPD já existe, FR6) | idem | `views/ReportsView.vue` | Query-based |
| FR82-86 (Admin, pós-MVP) — *planejado* | `AdminController`, Actuator | N/A | System metrics |

## Architectural Boundaries

**API Boundary:** Frontend ↔ Backend via REST (`/api/v1/*`)
**Auth Boundary:** Supabase Auth → JWT → Spring Security
**Data Boundary:** User segregation via `user_id` filter
**Cache Boundary:** Redis for Dashboard GPS (30s, 5min, 15min TTLs)

## Integration Points

**External:**
- Supabase: Auth + PostgreSQL
- OpenAI/Claude: IA classification
- Stripe: Payment processing

**Internal:**
- Frontend → Backend: Axios + JWT
- Backend Services: Service layer dependencies
- Cache Invalidation: Transaction classify/budget allocate → Clear Redis

## Deployment

**VPS Hostinger + Dokploy:**
- Frontend: Nginx container (port 3000)
- Backend: Java 21 container (port 8080)
- Redis: Cache container (port 6379)
- Nginx Reverse Proxy: SSL termination, routing

---

**Estrutura completa do projeto documentada!**

---
