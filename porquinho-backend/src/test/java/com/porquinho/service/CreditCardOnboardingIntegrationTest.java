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
        creditCardService.delete(userId, card.getId(), "127.0.0.1");
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

        creditCardService.delete(userId, removed.getId(), "127.0.0.1");
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
        creditCardService.delete(userId, mine.getId(), "127.0.0.1");

        assertThatThrownBy(() -> creditCardService.delete(userId, foreign.getId(), "127.0.0.1")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> creditCardService.delete(userId, mine.getId(), "127.0.0.1")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> creditCardService.delete(userId, UUID.randomUUID(), "127.0.0.1")).isInstanceOf(ResourceNotFoundException.class);
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
        creditCardService.delete(userId, removed.getId(), "127.0.0.1");
        creditCardRepository.flush();
        UUID other = newUser();
        add(other, "De outra pessoa", "1.00", 10);

        assertThat(creditCardRepository.deleteByUserId(userId)).isEqualTo(2);
        // Story 4.4: removing writes an audit; the real purge hard-deletes audit_logs before the user
        jdbc.update("DELETE FROM audit_logs WHERE user_id = ?", userId);
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
        creditCardService.delete(userId, removed.getId(), "127.0.0.1");
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

    // Story 4.3: editing

    @Test
    void editingDueDay10To20AndLimitTo6000UpdatesTheRowAndUpdatedAt() throws Exception {
        // Acceptance criterion: card due on day 10 edited to day 20 with a R$ 6.000,00 limit
        CreditCard card = add(userId, "Nubank", "5000.00", 10);
        java.time.OffsetDateTime createdUpdatedAt = jdbc.queryForObject(
            "SELECT updated_at FROM credit_cards WHERE id = ?", java.time.OffsetDateTime.class, card.getId());
        Thread.sleep(5);

        creditCardService.update(userId, card.getId(),
            new CreateCreditCardRequest("Nubank Ultravioleta", "Nubank", new BigDecimal("6000.00"), new BigDecimal("20")));
        creditCardRepository.flush();

        Map<String, Object> row = jdbc.queryForMap(
            "SELECT card_name, bank_name, card_limit, due_day, deleted_at FROM credit_cards WHERE id = ?", card.getId());
        assertThat(row).containsEntry("card_name", "Nubank Ultravioleta").containsEntry("bank_name", "Nubank")
            .containsEntry("card_limit", new BigDecimal("6000.00")).containsEntry("due_day", 20)
            .containsEntry("deleted_at", null);
        assertThat(jdbc.queryForObject("SELECT updated_at > ? FROM credit_cards WHERE id = ?", Boolean.class,
            createdUpdatedAt, card.getId())).isTrue();
    }

    @Test
    void editingAnotherUsersARemovedOrAMissingCardIsNotFoundAndChangesNothing() {
        CreditCard foreign = add(newUser(), "De outra pessoa", "1.00", 5);
        CreditCard removed = add(userId, "Removido", "1.00", 5);
        creditCardService.delete(userId, removed.getId(), "127.0.0.1");
        creditCardRepository.flush();
        CreateCreditCardRequest request =
            new CreateCreditCardRequest("Meu", null, new BigDecimal("999.00"), new BigDecimal("15"));

        assertThatThrownBy(() -> creditCardService.update(userId, foreign.getId(), request))
            .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> creditCardService.update(userId, removed.getId(), request))
            .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> creditCardService.update(userId, UUID.randomUUID(), request))
            .isInstanceOf(ResourceNotFoundException.class);
        creditCardRepository.flush();

        assertThat(jdbc.queryForMap("SELECT card_name, due_day FROM credit_cards WHERE id = ?", foreign.getId()))
            .containsEntry("card_name", "De outra pessoa").containsEntry("due_day", 5);
    }

    // Story 4.4: remove and restore

    private Map<String, Object> deletionRow(UUID cardId) {
        return jdbc.queryForMap("SELECT deleted_at, deleted_by FROM credit_cards WHERE id = ?", cardId);
    }

    private List<String> audits(String eventType) {
        return jdbc.queryForList("SELECT metadata FROM audit_logs WHERE user_id = ? AND event_type = ?",
            String.class, userId, eventType);
    }

    @Test
    void removingTheOnlyCardFillsDeletedByAuditsAndRestoreClearsIt() {
        CreditCard card = add(userId, "Nubank", "5000.00", 10);

        creditCardService.delete(userId, card.getId(), "127.0.0.1");
        creditCardRepository.flush();

        assertThat(creditCardService.listActive(userId)).isEmpty();
        assertThat(deletionRow(card.getId())).containsEntry("deleted_by", userId);
        assertThat(deletionRow(card.getId()).get("deleted_at")).isNotNull();
        assertThat(audits("card_deleted")).singleElement().satisfies(m -> assertThat(m).contains(card.getId().toString()));
        assertThat(creditCardService.listDeleted(userId)).extracting(CreditCard::getCardName).containsExactly("Nubank");

        CreditCard restored = creditCardService.restore(userId, card.getId(), "127.0.0.1");
        creditCardRepository.flush();

        assertThat(restored.getDeletedAt()).isNull();
        assertThat(deletionRow(card.getId())).containsEntry("deleted_at", null).containsEntry("deleted_by", null);
        assertThat(creditCardService.listActive(userId)).extracting(CreditCard::getCardName).containsExactly("Nubank");
        assertThat(creditCardService.listDeleted(userId)).isEmpty();
        assertThat(audits("card_restored")).singleElement().satisfies(m -> assertThat(m).contains(card.getId().toString()));
    }

    @Test
    void removedCardsAreScopedByUserMostRecentlyRemovedFirst() throws Exception {
        CreditCard first = add(userId, "Primeiro", "1.00", 1);
        CreditCard second = add(userId, "Segundo", "1.00", 2);
        CreditCard foreign = add(newUser(), "De outra pessoa", "1.00", 3);
        creditCardService.delete(userId, first.getId(), "127.0.0.1");
        creditCardRepository.flush();
        Thread.sleep(5);  // distinct deleted_at
        creditCardService.delete(userId, second.getId(), "127.0.0.1");
        creditCardService.delete(foreign.getUserId(), foreign.getId(), "127.0.0.1");
        creditCardRepository.flush();

        assertThat(creditCardService.listDeleted(userId)).extracting(CreditCard::getCardName)
            .containsExactly("Segundo", "Primeiro");
    }

    @Test
    void restoringAnActiveAnotherUsersOrAMissingCardIsNotFound() {
        CreditCard active = add(userId, "Ativo", "1.00", 1);
        CreditCard foreign = add(newUser(), "De outra pessoa", "1.00", 1);
        creditCardService.delete(foreign.getUserId(), foreign.getId(), "127.0.0.1");
        creditCardRepository.flush();

        assertThatThrownBy(() -> creditCardService.restore(userId, active.getId(), "127.0.0.1"))
            .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> creditCardService.restore(userId, foreign.getId(), "127.0.0.1"))
            .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> creditCardService.restore(userId, UUID.randomUUID(), "127.0.0.1"))
            .isInstanceOf(ResourceNotFoundException.class);
        assertThat(audits("card_restored")).isEmpty();
    }

    @Test
    void exportIncludesDeletedBy() {
        add(userId, "Nubank", "1.00", 1);
        CreditCard removed = add(userId, "Antigo", "1.00", 1);
        creditCardService.delete(userId, removed.getId(), "127.0.0.1");
        creditCardRepository.flush();

        @SuppressWarnings("unchecked")
        var rows = (List<Map<String, Object>>) dataExportService.collectUserData(userId).get("credit_cards");

        assertThat(rows.get(0)).containsEntry("deleted_by", null);
        assertThat(rows.get(1)).containsEntry("deleted_by", userId.toString());
    }
}
