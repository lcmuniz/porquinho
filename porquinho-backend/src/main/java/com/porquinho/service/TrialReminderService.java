package com.porquinho.service;

import com.porquinho.entity.Subscription;
import com.porquinho.entity.Subscription.PlanType;
import com.porquinho.entity.Subscription.Status;
import com.porquinho.entity.User;
import com.porquinho.repository.SubscriptionRepository;
import com.porquinho.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Trial-ending reminder e-mails (Story 2.3): "termina em 7 dias" and "termina amanhã".
 *
 * Daily at 09:00 Sao Paulo (decision 2026-10-31: Brazilian audience, no per-user timezone).
 * Days left = calendar days between today and trial_end_date, both as Sao Paulo dates.
 * Tolerant to missed runs: 7d applies while 1 < days left <= 7, 1d while days left <= 1 and the
 * trial is still running. Only one of them is sent per run, and each at most once (sent_at columns).
 *
 * The sent_at mark and the audit are committed first; the e-mail goes after commit. If SMTP fails the
 * reminder is lost (best-effort) instead of being sent twice. Single backend instance: no distributed lock.
 */
@Service
public class TrialReminderService {

    static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    /** Candidate windows: 7 (1) Sao Paulo calendar days never span more than 8 (2) days from the 09:00 run. */
    private static final int SEVEN_DAY_WINDOW_DAYS = 8;
    private static final int ONE_DAY_WINDOW_DAYS = 2;

    enum Reminder {
        SEVEN_DAYS("7d"), ONE_DAY("1d");

        final String code;

        Reminder(String code) {
            this.code = code;
        }
    }

    private static final Logger log = LoggerFactory.getLogger(TrialReminderService.class);

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final EmailService emailService;
    private final Clock clock;
    private final String subscribeUrl;

    /** Spring proxy of this bean, so processReminder runs in its own REQUIRES_NEW transaction. */
    private final TrialReminderService self;

    public TrialReminderService(SubscriptionRepository subscriptionRepository,
                                UserRepository userRepository,
                                AuditLogService auditLogService,
                                EmailService emailService,
                                Clock clock,
                                @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl,
                                @Lazy TrialReminderService self) {
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.emailService = emailService;
        this.clock = clock;
        this.subscribeUrl = stripTrailingSlash(frontendUrl) + "/assinatura";
        this.self = self;
    }

    /**
     * Daily job - intentionally NOT @Transactional: each subscription runs in its own transaction,
     * so one failure does not stop the others.
     */
    @Scheduled(cron = "0 0 9 * * *", zone = "America/Sao_Paulo")
    public void sendTrialReminders() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<Subscription> candidates = subscriptionRepository
            .findTrialReminderCandidates(now, now.plusDays(SEVEN_DAY_WINDOW_DAYS), now.plusDays(ONE_DAY_WINDOW_DAYS));

        if (candidates.isEmpty()) {
            return;
        }

        int sent = 0;
        int failed = 0;
        for (Subscription candidate : candidates) {
            try {
                if (self.processReminder(candidate.getUserId()).isPresent()) {
                    sent++;
                }
            } catch (Exception e) {
                failed++;
                log.warn("Trial reminder failed for user {}: {} - {}",
                    candidate.getUserId(), e.getClass().getSimpleName(), e.getMessage());
            }
        }

        log.info("Trial reminder job done: {} candidates, {} reminders, {} failed", candidates.size(), sent, failed);
    }

    /**
     * Decide and record the reminder for one user. Re-reads the row under lock: the user may have
     * subscribed since the candidate query, and the webhook (activateMonthly) writes the same row.
     *
     * @return the reminder recorded, or empty when nothing is due
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Reminder> processReminder(UUID userId) {
        Subscription subscription = subscriptionRepository.findByUserIdForUpdate(userId).orElse(null);
        if (subscription == null) {
            return Optional.empty();
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        Optional<Reminder> due = dueReminder(subscription, now);
        if (due.isEmpty()) {
            return due;
        }

        // Soft-deleted user (pending LGPD purge): @Where hides it. Nothing to send, so mark/audit nothing.
        Optional<String> email = userRepository.findById(userId).map(User::getEmail);
        if (email.isEmpty()) {
            log.warn("No active user {} for trial reminder; skipped", userId);
            return Optional.empty();
        }

        Reminder reminder = due.get();
        if (reminder == Reminder.ONE_DAY) {
            subscription.setTrialReminder1dSentAt(now);
        } else {
            subscription.setTrialReminder7dSentAt(now);
        }
        subscriptionRepository.save(subscription);
        auditLogService.log("trial_reminder_sent", userId, null,
            String.format("{\"reminder\":\"%s\"}", reminder.code));

        long daysLeft = daysLeft(subscription.getTrialEndDate(), now);
        OffsetDateTime trialEnd = subscription.getTrialEndDate();
        SubscriptionService.afterCommit(() -> {
            try {
                emailService.sendTrialReminder(email.get(), daysLeft, trialEnd, subscribeUrl);
            } catch (RuntimeException e) {
                // Already marked as sent: lost reminder is acceptable, a duplicate is not.
                log.warn("Trial reminder {} e-mail failed for user {}: {} - {}",
                    reminder.code, userId, e.getClass().getSimpleName(), e.getMessage());
            }
        });

        return due;
    }

    /** Which reminder is due now, if any. Never both: when 1d is due, 7d is skipped for good. */
    Optional<Reminder> dueReminder(Subscription subscription, OffsetDateTime now) {
        if (subscription.getPlanType() != PlanType.TRIAL || subscription.getStatus() != Status.ACTIVE) {
            return Optional.empty();
        }
        OffsetDateTime end = subscription.getTrialEndDate();
        if (end == null || !now.isBefore(end)) {
            return Optional.empty();
        }

        long daysLeft = daysLeft(end, now);
        if (daysLeft <= 1) {
            return subscription.getTrialReminder1dSentAt() == null
                ? Optional.of(Reminder.ONE_DAY) : Optional.empty();
        }
        if (daysLeft <= 7 && subscription.getTrialReminder7dSentAt() == null) {
            return Optional.of(Reminder.SEVEN_DAYS);
        }
        return Optional.empty();
    }

    /** Calendar days between today and the trial end, both as Sao Paulo dates. */
    static long daysLeft(OffsetDateTime trialEnd, OffsetDateTime now) {
        LocalDate today = now.atZoneSameInstant(SAO_PAULO).toLocalDate();
        LocalDate endDate = trialEnd.atZoneSameInstant(SAO_PAULO).toLocalDate();
        return ChronoUnit.DAYS.between(today, endDate);
    }

    private static String stripTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
