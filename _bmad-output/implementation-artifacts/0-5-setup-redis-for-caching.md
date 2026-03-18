# Story 0.5: Configurar Redis para Caching

Status: done

## Story

As a developer,
I want to configure Redis for dashboard caching,
so that I can implement TTL-based cache invalidation strategy for the Dashboard GPS layers.

## Acceptance Criteria

1. **Given** Redis 7-alpine Docker image disponivel
   **When** eu adiciono a dependencia Redis ao Spring Boot pom.xml (`spring-boot-starter-data-redis` e `spring-boot-starter-cache`)
   **Then** as dependencias sao resolvidas pelo Maven sem conflitos

2. **And** Redis esta configurado em `application.yml` com host, port e configuracoes de conexao via environment variables

3. **And** beans `RedisTemplate` e `CacheManager` estao configurados em `RedisConfig.java`

4. **And** configuracao de cache inclui key patterns: `dashboard:layer1:{userId}`, `dashboard:layer2:{userId}`, `dashboard:layer3:{userId}`

5. **And** TTL settings estao definidos: Layer 1 (30s), Layer 2 (5min), Layer 3 (15min)

6. **And** Spring Boot consegue iniciar com Redis conectado (verificavel via Actuator health endpoint)

7. **And** `@Cacheable` annotation pode ser usada em services (habilitado via `@EnableCaching`)

8. **And** `./mvnw test` passa sem requerer Redis real (testes usam configuracao sem Redis)

## Tasks / Subtasks

