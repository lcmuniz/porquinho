# Implementation Patterns & Consistency Rules

## Pattern Categories Defined

**Critical Conflict Points Identified:** 13 áreas onde AI agents poderiam fazer escolhas diferentes

**Purpose:** Garantir que múltiplos agentes de IA escrevam código compatível e consistente que funcione perfeitamente junto.

---

## Naming Patterns

### Database Naming Conventions (PostgreSQL)

**Tables:**
- ✅ **snake_case plural:** `users`, `transactions`, `budget_categories`
- ❌ **NOT:** `Users`, `Transaction`, `user`

**Columns:**
- ✅ **snake_case:** `user_id`, `created_at`, `transaction_date`
- ❌ **NOT:** `userId`, `createdAt`, `transactionDate`

**Primary Keys:**
- ✅ **Always `id` (UUID):** `id UUID PRIMARY KEY`
- Type: UUID para compatibilidade Supabase Auth

**Foreign Keys:**
- ✅ **Format:** `{table_singular}_id`
- Examples: `user_id`, `category_id`, `budget_id`
- ❌ **NOT:** `fk_user`, `userId`, `user`

**Indexes:**
- ✅ **Format:** `idx_{table}_{column(s)}`
- Examples: `idx_transactions_user_id`, `idx_transactions_date`
- ❌ **NOT:** `transactions_user_id_index`, `user_id_idx`

**Constraints:**
- ✅ **Format:** `{table}_{column}_{type}`
- Examples: `users_email_unique`, `transactions_amount_check`

**Audit Columns (todas as tabelas):**
```sql
created_at TIMESTAMP DEFAULT NOW() NOT NULL,
updated_at TIMESTAMP DEFAULT NOW() NOT NULL,
created_by UUID REFERENCES users(id),
updated_by UUID REFERENCES users(id),
deleted_at TIMESTAMP NULL,
deleted_by UUID REFERENCES users(id)
```

**Example Table:**
```sql
CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    category_id UUID REFERENCES budget_categories(id),
    transaction_date DATE NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    description VARCHAR(500),
    classification_metadata JSONB,
    created_at TIMESTAMP DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMP DEFAULT NOW() NOT NULL,
    deleted_at TIMESTAMP NULL
);

CREATE INDEX idx_transactions_user_id ON transactions(user_id);
CREATE INDEX idx_transactions_date ON transactions(transaction_date);
CREATE INDEX idx_transactions_category ON transactions(category_id);
```

---

### API Naming Conventions

**Endpoints:**
- ✅ **Plural nouns:** `/api/v1/transactions`, `/api/v1/categories`, `/api/v1/goals`
- ❌ **NOT:** `/api/v1/transaction`, `/api/v1/category`

**REST Conventions:**
```
GET    /api/v1/transactions          → List all (filtered by user)
GET    /api/v1/transactions/{id}     → Get single
POST   /api/v1/transactions          → Create new
PUT    /api/v1/transactions/{id}     → Full update
PATCH  /api/v1/transactions/{id}     → Partial update
DELETE /api/v1/transactions/{id}     → Soft delete
```

**Nested Resources:**
```
GET    /api/v1/budgets/{budgetId}/categories
POST   /api/v1/transactions/{id}/classify
GET    /api/v1/dashboard/layer1
```

**Query Parameters:**
- ✅ **camelCase:** `?userId=abc&startDate=2026-01`
- ❌ **NOT:** `?user_id=abc&start_date=2026-01`

**Path Parameters:**
- ✅ **Format:** `{id}`, `{userId}`, `{categoryId}`
- ❌ **NOT:** `:id`, `{user_id}`, `<id>`

**Headers:**
- ✅ **Standard:** `Authorization`, `Content-Type`, `Accept`
- ✅ **Custom:** `X-Request-Id`, `X-User-Timezone`
- ❌ **NOT:** `Custom-Header` (sem X- prefix)

---

### Code Naming Conventions

**Backend Java (Spring Boot):**

**Classes:**
- ✅ **PascalCase:** `TransactionController`, `BudgetService`, `UserRepository`
- Suffixes obrigatórios:
  - Controllers: `*Controller`
  - Services: `*Service`
  - Repositories: `*Repository`
  - DTOs: `*DTO` ou `*Request`/`*Response`
  - Entities: Sem suffix (`Transaction`, `User`)

