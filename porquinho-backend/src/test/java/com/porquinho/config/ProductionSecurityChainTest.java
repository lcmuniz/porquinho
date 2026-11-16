package com.porquinho.config;

import com.porquinho.service.JwtBlacklistService;
import com.porquinho.service.SessionService;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Retro Epic 1 A6 (S11, P1): o contexto inteiro sobe com o perfil "prod" e a SecurityFilterChain real
 * (SecurityConfig é @Profile("!test"), então os outros testes nunca a veem). Só o JwtDecoder (Supabase) e a blacklist
 * (Redis) são trocados: nada aqui fala com Redis, porque a 6379 local pode ser de outro projeto.
 */
@SpringBootTest(properties = {
    // Liga de volta a segurança que o application.yml de teste desliga para todos os outros testes
    "spring.autoconfigure.exclude=",
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate",
    "management.health.redis.enabled=false",
    "app.cors.allowed-origins=https://app.example.com,http://localhost:5173"
})
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@Import(ProductionSecurityChainTest.JwtDecoderConfig.class)
class ProductionSecurityChainTest {

    static final UUID USER_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    /** Dono do token "deleted-token": conta com exclusão pedida (users.deleted_at preenchido). */
    static final UUID DELETED_USER_ID = UUID.fromString("dddddddd-bbbb-cccc-dddd-eeeeeeeeeeee");

    /** Rotas da API que respondem sem JWT. Abrir uma nova exige mudar esta lista de propósito. */
    static final Set<String> PUBLIC_API_ROUTES = Set.of(
        "POST /api/v1/auth/login/check",
        "POST /api/v1/auth/login/failed",
        "POST /api/v1/auth/password-reset/requested",
        "GET /api/v1/auth/health",
        "GET /api/v1/data-export/download/{token}",
        "POST /api/v1/webhooks/stripe"
    );

    @TestConfiguration
    static class JwtDecoderConfig {
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> Jwt.withTokenValue(token)
                .header("alg", "none")
                .subject(("deleted-token".equals(token) ? DELETED_USER_ID : USER_ID).toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private List<SecurityFilterChain> chains;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @MockBean
    private JwtBlacklistService jwtBlacklistService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextStartsWithTheProductionChain() {
        assertThat(chains).hasSize(1);
        List<Filter> filters = chains.get(0).getFilters();
        assertThat(filters).anyMatch(f -> f instanceof SubscriptionAccessFilter);
        // Conta em exclusão deve dar 403 antes de o filtro de assinatura dar 402
        int deleted = filters.indexOf(filters.stream().filter(f -> f instanceof DeletedAccountFilter).findFirst().orElseThrow());
        int subscription = filters.indexOf(filters.stream().filter(f -> f instanceof SubscriptionAccessFilter).findFirst().orElseThrow());
        assertThat(deleted).isLessThan(subscription);
    }

    @Test
    void corsAllowsOnlyTheConfiguredOrigins() throws Exception {
        mockMvc.perform(request(HttpMethod.OPTIONS, "/api/v1/accounts")
                .header("Origin", "https://app.example.com")
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "https://app.example.com"));
        mockMvc.perform(request(HttpMethod.OPTIONS, "/api/v1/accounts")
                .header("Origin", "http://localhost:5175")
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isForbidden());
    }

    @Test
    void tokenOfAccountInDeletionGets403() throws Exception {
        // Retro Epic 1 A2 (S6): login novo depois do pedido de exclusão gera token fora da blacklist
        jdbcTemplate.update("INSERT INTO users (id, email, auth_provider, failed_login_attempts, deleted_at, deleted_by) "
            + "VALUES (?, 'em-exclusao@example.com', 'EMAIL', 0, now(), ?)", DELETED_USER_ID, DELETED_USER_ID);
        try {
            for (String path : List.of("/api/v1/budgets/2026-11", "/api/v1/subscriptions/me", "/api/v1/auth/sessions")) {
                mockMvc.perform(get(path).header("Authorization", "Bearer deleted-token"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.detail").value("Esta conta está em exclusão e não pode mais ser usada."));
            }
        } finally {
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", DELETED_USER_ID);
        }
    }

    @Test
    void revokedTokenGets401() throws Exception {
        when(jwtBlacklistService.isBlacklisted(SessionService.hashToken("revoked-token"))).thenReturn(true);

        mockMvc.perform(get("/api/v1/budgets/2026-11").header("Authorization", "Bearer revoked-token"))
            .andExpect(status().isUnauthorized());
        // Mesmo usuário com outro token passa da autenticação (402: sem assinatura no banco)
        mockMvc.perform(get("/api/v1/budgets/2026-11").header("Authorization", "Bearer live-token"))
            .andExpect(status().isPaymentRequired());
    }

    @Test
    void everyApiRouteOutsideThePublicListRequiresJwt() throws Exception {
        Set<String> found = new TreeSet<>();
        List<String> opened = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, ?> entry : handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            for (String pattern : info.getPatternValues()) {
                for (RequestMethod method : methods.isEmpty() ? Set.of(RequestMethod.GET) : methods) {
                    String route = method + " " + pattern;
                    if (!pattern.startsWith("/api/v1/")) {
                        found.add(route);
                        continue;
                    }
                    if (PUBLIC_API_ROUTES.contains(route)) {
                        continue;
                    }
                    String path = pattern.replaceAll("\\{[^}]+}", UUID.randomUUID().toString());
                    try {
                        int status = mockMvc.perform(request(HttpMethod.valueOf(method.name()), path))
                            .andReturn().getResponse().getStatus();
                        if (status != 401) {
                            opened.add(route + " -> " + status);
                        }
                    } catch (Exception e) {
                        // O controller rodou sem JWT (ex.: NPE no jwt.getSubject()): a rota está aberta
                        opened.add(route + " -> " + e.getClass().getSimpleName());
                    }
                }
            }
        }
        assertThat(opened).as("rotas da API que respondem sem JWT").isEmpty();
        // Fora de /api/v1 tudo é permitAll: só o /error do Spring pode existir aí
        assertThat(found).allMatch(route -> route.endsWith(" /error"));
    }

    @Test
    void publicRoutesAnswerWithoutJwt() throws Exception {
        // Corpo vazio: 400 da validação prova que passou pela chain sem tocar no Redis do rate limit
        for (String path : List.of("/api/v1/auth/login/check", "/api/v1/auth/login/failed",
                "/api/v1/auth/password-reset/requested")) {
            mockMvc.perform(post(path)).andExpect(status().isBadRequest());
        }
        mockMvc.perform(get("/api/v1/auth/health")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        int download = mockMvc.perform(get("/api/v1/data-export/download/" + UUID.randomUUID()))
            .andReturn().getResponse().getStatus();
        int webhook = mockMvc.perform(post("/api/v1/webhooks/stripe").content("{}"))
            .andReturn().getResponse().getStatus();
        assertThat(download).isNotIn(401, 403);
        assertThat(webhook).isNotIn(401, 403);
    }
}