- [x] Adicionar dependencias Redis ao pom.xml (AC: #1)
  - [x] Adicionar `spring-boot-starter-data-redis`
  - [x] Adicionar `spring-boot-starter-cache`

- [x] Configurar Redis em application.yml (AC: #2)
  - [x] Adicionar bloco `spring.data.redis` com host, port, password via env vars
  - [x] Adicionar `spring.cache.type: redis`
  - [x] Configurar timeout de conexao

- [x] Configurar Redis em application-dev.yml (AC: #2, #6)
  - [x] Adicionar configuracao Redis apontando para localhost:6379
  - [x] Sem password para Redis local

- [x] Criar docker-compose.yml com Redis para desenvolvimento local (AC: #1)
  - [x] Definir servico Redis com `redis:7-alpine`
  - [x] Mapear porta 6379
  - [x] Adicionar volume para persistencia de dados

- [x] Criar RedisConfig.java com CacheManager personalizado (AC: #3, #4, #5)
  - [x] Criar classe `com.porquinho.config.RedisConfig`
  - [x] Anotar com `@Configuration` e `@EnableCaching`
  - [x] Configurar `RedisTemplate<String, Object>` com serializers JSON
  - [x] Configurar `RedisCacheManager` com TTLs diferenciados por cache name
  - [x] Dashboard Layer 1: TTL 30 segundos
  - [x] Dashboard Layer 2: TTL 5 minutos
  - [x] Dashboard Layer 3: TTL 15 minutos
  - [x] Default TTL: 5 minutos

- [x] Atualizar configuracao de teste para funcionar sem Redis (AC: #8)
  - [x] Adicionar exclusao de Redis auto-configuration no test application.yml
  - [x] OU configurar `spring.cache.type: none` para testes

- [x] Verificar startup e health endpoint (AC: #6, #7)
  - [x] Iniciar Redis via docker-compose
  - [x] Rodar `./mvnw spring-boot:run` com profile dev
  - [x] Confirmar `/actuator/health` mostra Redis UP
  - [x] Rodar `./mvnw clean test` — todos os testes passam

## Dev Notes

### CRITICAL: Estado Atual do Backend

O projeto backend (`porquinho-backend/`) esta em Spring Boot **3.5.0** com JDK 21:
- **pom.xml** ja inclui: spring-boot-starter-data-jpa, starter-security, starter-web, starter-actuator, starter-validation, starter-oauth2-client, Flyway, Lombok, PostgreSQL driver, H2 (test)
- **application.yml** tem datasource configurado com env vars, Flyway habilitado, security auto-config excluida
- **application-dev.yml** (gitignored) tem credenciais reais Supabase
- **Unico arquivo Java:** `PorquinhoBackendApplication.java`
- **Pasta config/** NAO existe ainda — sera criada nesta story

NAO ha Docker compose, RedisConfig, ou qualquer dependencia Redis no projeto.

### Dependencias Maven — Sem Version Tags

Spring Boot BOM 3.5.0 gerencia as versoes automaticamente. NAO especificar versoes:

```xml
<!-- Redis - Caching -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
```

### application.yml — Bloco Redis a Adicionar

Adicionar ABAIXO do bloco `flyway:` existente:

```yaml
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      timeout: 2000ms
  cache:
    type: redis
```

**ATENCAO:** A propriedade correta no Spring Boot 3.x e `spring.data.redis.*` (NAO `spring.redis.*` que era Spring Boot 2.x).

### application-dev.yml — Adicionar Redis Local

Adicionar ao `application-dev.yml` existente:

```yaml
  data:
    redis:
      host: localhost
      port: 6379
      password:
```

### docker-compose.yml — Redis para Dev

Criar na raiz do projeto (`porquinho/docker-compose.yml`):

```yaml
services:
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    restart: unless-stopped

volumes:
  redis-data:
```

**NOTA:** Este docker-compose.yml e apenas para desenvolvimento local. O docker-compose de producao (Story 0.6) incluira frontend, backend e nginx tambem.

### RedisConfig.java — Implementacao Completa

**Path:** `porquinho-backend/src/main/java/com/porquinho/config/RedisConfig.java`

```java
package com.porquinho.config;

import java.time.Duration;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@EnableCaching
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .entryTtl(Duration.ofMinutes(5))
                .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration("dashboard-layer1",
                        defaultConfig.entryTtl(Duration.ofSeconds(30)))
                .withCacheConfiguration("dashboard-layer2",
                        defaultConfig.entryTtl(Duration.ofMinutes(5)))
                .withCacheConfiguration("dashboard-layer3",
                        defaultConfig.entryTtl(Duration.ofMinutes(15)))
                .build();
    }
}
```

### Cache Key Patterns (Referencia para Stories Futuras)

```
dashboard-layer1:{userId}    → 30s TTL (metricas essenciais)
dashboard-layer2:{userId}    → 5min TTL (visao tatica)
dashboard-layer3:{userId}    → 15min TTL (lista completa transacoes)
```

Uso futuro em services:
```java
@Cacheable(value = "dashboard-layer1", key = "#userId")
public DashboardLayer1DTO getLayer1(String userId) { ... }

@CacheEvict(value = {"dashboard-layer1", "dashboard-layer2", "dashboard-layer3"}, key = "#userId")
public void invalidateDashboardCache(String userId) { ... }
```

### Configuracao de Testes — Desabilitar Redis

No `src/test/resources/application.yml` existente, adicionar:

```yaml
  cache:
    type: none
```

Isso desabilita o cache Redis nos testes unitarios. Os testes rodam com H2 in-memory e sem Redis.

**NAO adicionar exclusao de RedisAutoConfiguration** — `spring.cache.type: none` e suficiente e mais limpo.

### Learnings das Stories Anteriores

- ~~**Story 0.3:** `mise exec` e necessario para garantir JDK 21 — sempre usar `mise exec -- ./mvnw ...`~~ **⚠️ OBSOLETE (2026-03-17):** JDK 21 is now system default, just use `./mvnw` directly
- **Story 0.3:** Session Pooler do Supabase e necessario (direct connection e IPv6-only)
- **Story 0.3:** Sempre testar `./mvnw clean test` DEPOIS de cada mudanca
- **Story 0.4:** shadcn-vue usa Tailwind v4 (CSS-first) — irrelevante para esta story backend, mas bom saber
- **Story 0.3:** Spring Boot BOM gerencia versoes automaticamente — NAO especificar version tags

### Verificacao de Startup Esperada

Quando Redis esta UP e conectado:
```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "redis": { "status": "UP", "details": { "version": "7.x.x" } },
    "diskSpace": { "status": "UP" }
  }
}
```

Se Redis estiver DOWN, o health endpoint mostrara:
```json
{
  "status": "DOWN",
  "components": {
    "redis": { "status": "DOWN" }
  }
}
```

### Project Structure Notes

- `com.porquinho.config.RedisConfig` — primeira classe na pasta `config/`
- Pasta `config/` sera reutilizada em stories futuras: `SecurityConfig.java` (1.1), `CorsConfig.java` (1.1), `OpenApiConfig.java`
- Arquivo `docker-compose.yml` na raiz do projeto — sera expandido na Story 0.6 com frontend, backend, nginx

### Arquivos a Criar/Modificar

| Acao | Arquivo |
|------|---------|
| MODIFY | `porquinho-backend/pom.xml` |
| MODIFY | `porquinho-backend/src/main/resources/application.yml` |
| MODIFY | `porquinho-backend/src/main/resources/application-dev.yml` |
| MODIFY | `porquinho-backend/src/test/resources/application.yml` |
| CREATE | `porquinho-backend/src/main/java/com/porquinho/config/RedisConfig.java` |
| CREATE | `docker-compose.yml` (raiz do projeto porquinho/) |

### References

- [Source: _bmad-output/planning-artifacts/epics/epic-0-project-foundation-technical-setup.md#Story 0.5]
- [Source: _bmad-output/planning-artifacts/architecture/core-architectural-decisions.md#Caching Strategy: Redis]
- [Source: _bmad-output/planning-artifacts/architecture/implementation-patterns-consistency-rules.md#Code Naming Conventions]
- [Source: _bmad-output/planning-artifacts/architecture/project-structure-boundaries.md#Backend Spring Boot]
- [Source: _bmad-output/implementation-artifacts/0-3-setup-postgresql-database-with-flyway.md#Completion Notes]
- [Source: _bmad-output/implementation-artifacts/0-4-configure-tailwind-css-shadcn-vue-design-system.md#Dev Notes]

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6 (1M context)

### Debug Log References

Nenhum problema encontrado. Implementacao direta conforme Dev Notes.

### Completion Notes List

- Adicionadas dependencias `spring-boot-starter-data-redis` e `spring-boot-starter-cache` ao pom.xml (sem version tags — gerenciadas pelo Spring Boot BOM 3.5.0)
- Configurado `spring.data.redis` em application.yml com env vars (REDIS_HOST, REDIS_PORT, REDIS_PASSWORD) e timeout 2000ms; `spring.cache.type: redis`
- Configurado Redis local (localhost:6379, sem password) em application-dev.yml
- Criado docker-compose.yml na raiz do projeto com `redis:7-alpine`, porta 6379 e volume redis-data para persistencia
- Criada classe `RedisConfig.java` em `com.porquinho.config` com `@Configuration` + `@EnableCaching`, RedisTemplate com serializers JSON (StringRedisSerializer para keys, GenericJackson2JsonRedisSerializer para values), e RedisCacheManager com TTLs: dashboard-layer1 (30s), dashboard-layer2 (5min), dashboard-layer3 (15min), default (5min)
- Adicionado `spring.cache.type: none` ao test application.yml para desabilitar Redis nos testes unitarios
- Testes rodados: `./mvnw clean test` — 1 teste, 0 falhas, 0 erros
- Startup verificado: Spring Boot iniciou sem erros com Redis conectado; `/actuator/health` retornou `{"status":"UP"}` confirmando Redis UP

### File List

- porquinho-backend/pom.xml (modificado)
- porquinho-backend/src/main/resources/application.yml (modificado)
- porquinho-backend/src/main/resources/application-dev.yml (modificado)
- porquinho-backend/src/test/resources/application.yml (modificado)
- porquinho-backend/src/main/java/com/porquinho/config/RedisConfig.java (criado)
- docker-compose.yml (criado)
- porquinho-backend/src/main/resources/application-dev.yml.example (modificado)

## Change Log

| Data | Mudanca |
|------|---------|
| 2026-03-14 | Implementacao completa da Story 0.5: dependencias Redis adicionadas ao pom.xml, configuracao em application.yml/application-dev.yml, docker-compose.yml criado, RedisConfig.java com CacheManager e TTLs por layer, testes configurados sem Redis. Todos os testes passaram. |
| 2026-03-14 | Code Review: application-dev.yml.example atualizado com bloco Redis (host, port, password) para manter template completo. File List atualizado com arquivo .example. Status → done. |