**Files:**
- ✅ **Match class name:** `TransactionController.java`, `BudgetService.java`

**Methods:**
- ✅ **camelCase:** `getTransactions()`, `createBudget()`, `classifyTransaction()`
- Prefixes:
  - `get*` → Leitura
  - `create*` → POST
  - `update*` → PUT/PATCH
  - `delete*` → DELETE
  - `find*` → Queries complexas
  - `is*` / `has*` → Booleans

**Variables:**
- ✅ **camelCase:** `userId`, `transactionList`, `budgetCategory`

**Constants:**
- ✅ **UPPER_SNAKE_CASE:** `MAX_TRANSACTIONS`, `DEFAULT_CACHE_TTL`

**Packages:**
- ✅ **lowercase:** `com.porquinho.controller`, `com.porquinho.service`

**Example Backend:**
```java
// TransactionController.java
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping
    public List<TransactionResponse> getTransactions(
        @AuthenticationPrincipal String userId,
        @RequestParam(required = false) String startDate
    ) {
        return transactionService.findByUserId(userId, startDate);
    }

    @PostMapping
    public TransactionResponse createTransaction(
        @AuthenticationPrincipal String userId,
        @Valid @RequestBody TransactionRequest request
    ) {
        return transactionService.create(userId, request);
    }
}

// TransactionService.java
@Service
public class TransactionService {

    public List<TransactionResponse> findByUserId(String userId, String startDate) {
        // ...
    }
}

// TransactionRepository.java
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    List<Transaction> findByUserIdAndDeletedAtIsNull(String userId);
}
```

---

**Frontend Vue (TypeScript):**

**Components:**
- ✅ **PascalCase:** `TransactionList.vue`, `BudgetCard.vue`, `DashboardLayer1.vue`
- ❌ **NOT:** `transaction-list.vue`, `budget_card.vue`

**Files:**
- ✅ **Match component name:** `TransactionList.vue`
- ✅ **Composables:** `useTransactions.ts`, `useDashboard.ts`
- ✅ **Services:** `api.ts`, `supabase.ts`
- ✅ **Stores:** `auth.ts`, `transactions.ts`

**Functions/Methods:**
- ✅ **camelCase:** `fetchTransactions()`, `createBudget()`, `handleSubmit()`
- Prefixes:
  - `fetch*` → API calls
  - `handle*` → Event handlers
  - `use*` → Composables
  - `is*` / `has*` → Computed booleans

**Variables:**
- ✅ **camelCase:** `userId`, `transactionList`, `isLoading`

**Constants:**
- ✅ **UPPER_SNAKE_CASE:** `API_BASE_URL`, `MAX_RETRIES`

**Types/Interfaces:**
- ✅ **PascalCase:** `Transaction`, `Budget`, `User`
- ✅ **Prefix interfaces:** `ITransaction` (opcional, se preferir)

**Example Frontend:**
```typescript
// TransactionList.vue
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useTransactions } from '@/composables/useTransactions'
import type { Transaction } from '@/types'

const { transactions, isLoading, fetchTransactions } = useTransactions()

onMounted(() => {
  fetchTransactions()
})

function handleDelete(id: string) {
  // ...
}
</script>

// useTransactions.ts (composable)
import { ref } from 'vue'
import api from '@/services/api'
import type { Transaction } from '@/types'

export function useTransactions() {
  const transactions = ref<Transaction[]>([])
  const isLoading = ref(false)

  async function fetchTransactions() {
    isLoading.value = true
    try {
      const response = await api.get('/transactions')
      transactions.value = response.data
    } finally {
      isLoading.value = false
    }
  }

  return { transactions, isLoading, fetchTransactions }
}

// transactions.ts (Pinia store)
import { defineStore } from 'pinia'

export const useTransactionStore = defineStore('transactions', () => {
  const items = ref<Transaction[]>([])
  const isLoading = ref(false)

  async function fetchAll() {
    // ...
  }

  return { items, isLoading, fetchAll }
})
```

---

## Structure Patterns

