package com.porquinho.controller;

import com.porquinho.config.TestWebSecurityConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Story 10.1 with the test profile (security open): the 403 of non-admins comes from the interceptor, not the chain. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestWebSecurityConfig.class, AdminControllerIntegrationTest.ProbeController.class})
// The probe gives this class its own context (and Hikari pool); closing it keeps the suite under max_connections
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    private UUID admin;
    private UUID user;

    @BeforeEach
    void setUp() {
        admin = UUID.randomUUID();
        user = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, auth_provider, is_admin) VALUES (?, ?, 'EMAIL', true)", admin, admin + "@example.com");
        jdbc.update("INSERT INTO users (id, email, auth_provider) VALUES (?, ?, 'EMAIL')", user, user + "@example.com");
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM users WHERE id IN (?, ?)", admin, user);
    }

    @Test
    void nonAdminGets403OnHealth() throws Exception {
        mockMvc.perform(get("/api/v1/admin/health").with(jwt().jwt(j -> j.subject(user.toString()))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.title").value("Acesso restrito"));
    }

    @Test
    void everyAdminRouteExceptAccessGives403ToNonAdmins() throws Exception {
        // Every route under /api/v1/admin goes through AdminAccessInterceptor; the probe POST is sent without body and
        // must still be a 403, not a 400. A route the interceptor misses fails here
        List<String> checked = new ArrayList<>();
        List<String> opened = new ArrayList<>();
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            for (String pattern : info.getPatternValues()) {
                if (!pattern.startsWith("/api/v1/admin/")) continue;
                for (RequestMethod method : methods.isEmpty() ? Set.of(RequestMethod.GET) : methods) {
                    String route = method + " " + pattern;
                    if (route.equals("GET /api/v1/admin/access")) continue;
                    checked.add(route);
                    String path = pattern.replaceAll("\\{[^}]+}", UUID.randomUUID().toString());
                    int status = mockMvc.perform(request(HttpMethod.valueOf(method.name()), path)
                            .with(jwt().jwt(j -> j.subject(user.toString()))))
                        .andReturn().getResponse().getStatus();
                    if (status != 403) opened.add(route + " -> " + status);
                }
            }
        }
        assertThat(checked).contains("GET /api/v1/admin/health", "GET /api/v1/admin/imports",
            "GET /api/v1/admin/imports/detail", "GET /api/v1/admin/imports/export", "GET /api/v1/admin/ai",
            "GET /api/v1/admin/ai/detail", "GET /api/v1/admin/ai/export", "GET /api/v1/admin/users",
            "GET /api/v1/admin/users/export", "POST " + PROBE);
        assertThat(opened).as("admin routes open to non-admins").isEmpty();
    }

    @Test
    void adminGetsTheHealth() throws Exception {
        mockMvc.perform(get("/api/v1/admin/health").with(jwt().jwt(j -> j.subject(admin.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.uptime.target").value(99.5))
            .andExpect(jsonPath("$.hourly.length()").value(24))
            .andExpect(jsonPath("$.database.max").isNumber())
            .andExpect(jsonPath("$.redis.status").exists())
            .andExpect(jsonPath("$.sessions.active").isNumber());
    }

    @Test
    void accessTellsWhetherTheUserIsAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/access").with(jwt().jwt(j -> j.subject(admin.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.admin").value(true));
        mockMvc.perform(get("/api/v1/admin/access").with(jwt().jwt(j -> j.subject(user.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.admin").value(false));
    }

    @Test
    void nonAdminGets403BeforeTheBodyIsValidated() throws Exception {
        String invalid = "{\"note\":\"\"}";
        mockMvc.perform(post(PROBE).with(jwt().jwt(j -> j.subject(user.toString())))
                .contentType(MediaType.APPLICATION_JSON).content(invalid))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.title").value("Acesso restrito"));
        // the same body is a 400 for an admin: the probe does validate
        mockMvc.perform(post(PROBE).with(jwt().jwt(j -> j.subject(admin.toString())))
                .contentType(MediaType.APPLICATION_JSON).content(invalid))
            .andExpect(status().isBadRequest());
    }

    private static final String PROBE = "/api/v1/admin/test-probe";

    /** A write route with a validated body, the shape the 10.5 will add; only this test context loads it. */
    @RestController
    static class ProbeController {
        record Body(@NotBlank String note) {}

        @PostMapping(PROBE)
        void probe(@Valid @RequestBody Body body) {
        }
    }
}
