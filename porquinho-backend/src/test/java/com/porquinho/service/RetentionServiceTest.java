package com.porquinho.service;

import com.porquinho.entity.AccountDeletion;
import com.porquinho.entity.AccountDeletion.DeletionStatus;
import com.porquinho.entity.Subscription;
import com.porquinho.entity.Subscription.PlanType;
import com.porquinho.entity.Subscription.Status;
import com.porquinho.entity.User;
import com.porquinho.entity.User.AuthProvider;
import com.porquinho.repository.AccountDeletionRepository;
import com.porquinho.repository.SubscriptionRepository;
import com.porquinho.repository.UserRepository;
import com.porquinho.service.RetentionService.Action;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pre-Epic 10: I/O matrix of the retention job. Clock fixed at the 09:00 Sao Paulo run (12:00 UTC) of
 * 2026-11-23; no transaction is active, so the "after commit" e-mail runs inline.
 */
@ExtendWith(MockitoExtension.class)
class RetentionServiceTest {

    static final OffsetDateTime NOW = OffsetDateTime.of(2026, 11, 23, 12, 0, 0, 0, ZoneOffset.UTC);
    static final String EMAIL = "user@example.com";

    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AccountDeletionRepository accountDeletionRepository;
    @Mock
    private AccountDeletionService accountDeletionService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private EmailService emailService;

