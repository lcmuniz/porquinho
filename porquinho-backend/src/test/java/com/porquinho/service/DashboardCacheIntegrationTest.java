package com.porquinho.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.porquinho.config.TestWebSecurityConfig;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 7.7 over HTTP with the real Redis: a second read is a hit, a transaction write clears every layer, a budget
 * write keeps the layer 3. Rows inserted straight into the database (no request) show what is still cached.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestWebSecurityConfig.class)
class DashboardCacheIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private DashboardCache dashboardCache;
    @Autowired
    private BudgetBalanceCache balanceCache;
    @Autowired
    private MeterRegistry meters;

    private UUID userId;
    private UUID account;
    private UUID food;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, auth_provider) VALUES (?, ?, 'EMAIL')", userId,
            userId + "@example.com");
        account = UUID.randomUUID();
        jdbc.update("INSERT INTO accounts (id, user_id, account_name, account_type, initial_balance, current_balance) "
            + "VALUES (?, ?, 'Nubank', 'CHECKING', 1000, 1000)", account, userId);
        UUID group = category("Essenciais", null);
        food = category("Mercado", group);
    }

    @AfterEach
    void tearDown() {
        dashboardCache.evictAll(userId);
        balanceCache.evict(userId);
        jdbc.update("DELETE FROM audit_logs WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM budget_allocations WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM transactions WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM account_balance_history WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM accounts WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM categories WHERE user_id = ? AND parent_category_id IS NOT NULL", userId);
        jdbc.update("DELETE FROM categories WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }

    private UUID category(String name, UUID parent) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO categories (id, user_id, category_name, parent_category_id, color_hex, icon_name, "
            + "is_system_default) VALUES (?, ?, ?, ?, '#000000', 'tag', FALSE)", id, userId, name, parent);
        return id;
    }

    /** A confirmed debit straight into the database: no request, so no invalidation. */
    private void silentRow(String description) {
        jdbc.update("INSERT INTO transactions (id, user_id, account_id, category_id, transaction_date, description, "
            + "original_description, amount, transaction_type, kind, review_status, is_future_installment) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, 5.00, 'DEBIT', 'REGULAR', 'CONFIRMED', FALSE)", UUID.randomUUID(), userId,
            account, food, TODAY, description, description);
    }

    private JsonNode call(MockHttpServletRequestBuilder request) throws Exception {
        String body = mockMvc.perform(request.with(jwt().jwt(j -> j.subject(userId.toString()))))
            .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private JsonNode read(String layer) throws Exception {
        return call(get("/api/v1/dashboard/" + layer));
    }

    private static List<String> descriptions(JsonNode layer3) {
        return layer3.get("items").findValuesAsText("description");
    }

    private double hits(String layer) {
        Counter counter = meters.find("dashboard.cache").tag("layer", layer).tag("result", "hit").counter();
        return counter == null ? 0 : counter.count();
    }

    @Test
    void aSecondReadIsAHitWithTheSameAnswer() throws Exception {
        JsonNode first = read("layer1");
        JsonNode firstLayer3 = read("layer3");
        double before = hits("layer1");
        silentRow("Fora do cache");

        assertThat(read("layer1")).isEqualTo(first);
        assertThat(read("layer3")).isEqualTo(firstLayer3);
        assertThat(hits("layer1")).isEqualTo(before + 1);
        assertThat(redis.hasKey("dashboard:layer1:" + userId)).isTrue();
    }

    @Test
    void aTransactionWriteClearsEveryLayer() throws Exception {
        read("health");
        read("layer1");
        read("layer2");
        assertThat(descriptions(read("layer3"))).isEmpty();

        call(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(objectMapper
            .writeValueAsString(Map.of("date", TODAY.toString(), "description", "Feira", "amount", "12.34",
                "type", "DEBIT", "categoryId", food, "accountId", account))));

        assertThat(descriptions(read("layer3"))).containsExactly("Feira");
        assertThat(read("layer1").get("monthSpent").decimalValue()).isEqualByComparingTo("12.34");
        assertThat(read("layer2").get("categories").findValuesAsText("name")).containsExactly("Mercado");
    }

    @Test
    void aBudgetWriteKeepsTheLayer3() throws Exception {
        read("health");
        assertThat(read("layer1").get("monthAllocated").decimalValue()).isZero();
        assertThat(descriptions(read("layer3"))).isEmpty();
        silentRow("Fora do cache");

        String month = YearMonth.from(TODAY).toString();
        String version = call(get("/api/v1/budgets/" + month)).get("version").asText();
        call(put("/api/v1/budgets/" + month).contentType(MediaType.APPLICATION_JSON).content(objectMapper
            .writeValueAsString(Map.of("version", version,
                "allocations", List.of(Map.of("categoryId", food, "amount", new BigDecimal("100.00")))))));

        assertThat(read("layer1").get("monthAllocated").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(redis.hasKey("dashboard:layer3:" + userId)).isTrue();
        assertThat(descriptions(read("layer3"))).isEmpty();
    }

    @Test
    void aBadLayer3RequestIsNotCached() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/layer3").param("from", "2026-13-01")
                .with(jwt().jwt(j -> j.subject(userId.toString()))))
            .andExpect(status().isBadRequest());

        assertThat(redis.hasKey("dashboard:layer3:" + userId)).isFalse();
    }
}
