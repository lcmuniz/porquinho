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
 * Story 3.6: onboarding step 6 (first import, still without upload) against the real schema (PostgreSQL via
 * Testcontainers): "Importar depois" advances to step 7 with import_skipped and the audit, repeating changes
 * nothing, and steps 1 and 5 are a conflict (2 to 4 are covered by the unit test).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OnboardingImportIntegrationTest {

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

    private void atStepFive() {
        onboardingService.completeTutorial(userId, false, "127.0.0.1");
        onboardingProgressRepository.flush();
        accountService.create(userId, new CreateAccountRequest("Nubank", null, "CHECKING", BigDecimal.ONE));
        accountRepository.flush();
        onboardingService.completeAccounts(userId, false, "127.0.0.1");
        onboardingService.completeCards(userId, true, "127.0.0.1");
        onboardingService.completeCategories(userId, List.of("housing", "leisure"), "127.0.0.1");
        onboardingProgressRepository.flush();
        categoryRepository.flush();
    }

    private void atStepSix() {
        atStepFive();
        onboardingService.completeCustomization(userId, false, "127.0.0.1");
        onboardingProgressRepository.flush();
    }

    private Map<String, Object> progressRow() {
        return jdbc.queryForMap("SELECT current_step, steps_completed, is_completed FROM onboarding_progress "
            + "WHERE user_id = ?", userId);
    }

    private List<String> importAudits() {
        return jdbc.queryForList(
            "SELECT metadata FROM audit_logs WHERE user_id = ? AND event_type = 'onboarding_import_completed'",
            String.class, userId);
    }

    @Test
    void acceptanceImportLaterRecordsImportSkippedMovesToStepSevenAndAudits() {
        atStepSix();

        onboardingService.completeImport(userId, "127.0.0.1");
        onboardingProgressRepository.flush();

        assertThat(progressRow()).containsEntry("current_step", 7).containsEntry("is_completed", false)
            .containsEntry("steps_completed",
                "[\"tutorial_viewed\",\"accounts_added\",\"cards_skipped\",\"categories_suggested\","
                    + "\"categories_customized\",\"import_skipped\"]");
        assertThat((String) progressRow().get("steps_completed")).doesNotContain("first_import_completed");
        assertThat(importAudits()).containsExactly("{\"skipped\":true}");
    }

    @Test
    void repeatingTheStepSixPostChangesNothingAndWritesNoAudit() {
        atStepSix();
        onboardingService.completeImport(userId, "127.0.0.1");
        onboardingProgressRepository.flush();
        Object stepsBefore = progressRow().get("steps_completed");

        onboardingService.completeImport(userId, "127.0.0.1");
        onboardingProgressRepository.flush();

        assertThat(progressRow()).containsEntry("current_step", 7).containsEntry("steps_completed", stepsBefore);
        assertThat(importAudits()).hasSize(1);
    }

    @Test
    void stepSixPostIsAConflictOnStepsOneAndFive() {
        onboardingService.getOrCreate(userId);
        assertThatThrownBy(() -> onboardingService.completeImport(userId, "127.0.0.1"))
            .isInstanceOf(ConflictException.class);

        atStepFive();
        assertThatThrownBy(() -> onboardingService.completeImport(userId, "127.0.0.1"))
            .isInstanceOf(ConflictException.class);

        assertThat(progressRow()).containsEntry("current_step", 5);
        assertThat(importAudits()).isEmpty();
    }
}