    private RetentionService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.toInstant(), ZoneOffset.UTC);
        SubscriptionService subscriptionService = new SubscriptionService(subscriptionRepository, auditLogService,
            clock, null, userRepository, emailService, accountDeletionRepository);
        service = new RetentionService(subscriptionRepository, userRepository, accountDeletionRepository,
            accountDeletionService, subscriptionService, auditLogService, emailService, clock,
            "https://app.example.com/", null);
        ReflectionTestUtils.setField(service, "self", service);
    }

    private Subscription stored(Subscription s) {
        lenient().when(subscriptionRepository.findByUserIdForUpdate(s.getUserId())).thenReturn(Optional.of(s));
        lenient().when(userRepository.findById(s.getUserId()))
            .thenReturn(Optional.of(new User(EMAIL, AuthProvider.EMAIL, null)));
        return s;
    }

    private Subscription trialEndedAt(OffsetDateTime end) {
        return stored(new Subscription(UUID.randomUUID(), PlanType.TRIAL, Status.ACTIVE, end.minusDays(30), end));
    }

    @Test
    void expiredTrialSevenDaysBeforePurgeGetsSevenDayWarning() {
        Subscription s = trialEndedAt(NOW.minusDays(23));

        assertThat(service.processRetention(s.getUserId())).get()
            .extracting(RetentionService.Outcome::action).isEqualTo(Action.WARN_7D);

        assertThat(s.getRetentionWarning7dSentAt()).isEqualTo(NOW);
        assertThat(s.getRetentionWarning1dSentAt()).isNull();
        verify(auditLogService).log("retention_warning_sent", s.getUserId(), null, "{\"warning\":\"7d\"}");
        verify(emailService).sendRetentionWarning(EMAIL, 7, NOW.plusDays(7),
            "https://app.example.com/assinatura", "https://app.example.com/auth/data-export");
    }

    @Test
    void failedEmailKeepsTheMark() {
        Subscription s = trialEndedAt(NOW.minusDays(23));
        doThrow(new RuntimeException("smtp down")).when(emailService)
            .sendRetentionWarning(anyString(), anyLong(), any(), anyString(), anyString());

        assertThat(service.processRetention(s.getUserId())).isPresent();

        assertThat(s.getRetentionWarning7dSentAt()).isEqualTo(NOW);
        verify(subscriptionRepository).save(s);
    }

    @Test
    void dayBeforePurgeGetsOneDayWarning() {
        Subscription s = trialEndedAt(NOW.minusDays(29));
        s.setRetentionWarning7dSentAt(NOW.minusDays(6));

        assertThat(service.processRetention(s.getUserId())).get()
            .extracting(RetentionService.Outcome::action).isEqualTo(Action.WARN_1D);

        assertThat(s.getRetentionWarning1dSentAt()).isEqualTo(NOW);
        verify(emailService).sendRetentionWarning(eq(EMAIL), eq(1L), any(), anyString(), anyString());
    }

    @Test
    void dayThirtyWithOneDaySentOver24hAgoPurges() {
        Subscription s = trialEndedAt(NOW.minusDays(30).minusHours(1));
        s.setRetentionWarning7dSentAt(NOW.minusDays(7));
        s.setRetentionWarning1dSentAt(NOW.minusHours(25));
        AccountDeletion deletion = new AccountDeletion(s.getUserId(), NOW, null);
        when(accountDeletionService.createRetentionDeletion(s.getUserId())).thenReturn(deletion);
        when(subscriptionRepository.findRetentionCandidates(any())).thenReturn(List.of(s));

        service.runRetention();

        verify(accountDeletionService).createRetentionDeletion(s.getUserId());
        verify(accountDeletionService).purgeUser(deletion);
        verifyNoInteractions(emailService);
    }

    @Test
    void oneDaySentLessThan24hAgoDoesNotPurgeYet() {
        Subscription s = trialEndedAt(NOW.minusDays(31));
        s.setRetentionWarning1dSentAt(NOW.minusHours(23));

        assertThat(service.processRetention(s.getUserId())).isEmpty();

        verifyNoInteractions(accountDeletionService, emailService);
    }

    @Test
    void failureOfOneUserDoesNotStopTheOthers() {
        Subscription broken = trialEndedAt(NOW.minusDays(23));
        Subscription ok = trialEndedAt(NOW.minusDays(23));
        when(subscriptionRepository.findRetentionCandidates(any())).thenReturn(List.of(broken, ok));
        when(subscriptionRepository.findByUserIdForUpdate(broken.getUserId())).thenThrow(new RuntimeException("db"));

        service.runRetention();

        assertThat(ok.getRetentionWarning7dSentAt()).isEqualTo(NOW);
    }

    @Test
    void jobStoppedForDaysSendsOnlyOneDayWarningThisRun() {
        Subscription s = trialEndedAt(NOW.minusDays(35));

        assertThat(service.processRetention(s.getUserId())).get()
            .extracting(RetentionService.Outcome::action).isEqualTo(Action.WARN_1D);

        assertThat(s.getRetentionWarning1dSentAt()).isEqualTo(NOW);
        assertThat(s.getRetentionWarning7dSentAt()).isNull();
        verifyNoInteractions(accountDeletionService);
        // The purge date is long past: the e-mail announces the real one, 24 h from now (tomorrow)
        verify(emailService).sendRetentionWarning(EMAIL, 1, NOW.plusHours(24),
            "https://app.example.com/assinatura", "https://app.example.com/auth/data-export");
        // The next run, less than 24 h later, still does not purge
        assertThat(service.dueAction(s, NOW.plusHours(23))).isEmpty();
        assertThat(service.dueAction(s, NOW.plusHours(24))).contains(Action.PURGE);
    }

    @Test
    void subscribedBeforePurgeDoesNothing() {
        Subscription s = stored(new Subscription(UUID.randomUUID(), PlanType.MONTHLY, Status.ACTIVE, null, null));
        s.setRetentionWarning1dSentAt(NOW.minusDays(3));

        assertThat(service.processRetention(s.getUserId())).isEmpty();

        verifyNoInteractions(accountDeletionService, emailService);
    }

    @Test
    void cancelledWithinPaidPeriodDoesNothing() {
        Subscription s = stored(new Subscription(UUID.randomUUID(), PlanType.MONTHLY, Status.CANCELLED, null, null));
        s.setNextBillingDate(NOW.plusDays(10));

        assertThat(service.processRetention(s.getUserId())).isEmpty();

        verifyNoInteractions(emailService, auditLogService);
    }

    @Test
    void cancelledPaidPeriodOverCountsFromNextBillingDate() {
        Subscription s = stored(new Subscription(UUID.randomUUID(), PlanType.MONTHLY, Status.CANCELLED, null, null));
        s.setNextBillingDate(NOW.minusDays(23));

        assertThat(service.dueAction(s, NOW)).contains(Action.WARN_7D);
    }

    @Test
    void accessEndedAtWinsOverTrialEnd() {
        Subscription s = trialEndedAt(NOW.minusDays(60));
        s.setStatus(Status.EXPIRED);
        s.setAccessEndedAt(NOW.minusDays(10)); // deploy backfill: 30 days from the deploy

        assertThat(service.dueAction(s, NOW)).isEmpty();
    }

    @Test
    void softDeletedUserIsIgnored() {
        Subscription s = trialEndedAt(NOW.minusDays(31));
        s.setRetentionWarning1dSentAt(NOW.minusDays(2));
        when(userRepository.findById(s.getUserId())).thenReturn(Optional.empty());

        assertThat(service.processRetention(s.getUserId())).isEmpty();

        verifyNoInteractions(accountDeletionService);
    }

    @Test
    void pendingDeletionIsIgnored() {
        Subscription s = trialEndedAt(NOW.minusDays(31));
        s.setRetentionWarning1dSentAt(NOW.minusDays(2));
        when(accountDeletionRepository.findByUserIdAndStatus(s.getUserId(), DeletionStatus.PENDING_PURGE))
            .thenReturn(Optional.of(new AccountDeletion()));

        assertThat(service.processRetention(s.getUserId())).isEmpty();

        verifyNoInteractions(accountDeletionService);
    }
}
