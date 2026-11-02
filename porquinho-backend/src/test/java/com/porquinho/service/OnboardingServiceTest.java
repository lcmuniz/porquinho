package com.porquinho.service;

import com.porquinho.entity.OnboardingProgress;
import com.porquinho.repository.OnboardingProgressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OnboardingServiceTest {

    @Mock
    private OnboardingProgressRepository repository;
    @Mock
    private AuditLogService auditLogService;

    private OnboardingService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        OnboardingService temp = new OnboardingService(repository, auditLogService, null);
        service = new OnboardingService(repository, auditLogService, temp);
        // self == service: in production it is the Spring proxy of this same bean
        try {
            java.lang.reflect.Field selfField = OnboardingService.class.getDeclaredField("self");
            selfField.setAccessible(true);
            selfField.set(service, service);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private OnboardingProgress progressAt(int step, String... steps) {
        OnboardingProgress p = new OnboardingProgress(userId);
        p.setId(UUID.randomUUID());
        p.setCurrentStep(step);
        for (String s : steps) {
            p.addStep(s);
        }
        return p;
    }

    @Test
    void firstAccessCreatesRowAtStepOne() {
        when(repository.findByUserId(userId)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(OnboardingProgress.class))).thenAnswer(inv -> inv.getArgument(0));

        OnboardingProgress progress = service.getOrCreate(userId);

        ArgumentCaptor<OnboardingProgress> saved = ArgumentCaptor.forClass(OnboardingProgress.class);
        verify(repository).saveAndFlush(saved.capture());
        assertEquals(userId, saved.getValue().getUserId());
        assertEquals(1, progress.getCurrentStep());
        assertFalse(progress.isCompleted());
        assertEquals(List.of(), progress.getSteps());
        assertEquals("[]", progress.getStepsCompleted());
    }

    @Test
    void existingRowIsReturnedWithoutInsert() {
        OnboardingProgress existing = progressAt(2, OnboardingService.STEP_TUTORIAL_VIEWED);
        when(repository.findByUserId(userId)).thenReturn(Optional.of(existing));

        assertSame(existing, service.getOrCreate(userId));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void concurrentFirstAccessRereadsTheWinnersRow() {
        OnboardingProgress winner = progressAt(1);
        when(repository.findByUserId(userId)).thenReturn(Optional.empty(), Optional.of(winner));
        when(repository.saveAndFlush(any(OnboardingProgress.class)))
            .thenThrow(new DataIntegrityViolationException("onboarding_progress_user_id_unique"));

        assertSame(winner, service.getOrCreate(userId));
    }

    @Test
    void completingTheTutorialRecordsViewedMovesToStepTwoAndAudits() {
        OnboardingProgress progress = progressAt(1);
        when(repository.findByUserId(userId)).thenReturn(Optional.of(progress));
        when(repository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(progress));
        when(repository.save(progress)).thenReturn(progress);

        OnboardingProgress result = service.completeTutorial(userId, false, "10.0.0.1");

        assertEquals(2, result.getCurrentStep());
        assertEquals(List.of("tutorial_viewed"), result.getSteps());
        assertFalse(result.isCompleted());
        verify(auditLogService).log("onboarding_tutorial_completed", userId, "10.0.0.1", "{\"skipped\":false}");
    }

    @Test
    void skippingTheTutorialRecordsSkippedMovesToStepTwoAndAudits() {
        OnboardingProgress progress = progressAt(1);
        when(repository.findByUserId(userId)).thenReturn(Optional.of(progress));
        when(repository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(progress));
        when(repository.save(progress)).thenReturn(progress);

        OnboardingProgress result = service.completeTutorial(userId, true, "10.0.0.1");

        assertEquals(2, result.getCurrentStep());
        assertEquals(List.of("tutorial_skipped"), result.getSteps());
        verify(auditLogService).log("onboarding_tutorial_completed", userId, "10.0.0.1", "{\"skipped\":true}");
    }

    @Test
    void repeatingThePostChangesNothingAndDoesNotAuditAgain() {
        OnboardingProgress progress = progressAt(2, OnboardingService.STEP_TUTORIAL_VIEWED);
        when(repository.findByUserId(userId)).thenReturn(Optional.of(progress));
        when(repository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(progress));

        OnboardingProgress result = service.completeTutorial(userId, true, "10.0.0.1");

        assertEquals(2, result.getCurrentStep());
        assertEquals(List.of("tutorial_viewed"), result.getSteps());
        verify(repository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void laterStepIsNeverMovedBack() {
        OnboardingProgress progress = progressAt(5, OnboardingService.STEP_TUTORIAL_VIEWED);
        when(repository.findByUserId(userId)).thenReturn(Optional.of(progress));
        when(repository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(progress));

        assertEquals(5, service.completeTutorial(userId, false, "10.0.0.1").getCurrentStep());
        verify(repository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void completedOnboardingIsNotChanged() {
        OnboardingProgress progress = progressAt(1);
        progress.setCompleted(true);
        when(repository.findByUserId(userId)).thenReturn(Optional.of(progress));
        when(repository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(progress));

        OnboardingProgress result = service.completeTutorial(userId, false, "10.0.0.1");

        assertEquals(1, result.getCurrentStep());
        assertEquals(List.of(), result.getSteps());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void completingWithoutARowCreatesItFirst() {
        // POST before any GET: the row is created at step 1, then advanced
        OnboardingProgress created = progressAt(1);
        when(repository.findByUserId(userId)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(OnboardingProgress.class))).thenReturn(created);
        when(repository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(created));
        when(repository.save(created)).thenReturn(created);

        assertEquals(2, service.completeTutorial(userId, false, "10.0.0.1").getCurrentStep());
    }

    @Test
    void addStepDoesNotDuplicate() {
        OnboardingProgress progress = progressAt(1, "tutorial_viewed");
        progress.addStep("tutorial_viewed");

        assertEquals("[\"tutorial_viewed\"]", progress.getStepsCompleted());
    }
}