### Project Organization

**Backend (Spring Boot):**
```
porquinho-backend/
├── src/
│   ├── main/
│   │   ├── java/com/porquinho/
│   │   │   ├── controller/          # REST Controllers
│   │   │   │   ├── TransactionController.java
│   │   │   │   ├── BudgetController.java
│   │   │   │   └── DashboardController.java
│   │   │   ├── service/             # Business Logic
│   │   │   │   ├── TransactionService.java
│   │   │   │   ├── BudgetService.java
│   │   │   │   └── ClassificationService.java
│   │   │   ├── repository/          # Data Access
│   │   │   │   ├── TransactionRepository.java
│   │   │   │   └── UserRepository.java
│   │   │   ├── entity/              # JPA Entities
│   │   │   │   ├── Transaction.java
│   │   │   │   ├── User.java
│   │   │   │   └── BudgetCategory.java
│   │   │   ├── dto/                 # Data Transfer Objects
│   │   │   │   ├── request/
│   │   │   │   │   ├── TransactionRequest.java
│   │   │   │   │   └── BudgetRequest.java
│   │   │   │   └── response/
│   │   │   │       ├── TransactionResponse.java
│   │   │   │       └── DashboardLayer1Response.java
│   │   │   ├── config/              # Configuration
│   │   │   │   ├── SecurityConfig.java
│   │   │   │   ├── CorsConfig.java
│   │   │   │   ├── RedisConfig.java
│   │   │   │   └── OpenApiConfig.java
│   │   │   ├── exception/           # Exception Handling
│   │   │   │   ├── GlobalExceptionHandler.java
│   │   │   │   ├── ResourceNotFoundException.java
│   │   │   │   └── ValidationException.java
│   │   │   ├── security/            # Security Components
│   │   │   │   ├── JwtAuthenticationFilter.java
│   │   │   │   └── RateLimitFilter.java
│   │   │   ├── mapper/              # Entity <-> DTO Mappers
│   │   │   │   ├── TransactionMapper.java
│   │   │   │   └── BudgetMapper.java
│   │   │   ├── util/                # Utilities
│   │   │   │   └── DateUtils.java
│   │   │   └── PorquinhoApplication.java
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       ├── application-prod.yml
│   │       └── db/migration/        # Flyway Migrations
│   │           ├── V1__create_users_table.sql
│   │           ├── V2__create_transactions_table.sql
│   │           └── V3__create_budget_tables.sql
│   └── test/
│       └── java/com/porquinho/
│           ├── controller/          # Controller Tests
│           ├── service/             # Service Tests
│           └── repository/          # Repository Tests
└── pom.xml
```

