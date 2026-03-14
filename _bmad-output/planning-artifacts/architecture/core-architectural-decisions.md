# Core Architectural Decisions

## Decision Priority Analysis

**Critical Decisions (Block Implementation):**
1. Authentication Flow: JWT Validation (Supabase Auth → Spring Boot)
2. Data Migrations: Flyway
3. API Error Handling: RFC 7807 Problem Details
4. Caching Strategy: Redis (Dashboard GPS queries)

**Important Decisions (Shape Architecture):**
5. API Versioning: `/v1/` no path
6. Authorization Pattern: RBAC Simples (USER/ADMIN roles)
7. API Documentation: OpenAPI/Swagger auto-generated
8. CI/CD Pipeline: GitHub Actions

**Deferred Decisions (Post-MVP):**
9. Observability Stack: Prometheus + Grafana (implementar quando tiver carga real)
10. Feature Flags: Não implementar no MVP (YAGNI - adicionar se necessário)

---

## Data Architecture

### Database: PostgreSQL via Supabase

**Decision:** PostgreSQL 16+ managed via Supabase
**Version:** PostgreSQL 16+ (Supabase latest)
**Provided by:** User preference (Step 3)

**Rationale:**
- ACID compliance essencial para transações financeiras
- Supabase oferece Auth built-in (Google OAuth)
- Row Level Security (RLS) para segregação de dados (NFR15)
- Realtime capabilities para dashboard updates (futuro)
- Storage para anexos/comprovantes (futuro)

**Affects:**
- Todas as JPA entities
- Spring Boot datasource configuration
- Auth flow (JWT validation)

**Configuration:**
```yaml
spring:
  datasource:
    url: jdbc:postgresql://db.[PROJECT-REF].supabase.co:5432/postgres
    username: postgres
    password: ${SUPABASE_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
  jpa:
    hibernate:
      ddl-auto: validate  # Prod: validate, Dev: update
    show-sql: false       # Dev only: true
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
        default_schema: public
```

---

### Data Migrations: Flyway

**Decision:** Flyway para database migrations
**Version:** Flyway 10.x (latest com Spring Boot 3.4.x)
**Rationale:**
- SQL-based migrations (mais controle que Hibernate auto-DDL)
- Versionamento explícito de schema changes
- Rollback support
- Mais simples que Liquibase para equipe pequena
- Excelente integração com Spring Boot

**Affects:**
- Database schema evolution
- Deploy process (migrations rodam antes da aplicação)
- CI/CD pipeline (validação de migrations)

**Implementation:**
```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
```

```yaml
# application.yml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration
    validate-on-migrate: true
```

**Migration File Convention:**
```
src/main/resources/db/migration/
├── V1__create_users_table.sql
├── V2__create_transactions_table.sql
├── V3__create_categories_table.sql
└── V4__add_budget_tables.sql
```

---

### Data Modeling Approach

**Decision:** Multi-tenant via `user_id` foreign key (não schema separado)
**Rationale:**
- SaaS single-tenant architecture
- Simples query pattern: `WHERE user_id = :authenticatedUserId`
- Suporte a Row Level Security do Supabase
- Escalável para 1.000+ usuários

**Schema Design Principles:**
1. **Audit Columns (todas as tabelas):**
   ```sql
   created_at TIMESTAMP DEFAULT NOW()
   updated_at TIMESTAMP DEFAULT NOW()
   created_by VARCHAR(255)  -- user_id do JWT
   updated_by VARCHAR(255)
   ```

2. **Soft Deletes (LGPD compliance):**
   ```sql
   deleted_at TIMESTAMP NULL
   deleted_by VARCHAR(255)
   ```

3. **User Segregation:**
   ```sql
   user_id UUID NOT NULL REFERENCES users(id)
   INDEX idx_user_id ON table_name(user_id)
   ```

4. **Flexible Data (IA metadata):**
   ```sql
   metadata JSONB  -- Para classificação IA, scores, etc
   ```

**Affects:**
- Todas as JPA entities herdam de `AuditableEntity`
- Repositories sempre filtram por `user_id`
- Soft delete via `@Where(clause = "deleted_at IS NULL")`

---

### Caching Strategy: Redis

**Decision:** Redis para cache de queries do Dashboard GPS
**Version:** Redis 7.x (latest stable)
**Rationale:**
- Dashboard GPS precisa < 2s load time (NFR1)
- Cálculos complexos em 3 camadas (Layer 1, 2, 3)
- TTL configurável por layer:
  - Layer 1 (métricas principais): 30 segundos
  - Layer 2 (tendências): 5 minutos
  - Layer 3 (análise detalhada): 15 minutos
- Invalidação ao classificar transação ou alterar orçamento

**Affects:**
- Dashboard queries (DashboardService)
- Transaction classification (invalida cache)
- Budget allocation (invalida cache)

