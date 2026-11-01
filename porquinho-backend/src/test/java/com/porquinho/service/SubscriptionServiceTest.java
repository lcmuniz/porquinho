package com.porquinho.service;

import com.porquinho.entity.AuditLog;
import com.porquinho.entity.BillingTransaction;
import com.porquinho.entity.Subscription;
import com.porquinho.entity.Subscription.PlanType;
import com.porquinho.entity.Subscription.Status;
import com.porquinho.entity.User;
import com.porquinho.exception.ConflictException;
import com.porquinho.exception.PaymentProviderUnavailableException;
import com.porquinho.repository.BillingTransactionRepository;
import com.porquinho.repository.SubscriptionRepository;
import com.porquinho.repository.UserRepository;
import com.porquinho.service.StripeBillingService.SubscriptionDetails;
import com.porquinho.service.SubscriptionService.FailureOutcome;
import com.porquinho.service.SubscriptionService.InvoicePayment;
import com.porquinho.service.SubscriptionService.PaymentFailure;
import com.porquinho.service.SubscriptionService.PaymentOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
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
import static org.mockito.Mockito.doThrow;
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

    @Mock
    private BillingTransactionRepository billingTransactionRepository;

    private SubscriptionService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new SubscriptionService(subscriptionRepository, auditLogService, Clock.fixed(NOW, ZoneOffset.UTC),
            stripeBillingService, userRepository, emailService, billingTransactionRepository, "http://app.test/");
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

        assertThat(service.expireByStripeSubscriptionId("sub_1", null)).isTrue();

        assertThat(cancelled.getStatus()).isEqualTo(Status.EXPIRED);
        ArgumentCaptor<String> metadata = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).log(eq("subscription_expired"), eq(userId), isNull(), metadata.capture());
        assertThat(metadata.getValue()).isEqualTo("{\"stripe_subscription_id\":\"sub_1\",\"previous_status\":\"CANCELLED\"}");
    }

    @Test
    void expireIsIdempotentForReplayedWebhook() {
        when(subscriptionRepository.findByStripeSubscriptionIdForUpdate("sub_1"))
            .thenReturn(Optional.of(monthly(Status.EXPIRED, NOW_UTC.minusDays(1))));

        assertThat(service.expireByStripeSubscriptionId("sub_1", null)).isFalse();

        verify(subscriptionRepository, never()).save(any());
        verify(auditLogService, never()).log(anyString(), any(), any(), any());
    }

    @Test
    void expireUnknownStripeSubscriptionChangesNothing() {
        when(subscriptionRepository.findByStripeSubscriptionIdForUpdate("sub_unknown")).thenReturn(Optional.empty());

        assertThat(service.expireByStripeSubscriptionId("sub_unknown", null)).isFalse();
        assertThat(service.expireByStripeSubscriptionId(null, null)).isFalse();

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

    // ---- Story 2.6: recurring payments (invoice.paid) ----

    private Subscription activeMonthlyWithId(OffsetDateTime nextBilling) {
        Subscription s = monthly(Status.ACTIVE, nextBilling);
        s.setId(UUID.randomUUID());
        return s;
    }

    private InvoicePayment invoice(OffsetDateTime paidAt, OffsetDateTime linePeriodEnd) {
        return new InvoicePayment("in_1", "sub_1", 1990, "BRL", paidAt, linePeriodEnd);
    }

    @Test
    void recordPaymentStoresPaidTransactionAdvancesBillingAuditsAndEmailsReceiptAfterCommit() {
        Subscription sub = activeMonthlyWithId(NOW_UTC);
        OffsetDateTime newPeriodEnd = NOW_UTC.plusMonths(1);
        when(subscriptionRepository.findByStripeSubscriptionIdForUpdate("sub_1")).thenReturn(Optional.of(sub));
        when(billingTransactionRepository.existsByStripeTransactionId("in_1")).thenReturn(false);
        when(stripeBillingService.retrieveSubscription("sub_1"))
            .thenReturn(new SubscriptionDetails("cus_1", "pm_1", newPeriodEnd));
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithEmail("ana@example.com")));

        TransactionSynchronizationManager.initSynchronization();
        try {
            PaymentOutcome outcome = service.recordPayment(invoice(NOW_UTC, NOW_UTC.plusDays(29)));

            assertThat(outcome).isEqualTo(PaymentOutcome.RECORDED);
            ArgumentCaptor<BillingTransaction> tx = ArgumentCaptor.forClass(BillingTransaction.class);
            verify(billingTransactionRepository).save(tx.capture());
            assertThat(tx.getValue().getAmount()).isEqualByComparingTo("19.90");
            assertThat(tx.getValue().getAmount().scale()).isEqualTo(2);
            assertThat(tx.getValue().getCurrency()).isEqualTo("brl");
            assertThat(tx.getValue().getStatus()).isEqualTo(BillingTransaction.Status.PAID);
            assertThat(tx.getValue().getStripeTransactionId()).isEqualTo("in_1");
            assertThat(tx.getValue().getPaymentMethod()).isEqualTo("pm_1");
            assertThat(tx.getValue().getSubscriptionId()).isEqualTo(sub.getId());
            assertThat(tx.getValue().getUserId()).isEqualTo(userId);
            assertThat(tx.getValue().getPaidAt()).isEqualTo(NOW_UTC);

            assertThat(sub.getNextBillingDate()).isEqualTo(newPeriodEnd);
            verify(subscriptionRepository).save(sub);
            verify(auditLogService).log(eq("payment_success"), eq(userId), isNull(),
                eq("{\"amount\":19.90,\"currency\":\"brl\",\"invoice_id\":\"in_1\","
                    + "\"next_billing_date\":\"" + newPeriodEnd + "\"}"));

            verify(emailService, never()).sendPaymentReceipt(any(), any(), any(), any());
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(emailService).sendPaymentReceipt(eq("ana@example.com"),
                eq(new BigDecimal("19.90")), eq(NOW_UTC), eq(newPeriodEnd));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        // Locked straight by Stripe id: no unlocked read that would pin a stale instance (identity map)
        verify(subscriptionRepository, never()).findByUserIdForUpdate(any());
        verify(subscriptionRepository, never()).findByUserId(any());
    }

    @Test
    void recordPaymentForFirstChargeAtTrialEndIsRecordedTheSameWay() {
        // Subscribed during the trial: next_billing_date was the trial end, the first real charge happens there
        Subscription sub = activeMonthlyWithId(NOW_UTC);
        sub.setTrialEndDate(NOW_UTC);
        OffsetDateTime newPeriodEnd = NOW_UTC.plusMonths(1);
        when(subscriptionRepository.findByStripeSubscriptionIdForUpdate("sub_1")).thenReturn(Optional.of(sub));
        when(stripeBillingService.retrieveSubscription("sub_1"))
            .thenReturn(new SubscriptionDetails("cus_1", "pm_1", newPeriodEnd));
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithEmail("ana@example.com")));

        assertThat(service.recordPayment(invoice(NOW_UTC, null))).isEqualTo(PaymentOutcome.RECORDED);

        assertThat(sub.getNextBillingDate()).isEqualTo(newPeriodEnd);
        verify(billingTransactionRepository).save(any(BillingTransaction.class));
        verify(emailService).sendPaymentReceipt("ana@example.com", new BigDecimal("19.90"), NOW_UTC, newPeriodEnd);
    }

    @Test
    void recordPaymentReplayChangesNothing() {
        Subscription sub = activeMonthlyWithId(NOW_UTC.plusDays(30));
        when(subscriptionRepository.findByStripeSubscriptionIdForUpdate("sub_1")).thenReturn(Optional.of(sub));
        when(billingTransactionRepository.existsByStripeTransactionId("in_1")).thenReturn(true);

        assertThat(service.recordPayment(invoice(NOW_UTC, NOW_UTC.plusDays(30)))).isEqualTo(PaymentOutcome.DUPLICATE);

        verify(billingTransactionRepository, never()).save(any());
        verify(subscriptionRepository, never()).save(any());
        verify(stripeBillingService, never()).retrieveSubscription(anyString());
        verify(auditLogService, never()).log(anyString(), any(), any(), any());
        verify(emailService, never()).sendPaymentReceipt(any(), any(), any(), any());
    }

    @Test
    void recordPaymentForUnknownStripeSubscriptionChangesNothing() {
        when(subscriptionRepository.findByStripeSubscriptionIdForUpdate("sub_1")).thenReturn(Optional.empty());

        assertThat(service.recordPayment(invoice(NOW_UTC, null))).isEqualTo(PaymentOutcome.UNKNOWN_SUBSCRIPTION);

        verify(billingTransactionRepository, never()).existsByStripeTransactionId(any());
        verify(billingTransactionRepository, never()).save(any());
        verify(stripeBillingService, never()).retrieveSubscription(anyString());
        verify(auditLogService, never()).log(anyString(), any(), any(), any());
        verify(emailService, never()).sendPaymentReceipt(any(), any(), any(), any());
    }

    @Test
    void recordPaymentWithoutStripeSubscriptionIdIsUnknown() {
        InvoicePayment payment = new InvoicePayment("in_1", null, 1990, "brl", NOW_UTC, null);

        assertThat(service.recordPayment(payment)).isEqualTo(PaymentOutcome.UNKNOWN_SUBSCRIPTION);

        verify(subscriptionRepository, never()).findByStripeSubscriptionIdForUpdate(any());
        verify(billingTransactionRepository, never()).save(any());
    }

    @Test
    void recordPaymentWhenStripeDownUsesInvoiceLinePeriodEnd() {
        Subscription sub = activeMonthlyWithId(NOW_UTC);
        OffsetDateTime linePeriodEnd = NOW_UTC.plusDays(30);
        when(subscriptionRepository.findByStripeSubscriptionIdForUpdate("sub_1")).thenReturn(Optional.of(sub));
        when(stripeBillingService.retrieveSubscription("sub_1")).thenThrow(new PaymentProviderUnavailableException("down"));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThat(service.recordPayment(invoice(NOW_UTC, linePeriodEnd))).isEqualTo(PaymentOutcome.RECORDED);

        assertThat(sub.getNextBillingDate()).isEqualTo(linePeriodEnd);
        verify(billingTransactionRepository).save(any(BillingTransaction.class));
        verify(auditLogService).log(eq("payment_success"), eq(userId), isNull(), anyString());
    }

    @Test
    void recordPaymentNeverMovesNextBillingDateBackwards() {
        // A late invoice.paid of an older period: Stripe says the period ends before what we already have
        OffsetDateTime current = NOW_UTC.plusDays(40);
        Subscription sub = activeMonthlyWithId(current);
        when(subscriptionRepository.findByStripeSubscriptionIdForUpdate("sub_1")).thenReturn(Optional.of(sub));
        when(stripeBillingService.retrieveSubscription("sub_1"))
            .thenThrow(new PaymentProviderUnavailableException("down"));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThat(service.recordPayment(invoice(NOW_UTC.minusDays(30), NOW_UTC.plusDays(10))))
            .isEqualTo(PaymentOutcome.RECORDED);

        assertThat(sub.getNextBillingDate()).isEqualTo(current);
        verify(subscriptionRepository, never()).save(any());
        verify(billingTransactionRepository).save(any(BillingTransaction.class));
        verify(auditLogService).log(eq("payment_success"), eq(userId), isNull(),
            eq("{\"amount\":19.90,\"currency\":\"brl\",\"invoice_id\":\"in_1\","
                + "\"next_billing_date\":\"" + current + "\"}"));
    }

    @Test
    void recordPaymentWithoutPaidAtUsesNow() {
        Subscription sub = activeMonthlyWithId(NOW_UTC);
        when(subscriptionRepository.findByStripeSubscriptionIdForUpdate("sub_1")).thenReturn(Optional.of(sub));
        when(stripeBillingService.retrieveSubscription("sub_1"))
            .thenReturn(new SubscriptionDetails("cus_1", "pm_1", NOW_UTC.plusMonths(1)));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        service.recordPayment(invoice(null, null));

        ArgumentCaptor<BillingTransaction> tx = ArgumentCaptor.forClass(BillingTransaction.class);
        verify(billingTransactionRepository).save(tx.capture());
        assertThat(tx.getValue().getPaidAt()).isEqualTo(NOW_UTC);
    }

    @Test
    void recordPaymentWithoutCurrencyDefaultsToBrl() {
        Subscription sub = activeMonthlyWithId(NOW_UTC);
        when(subscriptionRepository.findByStripeSubscriptionIdForUpdate("sub_1")).thenReturn(Optional.of(sub));
        when(stripeBillingService.retrieveSubscription("sub_1"))
            .thenReturn(new SubscriptionDetails("cus_1", "pm_1", NOW_UTC.plusMonths(1)));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        service.recordPayment(new InvoicePayment("in_1", "sub_1", 1990, null, NOW_UTC, null));

        ArgumentCaptor<BillingTransaction> tx = ArgumentCaptor.forClass(BillingTransaction.class);
        verify(billingTransactionRepository).save(tx.capture());
        assertThat(tx.getValue().getCurrency()).isEqualTo("brl");
    }

    // ---- Story 2.7: payment failures ----

    private static final String PAY_URL = "https://invoice.stripe.com/i/in_1";

    private PaymentFailure failure(long attempt) {
        return new PaymentFailure("in_1", "sub_1", attempt, PAY_URL, true);
    }

    @Test
    void firstFailureMarksPaymentFailedSavesLinkAuditsAndEmailsAfterCommit() {
        Subscription sub = monthly(Status.ACTIVE, NOW_UTC);
        when(subscriptionRepository.findByStripeSubscriptionIdForUpdate("sub_1")).thenReturn(Optional.of(sub));
        when(billingTransactionRepository.existsByStripeTransactionId("in_1")).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithEmail("ana@example.com")));

        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThat(service.recordPaymentFailure(failure(1))).isEqualTo(FailureOutcome.RECORDED);

            assertThat(sub.getStatus()).isEqualTo(Status.PAYMENT_FAILED);
            assertThat(sub.getFailedPaymentAttempts()).isEqualTo(1);
            assertThat(sub.getPaymentUpdateUrl()).isEqualTo(PAY_URL);
            assertThat(service.hasAccess(sub)).isTrue();
            verify(subscriptionRepository).save(sub);
            verify(auditLogService).log(eq("payment_failed"), eq(userId), isNull(),
                eq("{\"attempt_count\":1,\"invoice_id\":\"in_1\",\"previous_status\":\"ACTIVE\"}"));
            verify(emailService, never()).sendPaymentFailed(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());

            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

            verify(emailService).sendPaymentFailed("ana@example.com", PAY_URL, true);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        verify(billingTransactionRepository, never()).save(any());
    }

    @Test
    void retryFailureIncrementsAttemptsAuditsAndEmailsAgain() {
        Subscription sub = monthly(Status.PAYMENT_FAILED, NOW_UTC.minusDays(3));
        sub.setFailedPaymentAttempts(1);
        sub.setPaymentUpdateUrl(PAY_URL);
        when(subscriptionRepository.findByStripeSubscriptionIdForUpdate("sub_1")).thenReturn(Optional.of(sub));
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithEmail("ana@example.com")));

        assertThat(service.recordPaymentFailure(new PaymentFailure("in_1", "sub_1", 2, null, false)))
            .isEqualTo(FailureOutcome.RECORDED);

        assertThat(sub.getStatus()).isEqualTo(Status.PAYMENT_FAILED);
        assertThat(sub.getFailedPaymentAttempts()).isEqualTo(2);
        assertThat(sub.getPaymentUpdateUrl()).isEqualTo(PAY_URL);
        verify(auditLogService).log(eq("payment_failed"), eq(userId), isNull(),
            eq("{\"attempt_count\":2,\"invoice_id\":\"in_1\",\"previous_status\":\"PAYMENT_FAILED\"}"));
        // Last attempt (no next_payment_attempt): the e-mail warns of suspension instead of a retry
        verify(emailService).sendPaymentFailed("ana@example.com", PAY_URL, false);
    }

    @Test
    void repeatedFailureAtOrBelowRecordedAttemptChangesNothing() {
        Subscription sub = monthly(Status.PAYMENT_FAILED, NOW_UTC);
        sub.setFailedPaymentAttempts(2);
        sub.setPaymentUpdateUrl(PAY_URL);
        when(subscriptionRepository.findByStripeSubscriptionIdForUpdate("sub_1")).thenReturn(Optional.of(sub));

        assertThat(service.recordPaymentFailure(failure(2))).isEqualTo(FailureOutcome.IGNORED);
        assertThat(service.recordPaymentFailure(failure(1))).isEqualTo(FailureOutcome.IGNORED);

        assertThat(sub.getFailedPaymentAttempts()).isEqualTo(2);
        verify(subscriptionRepository, never()).save(any());
        verify(auditLogService, never()).log(anyString(), any(), any(), any());
        verify(emailService, never()).sendPaymentFailed(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void lateFailureOfAnAlreadyPaidInvoiceIsIgnored() {
        Subscription sub = monthly(Status.ACTIVE, NOW_UTC.plusDays(30));
        when(subscriptionRepository.findByStripeSubscriptionIdForUpdate("sub_1")).thenReturn(Optional.of(sub));
        when(billingTransactionRepository.existsByStripeTransactionId("in_1")).thenReturn(true);

        assertThat(service.recordPaymentFailure(failure(1))).isEqualTo(FailureOutcome.IGNORED);

        assertThat(sub.getStatus()).isEqualTo(Status.ACTIVE);
        verify(subscriptionRepository, never()).save(any());
        verify(auditLogService, never()).log(anyString(), any(), any(), any());
    }

    @Test
    void failureForUnknownOrNotApplicableSubscriptionChangesNothing() {
        when(subscriptionRepository.findByStripeSubscriptionIdForUpdate("sub_1"))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(monthly(Status.EXPIRED, NOW_UTC)))
            .thenReturn(Optional.of(monthly(Status.SUSPENDED, NOW_UTC)))
            .thenReturn(Optional.of(monthly(Status.CANCELLED, NOW_UTC.plusDays(3))));

        assertThat(service.recordPaymentFailure(failure(1))).isEqualTo(FailureOutcome.UNKNOWN_SUBSCRIPTION);
        assertThat(service.recordPaymentFailure(failure(1))).isEqualTo(FailureOutcome.IGNORED);
        assertThat(service.recordPaymentFailure(failure(1))).isEqualTo(FailureOutcome.IGNORED);
        assertThat(service.recordPaymentFailure(failure(1))).isEqualTo(FailureOutcome.IGNORED);
        assertThat(service.recordPaymentFailure(new PaymentFailure("in_1", null, 1, PAY_URL, true)))
            .isEqualTo(FailureOutcome.UNKNOWN_SUBSCRIPTION);

        verify(subscriptionRepository, never()).save(any());
        verify(auditLogService, never()).log(anyString(), any(), any(), any());
        verify(emailService, never()).sendPaymentFailed(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void failureWithoutHostedUrlEmailsTheSubscriptionPage() {
        Subscription sub = monthly(Status.ACTIVE, NOW_UTC);
        when(subscriptionRepository.findByStripeSubscriptionIdForUpdate("sub_1")).thenReturn(Optional.of(sub));
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithEmail("ana@example.com")));

        service.recordPaymentFailure(new PaymentFailure("in_1", "sub_1", 1, null, true));

        assertThat(sub.getPaymentUpdateUrl()).isNull();
        verify(emailService).sendPaymentFailed("ana@example.com", "http://app.test/assinatura", true);
    }

    @Test
    void paidRetryRecoversToActiveClearsCounterAndLinkAndAuditsRecovery() {
        Subscription sub = activeMonthlyWithId(NOW_UTC);
        sub.setStatus(Status.PAYMENT_FAILED);
        sub.setFailedPaymentAttempts(2);
        sub.setPaymentUpdateUrl(PAY_URL);
        OffsetDateTime newPeriodEnd = NOW_UTC.plusMonths(1);
        when(subscriptionRepository.findByStripeSubscriptionIdForUpdate("sub_1")).thenReturn(Optional.of(sub));
        when(stripeBillingService.retrieveSubscription("sub_1"))
            .thenReturn(new SubscriptionDetails("cus_1", "pm_1", newPeriodEnd));
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithEmail("ana@example.com")));

        assertThat(service.recordPayment(invoice(NOW_UTC, null))).isEqualTo(PaymentOutcome.RECORDED);

        assertThat(sub.getStatus()).isEqualTo(Status.ACTIVE);
        assertThat(sub.getFailedPaymentAttempts()).isZero();
        assertThat(sub.getPaymentUpdateUrl()).isNull();
        verify(auditLogService).log(eq("payment_recovered"), eq(userId), isNull(),
            eq("{\"invoice_id\":\"in_1\",\"failed_payment_attempts\":2}"));
        verify(auditLogService).log(eq("payment_success"), eq(userId), isNull(), anyString());
        verify(billingTransactionRepository).save(any(BillingTransaction.class));
        verify(emailService).sendPaymentReceipt("ana@example.com", new BigDecimal("19.90"), NOW_UTC, newPeriodEnd);
    }

    @Test
    void regularPaymentDoesNotAuditRecovery() {
        Subscription sub = activeMonthlyWithId(NOW_UTC);
        when(subscriptionRepository.findByStripeSubscriptionIdForUpdate("sub_1")).thenReturn(Optional.of(sub));
        when(stripeBillingService.retrieveSubscription("sub_1"))
            .thenReturn(new SubscriptionDetails("cus_1", "pm_1", NOW_UTC.plusMonths(1)));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        service.recordPayment(invoice(NOW_UTC, null));

        verify(auditLogService, never()).log(eq("payment_recovered"), any(), any(), any());
    }

    @Test
    void deletedWhilePaymentFailedSuspendsAuditsAndEmailsAfterCommit() {
        Subscription sub = monthly(Status.PAYMENT_FAILED, NOW_UTC.minusDays(10));
        sub.setFailedPaymentAttempts(3);
        sub.setPaymentUpdateUrl(PAY_URL);
        when(subscriptionRepository.findByStripeSubscriptionIdForUpdate("sub_1")).thenReturn(Optional.of(sub));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithEmail("ana@example.com")));

        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThat(service.expireByStripeSubscriptionId("sub_1", "in_open")).isTrue();

            assertThat(sub.getStatus()).isEqualTo(Status.SUSPENDED);
            assertThat(sub.getPaymentUpdateUrl()).isNull();
            assertThat(service.hasAccess(sub)).isFalse();
            verify(auditLogService).log(eq("subscription_suspended"), eq(userId), isNull(),
                eq("{\"stripe_subscription_id\":\"sub_1\",\"failed_payment_attempts\":3,"
                    + "\"latest_invoice_id\":\"in_open\"}"));
            verify(auditLogService, never()).log(eq("subscription_expired"), any(), any(), any());
            verify(emailService, never()).sendSubscriptionSuspended(any(), any());
            verify(stripeBillingService, never()).voidInvoice(any());

            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

            verify(stripeBillingService).voidInvoice("in_open");
            verify(emailService).sendSubscriptionSuspended("ana@example.com", "http://app.test/assinatura");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void deletedReplayForSuspendedChangesNothing() {
        when(subscriptionRepository.findByStripeSubscriptionIdForUpdate("sub_1"))
            .thenReturn(Optional.of(monthly(Status.SUSPENDED, NOW_UTC.minusDays(10))));

        assertThat(service.expireByStripeSubscriptionId("sub_1", null)).isFalse();

        verify(subscriptionRepository, never()).save(any());
        verify(auditLogService, never()).log(anyString(), any(), any(), any());
        verify(emailService, never()).sendSubscriptionSuspended(any(), any());
    }

    @Test
    void deletedForActiveMonthlyStillExpires() {
        Subscription sub = monthly(Status.ACTIVE, NOW_UTC);
        when(subscriptionRepository.findByStripeSubscriptionIdForUpdate("sub_1")).thenReturn(Optional.of(sub));

        assertThat(service.expireByStripeSubscriptionId("sub_1", null)).isTrue();

        assertThat(sub.getStatus()).isEqualTo(Status.EXPIRED);
        verify(auditLogService).log(eq("subscription_expired"), eq(userId), isNull(), anyString());
        verify(emailService, never()).sendSubscriptionSuspended(any(), any());
        verify(stripeBillingService, never()).voidInvoice(any());
    }

    @Test
    void paymentFailedKeepsAccessAndSuspendedHasNone() {
        // Decision 2A: access kept while Stripe retries, even past next_billing_date
        assertThat(service.hasAccess(monthly(Status.PAYMENT_FAILED, NOW_UTC.minusDays(5)))).isTrue();
        assertThat(service.hasAccess(monthly(Status.SUSPENDED, NOW_UTC.plusDays(5)))).isFalse();
    }

    @Test
    void createCheckoutOrReactivateDuringPaymentFailedThrowsConflict() {
        Subscription sub = monthly(Status.PAYMENT_FAILED, NOW_UTC);
        when(subscriptionRepository.findByUserId(userId)).thenReturn(Optional.of(sub));
        when(subscriptionRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(sub));

        assertThatThrownBy(() -> service.createCheckout(userId))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("fatura pendente");
        assertThatThrownBy(() -> service.reactivate(userId, null))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("fatura pendente");

        verify(stripeBillingService, never()).createCheckoutSession(any(), any(), any());
        verify(stripeBillingService, never()).resumeSubscription(any());
    }

    @Test
    void createCheckoutForSuspendedIsAllowed() {
        when(subscriptionRepository.findByUserId(userId)).thenReturn(Optional.of(monthly(Status.SUSPENDED, NOW_UTC)));
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithEmail("ana@example.com")));
        when(stripeBillingService.createCheckoutSession(eq(userId), eq("ana@example.com"), isNull()))
            .thenReturn("https://checkout.stripe.com/x");

        assertThat(service.createCheckout(userId)).isEqualTo("https://checkout.stripe.com/x");
    }

    @Test
    void checkoutAfterSuspensionReactivatesAndClearsFailureTracking() {
        Subscription sub = monthly(Status.SUSPENDED, NOW_UTC.minusDays(10));
        sub.setFailedPaymentAttempts(3);
        OffsetDateTime nextBilling = NOW_UTC.plusDays(30);
        when(subscriptionRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(sub));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithEmail("ana@example.com")));

        Subscription result = service.activateMonthly(userId, "cus_1", "sub_2", "pm_2", nextBilling);

        assertThat(result.getStatus()).isEqualTo(Status.ACTIVE);
        assertThat(result.getStripeSubscriptionId()).isEqualTo("sub_2");
        assertThat(result.getFailedPaymentAttempts()).isZero();
        assertThat(result.getPaymentUpdateUrl()).isNull();
        verify(auditLogService).log(eq("subscription_reactivated"), eq(userId), isNull(), anyString());
        verify(emailService).sendSubscriptionReactivated("ana@example.com", nextBilling);
    }

    @Test
    void completedCheckoutDuringPaymentFailedCancelsTheDuplicate() {
        Subscription sub = monthly(Status.PAYMENT_FAILED, NOW_UTC);
        when(subscriptionRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(sub));

        service.activateMonthly(userId, "cus_1", "sub_2", "pm_2", NOW_UTC.plusDays(30));

        assertThat(sub.getStripeSubscriptionId()).isEqualTo("sub_1");
        assertThat(sub.getStatus()).isEqualTo(Status.PAYMENT_FAILED);
        verify(stripeBillingService).cancelSubscription("sub_2");
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void cancelDuringPaymentFailedCancelsStripeNowExpiresAuditsAndEmailsAfterCommit() {
        Subscription sub = monthly(Status.PAYMENT_FAILED, NOW_UTC.minusDays(2));
        sub.setFailedPaymentAttempts(2);
        sub.setPaymentUpdateUrl(PAY_URL);
        when(subscriptionRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(sub));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithEmail("ana@example.com")));

        when(stripeBillingService.cancelImmediately("sub_1")).thenReturn("in_open");

        TransactionSynchronizationManager.initSynchronization();
        try {
            Subscription result = service.cancel(userId, "10.0.0.1");

            assertThat(result.getStatus()).isEqualTo(Status.EXPIRED);
            assertThat(result.getCancellationDate()).isEqualTo(NOW_UTC);
            assertThat(result.getPaymentUpdateUrl()).isNull();
            assertThat(service.hasAccess(result)).isFalse();
            verify(stripeBillingService).cancelImmediately("sub_1");
            verify(stripeBillingService, never()).cancelAtPeriodEnd(any());
            verify(stripeBillingService, never()).cancelSubscription(any());
            ArgumentCaptor<String> metadata = ArgumentCaptor.forClass(String.class);
            verify(auditLogService).log(eq("subscription_cancelled"), eq(userId), eq("10.0.0.1"), metadata.capture());
            assertThat(metadata.getValue())
                .contains("\"immediate\":true")
                .contains("\"cancellation_date\":\"" + NOW_UTC + "\"")
                .contains("\"previous_status\":\"PAYMENT_FAILED\"");
            verify(emailService, never()).sendSubscriptionCancelledImmediately(any());
            verify(stripeBillingService, never()).voidInvoice(any());

            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

            // The failed invoice stays open after the cancel: voided so the e-mailed link stops charging
            verify(stripeBillingService).voidInvoice("in_open");
            verify(emailService).sendSubscriptionCancelledImmediately("ana@example.com");
            verify(emailService, never()).sendSubscriptionCancelled(any(), any());
            verify(emailService, never()).sendSubscriptionSuspended(any(), any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void cancelDuringPaymentFailedWhenStripeFailsLeavesRowUntouched() {
        Subscription sub = monthly(Status.PAYMENT_FAILED, NOW_UTC.minusDays(2));
        sub.setFailedPaymentAttempts(1);
        sub.setPaymentUpdateUrl(PAY_URL);
        when(subscriptionRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(sub));
        doThrow(new PaymentProviderUnavailableException("down")).when(stripeBillingService).cancelImmediately("sub_1");

        assertThatThrownBy(() -> service.cancel(userId, null)).isInstanceOf(PaymentProviderUnavailableException.class);

        assertThat(sub.getStatus()).isEqualTo(Status.PAYMENT_FAILED);
        assertThat(sub.getCancellationDate()).isNull();
        assertThat(sub.getPaymentUpdateUrl()).isEqualTo(PAY_URL);
        verify(subscriptionRepository, never()).save(any());
        verify(auditLogService, never()).log(anyString(), any(), any(), any());
        verify(emailService, never()).sendSubscriptionCancelledImmediately(any());
    }

    @Test
    void cancelDuringPaymentFailedWithoutLatestInvoiceSkipsTheVoid() {
        Subscription sub = monthly(Status.PAYMENT_FAILED, NOW_UTC.minusDays(2));
        when(subscriptionRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(sub));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        service.cancel(userId, null);

        assertThat(sub.getStatus()).isEqualTo(Status.EXPIRED);
        verify(stripeBillingService, never()).voidInvoice(any());
    }

    @Test
    void suspensionWithoutLatestInvoiceSkipsTheVoid() {
        Subscription sub = monthly(Status.PAYMENT_FAILED, NOW_UTC.minusDays(10));
        when(subscriptionRepository.findByStripeSubscriptionIdForUpdate("sub_1")).thenReturn(Optional.of(sub));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThat(service.expireByStripeSubscriptionId("sub_1", null)).isTrue();

        assertThat(sub.getStatus()).isEqualTo(Status.SUSPENDED);
        verify(stripeBillingService, never()).voidInvoice(any());
    }

    @Test
    void deletedAfterImmediateCancelIsANoOp() {
        Subscription sub = monthly(Status.EXPIRED, NOW_UTC.minusDays(2));
        sub.setCancellationDate(NOW_UTC);
        when(subscriptionRepository.findByStripeSubscriptionIdForUpdate("sub_1")).thenReturn(Optional.of(sub));

        assertThat(service.expireByStripeSubscriptionId("sub_1", "in_open")).isFalse();

        verify(subscriptionRepository, never()).save(any());
        verify(auditLogService, never()).log(anyString(), any(), any(), any());
        verify(stripeBillingService, never()).voidInvoice(any());
        verify(emailService, never()).sendSubscriptionSuspended(any(), any());
    }

    @Test
    void cancelSuspendedThrowsConflictWithoutCallingStripe() {
        when(subscriptionRepository.findByUserIdForUpdate(userId))
            .thenReturn(Optional.of(monthly(Status.SUSPENDED, NOW_UTC.minusDays(10))));

        assertThatThrownBy(() -> service.cancel(userId, null)).isInstanceOf(ConflictException.class);

        verify(stripeBillingService, never()).cancelImmediately(any());
        verify(stripeBillingService, never()).cancelAtPeriodEnd(any());
    }

    @Test
    void invoicePaidAfterSuspensionIsRecordedButStatusStaysSuspended() {
        // The void at suspension failed and the user still paid the old invoice: record it, keep SUSPENDED
        Subscription sub = activeMonthlyWithId(NOW_UTC.minusDays(10));
        sub.setStatus(Status.SUSPENDED);
        sub.setFailedPaymentAttempts(3);
        when(subscriptionRepository.findByStripeSubscriptionIdForUpdate("sub_1")).thenReturn(Optional.of(sub));
        when(stripeBillingService.retrieveSubscription("sub_1"))
            .thenThrow(new PaymentProviderUnavailableException("canceled"));
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithEmail("ana@example.com")));

        assertThat(service.recordPayment(invoice(NOW_UTC, null))).isEqualTo(PaymentOutcome.RECORDED);

        assertThat(sub.getStatus()).isEqualTo(Status.SUSPENDED);
        assertThat(service.hasAccess(sub)).isFalse();
        verify(billingTransactionRepository).save(any(BillingTransaction.class));
        verify(auditLogService).log(eq("payment_success"), eq(userId), isNull(), anyString());
        verify(auditLogService, never()).log(eq("payment_recovered"), any(), any(), any());
    }

    @Test
    void invoicePaidAfterImmediateCancelIsRecordedButStatusStaysExpired() {
        // Cancelled during PAYMENT_FAILED, the void failed and the old invoice was paid: record it, keep EXPIRED
        Subscription sub = activeMonthlyWithId(NOW_UTC.minusDays(2));
        sub.setStatus(Status.EXPIRED);
        sub.setCancellationDate(NOW_UTC.minusDays(1));
        when(subscriptionRepository.findByStripeSubscriptionIdForUpdate("sub_1")).thenReturn(Optional.of(sub));
        when(stripeBillingService.retrieveSubscription("sub_1"))
            .thenThrow(new PaymentProviderUnavailableException("canceled"));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThat(service.recordPayment(invoice(NOW_UTC, null))).isEqualTo(PaymentOutcome.RECORDED);

        assertThat(sub.getStatus()).isEqualTo(Status.EXPIRED);
        assertThat(service.hasAccess(sub)).isFalse();
        verify(billingTransactionRepository).save(any(BillingTransaction.class));
        verify(auditLogService, never()).log(eq("payment_recovered"), any(), any(), any());
    }
}
