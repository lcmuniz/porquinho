package com.porquinho.service;

import com.porquinho.dto.CreateAccountRequest;
import com.porquinho.dto.CreateReconciliationRequest;
import com.porquinho.dto.ReconciliationResponse;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.repository.AccountBalanceHistoryRepository;
import com.porquinho.repository.AccountRepository;
import com.porquinho.repository.ReconciliationRepository;
import com.porquinho.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Story 4.6: reconciliation against the real schema (PostgreSQL via Testcontainers, Flyway V23): new balance, event,
 * "reconciliation" snapshot, last_reconciliation_date and audit in one transaction; zero difference; 404 for another
 * user's or a removed account with nothing changed; atomicity; the list; purge order and export.
 * Not @Transactional: each service call commits on its own. Cleans up after each test.
 */
@SpringBootTest
@ActiveProfiles("test")
class ReconciliationIntegrationTest {

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private ReconciliationRepository reconciliationRepository;

    @Autowired
    private AccountBalanceHistoryRepository balanceHistoryRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DataExportService dataExportService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbc;

    private final List<UUID> users = new ArrayList<>();
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = newUser();
    }

    @AfterEach
    void tearDown() {
        for (UUID id : users) {
            jdbc.update("DELETE FROM audit_logs WHERE user_id = ?", id);
            jdbc.update("DELETE FROM reconciliation_history WHERE user_id = ?", id);
            jdbc.update("DELETE FROM account_balance_history WHERE user_id = ?", id);
            jdbc.update("DELETE FROM accounts WHERE user_id = ?", id);
            jdbc.update("DELETE FROM users WHERE id = ?", id);
        }
    }

    private UUID newUser() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, auth_provider) VALUES (?, ?, 'EMAIL')", id, id + "@example.com");
        users.add(id);
        return id;
    }

    private UUID add(UUID owner, String name, String balance) {
        return accountService.create(owner, new CreateAccountRequest(name, null, "CHECKING", new BigDecimal(balance))).getId();
    }

    private ReconciliationResponse.Created reconcile(UUID accountId, String bankBalance, String reason) {
        return reconciliationService.reconcile(userId, accountId,
            new CreateReconciliationRequest(new BigDecimal(bankBalance), reason), "10.0.0.1");
    }

    private BigDecimal balance(UUID accountId) {
        return jdbc.queryForObject("SELECT current_balance FROM accounts WHERE id = ?", BigDecimal.class, accountId);
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    @Test
    void bankShowsMoreAdjustsTheBalanceAndRecordsEverything() {
        // Critério de aceite: Nubank R$ 1.000,00, banco R$ 1.050,00, "Ajustar saldo"
        UUID nubank = add(userId, "Nubank", "1000.00");

        ReconciliationResponse.Created result = reconcile(nubank, "1050.00", " Tarifa estornada ");

        assertThat(balance(nubank)).isEqualByComparingTo("1050.00");
        assertThat(result.account().currentBalance()).isEqualByComparingTo("1050.00");
        assertThat(result.account().lastReconciliationDate()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT last_reconciliation_date FROM accounts WHERE id = ?", Object.class, nubank))
            .isNotNull();

        Map<String, Object> row = jdbc.queryForMap("SELECT account_id, user_id, previous_balance, bank_balance, "
            + "difference, reason, reconciled_at FROM reconciliation_history WHERE id = ?", result.reconciliation().id());
        assertThat(row.get("account_id")).isEqualTo(nubank);
        assertThat(row.get("user_id")).isEqualTo(userId);
        assertThat((BigDecimal) row.get("previous_balance")).isEqualByComparingTo("1000.00");
        assertThat((BigDecimal) row.get("bank_balance")).isEqualByComparingTo("1050.00");
        assertThat((BigDecimal) row.get("difference")).isEqualByComparingTo("50.00");
        assertThat(row.get("reason")).isEqualTo("Tarifa estornada");
        assertThat(row.get("reconciled_at")).isNotNull();

        assertThat(jdbc.queryForObject("SELECT balance_snapshot FROM account_balance_history "
            + "WHERE account_id = ? AND created_by_event = 'reconciliation'", BigDecimal.class, nubank))
            .isEqualByComparingTo("1050.00");

        Map<String, Object> audit = jdbc.queryForMap(
            "SELECT ip_address, metadata FROM audit_logs WHERE user_id = ? AND event_type = 'account_reconciled'", userId);
        assertThat(audit.get("ip_address")).isEqualTo("10.0.0.1");
        assertThat(audit.get("metadata").toString()).contains(nubank.toString()).contains("50.00");
    }

    @Test
    void bankShowsLessGivesNegativeDifference() {
        UUID nubank = add(userId, "Nubank", "1000.00");

        ReconciliationResponse.Created result = reconcile(nubank, "980.00", null);

        assertThat(balance(nubank)).isEqualByComparingTo("980.00");
        assertThat(result.reconciliation().difference()).isEqualByComparingTo("-20.00");
    }

    @Test
    void equalBalancesRecordTheEventAndDateButNoSnapshot() {
        UUID nubank = add(userId, "Nubank", "1000.00");

        ReconciliationResponse.Created result = reconcile(nubank, "1000.00", null);

        assertThat(balance(nubank)).isEqualByComparingTo("1000.00");
        assertThat(result.reconciliation().difference()).isEqualByComparingTo("0");
        assertThat(count("SELECT COUNT(*) FROM reconciliation_history WHERE account_id = ?", nubank)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT last_reconciliation_date FROM accounts WHERE id = ?", Object.class, nubank))
            .isNotNull();
        assertThat(count("SELECT COUNT(*) FROM account_balance_history WHERE account_id = ? "
            + "AND created_by_event = 'reconciliation'", nubank)).isZero();
        assertThat(count("SELECT COUNT(*) FROM audit_logs WHERE user_id = ? AND event_type = 'account_reconciled'", userId))
            .isEqualTo(1);
    }

    @Test
    void negativeBankBalanceIsAllowed() {
        UUID nubank = add(userId, "Nubank", "10.00");

        reconcile(nubank, "-30.00", null);

        assertThat(balance(nubank)).isEqualByComparingTo("-30.00");
    }

    @Test
    void differenceIsComputedFromTheStoredBalance() {
        // Saldo mudou em outra aba: o servidor tem 900,00 e a tela ainda mostra 1.000,00
        UUID nubank = add(userId, "Nubank", "1000.00");
        jdbc.update("UPDATE accounts SET current_balance = 900.00 WHERE id = ?", nubank);

        ReconciliationResponse.Created result = reconcile(nubank, "1050.00", null);

        assertThat(result.reconciliation().previousBalance()).isEqualByComparingTo("900.00");
        assertThat(result.reconciliation().difference()).isEqualByComparingTo("150.00");
    }

    @Test
    void anotherUsersAccountIs404AndNothingChanges() {
        UUID theirs = add(newUser(), "Alheia", "100.00");

        assertThatThrownBy(() -> reconcile(theirs, "500.00", null))
            .isInstanceOf(ResourceNotFoundException.class).hasMessage("Conta não encontrada");

        assertThat(balance(theirs)).isEqualByComparingTo("100.00");
        assertThat(count("SELECT COUNT(*) FROM reconciliation_history WHERE account_id = ?", theirs)).isZero();
        assertThat(jdbc.queryForObject("SELECT last_reconciliation_date FROM accounts WHERE id = ?", Object.class, theirs))
            .isNull();
        assertThatThrownBy(() -> reconciliationService.listRecent(userId, theirs))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void removedAccountIs404AndNothingChanges() {
        UUID nubank = add(userId, "Nubank", "100.00");
        add(userId, "Inter", "100.00");
        accountService.delete(userId, nubank, "127.0.0.1");

        assertThatThrownBy(() -> reconcile(nubank, "500.00", null)).isInstanceOf(ResourceNotFoundException.class);

        assertThat(balance(nubank)).isEqualByComparingTo("100.00");
        assertThat(count("SELECT COUNT(*) FROM reconciliation_history WHERE account_id = ?", nubank)).isZero();
    }

    @Test
    void failureWhileWritingTheEventRollsBackTheBalanceTheDateAndTheHistory() {
        // O serviço não confere o tamanho do motivo (a validação do request confere); 201 caracteres enviados direto
        // passam do lock e do cálculo e falham no VARCHAR(200): nada pode sobrar.
        UUID nubank = add(userId, "Nubank", "1000.00");

        assertThatThrownBy(() -> reconcile(nubank, "1050.00", "a".repeat(201)))
            .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(balance(nubank)).isEqualByComparingTo("1000.00");
        assertThat(jdbc.queryForObject("SELECT last_reconciliation_date FROM accounts WHERE id = ?", Object.class, nubank))
            .isNull();
        assertThat(count("SELECT COUNT(*) FROM reconciliation_history WHERE user_id = ?", userId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM account_balance_history WHERE user_id = ? "
            + "AND created_by_event = 'reconciliation'", userId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM audit_logs WHERE user_id = ? AND event_type = 'account_reconciled'", userId))
            .isZero();
    }

    @Test
    void listReturnsTheAccountsReconciliationsMostRecentFirst() {
        UUID nubank = add(userId, "Nubank", "1000.00");
        UUID inter = add(userId, "Inter", "500.00");
        reconcile(nubank, "1050.00", "primeira");
        reconcile(nubank, "1000.00", "segunda");
        reconcile(inter, "400.00", null);

        List<ReconciliationResponse> list = reconciliationService.listRecent(userId, nubank);

        assertThat(list).extracting(ReconciliationResponse::reason).containsExactly("segunda", "primeira");
        assertThat(list.get(0).difference()).isEqualByComparingTo("-50.00");
    }

    @Test
    void purgeDeletesReconciliationsBeforeAccountsAndTheUser() {
        UUID nubank = add(userId, "Nubank", "1000.00");
        reconcile(nubank, "1050.00", null);

        transactionTemplate.executeWithoutResult(status -> {
            assertThat(reconciliationRepository.deleteByUserId(userId)).isEqualTo(1);
            assertThat(balanceHistoryRepository.deleteByUserId(userId)).isEqualTo(2);
            assertThat(accountRepository.deleteByUserId(userId)).isEqualTo(1);
            jdbc.update("DELETE FROM audit_logs WHERE user_id = ?", userId);
            assertThat(userRepository.deleteByIdNative(userId)).isEqualTo(1);
        });
    }

    @Test
    void exportIncludesTheReconciliationsAndTheDate() {
        UUID nubank = add(userId, "Nubank", "1000.00");
        ReconciliationResponse.Created created = reconcile(nubank, "1050.00", null);

        Map<String, Object> data = dataExportService.collectUserData(userId);

        @SuppressWarnings("unchecked")
        var rows = (List<Map<String, Object>>) data.get("reconciliation_history");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("id")).isEqualTo(created.reconciliation().id().toString());
        assertThat(rows.get(0).get("account_id")).isEqualTo(nubank.toString());
        @SuppressWarnings("unchecked")
        var accounts = (List<Map<String, Object>>) data.get("accounts");
        assertThat(accounts.get(0).get("last_reconciliation_date")).isNotNull();
    }
}
