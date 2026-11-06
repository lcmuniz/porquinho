package com.porquinho.service;

import com.porquinho.dto.CreateAccountRequest;
import com.porquinho.exception.ConflictException;
import com.porquinho.repository.AccountRepository;
import com.porquinho.repository.CategoryRepository;
import com.porquinho.repository.OnboardingProgressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
 * Story 3.7: onboarding step 7 (Dashboard GPS tour) against the real schema (PostgreSQL via Testcontainers):
 * finishing the tour advances to step 8 with dashboard_gps_explained and the audit, repeating changes nothing,
 * and steps 1 and 6 are a conflict, each with its own user (2 to 5 are covered by the unit test).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OnboardingDashboardTourIntegrationTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private OnboardingService onboardingService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private OnboardingProgressRepository onboardingProgressRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, auth_provider) VALUES (?, ?, 'EMAIL')", userId,
            userId + "@example.com");
    }

    private void atStepSix() {
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
        onboardingProgressRepository.flush();
    }

    private void atStepSeven() {
        atStepSix();
        onboardingService.completeImport(userId, null, "127.0.0.1");
        onboardingProgressRepository.flush();
    }

    private Map<String, Object> progressRow() {
        return jdbc.queryForMap("SELECT current_step, steps_completed, is_completed FROM onboarding_progress "
            + "WHERE user_id = ?", userId);
    }

    private long tourAudits() {
        return jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_logs WHERE user_id = ? AND event_type = 'onboarding_dashboard_tour_completed'",
            Long.class, userId);
    }

    @Test
    void acceptanceFinishingTheTourRecordsDashboardGpsExplainedMovesToStepEightAndAudits() {
        atStepSeven();

        onboardingService.completeDashboardTour(userId, "127.0.0.1");
        onboardingProgressRepository.flush();

        assertThat(progressRow()).containsEntry("current_step", 8).containsEntry("is_completed", false)
            .containsEntry("steps_completed",
                "[\"tutorial_viewed\",\"accounts_added\",\"cards_skipped\",\"categories_suggested\","
                    + "\"categories_customized\",\"import_skipped\",\"dashboard_gps_explained\"]");
        assertThat(tourAudits()).isEqualTo(1);
    }

    @Test
    void repeatingTheStepSevenPostChangesNothingAndWritesNoAudit() {
        atStepSeven();
        onboardingService.completeDashboardTour(userId, "127.0.0.1");
        onboardingProgressRepository.flush();
        Object stepsBefore = progressRow().get("steps_completed");

        onboardingService.completeDashboardTour(userId, "127.0.0.1");
        onboardingProgressRepository.flush();

        assertThat(progressRow()).containsEntry("current_step", 8).containsEntry("steps_completed", stepsBefore);
        assertThat(tourAudits()).isEqualTo(1);
    }

    @Test
    void stepSevenPostIsAConflictOnStepOne() {
        onboardingService.getOrCreate(userId);
        onboardingProgressRepository.flush();

        assertThatThrownBy(() -> onboardingService.completeDashboardTour(userId, "127.0.0.1"))
            .isInstanceOf(ConflictException.class);

        assertThat(progressRow()).containsEntry("current_step", 1).containsEntry("steps_completed", "[]");
        assertThat(tourAudits()).isZero();
    }

    @Test
    void stepSevenPostIsAConflictOnStepSix() {
        atStepSix();

        assertThatThrownBy(() -> onboardingService.completeDashboardTour(userId, "127.0.0.1"))
            .isInstanceOf(ConflictException.class);

        assertThat(progressRow()).containsEntry("current_step", 6);
        assertThat((String) progressRow().get("steps_completed")).doesNotContain("dashboard_gps_explained");
        assertThat(tourAudits()).isZero();
    }
}
