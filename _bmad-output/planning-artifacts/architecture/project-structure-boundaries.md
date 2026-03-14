# Project Structure & Boundaries

## Complete Project Directory Structure

O projeto **porquinho** é organizado como uma arquitetura separada com dois repositórios distintos.

**Veja o documento completo em architecture.md para a estrutura detalhada dos dois projetos:**
- Backend (Spring Boot): Estrutura em camadas (controller/service/repository/entity)
- Frontend (Vue): Estrutura por features (components/views/stores/services)

## Mapeamento Resumido FR → Estrutura

| FR Category | Backend | Frontend | Database |
|-------------|---------|----------|----------|
| FR1-10 (Auth) | `auth/`, `security/` | `components/auth/`, `stores/auth.ts` | `users` table |
| FR11-17 (Subscription) | `subscription/` | `components/subscription/` | `subscriptions` table |
| FR18-25 (Onboarding) | `onboarding/` | `components/onboarding/` | `onboarding_progress` table |
| FR26-45 (Transactions) | `transaction/`, `external/` | `components/transactions/` | `transactions` table |
| FR46-54 (Budget) | `budget/` | `components/budget/` | `budget_categories`, `budget_allocations` tables |
| FR55-72 (Dashboard GPS) | `dashboard/` | `components/dashboard/` | Aggregation queries + Redis cache |
| FR73-81 (Goals) | `goal/` | `components/goals/` | `goals` table |
| FR82-84 (Reports) | `report/` | `views/ReportsView.vue` | Query-based |
| FR85-89 (Admin) | `AdminController.java`, Actuator | N/A | System metrics |

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