**Frontend (Vue):**
```
porquinho-frontend/
├── src/
│   ├── assets/                      # Static Assets
│   │   ├── images/
│   │   └── fonts/
│   ├── components/                  # Vue Components
│   │   ├── ui/                     # shadcn-vue (copied)
│   │   │   ├── Button.vue
│   │   │   ├── Input.vue
│   │   │   ├── Card.vue
│   │   │   └── Dialog.vue
│   │   ├── dashboard/              # Dashboard Components
│   │   │   ├── DashboardLayer1.vue
│   │   │   ├── DashboardLayer2.vue
│   │   │   ├── DashboardLayer3.vue
│   │   │   ├── HealthSemaphore.vue
│   │   │   └── MetricCard.vue
│   │   ├── transactions/           # Transaction Components
│   │   │   ├── TransactionList.vue
│   │   │   ├── TransactionForm.vue
│   │   │   ├── TransactionCard.vue
│   │   │   └── ClassificationReview.vue
│   │   ├── budget/                 # Budget Components
│   │   │   ├── EnvelopeCard.vue
│   │   │   ├── CategoryAllocation.vue
│   │   │   └── BudgetSummary.vue
│   │   └── common/                 # Shared Components
│   │       ├── AppHeader.vue
│   │       ├── AppSidebar.vue
│   │       ├── LoadingSpinner.vue
│   │       └── ErrorBoundary.vue
│   ├── composables/                # Composition API Reusables
│   │   ├── useTransactions.ts
│   │   ├── useDashboard.ts
│   │   ├── useBudget.ts
│   │   └── useAuth.ts
│   ├── layouts/                    # Layout Components
│   │   ├── AppLayout.vue
│   │   └── AuthLayout.vue
│   ├── router/                     # Vue Router
│   │   └── index.ts
│   ├── stores/                     # Pinia Stores
│   │   ├── auth.ts
│   │   ├── transactions.ts
│   │   ├── budget.ts
│   │   ├── dashboard.ts
│   │   └── ui.ts
│   ├── services/                   # API Services
│   │   ├── api.ts                 # Axios instance
│   │   ├── supabase.ts            # Supabase client
│   │   ├── transactions.ts
│   │   └── dashboard.ts
│   ├── types/                      # TypeScript Types
│   │   ├── index.ts
│   │   ├── transaction.ts
│   │   ├── budget.ts
│   │   └── user.ts
│   ├── utils/                      # Utility Functions
│   │   ├── date.ts
│   │   ├── currency.ts
│   │   └── validation.ts
│   ├── views/                      # Page Components
│   │   ├── DashboardView.vue
│   │   ├── TransactionsView.vue
│   │   ├── BudgetView.vue
│   │   ├── GoalsView.vue
│   │   └── LoginView.vue
│   ├── App.vue
│   └── main.ts
├── public/
├── tests/
│   ├── unit/                       # Vitest Unit Tests
│   │   └── components/
│   └── e2e/                        # Playwright E2E Tests
│       └── dashboard.spec.ts
├── .env.development
├── .env.production
├── vite.config.ts
├── tailwind.config.js
└── package.json
```

---

### File Structure Patterns

**Test Files:**
- ✅ **Co-located with source:** `TransactionService.java` → `TransactionServiceTest.java` (mesmo package)
- ✅ **E2E separados:** `tests/e2e/dashboard.spec.ts`

**Configuration Files:**
- ✅ **Root directory:** `.env`, `vite.config.ts`, `pom.xml`
- ✅ **Resources:** `src/main/resources/application.yml`

**Static Assets:**
- ✅ **Frontend:** `src/assets/images/`, `src/assets/fonts/`
- ✅ **Public:** `public/favicon.ico` (servido na raiz)

**Documentation:**
- ✅ **Root README.md:** Overview, setup instructions
- ✅ **Component docs:** JSDoc comments inline

---

## Format Patterns

### API Response Formats

**Success Response (200, 201):**
```json
// Direct response (sem wrapper)
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "userId": "abc-123",
  "amount": 150.50,
  "description": "Supermercado",
  "transactionDate": "2026-03-13",
  "createdAt": "2026-03-13T10:30:00.000Z",
  "updatedAt": "2026-03-13T10:30:00.000Z"
}

// List response
[
  { "id": "...", "amount": 150.50 },
  { "id": "...", "amount": 200.00 }
]
```

**Error Response (4xx, 5xx) - RFC 7807:**
```json
{
  "type": "https://porquinho.com/errors/validation-error",
  "title": "Validation Error",
  "status": 400,
  "detail": "Campo 'amount' é obrigatório",
  "instance": "/api/v1/transactions",
  "timestamp": "2026-03-13T10:30:00.000Z",
  "errors": [
    {
      "field": "amount",
      "message": "não pode ser nulo",
      "rejectedValue": null
    }
  ]
}
```

**Pagination (quando necessário):**
```json
{
  "content": [
    { "id": "...", "amount": 150.50 },
    { "id": "...", "amount": 200.00 }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 156,
  "totalPages": 8
}
```

**Empty Response:**
```
DELETE /api/v1/transactions/{id}
→ 204 No Content (sem body)

GET /api/v1/transactions
→ 200 OK []
```

---

### Data Exchange Formats

**JSON Field Naming:**
- ✅ **camelCase:** `userId`, `createdAt`, `transactionDate`
- ❌ **NOT:** `user_id`, `created_at`, `transaction_date`

**JPA Entity → JSON Mapping:**
```java
// Entity (snake_case no DB)
@Entity
@Table(name = "transactions")
public class Transaction {

    @Column(name = "user_id")
    private String userId;  // JPA mapeia automaticamente

    @Column(name = "created_at")
    private Instant createdAt;
}

// DTO (camelCase no JSON)
public class TransactionResponse {
    private String userId;      // → "userId" no JSON
    private Instant createdAt;  // → "createdAt" no JSON
}
```

