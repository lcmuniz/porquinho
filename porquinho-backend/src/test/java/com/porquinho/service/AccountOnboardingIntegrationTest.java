package com.porquinho.service;

import com.porquinho.dto.CreateAccountRequest;
import com.porquinho.entity.Account;
import com.porquinho.entity.OnboardingProgress;
import com.porquinho.exception.ConflictException;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.repository.AccountBalanceHistoryRepository;
import com.porquinho.repository.AccountRepository;
import com.porquinho.repository.OnboardingProgressRepository;
import com.porquinho.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.porquinho.dto.UpdateAccountRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Story 3.2: accounts and onboarding step 2 against the real schema (PostgreSQL via Testcontainers, Flyway V16):
 * create/list/remove scoped by user, the step-2 matrix (next, skip with/without accounts, repeat, step 1),
 * the CHECK on account_type, the purge order and the export. Story 4.3: editing (PUT) and the balance
 * history (V20) against the same schema. Story 4.4: audited removal, the last-active-account rule, deleted_by (V21),
 * the removed list and restore, with the balance history preserved.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AccountOnboardingIntegrationTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private OnboardingService onboardingService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private OnboardingProgressRepository onboardingProgressRepository;

    @Autowired
    private AccountBalanceHistoryRepository balanceHistoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DataExportService dataExportService;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = newUser();
    }

    private UUID newUser() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, auth_provider) VALUES (?, ?, 'EMAIL')", id, id + "@example.com");
        return id;
    }

    private Account add(UUID owner, String name, String balance) {
        Account account = accountService.create(owner, new CreateAccountRequest(name, null, "CHECKING", new BigDecimal(balance)));
        accountRepository.flush();
        return account;
    }

    private void atStepTwo() {
        onboardingService.completeTutorial(userId, false, "127.0.0.1");
        onboardingProgressRepository.flush();
    }

    private Map<String, Object> progressRow() {
        return jdbc.queryForMap("SELECT current_step, steps_completed FROM onboarding_progress WHERE user_id = ?", userId);
    }

    private List<String> accountsAudits() {
        return jdbc.queryForList("SELECT metadata FROM audit_logs WHERE user_id = ? AND event_type = 'onboarding_accounts_completed'",
            String.class, userId);
    }

    @Test
    void nubankWithR$1234_56ThenNextReachesStepThree() {
        // Acceptance criterion: "Nubank" with R$ 1.234,56, then Próximo
        atStepTwo();
        accountService.create(userId, new CreateAccountRequest("Nubank", "Nubank", "CHECKING", new BigDecimal("1234.56")));
        accountRepository.flush();

        onboardingService.completeAccounts(userId, false, "127.0.0.1");
        onboardingProgressRepository.flush();

        Map<String, Object> account = jdbc.queryForMap(
            "SELECT account_name, bank_name, account_type, initial_balance, current_balance, deleted_at FROM accounts WHERE user_id = ?", userId);
        assertThat(account).containsEntry("account_name", "Nubank").containsEntry("account_type", "CHECKING")
            .containsEntry("initial_balance", new BigDecimal("1234.56"))
            .containsEntry("current_balance", new BigDecimal("1234.56"))
            .containsEntry("deleted_at", null);
        assertThat(progressRow()).containsEntry("current_step", 3)
            .containsEntry("steps_completed", "[\"tutorial_viewed\",\"accounts_added\"]");
        assertThat(accountsAudits()).containsExactly("{\"skipped\":false,\"accounts\":1}");
    }

    @Test
    void listIsScopedByUserOldestFirstAndHidesRemovedAccounts() {
        Account first = add(userId, "Nubank", "10.00");
        Account second = add(userId, "Itaú", "-150.00");
        Account removed = add(userId, "Antiga", "0");
        add(newUser(), "De outra pessoa", "99.99");

        accountService.delete(userId, removed.getId(), "127.0.0.1");
        accountRepository.flush();

        assertThat(accountService.listActive(userId)).extracting(Account::getId).containsExactly(first.getId(), second.getId());
        assertThat(accountService.countActive(userId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM accounts WHERE id = ?", Boolean.class, removed.getId()))
            .isTrue();
    }

    @Test
    void removingAnotherUsersOrAnAlreadyRemovedAccountIsNotFound() {
        Account foreign = add(newUser(), "De outra pessoa", "1.00");
        Account mine = add(userId, "Minha", "1.00");
        accountService.delete(userId, mine.getId(), "127.0.0.1");

        assertThatThrownBy(() -> accountService.delete(userId, foreign.getId(), "127.0.0.1")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> accountService.delete(userId, mine.getId(), "127.0.0.1")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> accountService.delete(userId, UUID.randomUUID(), "127.0.0.1")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void nextWithOnlyRemovedAccountsIsAConflict() {
        atStepTwo();
        Account account = add(userId, "Nubank", "1.00");
        accountService.delete(userId, account.getId(), "127.0.0.1");
        accountRepository.flush();

        assertThatThrownBy(() -> onboardingService.completeAccounts(userId, false, "127.0.0.1"))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void skippingWithoutAccountsCreatesMinhaContaPrincipal() {
        atStepTwo();

        onboardingService.completeAccounts(userId, true, "127.0.0.1");
        accountRepository.flush();

        List<Account> accounts = accountService.listActive(userId);
        assertThat(accounts).hasSize(1);
        assertThat(accounts.get(0).getAccountName()).isEqualTo("Minha Conta Principal");
        assertThat(accounts.get(0).getAccountType()).isEqualTo(Account.AccountType.CHECKING);
        assertThat(accounts.get(0).getInitialBalance()).isEqualByComparingTo("0");
        assertThat(accounts.get(0).getCurrentBalance()).isEqualByComparingTo("0");
        assertThat(progressRow()).containsEntry("current_step", 3)
            .containsEntry("steps_completed", "[\"tutorial_viewed\",\"accounts_skipped\"]");
        assertThat(accountsAudits()).containsExactly("{\"skipped\":true,\"accounts\":1}");
    }

    @Test
    void skippingWithAccountsOnlyAdvancesAndRepeatingChangesNothing() {
        atStepTwo();
        add(userId, "Nubank", "1.00");
        add(userId, "Itaú", "2.00");

        onboardingService.completeAccounts(userId, true, "127.0.0.1");
        onboardingProgressRepository.flush();
        onboardingService.completeAccounts(userId, false, "127.0.0.1");
        onboardingService.completeAccounts(userId, true, "127.0.0.1");
        onboardingProgressRepository.flush();

        assertThat(accountService.countActive(userId)).isEqualTo(2);
        assertThat(progressRow()).containsEntry("current_step", 3)
            .containsEntry("steps_completed", "[\"tutorial_viewed\",\"accounts_skipped\"]");
        assertThat(accountsAudits()).containsExactly("{\"skipped\":true,\"accounts\":2}");
    }

    @Test
    void accountsStepIsAConflictOnStepOne() {
        onboardingService.getOrCreate(userId);

        assertThatThrownBy(() -> onboardingService.completeAccounts(userId, true, "127.0.0.1"))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void accountTypeOutsideTheCheckIsRejectedByTheDatabase() {
        assertThatThrownBy(() -> jdbc.update(
            "INSERT INTO accounts (user_id, account_name, account_type, initial_balance, current_balance) VALUES (?, 'X', 'CREDIT', 0, 0)",
            userId)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void purgeDeletesAllOfTheUsersAccountsIncludingRemovedOnesAndThenTheUser() {
        // Purge SQL: removed accounts go too, other users keep theirs (the call order is in AccountDeletionServiceTest)
        Account removed = add(userId, "Removida", "1.00");
        add(userId, "Ativa", "1.00");
        accountService.delete(userId, removed.getId(), "127.0.0.1");
        accountRepository.flush();
        UUID other = newUser();
        add(other, "De outra pessoa", "1.00");

        assertThat(balanceHistoryRepository.deleteByUserId(userId)).isEqualTo(2);  // Story 4.3: one "initial" each
        assertThat(accountRepository.deleteByUserId(userId)).isEqualTo(2);
        // Story 4.4: removing writes an audit; the real purge hard-deletes audit_logs before the user
        jdbc.update("DELETE FROM audit_logs WHERE user_id = ?", userId);
        assertThat(userRepository.deleteByIdNative(userId)).isEqualTo(1);
        assertThat(accountService.countActive(other)).isEqualTo(1);
    }

    @Test
    void foreignKeyBlocksDeletingTheUser() {
        add(userId, "Nubank", "1.00");

        assertThatThrownBy(() -> userRepository.deleteByIdNative(userId))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void exportIncludesActiveAndRemovedAccounts() {
        add(userId, "Nubank", "1234.56");
        Account removed = add(userId, "Antiga", "-150.00");
        accountService.delete(userId, removed.getId(), "127.0.0.1");
        accountRepository.flush();

        @SuppressWarnings("unchecked")
        var rows = (List<Map<String, Object>>) dataExportService.collectUserData(userId).get("accounts");

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("account_name", "Nubank").containsEntry("account_type", "CHECKING")
            .containsEntry("deleted_at", null);
        assertThat((BigDecimal) rows.get(0).get("initial_balance")).isEqualByComparingTo("1234.56");
        assertThat(rows.get(1)).containsEntry("account_name", "Antiga");
        assertThat(rows.get(1).get("deleted_at")).isNotNull();
    }

    // Story 4.3: editing and the balance history

    private List<Map<String, Object>> history(UUID accountId) {
        return jdbc.queryForList(
            "SELECT user_id, balance_snapshot, created_by_event, snapshot_date FROM account_balance_history "
                + "WHERE account_id = ? ORDER BY snapshot_date, created_at", accountId);
    }

    @Test
    void editingNubankTo250UpdatesTheCurrentBalanceKeepsTheInitialAndRecordsAnEdit() throws Exception {
        // Acceptance criterion: "Nubank" with R$ 100,00 edited to "Nubank PJ" with R$ 250,00
        Account account = add(userId, "Nubank", "100.00");
        java.time.OffsetDateTime createdUpdatedAt = jdbc.queryForObject(
            "SELECT updated_at FROM accounts WHERE id = ?", java.time.OffsetDateTime.class, account.getId());
        Thread.sleep(5);

        accountService.update(userId, account.getId(),
            new UpdateAccountRequest("Nubank PJ", "Nubank", "CHECKING", new BigDecimal("250.00"), account.getVersion()));
        accountRepository.flush();
        balanceHistoryRepository.flush();

        Map<String, Object> row = jdbc.queryForMap(
            "SELECT account_name, initial_balance, current_balance, updated_at FROM accounts WHERE id = ?", account.getId());
        assertThat(row).containsEntry("account_name", "Nubank PJ")
            .containsEntry("initial_balance", new BigDecimal("100.00"))
            .containsEntry("current_balance", new BigDecimal("250.00"));
        assertThat(jdbc.queryForObject("SELECT updated_at > ? FROM accounts WHERE id = ?", Boolean.class,
            createdUpdatedAt, account.getId())).isTrue();

        List<Map<String, Object>> history = history(account.getId());
        assertThat(history).hasSize(2);
        assertThat(history.get(0)).containsEntry("created_by_event", "initial")
            .containsEntry("balance_snapshot", new BigDecimal("100.00")).containsEntry("user_id", userId);
        assertThat(history.get(1)).containsEntry("created_by_event", "edit")
            .containsEntry("balance_snapshot", new BigDecimal("250.00")).containsEntry("user_id", userId);
        assertThat(history.get(1).get("snapshot_date")).isNotNull();
    }

    @Test
    void editingOnlyTheNameRecordsNoSnapshot() {
        Account account = add(userId, "Nubank", "100.00");

        accountService.update(userId, account.getId(),
            new UpdateAccountRequest("Nubank PJ", null, "SAVINGS", new BigDecimal("100"), account.getVersion()));
        accountRepository.flush();

        assertThat(history(account.getId())).extracting(r -> r.get("created_by_event")).containsExactly("initial");
        assertThat(jdbc.queryForObject("SELECT account_type FROM accounts WHERE id = ?", String.class, account.getId()))
            .isEqualTo("SAVINGS");
    }

    @Test
    void creatingAnAccountRecordsAnInitialSnapshotWithTheInitialBalance() {
        Account account = add(userId, "Itaú", "-150.00");

        assertThat(history(account.getId())).singleElement().satisfies(r -> {
            assertThat(r).containsEntry("created_by_event", "initial");
            assertThat((BigDecimal) r.get("balance_snapshot")).isEqualByComparingTo("-150.00");
        });
    }

    @Test
    void minhaContaPrincipalAlsoRecordsAnInitialSnapshot() {
        atStepTwo();
        onboardingService.completeAccounts(userId, true, "127.0.0.1");
        accountRepository.flush();

        Account account = accountService.listActive(userId).get(0);
        assertThat(history(account.getId())).singleElement().satisfies(r -> {
            assertThat(r).containsEntry("created_by_event", "initial");
            assertThat((BigDecimal) r.get("balance_snapshot")).isEqualByComparingTo("0");
        });
    }

    @Test
    void editingAnotherUsersARemovedOrAMissingAccountIsNotFoundAndChangesNothing() {
        Account foreign = add(newUser(), "De outra pessoa", "1.00");
        Account removed = add(userId, "Removida", "1.00");
        accountService.delete(userId, removed.getId(), "127.0.0.1");
        accountRepository.flush();
        UpdateAccountRequest request = new UpdateAccountRequest("Minha", null, "CHECKING", new BigDecimal("999.00"), 0L);

        assertThatThrownBy(() -> accountService.update(userId, foreign.getId(), request))
            .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> accountService.update(userId, removed.getId(), request))
            .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> accountService.update(userId, UUID.randomUUID(), request))
            .isInstanceOf(ResourceNotFoundException.class);
        accountRepository.flush();

        assertThat(jdbc.queryForObject("SELECT account_name FROM accounts WHERE id = ?", String.class, foreign.getId()))
            .isEqualTo("De outra pessoa");
        assertThat(history(foreign.getId())).hasSize(1);
        assertThat(history(removed.getId())).hasSize(1);
    }

    @Test
    void createdByEventOutsideTheCheckIsRejectedByTheDatabase() {
        Account account = add(userId, "Nubank", "1.00");

        assertThatThrownBy(() -> jdbc.update(
            "INSERT INTO account_balance_history (account_id, user_id, balance_snapshot, snapshot_date, created_by_event) "
                + "VALUES (?, ?, 0, NOW(), 'manual')", account.getId(), userId))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingAnAccountCascadesToItsHistory() {
        // V20: ON DELETE CASCADE, so a snapshot written during the LGPD purge never blocks deleting accounts
        Account account = add(userId, "Nubank", "1.00");

        assertThat(accountRepository.deleteByUserId(userId)).isEqualTo(1);
        assertThat(history(account.getId())).isEmpty();
    }

    @Test
    void v20BackfillsAnInitialSnapshotForEveryExistingAccount() throws Exception {
        // Accounts created before V20 cannot be simulated here (Flyway already ran on the container):
        // assert the backfill statement is in the migration, removed accounts included (no WHERE)
        String sql;
        try (var in = getClass().getResourceAsStream("/db/migration/V20__create_account_balance_history_table.sql")) {
            sql = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).replaceAll("\\s+", " ");
        }
        assertThat(sql).contains("INSERT INTO account_balance_history (account_id, user_id, balance_snapshot, "
            + "snapshot_date, created_by_event) SELECT id, user_id, initial_balance, created_at, 'initial' FROM accounts;");
    }

    @Test
    void exportIncludesTheBalanceHistory() throws Exception {
        Account account = add(userId, "Nubank", "100.00");
        Thread.sleep(5);  // distinct snapshot_date, so the order is deterministic
        accountService.update(userId, account.getId(),
            new UpdateAccountRequest("Nubank", null, "CHECKING", new BigDecimal("250.00"), account.getVersion()));
        accountRepository.flush();
        balanceHistoryRepository.flush();

        @SuppressWarnings("unchecked")
        var rows = (List<Map<String, Object>>) dataExportService.collectUserData(userId).get("account_balance_history");

        assertThat(rows).extracting(r -> r.get("created_by_event")).containsExactly("initial", "edit");
        assertThat(rows).allSatisfy(r -> assertThat(r).containsEntry("account_id", account.getId().toString()));
        assertThat((BigDecimal) rows.get(1).get("balance_snapshot")).isEqualByComparingTo("250.00");
    }

    // Story 4.4: remove and restore

    private void onboardingCompleted() {
        OnboardingProgress progress = onboardingService.getOrCreate(userId);
        progress.setCompleted(true);
        onboardingProgressRepository.saveAndFlush(progress);
    }

    private Map<String, Object> deletionRow(UUID accountId) {
        return jdbc.queryForMap("SELECT deleted_at, deleted_by FROM accounts WHERE id = ?", accountId);
    }

    private List<String> audits(String eventType) {
        return jdbc.queryForList("SELECT metadata FROM audit_logs WHERE user_id = ? AND event_type = ?",
            String.class, userId, eventType);
    }

    @Test
    void removingBbWithTwoActiveAccountsFillsDeletedByAuditsAndKeepsTheHistory() throws Exception {
        onboardingCompleted();
        add(userId, "Nubank", "100.00");
        Account bb = add(userId, "BB", "50.00");
        Thread.sleep(5);  // distinct snapshot_date, so the history order is deterministic
        accountService.update(userId, bb.getId(), new UpdateAccountRequest("BB", null, "CHECKING", new BigDecimal("75.00"),
            bb.getVersion()));
        accountRepository.flush();

        accountService.delete(userId, bb.getId(), "127.0.0.1");
        accountRepository.flush();

        assertThat(accountService.listActive(userId)).extracting(Account::getAccountName).containsExactly("Nubank");
        assertThat(deletionRow(bb.getId())).containsEntry("deleted_by", userId);
        assertThat(deletionRow(bb.getId()).get("deleted_at")).isNotNull();
        assertThat(audits("account_deleted")).singleElement().satisfies(m -> assertThat(m).contains(bb.getId().toString()));
        assertThat(history(bb.getId())).extracting(r -> r.get("created_by_event")).containsExactly("initial", "edit");

        Account restored = accountService.restore(userId, bb.getId(), "127.0.0.1");
        accountRepository.flush();

        assertThat(restored.getDeletedAt()).isNull();
        assertThat(deletionRow(bb.getId())).containsEntry("deleted_at", null).containsEntry("deleted_by", null);
        assertThat(accountService.listActive(userId)).extracting(Account::getAccountName).containsExactly("Nubank", "BB");
        assertThat(audits("account_restored")).singleElement().satisfies(m -> assertThat(m).contains(bb.getId().toString()));
        assertThat(history(bb.getId())).extracting(r -> r.get("created_by_event")).containsExactly("initial", "edit");
    }

    @Test
    void removingTheLastActiveAccountAfterTheOnboardingIsAConflictAndKeepsItActive() {
        onboardingCompleted();
        Account only = add(userId, "Nubank", "100.00");
        Account removed = add(userId, "Antiga", "1.00");
        accountService.delete(userId, removed.getId(), "127.0.0.1");
        accountRepository.flush();

        assertThatThrownBy(() -> accountService.delete(userId, only.getId(), "127.0.0.1"))
            .isInstanceOf(ConflictException.class)
            .hasMessage("Mantenha ao menos uma conta ativa");
        accountRepository.flush();

        assertThat(deletionRow(only.getId())).containsEntry("deleted_at", null).containsEntry("deleted_by", null);
        assertThat(audits("account_deleted")).hasSize(1);
    }

    @Test
    void removingTheLastAccountDuringStepTwoIsAllowedAndSkippingCreatesMinhaContaPrincipal() {
        atStepTwo();
        Account only = add(userId, "Nubank", "100.00");

        accountService.delete(userId, only.getId(), "127.0.0.1");
        accountRepository.flush();
        onboardingService.completeAccounts(userId, true, "127.0.0.1");
        accountRepository.flush();

        assertThat(accountService.listActive(userId)).extracting(Account::getAccountName)
            .containsExactly(AccountService.DEFAULT_ACCOUNT_NAME);
    }

    @Test
    void removedListIsScopedByUserMostRecentlyRemovedFirst() throws Exception {
        Account first = add(userId, "Primeira", "1.00");
        Account second = add(userId, "Segunda", "1.00");
        add(userId, "Ativa", "1.00");
        Account foreign = add(newUser(), "De outra pessoa", "1.00");
        accountService.delete(userId, first.getId(), "127.0.0.1");
        accountRepository.flush();
        Thread.sleep(5);  // distinct deleted_at
        accountService.delete(userId, second.getId(), "127.0.0.1");
        accountService.delete(foreign.getUserId(), foreign.getId(), "127.0.0.1");
        accountRepository.flush();

        assertThat(accountService.listDeleted(userId)).extracting(Account::getAccountName)
            .containsExactly("Segunda", "Primeira");
    }

    @Test
    void restoringAnActiveAnotherUsersOrAMissingAccountIsNotFound() {
        Account active = add(userId, "Ativa", "1.00");
        Account foreign = add(newUser(), "De outra pessoa", "1.00");
        accountService.delete(foreign.getUserId(), foreign.getId(), "127.0.0.1");
        accountRepository.flush();

        assertThatThrownBy(() -> accountService.restore(userId, active.getId(), "127.0.0.1"))
            .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> accountService.restore(userId, foreign.getId(), "127.0.0.1"))
            .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> accountService.restore(userId, UUID.randomUUID(), "127.0.0.1"))
            .isInstanceOf(ResourceNotFoundException.class);
        assertThat(deletionRow(foreign.getId()).get("deleted_at")).isNotNull();
        assertThat(audits("account_restored")).isEmpty();
    }

    @Test
    void exportIncludesDeletedBy() {
        add(userId, "Nubank", "1.00");
        Account removed = add(userId, "Antiga", "1.00");
        accountService.delete(userId, removed.getId(), "127.0.0.1");
        accountRepository.flush();

        @SuppressWarnings("unchecked")
        var rows = (List<Map<String, Object>>) dataExportService.collectUserData(userId).get("accounts");

        assertThat(rows.get(0)).containsEntry("deleted_by", null);
        assertThat(rows.get(1)).containsEntry("deleted_by", userId.toString());
    }
}
