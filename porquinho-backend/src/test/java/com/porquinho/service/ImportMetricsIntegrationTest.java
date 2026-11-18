package com.porquinho.service;

import com.porquinho.config.TestWebSecurityConfig;
import com.porquinho.repository.TransactionImportRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 10.2 against the real database (Testcontainers + Flyway). The rows sit in March 2020 so the imports of other
 * tests (always "now") never fall inside the period.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestWebSecurityConfig.class)
class ImportMetricsIntegrationTest {

    private static final String PERIOD = "from=2020-03-01&to=2020-03-31";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionImportRepository repository;

    private UUID admin;
    private UUID account;

    @BeforeEach
    void setUp() {
        admin = UUID.randomUUID();
        account = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, auth_provider, is_admin) VALUES (?, ?, 'EMAIL', true)", admin, admin + "@example.com");
        jdbc.update("INSERT INTO accounts (id, user_id, account_name, account_type, initial_balance, current_balance) "
            + "VALUES (?, ?, 'Conta', 'CHECKING', 0, 0)", account, admin);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM transaction_imports WHERE user_id = ?", admin);
        jdbc.update("DELETE FROM accounts WHERE user_id = ?", admin);
        jdbc.update("DELETE FROM users WHERE id = ?", admin);
    }

    private void insert(String bank, String status, String message, String createdAt) {
        jdbc.update("INSERT INTO transaction_imports (user_id, account_id, file_name, file_type, file_size_kb, status, "
                + "bank_name, error_message, created_at) VALUES (?, ?, 'joao-silva-extrato.ofx', 'OFX', 1, ?, ?, ?, "
                + "CAST(? AS timestamptz))",
            admin, account, status, bank, message, createdAt);
    }

    @Test
    void tableCountsPerBankWithTheNullBankAsUnknownAndProcessingOut() throws Exception {
        insert("NUBANK", "FAILED", TransactionImportService.UNKNOWN_CSV_MESSAGE, "2020-03-10T12:00:00Z");
        insert("NUBANK", "COMPLETED", null, "2020-03-10T12:00:00Z");
        insert("NUBANK", "REVIEW", null, "2020-03-11T12:00:00Z");
        insert("NUBANK", "DISCARDED", null, "2020-03-11T12:00:00Z");
        insert("NUBANK", "PROCESSING", null, "2020-03-11T12:00:00Z");
        insert(null, "FAILED", TransactionImportProcessor.TRUNCATED_MESSAGE, "2020-03-12T12:00:00Z");
        insert("ITAU", "PROCESSING", null, "2020-03-12T12:00:00Z");
        insert("ITAU", "FAILED", null, "2020-04-01T12:00:00Z"); // outside the period

        mockMvc.perform(get("/api/v1/admin/imports?" + PERIOD).with(jwt().jwt(j -> j.subject(admin.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.target").value(5.0))
            .andExpect(jsonPath("$.banks.length()").value(3))
            .andExpect(jsonPath("$.banks[0].bank").value("UNKNOWN"))
            .andExpect(jsonPath("$.banks[0].failures").value(1))
            .andExpect(jsonPath("$.banks[0].status").value("RED"))
            .andExpect(jsonPath("$.banks[1].bank").value("NUBANK"))
            .andExpect(jsonPath("$.banks[1].total").value(4))
            .andExpect(jsonPath("$.banks[1].successes").value(3))
            .andExpect(jsonPath("$.banks[1].errorRate").value(25.0))
            .andExpect(jsonPath("$.banks[2].bank").value("ITAU"))
            .andExpect(jsonPath("$.banks[2].total").value(0))
            .andExpect(jsonPath("$.banks[2].errorRate").doesNotExist())
            .andExpect(jsonPath("$.banks[2].status").value("UNKNOWN"));

        mockMvc.perform(get("/api/v1/admin/imports?bank=UNKNOWN&" + PERIOD).with(jwt().jwt(j -> j.subject(admin.toString()))))
            .andExpect(jsonPath("$.banks.length()").value(1))
            .andExpect(jsonPath("$.banks[0].bank").value("UNKNOWN"));
    }

    @Test
    void detailAndExportFilterByStatusWithoutLeakingUserOrFileName() throws Exception {
        insert("NUBANK", "FAILED", TransactionImportService.UNKNOWN_CSV_MESSAGE, "2020-03-10T12:00:00Z");
        insert("NUBANK", "FAILED", TransactionImportService.UNKNOWN_CSV_MESSAGE, "2020-03-11T12:00:00Z");
        insert("NUBANK", "FAILED", TransactionImportProcessor.INTERRUPTED_MESSAGE, "2020-03-12T12:00:00Z");
        insert("NUBANK", "COMPLETED", null, "2020-03-13T12:00:00Z");
        insert("ITAU", "FAILED", TransactionImportProcessor.INTERRUPTED_MESSAGE, "2020-03-13T12:00:00Z");

        mockMvc.perform(get("/api/v1/admin/imports/detail?bank=NUBANK&status=FAILED&" + PERIOD)
                .with(jwt().jwt(j -> j.subject(admin.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.errorTypes[0].type").value("FORMAT"))
            .andExpect(jsonPath("$.errorTypes[0].count").value(2))
            .andExpect(jsonPath("$.errorTypes[1].type").value("INTERRUPTED"))
            .andExpect(jsonPath("$.recent.length()").value(3))
            .andExpect(jsonPath("$.recent[0].errorType").value("INTERRUPTED"))
            .andExpect(jsonPath("$.trend.length()").value(30))
            .andExpect(content().string(not(containsString(admin.toString()))))
            .andExpect(content().string(not(containsString("joao-silva"))));

        String csv = mockMvc.perform(get("/api/v1/admin/imports/export?bank=NUBANK&status=SUCCESS&" + PERIOD)
                .with(jwt().jwt(j -> j.subject(admin.toString()))))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"erros_importacao_2020-03-01_2020-03-31.csv\""))
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv.split("\r\n")).hasSize(2);
        assertThat(csv).contains("13/03/2020 09:00;Nubank;OFX;Concluída;;").doesNotContain("joao-silva")
            .doesNotContain(admin.toString());
    }

    @Test
    void detailAndExportOfTheUnknownBankReadTheNullBankName() throws Exception {
        insert(null, "FAILED", TransactionImportProcessor.TRUNCATED_MESSAGE, "2020-03-10T12:00:00Z");
        insert(null, "FAILED", TransactionImportProcessor.TRUNCATED_MESSAGE, OffsetDateTime.now().toString());

        mockMvc.perform(get("/api/v1/admin/imports/detail?bank=UNKNOWN&" + PERIOD)
                .with(jwt().jwt(j -> j.subject(admin.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.errorTypes[0].type").value("FORMAT"))
            .andExpect(jsonPath("$.errorTypes[0].count").value(1))
            .andExpect(jsonPath("$.recent.length()").value(1))
            .andExpect(jsonPath("$.recent[0].bank").value("UNKNOWN"))
            .andExpect(jsonPath("$.recent[0].errorType").value("FORMAT"))
            // the trend is the last 30 days whatever the period: today (São Paulo) has the row made now
            .andExpect(jsonPath("$.trend[29].failures").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        String csv = mockMvc.perform(get("/api/v1/admin/imports/export?bank=UNKNOWN&" + PERIOD)
                .with(jwt().jwt(j -> j.subject(admin.toString()))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv.split("\r\n")).hasSize(2);
        assertThat(csv).contains("10/03/2020 09:00;Não identificado;OFX;Falhou;Problema de formato;");
    }

    @Test
    void invalidFiltersGive400() throws Exception {
        for (String query : new String[] {"from=2020-03-10&to=2020-03-01", "from=2020-01-01&to=2021-01-02",
                "from=10/03/2020", "bank=SANTANDER"}) {
            mockMvc.perform(get("/api/v1/admin/imports?" + query).with(jwt().jwt(j -> j.subject(admin.toString()))))
                .andExpect(status().isBadRequest());
        }
        mockMvc.perform(get("/api/v1/admin/imports/detail").with(jwt().jwt(j -> j.subject(admin.toString()))))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/admin/imports/export?status=X").with(jwt().jwt(j -> j.subject(admin.toString()))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void dailyCountsUseTheSaoPauloDay() {
        String bank = "T" + UUID.randomUUID(); // no other row has this bank
        insert(bank, "FAILED", null, "2020-03-02T02:00:00Z"); // 23:00 of March 1st in São Paulo
        insert(bank, "COMPLETED", null, "2020-03-02T04:00:00Z");

        var days = repository.countByDay(bank, OffsetDateTime.parse("2020-03-01T00:00-03:00"),
            OffsetDateTime.parse("2020-03-03T00:00-03:00"));

        assertThat(days).extracting(TransactionImportRepository.DayCounts::getDay,
                TransactionImportRepository.DayCounts::getFailures, TransactionImportRepository.DayCounts::getSuccesses)
            .containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple("2020-03-01", 1L, 0L),
                org.assertj.core.groups.Tuple.tuple("2020-03-02", 0L, 1L));
    }
}