**Date/Time Format:**
- ✅ **ISO 8601 com timezone:** `"2026-03-13T10:30:00.000Z"`
- ✅ **Date only:** `"2026-03-13"`
- ❌ **NOT:** Unix timestamp, formato brasileiro

**Backend Serialization:**
```java
@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
private Instant createdAt;

@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
private LocalDate transactionDate;
```

**Frontend Parsing:**
```typescript
// Date objects
const date = new Date("2026-03-13T10:30:00.000Z")

// Display formatado
import { format } from 'date-fns'
import { ptBR } from 'date-fns/locale'

const formatted = format(date, "dd/MM/yyyy HH:mm", { locale: ptBR })
// → "13/03/2026 10:30"
```

**Boolean Representation:**
- ✅ **JSON:** `true` / `false` (lowercase)
- ✅ **Database:** `BOOLEAN` type
- ❌ **NOT:** `1`/`0`, `"true"`/`"false"` (strings)

**Null Handling:**
- ✅ **Nullable fields:** Incluir como `null` no JSON
- ✅ **Optional fields:** Omitir completamente
```json
{
  "categoryId": null,     // Explicitamente null
  "description": "Test"   // notes omitido (opcional)
}
```

**Currency/Money:**
- ✅ **Type:** `DECIMAL(15, 2)` no DB
- ✅ **JSON:** Number (não string)
```json
{
  "amount": 150.50,        // ✅ Correto
  "amount": "150.50"       // ❌ Errado
}
```

**Arrays vs Single Items:**
- ✅ **Empty list:** `[]` (não `null`)
- ✅ **Single item:** Ainda array `[{...}]`
- ❌ **NOT:** `null` para listas vazias

---

## Communication Patterns

### Event System Patterns

**Pinia Actions (não events propriamente):**
- ✅ **Naming:** Verbos imperativos
```typescript
// transactions.ts store
export const useTransactionStore = defineStore('transactions', () => {

  async function fetchAll() { }      // ✅
  async function create(data) { }    // ✅
  async function update(id, data) { }// ✅
  async function remove(id) { }      // ✅

  // NOT: getTransactions, createTransaction (redundante)
})
```

**Toast/Notification Events:**
```typescript
// ui.ts store
export const useUiStore = defineStore('ui', () => {

  function showSuccess(message: string) {
    // Toast verde
  }

  function showError(message: string) {
    // Toast vermelho
  }

  function showWarning(message: string) {
    // Toast amarelo
  }
})
```

---

### State Management Patterns

**Pinia Store Structure:**
```typescript
// stores/transactions.ts
import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import type { Transaction } from '@/types'

export const useTransactionStore = defineStore('transactions', () => {
  // State
  const items = ref<Transaction[]>([])
  const isLoading = ref(false)
  const error = ref<string | null>(null)

  // Getters (computed)
  const totalAmount = computed(() =>
    items.value.reduce((sum, t) => sum + t.amount, 0)
  )

  const itemCount = computed(() => items.value.length)

  // Actions
  async function fetchAll() {
    isLoading.value = true
    error.value = null
    try {
      const response = await api.get('/transactions')
      items.value = response.data
    } catch (e) {
      error.value = 'Failed to fetch transactions'
    } finally {
      isLoading.value = false
    }
  }

  async function create(data: TransactionRequest) {
    const response = await api.post('/transactions', data)
    items.value.push(response.data)
    return response.data
  }

  function clearError() {
    error.value = null
  }

  return {
    // State
    items,
    isLoading,
    error,
    // Getters
    totalAmount,
    itemCount,
    // Actions
    fetchAll,
    create,
    clearError
  }
})
```

**State Update Patterns:**
- ✅ **Immutable style:** `items.value = [...items.value, newItem]`
- ✅ **Direct mutation ok (Vue 3):** `items.value.push(newItem)`
- Backend state is source of truth, não optimistic updates no MVP

---

## Process Patterns

