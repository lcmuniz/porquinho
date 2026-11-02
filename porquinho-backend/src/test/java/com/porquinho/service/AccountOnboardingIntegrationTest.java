package com.porquinho.service;

import com.porquinho.dto.CreateAccountRequest;
import com.porquinho.entity.Account;
import com.porquinho.exception.ConflictException;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.repository.AccountRepository;
import com.porquinho.repository.OnboardingProgressRepository;
import com.porquinho.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
 * the CHECK on account_type, the purge order and the export.
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

        accountService.delete(userId, removed.getId());
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
        accountService.delete(userId, mine.getId());

        assertThatThrownBy(() -> accountService.delete(userId, foreign.getId())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> accountService.delete(userId, mine.getId())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> accountService.delete(userId, UUID.randomUUID())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void nextWithOnlyRemovedAccountsIsAConflict() {
        atStepTwo();
        Account account = add(userId, "Nubank", "1.00");
        accountService.delete(userId, account.getId());
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
        accountService.delete(userId, removed.getId());
        accountRepository.flush();
        UUID other = newUser();
        add(other, "De outra pessoa", "1.00");

        assertThat(accountRepository.deleteByUserId(userId)).isEqualTo(2);
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
        accountService.delete(userId, removed.getId());
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
}
