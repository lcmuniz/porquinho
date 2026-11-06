package com.porquinho.service;

import com.porquinho.entity.OnboardingProgress;
import com.porquinho.entity.TransactionImport;
import com.porquinho.exception.ConflictException;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.repository.OnboardingProgressRepository;
import com.porquinho.repository.TransactionImportRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Guided onboarding progress (Story 3.1: step 1, the envelope budget tutorial; Story 3.2: step 2,
 * the bank accounts; Story 3.3: step 3, the credit cards; Story 3.4: step 4, the suggested categories;
 * Story 3.5: step 5, customizing the categories; Story 3.6: step 6, the first import, still without upload;
 * Story 3.7: step 7, the Dashboard GPS tour; Story 3.8: step 8, the emergency fund goal, which completes it).
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
    public static final String STEP_CARDS_ADDED = "cards_added";
    public static final String STEP_CARDS_SKIPPED = "cards_skipped";
    public static final String AUDIT_CARDS_COMPLETED = "onboarding_cards_completed";
    public static final String STEP_CATEGORIES_SUGGESTED = "categories_suggested";
    public static final String AUDIT_CATEGORIES_COMPLETED = "onboarding_categories_completed";
    public static final String STEP_CATEGORIES_CUSTOMIZED = "categories_customized";
    public static final String STEP_CUSTOMIZATION_SKIPPED = "customization_skipped";
    public static final String AUDIT_CUSTOMIZATION_COMPLETED = "onboarding_customization_completed";
    public static final String STEP_IMPORT_SKIPPED = "import_skipped";
    public static final String STEP_FIRST_IMPORT_COMPLETED = "first_import_completed";
    public static final String AUDIT_IMPORT_COMPLETED = "onboarding_import_completed";
    public static final String STEP_DASHBOARD_GPS_EXPLAINED = "dashboard_gps_explained";
    public static final String AUDIT_DASHBOARD_TOUR_COMPLETED = "onboarding_dashboard_tour_completed";
    public static final String STEP_EMERGENCY_FUND_CREATED = "emergency_fund_created";
    public static final String STEP_EMERGENCY_FUND_SKIPPED = "emergency_fund_skipped";
    public static final String AUDIT_ONBOARDING_COMPLETED = "onboarding_completed";

    private static final int TUTORIAL_STEP = 1;
    private static final int ACCOUNTS_STEP = 2;
    private static final int CARDS_STEP = 3;
    private static final int CATEGORIES_STEP = 4;
    private static final int CUSTOMIZATION_STEP = 5;
    private static final int IMPORT_STEP = 6;
    private static final int NEXT_STEP_AFTER_CUSTOMIZATION = IMPORT_STEP;
    private static final int DASHBOARD_TOUR_STEP = 7;
    private static final int NEXT_STEP_AFTER_IMPORT = DASHBOARD_TOUR_STEP;
    private static final int GOAL_STEP = 8;
    private static final int NEXT_STEP_AFTER_DASHBOARD_TOUR = GOAL_STEP;

    private final OnboardingProgressRepository onboardingProgressRepository;
    private final AuditLogService auditLogService;
    private final AccountService accountService;
    private final CreditCardService creditCardService;
    private final CategoryService categoryService;
    private final GoalService goalService;
    private final TransactionImportRepository transactionImportRepository;

    /** Spring proxy of this bean, so the calls to the apply* methods go through @Transactional. */
    private final OnboardingService self;

    public OnboardingService(OnboardingProgressRepository onboardingProgressRepository,
                             AuditLogService auditLogService,
                             AccountService accountService,
                             CreditCardService creditCardService,
                             CategoryService categoryService,
                             GoalService goalService,
                             TransactionImportRepository transactionImportRepository,
                             @Lazy OnboardingService self) {
        this.onboardingProgressRepository = onboardingProgressRepository;
        this.auditLogService = auditLogService;
        this.accountService = accountService;
        this.creditCardService = creditCardService;
        this.categoryService = categoryService;
        this.goalService = goalService;
        this.transactionImportRepository = transactionImportRepository;
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
        progress.setCurrentStep(CARDS_STEP);
        OnboardingProgress saved = onboardingProgressRepository.save(progress);

        auditLogService.log(AUDIT_ACCOUNTS_COMPLETED, userId, ipAddress,
            "{\"skipped\":" + skipped + ",\"accounts\":" + accounts + "}");
        return saved;
    }

    /**
     * Step 3 done (Story 3.3): cards are optional, so "Próximo" with no card is allowed. Records cards_added
     * when skipped=false and the user has at least one active card, cards_skipped otherwise; skipping never
     * creates a card. Moves to step 4 and writes the onboarding_cards_completed audit. 409 while step 1 or 2
     * is pending. Idempotent: once past step 3, repeating returns the current state with no change and no audit.
     */
    public OnboardingProgress completeCards(UUID userId, boolean skipped, String ipAddress) {
        getOrCreate(userId);
        return self.applyCards(userId, skipped, ipAddress);
    }

    /** Transactional half of {@link #completeCards}; public only so the Spring proxy can apply @Transactional. */
    @Transactional
    public OnboardingProgress applyCards(UUID userId, boolean skipped, String ipAddress) {
        // Row lock: two concurrent POSTs are serialized, so only the first advances and audits.
        OnboardingProgress progress = onboardingProgressRepository.findByUserIdForUpdate(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Onboarding progress not found"));

        if (progress.isCompleted() || progress.getCurrentStep() > CARDS_STEP) {
            return progress;
        }
        if (progress.getCurrentStep() < CARDS_STEP) {
            throw new ConflictException("Conclua o tutorial e as contas antes de adicionar seus cartões.");
        }

        long cards = creditCardService.countActive(userId);
        boolean added = !skipped && cards > 0;

        progress.addStep(added ? STEP_CARDS_ADDED : STEP_CARDS_SKIPPED);
        progress.setCurrentStep(CATEGORIES_STEP);
        OnboardingProgress saved = onboardingProgressRepository.save(progress);

        auditLogService.log(AUDIT_CARDS_COMPLETED, userId, ipAddress,
            "{\"skipped\":" + skipped + ",\"cards\":" + cards + "}");
        return saved;
    }

    /**
     * Step 4 done (Story 3.4): creates the chosen suggested categories (children of their group, groups as
     * parents, all is_system_default), records categories_suggested, moves to step 5 and writes the
     * onboarding_categories_completed audit with the number of children created, all in one transaction.
     * 400 when no key or an unknown key is given (checked first, before any state change). 409 while
     * steps 1 to 3 are pending. Idempotent: once past step 4, repeating creates nothing and writes no audit.
     */
    public OnboardingProgress completeCategories(UUID userId, Collection<String> keys, String ipAddress) {
        Set<String> chosen = CategoryService.validateKeys(keys);
        getOrCreate(userId);
        return self.applyCategories(userId, chosen, ipAddress);
    }

    /** Transactional half of {@link #completeCategories}; public only so the Spring proxy can apply @Transactional. */
    @Transactional
    public OnboardingProgress applyCategories(UUID userId, Collection<String> keys, String ipAddress) {
        // Row lock: two concurrent POSTs are serialized, so only the first creates the categories and audits.
        OnboardingProgress progress = onboardingProgressRepository.findByUserIdForUpdate(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Onboarding progress not found"));

        if (progress.isCompleted() || progress.getCurrentStep() > CATEGORIES_STEP) {
            return progress;
        }
        if (progress.getCurrentStep() < CATEGORIES_STEP) {
            throw new ConflictException("Conclua os passos anteriores antes de escolher suas categorias.");
        }

        int created = categoryService.createFromSuggestions(userId, keys);

        progress.addStep(STEP_CATEGORIES_SUGGESTED);
        progress.setCurrentStep(CUSTOMIZATION_STEP);
        OnboardingProgress saved = onboardingProgressRepository.save(progress);

        auditLogService.log(AUDIT_CATEGORIES_COMPLETED, userId, ipAddress, "{\"count\":" + created + "}");
        return saved;
    }

    /**
     * Step 5 done (Story 3.5): the edits were already saved one by one through the categories API, so this only
     * records categories_customized (or customization_skipped), moves to step 6 and writes the
     * onboarding_customization_completed audit. Skipping never undoes saved edits. 409 while steps 1 to 4 are
     * pending. Idempotent: once past step 5, repeating returns the current state with no change and no audit.
     */
    public OnboardingProgress completeCustomization(UUID userId, boolean skipped, String ipAddress) {
        getOrCreate(userId);
        return self.applyCustomization(userId, skipped, ipAddress);
    }

    /** Transactional half of {@link #completeCustomization}; public only so the Spring proxy can apply @Transactional. */
    @Transactional
    public OnboardingProgress applyCustomization(UUID userId, boolean skipped, String ipAddress) {
        // Row lock: two concurrent POSTs are serialized, so only the first advances and audits.
        OnboardingProgress progress = onboardingProgressRepository.findByUserIdForUpdate(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Onboarding progress not found"));

        if (progress.isCompleted() || progress.getCurrentStep() > CUSTOMIZATION_STEP) {
            return progress;
        }
        if (progress.getCurrentStep() < CUSTOMIZATION_STEP) {
            throw new ConflictException("Conclua os passos anteriores antes de personalizar suas categorias.");
        }

        progress.addStep(skipped ? STEP_CUSTOMIZATION_SKIPPED : STEP_CATEGORIES_CUSTOMIZED);
        progress.setCurrentStep(NEXT_STEP_AFTER_CUSTOMIZATION);
        OnboardingProgress saved = onboardingProgressRepository.save(progress);

        auditLogService.log(AUDIT_CUSTOMIZATION_COMPLETED, userId, ipAddress, "{\"skipped\":" + skipped + "}");
        return saved;
    }

    /**
     * Step 6 done. With {@code importId} (Story 5.10), the user finished that import: it must be one of the user's
     * (404) and COMPLETED (409), and records first_import_completed with the onboarding_import_completed audit
     * {skipped: false}. Without it (Story 3.6, "Importar depois"), records import_skipped with {skipped: true}. Either
     * way moves to step 7. 409 while steps 1 to 5 are pending. Idempotent: once past step 6, repeating returns the
     * current state with no change and no audit.
     */
    public OnboardingProgress completeImport(UUID userId, UUID importId, String ipAddress) {
        getOrCreate(userId);
        return self.applyImport(userId, importId, ipAddress);
    }

    /** Transactional half of {@link #completeImport}; public only so the Spring proxy can apply @Transactional. */
    @Transactional
    public OnboardingProgress applyImport(UUID userId, UUID importId, String ipAddress) {
        // Row lock (same as the other steps): it serializes concurrent POSTs, so only the first should advance
        // and audit.
        OnboardingProgress progress = onboardingProgressRepository.findByUserIdForUpdate(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Onboarding progress not found"));

        if (progress.isCompleted() || progress.getCurrentStep() > IMPORT_STEP) {
            return progress;
        }
        if (progress.getCurrentStep() < IMPORT_STEP) {
            throw new ConflictException("Conclua os passos anteriores antes de importar suas transações.");
        }

        boolean skipped = importId == null;
        if (!skipped) {
            requireCompletedImport(userId, importId);
        }
        progress.addStep(skipped ? STEP_IMPORT_SKIPPED : STEP_FIRST_IMPORT_COMPLETED);
        progress.setCurrentStep(NEXT_STEP_AFTER_IMPORT);
        OnboardingProgress saved = onboardingProgressRepository.save(progress);

        auditLogService.log(AUDIT_IMPORT_COMPLETED, userId, ipAddress, "{\"skipped\":" + skipped + "}");
        return saved;
    }

    private void requireCompletedImport(UUID userId, UUID importId) {
        TransactionImport transactionImport = transactionImportRepository.findByIdAndUserId(importId, userId)
            .orElseThrow(() -> new ResourceNotFoundException(TransactionImportService.NOT_FOUND_MESSAGE));
        if (transactionImport.getStatus() != TransactionImport.Status.COMPLETED) {
            throw new ConflictException("Conclua a importação antes de seguir para o próximo passo.");
        }
    }

    /**
     * Step 7 done (Story 3.7): the user went through the Dashboard GPS tour. Records dashboard_gps_explained,
     * moves to step 8 and writes the onboarding_dashboard_tour_completed audit. No skip: the tour is short and
     * has nothing to configure. 409 while steps 1 to 6 are pending. Idempotent: once past step 7, repeating
     * returns the current state with no change and no audit.
     */
    public OnboardingProgress completeDashboardTour(UUID userId, String ipAddress) {
        getOrCreate(userId);
        return self.applyDashboardTour(userId, ipAddress);
    }

    /** Transactional half of {@link #completeDashboardTour}; public only so the Spring proxy can apply @Transactional. */
    @Transactional
    public OnboardingProgress applyDashboardTour(UUID userId, String ipAddress) {
        // Row lock (same as the other steps): it serializes concurrent POSTs, so only the first should advance
        // and audit.
        OnboardingProgress progress = onboardingProgressRepository.findByUserIdForUpdate(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Onboarding progress not found"));

        if (progress.isCompleted() || progress.getCurrentStep() > DASHBOARD_TOUR_STEP) {
            return progress;
        }
        if (progress.getCurrentStep() < DASHBOARD_TOUR_STEP) {
            throw new ConflictException("Conclua os passos anteriores antes de conhecer o Dashboard GPS.");
        }

        progress.addStep(STEP_DASHBOARD_GPS_EXPLAINED);
        progress.setCurrentStep(NEXT_STEP_AFTER_DASHBOARD_TOUR);
        OnboardingProgress saved = onboardingProgressRepository.save(progress);

        auditLogService.log(AUDIT_DASHBOARD_TOUR_COMPLETED, userId, ipAddress);
        return saved;
    }

    /**
     * Step 8 done (Story 3.8), the last one: "Criar meta e concluir" (skipped=false) needs at least one active goal
     * (409 otherwise; the goal itself is created through /api/v1/goals) and records emergency_fund_created;
     * "Pular por agora" records emergency_fund_skipped and creates nothing; skipping while the user already has an
     * active goal records emergency_fund_created with skipped=false instead. Both mark the onboarding completed
     * (current_step stays 8) and write the onboarding_completed audit {skipped, goals}, the completion metric.
     * 409 while steps 1 to 7 are pending. Idempotent: once completed, repeating returns the current state with no
     * change and no audit.
     */
    public OnboardingProgress finish(UUID userId, boolean skipped, String ipAddress) {
        getOrCreate(userId);
        return self.applyFinish(userId, skipped, ipAddress);
    }

    /** Transactional half of {@link #finish}; public only so the Spring proxy can apply @Transactional. */
    @Transactional
    public OnboardingProgress applyFinish(UUID userId, boolean skipped, String ipAddress) {
        // Row lock (same as the other steps): it serializes concurrent POSTs, so only the first completes and audits.
        OnboardingProgress progress = onboardingProgressRepository.findByUserIdForUpdate(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Onboarding progress not found"));

        if (progress.isCompleted()) {
            return progress;
        }
        if (progress.getCurrentStep() < GOAL_STEP) {
            throw new ConflictException("Conclua os passos anteriores antes de criar sua primeira meta.");
        }

        long goals = goalService.countActive(userId);
        if (!skipped && goals == 0) {
            throw new ConflictException(
                "Crie sua meta para concluir, ou toque em \"Pular por agora\".");
        }
        // A goal already exists (e.g. created before a failed /finish, then "Pular" from another tab): it was
        // not skipped, so record it as created
        if (goals > 0) {
            skipped = false;
        }

        progress.addStep(skipped ? STEP_EMERGENCY_FUND_SKIPPED : STEP_EMERGENCY_FUND_CREATED);
        progress.setCompleted(true);
        OnboardingProgress saved = onboardingProgressRepository.save(progress);

        auditLogService.log(AUDIT_ONBOARDING_COMPLETED, userId, ipAddress,
            "{\"skipped\":" + skipped + ",\"goals\":" + goals + "}");
        return saved;
    }
}