### Error Handling Patterns

**Backend (Spring Boot):**

**Global Exception Handler:**
```java
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "Validation failed"
        );
        problem.setType(URI.create("https://porquinho.com/errors/validation-error"));
        problem.setTitle("Validation Error");

        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors();
        List<Map<String, Object>> errors = fieldErrors.stream()
            .map(error -> Map.of(
                "field", error.getField(),
                "message", error.getDefaultMessage(),
                "rejectedValue", error.getRejectedValue()
            ))
            .toList();

        problem.setProperty("errors", errors);
        problem.setProperty("timestamp", Instant.now());

        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(ResourceNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND,
            ex.getMessage()
        );
        problem.setType(URI.create("https://porquinho.com/errors/not-found"));
        problem.setTitle("Resource Not Found");
        problem.setProperty("timestamp", Instant.now());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneric(Exception ex) {
        // Log completo para debugging
        logger.error("Unhandled exception", ex);

        // Cliente recebe mensagem genérica (segurança)
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred"
        );
        problem.setType(URI.create("https://porquinho.com/errors/internal-error"));
        problem.setTitle("Internal Server Error");
        problem.setProperty("timestamp", Instant.now());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }
}
```

**Frontend (Vue):**

**Axios Error Interceptor:**
```typescript
// services/api.ts
import axios from 'axios'
import { useUiStore } from '@/stores/ui'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL
})

// Response interceptor: error handling
api.interceptors.response.use(
  response => response,
  async (error) => {
    const uiStore = useUiStore()

    if (error.response) {
      // RFC 7807 Problem Details
      const problem = error.response.data

      switch (error.response.status) {
        case 400:
          // Validation errors
          if (problem.errors) {
            const messages = problem.errors
              .map((e: any) => `${e.field}: ${e.message}`)
              .join(', ')
            uiStore.showError(messages)
          } else {
            uiStore.showError(problem.detail || 'Erro de validação')
          }
          break

        case 401:
          // Unauthorized - redirect to login
          uiStore.showError('Sessão expirada. Faça login novamente.')
          router.push('/login')
          break

        case 403:
          uiStore.showError('Você não tem permissão para esta ação')
          break

        case 404:
          uiStore.showError(problem.detail || 'Recurso não encontrado')
          break

        case 429:
          uiStore.showError('Muitas requisições. Aguarde um momento.')
          break

        case 500:
        default:
          uiStore.showError('Erro inesperado. Tente novamente.')
          // Log para Sentry/monitoring (futuro)
          console.error('API Error:', problem)
      }
    } else if (error.request) {
      // Network error
      uiStore.showError('Erro de conexão. Verifique sua internet.')
    } else {
      uiStore.showError('Erro ao processar requisição')
    }

    return Promise.reject(error)
  }
)

export default api
```

**Component Error Handling:**
```vue
<script setup lang="ts">
import { ref } from 'vue'
import api from '@/services/api'

const isLoading = ref(false)
const error = ref<string | null>(null)

async function handleSubmit() {
  isLoading.value = true
  error.value = null

  try {
    await api.post('/transactions', formData)
    // Success - Axios interceptor já mostra toast
    router.push('/transactions')
  } catch (e) {
    // Error já tratado no interceptor
    // Apenas para lógica local se necessário
    error.value = 'Falha ao criar transação'
  } finally {
    isLoading.value = false
  }
}
</script>

<template>
  <form @submit.prevent="handleSubmit">
    <!-- form fields -->

    <div v-if="error" class="error-message">
      {{ error }}
    </div>

    <Button :disabled="isLoading" type="submit">
      {{ isLoading ? 'Salvando...' : 'Salvar' }}
    </Button>
  </form>
</template>
```

---

### Loading State Patterns

**Naming Conventions:**
- ✅ **Boolean:** `isLoading`, `isSaving`, `isFetching`
- ✅ **Status:** `status: 'idle' | 'loading' | 'success' | 'error'`
- ❌ **NOT:** `loading`, `load`, `fetching` (ambíguo)

