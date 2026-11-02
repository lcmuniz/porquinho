package com.porquinho.service;

import com.porquinho.entity.OnboardingProgress;
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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Story 3.1: onboarding_progress against the real schema (PostgreSQL via Testcontainers, Flyway V15):
 * row created on first read, tutorial completion with audit, idempotent repeat, UNIQUE(user_id),
 * the native purge delete before the user row, and the export.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OnboardingServiceIntegrationTest {

    @Autowired
    private OnboardingService onboardingService;

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
        userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, auth_provider) VALUES (?, ?, 'EMAIL')", userId, userId + "@example.com");
    }

    private Map<String, Object> row() {
        return jdbc.queryForMap("SELECT current_step, steps_completed, is_completed FROM onboarding_progress WHERE user_id = ?", userId);
    }

    private int tutorialAudits() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE user_id = ? AND event_type = 'onboarding_tutorial_completed'",
            Integer.class, userId);
    }

    @Test
    void firstReadCreatesRowAtStepOne() {
        OnboardingProgress progress = onboardingService.getOrCreate(userId);

        assertThat(progress.getId()).isNotNull();
        assertThat(progress.getCreatedAt()).isNotNull();
        assertThat(row()).containsEntry("current_step", 1).containsEntry("steps_completed", "[]")
            .containsEntry("is_completed", false);

        // second read returns the same row
        assertThat(onboardingService.getOrCreate(userId).getId()).isEqualTo(progress.getId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM onboarding_progress WHERE user_id = ?", Integer.class, userId))
            .isEqualTo(1);
    }

    @Test
    void completingTheTutorialAdvancesAndAuditsOnce() {
        onboardingService.getOrCreate(userId);

        onboardingService.completeTutorial(userId, false, "127.0.0.1");
        onboardingProgressRepository.flush();

        assertThat(row()).containsEntry("current_step", 2).containsEntry("steps_completed", "[\"tutorial_viewed\"]");
        assertThat(tutorialAudits()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT metadata FROM audit_logs WHERE user_id = ? AND event_type = 'onboarding_tutorial_completed'",
            String.class, userId)).isEqualTo("{\"skipped\":false}");

        // repeat (even as "skipped"): no change, no new audit
        OnboardingProgress again = onboardingService.completeTutorial(userId, true, "127.0.0.1");
        onboardingProgressRepository.flush();

        assertThat(again.getCurrentStep()).isEqualTo(2);
        assertThat(row()).containsEntry("steps_completed", "[\"tutorial_viewed\"]");
        assertThat(tutorialAudits()).isEqualTo(1);
    }

    @Test
    void skippingRecordsTutorialSkipped() {
        onboardingService.completeTutorial(userId, true, "127.0.0.1");
        onboardingProgressRepository.flush();

        assertThat(row()).containsEntry("current_step", 2).containsEntry("steps_completed", "[\"tutorial_skipped\"]");
    }

    @Test
    void onlyOneRowPerUser() {
        onboardingProgressRepository.saveAndFlush(new OnboardingProgress(userId));

        assertThatThrownBy(() -> onboardingProgressRepository.saveAndFlush(new OnboardingProgress(userId)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void foreignKeyBlocksDeletingTheUserWhileProgressExists() {
        // Why the purge deletes onboarding_progress before the user (the order itself is in AccountDeletionServiceTest)
        onboardingService.getOrCreate(userId);

        assertThatThrownBy(() -> userRepository.deleteByIdNative(userId))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void purgeOrderDeletesOnlyThatUsersProgress() {
        onboardingService.getOrCreate(userId);
        UUID other = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, auth_provider) VALUES (?, ?, 'EMAIL')", other, other + "@example.com");
        onboardingService.getOrCreate(other);

        assertThat(onboardingProgressRepository.deleteByUserId(userId)).isEqualTo(1);
        assertThat(userRepository.deleteByIdNative(userId)).isEqualTo(1);
        assertThat(onboardingProgressRepository.findByUserId(other)).isPresent();
    }

    @Test
    void exportIncludesTheProgressRow() {
        onboardingService.completeTutorial(userId, false, "127.0.0.1");
        onboardingProgressRepository.flush();

        @SuppressWarnings("unchecked")
        var rows = (java.util.List<Map<String, Object>>) dataExportService.collectUserData(userId).get("onboarding_progress");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("current_step", 2).containsEntry("is_completed", false)
            .containsEntry("steps_completed", java.util.List.of("tutorial_viewed"));
    }
}
