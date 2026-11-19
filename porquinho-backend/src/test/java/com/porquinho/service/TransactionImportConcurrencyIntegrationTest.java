package com.porquinho.service;

import com.porquinho.dto.CreateAccountRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

/**
 * Story 5.5: two imports of the same file into the same account at once save its rows only once, because the advisory
 * lock of the target serializes them from the duplicate check to the commit. The audit at the end of that critical
 * section (after saveAll, before the commit) waits for the other import (up to 3 s): without the lock both get there
 * having checked an empty account, so both save; with it the second is still waiting for the lock, the first gives up
 * waiting and commits, and the second sees its rows as duplicates. Not @Transactional; cleans up after.
 */
@SpringBootTest
@ActiveProfiles("test")
// The spy gives this class its own context (and Hikari pool); closing it keeps the suite under max_connections
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TransactionImportConcurrencyIntegrationTest {

    @Autowired
    private TransactionImportService transactionImportService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoSpyBean
    private AuditLogService auditLogService;
    /** Never the real client: nothing here goes to the AI. */
    @MockitoBean
    private TransactionClassifier classifier;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, auth_provider) VALUES (?, ?, 'EMAIL')", userId, userId + "@example.com");
    }

    @AfterEach
    void tearDown() {
        jdbc.update("UPDATE transaction_imports SET status = 'FAILED' WHERE user_id = ? AND status = 'PROCESSING'", userId);
        jdbc.update("DELETE FROM audit_logs WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM ai_classification_history WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM transactions WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM transaction_imports WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM account_balance_history WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM accounts WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }

    private static MockMultipartFile sample(String name) throws IOException {
        byte[] bytes = new ClassPathResource("import-samples/" + name).getInputStream().readAllBytes();
        return new MockMultipartFile("file", name, "application/octet-stream", bytes);
    }

    private String awaitDone(UUID importId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        String status = jdbc.queryForObject("SELECT status FROM transaction_imports WHERE id = ?", String.class, importId);
        while ("PROCESSING".equals(status) && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
            status = jdbc.queryForObject("SELECT status FROM transaction_imports WHERE id = ?", String.class, importId);
        }
        return status;
    }

    @Test
    void twoImportsOfTheSameFileAtOnceSaveItOnlyOnce() throws Exception {
        UUID nubank = accountService.create(userId,
            new CreateAccountRequest("Nubank", null, "CHECKING", new BigDecimal("100.00"))).getId();
        CyclicBarrier bothChecked = new CyclicBarrier(2);
        doAnswer(inv -> {
            try {
                bothChecked.await(3, TimeUnit.SECONDS);
            } catch (TimeoutException | BrokenBarrierException e) {
                // the other import never got here: the advisory lock held it back
            }
            return inv.callRealMethod();
        }).when(auditLogService).log(eq(TransactionImportProcessor.AUDIT_DUPLICATES_CHECKED), eq(userId), any(), any());

        UUID a = transactionImportService.create(userId, nubank, null, sample("nubank-conta.ofx"), "127.0.0.1").id();
        UUID b = transactionImportService.create(userId, nubank, null, sample("nubank-conta.ofx"), "127.0.0.1").id();

        assertThat(new String[] {awaitDone(a), awaitDone(b)}).containsExactlyInAnyOrder("REVIEW", "COMPLETED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE account_id = ?", Long.class, nubank))
            .isEqualTo(10);
    }
}
