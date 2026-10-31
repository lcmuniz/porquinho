package com.porquinho.service;

import com.porquinho.entity.AuditLog;
import com.porquinho.entity.Subscription;
import com.porquinho.entity.Subscription.PlanType;
import com.porquinho.entity.Subscription.Status;
import com.porquinho.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-31T12:00:00Z");
    private static final OffsetDateTime NOW_UTC = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private AuditLogService auditLogService;

    private SubscriptionService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new SubscriptionService(subscriptionRepository, auditLogService, Clock.fixed(NOW, ZoneOffset.UTC));
        userId = UUID.randomUUID();
    }

    private Subscription trialEndingAt(OffsetDateTime end) {
        return new Subscription(userId, PlanType.TRIAL, Status.ACTIVE, end.minusDays(30), end);
    }

    @Test
    void startTrialShouldCreateActiveTrialOf30DaysAndAudit() {
        when(subscriptionRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogService.log(anyString(), any(), any(), anyString())).thenReturn(new AuditLog());

        Subscription result = service.startTrial(userId, "10.0.0.1");

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        Subscription saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getPlanType()).isEqualTo(PlanType.TRIAL);
        assertThat(saved.getStatus()).isEqualTo(Status.ACTIVE);
        assertThat(saved.getTrialStartDate()).isEqualTo(NOW_UTC);
        assertThat(saved.getTrialEndDate()).isEqualTo(NOW_UTC.plusDays(SubscriptionService.TRIAL_DAYS));
        assertThat(result).isSameAs(saved);

        verify(auditLogService).log(eq("trial_started"), eq(userId), eq("10.0.0.1"),
            eq("{\"trial_end_date\":\"" + NOW_UTC.plusDays(30) + "\"}"));
    }

    @Test
    void startTrialShouldBeIdempotentAndNeverRestartTrial() {
        Subscription existing = trialEndingAt(NOW_UTC.minusDays(5)); // already expired
        when(subscriptionRepository.findByUserId(userId)).thenReturn(Optional.of(existing));

        Subscription result = service.startTrial(userId, "10.0.0.1");

        assertThat(result).isSameAs(existing);
        assertThat(result.getTrialEndDate()).isEqualTo(NOW_UTC.minusDays(5));
        verify(subscriptionRepository, never()).save(any());
        verify(auditLogService, never()).log(anyString(), any(), any(), any());
    }

    @Test
    void hasAccessShouldBeTrueBeforeTrialEnd() {
        when(subscriptionRepository.findByUserId(userId))
            .thenReturn(Optional.of(trialEndingAt(NOW_UTC.plusSeconds(1))));

        assertThat(service.hasAccess(userId)).isTrue();
    }

    @Test
    void hasAccessShouldBeFalseAtAndAfterTrialEnd() {
        assertThat(service.hasAccess(trialEndingAt(NOW_UTC))).isFalse();
        assertThat(service.hasAccess(trialEndingAt(NOW_UTC.minusDays(1)))).isFalse();
    }

    @Test
    void hasAccessShouldBeFalseWithoutSubscription() {
        when(subscriptionRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThat(service.hasAccess(userId)).isFalse();
    }

    @Test
    void hasAccessShouldBeFalseWhenStatusNotActive() {
        Subscription cancelled = trialEndingAt(NOW_UTC.plusDays(10));
        cancelled.setStatus(Status.CANCELLED);
        Subscription expired = trialEndingAt(NOW_UTC.plusDays(10));
        expired.setStatus(Status.EXPIRED);

        assertThat(service.hasAccess(cancelled)).isFalse();
        assertThat(service.hasAccess(expired)).isFalse();
    }

    @Test
    void hasAccessShouldBeTrueForActiveMonthlyRegardlessOfTrialDates() {
        Subscription monthly = trialEndingAt(NOW_UTC.minusDays(60));
        monthly.setPlanType(PlanType.MONTHLY);

        assertThat(service.hasAccess(monthly)).isTrue();
    }

    @Test
    void daysRemainingShouldRoundUpPartialDays() {
        assertThat(service.daysRemaining(trialEndingAt(NOW_UTC.plusDays(29).plusHours(1)))).isEqualTo(30);
        assertThat(service.daysRemaining(trialEndingAt(NOW_UTC.plusDays(30)))).isEqualTo(30);
        assertThat(service.daysRemaining(trialEndingAt(NOW_UTC.plusHours(1)))).isEqualTo(1);
    }

    @Test
    void daysRemainingShouldBeZeroForMonthlyPlan() {
        Subscription monthly = trialEndingAt(NOW_UTC.plusDays(10));
        monthly.setPlanType(PlanType.MONTHLY);

        assertThat(service.daysRemaining(monthly)).isZero();
    }

    @Test
    void daysRemainingShouldBeZeroWhenExpired() {
        assertThat(service.daysRemaining(trialEndingAt(NOW_UTC))).isZero();
        assertThat(service.daysRemaining(trialEndingAt(NOW_UTC.minusDays(3)))).isZero();
    }
}
