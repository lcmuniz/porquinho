package com.porquinho.controller;

import com.porquinho.config.TestWebSecurityConfig;
import com.porquinho.service.EmailUnsubscribeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** List-Unsubscribe against the real database: one-click POST turns the budget alert off, GET only redirects. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestWebSecurityConfig.class)
class EmailUnsubscribeControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EmailUnsubscribeService emailUnsubscribeService;

    private UUID userId;
    private String token;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, auth_provider) VALUES (?, ?, 'EMAIL')", userId,
            "descadastro-" + userId + "@example.com");
        String url = emailUnsubscribeService.budgetAlertUnsubscribeUrl(userId);
        token = url.substring(url.indexOf("token=") + "token=".length());
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }

    private boolean alertEnabled() {
        return jdbc.queryForObject("SELECT budget_alert_email_enabled FROM users WHERE id = ?", Boolean.class, userId);
    }

    @Test
    void oneClickPostTurnsTheBudgetAlertOff() throws Exception {
        assertThat(alertEnabled()).isTrue();

        // RFC 8058: the mail client posts this form body to the List-Unsubscribe URL
        mockMvc.perform(post("/api/v1/email/unsubscribe").param("token", token)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).content("List-Unsubscribe=One-Click"))
            .andExpect(status().isNoContent());
        assertThat(alertEnabled()).isFalse();

        // A second click (or the button of the page after the header) is still a success
        mockMvc.perform(post("/api/v1/email/unsubscribe").param("token", token)).andExpect(status().isNoContent());
    }

    @Test
    void invalidTokenIs400AndChangesNothing() throws Exception {
        String tampered = UUID.randomUUID() + token.substring(token.indexOf('.'));

        for (String bad : new String[] {tampered, token + "x", "lixo"}) {
            mockMvc.perform(post("/api/v1/email/unsubscribe").param("token", bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Link de descadastro inválido"));
        }
        mockMvc.perform(post("/api/v1/email/unsubscribe")).andExpect(status().isBadRequest());
        assertThat(alertEnabled()).isTrue();
    }

    @Test
    void getOnlyRedirectsToTheFrontPage() throws Exception {
        // Link scanners prefetch GETs: only the POST of the page (or of the mail client) unsubscribes
        mockMvc.perform(get("/api/v1/email/unsubscribe").param("token", token))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", "http://localhost:5173/descadastro?token=" + token));
        assertThat(alertEnabled()).isTrue();
    }
}
