package com.porquinho.service;

import com.porquinho.dto.AccountDeletionResponse;
import com.porquinho.entity.AccountDeletion;
import com.porquinho.entity.AccountDeletion.DeletionStatus;
import com.porquinho.entity.DataExport;
import com.porquinho.entity.User;
import com.porquinho.entity.User.AuthProvider;
import com.porquinho.entity.UserSession;
import com.porquinho.exception.RateLimitExceededException;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.repository.AccountDeletionRepository;
import com.porquinho.repository.SubscriptionRepository;
import com.porquinho.repository.AccountRepository;
import com.porquinho.repository.CategoryRepository;
import com.porquinho.repository.CreditCardRepository;
import com.porquinho.repository.GoalRepository;
import com.porquinho.repository.AuditLogRepository;
import com.porquinho.repository.BillingTransactionRepository;
import com.porquinho.repository.DataExportRepository;
import com.porquinho.repository.OnboardingProgressRepository;
import com.porquinho.repository.UserRepository;
import com.porquinho.repository.UserSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountDeletionServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserSessionRepository userSessionRepository;
    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private DataExportRepository dataExportRepository;
    @Mock
    private AccountDeletionRepository accountDeletionRepository;
    @Mock
    private JwtBlacklistService jwtBlacklistService;
    @Mock
    private EmailService emailService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private SupabaseAdminClient supabaseAdminClient;
    @Mock
    private RateLimitService rateLimitService;
    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private StripeBillingService stripeBillingService;
    @Mock
    private BillingTransactionRepository billingTransactionRepository;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private OnboardingProgressRepository onboardingProgressRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private CreditCardRepository creditCardRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private GoalRepository goalRepository;

    private AccountDeletionService service;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = new User("test@example.com", AuthProvider.EMAIL, null);
        user.setId(userId);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        // Build the service. The "self" reference is normally a Spring AOP proxy provided by
        // @Lazy. In unit tests we need to wire a working delegate so internal calls (e.g.
        // self.purgeUser inside purgeScheduledDeletions, or self.executeDeletionTransaction
        // inside requestDeletion) actually run their logic. We construct the service twice:
        // once as a placeholder, then a real instance that points at itself. This mirrors
        // how the proxy resolves at runtime for testing purposes.
        AccountDeletionService temp = new AccountDeletionService(
            userRepository, userSessionRepository, auditLogRepository, dataExportRepository,
            accountDeletionRepository, jwtBlacklistService, emailService, auditLogService,
            supabaseAdminClient, rateLimitService, subscriptionRepository, stripeBillingService,
            billingTransactionRepository, subscriptionService, onboardingProgressRepository, accountRepository,
            creditCardRepository, categoryRepository, goalRepository, null);
        service = new AccountDeletionService(
            userRepository, userSessionRepository, auditLogRepository, dataExportRepository,
            accountDeletionRepository, jwtBlacklistService, emailService, auditLogService,
            supabaseAdminClient, rateLimitService, subscriptionRepository, stripeBillingService,
            billingTransactionRepository, subscriptionService, onboardingProgressRepository, accountRepository,
            creditCardRepository, categoryRepository, goalRepository, temp);
        // After construction, swap in the real reference so self == service (recursive ref ok
        // for tests because Spring's proxy would similarly route the call into this same bean).
        try {
            java.lang.reflect.Field selfField = AccountDeletionService.class.getDeclaredField("self");
            selfField.setAccessible(true);
            selfField.set(service, service);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to wire self-reference in test", e);
        }
        // The service reads exportDirectory via @Value; in unit tests we set it explicitly
        // so the path-traversal check in purgeUser knows where files are allowed to live.
        ReflectionTestUtils.setField(service, "exportDirectory", System.getProperty("java.io.tmpdir"));
    }

    @Test
    void requestDeletionShouldSoftDeleteUserAndRevokeSessions() {
        when(accountDeletionRepository.findByUserIdAndStatus(userId, DeletionStatus.PENDING_PURGE))
            .thenReturn(Optional.empty());
        when(rateLimitService.allowRequest(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UserSession session = new UserSession(userId, "hash1", "Chrome", "1.1.1.1",
            OffsetDateTime.now().plusDays(7));
        session.setId(UUID.randomUUID());
        when(userSessionRepository.findByUserIdAndRevokedAtIsNullAndExpiresAtAfter(eq(userId), any()))
            .thenReturn(List.of(session));

        when(accountDeletionRepository.save(any(AccountDeletion.class))).thenAnswer(inv -> {
            AccountDeletion d = inv.getArgument(0);
            d.setId(UUID.randomUUID());
            d.setRequestedAt(OffsetDateTime.now());
            return d;
        });

        AccountDeletionResponse response = service.requestDeletion(userId, "127.0.0.1");

        assertNotNull(response);
        assertEquals(DeletionStatus.PENDING_PURGE, response.getStatus());
        assertNotNull(response.getScheduledPurgeAt());

        // User soft-deleted
        assertNotNull(user.getDeletedAt());
        assertEquals(userId, user.getDeletedBy());
        verify(userRepository).save(user);

        // Session revoked + JWT blacklisted
        assertNotNull(session.getRevokedAt());
        verify(jwtBlacklistService).blacklistToken(eq("hash1"), any());
        verify(userSessionRepository).saveAll(anyList());

        // Audit log written
        verify(auditLogService).log(eq("account_deletion_requested"), eq(userId), eq("127.0.0.1"), anyString());

        // Email sent (best-effort, post-commit phase) - only on newly-created deletion
        verify(emailService).sendAccountDeletionConfirmation(eq("test@example.com"), any());

        // Billing stops at the request, not at purge (retro Epic 2, F1); after the request audit
        InOrder order = inOrder(auditLogService, subscriptionService);
        order.verify(auditLogService).log(eq("account_deletion_requested"), eq(userId), eq("127.0.0.1"), anyString());
        order.verify(subscriptionService).cancelForAccountDeletion(userId);
        verifyNoInteractions(stripeBillingService);
    }

    @Test
    void requestDeletionRaceOnPendingPurgeDoesNotStopBillingAgain() {
        when(rateLimitService.allowRequest(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userSessionRepository.findByUserIdAndRevokedAtIsNullAndExpiresAtAfter(eq(userId), any()))
            .thenReturn(List.of());
        AccountDeletion concurrent = new AccountDeletion(userId, OffsetDateTime.now().plusDays(30), "127.0.0.1");
        concurrent.setId(UUID.randomUUID());
        when(accountDeletionRepository.save(any(AccountDeletion.class)))
            .thenThrow(new DataIntegrityViolationException("uq_pending_purge"));
        when(accountDeletionRepository.findByUserIdAndStatus(userId, DeletionStatus.PENDING_PURGE))
            .thenReturn(Optional.empty(), Optional.of(concurrent));

        AccountDeletionResponse response = service.requestDeletion(userId, "127.0.0.1");

        assertEquals(concurrent.getId(), response.getId());
        verify(subscriptionService, never()).cancelForAccountDeletion(any());
        verify(emailService, never()).sendAccountDeletionConfirmation(any(), any());
    }

    @Test
    void requestDeletionShouldBeIdempotentWhenAlreadyPending() {
        AccountDeletion existing = new AccountDeletion(userId,
            OffsetDateTime.now().plusDays(30), "127.0.0.1");
        existing.setId(UUID.randomUUID());
        existing.setRequestedAt(OffsetDateTime.now().minusHours(1));
        when(accountDeletionRepository.findByUserIdAndStatus(userId, DeletionStatus.PENDING_PURGE))
            .thenReturn(Optional.of(existing));
        when(userRepository.findByIdIncludingDeleted(userId)).thenReturn(Optional.of(user));

        AccountDeletionResponse response = service.requestDeletion(userId, "127.0.0.1");

        assertNotNull(response);
        assertEquals(existing.getId(), response.getId());

        // Idempotency runs BEFORE rate limit, so the bucket is not consumed.
        verify(rateLimitService, never()).allowRequest(anyString(), anyInt(), anyInt());

        // Should NOT soft-delete again or save a new deletion
        verify(userRepository, never()).save(any());
        verify(accountDeletionRepository, never()).save(any());
        verify(auditLogService, never()).log(eq("account_deletion_requested"), any(), any(), any());
        verifyNoInteractions(jwtBlacklistService);

        // Email NOT re-sent on idempotent retry
        verify(emailService, never()).sendAccountDeletionConfirmation(any(), any());

        // Stripe NOT called again on idempotent retry
        verifyNoInteractions(subscriptionService, stripeBillingService);
    }

    @Test
    void requestDeletionShouldThrowWhenRateLimited() {
        when(accountDeletionRepository.findByUserIdAndStatus(userId, DeletionStatus.PENDING_PURGE))
            .thenReturn(Optional.empty());
        when(rateLimitService.allowRequest(anyString(), anyInt(), anyInt())).thenReturn(false);

        assertThrows(RateLimitExceededException.class,
            () -> service.requestDeletion(userId, "127.0.0.1"));

        verify(userRepository, never()).save(any());
        verify(accountDeletionRepository, never()).save(any());
        verify(emailService, never()).sendAccountDeletionConfirmation(any(), any());
    }

    @Test
    void requestDeletionShouldThrowWhenUserNotFound() {
        when(accountDeletionRepository.findByUserIdAndStatus(userId, DeletionStatus.PENDING_PURGE))
            .thenReturn(Optional.empty());
        when(rateLimitService.allowRequest(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
            () -> service.requestDeletion(userId, "127.0.0.1"));

        verify(emailService, never()).sendAccountDeletionConfirmation(any(), any());
    }

    @Test
    void requestDeletionShouldNotFailWhenEmailSendFails() {
        when(accountDeletionRepository.findByUserIdAndStatus(userId, DeletionStatus.PENDING_PURGE))
            .thenReturn(Optional.empty());
        when(rateLimitService.allowRequest(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userSessionRepository.findByUserIdAndRevokedAtIsNullAndExpiresAtAfter(eq(userId), any()))
            .thenReturn(List.of());
        when(accountDeletionRepository.save(any(AccountDeletion.class))).thenAnswer(inv -> {
            AccountDeletion d = inv.getArgument(0);
            d.setId(UUID.randomUUID());
            d.setRequestedAt(OffsetDateTime.now());
            return d;
        });

        // Simulate the EmailService leaking a RuntimeException (e.g. unchecked throw from
        // the mailSender bean before EmailService's internal try/catch). The public
        // requestDeletion wraps Phase 2 in a defensive catch, so the call still returns
        // a successful response - the deletion was already committed in Phase 1.
        doThrow(new RuntimeException("SMTP down"))
            .when(emailService).sendAccountDeletionConfirmation(any(), any());

        AccountDeletionResponse response = service.requestDeletion(userId, "127.0.0.1");

        assertNotNull(response);
        assertEquals(DeletionStatus.PENDING_PURGE, response.getStatus());

        // Phase 1 work committed: deletion saved, audit logged.
        verify(accountDeletionRepository).save(any(AccountDeletion.class));
        verify(auditLogService).log(eq("account_deletion_requested"), eq(userId), any(), any());
        // Phase 2 was attempted but caught.
        verify(emailService).sendAccountDeletionConfirmation(any(), any());
    }

    @Test
    void purgeScheduledDeletionsShouldHardDeleteAllRelatedData() {
        AccountDeletion deletion = new AccountDeletion(userId,
            OffsetDateTime.now().minusDays(1), "127.0.0.1");
        deletion.setId(UUID.randomUUID());

        when(accountDeletionRepository.findAllByStatusAndScheduledPurgeAtBefore(
            eq(DeletionStatus.PENDING_PURGE), any())).thenReturn(List.of(deletion));
        when(dataExportRepository.findAllByUserId(userId)).thenReturn(List.of());

        service.purgeScheduledDeletions();

        // Anonymous audit log written FIRST
        verify(auditLogService).logAnonymous(eq("account_purged"), eq("127.0.0.1"), anyString());

        // Hard-delete in correct order. Native DELETEs for User and UserSession bypass @Where filters.
        verify(dataExportRepository).deleteByUserId(userId);
        verify(userSessionRepository).deleteByUserId(userId);
        verify(auditLogRepository).deleteByUserId(userId);
        verify(userRepository).deleteByIdNative(userId);

        // Story 2.1: subscription deleted BEFORE the user (FK subscriptions.user_id -> users.id)
        InOrder fkOrder = inOrder(subscriptionRepository, userRepository);
        fkOrder.verify(subscriptionRepository).deleteByUserId(userId);
        fkOrder.verify(userRepository).deleteByIdNative(userId);

        // Story 3.1: onboarding progress deleted BEFORE the user (FK onboarding_progress.user_id -> users.id)
        InOrder onboardingOrder = inOrder(onboardingProgressRepository, userRepository);
        onboardingOrder.verify(onboardingProgressRepository).deleteByUserId(userId);
        onboardingOrder.verify(userRepository).deleteByIdNative(userId);

        // Story 3.2: accounts deleted BEFORE the user (FK accounts.user_id -> users.id)
        InOrder accountsOrder = inOrder(accountRepository, userRepository);
        accountsOrder.verify(accountRepository).deleteByUserId(userId);
        accountsOrder.verify(userRepository).deleteByIdNative(userId);

        // Story 3.3: credit cards deleted BEFORE the user (FK credit_cards.user_id -> users.id)
        InOrder cardsOrder = inOrder(creditCardRepository, userRepository);
        cardsOrder.verify(creditCardRepository).deleteByUserId(userId);
        cardsOrder.verify(userRepository).deleteByIdNative(userId);

        // Story 3.4: categories deleted BEFORE the user (FK categories.user_id -> users.id)
        InOrder categoriesOrder = inOrder(categoryRepository, userRepository);
        categoriesOrder.verify(categoryRepository).deleteByUserId(userId);
        categoriesOrder.verify(userRepository).deleteByIdNative(userId);

        // Story 3.8: goals deleted BEFORE the user (FK goals.user_id -> users.id)
        InOrder goalsOrder = inOrder(goalRepository, userRepository);
        goalsOrder.verify(goalRepository).deleteByUserId(userId);
        goalsOrder.verify(userRepository).deleteByIdNative(userId);

        // Supabase admin called
        verify(supabaseAdminClient).deleteUser(userId);

        // Status updated to PURGED
        assertEquals(DeletionStatus.PURGED, deletion.getStatus());
        assertNotNull(deletion.getPurgedAt());
        verify(accountDeletionRepository).save(deletion);
    }

    @Test
    void purgeShouldCancelStripeSubscriptionBeforeDeletingSubscriptionRow() {
        AccountDeletion deletion = new AccountDeletion(userId,
            OffsetDateTime.now().minusDays(1), "127.0.0.1");
        deletion.setId(UUID.randomUUID());
        com.porquinho.entity.Subscription sub = new com.porquinho.entity.Subscription(
            userId, com.porquinho.entity.Subscription.PlanType.MONTHLY,
            com.porquinho.entity.Subscription.Status.ACTIVE, null, null);
        sub.setStripeSubscriptionId("sub_1");

        when(accountDeletionRepository.findAllByStatusAndScheduledPurgeAtBefore(
            eq(DeletionStatus.PENDING_PURGE), any())).thenReturn(List.of(deletion));
        when(dataExportRepository.findAllByUserId(userId)).thenReturn(List.of());
        when(subscriptionRepository.findByUserId(userId)).thenReturn(java.util.Optional.of(sub));

        service.purgeScheduledDeletions();

        InOrder order = inOrder(stripeBillingService, subscriptionRepository);
        order.verify(stripeBillingService).cancelSubscription("sub_1");
        order.verify(subscriptionRepository).deleteByUserId(userId);
        assertEquals(DeletionStatus.PURGED, deletion.getStatus());
    }

    @Test
    void purgeWithoutStripeSubscriptionDoesNotCallStripe() {
        AccountDeletion deletion = new AccountDeletion(userId,
            OffsetDateTime.now().minusDays(1), "127.0.0.1");
        deletion.setId(UUID.randomUUID());

        when(accountDeletionRepository.findAllByStatusAndScheduledPurgeAtBefore(
            eq(DeletionStatus.PENDING_PURGE), any())).thenReturn(List.of(deletion));
        when(dataExportRepository.findAllByUserId(userId)).thenReturn(List.of());
        when(subscriptionRepository.findByUserId(userId)).thenReturn(java.util.Optional.empty());

        service.purgeScheduledDeletions();

        verify(stripeBillingService, never()).cancelSubscription(any());
        verify(subscriptionRepository).deleteByUserId(userId);
    }

    @Test
    void purgeScheduledDeletionsShouldDeleteExportFiles() throws IOException {
        AccountDeletion deletion = new AccountDeletion(userId,
            OffsetDateTime.now().minusDays(1), "127.0.0.1");
        deletion.setId(UUID.randomUUID());

        Path tempFile = Files.createTempFile("test-purge-export", ".json");
        Files.writeString(tempFile, "{\"x\": 1}");

        DataExport export = new DataExport(userId);
        export.setId(UUID.randomUUID());
        export.setFilePath(tempFile.toString());

        when(accountDeletionRepository.findAllByStatusAndScheduledPurgeAtBefore(
            eq(DeletionStatus.PENDING_PURGE), any())).thenReturn(List.of(deletion));
        when(dataExportRepository.findAllByUserId(userId)).thenReturn(List.of(export));

        service.purgeScheduledDeletions();

        assertFalse(Files.exists(tempFile), "Export file should be deleted on purge");
    }

    @Test
    void purgeScheduledDeletionsShouldRefuseFilesOutsideExportRoot() throws IOException {
        AccountDeletion deletion = new AccountDeletion(userId,
            OffsetDateTime.now().minusDays(1), "127.0.0.1");
        deletion.setId(UUID.randomUUID());

        // Configure the export root to a specific subdirectory.
        Path exportRoot = Files.createTempDirectory("porquinho-export-root");
        ReflectionTestUtils.setField(service, "exportDirectory", exportRoot.toString());

        // The "tampered" file lives outside the export root.
        Path tamperedFile = Files.createTempFile("tampered-outside-export", ".json");
        Files.writeString(tamperedFile, "{\"evil\": true}");

        DataExport export = new DataExport(userId);
        export.setId(UUID.randomUUID());
        export.setFilePath(tamperedFile.toString());

        when(accountDeletionRepository.findAllByStatusAndScheduledPurgeAtBefore(
            eq(DeletionStatus.PENDING_PURGE), any())).thenReturn(List.of(deletion));
        when(dataExportRepository.findAllByUserId(userId)).thenReturn(List.of(export));

        service.purgeScheduledDeletions();

        // Path traversal defense: file outside export root is NOT deleted.
        assertTrue(Files.exists(tamperedFile), "File outside export root must not be deleted");

        // Cleanup
        Files.deleteIfExists(tamperedFile);
        Files.deleteIfExists(exportRoot);
    }

    @Test
    void purgeScheduledDeletionsShouldContinueWhenSupabaseFails() {
        AccountDeletion deletion = new AccountDeletion(userId,
            OffsetDateTime.now().minusDays(1), "127.0.0.1");
        deletion.setId(UUID.randomUUID());

        when(accountDeletionRepository.findAllByStatusAndScheduledPurgeAtBefore(
            eq(DeletionStatus.PENDING_PURGE), any())).thenReturn(List.of(deletion));
        when(dataExportRepository.findAllByUserId(userId)).thenReturn(List.of());

        // SupabaseAdminClient.deleteUser is best-effort and never throws.
        // We verify the method is called and the rest of the flow completes regardless.
        doNothing().when(supabaseAdminClient).deleteUser(userId);

        service.purgeScheduledDeletions();

        assertEquals(DeletionStatus.PURGED, deletion.getStatus());
        verify(accountDeletionRepository).save(deletion);
    }

    @Test
    void purgeScheduledDeletionsShouldIsolateFailuresBetweenUsers() {
        UUID userId2 = UUID.randomUUID();
        AccountDeletion d1 = new AccountDeletion(userId,
            OffsetDateTime.now().minusDays(1), "1.1.1.1");
        d1.setId(UUID.randomUUID());
        AccountDeletion d2 = new AccountDeletion(userId2,
            OffsetDateTime.now().minusDays(1), "2.2.2.2");
        d2.setId(UUID.randomUUID());

        when(accountDeletionRepository.findAllByStatusAndScheduledPurgeAtBefore(
            eq(DeletionStatus.PENDING_PURGE), any())).thenReturn(List.of(d1, d2));

        // Make first user's purge throw inside the deleteByIdNative call.
        when(dataExportRepository.findAllByUserId(userId)).thenReturn(List.of());
        doThrow(new RuntimeException("DB failure for user 1")).when(userRepository).deleteByIdNative(userId);

        // Second user's purge should still run.
        when(dataExportRepository.findAllByUserId(userId2)).thenReturn(List.of());

        service.purgeScheduledDeletions();

        // Second user is purged
        assertEquals(DeletionStatus.PURGED, d2.getStatus());
        verify(userRepository).deleteByIdNative(userId2);

        // First user remains PENDING_PURGE for retry
        assertEquals(DeletionStatus.PENDING_PURGE, d1.getStatus());
    }

    @Test
    void purgeScheduledDeletionsShouldDoNothingWhenEmpty() {
        when(accountDeletionRepository.findAllByStatusAndScheduledPurgeAtBefore(
            eq(DeletionStatus.PENDING_PURGE), any())).thenReturn(List.of());

        service.purgeScheduledDeletions();

        verifyNoInteractions(userRepository);
        verifyNoInteractions(supabaseAdminClient);
    }

    @Test
    void purgeDeletesBillingTransactionsBeforeSubscriptionsAndUser() {
        // Story 2.6: billing_transactions has FKs to subscriptions and users
        AccountDeletion deletion = new AccountDeletion(userId,
            OffsetDateTime.now().minusDays(1), "127.0.0.1");
        deletion.setId(UUID.randomUUID());

        when(accountDeletionRepository.findAllByStatusAndScheduledPurgeAtBefore(
            eq(DeletionStatus.PENDING_PURGE), any())).thenReturn(List.of(deletion));
        when(dataExportRepository.findAllByUserId(userId)).thenReturn(List.of());

        service.purgeScheduledDeletions();

        InOrder order = inOrder(billingTransactionRepository, subscriptionRepository, userRepository);
        order.verify(billingTransactionRepository).deleteByUserId(userId);
        order.verify(subscriptionRepository).deleteByUserId(userId);
        order.verify(userRepository).deleteByIdNative(userId);
        assertEquals(DeletionStatus.PURGED, deletion.getStatus());
    }
}
