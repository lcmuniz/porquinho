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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pre-Epic 10: I/O matrix of the retention job. Clock fixed at the 09:00 Sao Paulo run (12:00 UTC) of
 * 2026-11-23. The e-mail is sent before the mark, which is written only when the send was accepted.
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
        lenient().when(emailService.isConfigured()).thenReturn(true);
        lenient().when(emailService.sendRetentionWarning(anyString(), anyLong(), any(), anyString(), anyString()))
            .thenReturn(true);
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

    // --- Story 10.5, RETENTION_STALLED

    @Test
    void sevenDayWarningIsDueFromMidnightAndLateAfterTheNextRunPlusGrace() {
        Subscription s = trialEndedAt(NOW.minusDays(23)); // purge 30/11 09:00 Sao Paulo
        when(subscriptionRepository.findRetentionCandidates(any())).thenReturn(List.of(s));

        assertThat(RetentionService.dueSince(s)).isEqualTo(OffsetDateTime.parse("2026-11-23T00:00:00-03:00"));
        assertThat(service.stalledCount(NOW)).isZero();
        // the 09:00 run of the 24th also had its chance; 1 h of grace for it to finish
        assertThat(service.stalledCount(NOW.plusHours(25))).isZero();
        assertThat(service.stalledCount(NOW.plusHours(25).plusMinutes(1))).isOne();
    }

    @Test
    void oneDayWarningIsDueTheDayBeforeTheEffectivePurge() {
        Subscription s = trialEndedAt(NOW.minusDays(29));
        s.setRetentionWarning7dSentAt(NOW.minusDays(6)); // effective purge 24/11 09:00 Sao Paulo
        assertThat(RetentionService.dueSince(s)).isEqualTo(OffsetDateTime.parse("2026-11-23T00:00:00-03:00"));
    }

    @Test
    void purgeAWhileAfterTheRunIsDoneOnlyByTheNextDayRunSoItIsNotLateYet() {
        Subscription s = trialEndedAt(NOW.minusDays(31));
        s.setRetentionWarning7dSentAt(NOW.minusDays(8));
        s.setRetentionWarning1dSentAt(NOW.minusDays(1).plusSeconds(5)); // purge due 09:00:05 today
        when(subscriptionRepository.findRetentionCandidates(any())).thenReturn(List.of(s));

        assertThat(RetentionService.dueSince(s)).isEqualTo(NOW.plusSeconds(5));
        assertThat(RetentionService.firstRunAtOrAfter(NOW.plusSeconds(5))).isEqualTo(NOW.plusDays(1));
        assertThat(service.stalledCount(NOW.plusDays(2))).isZero();
        assertThat(service.stalledCount(NOW.plusDays(2).plusHours(2))).isOne();
    }

    @Test
    void stalledSkipsAccessGoneUsersAndPendingDeletionsAndRowsWithoutEnd() {
        // PAYMENT_FAILED keeps access, whatever the dates
        Subscription withAccess = trialEndedAt(NOW.minusDays(25));
        withAccess.setStatus(Status.PAYMENT_FAILED);
        Subscription gone = trialEndedAt(NOW.minusDays(25));
        when(userRepository.findById(gone.getUserId())).thenReturn(Optional.empty());
        Subscription pending = trialEndedAt(NOW.minusDays(25));
        when(accountDeletionRepository.findByUserIdAndStatus(pending.getUserId(), DeletionStatus.PENDING_PURGE))
            .thenReturn(Optional.of(mock(AccountDeletion.class)));
        Subscription late = trialEndedAt(NOW.minusDays(25));
        when(subscriptionRepository.findRetentionCandidates(any())).thenReturn(List.of(withAccess, gone, pending, late));

        assertThat(service.stalledCount(NOW)).isOne();
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
    void refusedEmailWritesNothingAndIsRetriedNextRun() {
        Subscription s = trialEndedAt(NOW.minusDays(23));
        when(subscriptionRepository.findRetentionCandidates(any())).thenReturn(List.of(s));
        when(emailService.sendRetentionWarning(anyString(), anyLong(), any(), anyString(), anyString()))
            .thenReturn(false);

        service.runRetention();  // counted as a failure, not thrown

        assertThat(s.getRetentionWarning7dSentAt()).isNull();
        verify(subscriptionRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
        // Same warning due again on the next run
        assertThat(service.dueAction(s, NOW.plusDays(1))).contains(Action.WARN_7D);
    }

    @Test
    void refusedOneDayWarningNeverLetsThePurgeGoAhead() {
        Subscription s = trialEndedAt(NOW.minusDays(29));
        s.setRetentionWarning7dSentAt(NOW.minusDays(6));
        when(emailService.sendRetentionWarning(anyString(), anyLong(), any(), anyString(), anyString()))
            .thenReturn(false);

        assertThatThrownBy(() -> service.processRetention(s.getUserId())).isInstanceOf(IllegalStateException.class);

        assertThat(s.getRetentionWarning1dSentAt()).isNull();
        verifyNoInteractions(auditLogService, accountDeletionService);
        assertThat(service.dueAction(s, NOW.plusDays(5))).contains(Action.WARN_1D);
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
        verify(emailService).isConfigured();
        verifyNoMoreInteractions(emailService);
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
    void longOutageWithoutSevenDayMarkSendsSevenDayWarningAndPostponesThePurge() {
        Subscription s = trialEndedAt(NOW.minusDays(35));

        assertThat(service.processRetention(s.getUserId())).get()
            .extracting(RetentionService.Outcome::action).isEqualTo(Action.WARN_7D);

        assertThat(s.getRetentionWarning7dSentAt()).isEqualTo(NOW);
        assertThat(s.getRetentionWarning1dSentAt()).isNull();
        verifyNoInteractions(accountDeletionService);
        // The purge date is long past: the e-mail announces the postponed one, 7 days from now
        verify(emailService).sendRetentionWarning(EMAIL, 7, NOW.plusDays(7),
            "https://app.example.com/assinatura", "https://app.example.com/auth/data-export");
        assertThat(service.dueAction(s, NOW.plusDays(5))).isEmpty();
        assertThat(service.dueAction(s, NOW.plusDays(6))).contains(Action.WARN_1D);
    }

    @Test
    void lateSevenDayWarningAnnouncesNowPlusSevenDays() {
        Subscription s = trialEndedAt(NOW.minusDays(27));  // purgeAt in 3 days

        assertThat(service.processRetention(s.getUserId())).get()
            .extracting(RetentionService.Outcome::action).isEqualTo(Action.WARN_7D);

        verify(emailService).sendRetentionWarning(eq(EMAIL), eq(7L), eq(NOW.plusDays(7)), anyString(), anyString());
        // The 1-day warning comes the day before the announced date, not before the original purgeAt
        assertThat(service.dueAction(s, NOW.plusDays(2))).isEmpty();
        assertThat(service.dueAction(s, NOW.plusDays(6))).contains(Action.WARN_1D);
    }

    @Test
    void oneDayWarningAfterLateSevenDayUsesTheEffectivePurge() {
        Subscription s = trialEndedAt(NOW.minusDays(31));  // original purgeAt already past
        s.setRetentionWarning7dSentAt(NOW.minusDays(6));

        assertThat(service.processRetention(s.getUserId())).get()
            .extracting(RetentionService.Outcome::action).isEqualTo(Action.WARN_1D);

        assertThat(s.getRetentionWarning1dSentAt()).isEqualTo(NOW);
        // 7-day mark + 7 days = tomorrow, already >= now + 24 h
        verify(emailService).sendRetentionWarning(eq(EMAIL), eq(1L), eq(NOW.plusDays(1)), anyString(), anyString());
    }

    @Test
    void lateOneDayWarningAnnouncesNowPlus24h() {
        Subscription s = trialEndedAt(NOW.minusDays(31));
        s.setRetentionWarning7dSentAt(NOW.minusDays(10));  // effective purge = NOW - 1 day, already past

        assertThat(service.processRetention(s.getUserId())).get()
            .extracting(RetentionService.Outcome::action).isEqualTo(Action.WARN_1D);

        verify(emailService).sendRetentionWarning(eq(EMAIL), eq(1L), eq(NOW.plusHours(24)), anyString(), anyString());
    }

    @Test
    void purgeWaitsForTheEffectiveDate() {
        Subscription s = trialEndedAt(NOW.minusDays(31));
        s.setRetentionWarning7dSentAt(NOW.minusDays(6));
        s.setRetentionWarning1dSentAt(NOW.minusHours(25));

        assertThat(service.dueAction(s, NOW)).isEmpty();
        assertThat(service.dueAction(s, NOW.plusDays(1))).contains(Action.PURGE);
    }

    @Test
    void oldRowWithOnlyTheOneDayMarkPurgesWithoutANewSevenDayWarning() {
        Subscription s = trialEndedAt(NOW.minusDays(31));
        s.setRetentionWarning1dSentAt(NOW.minusHours(23));

        assertThat(service.dueAction(s, NOW)).isEmpty();
        assertThat(service.dueAction(s, NOW.plusHours(1))).contains(Action.PURGE);
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

    @Test
    void withoutSmtpNoWarningIsMarkedSoNobodyIsPurgedUnwarned() {
        when(emailService.isConfigured()).thenReturn(false);
        Subscription sevenDays = trialEndedAt(NOW.minusDays(23));
        Subscription oneDay = trialEndedAt(NOW.minusDays(29));
        oneDay.setRetentionWarning7dSentAt(NOW.minusDays(6));

        assertThat(service.processRetention(sevenDays.getUserId())).isEmpty();
        assertThat(service.processRetention(oneDay.getUserId())).isEmpty();

        assertThat(sevenDays.getRetentionWarning7dSentAt()).isNull();
        assertThat(oneDay.getRetentionWarning1dSentAt()).isNull();
        verify(subscriptionRepository, never()).save(any());
        verifyNoInteractions(auditLogService, accountDeletionService);
        verify(emailService, never()).sendRetentionWarning(anyString(), anyLong(), any(), anyString(), anyString());
        // Without the 1-day mark the purge never becomes due, even past day 30
        assertThat(service.dueAction(oneDay, NOW.plusDays(5))).contains(Action.WARN_1D);
    }

    @Test
    void withoutSmtpAOneDayWarningSentBeforeStillPurges() {
        // The 1-day mark was recorded while SMTP worked: the user was warned, so the purge goes on
        lenient().when(emailService.isConfigured()).thenReturn(false);
        Subscription s = trialEndedAt(NOW.minusDays(30).minusHours(1));
        s.setRetentionWarning1dSentAt(NOW.minusHours(25));
        AccountDeletion deletion = new AccountDeletion(s.getUserId(), NOW, null);
        when(accountDeletionService.createRetentionDeletion(s.getUserId())).thenReturn(deletion);

        assertThat(service.processRetention(s.getUserId())).get()
            .extracting(RetentionService.Outcome::action).isEqualTo(Action.PURGE);
    }

    @Test
    void runWithoutEmailPostponesWarningsButStillPurgesWarnedUsers() {
        when(emailService.isConfigured()).thenReturn(false);
        Subscription toWarn = trialEndedAt(NOW.minusDays(23));
        Subscription warned = trialEndedAt(NOW.minusDays(30).minusHours(1));
        warned.setRetentionWarning1dSentAt(NOW.minusHours(25));
        AccountDeletion deletion = new AccountDeletion(warned.getUserId(), NOW, null);
        when(accountDeletionService.createRetentionDeletion(warned.getUserId())).thenReturn(deletion);
        when(subscriptionRepository.findRetentionCandidates(any())).thenReturn(List.of(toWarn, warned));

        service.runRetention();

        verify(subscriptionRepository, never()).findByUserIdForUpdate(toWarn.getUserId());
        assertThat(toWarn.getRetentionWarning7dSentAt()).isNull();
        verify(accountDeletionService).purgeUser(deletion);
    }
}
