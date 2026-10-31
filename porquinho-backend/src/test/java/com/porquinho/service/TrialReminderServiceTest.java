package com.porquinho.service;

import com.porquinho.entity.Subscription;
import com.porquinho.entity.Subscription.PlanType;
import com.porquinho.entity.Subscription.Status;
import com.porquinho.entity.User;
import com.porquinho.entity.User.AuthProvider;
import com.porquinho.repository.SubscriptionRepository;
import com.porquinho.repository.UserRepository;
import com.porquinho.service.TrialReminderService.Reminder;
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
 * Story 2.3: I/O matrix of the trial reminder job. Clock fixed at the 09:00 Sao Paulo run
 * (12:00 UTC) of 2026-11-23; no transaction is active, so the "after commit" e-mail runs inline.
 */
@ExtendWith(MockitoExtension.class)
class TrialReminderServiceTest {

    /** 23/11/2026 09:00 in Sao Paulo (UTC-3). */
    static final OffsetDateTime NOW = OffsetDateTime.of(2026, 11, 23, 12, 0, 0, 0, ZoneOffset.UTC);
    static final String EMAIL = "user@example.com";
    static final String SUBSCRIBE_URL = "https://app.example.com/assinatura";

    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private EmailService emailService;

    private TrialReminderService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.toInstant(), ZoneOffset.UTC);
        service = new TrialReminderService(subscriptionRepository, userRepository, auditLogService,
            emailService, clock, "https://app.example.com/", null);
        ReflectionTestUtils.setField(service, "self", service);
    }

    private Subscription trial(OffsetDateTime end) {
        Subscription s = new Subscription(UUID.randomUUID(), PlanType.TRIAL, Status.ACTIVE, end.minusDays(30), end);
        lenient().when(subscriptionRepository.findByUserIdForUpdate(s.getUserId())).thenReturn(Optional.of(s));
        lenient().when(userRepository.findById(s.getUserId()))
            .thenReturn(Optional.of(new User(EMAIL, AuthProvider.EMAIL, null)));
        return s;
    }

    /** Trial ends on 30/11 10:00 Sao Paulo: 7 calendar days from 23/11 (Design Notes example). */
    private static final OffsetDateTime END_IN_7 = OffsetDateTime.of(2026, 11, 30, 13, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void sevenDaysLeftSendsSevenDayReminderMarksAndAudits() {
        Subscription s = trial(END_IN_7);

        assertThat(service.processReminder(s.getUserId())).contains(Reminder.SEVEN_DAYS);

        assertThat(s.getTrialReminder7dSentAt()).isEqualTo(NOW);
        assertThat(s.getTrialReminder1dSentAt()).isNull();
        verify(subscriptionRepository).save(s);
        verify(auditLogService).log("trial_reminder_sent", s.getUserId(), null, "{\"reminder\":\"7d\"}");
        verify(emailService).sendTrialReminder(EMAIL, 7, END_IN_7, SUBSCRIBE_URL);
    }

    @Test
    void fiveDaysLeftAfterMissedRunsStillSendsSevenDayReminder() {
        Subscription s = trial(END_IN_7.minusDays(2));

        assertThat(service.processReminder(s.getUserId())).contains(Reminder.SEVEN_DAYS);

        verify(emailService).sendTrialReminder(EMAIL, 5, END_IN_7.minusDays(2), SUBSCRIBE_URL);
    }

    @Test
    void sevenDaysLeftAlreadySentDoesNothing() {
        Subscription s = trial(END_IN_7);
        s.setTrialReminder7dSentAt(NOW.minusHours(1));

        assertThat(service.processReminder(s.getUserId())).isEmpty();

        verifyNoInteractions(emailService, auditLogService);
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void oneDayLeftSendsOneDayReminder() {
        // 24/11 10:00 Sao Paulo: tomorrow
        OffsetDateTime end = OffsetDateTime.of(2026, 11, 24, 13, 0, 0, 0, ZoneOffset.UTC);
        Subscription s = trial(end);
        s.setTrialReminder7dSentAt(NOW.minusDays(6));

        assertThat(service.processReminder(s.getUserId())).contains(Reminder.ONE_DAY);

        assertThat(s.getTrialReminder1dSentAt()).isEqualTo(NOW);
        verify(auditLogService).log("trial_reminder_sent", s.getUserId(), null, "{\"reminder\":\"1d\"}");
        verify(emailService).sendTrialReminder(EMAIL, 1, end, SUBSCRIBE_URL);
    }

    @Test
    void oneDayLeftWithoutSevenDaySendsOnlyOneDay() {
        OffsetDateTime end = OffsetDateTime.of(2026, 11, 24, 13, 0, 0, 0, ZoneOffset.UTC);
        Subscription s = trial(end);

        assertThat(service.processReminder(s.getUserId())).contains(Reminder.ONE_DAY);

        assertThat(s.getTrialReminder7dSentAt()).isNull();
        verify(emailService, times(1)).sendTrialReminder(anyString(), anyLong(), any(), anyString());
        verify(auditLogService, times(1)).log(eq("trial_reminder_sent"), any(), any(), eq("{\"reminder\":\"1d\"}"));
    }

    @Test
    void endsLaterTodayStillSendsOneDayReminder() {
        // 23/11 20:00 Sao Paulo: today, trial still running
        OffsetDateTime end = OffsetDateTime.of(2026, 11, 23, 23, 0, 0, 0, ZoneOffset.UTC);
        Subscription s = trial(end);

        assertThat(service.processReminder(s.getUserId())).contains(Reminder.ONE_DAY);
        verify(emailService).sendTrialReminder(EMAIL, 0, end, SUBSCRIBE_URL);
    }

    @Test
    void oneDayAlreadySentDoesNothing() {
        Subscription s = trial(OffsetDateTime.of(2026, 11, 24, 13, 0, 0, 0, ZoneOffset.UTC));
        s.setTrialReminder1dSentAt(NOW.minusMinutes(5));

        assertThat(service.processReminder(s.getUserId())).isEmpty();
        verifyNoInteractions(emailService, auditLogService);
    }

    @Test
    void monthlyPlanGetsNothing() {
        Subscription s = trial(END_IN_7);
        s.setPlanType(PlanType.MONTHLY);

        assertThat(service.processReminder(s.getUserId())).isEmpty();
        verifyNoInteractions(emailService, auditLogService);
    }

    @Test
    void expiredTrialGetsNothing() {
        Subscription s = trial(NOW.minusMinutes(1));

        assertThat(service.processReminder(s.getUserId())).isEmpty();
        verifyNoInteractions(emailService, auditLogService);
    }

    @Test
    void eightDaysLeftIsTooEarly() {
        Subscription s = trial(END_IN_7.plusDays(1));

        assertThat(service.processReminder(s.getUserId())).isEmpty();
        verifyNoInteractions(emailService, auditLogService);
    }

    @Test
    void softDeletedUserIsSkippedWithoutMarkOrAudit() {
        // Pending LGPD purge: subscription still TRIAL/ACTIVE, but @Where hides the user
        Subscription s = trial(END_IN_7);
        when(userRepository.findById(s.getUserId())).thenReturn(Optional.empty());

        assertThat(service.processReminder(s.getUserId())).isEmpty();

        assertThat(s.getTrialReminder7dSentAt()).isNull();
        verify(subscriptionRepository, never()).save(any());
        verifyNoInteractions(emailService, auditLogService);
    }

    @Test
    void daysAreSaoPauloCalendarDays() {
        // 01/12 01:00 UTC is still 30/11 22:00 in Sao Paulo -> 7 days, not 8
        OffsetDateTime end = OffsetDateTime.of(2026, 12, 1, 1, 0, 0, 0, ZoneOffset.UTC);
        assertThat(TrialReminderService.daysLeft(end, NOW)).isEqualTo(7);
    }

    @Test
    void jobQueriesReminderWindowsAndRunningTwiceSendsOnce() {
        Subscription s = trial(END_IN_7);
        when(subscriptionRepository.findTrialReminderCandidates(NOW, NOW.plusDays(8), NOW.plusDays(2))).thenReturn(List.of(s));

        service.sendTrialReminders();
        service.sendTrialReminders();

        verify(emailService, times(1)).sendTrialReminder(EMAIL, 7, END_IN_7, SUBSCRIBE_URL);
        verify(auditLogService, times(1)).log(anyString(), any(), any(), anyString());
    }

    @Test
    void emailFailureForOneUserDoesNotStopOthersAndKeepsMark() {
        Subscription first = trial(END_IN_7);
        Subscription second = trial(END_IN_7);
        when(subscriptionRepository.findTrialReminderCandidates(any(), any(), any())).thenReturn(List.of(first, second));
        doThrow(new RuntimeException("SMTP down")).doNothing()
            .when(emailService).sendTrialReminder(anyString(), anyLong(), any(), anyString());

        service.sendTrialReminders();

        verify(emailService, times(2)).sendTrialReminder(anyString(), anyLong(), any(), anyString());
        assertThat(first.getTrialReminder7dSentAt()).isEqualTo(NOW);
        assertThat(second.getTrialReminder7dSentAt()).isEqualTo(NOW);
    }

    @Test
    void failureProcessingOneUserDoesNotStopOthers() {
        Subscription broken = trial(END_IN_7);
        Subscription ok = trial(END_IN_7);
        when(subscriptionRepository.findTrialReminderCandidates(any(), any(), any())).thenReturn(List.of(broken, ok));
        when(subscriptionRepository.findByUserIdForUpdate(broken.getUserId())).thenThrow(new RuntimeException("db"));

        service.sendTrialReminders();

        assertThat(ok.getTrialReminder7dSentAt()).isEqualTo(NOW);
        verify(emailService, times(1)).sendTrialReminder(anyString(), anyLong(), any(), anyString());
    }
}
