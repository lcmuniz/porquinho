package com.porquinho.service;

import com.porquinho.dto.CreateAccountRequest;
import com.porquinho.dto.CreateCreditCardRequest;
import com.porquinho.entity.CreditCard;
import com.porquinho.exception.ConflictException;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.repository.AccountRepository;
import com.porquinho.repository.CreditCardRepository;
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
 * Story 3.3: credit cards and onboarding step 3 against the real schema (PostgreSQL via Testcontainers, Flyway V17):
 * create/list/remove scoped by user, the step-3 matrix (next with/without cards, skip, repeat, steps 1 and 2),
 * the CHECK on due_day, the purge SQL, the FK to users and the export.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CreditCardOnboardingIntegrationTest {

    @Autowired
    private CreditCardService creditCardService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private OnboardingService onboardingService;

    @Autowired
    private CreditCardRepository creditCardRepository;

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

    private CreditCard add(UUID owner, String name, String limit, int dueDay) {
        CreditCard card = creditCardService.create(owner,
            new CreateCreditCardRequest(name, null, new BigDecimal(limit), BigDecimal.valueOf(dueDay)));
        creditCardRepository.flush();
        return card;
    }

    private void atStepTwo() {
        onboardingService.completeTutorial(userId, false, "127.0.0.1");
        onboardingProgressRepository.flush();
    }

    private void atStepThree() {
        atStepTwo();
        accountService.create(userId, new CreateAccountRequest("Nubank", null, "CHECKING", BigDecimal.ONE));
        accountRepository.flush();
        onboardingService.completeAccounts(userId, false, "127.0.0.1");
        onboardingProgressRepository.flush();
    }

    private Map<String, Object> progressRow() {
        return jdbc.queryForMap("SELECT current_step, steps_completed FROM onboarding_progress WHERE user_id = ?", userId);
    }

    private List<String> cardsAudits() {
        return jdbc.queryForList("SELECT metadata FROM audit_logs WHERE user_id = ? AND event_type = 'onboarding_cards_completed'",
            String.class, userId);
    }

    private long cardRows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM credit_cards WHERE user_id = ?", Long.class, userId);
    }

    @Test
    void nubankRoxinhoWithR$5000LimitDueOnThe10thThenNextReachesStepFour() {
        // Acceptance criterion: "Nubank Roxinho", limit R$ 5.000,00, due day 10, then Próximo
        atStepThree();
        creditCardService.create(userId,
            new CreateCreditCardRequest("Nubank Roxinho", "Nubank", new BigDecimal("5000.00"), new BigDecimal("10")));
        creditCardRepository.flush();

        onboardingService.completeCards(userId, false, "127.0.0.1");
        onboardingProgressRepository.flush();

        Map<String, Object> card = jdbc.queryForMap(
            "SELECT card_name, bank_name, card_limit, due_day, deleted_at FROM credit_cards WHERE user_id = ?", userId);
        assertThat(card).containsEntry("card_name", "Nubank Roxinho").containsEntry("bank_name", "Nubank")
            .containsEntry("card_limit", new BigDecimal("5000.00"))
            .containsEntry("due_day", 10)
            .containsEntry("deleted_at", null);
        assertThat(progressRow()).containsEntry("current_step", 4)
            .containsEntry("steps_completed", "[\"tutorial_viewed\",\"accounts_added\",\"cards_added\"]");
        assertThat(cardsAudits()).containsExactly("{\"skipped\":false,\"cards\":1}");
    }

    @Test
    void nextWithoutCardsRecordsSkippedAndCreatesNoCard() {
        atStepThree();

        onboardingService.completeCards(userId, false, "127.0.0.1");
        onboardingProgressRepository.flush();

        assertThat(cardRows()).isZero();
        assertThat(progressRow()).containsEntry("current_step", 4)
            .containsEntry("steps_completed", "[\"tutorial_viewed\",\"accounts_added\",\"cards_skipped\"]");
        assertThat(cardsAudits()).containsExactly("{\"skipped\":false,\"cards\":0}");
    }

    @Test
    void nextWithOnlyRemovedCardsRecordsSkipped() {
        atStepThree();
        CreditCard card = add(userId, "Antigo", "100.00", 5);
        creditCardService.delete(userId, card.getId());
        creditCardRepository.flush();

        onboardingService.completeCards(userId, false, "127.0.0.1");
        onboardingProgressRepository.flush();

        assertThat(progressRow()).containsEntry("steps_completed", "[\"tutorial_viewed\",\"accounts_added\",\"cards_skipped\"]");
        assertThat(cardsAudits()).containsExactly("{\"skipped\":false,\"cards\":0}");
    }

    @Test
    void skippingCreatesNoCardAndRepeatingChangesNothing() {
        atStepThree();

        onboardingService.completeCards(userId, true, "127.0.0.1");
        onboardingProgressRepository.flush();
        onboardingService.completeCards(userId, false, "127.0.0.1");
        onboardingService.completeCards(userId, true, "127.0.0.1");
        onboardingProgressRepository.flush();

        assertThat(cardRows()).isZero();
        assertThat(progressRow()).containsEntry("current_step", 4)
            .containsEntry("steps_completed", "[\"tutorial_viewed\",\"accounts_added\",\"cards_skipped\"]");
        assertThat(cardsAudits()).containsExactly("{\"skipped\":true,\"cards\":0}");
    }

    @Test
    void cardsStepIsAConflictOnStepsOneAndTwo() {
        onboardingService.getOrCreate(userId);
        assertThatThrownBy(() -> onboardingService.completeCards(userId, true, "127.0.0.1"))
            .isInstanceOf(ConflictException.class);

        atStepTwo();
        assertThatThrownBy(() -> onboardingService.completeCards(userId, false, "127.0.0.1"))
            .isInstanceOf(ConflictException.class);
        assertThat(progressRow()).containsEntry("current_step", 2);
        assertThat(cardsAudits()).isEmpty();
    }

    @Test
    void listIsScopedByUserOldestFirstAndHidesRemovedCards() {
        CreditCard first = add(userId, "Nubank", "5000.00", 10);
        CreditCard second = add(userId, "Inter", "0", 31);
        CreditCard removed = add(userId, "Antigo", "1.00", 1);
        add(newUser(), "De outra pessoa", "99.99", 15);

        creditCardService.delete(userId, removed.getId());
        creditCardRepository.flush();

        assertThat(creditCardService.listActive(userId)).extracting(CreditCard::getId)
            .containsExactly(first.getId(), second.getId());
        assertThat(creditCardService.countActive(userId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM credit_cards WHERE id = ?", Boolean.class, removed.getId()))
            .isTrue();
    }

    @Test
    void removingAnotherUsersMissingOrAlreadyRemovedCardIsNotFound() {
        CreditCard foreign = add(newUser(), "De outra pessoa", "1.00", 10);
        CreditCard mine = add(userId, "Meu", "1.00", 10);
        creditCardService.delete(userId, mine.getId());

        assertThatThrownBy(() -> creditCardService.delete(userId, foreign.getId())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> creditCardService.delete(userId, mine.getId())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> creditCardService.delete(userId, UUID.randomUUID())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void dueDayOutsideTheCheckIsRejectedByTheDatabase() {
        assertThatThrownBy(() -> jdbc.update(
            "INSERT INTO credit_cards (user_id, card_name, card_limit, due_day) VALUES (?, 'X', 0, 32)", userId))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void dueDayZeroIsRejectedByTheDatabase() {
        assertThatThrownBy(() -> jdbc.update(
            "INSERT INTO credit_cards (user_id, card_name, card_limit, due_day) VALUES (?, 'X', 0, 0)", userId))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void purgeDeletesAllOfTheUsersCardsIncludingRemovedOnesAndThenTheUser() {
        // Purge SQL: removed cards go too, other users keep theirs (the call order is in AccountDeletionServiceTest)
        CreditCard removed = add(userId, "Removido", "1.00", 10);
        add(userId, "Ativo", "1.00", 10);
        creditCardService.delete(userId, removed.getId());
        creditCardRepository.flush();
        UUID other = newUser();
        add(other, "De outra pessoa", "1.00", 10);

        assertThat(creditCardRepository.deleteByUserId(userId)).isEqualTo(2);
        assertThat(userRepository.deleteByIdNative(userId)).isEqualTo(1);
        assertThat(creditCardService.countActive(other)).isEqualTo(1);
    }

    @Test
    void foreignKeyBlocksDeletingTheUserWhileCardsExist() {
        add(userId, "Nubank", "1.00", 10);

        assertThatThrownBy(() -> userRepository.deleteByIdNative(userId))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void exportIncludesActiveAndRemovedCards() {
        add(userId, "Nubank Roxinho", "5000.00", 10);
        CreditCard removed = add(userId, "Antigo", "0", 31);
        creditCardService.delete(userId, removed.getId());
        creditCardRepository.flush();

        @SuppressWarnings("unchecked")
        var rows = (List<Map<String, Object>>) dataExportService.collectUserData(userId).get("credit_cards");

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("card_name", "Nubank Roxinho").containsEntry("due_day", 10)
            .containsEntry("deleted_at", null);
        assertThat((BigDecimal) rows.get(0).get("card_limit")).isEqualByComparingTo("5000.00");
        assertThat(rows.get(1)).containsEntry("card_name", "Antigo");
        assertThat(rows.get(1).get("deleted_at")).isNotNull();
    }
}
