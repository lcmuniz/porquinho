package com.porquinho.service;

import com.porquinho.dto.AccountDeletionResponse;
import com.porquinho.entity.AccountDeletion;
import com.porquinho.entity.AccountDeletion.DeletionStatus;
import com.porquinho.entity.DataExport;
import com.porquinho.entity.Subscription;
import com.porquinho.entity.User;
import com.porquinho.entity.UserSession;
import com.porquinho.exception.RateLimitExceededException;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.repository.AccountDeletionRepository;
import com.porquinho.repository.AccountBalanceHistoryRepository;
import com.porquinho.repository.AccountRepository;
import com.porquinho.repository.AiClassificationHistoryRepository;
import com.porquinho.repository.CategoryRepository;
import com.porquinho.repository.CreditCardRepository;
import com.porquinho.repository.GoalRepository;
import com.porquinho.repository.AuditLogRepository;
import com.porquinho.repository.BillingTransactionRepository;
import com.porquinho.repository.DataExportRepository;
import com.porquinho.repository.OnboardingProgressRepository;
import com.porquinho.repository.SubscriptionRepository;
import com.porquinho.repository.ReconciliationRepository;
import com.porquinho.repository.TransactionImportRepository;
import com.porquinho.repository.TransactionRepository;
import com.porquinho.repository.TransferRepository;
import com.porquinho.repository.UserRepository;
import com.porquinho.repository.UserSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing user account deletion requests (Story 1.7 - LGPD right to erasure).
 *
 * Two-phase request:
 * - Phase 1 (transactional): rate-limit check, soft-delete user, revoke sessions, blacklist JWTs,
 *   create AccountDeletion record, write audit log.
 * - Phase 2 (post-commit): best-effort email notification.
 *
 * Daily scheduled purge (03:00 UTC):
 * - Each AccountDeletion is processed in its own transaction (REQUIRES_NEW) so a single
 *   failure does not block the queue.
 * - Hard-deletes data_exports, user_sessions, audit_logs, billing_transactions, subscriptions,
 *   onboarding_progress, ai_classification_history, transactions, transaction_imports, transfers, reconciliation_history,
 *   account_balance_history, accounts, credit_cards, categories, goals, users
 *   (in that order, native SQL to bypass @Where filters on User and UserSession; billing_transactions
 *   has FKs to subscriptions and users, so it goes before both).
 * - Calls Supabase Admin API best-effort.
 */
