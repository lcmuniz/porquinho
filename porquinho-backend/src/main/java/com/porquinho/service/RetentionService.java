package com.porquinho.service;

import com.porquinho.entity.AccountDeletion;
import com.porquinho.entity.AccountDeletion.DeletionStatus;
import com.porquinho.entity.Subscription;
import com.porquinho.entity.Subscription.PlanType;
import com.porquinho.entity.Subscription.Status;
import com.porquinho.entity.User;
import com.porquinho.repository.AccountDeletionRepository;
import com.porquinho.repository.SubscriptionRepository;
import com.porquinho.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Retention of non-converters (pre-Epic 10, LGPD): data of a user without access is kept 30 days after the
 * access ended, then purged in full through the Story 1.7 path (AccountDeletion + purgeUser).
 *
 * End of access = access_ended_at; else trial_end_date (TRIAL) or next_billing_date (MONTHLY CANCELLED).
 * Daily at 09:00 Sao Paulo and once at startup, like TrialReminderService: warnings 7 days and 1 day before the
 * purge (Sao Paulo calendar days), at most one action per user per run, each warning at most once. The mark and
 * audit commit first and the e-mail goes after commit (a lost warning is acceptable, a duplicate is not).
 * The purge needs the 1-day warning sent at least 24 h before, so a job stopped for days still warns first.
 * Soft-deleted users (deletion requested by the user) are left to the Story 1.7 job.
 * Without an SMTP host no warning is marked, so nobody is purged unwarned for that reason (pre-Epic 10, item 7).
 * A send that fails with SMTP configured (bad credentials, rejected From) still keeps the mark, as before.
 */
@Service
public class RetentionService {

    static final int RETENTION_DAYS = 30;

    /** The 7-day warning spans at most 8 days from the 09:00 run (same window as TrialReminderService). */
    private static final int WARNING_WINDOW_DAYS = 8;

    enum Action {
        WARN_7D("7d"), WARN_1D("1d"), PURGE("purge");

        final String code;

        Action(String code) {
            this.code = code;
        }
    }

    /** What processRetention did; deletion is set only for PURGE (to purge after commit). */
    record Outcome(Action action, AccountDeletion deletion) {}

    private static final Logger log = LoggerFactory.getLogger(RetentionService.class);

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final AccountDeletionRepository accountDeletionRepository;
    private final AccountDeletionService accountDeletionService;
    private final SubscriptionService subscriptionService;
    private final AuditLogService auditLogService;
    private final EmailService emailService;
    private final Clock clock;
    private final String subscribeUrl;
    private final String exportUrl;

    /** Spring proxy of this bean, so processRetention runs in its own REQUIRES_NEW transaction. */
    private final RetentionService self;

    public RetentionService(SubscriptionRepository subscriptionRepository,
                            UserRepository userRepository,
                            AccountDeletionRepository accountDeletionRepository,
                            AccountDeletionService accountDeletionService,
                            SubscriptionService subscriptionService,
                            AuditLogService auditLogService,
                            EmailService emailService,
                            Clock clock,
                            @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl,
                            @Lazy RetentionService self) {
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.accountDeletionRepository = accountDeletionRepository;
        this.accountDeletionService = accountDeletionService;
        this.subscriptionService = subscriptionService;
        this.auditLogService = auditLogService;
        this.emailService = emailService;
        this.clock = clock;
        String base = frontendUrl != null && frontendUrl.endsWith("/")
            ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        this.subscribeUrl = base + "/assinatura";
        this.exportUrl = base + "/auth/data-export";
        this.self = self;
    }

    /** At startup too: a backend down at 09:00 would otherwise skip a day. Async, so it does not hold readiness. */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void runAtStartup() {
        try {
            runRetention();
        } catch (RuntimeException e) {
            log.warn("Retention run at startup failed: {} - {}", e.getClass().getSimpleName(), e.getMessage());
        }
    }

    /** Daily job - intentionally NOT @Transactional: one transaction per user, so one failure does not stop the others. */
    @Scheduled(cron = "0 0 9 * * *", zone = "America/Sao_Paulo")
    public void runRetention() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<Subscription> candidates = subscriptionRepository
            .findRetentionCandidates(now.plusDays(WARNING_WINDOW_DAYS).minusDays(RETENTION_DAYS));

        int warned = 0;
        int purged = 0;
        int failed = 0;
        for (Subscription candidate : candidates) {
            // Cheap pre-check on the unlocked snapshot; processRetention decides again under lock
            if (dueAction(candidate, now).isEmpty()) {
                continue;
            }
            UUID userId = candidate.getUserId();
            try {
                Optional<Outcome> outcome = self.processRetention(userId);
                if (outcome.isEmpty()) {
                    continue;
                }
                if (outcome.get().action() == Action.PURGE) {
                    // Own REQUIRES_NEW transaction, after the deletion committed. On failure the row stays
                    // PENDING_PURGE (due now) and the 03:00 job of AccountDeletionService retries it.
                    accountDeletionService.purgeUser(outcome.get().deletion());
                    purged++;
                } else {
                    warned++;
                }
            } catch (Exception e) {
                failed++;
                log.warn("Retention failed for user {}: {} - {}", userId, e.getClass().getSimpleName(), e.getMessage());
            }
        }