**Store Pattern:**
```typescript
export const useTransactionStore = defineStore('transactions', () => {
  const items = ref<Transaction[]>([])
  const isLoading = ref(false)
  const isSaving = ref(false)
  const error = ref<string | null>(null)

  async function fetchAll() {
    isLoading.value = true
    error.value = null
    try {
      const response = await api.get('/transactions')
      items.value = response.data
    } catch (e) {
      error.value = 'Failed to fetch'
    } finally {
      isLoading.value = false
    }
  }

  async function create(data: TransactionRequest) {
    isSaving.value = true
    try {
      const response = await api.post('/transactions', data)
      items.value.push(response.data)
      return response.data
    } finally {
      isSaving.value = false
    }
  }

  return { items, isLoading, isSaving, error, fetchAll, create }
})
```

**Component Pattern:**
```vue
<script setup lang="ts">
const transactionStore = useTransactionStore()
const { items, isLoading } = storeToRefs(transactionStore)

onMounted(() => {
  transactionStore.fetchAll()
})
</script>

<template>
  <div>
    <!-- Loading spinner -->
    <LoadingSpinner v-if="isLoading" />

    <!-- Empty state -->
    <div v-else-if="items.length === 0">
      Nenhuma transação encontrada
    </div>

    <!-- Content -->
    <TransactionList v-else :transactions="items" />
  </div>
</template>
```

**Button Loading:**
```vue
<Button :disabled="isLoading" @click="handleAction">
  <Loader2 v-if="isLoading" class="animate-spin" />
  {{ isLoading ? 'Processando...' : 'Confirmar' }}
</Button>
```

---

## Enforcement Guidelines

### All AI Agents MUST:

1. **Follow Naming Conventions:**
   - Database: `snake_case`
   - JSON: `camelCase`
   - API Endpoints: Plural nouns
   - Files: Match class/component names

2. **Use Established Project Structure:**
   - Backend: controller/service/repository layers
   - Frontend: components by feature, composables, stores
   - Tests: Co-located with source

3. **Implement Consistent Error Handling:**
   - Backend: RFC 7807 Problem Details via `@ControllerAdvice`
   - Frontend: Axios interceptors + UI store toasts
   - Never expose internal errors to users

4. **Use Defined Data Formats:**
   - Dates: ISO 8601 with timezone
   - Currency: DECIMAL(15,2), JSON number
   - Booleans: `true`/`false` (not strings)
   - Empty lists: `[]` (not `null`)

5. **Respect Loading State Patterns:**
   - Boolean: `isLoading`, `isSaving`
   - Store-level loading states
   - Disable buttons during actions

6. **Maintain Audit Trail:**
   - All tables: `created_at`, `updated_at`, `deleted_at`
   - Soft deletes only (never hard delete user data)
   - Log sensitive operations (NFR20)

7. **Follow Security Patterns:**
   - All queries filter by `userId` from JWT
   - CORS configured for known origins only
   - Rate limiting on auth endpoints
   - Passwords via BCrypt (if local auth)

---

### Pattern Enforcement

**Automated Checks:**
- ESLint (frontend): naming, imports, unused vars
- Checkstyle (backend): naming, formatting
- Flyway: database migrations validated before deploy
- GitHub Actions: lint/test fail = block merge

**Code Review Checklist:**
- [ ] Naming conventions followed?
- [ ] Error handling implemented?
- [ ] Loading states managed?
- [ ] Audit columns present (new tables)?
- [ ] Tests co-located?
- [ ] RFC 7807 errors (backend)?
- [ ] TypeScript types defined (frontend)?

**Pattern Violations:**
- Document in PR comments
- Fix before merge
- Update architecture.md if pattern needs adjustment

**Updating Patterns:**
- Discuss in team meeting
- Update architecture.md
- Create migration guide for existing code
- Run codemod/refactor if possible

---

## Pattern Examples

### Good Examples

**Backend Controller:**
```java
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    @GetMapping
    public List<TransactionResponse> getTransactions(
        @AuthenticationPrincipal String userId,
        @RequestParam(required = false) String month
    ) {
        return transactionService.findByUserId(userId, month);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse createTransaction(
        @AuthenticationPrincipal String userId,
        @Valid @RequestBody TransactionRequest request
    ) {
        return transactionService.create(userId, request);
    }
}
```