@Service
public class AccountDeletionService {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);

    private static final int RETENTION_DAYS = 30;
    private static final int RATE_LIMIT_PER_USER_DAY = 1;
    private static final int RATE_LIMIT_WINDOW_SECONDS = 86400;

    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final AuditLogRepository auditLogRepository;
    private final DataExportRepository dataExportRepository;
    private final AccountDeletionRepository accountDeletionRepository;
    private final JwtBlacklistService jwtBlacklistService;
    private final EmailService emailService;
    private final AuditLogService auditLogService;
    private final SupabaseAdminClient supabaseAdminClient;
    private final RateLimitService rateLimitService;
    private final SubscriptionRepository subscriptionRepository;
    private final StripeBillingService stripeBillingService;
    private final BillingTransactionRepository billingTransactionRepository;
    private final SubscriptionService subscriptionService;
    private final OnboardingProgressRepository onboardingProgressRepository;
    private final AccountRepository accountRepository;
    private final CreditCardRepository creditCardRepository;
    private final CategoryRepository categoryRepository;
    private final GoalRepository goalRepository;
    private final AccountBalanceHistoryRepository accountBalanceHistoryRepository;
    private final TransferRepository transferRepository;
    private final ReconciliationRepository reconciliationRepository;
    private final TransactionImportRepository transactionImportRepository;
    private final TransactionRepository transactionRepository;
    private final AiClassificationHistoryRepository aiClassificationHistoryRepository;

    @Value("${app.export.directory:./exports}")
    private String exportDirectory;

    /**
     * Self-injected proxy reference. Required so that calls from purgeScheduledDeletions
     * to purgeUser go through the Spring AOP proxy and the @Transactional(REQUIRES_NEW)
     * annotation actually starts a new transaction. Without this, the call is a plain
     * Java invocation and the propagation annotation would be ignored.
     */
    private final AccountDeletionService self;

    public AccountDeletionService(
            UserRepository userRepository,
            UserSessionRepository userSessionRepository,
            AuditLogRepository auditLogRepository,
            DataExportRepository dataExportRepository,
            AccountDeletionRepository accountDeletionRepository,
            JwtBlacklistService jwtBlacklistService,
            EmailService emailService,
            AuditLogService auditLogService,
            SupabaseAdminClient supabaseAdminClient,
            RateLimitService rateLimitService,
            SubscriptionRepository subscriptionRepository,
            StripeBillingService stripeBillingService,
            BillingTransactionRepository billingTransactionRepository,
            SubscriptionService subscriptionService,
            OnboardingProgressRepository onboardingProgressRepository,
            AccountRepository accountRepository,
            CreditCardRepository creditCardRepository,
            CategoryRepository categoryRepository,
            GoalRepository goalRepository,
            AccountBalanceHistoryRepository accountBalanceHistoryRepository,
            TransferRepository transferRepository,
            ReconciliationRepository reconciliationRepository,
            TransactionImportRepository transactionImportRepository,
            TransactionRepository transactionRepository,
            AiClassificationHistoryRepository aiClassificationHistoryRepository,
            @Lazy AccountDeletionService self) {
        this.userRepository = userRepository;
        this.userSessionRepository = userSessionRepository;
        this.auditLogRepository = auditLogRepository;
        this.dataExportRepository = dataExportRepository;
        this.accountDeletionRepository = accountDeletionRepository;
        this.jwtBlacklistService = jwtBlacklistService;
        this.emailService = emailService;
        this.auditLogService = auditLogService;
        this.supabaseAdminClient = supabaseAdminClient;
        this.rateLimitService = rateLimitService;
        this.subscriptionRepository = subscriptionRepository;
        this.stripeBillingService = stripeBillingService;
        this.billingTransactionRepository = billingTransactionRepository;
        this.subscriptionService = subscriptionService;
        this.onboardingProgressRepository = onboardingProgressRepository;
        this.accountRepository = accountRepository;
        this.creditCardRepository = creditCardRepository;
        this.categoryRepository = categoryRepository;
        this.goalRepository = goalRepository;
        this.accountBalanceHistoryRepository = accountBalanceHistoryRepository;
        this.transferRepository = transferRepository;
        this.reconciliationRepository = reconciliationRepository;
        this.transactionImportRepository = transactionImportRepository;
        this.transactionRepository = transactionRepository;
        this.aiClassificationHistoryRepository = aiClassificationHistoryRepository;
        this.self = self;
    }

    /**
     * Internal context returned from the transactional phase to the post-commit phase.
     * The flag tells whether a NEW deletion was created in this call (true) or this is
     * an idempotent return of an existing PENDING_PURGE (false). The latter skips the
     * email send to avoid spamming on retries.
     */
    private record DeletionContext(String email, AccountDeletion deletion, boolean newlyCreated) {}

    /**
     * Public entry point. Orchestrates the two phases so the email send (slow I/O) does
     * not hold a database transaction open during SMTP wait.
     */
    public AccountDeletionResponse requestDeletion(UUID userId, String ipAddress) {
        DeletionContext context = self.executeDeletionTransaction(userId, ipAddress);

        // Phase 2: post-commit, best-effort email notification.
        // Skip on idempotent return: the email was already sent on the original call.
        // Wrap in try/catch as a defensive backstop. EmailService.sendAccountDeletionConfirmation
        // already catches all exceptions internally, but if a future refactor (or unchecked
        // throw from the mailSender bean itself before EmailService can catch) leaks an
        // exception, we don't want to surface a 500 to a user whose Phase 1 already committed.
        if (context.newlyCreated()) {
            try {
                emailService.sendAccountDeletionConfirmation(context.email(), context.deletion().getScheduledPurgeAt());
            } catch (RuntimeException e) {
                log.warn("Account deletion email send failed (best-effort, account is already scheduled for deletion): {} - {}",
                    e.getClass().getSimpleName(), e.getMessage());
            }
        }

        return AccountDeletionResponse.fromEntity(context.deletion());
    }

    /**
     * Phase 1: transactional. Idempotency check FIRST (before consuming rate-limit token,
     * so legitimate retries don't get a 429). Then rate-limit, soft-delete user, revoke
     * sessions, blacklist JWTs, create AccountDeletion, audit. Returns context for Phase 2.
     *
     * Public visibility is required for Spring AOP proxy to apply @Transactional.
     */
    @Transactional
    public DeletionContext executeDeletionTransaction(UUID userId, String ipAddress) {
        // Idempotency FIRST: if there is already a PENDING_PURGE for this user, return it
        // without burning the rate-limit token or re-running the soft-delete. The user lookup
        // uses findByIdIncludingDeleted to bypass @Where(deleted_at IS NULL) since the user
        // was already soft-deleted on the original request.
        Optional<AccountDeletion> existing = accountDeletionRepository
            .findByUserIdAndStatus(userId, DeletionStatus.PENDING_PURGE);
        if (existing.isPresent()) {
            User user = userRepository.findByIdIncludingDeleted(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
            return new DeletionContext(user.getEmail(), existing.get(), false);
        }

        // Rate limit consumed only when actually creating a new deletion.
        if (!rateLimitService.allowRequest("account_deletion:" + userId, RATE_LIMIT_PER_USER_DAY, RATE_LIMIT_WINDOW_SECONDS)) {
            throw new RateLimitExceededException("Account deletion rate limit exceeded. Maximum 1 request per day.");
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        String email = user.getEmail();

        // Capture active sessions BEFORE soft-deleting the user.
        List<UserSession> activeSessions = userSessionRepository
            .findByUserIdAndRevokedAtIsNullAndExpiresAtAfter(userId, OffsetDateTime.now());

        // Revoke each session and blacklist its JWT.
        OffsetDateTime now = OffsetDateTime.now();
        for (UserSession session : activeSessions) {
            session.setRevokedAt(now);
            jwtBlacklistService.blacklistToken(session.getJwtTokenHash(), session.getExpiresAt());
        }
        userSessionRepository.saveAll(activeSessions);

        // Soft-delete the user.
        user.softDelete(userId);
        userRepository.save(user);

        // Create AccountDeletion record. Catch unique-constraint violations from the partial
        // index on (user_id) WHERE status='PENDING_PURGE' as a defensive race-condition guard:
        // if a concurrent request slipped past the idempotency check, we re-fetch and return.
        OffsetDateTime scheduledPurgeAt = OffsetDateTime.now().plusDays(RETENTION_DAYS);
        AccountDeletion deletion = new AccountDeletion(userId, scheduledPurgeAt, ipAddress);
        AccountDeletion saved;
        try {
            saved = accountDeletionRepository.save(deletion);
        } catch (DataIntegrityViolationException e) {
            saved = accountDeletionRepository
                .findByUserIdAndStatus(userId, DeletionStatus.PENDING_PURGE)
                .orElseThrow(() -> e);
            return new DeletionContext(email, saved, false);
        }

        // Audit log of request.
        String metadata = String.format("{\"scheduled_purge_at\":\"%s\"}", scheduledPurgeAt);
        auditLogService.log("account_deletion_requested", userId, ipAddress, metadata);

        // Stop Stripe billing now, not at purge (retro Epic 2, F1): the row expires in this transaction and
        // Stripe is cancelled best-effort after commit. purgeUser still cancels again as a safety net.
        subscriptionService.cancelForAccountDeletion(userId);

        return new DeletionContext(email, saved, true);
    }

    /**
     * Daily scheduled job (03:00 UTC) - intentionally NOT @Transactional.
     * Each user is processed in its own transaction via {@link #purgeUser(AccountDeletion)}.
     */
    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    public void purgeScheduledDeletions() {
        List<AccountDeletion> due = accountDeletionRepository
            .findAllByStatusAndScheduledPurgeAtBefore(DeletionStatus.PENDING_PURGE, OffsetDateTime.now());

        if (due.isEmpty()) {
            return;
        }

        log.info("Starting account purge job - {} accounts due", due.size());

        int succeeded = 0;
        int failed = 0;
        for (AccountDeletion deletion : due) {
            try {
                self.purgeUser(deletion);
                succeeded++;
            } catch (Exception e) {
                failed++;
                log.error("Failed to purge account_deletion {} (userId={}): {} - {}",
                    deletion.getId(), deletion.getUserId(),
                    e.getClass().getSimpleName(), e.getMessage());
                // Status remains PENDING_PURGE for retry on next run.
            }
        }

        log.info("Account purge job done: {} purged, {} failed", succeeded, failed);
    }

    /**
     * Hard-delete a single user's data. Each call runs in its own NEW transaction so
     * one user's failure does not abort the whole job.
     *
     * Uses native SQL DELETEs to bypass @Where filters on User (deleted_at IS NULL) and
     * UserSession (revoked_at IS NULL) - both filters would make a JPQL DELETE no-op
     * because by purge time the user is soft-deleted and all sessions are revoked.
     *
     * The anonymous "account_purged" audit log is written via REQUIRES_NEW (configured on
     * AuditLogService.logAnonymous) so it commits independently of this transaction. That
     * way it survives a rollback here and provides a durable LGPD audit trail even when
     * the purge itself fails.
     *
     * NOTE for future epics (3-10): when new tables with user_id are added (budgets, etc.),
     * extend this method to delete from those tables BEFORE the users delete (matching the
     * dependency chain below). transactions (Story 5.4) goes before transaction_imports,
     * accounts, credit_cards and categories (FKs to all of them).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void purgeUser(AccountDeletion deletion) {
        UUID userId = deletion.getUserId();

        // 1. Anonymous audit log FIRST (in its own REQUIRES_NEW tx). Survives rollback.
        String metadata = String.format("{\"deletion_id\":\"%s\"}", deletion.getId());
        auditLogService.logAnonymous("account_purged", deletion.getRequestedIp(), metadata);

        // 2. Delete export files from filesystem (best-effort - any failure logs warn but
        //    does not abort the transaction). Path traversal defense: only delete files
        //    that resolve inside the configured export directory.
        Path exportRoot = Paths.get(exportDirectory).toAbsolutePath().normalize();
        List<DataExport> exports = dataExportRepository.findAllByUserId(userId);
        for (DataExport export : exports) {
            String filePath = export.getFilePath();
            if (filePath == null || filePath.isBlank()) {
                continue;
            }
            try {
                Path resolved = Paths.get(filePath).toAbsolutePath().normalize();
                if (!resolved.startsWith(exportRoot)) {
                    log.warn("Refusing to delete export file outside configured directory: {} (root: {})",
                        resolved, exportRoot);
                    continue;
                }
                Files.deleteIfExists(resolved);
            } catch (Exception e) {
                // Best-effort: catch any failure (IOException, SecurityException, runtime)
                // and continue. A stale file is acceptable; failing the whole purge is not.
                log.warn("Failed to delete export file {} during purge: {} - {}",
                    filePath, e.getClass().getSimpleName(), e.getMessage());
            }
        }

        // 3. Hard-delete in dependency order (no real FKs today, but order is forward-proof).
        //    Native DELETEs for User and UserSession bypass @Where; JPQL is fine for the rest.
        dataExportRepository.deleteByUserId(userId);
        userSessionRepository.deleteByUserId(userId);
        auditLogRepository.deleteByUserId(userId);
        // Story 2.2: stop Stripe billing before the local row (and its stripe id) disappears. Best-effort.
        subscriptionRepository.findByUserId(userId)
            .map(Subscription::getStripeSubscriptionId)
            .ifPresent(stripeBillingService::cancelSubscription);
        billingTransactionRepository.deleteByUserId(userId);  // Story 2.6: FKs to subscriptions and users
        subscriptionRepository.deleteByUserId(userId);  // Story 2.1: FK to users, must go first
        onboardingProgressRepository.deleteByUserId(userId);  // Story 3.1: FK to users
        aiClassificationHistoryRepository.deleteByUserId(userId);  // Story 5.8: FKs to transactions, categories and users
        transactionRepository.deleteByUserId(userId);  // Story 5.4: FKs to imports, accounts, cards, categories and users
        transactionImportRepository.deleteByUserId(userId);  // Story 5.1: FKs to accounts, credit_cards and users
        transferRepository.deleteByUserId(userId);  // Story 4.5: FKs to accounts and users (removed transfers too)
        reconciliationRepository.deleteByUserId(userId);  // Story 4.6: FK to users (accounts FK cascades); explicit anyway
        accountBalanceHistoryRepository.deleteByUserId(userId);  // Story 4.3: FK to users (accounts FK cascades); explicit anyway
        accountRepository.deleteByUserId(userId);  // Story 3.2: FK to users (removed accounts too)
        creditCardRepository.deleteByUserId(userId);  // Story 3.3: FK to users (removed cards too)
        categoryRepository.deleteByUserId(userId);  // Story 3.4: FK to users; one statement, so parent FK does not block
        goalRepository.deleteByUserId(userId);  // Story 3.8: FK to users (removed goals too)
        userRepository.deleteByIdNative(userId);

        // 4. Best-effort Supabase removal (SupabaseAdminClient already swallows failures).
        supabaseAdminClient.deleteUser(userId);

        // 5. Mark account_deletion as PURGED.
        deletion.setStatus(DeletionStatus.PURGED);
        deletion.setPurgedAt(OffsetDateTime.now());
        accountDeletionRepository.save(deletion);
    }
}