**Implementation:**
```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
```

```yaml
# application.yml
spring:
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD}
    timeout: 2000ms
  cache:
    type: redis
    redis:
      time-to-live: 300000  # 5 minutes default
      cache-null-values: false
```

```java
@Service
public class DashboardService {

    @Cacheable(value = "dashboard-layer1", key = "#userId")
    public DashboardLayer1DTO getLayer1(String userId) {
        // Cálculos complexos aqui
    }

    @CacheEvict(value = {"dashboard-layer1", "dashboard-layer2", "dashboard-layer3"},
                key = "#userId")
    public void invalidateDashboardCache(String userId) {
        // Chamado após classificação ou mudança de orçamento
    }
}
```

**Cache Keys Pattern:**
```
dashboard-layer1:{userId}
dashboard-layer2:{userId}
dashboard-layer3:{userId}
transactions-summary:{userId}:{month}
```

**Deploy:**
- Dev: Redis local via Docker
- Prod: Redis no Dokploy (container separado) ou Redis Cloud

---

## Authentication & Security

### Authentication Flow: JWT Validation

**Decision:** Supabase Auth (frontend) + JWT validation (backend)
**Architecture:** Stateless JWT-based authentication
**Rationale:**
- Supabase maneja complexidade OAuth (Google)
- Backend stateless (escalável horizontalmente)
- JWT contém claims necessários (user_id, email, roles)
- Row Level Security do Supabase alinha com arquitetura
- Pattern comum em arquiteturas separadas (SPA + API)

**Flow:**
```
1. Frontend Vue → Supabase Auth
   supabase.auth.signInWithOAuth({ provider: 'google' })

2. Supabase → Google OAuth → User consent

3. Supabase retorna JWT token
   { access_token, refresh_token, expires_in }

4. Frontend guarda token
   localStorage.setItem('supabase.auth.token', token)

5. Frontend envia em todas requisições
   Authorization: Bearer {access_token}

6. Spring Boot valida JWT
   - Verifica assinatura via JWKS endpoint Supabase
   - Extrai claims (sub = user_id, email, role)
   - Cria SecurityContext com user_id

7. Controllers usam user_id
   @AuthenticationPrincipal String userId
```

**Affects:**
- SecurityConfig.java (JWT filter, JWKS configuration)
- Frontend Axios interceptors
- Todos os controllers (@AuthenticationPrincipal)

**Implementation (Backend):**
```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

```yaml
# application.yml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://[PROJECT-REF].supabase.co/auth/v1
          jwk-set-uri: https://[PROJECT-REF].supabase.co/auth/v1/jwks
```

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())  // Stateless JWT
            .cors(cors -> cors.configurationSource(corsConfig()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/health").permitAll()
                .requestMatchers("/api/v1/**").authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter()))
            );
        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("sub");  // user_id
        return converter;
    }
}
```

**Implementation (Frontend):**
```typescript
// src/services/api.ts
import axios from 'axios';
import { supabase } from './supabase';

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
});

// Interceptor: adiciona JWT em todas requisições
api.interceptors.request.use(async (config) => {
  const { data: { session } } = await supabase.auth.getSession();
  if (session?.access_token) {
    config.headers.Authorization = `Bearer ${session.access_token}`;
  }
  return config;
});

// Interceptor: refresh token se expirou
api.interceptors.response.use(
  response => response,
  async (error) => {
    if (error.response?.status === 401) {
      const { data: { session } } = await supabase.auth.refreshSession();
      if (session) {
        error.config.headers.Authorization = `Bearer ${session.access_token}`;
        return api.request(error.config);
      }
    }
    return Promise.reject(error);
  }
);
```

---

### Authorization Pattern: RBAC Simples

**Decision:** Role-Based Access Control (USER/ADMIN)
**Rationale:**
- SaaS single-tenant (usuários não compartilham dados)
- Apenas 2 roles necessários no MVP
- Segregação é por `user_id`, não permissions complexas
- Spring Security tem suporte excelente
- Simples de implementar e manter

**Roles:**
```sql
CREATE TYPE user_role AS ENUM ('USER', 'ADMIN');

ALTER TABLE users ADD COLUMN role user_role DEFAULT 'USER';
```

**Authorization Strategy:**
1. **USER role:** Acessa apenas seus próprios dados
   ```java
   @GetMapping("/api/v1/transactions")
   @PreAuthorize("hasRole('USER')")
   public List<Transaction> getTransactions(@AuthenticationPrincipal String userId) {
       return transactionService.findByUserId(userId);
   }
   ```

2. **ADMIN role:** Acessa métricas agregadas, health checks
   ```java
   @GetMapping("/api/v1/admin/metrics")
   @PreAuthorize("hasRole('ADMIN')")
   public SystemMetrics getMetrics() {
       return metricsService.getSystemMetrics();
   }
   ```