        if (!candidates.isEmpty()) {
            log.info("Retention job done: {} candidates, {} warnings, {} purged, {} failed",
                candidates.size(), warned, purged, failed);
        }
    }

    /**
     * Decide and record the action for one user under the subscription row lock (the webhook may have
     * reactivated the plan since the candidate query).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Outcome> processRetention(UUID userId) {
        Subscription subscription = subscriptionRepository.findByUserIdForUpdate(userId).orElse(null);
        if (subscription == null) {
            return Optional.empty();
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        Optional<Action> due = dueAction(subscription, now);
        if (due.isEmpty()) {
            return Optional.empty();
        }

        // Soft-deleted user (deletion requested, Story 1.7): @Where hides it and that job owns the purge
        Optional<String> email = userRepository.findById(userId).map(User::getEmail);
        if (email.isEmpty()
            || accountDeletionRepository.findByUserIdAndStatus(userId, DeletionStatus.PENDING_PURGE).isPresent()) {
            return Optional.empty();
        }

        Action action = due.get();
        if (action == Action.PURGE) {
            AccountDeletion deletion = accountDeletionService.createRetentionDeletion(userId);
            return Optional.of(new Outcome(action, deletion));
        }

        // Without SMTP the warning would be marked but never sent, and the purge only needs the 1-day mark:
        // keep the data (longer retention) rather than delete an account that was never warned.
        if (!emailService.isConfigured()) {
            log.warn("SMTP not configured - retention warning {} for user {} postponed", action.code, userId);
            return Optional.empty();
        }

        if (action == Action.WARN_1D) {
            subscription.setRetentionWarning1dSentAt(now);
        } else {
            subscription.setRetentionWarning7dSentAt(now);
        }
        subscriptionRepository.save(subscription);
        auditLogService.log("retention_warning_sent", userId, null,
            String.format("{\"warning\":\"%s\"}", action.code));

        // Late run: the purge waits >= 24 h after the 1-day warning, so never announce a date before that
        OffsetDateTime purgeAt = shownPurgeAt(subscription, now);
        long daysLeft = TrialReminderService.daysLeft(purgeAt, now);
        AfterCommit.run(() -> {
            try {
                emailService.sendRetentionWarning(email.get(), daysLeft, purgeAt, subscribeUrl, exportUrl);
            } catch (RuntimeException e) {
                // Already marked as sent: a lost warning is acceptable, a duplicate is not.
                log.warn("Retention warning {} e-mail failed for user {}: {} - {}",
                    action.code, userId, e.getClass().getSimpleName(), e.getMessage());
            }
        });

        return Optional.of(new Outcome(action, null));
    }

    /** What is due now, if anything. Never two actions at once: a late run sends only the 1-day warning. */
    Optional<Action> dueAction(Subscription subscription, OffsetDateTime now) {
        if (subscriptionService.hasAccess(subscription)) {
            return Optional.empty();
        }
        OffsetDateTime purgeAt = purgeAt(subscription);
        if (purgeAt == null) {
            return Optional.empty();
        }

        OffsetDateTime oneDaySentAt = subscription.getRetentionWarning1dSentAt();
        if (oneDaySentAt != null) {
            return !now.isBefore(purgeAt) && !now.isBefore(oneDaySentAt.plusHours(24))
                ? Optional.of(Action.PURGE) : Optional.empty();
        }

        long daysLeft = TrialReminderService.daysLeft(purgeAt, now);
        if (daysLeft <= 1) {
            return Optional.of(Action.WARN_1D);
        }
        if (daysLeft <= 7 && subscription.getRetentionWarning7dSentAt() == null) {
            return Optional.of(Action.WARN_7D);
        }
        return Optional.empty();
    }

    /** Purge date told in the warning: the later of purgeAt and now + 24 h (the purge needs 24 h after the 1-day warning). */
    static OffsetDateTime shownPurgeAt(Subscription subscription, OffsetDateTime now) {
        OffsetDateTime purgeAt = purgeAt(subscription);
        OffsetDateTime earliest = now.plusHours(24);
        return purgeAt.isAfter(earliest) ? purgeAt : earliest;
    }

    /** End of access + 30 days, or null when the row has no end (should not happen without access). */
    static OffsetDateTime purgeAt(Subscription subscription) {
        OffsetDateTime end = subscription.getAccessEndedAt();
        if (end == null) {
            end = subscription.getPlanType() == PlanType.TRIAL ? subscription.getTrialEndDate()
                : subscription.getStatus() == Status.CANCELLED ? subscription.getNextBillingDate() : null;
        }
        return end == null ? null : end.plusDays(RETENTION_DAYS);
    }
}
