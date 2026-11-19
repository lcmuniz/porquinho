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
import java.time.Duration;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.porquinho.service.ImportMetricsService.SAO_PAULO;

/**
 * Retention of non-converters (pre-Epic 10, LGPD): data of a user without access is kept 30 days after the
 * access ended, then purged in full through the Story 1.7 path (AccountDeletion + purgeUser).
 *
 * End of access = access_ended_at; else trial_end_date (TRIAL) or next_billing_date (MONTHLY CANCELLED).
 * Daily at 09:00 Sao Paulo and once at startup, like TrialReminderService: warnings 7 days and 1 day before the
 * purge (Sao Paulo calendar days), at most one action per user per run, each warning at most once.
 * The e-mail goes out inside the user's transaction, before the mark: mark and audit are written only when the
 * SMTP server accepted it (decision 2026-11-18, pre-Epic 10 item 8). A refused send (bad credentials, rejected From)
 * writes nothing and counts as a failure; the warning is tried again next run and the purge waits. A duplicate
 * warning is accepted for any failure after an accepted send (save, audit or commit). The 5 s timeouts per SMTP
 * operation bound the time holding the lock (a few multiples of 5 s at worst).
 * The 7-day warning always comes first (even when the job or the e-mail was down for days) and the purge is at
 * least 7 days after it: effective purge = later of purgeAt and the 7-day mark + 7 days (no extra column).
 * The purge needs the 1-day warning sent at least 24 h before, so a job stopped for days still warns first.
 * Soft-deleted users (deletion requested by the user) are left to the Story 1.7 job.
 * Without an SMTP host or a From no warning is sent nor marked, so nobody is purged unwarned for that reason
 * (pre-Epic 10, item 7); runRetention logs one WARN per run with the postponed count.
 * A permanent refusal of the address (SMTP 5xx to RCPT TO: mailbox gone, suppressed) is the exception (decision
 * 2026-11-19): its first day is stored in retention_refused_since and, once it lasts PERMANENT_REFUSAL_PURGE_DAYS
 * (Sao Paulo calendar days), the account is purged unwarned with the audit retention_purged_without_warning.
 * An accepted send clears it; a temporary failure neither counts nor clears it.
 */
@Service
public class RetentionService {

    static final int RETENTION_DAYS = 30;

    /** Minimum notice of the 7-day warning: the purge is never before its mark + 7 days. */
    static final int SEVEN_DAY_NOTICE_DAYS = 7;

    /** Days of permanent SMTP refusal of the warning after which the account is purged without it (2026-11-19). */
    static final int PERMANENT_REFUSAL_PURGE_DAYS = 14;

    /** The 7-day warning spans at most 8 days from the 09:00 run (same window as TrialReminderService). */
    private static final int WARNING_WINDOW_DAYS = 8;

    /** The hour of the daily job (its cron), in Sao Paulo. */
    static final LocalTime RUN_TIME = LocalTime.of(9, 0);

    /** Story 10.5, RETENTION_STALLED: late once the run that should have done it is more than 24 h old ... */
    static final Duration STALLED_AFTER = Duration.ofHours(24);

    /** ... plus the time the next 09:00 run takes, so the alert job at 09:00 does not race it. */
    static final Duration RUN_GRACE = Duration.ofHours(1);

    enum Action {
        WARN_7D("7d"), WARN_1D("1d"), PURGE("purge");

        final String code;

        Action(String code) {
            this.code = code;
        }
    }

    /**
     * What processRetention did; deletion is set only for PURGE (to purge after commit). refused: the warning was
     * permanently refused and the purge still waits (counted as a failure).
     */
    record Outcome(Action action, AccountDeletion deletion, boolean refused) {
        Outcome(Action action, AccountDeletion deletion) {
            this(action, deletion, false);
        }
    }

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