3. **Query-level enforcement:**
   ```java
   @Repository
   public interface TransactionRepository extends JpaRepository<Transaction, Long> {

       @Query("SELECT t FROM Transaction t WHERE t.userId = :userId AND t.deletedAt IS NULL")
       List<Transaction> findByUserId(@Param("userId") String userId);
   }
   ```

**Affects:**
- JWT claims (role incluído no token)
- Controllers (@PreAuthorize annotations)
- Repositories (query filters)

**Permission-based NO MVP porque:**
- Não há compartilhamento entre usuários
- Não há hierarquia organizacional
- YAGNI (You Ain't Gonna Need It)

---

### Security Middleware & Enforcement

**CORS Configuration:**
```java
@Bean
public CorsConfigurationSource corsConfig() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of(
        "http://localhost:5173",  // Dev: Vite
        "https://porquinho.com"   // Prod
    ));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    config.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/v1/**", config);
    return source;
}
```

**Rate Limiting (NFR17: 5 tentativas/minuto em login):**
```xml
<dependency>
    <groupId>com.github.vladimir-bukhtoyarov</groupId>
    <artifactId>bucket4j-core</artifactId>
</dependency>
```

```java
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> cache = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws IOException, ServletException {

        String ip = request.getRemoteAddr();
        Bucket bucket = cache.computeIfAbsent(ip, this::createBucket);

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            response.setStatus(429);  // Too Many Requests
            response.getWriter().write("Rate limit exceeded");
        }
    }

    private Bucket createBucket(String ip) {
        return Bucket.builder()
            .addLimit(Bandwidth.simple(5, Duration.ofMinutes(1)))
            .build();
    }
}
```

**Data Encryption (NFR11-13):**
- **Em trânsito:** TLS 1.3 (configuração Nginx/Dokploy)
- **Em repouso:** AES-256 (Supabase managed)
- **Passwords:** BCrypt (Spring Security default)
  ```java
  @Bean
  public PasswordEncoder passwordEncoder() {
      return new BCryptPasswordEncoder(12);  // Strength 12
  }
  ```

**Audit Logging (NFR20):**
```java
@Aspect
@Component
public class AuditAspect {

    @AfterReturning("@annotation(Auditable)")
    public void logAuditEvent(JoinPoint joinPoint) {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        String action = joinPoint.getSignature().getName();
        String ip = getCurrentRequestIp();

        auditLogRepository.save(new AuditLog(
            userId, action, ip, Instant.now()
        ));
    }
}

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {}

// Uso:
@Auditable
public void deleteAccount(String userId) { /* ... */ }
```

---

## API & Communication Patterns

### API Design: RESTful JSON

**Decision:** REST APIs com JSON, seguindo convenções HTTP
**Version:** OpenAPI 3.1 (Swagger)
**Provided by:** Spring Boot starter (Spring Web)

**Rationale:**
- Simples e bem compreendido
- Excelente suporte em Spring Boot e Vue/Axios
- Não precisa complexidade GraphQL no MVP
- HTTP status codes semânticos

**API Conventions:**
```
GET    /api/v1/transactions          → Lista transações do usuário
GET    /api/v1/transactions/{id}     → Busca transação específica
POST   /api/v1/transactions          → Cria nova transação
PUT    /api/v1/transactions/{id}     → Atualiza transação completa
PATCH  /api/v1/transactions/{id}     → Atualiza campos específicos
DELETE /api/v1/transactions/{id}     → Soft delete (deleted_at)
```

**HTTP Status Codes:**
```
200 OK                  → Sucesso (GET, PUT, PATCH)
201 Created             → Recurso criado (POST)
204 No Content          → Sucesso sem body (DELETE)
400 Bad Request         → Validação falhou
401 Unauthorized        → JWT inválido/ausente
403 Forbidden           → Sem permissão para recurso
404 Not Found           → Recurso não existe
409 Conflict            → Conflito (ex: duplicate key)
429 Too Many Requests   → Rate limit excedido
500 Internal Error      → Erro não tratado
```

---

### API Versioning: Path-based `/v1/`

**Decision:** Versioning no path (`/api/v1/`, `/api/v2/`)
**Rationale:**
- Mais explícito e visível que headers
- Fácil de testar (URL direto no browser)
- Suporte natural em Spring Boot (`@RequestMapping("/api/v1")`)
- Simples de documentar (Swagger por versão)
- Cache-friendly (diferentes URLs = diferentes caches)

**Structure:**
```java
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {
    // Endpoints v1
}

// Futuro v2:
@RestController
@RequestMapping("/api/v2/transactions")
public class TransactionControllerV2 {
    // Breaking changes aqui
}
```

**Versioning Strategy:**
- v1: MVP completo
- v2: Apenas se houver breaking changes
- Backward compatibility sempre que possível

**Affects:**
- Frontend base URL: `https://api.porquinho.com/api/v1`
- Swagger UI: `/swagger-ui.html` (todas versões)
- Deprecation: v1 mantida por 6 meses após v2 lançar

---

### API Error Handling: RFC 7807 Problem Details

**Decision:** RFC 7807 Problem Details para respostas de erro
**Version:** Spring Boot 3.x native support
**Rationale:**
- Padrão oficial (RFC 7807)
- Estrutura consistente e documentada
- Spring Boot tem suporte nativo (`ProblemDetail` class)
- OpenAPI/Swagger entende automaticamente
- Frontend tem contrato claro

**Error Response Format:**
```json
{
  "type": "https://porquinho.com/errors/validation-error",
  "title": "Validation Error",
  "status": 400,
  "detail": "Campo 'valor' é obrigatório",
  "instance": "/api/v1/transactions",
  "timestamp": "2026-03-13T14:30:00Z",
  "errors": [
    {
      "field": "valor",
      "message": "não pode ser nulo",
      "rejectedValue": null
    }
  ]
}
```

**Implementation:**
```java
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationException(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "Validation failed"
        );
        problem.setType(URI.create("https://porquinho.com/errors/validation-error"));
        problem.setTitle("Validation Error");
        problem.setProperty("errors", extractErrors(ex));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND,
            ex.getMessage()
        );
        problem.setType(URI.create("https://porquinho.com/errors/not-found"));
        problem.setTitle("Resource Not Found");
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        // Log completo internamente
        logger.error("Unhandled exception", ex);

        // Cliente recebe mensagem genérica (segurança)
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred"
        );
        problem.setType(URI.create("https://porquinho.com/errors/internal-error"));
        problem.setTitle("Internal Server Error");
        return problem;
    }
}
```

**Affects:**
- Todos os controllers (via @ControllerAdvice)
- Frontend error handling (Axios interceptors)
- OpenAPI docs (error schemas)

---

### API Documentation: OpenAPI/Swagger Auto-Generated

**Decision:** springdoc-openapi para documentação automática
**Version:** springdoc-openapi 2.x (compatível Spring Boot 3.x)
**Rationale:**
- Geração automática a partir de anotações
- UI interativo para testar endpoints
- Export OpenAPI 3.1 spec (pode gerar clients)
- Zero manutenção (atualiza com código)

**Implementation:**
```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.3.0</version>
</dependency>
```

```yaml
# application.yml
springdoc:
  api-docs:
    path: /api/v1/api-docs
  swagger-ui:
    path: /swagger-ui.html
    operations-sorter: method
    tags-sorter: alpha
  show-actuator: true
```

```java
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI porquinhoOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Porquinho API")
                .version("v1")
                .description("API de gestão financeira pessoal brasileira")
                .contact(new Contact()
                    .name("Porquinho Team")
                    .email("api@porquinho.com")))
            .components(new Components()
                .addSecuritySchemes("bearer-jwt", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")));
    }
}

// Controller annotations:
@RestController
@RequestMapping("/api/v1/transactions")
@Tag(name = "Transactions", description = "Gestão de transações financeiras")
public class TransactionController {

    @Operation(summary = "Lista transações",
               description = "Retorna todas as transações do usuário autenticado")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Sucesso"),
        @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    @GetMapping
    public List<TransactionDTO> getTransactions(
        @AuthenticationPrincipal String userId,
        @Parameter(description = "Mês de referência (YYYY-MM)")
        @RequestParam(required = false) String month
    ) {
        // ...
    }
}
```

**Affects:**
- Documentação viva em `/swagger-ui.html`
- Frontend pode gerar TypeScript types do spec
- Facilita onboarding de novos desenvolvedores

---

## Frontend Architecture

### State Management: Pinia

**Decision:** Pinia para estado global
**Version:** Pinia 2.x
**Provided by:** create-vue starter
**Rationale:**
- Sucessor oficial do Vuex
- API mais simples (Composition API style)
- TypeScript support excelente
- DevTools integration
- Modular (stores independentes)

**Store Structure:**
```typescript
// src/stores/auth.ts
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { supabase } from '@/services/supabase'

export const useAuthStore = defineStore('auth', () => {
  const user = ref(null)
  const token = ref<string | null>(null)

  const isAuthenticated = computed(() => !!user.value)

  async function signIn() {
    const { data, error } = await supabase.auth.signInWithOAuth({
      provider: 'google'
    })
    if (!error) {
      user.value = data.user
      token.value = data.session.access_token
    }
  }

  async function signOut() {
    await supabase.auth.signOut()
    user.value = null
    token.value = null
  }

  return { user, token, isAuthenticated, signIn, signOut }
})
```

**Store Categories:**
```
src/stores/
├── auth.ts           # Autenticação e user session
├── dashboard.ts      # Dashboard GPS state (3 layers)
├── transactions.ts   # Transações e classificação
├── budget.ts         # Orçamento por envelope
├── goals.ts          # Metas financeiras
└── ui.ts            # Estado UI (modals, toasts, loading)
```

**Affects:**
- Components accessam via `const authStore = useAuthStore()`
- Persisted state via `pinia-plugin-persistedstate` (localStorage)

---

### Component Architecture: Shadcn-vue + Custom

**Decision:** shadcn-vue (copy-paste) + custom components
**Version:** shadcn-vue latest
**Provided by:** User preference (Step 3)
**Rationale:**
- Copy-paste approach = total controle
- WCAG 2.1 AA compliance built-in
- Tailwind CSS aligned
- Purple (#9333EA) theme customized

**Component Structure:**
```
src/components/
├── ui/                    # shadcn-vue components (copiados)
│   ├── Button.vue
│   ├── Input.vue
│   ├── Card.vue
│   ├── Dialog.vue
│   └── ...
├── dashboard/            # Dashboard GPS específicos
│   ├── DashboardLayer1.vue
│   ├── DashboardLayer2.vue
│   ├── DashboardLayer3.vue
│   ├── HealthSemaphore.vue
│   └── MetricCard.vue
├── transactions/         # Transações
│   ├── TransactionList.vue
│   ├── TransactionForm.vue
│   └── ClassificationReview.vue
├── budget/               # Orçamento
│   ├── EnvelopeCard.vue
│   ├── CategoryAllocation.vue
│   └── BudgetSummary.vue
└── common/               # Shared
    ├── AppHeader.vue
    ├── AppSidebar.vue
    └── LoadingSpinner.vue
```

**Component Conventions:**
- Composition API (`<script setup>`)
- TypeScript props interface
- Emits explicit
- Slots com fallback

**Example:**
```vue
<script setup lang="ts">
interface Props {
  transaction: Transaction
  loading?: boolean
}

interface Emits {
  (e: 'classify', id: number): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()
</script>

<template>
  <Card class="transaction-card">
    <!-- ... -->
  </Card>
</template>
```

---

### Routing Strategy: Vue Router + Lazy Loading

**Decision:** Vue Router com code splitting por route
**Version:** Vue Router 4.x
**Provided by:** create-vue starter
**Rationale:**
- SPA navigation
- Lazy loading = melhor performance (NFR4: page load < 3s)
- Route guards para autenticação
- Meta fields para breadcrumbs, titles

**Route Structure:**
```typescript
// src/router/index.ts
import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: () => import('@/layouts/AppLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        {
          path: '',
          name: 'dashboard',
          component: () => import('@/views/DashboardView.vue'),
          meta: { title: 'Dashboard GPS' }
        },
        {
          path: 'transactions',
          name: 'transactions',
          component: () => import('@/views/TransactionsView.vue'),
          meta: { title: 'Transações' }
        },
        {
          path: 'budget',
          name: 'budget',
          component: () => import('@/views/BudgetView.vue'),
          meta: { title: 'Orçamento' }
        },
        {
          path: 'goals',
          name: 'goals',
          component: () => import('@/views/GoalsView.vue'),
          meta: { title: 'Metas' }
        }
      ]
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { requiresAuth: false }
    }
  ]
})

// Route guard: autenticação
router.beforeEach((to, from, next) => {
  const authStore = useAuthStore()

  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    next('/login')
  } else if (to.path === '/login' && authStore.isAuthenticated) {
    next('/')
  } else {
    next()
  }
})

// Route guard: page title
router.afterEach((to) => {
  document.title = `${to.meta.title || 'Dashboard'} | Porquinho`
})

export default router
```

**Lazy Loading Benefits:**
- Bundle split: dashboard.js, transactions.js, budget.js...
- Initial load < 3s (NFR4)
- Navegação subsequente < 500ms (NFR2)

---

### Performance Optimization Patterns

**Decision:** Implementar patterns críticos para NFRs
**Rationale:** Dashboard < 2s (NFR1), Navegação < 500ms (NFR2)

**Patterns:**

1. **Lazy Loading Components:**
   ```vue
   <script setup>
   import { defineAsyncComponent } from 'vue'

   const HeavyChart = defineAsyncComponent(() =>
     import('./HeavyChart.vue')
   )
   </script>
   ```

2. **Virtual Scrolling (transações grandes):**
   ```bash
   npm install vue-virtual-scroller
   ```
   ```vue
   <RecycleScroller
     :items="transactions"
     :item-size="80"
     key-field="id"
   >
     <template #default="{ item }">
       <TransactionRow :transaction="item" />
     </template>
   </RecycleScroller>
   ```

3. **Debouncing (search, filters):**
   ```typescript
   import { useDebounceFn } from '@vueuse/core'

   const debouncedSearch = useDebounceFn((value: string) => {
     searchTransactions(value)
   }, 300)
   ```

4. **Memoization (computed):**
   ```typescript
   const expensiveSummary = computed(() => {
     // Cálculo pesado
     return transactions.value.reduce(/* ... */)
   })
   ```

5. **Keep-alive (preserve state):**
   ```vue
   <router-view v-slot="{ Component }">
     <keep-alive :include="['DashboardView', 'TransactionsView']">
       <component :is="Component" />
     </keep-alive>
   </router-view>
   ```

**Affects:**
- Dashboard GPS (3 layers) performance
- Lista de transações (pode ter centenas)
- Gráficos e visualizações

---

## Infrastructure & Deployment

### Hosting Strategy: VPS Hostinger + Dokploy

**Decision:** Self-hosting em VPS Hostinger com Dokploy
**Rationale:**
- VPS já existe (custo fixo conhecido)
- Dokploy = Docker orchestration simples
- Controle total sobre infra
- Adequado para 1.000 usuários MVP

**Architecture:**
```
VPS Hostinger (Ubuntu 22.04 LTS)
├── Dokploy (Docker orchestration)
│   ├── Container: porquinho-frontend (Nginx)
│   ├── Container: porquinho-backend (Java 21)
│   ├── Container: Redis (cache)
│   └── Container: Nginx (reverse proxy)
├── Supabase (external, managed PostgreSQL)
└── Domain: porquinho.com (DNS)
```

**Dockerfile Frontend:**
```dockerfile
# Build stage
FROM node:22-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# Production stage
FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

**Dockerfile Backend:**
```dockerfile
FROM openjdk:21-jdk-slim
WORKDIR /app
COPY target/porquinho-backend-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx512m", "-jar", "app.jar"]
```

**docker-compose.yml (Dokploy uses):**
```yaml
version: '3.8'

services:
  frontend:
    build: ./porquinho-frontend
    ports:
      - "3000:80"
    environment:
      - VITE_API_BASE_URL=https://api.porquinho.com
      - VITE_SUPABASE_URL=${SUPABASE_URL}
      - VITE_SUPABASE_ANON_KEY=${SUPABASE_ANON_KEY}
    restart: unless-stopped

  backend:
    build: ./porquinho-backend
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - SPRING_DATASOURCE_URL=${SUPABASE_DB_URL}
      - SPRING_DATASOURCE_PASSWORD=${SUPABASE_DB_PASSWORD}
      - REDIS_HOST=redis
      - JWT_ISSUER_URI=${SUPABASE_URL}/auth/v1
    depends_on:
      - redis
    restart: unless-stopped

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    restart: unless-stopped

  nginx:
    image: nginx:alpine
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf
      - ./ssl:/etc/nginx/ssl
    depends_on:
      - frontend
      - backend
    restart: unless-stopped

volumes:
  redis-data:
```

**Nginx Reverse Proxy:**
```nginx
server {
    listen 80;
    server_name porquinho.com;
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl http2;
    server_name porquinho.com;

    ssl_certificate /etc/nginx/ssl/cert.pem;
    ssl_certificate_key /etc/nginx/ssl/key.pem;

    # Frontend
    location / {
        proxy_pass http://frontend:80;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # Backend API
    location /api/ {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

**Affects:**
- Deploy process (git push → CI/CD → Dokploy redeploy)
- Environment variables management
- SSL certificates (Let's Encrypt)

---

### CI/CD Pipeline: GitHub Actions

**Decision:** GitHub Actions para CI/CD
**Rationale:**
- Integração nativa com GitHub
- Free tier generoso (2.000 min/mês)
- Marketplace rico (actions prontas)
- YAML configuration simples
- Secrets management built-in

**Pipeline Stages:**
1. **Test:** Unit tests + E2E tests
2. **Build:** Maven package (backend) + Vite build (frontend)
3. **Deploy:** Push para Dokploy via SSH

**Workflow Frontend:**
```yaml
# .github/workflows/frontend.yml
name: Frontend CI/CD

on:
  push:
    branches: [main, develop]
    paths:
      - 'porquinho-frontend/**'
  pull_request:
    branches: [main]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Setup Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '22'
          cache: 'npm'
          cache-dependency-path: porquinho-frontend/package-lock.json

      - name: Install dependencies
        working-directory: porquinho-frontend
        run: npm ci

      - name: Run linter
        working-directory: porquinho-frontend
        run: npm run lint

      - name: Run unit tests
        working-directory: porquinho-frontend
        run: npm run test:unit

      - name: Run E2E tests
        working-directory: porquinho-frontend
        run: npm run test:e2e

  build:
    needs: test
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    steps:
      - uses: actions/checkout@v4

      - name: Setup Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '22'

      - name: Build
        working-directory: porquinho-frontend
        run: |
          npm ci
          npm run build

      - name: Upload artifact
        uses: actions/upload-artifact@v4
        with:
          name: frontend-dist
          path: porquinho-frontend/dist

  deploy:
    needs: build
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    steps:
      - name: Download artifact
        uses: actions/download-artifact@v4
        with:
          name: frontend-dist

      - name: Deploy to VPS
        uses: appleboy/ssh-action@v1.0.0
        with:
          host: ${{ secrets.VPS_HOST }}
          username: ${{ secrets.VPS_USER }}
          key: ${{ secrets.VPS_SSH_KEY }}
          script: |
            cd /opt/dokploy/porquinho
            docker-compose pull frontend
            docker-compose up -d frontend
```

**Workflow Backend:**
```yaml
# .github/workflows/backend.yml
name: Backend CI/CD

on:
  push:
    branches: [main, develop]
    paths:
      - 'porquinho-backend/**'
  pull_request:
    branches: [main]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Setup Java
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'
          cache: 'maven'

      - name: Run tests
        working-directory: porquinho-backend
        run: ./mvnw test

      - name: Run integration tests
        working-directory: porquinho-backend
        run: ./mvnw verify -P integration-tests

  build:
    needs: test
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    steps:
      - uses: actions/checkout@v4

      - name: Setup Java
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'
          cache: 'maven'

      - name: Build JAR
        working-directory: porquinho-backend
        run: ./mvnw clean package -DskipTests

      - name: Upload artifact
        uses: actions/upload-artifact@v4
        with:
          name: backend-jar
          path: porquinho-backend/target/*.jar

  deploy:
    needs: build
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    steps:
      - name: Download artifact
        uses: actions/download-artifact@v4
        with:
          name: backend-jar

      - name: Deploy to VPS
        uses: appleboy/ssh-action@v1.0.0
        with:
          host: ${{ secrets.VPS_HOST }}
          username: ${{ secrets.VPS_USER }}
          key: ${{ secrets.VPS_SSH_KEY }}
          script: |
            cd /opt/dokploy/porquinho
            docker-compose pull backend
            docker-compose up -d backend
```

**GitHub Secrets Required:**
```
VPS_HOST          → IP ou domain do VPS Hostinger
VPS_USER          → SSH username
VPS_SSH_KEY       → Private key para SSH
SUPABASE_URL      → https://[project-ref].supabase.co
SUPABASE_ANON_KEY → Supabase anon key
SUPABASE_DB_URL   → Connection string PostgreSQL
SUPABASE_DB_PASSWORD → DB password
```

**Affects:**
- Deploy automático em push para main
- PRs rodando testes antes de merge
- Rollback via revert commit + re-deploy

---

### Environment Configuration

**Decision:** Environment variables via `.env` files + secrets management
**Rationale:**
- 12-factor app principles
- Diferentes configs para dev/staging/prod
- Secrets não commitados no git

**Frontend (.env files):**
```bash
# .env.development
VITE_API_BASE_URL=http://localhost:8080/api/v1
VITE_SUPABASE_URL=https://[project-ref].supabase.co
VITE_SUPABASE_ANON_KEY=eyJhbG...

# .env.production
VITE_API_BASE_URL=https://api.porquinho.com/api/v1
VITE_SUPABASE_URL=https://[project-ref].supabase.co
VITE_SUPABASE_ANON_KEY=eyJhbG...
```

**Backend (application.yml profiles):**
```yaml
# application.yml (common)
spring:
  application:
    name: porquinho-backend
  jpa:
    open-in-view: false

---
# application-dev.yml
spring:
  config:
    activate:
      on-profile: dev
  datasource:
    url: jdbc:postgresql://localhost:54322/postgres
    username: postgres
    password: postgres
  jpa:
    show-sql: true
    hibernate:
      ddl-auto: update
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${SUPABASE_URL}/auth/v1

---
# application-prod.yml
spring:
  config:
    activate:
      on-profile: prod
  datasource:
    url: ${SPRING_DATASOURCE_URL}
    username: postgres
    password: ${SPRING_DATASOURCE_PASSWORD}
  jpa:
    show-sql: false
    hibernate:
      ddl-auto: validate
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${JWT_ISSUER_URI}
```

**Secrets Management:**
- Dev: `.env.local` (gitignored)
- Prod: GitHub Secrets → Dokploy environment variables
- Rotação: Supabase dashboard (regenerate keys)

---

### Monitoring & Logging (Deferred to Post-MVP)

**Decision:** Prometheus + Grafana (implementar quando tiver carga real)
**Rationale:**
- MVP: Logs básicos via Spring Boot Actuator suficientes
- Post-MVP: Métricas detalhadas quando tiver baseline

**MVP Observability:**
```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: when-authorized
  metrics:
    export:
      prometheus:
        enabled: true
```

**Health Check:**
```
GET /actuator/health

{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "redis": { "status": "UP" },
    "diskSpace": { "status": "UP" }
  }
}
```

**Logging:**
```yaml
logging:
  level:
    root: INFO
    com.porquinho: DEBUG
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"
  file:
    name: /var/log/porquinho/application.log
```

**Post-MVP (quando implementar):**
- Prometheus scraping `/actuator/prometheus`
- Grafana dashboards (JVM, HTTP requests, cache hit rate)
- Alerting (uptime < 99.5%, error rate > 1%)

---

## Decision Impact Analysis

### Implementation Sequence (Order Matters)

**Story 0: Project Setup**
1. Inicializar projetos (create-vue + Spring Initializr)
2. Configurar Tailwind + shadcn-vue (frontend)
3. Configurar Supabase project + Google OAuth
4. Configurar Spring Security JWT validation
5. Setup Flyway migrations
6. Setup Redis local (Docker)
7. Configurar GitHub repository + Actions

**Story 1: Authentication**
1. Supabase Auth integration (frontend)
2. JWT validation (backend)
3. Login/Logout flows
4. Protected routes (frontend)
5. User registration (criar record em `users` table)

**Story 2: Base Architecture**
1. Database schema inicial (users, transactions, categories)
2. JPA entities + repositories
3. DTOs + mappers
4. REST controllers base
5. Global exception handler (RFC 7807)
6. CORS configuration

**Story 3+: Features**
- Transaction import + classification
- Dashboard GPS (3 layers) + Redis cache
- Budget envelopes
- Goals tracking
- ...

### Cross-Component Dependencies

**Authentication Flow Affects:**
- ✅ Frontend: Axios interceptors precisam JWT
- ✅ Backend: SecurityConfig precisa JWKS URI
- ✅ Database: `users.id` como UUID (matching Supabase Auth)
- ✅ All Controllers: `@AuthenticationPrincipal String userId`

**Caching Strategy Affects:**
- ✅ Dashboard Service: `@Cacheable` annotations
- ✅ Transaction Service: `@CacheEvict` após classificação
- ✅ Budget Service: `@CacheEvict` após alocação
- ✅ Redis: Precisa estar UP antes backend starts

**API Versioning Affects:**
- ✅ All Controllers: `@RequestMapping("/api/v1/...")`
- ✅ Frontend: Base URL `${API_BASE}/api/v1`
- ✅ OpenAPI: Spec gerado com path prefix
- ✅ Nginx: Proxy pass para `/api/` → backend

**Flyway Migrations Affect:**
- ✅ Database: Schema versionado (V1, V2, V3...)
- ✅ CI/CD: Migrations rodam antes deploy
- ✅ JPA: `ddl-auto: validate` em prod (confia no Flyway)
- ✅ Rollback: Manual via Flyway undo migrations

**RBAC Authorization Affects:**
- ✅ JWT Claims: `role` claim necessário
- ✅ Controllers: `@PreAuthorize("hasRole('USER')")`
- ✅ Repositories: Query filters por `user_id`
- ✅ Frontend: Role-based UI (hide admin features)

### Technology Version Matrix

| Component | Technology | Version | Notes |
|-----------|------------|---------|-------|
| Frontend Framework | Vue | 3.5+ | Composition API |
| Build Tool (FE) | Vite | 6.x | ESBuild + Rollup |
| State Management | Pinia | 2.x | Successor Vuex |
| UI Components | shadcn-vue | latest | Copy-paste |
| CSS Framework | Tailwind CSS | 4.x | Utility-first |
| HTTP Client | Axios | 1.x | Interceptors |
| Backend Framework | Spring Boot | 3.4.x | Java 21 LTS |
| Language | Java | 21 LTS | Virtual threads |
| Build Tool (BE) | Maven | 3.9+ | Dependency management |
| ORM | Spring Data JPA | 3.x | Hibernate |
| Database | PostgreSQL | 16+ | Via Supabase |
| Database Migrations | Flyway | 10.x | SQL-based |
| Cache | Redis | 7.x | In-memory |
| Auth Provider | Supabase Auth | latest | OAuth2 + JWT |
| API Docs | springdoc-openapi | 2.3.0 | OpenAPI 3.1 |
| Testing (FE) | Vitest | latest | Unit tests |
| Testing (E2E) | Playwright | latest | Cross-browser |
| Testing (BE) | JUnit | 5.x | Unit tests |
| CI/CD | GitHub Actions | - | Free tier |
| Deployment | Dokploy | latest | Docker orchestration |
| Hosting | VPS Hostinger | Ubuntu 22.04 | Self-hosted |

---
