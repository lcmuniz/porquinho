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
import com.porquinho.exception.ConflictException;

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
    @Mock
    private AccountService accountService;
    @Mock
    private CreditCardService creditCardService;
    @Mock
    private CategoryService categoryService;

    private OnboardingService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        OnboardingService temp = new OnboardingService(repository, auditLogService, accountService, creditCardService, categoryService, null);
        service = new OnboardingService(repository, auditLogService, accountService, creditCardService, categoryService, temp);
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

    // ---- Story 3.2: step 2 (bank accounts) ----

    private OnboardingProgress atAccountsStep() {
        OnboardingProgress progress = progressAt(2, OnboardingService.STEP_TUTORIAL_VIEWED);
        when(repository.findByUserId(userId)).thenReturn(Optional.of(progress));
        when(repository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(progress));
        return progress;
    }

    @Test
    void nextWithAccountsRecordsAddedMovesToStepThreeAndAudits() {
        OnboardingProgress progress = atAccountsStep();
        when(accountService.countActive(userId)).thenReturn(2L);
        when(repository.save(progress)).thenReturn(progress);

        OnboardingProgress result = service.completeAccounts(userId, false, "10.0.0.1");

        assertEquals(3, result.getCurrentStep());
        assertEquals(List.of("tutorial_viewed", "accounts_added"), result.getSteps());
        verify(accountService, never()).createDefault(any());
        verify(auditLogService).log("onboarding_accounts_completed", userId, "10.0.0.1",
            "{\"skipped\":false,\"accounts\":2}");
    }

    @Test
    void nextWithoutAccountsIsAConflict() {
        OnboardingProgress progress = atAccountsStep();
        when(accountService.countActive(userId)).thenReturn(0L);

        assertThrows(ConflictException.class, () -> service.completeAccounts(userId, false, "10.0.0.1"));

        assertEquals(2, progress.getCurrentStep());
        verify(accountService, never()).createDefault(any());
        verify(repository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void skippingWithoutAccountsCreatesTheDefaultAccount() {
        OnboardingProgress progress = atAccountsStep();
        when(accountService.countActive(userId)).thenReturn(0L);
        when(repository.save(progress)).thenReturn(progress);

        OnboardingProgress result = service.completeAccounts(userId, true, "10.0.0.1");

        verify(accountService).createDefault(userId);
        assertEquals(3, result.getCurrentStep());
        assertEquals(List.of("tutorial_viewed", "accounts_skipped"), result.getSteps());
        verify(auditLogService).log("onboarding_accounts_completed", userId, "10.0.0.1",
            "{\"skipped\":true,\"accounts\":1}");
    }

    @Test
    void skippingWithAccountsOnlyAdvances() {
        OnboardingProgress progress = atAccountsStep();
        when(accountService.countActive(userId)).thenReturn(1L);
        when(repository.save(progress)).thenReturn(progress);

        OnboardingProgress result = service.completeAccounts(userId, true, "10.0.0.1");

        verify(accountService, never()).createDefault(any());
        assertEquals(3, result.getCurrentStep());
        assertEquals(List.of("tutorial_viewed", "accounts_skipped"), result.getSteps());
        verify(auditLogService).log("onboarding_accounts_completed", userId, "10.0.0.1",
            "{\"skipped\":true,\"accounts\":1}");
    }

    @Test
    void accountsStepIsIdempotentFromStepThree() {
        OnboardingProgress progress = progressAt(3, OnboardingService.STEP_TUTORIAL_VIEWED, OnboardingService.STEP_ACCOUNTS_ADDED);
        when(repository.findByUserId(userId)).thenReturn(Optional.of(progress));
        when(repository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(progress));

        OnboardingProgress result = service.completeAccounts(userId, true, "10.0.0.1");

        assertEquals(3, result.getCurrentStep());
        assertEquals(List.of("tutorial_viewed", "accounts_added"), result.getSteps());
        verifyNoInteractions(accountService, auditLogService);
        verify(repository, never()).save(any());
    }

    @Test
    void accountsStepIsAConflictWhileTheTutorialIsPending() {
        OnboardingProgress progress = progressAt(1);
        when(repository.findByUserId(userId)).thenReturn(Optional.of(progress));
        when(repository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(progress));

        assertThrows(ConflictException.class, () -> service.completeAccounts(userId, true, "10.0.0.1"));

        assertEquals(1, progress.getCurrentStep());
        verifyNoInteractions(accountService, auditLogService);
    }

    // ---- Story 3.3: step 3, credit cards ----

    private OnboardingProgress atCardsStep() {
        OnboardingProgress progress = progressAt(3, OnboardingService.STEP_TUTORIAL_VIEWED, OnboardingService.STEP_ACCOUNTS_ADDED);
        when(repository.findByUserId(userId)).thenReturn(Optional.of(progress));
        when(repository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(progress));
        return progress;
    }

    @Test
    void nextWithCardsRecordsAddedMovesToStepFourAndAudits() {
        OnboardingProgress progress = atCardsStep();
        when(creditCardService.countActive(userId)).thenReturn(2L);
        when(repository.save(progress)).thenReturn(progress);

        OnboardingProgress result = service.completeCards(userId, false, "10.0.0.1");

        assertEquals(4, result.getCurrentStep());
        assertEquals(List.of("tutorial_viewed", "accounts_added", "cards_added"), result.getSteps());
        verify(auditLogService).log("onboarding_cards_completed", userId, "10.0.0.1",
            "{\"skipped\":false,\"cards\":2}");
    }

    @Test
    void nextWithoutCardsIsAllowedAndRecordsSkipped() {
        OnboardingProgress progress = atCardsStep();
        when(creditCardService.countActive(userId)).thenReturn(0L);
        when(repository.save(progress)).thenReturn(progress);

        OnboardingProgress result = service.completeCards(userId, false, "10.0.0.1");

        assertEquals(4, result.getCurrentStep());
        assertEquals(List.of("tutorial_viewed", "accounts_added", "cards_skipped"), result.getSteps());
        verify(auditLogService).log("onboarding_cards_completed", userId, "10.0.0.1",
            "{\"skipped\":false,\"cards\":0}");
    }

    @Test
    void skippingWithCardsRecordsSkipped() {
        OnboardingProgress progress = atCardsStep();
        when(creditCardService.countActive(userId)).thenReturn(1L);
        when(repository.save(progress)).thenReturn(progress);

        OnboardingProgress result = service.completeCards(userId, true, "10.0.0.1");

        assertEquals(4, result.getCurrentStep());
        assertEquals(List.of("tutorial_viewed", "accounts_added", "cards_skipped"), result.getSteps());
        verify(auditLogService).log("onboarding_cards_completed", userId, "10.0.0.1",
            "{\"skipped\":true,\"cards\":1}");
        verify(creditCardService, never()).create(any(), any());
    }

    @Test
    void cardsStepIsIdempotentFromStepFour() {
        OnboardingProgress progress = progressAt(4, "tutorial_viewed", "accounts_added", "cards_added");
        when(repository.findByUserId(userId)).thenReturn(Optional.of(progress));
        when(repository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(progress));

        OnboardingProgress result = service.completeCards(userId, true, "10.0.0.1");

        assertEquals(4, result.getCurrentStep());
        assertEquals(List.of("tutorial_viewed", "accounts_added", "cards_added"), result.getSteps());
        verifyNoInteractions(creditCardService, auditLogService);
        verify(repository, never()).save(any());
    }

    @Test
    void cardsStepIsAConflictOnStepsOneAndTwo() {
        for (int step : new int[] {1, 2}) {
            OnboardingProgress progress = progressAt(step);
            when(repository.findByUserId(userId)).thenReturn(Optional.of(progress));
            when(repository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(progress));

            assertThrows(ConflictException.class, () -> service.completeCards(userId, false, "10.0.0.1"));

            assertEquals(step, progress.getCurrentStep());
        }
        verifyNoInteractions(creditCardService, auditLogService);
        verify(repository, never()).save(any());
    }

    // ---- Story 3.4: step 4, suggested categories ----

    private OnboardingProgress atCategoriesStep() {
        OnboardingProgress progress = progressAt(4, OnboardingService.STEP_TUTORIAL_VIEWED,
            OnboardingService.STEP_ACCOUNTS_ADDED, OnboardingService.STEP_CARDS_SKIPPED);
        when(repository.findByUserId(userId)).thenReturn(Optional.of(progress));
        when(repository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(progress));
        return progress;
    }

    @Test
    void categoriesCreatesTheChosenOnesRecordsSuggestedMovesToStepFiveAndAudits() {
        OnboardingProgress progress = atCategoriesStep();
        when(categoryService.createFromSuggestions(eq(userId), any())).thenReturn(2);
        when(repository.save(progress)).thenReturn(progress);

        OnboardingProgress result = service.completeCategories(userId, List.of("housing", "leisure", "housing"), "10.0.0.1");

        assertEquals(5, result.getCurrentStep());
        assertEquals(List.of("tutorial_viewed", "accounts_added", "cards_skipped", "categories_suggested"), result.getSteps());
        // repeated keys are dropped before reaching the category service
        verify(categoryService).createFromSuggestions(userId, new java.util.LinkedHashSet<>(List.of("housing", "leisure")));
        verify(auditLogService).log("onboarding_categories_completed", userId, "10.0.0.1", "{\"count\":2}");
    }

    @Test
    void categoriesWithNoKeyOrAnUnknownKeyIs400AndChangesNothing() {
        for (List<String> keys : List.<List<String>>of(List.of(), List.of("xyz"), List.of("housing", "xyz"))) {
            assertThrows(com.porquinho.exception.BadRequestException.class,
                () -> service.completeCategories(userId, keys, "10.0.0.1"));
        }
        assertThrows(com.porquinho.exception.BadRequestException.class,
            () -> service.completeCategories(userId, null, "10.0.0.1"));
        verifyNoInteractions(categoryService, auditLogService, repository);
    }

    @Test
    void categoriesStepIsIdempotentFromStepFive() {
        OnboardingProgress progress = progressAt(5, "tutorial_viewed", "accounts_added", "cards_added", "categories_suggested");
        when(repository.findByUserId(userId)).thenReturn(Optional.of(progress));
        when(repository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(progress));

        OnboardingProgress result = service.completeCategories(userId, List.of("housing"), "10.0.0.1");

        assertEquals(5, result.getCurrentStep());
        verifyNoInteractions(categoryService, auditLogService);
        verify(repository, never()).save(any());
    }

    @Test
    void categoriesStepIsAConflictOnStepsOneToThree() {
        for (int step : new int[] {1, 2, 3}) {
            OnboardingProgress progress = progressAt(step);
            when(repository.findByUserId(userId)).thenReturn(Optional.of(progress));
            when(repository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(progress));

            assertThrows(ConflictException.class, () -> service.completeCategories(userId, List.of("housing"), "10.0.0.1"));

            assertEquals(step, progress.getCurrentStep());
        }
        verifyNoInteractions(categoryService, auditLogService);
        verify(repository, never()).save(any());
    }

    // ---- Story 3.5: step 5, customizing the categories ----

    private OnboardingProgress atCustomizationStep() {
        OnboardingProgress progress = progressAt(5, OnboardingService.STEP_TUTORIAL_VIEWED,
            OnboardingService.STEP_ACCOUNTS_ADDED, OnboardingService.STEP_CARDS_SKIPPED,
            OnboardingService.STEP_CATEGORIES_SUGGESTED);
        when(repository.findByUserId(userId)).thenReturn(Optional.of(progress));
        when(repository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(progress));
        return progress;
    }

    @Test
    void customizationNextRecordsCustomizedMovesToStepSixAndAudits() {
        OnboardingProgress progress = atCustomizationStep();
        when(repository.save(progress)).thenReturn(progress);

        OnboardingProgress result = service.completeCustomization(userId, false, "10.0.0.1");

        assertEquals(6, result.getCurrentStep());
        assertEquals(List.of("tutorial_viewed", "accounts_added", "cards_skipped", "categories_suggested",
            "categories_customized"), result.getSteps());
        verify(auditLogService).log("onboarding_customization_completed", userId, "10.0.0.1", "{\"skipped\":false}");
        verifyNoInteractions(categoryService);
    }

    @Test
    void customizationSkipRecordsSkippedAndMovesToStepSix() {
        OnboardingProgress progress = atCustomizationStep();
        when(repository.save(progress)).thenReturn(progress);

        OnboardingProgress result = service.completeCustomization(userId, true, "10.0.0.1");

        assertEquals(6, result.getCurrentStep());
        assertEquals("customization_skipped", result.getSteps().get(4));
        verify(auditLogService).log("onboarding_customization_completed", userId, "10.0.0.1", "{\"skipped\":true}");
        verifyNoInteractions(categoryService);
    }

    @Test
    void customizationStepIsIdempotentFromStepSix() {
        OnboardingProgress progress = progressAt(6, "tutorial_viewed", "accounts_added", "cards_added",
            "categories_suggested", "categories_customized");
        when(repository.findByUserId(userId)).thenReturn(Optional.of(progress));
        when(repository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(progress));

        OnboardingProgress result = service.completeCustomization(userId, true, "10.0.0.1");

        assertEquals(6, result.getCurrentStep());
        assertEquals(5, result.getSteps().size());
        verifyNoInteractions(auditLogService);
        verify(repository, never()).save(any());
    }

    @Test
    void customizationStepIsAConflictOnStepsOneToFour() {
        for (int step : new int[] {1, 2, 3, 4}) {
            OnboardingProgress progress = progressAt(step);
            when(repository.findByUserId(userId)).thenReturn(Optional.of(progress));
            when(repository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(progress));

            assertThrows(ConflictException.class, () -> service.completeCustomization(userId, false, "10.0.0.1"));

            assertEquals(step, progress.getCurrentStep());
        }
        verifyNoInteractions(auditLogService);
        verify(repository, never()).save(any());
    }

    // ---- Story 3.6: step 6, first import (no upload yet) ----

    @Test
    void importLaterRecordsImportSkippedMovesToStepSevenAndAudits() {
        OnboardingProgress progress = progressAt(6, "tutorial_viewed", "accounts_added", "cards_skipped",
            "categories_suggested", "categories_customized");
        when(repository.findByUserId(userId)).thenReturn(Optional.of(progress));
        when(repository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(progress));
        when(repository.save(progress)).thenReturn(progress);

        OnboardingProgress result = service.completeImport(userId, "10.0.0.1");

        assertEquals(7, result.getCurrentStep());
        assertEquals(List.of("tutorial_viewed", "accounts_added", "cards_skipped", "categories_suggested",
            "categories_customized", "import_skipped"), result.getSteps());
        assertFalse(result.getSteps().contains("first_import_completed"));
        verify(auditLogService).log("onboarding_import_completed", userId, "10.0.0.1", "{\"skipped\":true}");
        verifyNoInteractions(categoryService);
    }

    @Test
    void importStepIsIdempotentFromStepSeven() {
        OnboardingProgress progress = progressAt(7, "tutorial_viewed", "accounts_added", "cards_added",
            "categories_suggested", "categories_customized", "import_skipped");
        when(repository.findByUserId(userId)).thenReturn(Optional.of(progress));
        when(repository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(progress));

        OnboardingProgress result = service.completeImport(userId, "10.0.0.1");

        assertEquals(7, result.getCurrentStep());
        assertEquals(6, result.getSteps().size());
        verifyNoInteractions(auditLogService);
        verify(repository, never()).save(any());
    }

    @Test
    void importStepReturnsCompletedProgressUnchangedEvenAtStepSixOrBelow() {
        for (int step : new int[] {1, 5, 6}) {
            OnboardingProgress progress = progressAt(step);
            progress.setCompleted(true);
            when(repository.findByUserId(userId)).thenReturn(Optional.of(progress));
            when(repository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(progress));

            OnboardingProgress result = service.completeImport(userId, "10.0.0.1");

            assertEquals(step, result.getCurrentStep());
            assertTrue(result.getSteps().isEmpty());
        }
        verifyNoInteractions(auditLogService);
        verify(repository, never()).save(any());
    }

    @Test
    void importStepIsAConflictOnStepsOneToFive() {
        for (int step : new int[] {1, 2, 3, 4, 5}) {
            OnboardingProgress progress = progressAt(step);
            when(repository.findByUserId(userId)).thenReturn(Optional.of(progress));
            when(repository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(progress));

            assertThrows(ConflictException.class, () -> service.completeImport(userId, "10.0.0.1"));

            assertEquals(step, progress.getCurrentStep());
        }
        verifyNoInteractions(auditLogService);
        verify(repository, never()).save(any());
    }
}
