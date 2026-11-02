package com.porquinho.service;

import com.porquinho.entity.OnboardingProgress;
import com.porquinho.repository.OnboardingProgressRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Story 3.1: first-access race on the real schema (PostgreSQL via Testcontainers), WITHOUT a test
 * transaction, so the losing insert really fails on UNIQUE(user_id) in its own transaction and the
 * re-read happens afterwards, as in production. Rows are committed, so cleanup is manual.
 */
@SpringBootTest
@ActiveProfiles("test")
class OnboardingServiceRaceIntegrationTest {

    @Autowired
    private OnboardingService onboardingService;

    @MockitoSpyBean
    private OnboardingProgressRepository onboardingProgressRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, auth_provider) VALUES (?, ?, 'EMAIL')", userId, userId + "@example.com");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM audit_logs WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM onboarding_progress WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }

    @Test
    void losingTheFirstAccessRaceRereadsTheCommittedRow() {
        // The "other tab" already committed the row...
        UUID winnerId = UUID.randomUUID();
        jdbc.update("INSERT INTO onboarding_progress (id, user_id) VALUES (?, ?)", winnerId, userId);
        // ...but this request's first lookup ran before that commit and saw nothing. The re-read after the
        // failed insert hits the database (findById is not stubbed; the spy delegates to the real repository,
        // and doCallRealMethod cannot be used on an interface method).
        doReturn(Optional.empty())
            .doAnswer(inv -> onboardingProgressRepository.findById(winnerId))
            .when(onboardingProgressRepository).findByUserId(userId);

        OnboardingProgress progress = onboardingService.getOrCreate(userId);

        assertThat(progress.getId()).isEqualTo(winnerId);
        assertThat(progress.getCurrentStep()).isEqualTo(1);
        verify(onboardingProgressRepository, times(2)).findByUserId(userId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM onboarding_progress WHERE user_id = ?", Integer.class, userId))
            .isEqualTo(1);
    }
}
