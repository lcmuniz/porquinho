package com.porquinho.service;

import com.porquinho.config.TestWebSecurityConfig;
import com.porquinho.repository.AiClassificationHistoryRepository;
import com.porquinho.repository.AiClassificationHistoryRepository.BankCounts;
import com.porquinho.repository.AiClassificationHistoryRepository.MonthCounts;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 10.3 against the real database (Testcontainers + Flyway). The rows sit in March 2020 so the classifications
 * of other tests (always "now") never fall inside the period.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestWebSecurityConfig.class)
class AiMetricsIntegrationTest {

    private static final String PERIOD = "from=2020-03-01&to=2020-03-31";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AiClassificationHistoryRepository repository;

    private final List<UUID> users = new ArrayList<>();
    private UUID admin;
    private UUID account;
    private UUID category;

    @BeforeEach
    void setUp() {
        admin = user(true);
        account = UUID.randomUUID();
        jdbc.update("INSERT INTO accounts (id, user_id, account_name, account_type, initial_balance, current_balance) "
            + "VALUES (?, ?, 'Conta', 'CHECKING', 0, 0)", account, admin);
        category = category(admin);
    }

    @AfterEach
    void tearDown() {
        for (UUID u : users) {
            jdbc.update("DELETE FROM ai_classification_history WHERE user_id = ?", u);
            jdbc.update("DELETE FROM user_classification_patterns WHERE user_id = ?", u);
            jdbc.update("DELETE FROM transactions WHERE user_id = ?", u);
            jdbc.update("DELETE FROM transaction_imports WHERE user_id = ?", u);
            jdbc.update("DELETE FROM categories WHERE user_id = ?", u);
            jdbc.update("DELETE FROM accounts WHERE user_id = ?", u);
            jdbc.update("DELETE FROM users WHERE id = ?", u);
        }
    }

