package com.porquinho.service;

import com.porquinho.dto.CreateAccountRequest;
import com.porquinho.dto.CreateGoalRequest;
import com.porquinho.entity.Goal;
import com.porquinho.exception.ConflictException;
import com.porquinho.repository.AccountRepository;
import com.porquinho.repository.CategoryRepository;
import com.porquinho.repository.GoalRepository;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Story 3.8: goals and onboarding step 8 against the real schema (PostgreSQL via Testcontainers): creating the
 * emergency fund and finishing, skipping, the 409s, idempotency, scoping by user, the CHECKs and the LGPD purge
 * and export.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GoalOnboardingIntegrationTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private OnboardingService onboardingService;

    @Autowired
    private GoalService goalService;

    @Autowired
    private GoalRepository goalRepository;

    @Autowired
    private CategoryRepository categoryRepository;

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

    private void atStepSeven() {
        onboardingService.completeTutorial(userId, false, "127.0.0.1");
        onboardingProgressRepository.flush();
        accountService.create(userId, new CreateAccountRequest("Nubank", null, "CHECKING", BigDecimal.ONE));
        accountRepository.flush();
        onboardingService.completeAccounts(userId, false, "127.0.0.1");
        onboardingService.completeCards(userId, true, "127.0.0.1");
        onboardingService.completeCategories(userId, List.of("housing", "leisure"), "127.0.0.1");
        onboardingProgressRepository.flush();
        categoryRepository.flush();
        onboardingService.completeCustomization(userId, false, "127.0.0.1");
        onboardingService.completeImport(userId, "127.0.0.1");
        onboardingProgressRepository.flush();
    }

    private void atStepEight() {
        atStepSeven();
        onboardingService.completeDashboardTour(userId, "127.0.0.1");
        onboardingProgressRepository.flush();
    }

    private Goal addGoal(UUID owner, String name) {
        Goal goal = goalService.create(owner, new CreateGoalRequest(name, new BigDecimal("25000"),
            LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusYears(1), new BigDecimal("2083.34")));
        goalRepository.flush();
        return goal;
    }

    private Map<String, Object> progressRow() {
        return jdbc.queryForMap("SELECT current_step, steps_completed, is_completed FROM onboarding_progress "
            + "WHERE user_id = ?", userId);
    }

    private List<Map<String, Object>> completionAudits() {
        return jdbc.queryForList(
            "SELECT metadata FROM audit_logs WHERE user_id = ? AND event_type = 'onboarding_completed'", userId);
    }

    private long goalCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM goals WHERE user_id = ?", Long.class, userId);
    }

    @Test
    void acceptanceCreatingTheGoalAndFinishingCompletesTheOnboarding() {
        atStepEight();

        addGoal(userId, "Reserva de Emergência");
        onboardingService.finish(userId, false, "127.0.0.1");
        onboardingProgressRepository.flush();

        Map<String, Object> goal = jdbc.queryForMap("SELECT goal_name, target_amount, current_amount, target_date, "
            + "monthly_allocation, is_completed, deleted_at FROM goals WHERE user_id = ?", userId);
        assertThat(goal).containsEntry("goal_name", "Reserva de Emergência").containsEntry("is_completed", false)
            .containsEntry("deleted_at", null);
        assertThat((BigDecimal) goal.get("current_amount")).isEqualByComparingTo("0");
        assertThat((BigDecimal) goal.get("target_amount")).isEqualByComparingTo("25000.00");
        assertThat((BigDecimal) goal.get("monthly_allocation")).isEqualByComparingTo("2083.34");
        assertThat(goal.get("target_date")).isNotNull();

        assertThat(progressRow()).containsEntry("current_step", 8).containsEntry("is_completed", true);
        assertThat((String) progressRow().get("steps_completed")).endsWith("\"dashboard_gps_explained\",\"emergency_fund_created\"]");
        assertThat(completionAudits()).hasSize(1);
        assertThat(completionAudits().get(0)).containsEntry("metadata", "{\"skipped\":false,\"goals\":1}");
    }

    @Test
    void skippingCompletesTheOnboardingWithoutCreatingAGoal() {
        atStepEight();

        onboardingService.finish(userId, true, "127.0.0.1");
        onboardingProgressRepository.flush();

        assertThat(goalCount()).isZero();
        assertThat(progressRow()).containsEntry("current_step", 8).containsEntry("is_completed", true);
        assertThat((String) progressRow().get("steps_completed")).endsWith("\"emergency_fund_skipped\"]");
        assertThat(completionAudits()).hasSize(1);
        assertThat(completionAudits().get(0)).containsEntry("metadata", "{\"skipped\":true,\"goals\":0}");
    }

    @Test
    void skippingWithAnExistingGoalRecordsItAsCreated() {
        atStepEight();
        addGoal(userId, "Reserva de Emergência");

        onboardingService.finish(userId, true, "127.0.0.1");
        onboardingProgressRepository.flush();

        assertThat((String) progressRow().get("steps_completed")).endsWith("\"emergency_fund_created\"]");
        assertThat(completionAudits().get(0)).containsEntry("metadata", "{\"skipped\":false,\"goals\":1}");
    }

    @Test
    void finishingWithoutAGoalIsAConflictAndChangesNothing() {
        atStepEight();

        assertThatThrownBy(() -> onboardingService.finish(userId, false, "127.0.0.1"))
            .isInstanceOf(ConflictException.class);

        assertThat(progressRow()).containsEntry("current_step", 8).containsEntry("is_completed", false);
        assertThat(completionAudits()).isEmpty();
    }

    @Test
    void anotherUsersGoalDoesNotCountForFinishing() {
        atStepEight();
        addGoal(newUser(), "De outra pessoa");

        assertThatThrownBy(() -> onboardingService.finish(userId, false, "127.0.0.1"))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void repeatingFinishChangesNothingAndWritesNoAudit() {
        atStepEight();
        addGoal(userId, "Reserva de Emergência");
        onboardingService.finish(userId, false, "127.0.0.1");
        onboardingProgressRepository.flush();
        Object stepsBefore = progressRow().get("steps_completed");

        onboardingService.finish(userId, true, "127.0.0.1");
        onboardingService.finish(userId, false, "127.0.0.1");
        onboardingService.completeDashboardTour(userId, "127.0.0.1");
        onboardingProgressRepository.flush();

        assertThat(progressRow()).containsEntry("current_step", 8).containsEntry("is_completed", true)
            .containsEntry("steps_completed", stepsBefore);
        assertThat(completionAudits()).hasSize(1);
        assertThat(goalCount()).isEqualTo(1);
    }

    @Test
    void finishIsAConflictOnStepSeven() {
        atStepSeven();

        assertThatThrownBy(() -> onboardingService.finish(userId, true, "127.0.0.1"))
            .isInstanceOf(ConflictException.class);

        assertThat(progressRow()).containsEntry("current_step", 7).containsEntry("is_completed", false);
        assertThat(completionAudits()).isEmpty();
    }

    @Test
    void finishIsAConflictOnStepOne() {
        onboardingService.getOrCreate(userId);
        onboardingProgressRepository.flush();

        assertThatThrownBy(() -> onboardingService.finish(userId, true, "127.0.0.1"))
            .isInstanceOf(ConflictException.class);

        assertThat(progressRow()).containsEntry("current_step", 1).containsEntry("is_completed", false);
    }

    @Test
    void listReturnsOnlyTheUsersActiveGoalsOldestFirst() {
        addGoal(userId, "Primeira");
        addGoal(newUser(), "De outra pessoa");
        addGoal(userId, "Segunda");
        Goal removed = addGoal(userId, "Removida");
        removed.setDeletedAt(java.time.OffsetDateTime.now());
        goalRepository.saveAndFlush(removed);

        assertThat(goalService.listActive(userId)).extracting(Goal::getGoalName).containsExactly("Primeira", "Segunda");
        assertThat(goalService.countActive(userId)).isEqualTo(2);
    }

    @Test
    void targetAmountNotAboveZeroIsRejectedByTheDatabase() {
        assertThatThrownBy(() -> jdbc.update(
            "INSERT INTO goals (user_id, goal_name, target_amount) VALUES (?, 'X', 0)", userId))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void negativeMonthlyAllocationIsRejectedByTheDatabase() {
        assertThatThrownBy(() -> jdbc.update(
            "INSERT INTO goals (user_id, goal_name, target_amount, monthly_allocation) VALUES (?, 'X', 1, -1)", userId))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseDefaultsCurrentAmountToZeroAndNotCompleted() {
        jdbc.update("INSERT INTO goals (user_id, goal_name, target_amount) VALUES (?, 'X', 1)", userId);

        Map<String, Object> row = jdbc.queryForMap("SELECT current_amount, is_completed FROM goals WHERE user_id = ?", userId);
        assertThat((BigDecimal) row.get("current_amount")).isEqualByComparingTo("0");
        assertThat(row).containsEntry("is_completed", false);
    }

    @Test
    void purgeDeletesAllOfTheUsersGoalsAndThenTheUser() {
        addGoal(userId, "Reserva");
        Goal removed = addGoal(userId, "Removida");
        removed.setDeletedAt(java.time.OffsetDateTime.now());
        goalRepository.saveAndFlush(removed);
        UUID other = newUser();
        addGoal(other, "De outra pessoa");

        assertThat(goalRepository.deleteByUserId(userId)).isEqualTo(2);
        assertThat(userRepository.deleteByIdNative(userId)).isEqualTo(1);
        assertThat(goalService.countActive(other)).isEqualTo(1);
    }

    @Test
    void foreignKeyBlocksDeletingTheUserWhileGoalsExist() {
        addGoal(userId, "Reserva");

        assertThatThrownBy(() -> userRepository.deleteByIdNative(userId))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void exportIncludesTheUsersGoals() {
        addGoal(userId, "Reserva de Emergência");

        @SuppressWarnings("unchecked")
        var rows = (List<Map<String, Object>>) dataExportService.collectUserData(userId).get("goals");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("goal_name", "Reserva de Emergência").containsEntry("deleted_at", null)
            .containsEntry("is_completed", false);
        assertThat((BigDecimal) rows.get(0).get("current_amount")).isEqualByComparingTo("0");
    }
}
