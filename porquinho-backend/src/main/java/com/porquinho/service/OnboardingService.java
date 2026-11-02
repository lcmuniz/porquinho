package com.porquinho.service;

import com.porquinho.entity.OnboardingProgress;
import com.porquinho.exception.ConflictException;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.repository.OnboardingProgressRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Guided onboarding progress (Story 3.1: step 1, the envelope budget tutorial; Story 3.2: step 2,
 * the bank accounts).
 *
 * The row is created on the first read. Two tabs opening /onboarding at the same time race on the
 * insert: UNIQUE(user_id) lets only one win and the loser re-reads the winner's row. The insert runs
 * in the repository's own short transaction (this method is NOT @Transactional on purpose), so the
 * failed insert does not poison a surrounding transaction (PostgreSQL aborts it on any error).
 */
@Service
public class OnboardingService {

    public static final String STEP_TUTORIAL_VIEWED = "tutorial_viewed";
    public static final String STEP_TUTORIAL_SKIPPED = "tutorial_skipped";
    public static final String AUDIT_TUTORIAL_COMPLETED = "onboarding_tutorial_completed";
    public static final String STEP_ACCOUNTS_ADDED = "accounts_added";
    public static final String STEP_ACCOUNTS_SKIPPED = "accounts_skipped";
    public static final String AUDIT_ACCOUNTS_COMPLETED = "onboarding_accounts_completed";

    private static final int TUTORIAL_STEP = 1;
    private static final int ACCOUNTS_STEP = 2;
    private static final int NEXT_STEP_AFTER_ACCOUNTS = 3;

    private final OnboardingProgressRepository onboardingProgressRepository;
    private final AuditLogService auditLogService;
    private final AccountService accountService;

    /** Spring proxy of this bean, so the calls to {@link #applyTutorial}/{@link #applyAccounts} go through @Transactional. */
    private final OnboardingService self;

    public OnboardingService(OnboardingProgressRepository onboardingProgressRepository,
                             AuditLogService auditLogService,
                             AccountService accountService,
                             @Lazy OnboardingService self) {
        this.onboardingProgressRepository = onboardingProgressRepository;
        this.auditLogService = auditLogService;
        this.accountService = accountService;
        this.self = self;
    }

    /** Returns the user's progress, creating it at step 1 on the first access. */
    public OnboardingProgress getOrCreate(UUID userId) {
        return onboardingProgressRepository.findByUserId(userId)
            .orElseGet(() -> create(userId));
    }

    private OnboardingProgress create(UUID userId) {
        try {
            return onboardingProgressRepository.saveAndFlush(new OnboardingProgress(userId));
        } catch (DataIntegrityViolationException e) {
            // Concurrent first access (two tabs): the other request created the row first.
            return onboardingProgressRepository.findByUserId(userId).orElseThrow(() -> e);
        }
    }

    /**
     * Step 1 done: records tutorial_viewed (or tutorial_skipped), moves to step 2 and writes the
     * onboarding_tutorial_completed audit. Idempotent: once past step 1, repeating returns the current
     * state with no change and no new audit.
     */
    public OnboardingProgress completeTutorial(UUID userId, boolean skipped, String ipAddress) {
        getOrCreate(userId);
        return self.applyTutorial(userId, skipped, ipAddress);
    }

    /** Transactional half of {@link #completeTutorial}; public only so the Spring proxy can apply @Transactional. */
    @Transactional
    public OnboardingProgress applyTutorial(UUID userId, boolean skipped, String ipAddress) {
        // Row lock: two concurrent POSTs are serialized, so only the first advances and audits.
        OnboardingProgress progress = onboardingProgressRepository.findByUserIdForUpdate(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Onboarding progress not found"));

        if (progress.isCompleted() || progress.getCurrentStep() > TUTORIAL_STEP) {
            return progress;
        }

        progress.addStep(skipped ? STEP_TUTORIAL_SKIPPED : STEP_TUTORIAL_VIEWED);
        progress.setCurrentStep(ACCOUNTS_STEP);
        OnboardingProgress saved = onboardingProgressRepository.save(progress);

        auditLogService.log(AUDIT_TUTORIAL_COMPLETED, userId, ipAddress, "{\"skipped\":" + skipped + "}");
        return saved;
    }

    /**
     * Step 2 done: "Próximo" (skipped=false) needs at least one active account (409 otherwise);
     * "Pular por agora" (skipped=true) creates "Minha Conta Principal" (checking, balance 0) when the user
     * has none. Records accounts_added (or accounts_skipped), moves to step 3 and writes the
     * onboarding_accounts_completed audit. 409 while the tutorial (step 1) is pending. Idempotent: once
     * past step 2, repeating returns the current state with no change and no new audit.
     */
    public OnboardingProgress completeAccounts(UUID userId, boolean skipped, String ipAddress) {
        getOrCreate(userId);
        return self.applyAccounts(userId, skipped, ipAddress);
    }

    /** Transactional half of {@link #completeAccounts}; public only so the Spring proxy can apply @Transactional. */
    @Transactional
    public OnboardingProgress applyAccounts(UUID userId, boolean skipped, String ipAddress) {
        // Row lock: two concurrent POSTs are serialized, so only the first creates the default account and audits.
        OnboardingProgress progress = onboardingProgressRepository.findByUserIdForUpdate(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Onboarding progress not found"));

        if (progress.isCompleted() || progress.getCurrentStep() > ACCOUNTS_STEP) {
            return progress;
        }
        if (progress.getCurrentStep() < ACCOUNTS_STEP) {
            throw new ConflictException("Conclua o tutorial antes de adicionar suas contas.");
        }

        long accounts = accountService.countActive(userId);
        if (accounts == 0) {
            if (!skipped) {
                throw new ConflictException(
                    "Adicione ao menos uma conta para continuar, ou toque em \"Pular por agora\".");
            }
            accountService.createDefault(userId);
            accounts = 1;
        }

        progress.addStep(skipped ? STEP_ACCOUNTS_SKIPPED : STEP_ACCOUNTS_ADDED);
        progress.setCurrentStep(NEXT_STEP_AFTER_ACCOUNTS);
        OnboardingProgress saved = onboardingProgressRepository.save(progress);

        auditLogService.log(AUDIT_ACCOUNTS_COMPLETED, userId, ipAddress,
            "{\"skipped\":" + skipped + ",\"accounts\":" + accounts + "}");
        return saved;
    }
}