        // Counts and skips postponed warnings here; processRetention checks again under lock as the real guard
        boolean mailOn = candidates.isEmpty() || emailService.isConfigured();
        int warned = 0;
        int purged = 0;
        int failed = 0;
        int postponed = 0;
        for (Subscription candidate : candidates) {
            // Cheap pre-check on the unlocked snapshot; processRetention decides again under lock
            Optional<Action> due = dueAction(candidate, now);
            if (due.isEmpty()) {
                continue;
            }
            if (!mailOn && due.get() != Action.PURGE) {
                postponed++;
                continue;
            }
            UUID userId = candidate.getUserId();
            try {
                Optional<Outcome> outcome = self.processRetention(userId);
                if (outcome.isEmpty()) {
                    continue;
                }
                if (outcome.get().refused()) {
                    failed++;
                    log.warn("Retention warning permanently refused for user {}", userId);
                } else if (outcome.get().action() == Action.PURGE) {
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

        if (postponed > 0) {
            log.warn("E-mail not configured: {} retention warning(s) postponed; their purge waits until the e-mail is back",
                postponed);
        }
        if (!candidates.isEmpty()) {
            log.info("Retention job done: {} candidates, {} warnings, {} purged, {} postponed, {} failed",
                candidates.size(), warned, purged, postponed, failed);
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

        // Without e-mail the warning would be marked but never sent, and the purge only needs the 1-day mark:
        // keep the data (longer retention) rather than delete an account that was never warned.
        if (!emailService.isConfigured()) {
            return Optional.empty();
        }

        // Send first, mark only if the SMTP server accepted it: an unsent warning must never let the purge go ahead
        OffsetDateTime purgeAt = shownPurgeAt(subscription, action, now);
        long daysLeft = TrialReminderService.daysLeft(purgeAt, now);
        EmailService.SendResult sent = emailService.sendRetentionWarning(email.get(), daysLeft, purgeAt, subscribeUrl,
            exportUrl);
        if (sent == EmailService.SendResult.PERMANENT_REFUSAL) {
            return Optional.of(onPermanentRefusal(subscription, action, now));
        }
        if (sent != EmailService.SendResult.SENT) {
            throw new IllegalStateException("retention warning " + action.code + " e-mail not sent");
        }

        subscription.setRetentionRefusedSince(null);

        if (action == Action.WARN_1D) {
            subscription.setRetentionWarning1dSentAt(now);
        } else {
            subscription.setRetentionWarning7dSentAt(now);
        }
        subscriptionRepository.save(subscription);
        auditLogService.log("retention_warning_sent", userId, null,
            String.format("{\"warning\":\"%s\"}", action.code));

        return Optional.of(new Outcome(action, null));
    }

    /**
     * The address refuses the warning for good: store the first day and, after PERMANENT_REFUSAL_PURGE_DAYS of it,
     * purge without the warning (no mark is written, so the account stays stalled for the alert until then).
     */
    private Outcome onPermanentRefusal(Subscription subscription, Action action, OffsetDateTime now) {
        OffsetDateTime since = subscription.getRetentionRefusedSince();
        if (since == null) {
            since = now;
            subscription.setRetentionRefusedSince(now);
            subscriptionRepository.save(subscription);
        }
        if (!refusedLongEnough(since, now)) {
            return new Outcome(action, null, true);
        }
        AccountDeletion deletion = accountDeletionService.createRetentionDeletion(subscription.getUserId());
        // Anonymous, keyed by the deletion row: the purge deletes every audit carrying the user_id
        auditLogService.logAnonymous("retention_purged_without_warning", null, AuditLogService.metadata(
            "deletion_id", deletion.getId(), "warning", action.code, "refused_since", since,
            "reason", "permanent_smtp_refusal"));
        return new Outcome(Action.PURGE, deletion);
    }

    /** At least PERMANENT_REFUSAL_PURGE_DAYS Sao Paulo calendar days since the first permanent refusal. */
    static boolean refusedLongEnough(OffsetDateTime since, OffsetDateTime now) {
        return !now.atZoneSameInstant(SAO_PAULO).toLocalDate()
            .isBefore(since.atZoneSameInstant(SAO_PAULO).toLocalDate().plusDays(PERMANENT_REFUSAL_PURGE_DAYS));
    }

    /**
     * Story 10.5 (RETENTION_STALLED, items 8 and 13 of the backlog): accounts whose warning or purge is due and was not
     * done by the daily run that should have done it, more than 24 h later. From the state of the database and the
     * rules of {@link #dueAction}, so it covers every reason: SMTP not configured, a refused send or a failing job.
     * A permanently refused address keeps its warning due (no mark), so it counts here until its purge.
     * The users the job skips on purpose (gone or with a deletion of Story 1.7 pending) do not count.
     */
    @Transactional(readOnly = true)
    public long stalledCount(OffsetDateTime now) {
        long stalled = 0;
        for (Subscription s : subscriptionRepository.findRetentionCandidates(
                now.plusDays(WARNING_WINDOW_DAYS).minusDays(RETENTION_DAYS))) {
            OffsetDateTime since = dueSince(s);
            if (since == null || subscriptionService.hasAccess(s)
                || !now.isAfter(firstRunAtOrAfter(since).plus(STALLED_AFTER).plus(RUN_GRACE))) {
                continue;
            }
            // only now the two queries, for the few late ones
            if (userRepository.findById(s.getUserId()).isPresent() && accountDeletionRepository
                    .findByUserIdAndStatus(s.getUserId(), DeletionStatus.PENDING_PURGE).isEmpty()) {
                stalled++;
            }
        }
        return stalled;
    }

    /**
     * Since when the next action of {@link #dueAction} is due (it may be in the future), or null without one. The
     * warnings follow the Sao Paulo calendar day of the purge (daysLeft), the purge its exact time.
     */
    static OffsetDateTime dueSince(Subscription subscription) {
        OffsetDateTime purgeAt = effectivePurgeAt(subscription);
        if (purgeAt == null) return null;
        OffsetDateTime oneDaySentAt = subscription.getRetentionWarning1dSentAt();
        if (oneDaySentAt != null) return later(purgeAt, oneDaySentAt.plusHours(24));
        int noticeDays = subscription.getRetentionWarning7dSentAt() == null ? SEVEN_DAY_NOTICE_DAYS : 1;
        return purgeAt.atZoneSameInstant(SAO_PAULO).toLocalDate().minusDays(noticeDays)
            .atStartOfDay(SAO_PAULO).toOffsetDateTime();
    }

    /** The first daily run (09:00 Sao Paulo) at or after {@code at}. */
    static OffsetDateTime firstRunAtOrAfter(OffsetDateTime at) {
        ZonedDateTime local = at.atZoneSameInstant(SAO_PAULO);
        ZonedDateTime run = local.toLocalDate().atTime(RUN_TIME).atZone(SAO_PAULO);
        return (run.isBefore(local) ? run.plusDays(1) : run).toOffsetDateTime();
    }

    /**
     * What is due now, if anything. Never two actions at once: a late run without the 7-day mark sends the
     * 7-day warning (which postpones the purge), never the 1-day one.
     */
    Optional<Action> dueAction(Subscription subscription, OffsetDateTime now) {
        if (subscriptionService.hasAccess(subscription)) {
            return Optional.empty();
        }
        OffsetDateTime purgeAt = effectivePurgeAt(subscription);
        if (purgeAt == null) {
            return Optional.empty();
        }

        // Rows marked before pre-Epic 10 item 8 may have the 1-day mark alone: they purge, no new 7-day warning
        OffsetDateTime oneDaySentAt = subscription.getRetentionWarning1dSentAt();
        if (oneDaySentAt != null) {
            return !now.isBefore(purgeAt) && !now.isBefore(oneDaySentAt.plusHours(24))
                ? Optional.of(Action.PURGE) : Optional.empty();
        }

        long daysLeft = TrialReminderService.daysLeft(purgeAt, now);
        if (subscription.getRetentionWarning7dSentAt() == null) {
            return daysLeft <= 7 ? Optional.of(Action.WARN_7D) : Optional.empty();
        }
        return daysLeft <= 1 ? Optional.of(Action.WARN_1D) : Optional.empty();
    }

    /**
     * Purge date told in the warning. 7 days: the later of purgeAt and now + 7 days (the effective purge once marked).
     * 1 day: the later of the effective purge and now + 24 h (the purge needs 24 h after the 1-day warning).
     */
    static OffsetDateTime shownPurgeAt(Subscription subscription, Action action, OffsetDateTime now) {
        OffsetDateTime earliest = action == Action.WARN_7D ? now.plusDays(SEVEN_DAY_NOTICE_DAYS) : now.plusHours(24);
        return later(effectivePurgeAt(subscription), earliest);
    }

    /** The later of purgeAt and the 7-day mark + 7 days: a late 7-day warning postpones the purge. */
    static OffsetDateTime effectivePurgeAt(Subscription subscription) {
        OffsetDateTime purgeAt = purgeAt(subscription);
        OffsetDateTime sevenDaySentAt = subscription.getRetentionWarning7dSentAt();
        return purgeAt == null || sevenDaySentAt == null ? purgeAt
            : later(purgeAt, sevenDaySentAt.plusDays(SEVEN_DAY_NOTICE_DAYS));
    }

    private static OffsetDateTime later(OffsetDateTime a, OffsetDateTime b) {
        return a.isAfter(b) ? a : b;
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
