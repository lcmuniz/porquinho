package com.porquinho.service;

import com.porquinho.entity.AuditLog;
import com.porquinho.entity.Subscription;
import com.porquinho.entity.User;
import com.porquinho.exception.ConflictException;
import com.porquinho.exception.PaymentProviderUnavailableException;
import com.porquinho.repository.UserRepository;
import com.porquinho.entity.Subscription.PlanType;
import com.porquinho.entity.Subscription.Status;
import com.porquinho.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    @Mock
    private StripeBillingService stripeBillingService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailService emailService;

    private SubscriptionService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new SubscriptionService(subscriptionRepository, auditLogService, Clock.fixed(NOW, ZoneOffset.UTC),
            stripeBillingService, userRepository, emailService);
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

    // ---- Story 2.2: checkout ----

    private User userWithEmail(String email) {
        User user = new User();
        user.setEmail(email);
        return user;
    }

    @Test
    void createCheckoutDuringTrialDefersFirstChargeToTrialEnd() {
        OffsetDateTime trialEnd = NOW_UTC.plusDays(10);
        when(subscriptionRepository.findByUserId(userId)).thenReturn(Optional.of(trialEndingAt(trialEnd)));
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithEmail("ana@example.com")));
        when(stripeBillingService.createCheckoutSession(userId, "ana@example.com", trialEnd.toInstant()))
            .thenReturn("https://checkout.stripe.com/c/pay/cs_test_1");

        assertThat(service.createCheckout(userId)).isEqualTo("https://checkout.stripe.com/c/pay/cs_test_1");
    }

    @Test
    void createCheckoutWithExpiredTrialChargesImmediately() {
        when(subscriptionRepository.findByUserId(userId)).thenReturn(Optional.of(trialEndingAt(NOW_UTC.minusDays(1))));
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithEmail("ana@example.com")));
        when(stripeBillingService.createCheckoutSession(eq(userId), eq("ana@example.com"), isNull()))
            .thenReturn("https://checkout.stripe.com/x");

        assertThat(service.createCheckout(userId)).isEqualTo("https://checkout.stripe.com/x");
    }

    @Test
    void firstChargeIsAtLeastStripeMinimumWhenTrialEndsSoon() {
        Subscription endingSoon = trialEndingAt(NOW_UTC.plusHours(3));

        assertThat(service.firstChargeAt(endingSoon)).isEqualTo(NOW.plus(SubscriptionService.MIN_STRIPE_TRIAL));
    }

    @Test
    void createCheckoutWhenAlreadyMonthlyActiveThrowsConflict() {
        Subscription monthly = trialEndingAt(NOW_UTC.minusDays(1));
        monthly.setPlanType(PlanType.MONTHLY);
        when(subscriptionRepository.findByUserId(userId)).thenReturn(Optional.of(monthly));

        assertThatThrownBy(() -> service.createCheckout(userId)).isInstanceOf(ConflictException.class);
        verify(stripeBillingService, never()).createCheckoutSession(any(), any(), any());
    }

    @Test
    void createCheckoutStripeFailureLeavesTrialUntouched() {
        Subscription trial = trialEndingAt(NOW_UTC.plusDays(10));
        when(subscriptionRepository.findByUserId(userId)).thenReturn(Optional.of(trial));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        when(stripeBillingService.createCheckoutSession(eq(userId), isNull(), any()))
            .thenThrow(new PaymentProviderUnavailableException("down"));

        assertThatThrownBy(() -> service.createCheckout(userId)).isInstanceOf(PaymentProviderUnavailableException.class);
        assertThat(trial.getPlanType()).isEqualTo(PlanType.TRIAL);
        verify(subscriptionRepository, never()).save(any());
    }

    // ---- Story 2.2: activateMonthly ----

    @Test
    void activateMonthlySwitchesPlanSavesStripeIdsAuditsAndEmails() {
        Subscription trial = trialEndingAt(NOW_UTC.plusDays(10));
        OffsetDateTime nextBilling = NOW_UTC.plusDays(10);
        when(subscriptionRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(trial));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithEmail("ana@example.com")));

        Subscription result = service.activateMonthly(userId, "cus_1", "sub_1", "pm_1", nextBilling);

        assertThat(result.getPlanType()).isEqualTo(PlanType.MONTHLY);
        assertThat(result.getStatus()).isEqualTo(Status.ACTIVE);
        assertThat(result.getStripeCustomerId()).isEqualTo("cus_1");
        assertThat(result.getStripeSubscriptionId()).isEqualTo("sub_1");
        assertThat(result.getPaymentMethodId()).isEqualTo("pm_1");
        assertThat(result.getNextBillingDate()).isEqualTo(nextBilling);
        verify(auditLogService).log(eq("subscription_activated"), eq(userId), isNull(), anyString());
        verify(emailService).sendSubscriptionActivated("ana@example.com", nextBilling);
    }

    @Test
    void activateMonthlyIsIdempotentForSameStripeSubscription() {
        Subscription trial = trialEndingAt(NOW_UTC.plusDays(10));
        OffsetDateTime nextBilling = NOW_UTC.plusDays(10);
        when(subscriptionRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(trial));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithEmail("ana@example.com")));

        service.activateMonthly(userId, "cus_1", "sub_1", "pm_1", nextBilling);
        Subscription second = service.activateMonthly(userId, "cus_1", "sub_1", "pm_1", nextBilling);

        assertThat(second.getPlanType()).isEqualTo(PlanType.MONTHLY);
        assertThat(second.getStripeSubscriptionId()).isEqualTo("sub_1");
        verify(subscriptionRepository, times(1)).save(any());
        verify(auditLogService, times(1)).log(eq("subscription_activated"), any(), any(), anyString());
        verify(emailService, times(1)).sendSubscriptionActivated(anyString(), any());
    }

    @Test
    void createCheckoutForCancelledMonthlyIsNotAConflict() {
        Subscription cancelled = trialEndingAt(NOW_UTC.minusDays(40));
        cancelled.setPlanType(PlanType.MONTHLY);
        cancelled.setStatus(Status.CANCELLED);
        when(subscriptionRepository.findByUserId(userId)).thenReturn(Optional.of(cancelled));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        when(stripeBillingService.createCheckoutSession(eq(userId), isNull(), isNull())).thenReturn("https://checkout.stripe.com/y");

        assertThat(service.createCheckout(userId)).isEqualTo("https://checkout.stripe.com/y");
    }

    @Test
    void activateMonthlyWithDifferentStripeSubscriptionKeepsRowAndCancelsDuplicate() {
        Subscription active = trialEndingAt(NOW_UTC.minusDays(5));
        active.setPlanType(PlanType.MONTHLY);
        active.setStripeCustomerId("cus_1");
        active.setStripeSubscriptionId("sub_1");
        active.setPaymentMethodId("pm_1");
        OffsetDateTime originalNext = NOW_UTC.plusDays(20);
        active.setNextBillingDate(originalNext);
        when(subscriptionRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(active));

        Subscription result = service.activateMonthly(userId, "cus_2", "sub_2", "pm_2", NOW_UTC.plusDays(30));

        assertThat(result.getStripeSubscriptionId()).isEqualTo("sub_1");
        assertThat(result.getStripeCustomerId()).isEqualTo("cus_1");
        assertThat(result.getPaymentMethodId()).isEqualTo("pm_1");
        assertThat(result.getNextBillingDate()).isEqualTo(originalNext);
        verify(stripeBillingService).cancelSubscription("sub_2");
        verify(subscriptionRepository, never()).save(any());
        verify(auditLogService, never()).log(anyString(), any(), any(), any());
        verify(emailService, never()).sendSubscriptionActivated(any(), any());
    }

    @Test
    void activateMonthlyAuditWritesJsonNullWhenNextBillingDateMissing() {
        when(subscriptionRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(trialEndingAt(NOW_UTC.plusDays(3))));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));

        service.activateMonthly(userId, "cus_1", "sub_1", "pm_1", null);

        ArgumentCaptor<String> metadata = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).log(eq("subscription_activated"), eq(userId), isNull(), metadata.capture());
        assertThat(metadata.getValue()).isEqualTo("{\"stripe_subscription_id\":\"sub_1\",\"next_billing_date\":null}");
    }

    @Test
    void activateMonthlySendsEmailOnlyAfterCommit() {
        OffsetDateTime nextBilling = NOW_UTC.plusDays(10);
        when(subscriptionRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(trialEndingAt(nextBilling)));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithEmail("ana@example.com")));

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.activateMonthly(userId, "cus_1", "sub_1", "pm_1", nextBilling);
            verify(emailService, never()).sendSubscriptionActivated(any(), any());

            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

            verify(emailService).sendSubscriptionActivated("ana@example.com", nextBilling);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // ---- Story 2.4: cancel / expire / access ----

    private Subscription monthly(Status status, OffsetDateTime nextBilling) {
        Subscription s = trialEndingAt(NOW_UTC.minusDays(40));
        s.setPlanType(PlanType.MONTHLY);
        s.setStatus(status);
        s.setStripeCustomerId("cus_1");
        s.setStripeSubscriptionId("sub_1");
        s.setPaymentMethodId("pm_1");
        s.setNextBillingDate(nextBilling);
        return s;
    }

    @Test
    void cancelActiveMonthlyAsksStripeMarksCancelledAuditsAndEmailsAfterCommit() {
        OffsetDateTime nextBilling = NOW_UTC.plusDays(12);
        Subscription active = monthly(Status.ACTIVE, nextBilling);
        when(subscriptionRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(active));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithEmail("ana@example.com")));

        TransactionSynchronizationManager.initSynchronization();
        try {
            Subscription result = service.cancel(userId, "10.0.0.1");

            assertThat(result.getStatus()).isEqualTo(Status.CANCELLED);
            assertThat(result.getPlanType()).isEqualTo(PlanType.MONTHLY);
            assertThat(result.getCancellationDate()).isEqualTo(NOW_UTC);
            assertThat(result.getNextBillingDate()).isEqualTo(nextBilling);
            assertThat(result.getStripeSubscriptionId()).isEqualTo("sub_1");
            verify(stripeBillingService).cancelAtPeriodEnd("sub_1");
            verify(stripeBillingService, never()).cancelSubscription(anyString());
            ArgumentCaptor<String> metadata = ArgumentCaptor.forClass(String.class);
            verify(auditLogService).log(eq("subscription_cancelled"), eq(userId), eq("10.0.0.1"), metadata.capture());
            assertThat(metadata.getValue()).contains("\"stripe_subscription_id\":\"sub_1\"")
                .contains("\"access_until\":\"" + nextBilling + "\"");
            verify(emailService, never()).sendSubscriptionCancelled(any(), any());

            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

            verify(emailService).sendSubscriptionCancelled("ana@example.com", nextBilling);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void cancelAlreadyCancelledThrowsConflictWithoutCallingStripe() {
        when(subscriptionRepository.findByUserIdForUpdate(userId))
            .thenReturn(Optional.of(monthly(Status.CANCELLED, NOW_UTC.plusDays(5))));

        assertThatThrownBy(() -> service.cancel(userId, "10.0.0.1")).isInstanceOf(ConflictException.class);

        verify(stripeBillingService, never()).cancelAtPeriodEnd(any());
        verify(subscriptionRepository, never()).save(any());
        verify(auditLogService, never()).log(anyString(), any(), any(), any());
    }

    @Test
    void cancelTrialOrExpiredThrowsConflictWithoutCallingStripe() {
        when(subscriptionRepository.findByUserIdForUpdate(userId))
            .thenReturn(Optional.of(trialEndingAt(NOW_UTC.plusDays(10))))
            .thenReturn(Optional.of(monthly(Status.EXPIRED, NOW_UTC.minusDays(1))));

        assertThatThrownBy(() -> service.cancel(userId, null)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.cancel(userId, null)).isInstanceOf(ConflictException.class);

        verify(stripeBillingService, never()).cancelAtPeriodEnd(any());
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void cancelWithoutSubscriptionRowThrowsNotFound() {
        when(subscriptionRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(userId, null))
            .isInstanceOf(com.porquinho.exception.ResourceNotFoundException.class);
    }

    @Test
    void cancelWhenStripeFailsLeavesRowUntouched() {
        Subscription active = monthly(Status.ACTIVE, NOW_UTC.plusDays(12));
        when(subscriptionRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(active));
        org.mockito.Mockito.doThrow(new PaymentProviderUnavailableException("down"))
            .when(stripeBillingService).cancelAtPeriodEnd("sub_1");

        assertThatThrownBy(() -> service.cancel(userId, null)).isInstanceOf(PaymentProviderUnavailableException.class);

        assertThat(active.getStatus()).isEqualTo(Status.ACTIVE);
        assertThat(active.getCancellationDate()).isNull();
        verify(subscriptionRepository, never()).save(any());
        verify(auditLogService, never()).log(anyString(), any(), any(), any());
        verify(emailService, never()).sendSubscriptionCancelled(any(), any());
    }

    @Test
    void cancelledMonthlyHasAccessUntilNextBillingDateOnly() {
        assertThat(service.hasAccess(monthly(Status.CANCELLED, NOW_UTC.plusSeconds(1)))).isTrue();
        assertThat(service.hasAccess(monthly(Status.CANCELLED, NOW_UTC))).isFalse();
        assertThat(service.hasAccess(monthly(Status.CANCELLED, NOW_UTC.minusDays(1)))).isFalse();
        assertThat(service.hasAccess(monthly(Status.CANCELLED, null))).isFalse();
    }

    @Test
    void expiredMonthlyHasNoAccessEvenBeforeNextBillingDate() {
        assertThat(service.hasAccess(monthly(Status.EXPIRED, NOW_UTC.plusDays(5)))).isFalse();
    }

    @Test
    void expireByStripeSubscriptionIdMarksExpiredAndAudits() {
        Subscription cancelled = monthly(Status.CANCELLED, NOW_UTC.minusDays(1));
        when(subscriptionRepository.findByStripeSubscriptionIdForUpdate("sub_1")).thenReturn(Optional.of(cancelled));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.expireByStripeSubscriptionId("sub_1")).isTrue();

        assertThat(cancelled.getStatus()).isEqualTo(Status.EXPIRED);
        ArgumentCaptor<String> metadata = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).log(eq("subscription_expired"), eq(userId), isNull(), metadata.capture());
        assertThat(metadata.getValue()).isEqualTo("{\"stripe_subscription_id\":\"sub_1\",\"previous_status\":\"CANCELLED\"}");
    }

    @Test
    void expireIsIdempotentForReplayedWebhook() {
        when(subscriptionRepository.findByStripeSubscriptionIdForUpdate("sub_1"))
            .thenReturn(Optional.of(monthly(Status.EXPIRED, NOW_UTC.minusDays(1))));

        assertThat(service.expireByStripeSubscriptionId("sub_1")).isFalse();

        verify(subscriptionRepository, never()).save(any());
        verify(auditLogService, never()).log(anyString(), any(), any(), any());
    }

    @Test
    void expireUnknownStripeSubscriptionChangesNothing() {
        when(subscriptionRepository.findByStripeSubscriptionIdForUpdate("sub_unknown")).thenReturn(Optional.empty());

        assertThat(service.expireByStripeSubscriptionId("sub_unknown")).isFalse();
        assertThat(service.expireByStripeSubscriptionId(null)).isFalse();

        verify(subscriptionRepository, never()).save(any());
        verify(auditLogService, never()).log(anyString(), any(), any(), any());
    }

    @Test
    void replayedCheckoutNeverReactivatesCancelledOrExpired() {
        for (Status status : new Status[] { Status.CANCELLED, Status.EXPIRED }) {
            OffsetDateTime nextBilling = NOW_UTC.plusDays(3);
            Subscription row = monthly(status, nextBilling);
            row.setCancellationDate(NOW_UTC.minusDays(2));
            when(subscriptionRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(row));

            Subscription result = service.activateMonthly(userId, "cus_1", "sub_1", "pm_1", NOW_UTC.plusDays(30));

            assertThat(result.getStatus()).isEqualTo(status);
            assertThat(result.getNextBillingDate()).isEqualTo(nextBilling);
            assertThat(result.getCancellationDate()).isEqualTo(NOW_UTC.minusDays(2));
        }
        verify(subscriptionRepository, never()).save(any());
        verify(stripeBillingService, never()).cancelSubscription(anyString());
        verify(auditLogService, never()).log(anyString(), any(), any(), any());
        verify(emailService, never()).sendSubscriptionActivated(any(), any());
    }

    @Test
    void cancelStoresStripePeriodEndAsAccessUntil() {
        // next_billing_date is stale (set at activation, not refreshed on renewals): Stripe's period end wins
        OffsetDateTime stale = NOW_UTC.minusDays(18);
        OffsetDateTime periodEnd = NOW_UTC.plusDays(12);
        when(subscriptionRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(monthly(Status.ACTIVE, stale)));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stripeBillingService.cancelAtPeriodEnd("sub_1")).thenReturn(periodEnd);
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithEmail("ana@example.com")));

        Subscription result = service.cancel(userId, null);

        assertThat(result.getNextBillingDate()).isEqualTo(periodEnd);
        assertThat(service.hasAccess(result)).isTrue();
        ArgumentCaptor<String> metadata = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).log(eq("subscription_cancelled"), eq(userId), isNull(), metadata.capture());
        assertThat(metadata.getValue()).contains("\"access_until\":\"" + periodEnd + "\"");
        verify(emailService).sendSubscriptionCancelled("ana@example.com", periodEnd);
    }

    @Test
    void cancelKeepsLocalNextBillingDateWhenStripeReturnsNoPeriodEnd() {
        OffsetDateTime nextBilling = NOW_UTC.plusDays(12);
        when(subscriptionRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(monthly(Status.ACTIVE, nextBilling)));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stripeBillingService.cancelAtPeriodEnd("sub_1")).thenReturn(null);

        Subscription result = service.cancel(userId, null);

        assertThat(result.getNextBillingDate()).isEqualTo(nextBilling);
        assertThat(service.hasAccess(result)).isTrue();
    }

    @Test
    void createCheckoutForCancelledStillInPaidPeriodThrowsConflict() {
        when(subscriptionRepository.findByUserId(userId)).thenReturn(Optional.of(monthly(Status.CANCELLED, NOW_UTC.plusDays(5))));

        assertThatThrownBy(() -> service.createCheckout(userId)).isInstanceOf(ConflictException.class);

        verify(stripeBillingService, never()).createCheckoutSession(any(), any(), any());
    }

    @Test
    void activateMonthlyWithNewStripeIdClearsCancellationDate() {
        Subscription expired = monthly(Status.EXPIRED, NOW_UTC.minusDays(3));
        expired.setCancellationDate(NOW_UTC.minusDays(20));
        when(subscriptionRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(expired));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));

        Subscription result = service.activateMonthly(userId, "cus_1", "sub_2", "pm_2", NOW_UTC.plusDays(30));

        assertThat(result.getStatus()).isEqualTo(Status.ACTIVE);
        assertThat(result.getStripeSubscriptionId()).isEqualTo("sub_2");
        assertThat(result.getCancellationDate()).isNull();
    }

    // ---- Story 2.5: reactivate ----

    @Test
    void reactivateCancelledWithAccessResumesStripeMarksActiveAuditsAndEmailsAfterCommit() {
        Subscription cancelled = monthly(Status.CANCELLED, NOW_UTC.plusDays(12));
        cancelled.setCancellationDate(NOW_UTC.minusDays(2));
        OffsetDateTime stripePeriodEnd = NOW_UTC.plusDays(12).plusHours(1);
        when(subscriptionRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(cancelled));
        when(stripeBillingService.resumeSubscription("sub_1")).thenReturn(stripePeriodEnd);
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithEmail("ana@example.com")));

        TransactionSynchronizationManager.initSynchronization();
        try {
            Subscription result = service.reactivate(userId, "10.0.0.1");

            assertThat(result.getPlanType()).isEqualTo(PlanType.MONTHLY);
            assertThat(result.getStatus()).isEqualTo(Status.ACTIVE);
            assertThat(result.getCancellationDate()).isNull();
            assertThat(result.getNextBillingDate()).isEqualTo(stripePeriodEnd);
            assertThat(result.getStripeSubscriptionId()).isEqualTo("sub_1");
            verify(stripeBillingService, never()).createCheckoutSession(any(), any(), any());
            ArgumentCaptor<String> metadata = ArgumentCaptor.forClass(String.class);
            verify(auditLogService).log(eq("subscription_reactivated"), eq(userId), eq("10.0.0.1"), metadata.capture());
            assertThat(metadata.getValue()).contains("\"stripe_subscription_id\":\"sub_1\"")
                .contains("\"next_billing_date\":\"" + stripePeriodEnd + "\"")
                .contains("\"via\":\"resume\"")
                .contains("\"previous_cancellation_date\":\"" + NOW_UTC.minusDays(2) + "\"");
            verify(emailService, never()).sendSubscriptionReactivated(any(), any());

            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

            verify(emailService).sendSubscriptionReactivated("ana@example.com", stripePeriodEnd);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void reactivateKeepsLocalNextBillingWhenStripeReturnsNoPeriodEnd() {
        OffsetDateTime nextBilling = NOW_UTC.plusDays(5);
        when(subscriptionRepository.findByUserIdForUpdate(userId))
            .thenReturn(Optional.of(monthly(Status.CANCELLED, nextBilling)));
        when(stripeBillingService.resumeSubscription("sub_1")).thenReturn(null);
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));

        Subscription result = service.reactivate(userId, null);

        assertThat(result.getStatus()).isEqualTo(Status.ACTIVE);
        assertThat(result.getNextBillingDate()).isEqualTo(nextBilling);
    }

    @Test
    void reactivateActiveTrialExpiredOrCancelledWithoutAccessThrowsConflictWithoutCallingStripe() {
        when(subscriptionRepository.findByUserIdForUpdate(userId))
            .thenReturn(Optional.of(monthly(Status.ACTIVE, NOW_UTC.plusDays(12))))
            .thenReturn(Optional.of(trialEndingAt(NOW_UTC.plusDays(10))))
            .thenReturn(Optional.of(monthly(Status.EXPIRED, NOW_UTC.minusDays(1))))
            .thenReturn(Optional.of(monthly(Status.CANCELLED, NOW_UTC)));

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> service.reactivate(userId, null)).isInstanceOf(ConflictException.class);
        }

        verify(stripeBillingService, never()).resumeSubscription(any());
        verify(subscriptionRepository, never()).save(any());
        verify(auditLogService, never()).log(anyString(), any(), any(), any());
        verify(emailService, never()).sendSubscriptionReactivated(any(), any());
    }

    @Test
    void reactivateWithoutSubscriptionRowThrowsNotFound() {
        when(subscriptionRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reactivate(userId, null))
            .isInstanceOf(com.porquinho.exception.ResourceNotFoundException.class);
    }

    @Test
    void reactivateWhenStripeFailsOrRefusesLeavesRowUntouched() {
        Subscription cancelled = monthly(Status.CANCELLED, NOW_UTC.plusDays(12));
        cancelled.setCancellationDate(NOW_UTC.minusDays(2));
        when(subscriptionRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(cancelled));
        when(stripeBillingService.resumeSubscription("sub_1"))
            .thenThrow(new PaymentProviderUnavailableException("down"))
            .thenThrow(new ConflictException("Sua assinatura já terminou. Assine novamente para reativar."));

        assertThatThrownBy(() -> service.reactivate(userId, null)).isInstanceOf(PaymentProviderUnavailableException.class);
        assertThatThrownBy(() -> service.reactivate(userId, null)).isInstanceOf(ConflictException.class);

        assertThat(cancelled.getStatus()).isEqualTo(Status.CANCELLED);
        assertThat(cancelled.getCancellationDate()).isEqualTo(NOW_UTC.minusDays(2));
        assertThat(cancelled.getNextBillingDate()).isEqualTo(NOW_UTC.plusDays(12));
        verify(subscriptionRepository, never()).save(any());
        verify(auditLogService, never()).log(anyString(), any(), any(), any());
        verify(emailService, never()).sendSubscriptionReactivated(any(), any());
    }

    @Test
    void activateMonthlyForExpiredFormerSubscriberAuditsReactivationAndSendsReactivatedEmail() {
        Subscription expired = monthly(Status.EXPIRED, NOW_UTC.minusDays(3));
        expired.setCancellationDate(NOW_UTC.minusDays(33));
        OffsetDateTime nextBilling = NOW_UTC.plusDays(30);
        when(subscriptionRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(expired));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithEmail("ana@example.com")));

        Subscription result = service.activateMonthly(userId, "cus_1", "sub_2", "pm_2", nextBilling);

        assertThat(result.getStatus()).isEqualTo(Status.ACTIVE);
        assertThat(result.getStripeSubscriptionId()).isEqualTo("sub_2");
        assertThat(result.getCancellationDate()).isNull();
        assertThat(result.getNextBillingDate()).isEqualTo(nextBilling);
        ArgumentCaptor<String> metadata = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).log(eq("subscription_reactivated"), eq(userId), isNull(), metadata.capture());
        assertThat(metadata.getValue()).contains("\"via\":\"checkout\"");
        verify(auditLogService, never()).log(eq("subscription_activated"), any(), any(), any());
        verify(emailService).sendSubscriptionReactivated("ana@example.com", nextBilling);
        verify(emailService, never()).sendSubscriptionActivated(any(), any());
    }

    @Test
    void activateMonthlyForCancelledWithoutAccessIsAReactivation() {
        when(subscriptionRepository.findByUserIdForUpdate(userId))
            .thenReturn(Optional.of(monthly(Status.CANCELLED, NOW_UTC.minusDays(1))));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        service.activateMonthly(userId, "cus_1", "sub_2", "pm_2", NOW_UTC.plusDays(30));

        verify(auditLogService).log(eq("subscription_reactivated"), eq(userId), isNull(), anyString());
    }
}