**Frontend Component:**
```vue
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useTransactionStore } from '@/stores/transactions'
import { storeToRefs } from 'pinia'
import TransactionCard from './TransactionCard.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'

const transactionStore = useTransactionStore()
const { items, isLoading } = storeToRefs(transactionStore)

onMounted(async () => {
  await transactionStore.fetchAll()
})
</script>

<template>
  <div class="transaction-list">
    <h2 class="text-2xl font-bold mb-4">Transações</h2>

    <LoadingSpinner v-if="isLoading" />

    <div v-else-if="items.length === 0" class="text-gray-500">
      Nenhuma transação encontrada
    </div>

    <div v-else class="space-y-2">
      <TransactionCard
        v-for="transaction in items"
        :key="transaction.id"
        :transaction="transaction"
      />
    </div>
  </div>
</template>
```

**Database Migration:**
```sql
-- V1__create_transactions_table.sql
CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    category_id UUID REFERENCES budget_categories(id),
    transaction_date DATE NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    description VARCHAR(500),
    classification_metadata JSONB,
    created_at TIMESTAMP DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMP DEFAULT NOW() NOT NULL,
    created_by UUID REFERENCES users(id),
    updated_by UUID REFERENCES users(id),
    deleted_at TIMESTAMP NULL,
    deleted_by UUID REFERENCES users(id)
);

CREATE INDEX idx_transactions_user_id ON transactions(user_id);
CREATE INDEX idx_transactions_date ON transactions(transaction_date);
CREATE INDEX idx_transactions_deleted ON transactions(deleted_at) WHERE deleted_at IS NULL;
```

---

### Anti-Patterns (What to Avoid)

**❌ Wrong Naming:**
```java
// BAD: Inconsistent naming
@RestController
@RequestMapping("/api/v1/transaction")  // ❌ Singular
public class TransactionController {

    @GetMapping
    public List<TransactionDTO> get_transactions(  // ❌ snake_case
        @AuthenticationPrincipal String user_id     // ❌ snake_case
    ) {
        return service.getTransactionsByUserId(user_id);
    }
}
```

**❌ Wrong Error Handling:**
```java
// BAD: Não usa RFC 7807
@ExceptionHandler(ValidationException.class)
public ResponseEntity<Map<String, String>> handleValidation(ValidationException ex) {
    return ResponseEntity.badRequest()
        .body(Map.of("error", ex.getMessage()));  // ❌ Formato custom
}
```

**❌ Wrong Database Schema:**
```sql
-- BAD: Wrong naming, missing audit columns
CREATE TABLE Transaction (              -- ❌ PascalCase
    ID UUID PRIMARY KEY,                -- ❌ Uppercase
    userId UUID NOT NULL,               -- ❌ camelCase
    transactionDate DATE,               -- ❌ camelCase
    Amount DECIMAL(10,2)                -- ❌ Uppercase, insufficient precision
);
-- ❌ Missing: created_at, updated_at, deleted_at, indexes
```

**❌ Wrong JSON Response:**
```json
// BAD: Wrong field naming
{
  "user_id": "abc-123",        // ❌ snake_case
  "created_at": 1710328200,    // ❌ Unix timestamp
  "amount": "150.50",          // ❌ String (deveria ser number)
  "errors": null               // ❌ null ao invés de array vazio
}
```

**❌ Wrong Frontend Pattern:**
```vue
<script setup lang="ts">
// BAD: Loading state inconsistente
const loading = ref(false)      // ❌ Deveria ser isLoading

// BAD: Não usa store
async function getTransactions() {
  loading.value = true
  const response = await fetch('/api/v1/transactions')  // ❌ Usa fetch direto
  transactions.value = await response.json()
  loading.value = false  // ❌ Não tem finally
}
</script>
```

---

**Padrões de implementação completos documentados!**

Estes padrões garantem que diferentes agentes de IA escrevam código compatível e consistente.

**O que você gostaria de fazer?**

**[A] Advanced Elicitation** - Explorar padrões adicionais de consistência
**[P] Party Mode** - Revisar padrões de diferentes perspectivas de implementação
**[C] Continue** - Salvar estes padrões e avançar para estrutura do projeto

---