    private UUID user(boolean isAdmin) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, auth_provider, is_admin) VALUES (?, ?, 'EMAIL', ?)", id,
            id + "@example.com", isAdmin);
        users.add(id);
        return id;
    }

    private UUID category(UUID user) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO categories (id, user_id, category_name, parent_category_id, color_hex, icon_name, "
            + "is_system_default) VALUES (?, ?, ?, NULL, '#000000', 'tag', FALSE)", id, user, "Categoria Secreta " + id.toString().substring(0, 8));
        return id;
    }

    private UUID importOf(String bank) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO transaction_imports (id, user_id, account_id, file_name, file_type, file_size_kb, status, "
            + "bank_name) VALUES (?, ?, ?, 'extrato.ofx', 'OFX', 1, 'COMPLETED', ?)", id, admin, account, bank);
        return id;
    }

    /** One AI classification of a new transaction of {@code importId}, classified and reviewed at {@code reviewedAt}. */
    private void classified(UUID importId, String type, boolean pix, boolean installment, String review,
                            boolean deleted, int confidence, boolean corrected, String reviewedAt) {
        classified(importId, type, pix, installment, review, deleted, confidence, corrected, reviewedAt, reviewedAt);
    }

    private void classified(UUID importId, String type, boolean pix, boolean installment, String review,
                            boolean deleted, int confidence, boolean corrected, String createdAt, String reviewedAt) {
        UUID tx = UUID.randomUUID();
        jdbc.update("INSERT INTO transactions (id, user_id, account_id, import_id, transaction_date, description, "
                + "original_description, amount, transaction_type, review_status, is_pix, is_installment, deleted_at) "
                + "VALUES (?, ?, ?, ?, DATE '2020-03-10', 'uber trip joao silva', 'uber trip joao silva', 12.34, ?, ?, ?, ?, "
                + (deleted ? "NOW()" : "NULL") + ")",
            tx, admin, account, importId, type, review, pix, installment);
        jdbc.update("INSERT INTO ai_classification_history (user_id, transaction_id, predicted_category_id, confidence, "
                + "was_corrected, created_at, reviewed_at) VALUES (?, ?, ?, ?, ?, CAST(? AS timestamptz), "
                + "CAST(? AS timestamptz))",
            admin, tx, category, confidence, corrected, createdAt, "PENDING".equals(review) ? null : reviewedAt);
    }

    private void reviewed(UUID importId, boolean corrected) {
        classified(importId, "DEBIT", false, false, "CONFIRMED", false, 90, corrected, "2020-03-10T12:00:00Z");
    }

    @Test
    void tableCountsReviewedRowsPerBankWithTheNullBankAsUnknown() throws Exception {
        UUID nubank = importOf("NUBANK");
        for (int i = 0; i < 4; i++) reviewed(nubank, i == 0); // 75 %
        classified(nubank, "DEBIT", false, false, "PENDING", false, 90, true, "2020-03-10T12:00:00Z");
        classified(nubank, "DEBIT", false, false, "CONFIRMED", true, 90, true, "2020-03-10T12:00:00Z");
        classified(nubank, "DEBIT", false, false, "CONFIRMED", false, 90, true, "2020-04-01T12:00:00Z"); // outside
        classified(nubank, "DEBIT", false, false, "CONFIRMED", false, 90, false, "2020-03-31T23:30:00-03:00"); // last day
        classified(nubank, "DEBIT", false, false, "CONFIRMED", false, 90, true, "2020-04-01T00:00:00-03:00"); // outside
        UUID unknown = importOf(null);
        classified(unknown, "CREDIT", false, false, "APPROVED", false, 90, false, "2020-03-11T12:00:00Z");

        mockMvc.perform(get("/api/v1/admin/ai?" + PERIOD).with(jwt().jwt(j -> j.subject(admin.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.target").value(85.0))
            .andExpect(jsonPath("$.overall.classifications").value(6))
            .andExpect(jsonPath("$.overall.corrections").value(1))
            .andExpect(jsonPath("$.overall.accuracy").value(83.3))
            .andExpect(jsonPath("$.banks.length()").value(2))
            .andExpect(jsonPath("$.banks[0].bank").value("NUBANK"))
            .andExpect(jsonPath("$.banks[0].classifications").value(5))
            .andExpect(jsonPath("$.banks[0].accuracy").value(80.0))
            .andExpect(jsonPath("$.banks[0].status").value("YELLOW"))
            .andExpect(jsonPath("$.banks[1].bank").value("UNKNOWN"))
            .andExpect(jsonPath("$.banks[1].status").value("GREEN"))
            .andExpect(jsonPath("$.months.length()").value(6))
            .andExpect(content().string(not(containsString(admin.toString()))))
            .andExpect(content().string(not(containsString("Categoria Secreta"))));

        String csv = mockMvc.perform(get("/api/v1/admin/ai/export?" + PERIOD)
                .with(jwt().jwt(j -> j.subject(admin.toString()))))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"precisao_ia_2020-03-01_2020-03-31.csv\""))
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).isEqualTo("﻿Banco;Classificações;Correções;Precisão (%)\r\n"
            + "Nubank;5;1;80,0\r\nNão identificado;1;0;100,0\r\nTotal;6;1;83,3\r\n");
        assertThat(csv).doesNotContain(admin.toString()).doesNotContain("Categoria Secreta");
    }

    @Test
    void bankFilterKeepsOnlyThatBankInTheTableAndTheCsv() throws Exception {
        UUID nubank = importOf("NUBANK");
        reviewed(nubank, true);
        reviewed(nubank, false);
        UUID itau = importOf("ITAU");
        for (int i = 0; i < 3; i++) reviewed(itau, false);

        mockMvc.perform(get("/api/v1/admin/ai?bank=NUBANK&" + PERIOD).with(jwt().jwt(j -> j.subject(admin.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.banks.length()").value(1))
            .andExpect(jsonPath("$.banks[0].bank").value("NUBANK"))
            .andExpect(jsonPath("$.overall.classifications").value(2))
            .andExpect(jsonPath("$.overall.corrections").value(1))
            .andExpect(jsonPath("$.overall.accuracy").value(50.0));

        String csv = mockMvc.perform(get("/api/v1/admin/ai/export?bank=NUBANK&" + PERIOD)
                .with(jwt().jwt(j -> j.subject(admin.toString()))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).isEqualTo("\uFEFFBanco;Classificações;Correções;Precisão (%)\r\n"
            + "Nubank;2;1;50,0\r\nTotal;2;1;50,0\r\n");
    }

    @Test
    void detailGroupsByTypeWithPixBeforeInstallmentAndByConfidenceBand() throws Exception {
        UUID itau = importOf("ITAU");
        classified(itau, "DEBIT", true, true, "CONFIRMED", false, 10, true, "2020-03-10T12:00:00Z"); // PIX
        classified(itau, "DEBIT", false, true, "CONFIRMED", false, 60, false, "2020-03-10T12:00:00Z"); // INSTALLMENT
        classified(itau, "CREDIT", false, false, "CONFIRMED", false, 80, false, "2020-03-10T12:00:00Z");
        reviewed(importOf("NUBANK"), true); // another bank: out of the ITAU detail
        classified(itau, "CREDIT", false, false, "CONFIRMED", false, 90, false, "2020-03-31T23:30:00-03:00"); // last day
        classified(itau, "CREDIT", false, false, "CONFIRMED", false, 90, true, "2020-04-01T00:00:00-03:00"); // outside

        mockMvc.perform(get("/api/v1/admin/ai/detail?bank=ITAU&" + PERIOD).with(jwt().jwt(j -> j.subject(admin.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.types.length()").value(3))
            .andExpect(jsonPath("$.types[0].key").value("PIX"))
            .andExpect(jsonPath("$.types[0].accuracy").value(0.0))
            .andExpect(jsonPath("$.types[0].status").value("RED"))
            .andExpect(jsonPath("$.types[?(@.key == 'DEBIT')]").isEmpty())
            .andExpect(jsonPath("$.confidence[2].classifications").value(2))
            .andExpect(jsonPath("$.confidence[2].corrections").value(0))
            .andExpect(jsonPath("$.types[?(@.key == 'CREDIT')].classifications", hasItem(2)))
            .andExpect(jsonPath("$.types[?(@.key == 'CREDIT')].corrections", hasItem(0)))
            .andExpect(jsonPath("$.types[*].key", hasItem("INSTALLMENT")))
            .andExpect(jsonPath("$.confidence[0].key").value("0-49"))
            .andExpect(jsonPath("$.confidence[1].key").value("50-79"))
            .andExpect(jsonPath("$.confidence[2].key").value("80-100"))
            .andExpect(content().string(not(containsString("Categoria Secreta"))))
            .andExpect(content().string(not(containsString("joao silva"))))
            .andExpect(content().string(not(containsString(admin.toString()))));
    }

    @Test
    void sharedPatternsNeedThreeUsersAndShowNoUserOrCategory() throws Exception {
        String three = "padrao tres " + UUID.randomUUID().toString().replaceAll("[^a-f]", "");
        String two = "padrao dois " + UUID.randomUUID().toString().replaceAll("[^a-f]", "");
        UUID second = user(false);
        UUID third = user(false);
        pattern(admin, three, 3);
        pattern(second, three, 2);
        pattern(third, three, 2);
        pattern(admin, two, 5);
        pattern(second, two, 5);

        mockMvc.perform(get("/api/v1/admin/ai/detail?bank=NUBANK&" + PERIOD).with(jwt().jwt(j -> j.subject(admin.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.patterns[?(@.pattern == '" + three + "')].users").value(3))
            .andExpect(jsonPath("$.patterns[?(@.pattern == '" + three + "')].usage").value(7))
            .andExpect(content().string(not(containsString(two))))
            .andExpect(content().string(containsString("Incluir '" + three + "' como exemplo no prompt (3 usuários usam)")))
            .andExpect(content().string(not(containsString("Categoria Secreta"))))
            .andExpect(content().string(not(containsString(second.toString()))));
    }

    @Test
    void sharedPatternsMaskPersonNamesAndMergeTheMaskedOnes() throws Exception {
        String prefix = "loja " + UUID.randomUUID().toString().replaceAll("[^a-f]", "") + " pix enviado ";
        UUID second = user(false);
        UUID third = user(false);
        pattern(admin, prefix + "joao silva", 1);
        pattern(admin, prefix + "maria", 2); // same user, other name: counted once
        pattern(second, prefix + "jose souza", 3);
        pattern(third, prefix + "ana", 4);

        mockMvc.perform(get("/api/v1/admin/ai/detail?bank=NUBANK&" + PERIOD).with(jwt().jwt(j -> j.subject(admin.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.patterns[?(@.pattern == '" + prefix + "<nome>')].users").value(3))
            .andExpect(jsonPath("$.patterns[?(@.pattern == '" + prefix + "<nome>')].usage").value(10))
            .andExpect(content().string(containsString("Incluir '" + prefix + "<nome>' como exemplo no prompt")))
            .andExpect(content().string(not(containsString("joao silva"))))
            .andExpect(content().string(not(containsString("jose souza"))))
            .andExpect(content().string(not(containsString(prefix + "ana"))));
    }

    private void pattern(UUID user, String pattern, int usage) {
        jdbc.update("INSERT INTO user_classification_patterns (user_id, description_pattern, preferred_category_id, "
            + "usage_count) VALUES (?, ?, ?, ?)", user, pattern, category(user), usage);
    }

    @Test
    void periodAndMonthFollowTheReviewDateNotTheClassificationDate() {
        String bank = "T" + UUID.randomUUID(); // no other row has this bank
        UUID imp = importOf(bank);
        // classified on Feb 29th, corrected by the user on March 2nd: a March error (retro do Epic 10, A3)
        classified(imp, "DEBIT", false, false, "CONFIRMED", false, 90, true, "2020-02-29T12:00:00Z",
            "2020-03-02T12:00:00Z");
        // classified in March, still in review: not counted
        classified(imp, "DEBIT", false, false, "PENDING", false, 90, true, "2020-03-02T12:00:00Z", null);

        assertThat(repository.countByBank(OffsetDateTime.parse("2020-03-02T00:00-03:00"),
                OffsetDateTime.parse("2020-03-03T00:00-03:00"), bank))
            .extracting(BankCounts::getClassifications, BankCounts::getCorrections)
            .containsExactly(Tuple.tuple(1L, 1L));
        assertThat(repository.countByBank(OffsetDateTime.parse("2020-02-29T00:00-03:00"),
            OffsetDateTime.parse("2020-03-01T00:00-03:00"), bank)).isEmpty();
        assertThat(repository.countByBankAndMonth(OffsetDateTime.parse("2020-02-01T00:00-03:00"),
                OffsetDateTime.parse("2020-04-01T00:00-03:00")).stream().filter(m -> m.getBank().equals(bank)))
            .extracting(MonthCounts::getMonth).containsExactly("2020-03");
    }

    @Test
    void monthlyCountsUseTheSaoPauloMonth() {
        String bank = "T" + UUID.randomUUID(); // no other row has this bank
        UUID imp = importOf(bank);
        classified(imp, "DEBIT", false, false, "CONFIRMED", false, 90, true, "2020-03-01T02:00:00Z"); // Feb 29th 23:00 SP
        classified(imp, "DEBIT", false, false, "CONFIRMED", false, 90, false, "2020-03-01T04:00:00Z");

        List<MonthCounts> months = repository.countByBankAndMonth(OffsetDateTime.parse("2020-02-01T00:00-03:00"),
            OffsetDateTime.parse("2020-04-01T00:00-03:00")).stream().filter(m -> m.getBank().equals(bank)).toList();

        assertThat(months).extracting(MonthCounts::getMonth, MonthCounts::getClassifications, MonthCounts::getCorrections)
            .containsExactlyInAnyOrder(Tuple.tuple("2020-02", 1L, 1L), Tuple.tuple("2020-03", 1L, 0L));
    }

    @Test
    void emptyPeriodGivesNullAccuracyAndACsvWithTotalZero() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ai?from=2019-01-01&to=2019-01-31").with(jwt().jwt(j -> j.subject(admin.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.banks.length()").value(0))
            .andExpect(jsonPath("$.overall.accuracy").doesNotExist())
            .andExpect(jsonPath("$.overall.status").value("UNKNOWN"));
        String csv = mockMvc.perform(get("/api/v1/admin/ai/export?from=2019-01-01&to=2019-01-31")
                .with(jwt().jwt(j -> j.subject(admin.toString()))))
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).isEqualTo("﻿Banco;Classificações;Correções;Precisão (%)\r\nTotal;0;0;\r\n");
    }

    @Test
    void invalidFiltersGive400AndNonAdminsGet403() throws Exception {
        for (String query : new String[] {"from=2020-03-10&to=2020-03-01", "from=2020-01-01&to=2021-01-02",
                "from=10/03/2020", "bank=SANTANDER"}) {
            mockMvc.perform(get("/api/v1/admin/ai?" + query).with(jwt().jwt(j -> j.subject(admin.toString()))))
                .andExpect(status().isBadRequest());
        }
        mockMvc.perform(get("/api/v1/admin/ai/detail").with(jwt().jwt(j -> j.subject(admin.toString()))))
            .andExpect(status().isBadRequest());
        UUID plain = user(false);
        for (String route : new String[] {"/api/v1/admin/ai", "/api/v1/admin/ai/detail?bank=X", "/api/v1/admin/ai/export"}) {
            mockMvc.perform(get(route).with(jwt().jwt(j -> j.subject(plain.toString()))))
                .andExpect(status().isForbidden());
        }
    }
}
