package com.porquinho.service;

import com.porquinho.entity.AiClassificationHistory;
import com.porquinho.entity.Account;
import com.porquinho.entity.AccountBalanceHistory;
import com.porquinho.entity.Reconciliation;
import com.porquinho.entity.Transaction;
import com.porquinho.entity.TransactionImport;
import com.porquinho.entity.Transfer;
import com.porquinho.entity.Category;
import com.porquinho.entity.CreditCard;
import com.porquinho.entity.Goal;
import com.porquinho.entity.AuditLog;
import com.porquinho.entity.BillingTransaction;
import com.porquinho.entity.DataExport;
import com.porquinho.entity.DataExport.ExportStatus;
import com.porquinho.entity.OnboardingProgress;
import com.porquinho.entity.Subscription;
import com.porquinho.entity.User;
import com.porquinho.entity.User.AuthProvider;
import com.porquinho.entity.UserSession;
import com.porquinho.exception.RateLimitExceededException;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.repository.AiClassificationHistoryRepository;
import com.porquinho.repository.AccountBalanceHistoryRepository;
import com.porquinho.repository.ReconciliationRepository;
import com.porquinho.repository.TransactionImportRepository;
import com.porquinho.repository.TransactionRepository;
import com.porquinho.repository.TransferRepository;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.persistence.Column;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataExportServiceTest {

    @Mock
    private DataExportRepository dataExportRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSessionRepository userSessionRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private EmailService emailService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private BillingTransactionRepository billingTransactionRepository;

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

    @Mock
    private AccountBalanceHistoryRepository accountBalanceHistoryRepository;

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private ReconciliationRepository reconciliationRepository;

    @Mock
    private TransactionImportRepository transactionImportRepository;

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private AiClassificationHistoryRepository aiClassificationHistoryRepository;

    @InjectMocks
    private DataExportService dataExportService;

    private UUID userId;
    private User testUser;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        testUser = new User("test@example.com", AuthProvider.EMAIL, null);
        testUser.setId(userId);
        testUser.setCreatedAt(LocalDateTime.now());
        testUser.setUpdatedAt(LocalDateTime.now());

        ReflectionTestUtils.setField(dataExportService, "exportDirectory", System.getProperty("java.io.tmpdir") + "/porquinho-test-exports");
        ReflectionTestUtils.setField(dataExportService, "expirationHours", 24);
        ReflectionTestUtils.setField(dataExportService, "baseUrl", "http://localhost:8080");
    }

    @Test
    void requestExportShouldCreateExportAndReturnResponse() {
        when(rateLimitService.allowRequest(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(dataExportRepository.save(any(DataExport.class))).thenAnswer(inv -> {
            DataExport export = inv.getArgument(0);
            export.setId(UUID.randomUUID());
            export.setCreatedAt(LocalDateTime.now());
            return export;
        });
        when(dataExportRepository.findById(any())).thenReturn(Optional.of(createTestExport()));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userSessionRepository.findAllByUserId(userId)).thenReturn(List.of());
        when(auditLogRepository.findByUserIdOrderByTimestampDesc(userId)).thenReturn(List.of());

        var response = dataExportService.requestExport(userId, "127.0.0.1");

        assertNotNull(response);
        assertNotNull(response.getId());
        assertEquals("PROCESSING", response.getStatus());
        verify(dataExportRepository, atLeastOnce()).save(any(DataExport.class));
    }

    @Test
    void requestExportShouldThrowWhenRateLimited() {
        when(rateLimitService.allowRequest(anyString(), anyInt(), anyInt())).thenReturn(false);

        assertThrows(RateLimitExceededException.class, () ->
            dataExportService.requestExport(userId, "127.0.0.1"));

        verify(dataExportRepository, never()).save(any());
    }

    @Test
    void collectUserDataShouldSanitizeSensitiveFields() {
        testUser.setFailedLoginAttempts(3);
        testUser.setLockedUntil(LocalDateTime.now());
        testUser.setLgpdConsentAt(LocalDateTime.now());

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        UserSession session = new UserSession(userId, "hash123", "Chrome", "192.168.1.1", OffsetDateTime.now().plusDays(7));
        session.setId(UUID.randomUUID());
        when(userSessionRepository.findAllByUserId(userId)).thenReturn(List.of(session));

        AuditLog log = new AuditLog(userId, "user_login", "192.168.1.1", null);
        log.setId(UUID.randomUUID());
        when(auditLogRepository.findByUserIdOrderByTimestampDesc(userId)).thenReturn(List.of(log));

        Map<String, Object> data = dataExportService.collectUserData(userId);

        // Verify metadata
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) data.get("metadata");
        assertEquals("1.0", metadata.get("data_version"));
        assertEquals(userId.toString(), metadata.get("user_id"));

        // Verify user profile - sensitive fields excluded
        @SuppressWarnings("unchecked")
        Map<String, Object> userProfile = (Map<String, Object>) data.get("user_profile");
        assertEquals("test@example.com", userProfile.get("email"));
        assertFalse(userProfile.containsKey("failed_login_attempts"));
        assertFalse(userProfile.containsKey("locked_until"));
        assertFalse(userProfile.containsKey("deleted_at"));
        assertFalse(userProfile.containsKey("deleted_by"));
        assertTrue(userProfile.containsKey("lgpd_consent_at"));

        // Verify sessions - jwt_token_hash excluded
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sessions = (List<Map<String, Object>>) data.get("sessions");
        assertEquals(1, sessions.size());
        assertFalse(sessions.get(0).containsKey("jwt_token_hash"));
        assertEquals("Chrome", sessions.get(0).get("device_info"));

        // Verify audit logs present
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> auditLogs = (List<Map<String, Object>>) data.get("audit_logs");
        assertEquals(1, auditLogs.size());
        assertEquals("user_login", auditLogs.get(0).get("event_type"));
    }

    @Test
    void collectUserDataShouldIncludeSubscription() {
        // Story 2.1 AC6: export includes the user's subscription
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userSessionRepository.findAllByUserId(userId)).thenReturn(List.of());
        when(auditLogRepository.findByUserIdOrderByTimestampDesc(userId)).thenReturn(List.of());

        OffsetDateTime start = OffsetDateTime.parse("2026-10-01T00:00:00Z");
        Subscription sub = new Subscription(userId, Subscription.PlanType.TRIAL, Subscription.Status.ACTIVE,
            start, start.plusDays(30));
        sub.setId(UUID.randomUUID());
        sub.setCreatedAt(start);
        sub.setUpdatedAt(start);
        when(subscriptionService.findByUserId(userId)).thenReturn(Optional.of(sub));
        when(subscriptionService.daysRemaining(sub)).thenReturn(12L);
        when(subscriptionService.hasAccess(sub)).thenReturn(true);

        Map<String, Object> data = dataExportService.collectUserData(userId);

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) data.get("metadata");
        assertTrue(((List<?>) metadata.get("exported_tables")).contains("subscriptions"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> subs = (List<Map<String, Object>>) data.get("subscriptions");
        assertEquals(1, subs.size());
        assertEquals("TRIAL", subs.get(0).get("plan_type"));
        assertEquals("ACTIVE", subs.get(0).get("status"));
        assertEquals(start.plusDays(30).toString(), subs.get(0).get("trial_end_date"));
        assertEquals(12L, subs.get(0).get("days_remaining"));
        assertEquals(true, subs.get(0).get("has_access"));
        // All stored columns exported (LGPD portability)
        assertEquals(sub.getId().toString(), subs.get(0).get("id"));
        assertEquals(start.toString(), subs.get(0).get("created_at"));
        assertEquals(start.toString(), subs.get(0).get("updated_at"));
        assertTrue(subs.get(0).containsKey("next_billing_date"));
        assertTrue(subs.get(0).containsKey("payment_method_id"));
        assertTrue(subs.get(0).containsKey("deleted_at"));
        assertNull(subs.get(0).get("next_billing_date"));
        assertNull(subs.get(0).get("payment_method_id"));
        assertExportsEveryColumn(Subscription.class, subs.get(0));
        assertEquals(userId.toString(), subs.get(0).get("user_id"));
        assertEquals(0, subs.get(0).get("failed_payment_attempts"));
    }

    @Test
    void collectUserDataShouldExportBillingColumnsOfSubscription() {
        // Retro Epic 2 F3: columns added in V10-V14 are exported too
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        OffsetDateTime start = OffsetDateTime.parse("2026-10-01T00:00:00Z");
        Subscription sub = new Subscription(userId, Subscription.PlanType.MONTHLY, Subscription.Status.PAYMENT_FAILED,
            start, start.plusDays(30));
        sub.setStripeCustomerId("cus_1");
        sub.setStripeSubscriptionId("sub_1");
        sub.setTrialReminder7dSentAt(start.plusDays(23));
        sub.setTrialReminder1dSentAt(start.plusDays(29));
        sub.setCancellationDate(start.plusDays(40));
        sub.setFailedPaymentAttempts(2);
        sub.setPaymentUpdateUrl("https://invoice.stripe.com/i/x");
        when(subscriptionService.findByUserId(userId)).thenReturn(Optional.of(sub));

        @SuppressWarnings("unchecked")
        Map<String, Object> exported = ((List<Map<String, Object>>) dataExportService.collectUserData(userId)
            .get("subscriptions")).get(0);

        assertEquals("cus_1", exported.get("stripe_customer_id"));
        assertEquals("sub_1", exported.get("stripe_subscription_id"));
        assertEquals(start.plusDays(23).toString(), exported.get("trial_reminder_7d_sent_at"));
        assertEquals(start.plusDays(29).toString(), exported.get("trial_reminder_1d_sent_at"));
        assertEquals(start.plusDays(40).toString(), exported.get("cancellation_date"));
        assertEquals(2, exported.get("failed_payment_attempts"));
        assertEquals("https://invoice.stripe.com/i/x", exported.get("payment_update_url"));
    }

    @Test
    void collectUserDataShouldExportOnboardingProgress() {
        // Story 3.1: onboarding_progress is exported with every stored column
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        OffsetDateTime created = OffsetDateTime.parse("2026-11-01T12:00:00Z");
        OnboardingProgress progress = new OnboardingProgress(userId);
        progress.setId(UUID.randomUUID());
        progress.setCurrentStep(2);
        progress.addStep("tutorial_skipped");
        progress.setCreatedAt(created);
        progress.setUpdatedAt(created.plusMinutes(3));
        when(onboardingProgressRepository.findByUserId(userId)).thenReturn(Optional.of(progress));

        Map<String, Object> data = dataExportService.collectUserData(userId);

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) data.get("metadata");
        assertTrue(((List<?>) metadata.get("exported_tables")).contains("onboarding_progress"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) data.get("onboarding_progress");
        assertEquals(1, rows.size());
        Map<String, Object> exported = rows.get(0);
        assertExportsEveryColumn(OnboardingProgress.class, exported);
        assertEquals(progress.getId().toString(), exported.get("id"));
        assertEquals(userId.toString(), exported.get("user_id"));
        assertEquals(2, exported.get("current_step"));
        assertEquals(List.of("tutorial_skipped"), exported.get("steps_completed"));
        assertEquals(false, exported.get("is_completed"));
        assertEquals(created.toString(), exported.get("created_at"));
        assertEquals(created.plusMinutes(3).toString(), exported.get("updated_at"));
    }

    @Test
    void collectUserDataShouldExportEmptyOnboardingListWhenNone() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        assertEquals(List.of(), dataExportService.collectUserData(userId).get("onboarding_progress"));
    }

    @Test
    void collectUserDataShouldExportAccountsIncludingRemovedOnes() {
        // Story 3.2: accounts are exported with every stored column, removed (soft-deleted) ones included
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        OffsetDateTime created = OffsetDateTime.parse("2026-11-02T12:00:00Z");
        Account active = new Account(userId, "Nubank", "Nubank", Account.AccountType.CHECKING, new java.math.BigDecimal("1234.56"));
        active.setId(UUID.randomUUID());
        active.setCreatedAt(created);
        active.setUpdatedAt(created);
        Account removed = new Account(userId, "Poupança", null, Account.AccountType.SAVINGS, new java.math.BigDecimal("-150.00"));
        removed.setId(UUID.randomUUID());
        removed.setCreatedAt(created.plusMinutes(1));
        removed.setUpdatedAt(created.plusMinutes(2));
        removed.setDeletedAt(created.plusMinutes(2));
        removed.setLastReconciliationDate(created.plusMinutes(1));
        removed.setVersion(3L);
        when(accountRepository.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(active, removed));

        Map<String, Object> data = dataExportService.collectUserData(userId);

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) data.get("metadata");
        assertTrue(((List<?>) metadata.get("exported_tables")).contains("accounts"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) data.get("accounts");
        assertEquals(2, rows.size());
        Map<String, Object> first = rows.get(0);
        assertExportsEveryColumn(Account.class, first);
        assertEquals(active.getId().toString(), first.get("id"));
        assertEquals(userId.toString(), first.get("user_id"));
        assertEquals("Nubank", first.get("account_name"));
        assertEquals("Nubank", first.get("bank_name"));
        assertEquals("CHECKING", first.get("account_type"));
        assertEquals(new java.math.BigDecimal("1234.56"), first.get("initial_balance"));
        assertEquals(new java.math.BigDecimal("1234.56"), first.get("current_balance"));
        assertEquals(created.toString(), first.get("created_at"));
        assertNull(first.get("deleted_at"));
        assertNull(first.get("last_reconciliation_date"));

        Map<String, Object> second = rows.get(1);
        assertExportsEveryColumn(Account.class, second);
        assertEquals(created.plusMinutes(1).toString(), second.get("last_reconciliation_date"));
        assertEquals(3L, second.get("version"));
        assertEquals("SAVINGS", second.get("account_type"));
        assertNull(second.get("bank_name"));
        assertEquals(new java.math.BigDecimal("-150.00"), second.get("initial_balance"));
        assertEquals(created.plusMinutes(2).toString(), second.get("deleted_at"));
    }

    @Test
    void collectUserDataShouldExportEmptyAccountListWhenNone() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        assertEquals(List.of(), dataExportService.collectUserData(userId).get("accounts"));
    }

    @Test
    void collectUserDataShouldExportCreditCardsIncludingRemovedOnes() {
        // Story 3.3: credit cards are exported with every stored column, removed (soft-deleted) ones included
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        OffsetDateTime created = OffsetDateTime.parse("2026-11-02T12:00:00Z");
        CreditCard active = new CreditCard(userId, "Nubank Roxinho", "Nubank", new java.math.BigDecimal("5000.00"), 10);
        active.setId(UUID.randomUUID());
        active.setCreatedAt(created);
        active.setUpdatedAt(created);
        CreditCard removed = new CreditCard(userId, "Inter Gold", null, new java.math.BigDecimal("0.00"), 31);
        removed.setId(UUID.randomUUID());
        removed.setCreatedAt(created.plusMinutes(1));
        removed.setUpdatedAt(created.plusMinutes(2));
        removed.setDeletedAt(created.plusMinutes(2));
        when(creditCardRepository.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(active, removed));

        Map<String, Object> data = dataExportService.collectUserData(userId);

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) data.get("metadata");
        assertTrue(((List<?>) metadata.get("exported_tables")).contains("credit_cards"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) data.get("credit_cards");
        assertEquals(2, rows.size());
        Map<String, Object> first = rows.get(0);
        assertExportsEveryColumn(CreditCard.class, first);
        assertEquals(active.getId().toString(), first.get("id"));
        assertEquals(userId.toString(), first.get("user_id"));
        assertEquals("Nubank Roxinho", first.get("card_name"));
        assertEquals("Nubank", first.get("bank_name"));
        assertEquals(new java.math.BigDecimal("5000.00"), first.get("card_limit"));
        assertEquals(10, first.get("due_day"));
        assertEquals(created.toString(), first.get("created_at"));
        assertNull(first.get("deleted_at"));

        Map<String, Object> second = rows.get(1);
        assertExportsEveryColumn(CreditCard.class, second);
        assertNull(second.get("bank_name"));
        assertEquals(31, second.get("due_day"));
        assertEquals(created.plusMinutes(2).toString(), second.get("deleted_at"));
    }

    @Test
    void collectUserDataShouldExportEmptyCreditCardListWhenNone() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        assertEquals(List.of(), dataExportService.collectUserData(userId).get("credit_cards"));
    }

    @Test
    void collectUserDataShouldExportCategoriesIncludingRemovedOnes() {
        // Story 3.4: categories are exported with every stored column, removed (soft-deleted) ones included
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        OffsetDateTime created = OffsetDateTime.parse("2026-11-02T12:00:00Z");
        Category parent = new Category(userId, "Essenciais", null, "#2563EB", "shield-check", true);
        parent.setId(UUID.randomUUID());
        parent.setCreatedAt(created);
        parent.setUpdatedAt(created);
        Category child = new Category(userId, "Moradia", parent.getId(), "#3B82F6", "house", true);
        child.setId(UUID.randomUUID());
        child.setCreatedAt(created.plusMinutes(1));
        child.setUpdatedAt(created.plusMinutes(2));
        child.setDeletedAt(created.plusMinutes(2));
        when(categoryRepository.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(parent, child));

        Map<String, Object> data = dataExportService.collectUserData(userId);

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) data.get("metadata");
        assertTrue(((List<?>) metadata.get("exported_tables")).contains("categories"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) data.get("categories");
        assertEquals(2, rows.size());
        Map<String, Object> first = rows.get(0);
        assertExportsEveryColumn(Category.class, first);
        assertEquals(parent.getId().toString(), first.get("id"));
        assertEquals(userId.toString(), first.get("user_id"));
        assertEquals("Essenciais", first.get("category_name"));
        assertNull(first.get("parent_category_id"));
        assertEquals("#2563EB", first.get("color_hex"));
        assertEquals("shield-check", first.get("icon_name"));
        assertEquals(true, first.get("is_system_default"));
        assertNull(first.get("deleted_at"));

        Map<String, Object> second = rows.get(1);
        assertExportsEveryColumn(Category.class, second);
        assertEquals(parent.getId().toString(), second.get("parent_category_id"));
        assertEquals(created.plusMinutes(2).toString(), second.get("deleted_at"));
    }

    @Test
    void collectUserDataShouldExportEmptyCategoryListWhenNone() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        assertEquals(List.of(), dataExportService.collectUserData(userId).get("categories"));
    }

    @Test
    void collectUserDataShouldExportGoalsIncludingRemovedOnes() {
        // Story 3.8: goals are exported with every stored column, removed (soft-deleted) ones included
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        OffsetDateTime created = OffsetDateTime.parse("2026-11-02T12:00:00Z");
        Goal active = new Goal(userId, "Reserva de Emergência", new java.math.BigDecimal("25000.00"),
            java.time.LocalDate.parse("2027-11-02"), new java.math.BigDecimal("2083.34"));
        active.setId(UUID.randomUUID());
        active.setCreatedAt(created);
        active.setUpdatedAt(created);
        Goal removed = new Goal(userId, "Viagem", new java.math.BigDecimal("8000.00"), null, null);
        removed.setId(UUID.randomUUID());
        removed.setDescription("Férias");
        removed.setCreatedAt(created.plusMinutes(1));
        removed.setUpdatedAt(created.plusMinutes(2));
        removed.setDeletedAt(created.plusMinutes(2));
        when(goalRepository.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(active, removed));

        Map<String, Object> data = dataExportService.collectUserData(userId);

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) data.get("metadata");
        assertTrue(((List<?>) metadata.get("exported_tables")).contains("goals"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) data.get("goals");
        assertEquals(2, rows.size());
        Map<String, Object> first = rows.get(0);
        assertExportsEveryColumn(Goal.class, first);
        assertEquals(active.getId().toString(), first.get("id"));
        assertEquals(userId.toString(), first.get("user_id"));
        assertEquals("Reserva de Emergência", first.get("goal_name"));
        assertNull(first.get("description"));
        assertEquals(new java.math.BigDecimal("25000.00"), first.get("target_amount"));
        assertEquals(new java.math.BigDecimal("0.00"), first.get("current_amount"));
        assertEquals("2027-11-02", first.get("target_date"));
        assertEquals(new java.math.BigDecimal("2083.34"), first.get("monthly_allocation"));
        assertEquals(false, first.get("is_completed"));
        assertNull(first.get("deleted_at"));

        Map<String, Object> second = rows.get(1);
        assertExportsEveryColumn(Goal.class, second);
        assertEquals("Férias", second.get("description"));
        assertNull(second.get("target_date"));
        assertNull(second.get("monthly_allocation"));
        assertEquals(created.plusMinutes(2).toString(), second.get("deleted_at"));
    }

    @Test
    void collectUserDataShouldExportEmptyGoalListWhenNone() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        assertEquals(List.of(), dataExportService.collectUserData(userId).get("goals"));
    }

    @Test
    void collectUserDataShouldExportTheAccountBalanceHistory() {
        // Story 4.3: every stored column of account_balance_history, oldest first
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        OffsetDateTime created = OffsetDateTime.parse("2026-11-03T12:00:00Z");
        UUID accountId = UUID.randomUUID();
        AccountBalanceHistory initial = new AccountBalanceHistory(accountId, userId, new java.math.BigDecimal("100.00"),
            AccountBalanceHistory.EVENT_INITIAL);
        initial.setId(UUID.randomUUID());
        initial.setSnapshotDate(created);
        initial.setCreatedAt(created);
        AccountBalanceHistory edit = new AccountBalanceHistory(accountId, userId, new java.math.BigDecimal("-250.00"),
            AccountBalanceHistory.EVENT_EDIT);
        edit.setId(UUID.randomUUID());
        edit.setSnapshotDate(created.plusMinutes(5));
        edit.setCreatedAt(created.plusMinutes(5));
        when(accountBalanceHistoryRepository.findByUserIdOrdered(userId)).thenReturn(List.of(initial, edit));

        Map<String, Object> data = dataExportService.collectUserData(userId);

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) data.get("metadata");
        assertTrue(((List<?>) metadata.get("exported_tables")).contains("account_balance_history"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) data.get("account_balance_history");
        assertEquals(2, rows.size());
        Map<String, Object> first = rows.get(0);
        assertExportsEveryColumn(AccountBalanceHistory.class, first);
        assertEquals(initial.getId().toString(), first.get("id"));
        assertEquals(accountId.toString(), first.get("account_id"));
        assertEquals(userId.toString(), first.get("user_id"));
        assertEquals(new java.math.BigDecimal("100.00"), first.get("balance_snapshot"));
        assertEquals(created.toString(), first.get("snapshot_date"));
        assertEquals("initial", first.get("created_by_event"));
        assertEquals(created.toString(), first.get("created_at"));

        Map<String, Object> second = rows.get(1);
        assertExportsEveryColumn(AccountBalanceHistory.class, second);
        assertEquals(new java.math.BigDecimal("-250.00"), second.get("balance_snapshot"));
        assertEquals("edit", second.get("created_by_event"));
    }

    @Test
    void collectUserDataShouldExportEmptyBalanceHistoryWhenNone() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        assertEquals(List.of(), dataExportService.collectUserData(userId).get("account_balance_history"));
    }

    @Test
    void collectUserDataShouldExportTheTransfers() {
        // Story 4.5: every stored column of transfers, oldest first, removed ones included
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        OffsetDateTime created = OffsetDateTime.parse("2026-11-03T12:00:00Z");
        UUID fromId = UUID.randomUUID();
        UUID toId = UUID.randomUUID();
        Transfer active = new Transfer(UUID.randomUUID(), userId, fromId, toId, new java.math.BigDecimal("200.00"),
            java.time.LocalDate.parse("2026-11-03"), "Reserva");
        active.setId(UUID.randomUUID());
        active.setCreatedAt(created);
        Transfer removed = new Transfer(UUID.randomUUID(), userId, toId, fromId, new java.math.BigDecimal("50.00"),
            java.time.LocalDate.parse("2026-11-02"), null);
        removed.setId(UUID.randomUUID());
        removed.setCreatedAt(created.plusMinutes(5));
        removed.setDeletedAt(created.plusMinutes(10));
        when(transferRepository.findByUserIdOrdered(userId)).thenReturn(List.of(active, removed));

        Map<String, Object> data = dataExportService.collectUserData(userId);

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) data.get("metadata");
        assertTrue(((List<?>) metadata.get("exported_tables")).contains("transfers"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) data.get("transfers");
        assertEquals(2, rows.size());
        Map<String, Object> first = rows.get(0);
        assertExportsEveryColumn(Transfer.class, first);
        assertEquals(active.getId().toString(), first.get("id"));
        assertEquals(userId.toString(), first.get("user_id"));
        assertEquals(fromId.toString(), first.get("from_account_id"));
        assertEquals(toId.toString(), first.get("to_account_id"));
        assertEquals(new java.math.BigDecimal("200.00"), first.get("amount"));
        assertEquals("2026-11-03", first.get("transfer_date"));
        assertEquals("Reserva", first.get("description"));
        assertEquals(created.toString(), first.get("created_at"));
        assertNull(first.get("deleted_at"));

        Map<String, Object> second = rows.get(1);
        assertExportsEveryColumn(Transfer.class, second);
        assertNull(second.get("description"));
        assertEquals(created.plusMinutes(10).toString(), second.get("deleted_at"));
    }

    @Test
    void collectUserDataShouldExportEmptyTransferListWhenNone() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        assertEquals(List.of(), dataExportService.collectUserData(userId).get("transfers"));
    }

    @Test
    void collectUserDataShouldExportTheReconciliations() {
        // Story 4.6: every stored column of reconciliation_history, oldest first
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        OffsetDateTime at = OffsetDateTime.parse("2026-11-03T12:00:00Z");
        UUID accountId = UUID.randomUUID();
        Reconciliation adjusted = new Reconciliation(accountId, userId, new java.math.BigDecimal("1000.00"),
            new java.math.BigDecimal("1050.00"), new java.math.BigDecimal("50.00"), "Tarifa esquecida", at);
        adjusted.setId(UUID.randomUUID());
        adjusted.setCreatedAt(at);
        Reconciliation matched = new Reconciliation(accountId, userId, new java.math.BigDecimal("1050.00"),
            new java.math.BigDecimal("1050.00"), new java.math.BigDecimal("0.00"), null, at.plusDays(1));
        matched.setId(UUID.randomUUID());
        matched.setCreatedAt(at.plusDays(1));
        when(reconciliationRepository.findByUserIdOrdered(userId)).thenReturn(List.of(adjusted, matched));

        Map<String, Object> data = dataExportService.collectUserData(userId);

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) data.get("metadata");
        assertTrue(((List<?>) metadata.get("exported_tables")).contains("reconciliation_history"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) data.get("reconciliation_history");
        assertEquals(2, rows.size());
        Map<String, Object> first = rows.get(0);
        assertExportsEveryColumn(Reconciliation.class, first);
        assertEquals(adjusted.getId().toString(), first.get("id"));
        assertEquals(accountId.toString(), first.get("account_id"));
        assertEquals(userId.toString(), first.get("user_id"));
        assertEquals(new java.math.BigDecimal("1000.00"), first.get("previous_balance"));
        assertEquals(new java.math.BigDecimal("1050.00"), first.get("bank_balance"));
        assertEquals(new java.math.BigDecimal("50.00"), first.get("difference"));
        assertEquals("Tarifa esquecida", first.get("reason"));
        assertEquals(at.toString(), first.get("reconciled_at"));
        assertEquals(at.toString(), first.get("created_at"));
        Map<String, Object> second = rows.get(1);
        assertExportsEveryColumn(Reconciliation.class, second);
        assertEquals(new java.math.BigDecimal("0.00"), second.get("difference"));
        assertNull(second.get("reason"));
    }

    @Test
    void collectUserDataShouldExportEmptyReconciliationListWhenNone() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        assertEquals(List.of(), dataExportService.collectUserData(userId).get("reconciliation_history"));
    }

    /** LGPD portability: every mapped column of the entity appears as a key in the exported map. */
    @Test
    void collectUserDataShouldExportTheTransactionImports() {
        // Story 5.1: every stored column of transaction_imports, oldest first (account and card targets)
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        OffsetDateTime created = OffsetDateTime.parse("2026-11-04T12:00:00Z");
        UUID accountId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        TransactionImport toAccount = new TransactionImport(userId, accountId, null, "nubank-conta.ofx",
            TransactionImport.FileType.OFX, 4);
        toAccount.setId(UUID.randomUUID());
        toAccount.setCreatedAt(created);
        toAccount.setUpdatedAt(created);
        TransactionImport toCard = new TransactionImport(userId, null, cardId, "itau-cartao.ofx",
            TransactionImport.FileType.OFX, 3);
        toCard.setId(UUID.randomUUID());
        toCard.setCreatedAt(created.plusMinutes(5));
        toCard.setUpdatedAt(created.plusMinutes(5));
        when(transactionImportRepository.findByUserIdOrdered(userId)).thenReturn(List.of(toAccount, toCard));

        Map<String, Object> data = dataExportService.collectUserData(userId);

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) data.get("metadata");
        assertTrue(((List<?>) metadata.get("exported_tables")).contains("transaction_imports"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) data.get("transaction_imports");
        assertEquals(2, rows.size());
        Map<String, Object> first = rows.get(0);
        assertExportsEveryColumn(TransactionImport.class, first);
        assertEquals(Set.of("id", "user_id", "account_id", "credit_card_id", "file_name", "file_type", "bank_name",
            "file_size_kb", "status", "total_transactions", "imported_transactions", "duplicates_found", "transfers_found",
            "skipped_rows", "classification_total", "classified_count", "error_message", "created_at", "updated_at"),
            first.keySet());
        assertEquals(toAccount.getId().toString(), first.get("id"));
        assertEquals(userId.toString(), first.get("user_id"));
        assertEquals(accountId.toString(), first.get("account_id"));
        assertNull(first.get("credit_card_id"));
        assertEquals("nubank-conta.ofx", first.get("file_name"));
        assertEquals("OFX", first.get("file_type"));
        assertNull(first.get("bank_name"));
        assertEquals(4, first.get("file_size_kb"));
        assertEquals("PROCESSING", first.get("status"));
        assertEquals(0, first.get("total_transactions"));
        assertEquals(created.toString(), first.get("created_at"));
        Map<String, Object> second = rows.get(1);
        assertNull(second.get("account_id"));
        assertEquals(cardId.toString(), second.get("credit_card_id"));
    }

    @Test
    void collectUserDataShouldExportEmptyTransactionImportListWhenNone() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        assertEquals(List.of(), dataExportService.collectUserData(userId).get("transaction_imports"));
    }

    /** LGPD portability: every mapped column of the transactions (Story 5.4), oldest first. */
    @Test
    void collectUserDataShouldExportTheTransactions() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        OffsetDateTime created = OffsetDateTime.parse("2026-11-04T12:00:00Z");
        UUID accountId = UUID.randomUUID();
        UUID importId = UUID.randomUUID();
        Transaction transaction = new Transaction();
        transaction.setId(UUID.randomUUID());
        transaction.setUserId(userId);
        transaction.setAccountId(accountId);
        transaction.setImportId(importId);
        transaction.setExternalId("FIT-1");
        transaction.setTransactionDate(java.time.LocalDate.of(2026, 10, 18));
        transaction.setDescription("Pagamento da fatura - Cartão Nubank");
        transaction.setOriginalDescription("Pagamento da fatura - Cartão Nubank");
        transaction.setAmount(new java.math.BigDecimal("1243.67"));
        transaction.setTransactionType(Transaction.Type.DEBIT);
        transaction.setKind(Transaction.Kind.CARD_PAYMENT);
        transaction.setCreatedAt(created);
        transaction.setUpdatedAt(created);
        when(transactionRepository.findByUserIdOrdered(userId)).thenReturn(List.of(transaction));

        Map<String, Object> data = dataExportService.collectUserData(userId);

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) data.get("metadata");
        assertTrue(((List<?>) metadata.get("exported_tables")).contains("transactions"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) data.get("transactions");
        assertEquals(1, rows.size());
        Map<String, Object> row = rows.get(0);
        assertExportsEveryColumn(Transaction.class, row);
        assertEquals(transaction.getId().toString(), row.get("id"));
        assertEquals(accountId.toString(), row.get("account_id"));
        assertNull(row.get("credit_card_id"));
        assertEquals(importId.toString(), row.get("import_id"));
        assertEquals("2026-10-18", row.get("transaction_date"));
        assertEquals(new java.math.BigDecimal("1243.67"), row.get("amount"));
        assertEquals("DEBIT", row.get("transaction_type"));
        assertEquals("CARD_PAYMENT", row.get("kind"));
        assertEquals("NONE", row.get("classification_source"));
        assertEquals("PENDING", row.get("review_status"));
        assertEquals(false, row.get("affects_balance"));
        assertEquals(false, row.get("is_pix"));
        assertNull(row.get("confirmed_by_import_id"));
        assertNull(row.get("deleted_at"));
    }

    /** Story 5.9, decision 3A: the mark of a confirmed installment goes out too. */
    @Test
    void collectUserDataShouldExportWhichImportConfirmedTheInstallment() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        UUID confirmedBy = UUID.randomUUID();
        Transaction transaction = new Transaction();
        transaction.setId(UUID.randomUUID());
        transaction.setUserId(userId);
        transaction.setConfirmedByImportId(confirmedBy);
        when(transactionRepository.findByUserIdOrdered(userId)).thenReturn(List.of(transaction));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) dataExportService.collectUserData(userId)
            .get("transactions");
        assertEquals(confirmedBy.toString(), rows.get(0).get("confirmed_by_import_id"));
    }

    @Test
    void collectUserDataShouldKeepInvalidPixDetailsAsText() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        Transaction transaction = new Transaction();
        transaction.setId(UUID.randomUUID());
        transaction.setUserId(userId);
        transaction.setPix(true);
        transaction.setPixDetails("nao-json");
        when(transactionRepository.findByUserIdOrdered(userId)).thenReturn(List.of(transaction));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) dataExportService.collectUserData(userId)
            .get("transactions");
        com.fasterxml.jackson.databind.JsonNode details = (com.fasterxml.jackson.databind.JsonNode) rows.get(0).get("pix_details");
        assertTrue(details.isTextual());
        assertEquals("nao-json", details.asText());
    }

    @Test
    void collectUserDataShouldExportPixDetailsAsAnObject() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        Transaction transaction = new Transaction();
        transaction.setId(UUID.randomUUID());
        transaction.setUserId(userId);
        transaction.setPix(true);
        transaction.setPixDetails("{\"detected\":true,\"direction\":\"SENT\",\"counterpart_name\":\"LOJA \\\"BOA\\\"\"}");
        when(transactionRepository.findByUserIdOrdered(userId)).thenReturn(List.of(transaction));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) dataExportService.collectUserData(userId)
            .get("transactions");
        Map<String, Object> row = rows.get(0);
        assertExportsEveryColumn(Transaction.class, row);
        assertEquals(true, row.get("is_pix"));
        com.fasterxml.jackson.databind.JsonNode details = (com.fasterxml.jackson.databind.JsonNode) row.get("pix_details");
        assertTrue(details.isObject());
        assertEquals("SENT", details.get("direction").asText());
        assertEquals("LOJA \"BOA\"", details.get("counterpart_name").asText());
    }

    @Test
    void collectUserDataShouldExportEmptyTransactionListWhenNone() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        assertEquals(List.of(), dataExportService.collectUserData(userId).get("transactions"));
    }

    @Test
    void collectUserDataShouldExportTheAiClassificationHistory() {
        // Story 5.8: every stored column, the corrected fields too
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        UUID transactionId = UUID.randomUUID();
        UUID predicted = UUID.randomUUID();
        UUID corrected = UUID.randomUUID();
        AiClassificationHistory history = new AiClassificationHistory(userId, transactionId, predicted, 60);
        history.setId(UUID.randomUUID());
        history.setWasCorrected(true);
        history.setCorrectedCategoryId(corrected);
        history.setCreatedAt(OffsetDateTime.parse("2026-11-06T12:00:00Z"));
        AiClassificationHistory noCategory = new AiClassificationHistory(userId, UUID.randomUUID(), null, 30);
        noCategory.setId(UUID.randomUUID());
        when(aiClassificationHistoryRepository.findByUserIdOrdered(userId)).thenReturn(List.of(history, noCategory));

        Map<String, Object> data = dataExportService.collectUserData(userId);

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) data.get("metadata");
        assertTrue(((List<?>) metadata.get("exported_tables")).contains("ai_classification_history"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) data.get("ai_classification_history");
        assertEquals(2, rows.size());
        Map<String, Object> first = rows.get(0);
        assertExportsEveryColumn(AiClassificationHistory.class, first);
        assertEquals(Set.of("id", "user_id", "transaction_id", "predicted_category_id", "confidence", "was_corrected",
            "corrected_category_id", "created_at"), first.keySet());
        assertEquals(transactionId.toString(), first.get("transaction_id"));
        assertEquals(predicted.toString(), first.get("predicted_category_id"));
        assertEquals(60, first.get("confidence"));
        assertEquals(true, first.get("was_corrected"));
        assertEquals(corrected.toString(), first.get("corrected_category_id"));
        assertEquals("2026-11-06T12:00Z", first.get("created_at"));
        assertEquals(null, rows.get(1).get("predicted_category_id"));
    }

    private static void assertExportsEveryColumn(Class<?> entity, Map<String, Object> exported) {
        for (Field field : entity.getDeclaredFields()) {
            Column column = field.getAnnotation(Column.class);
            if (column != null) {
                assertTrue(exported.containsKey(column.name()), entity.getSimpleName() + " column not exported: " + column.name());
            }
        }
    }

    @Test
    void collectUserDataShouldExportEmptySubscriptionListWhenNone() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userSessionRepository.findAllByUserId(userId)).thenReturn(List.of());
        when(auditLogRepository.findByUserIdOrderByTimestampDesc(userId)).thenReturn(List.of());
        when(subscriptionService.findByUserId(userId)).thenReturn(Optional.empty());

        Map<String, Object> data = dataExportService.collectUserData(userId);

        assertEquals(List.of(), data.get("subscriptions"));
    }

    @Test
    void downloadExportShouldReturnResourceAndMarkDownloadedAndDeleteFile() throws IOException {
        DataExport export = createTestExport();
        export.setStatus(ExportStatus.READY);
        export.setExpiresAt(LocalDateTime.now().plusHours(12));

        Path tempFile = Files.createTempFile("test-export", ".json");
        Files.writeString(tempFile, "{\"test\": true}");
        export.setFilePath(tempFile.toString());

        UUID token = export.getDownloadToken();
        when(dataExportRepository.findByDownloadToken(token)).thenReturn(Optional.of(export));
        when(dataExportRepository.save(any(DataExport.class))).thenAnswer(inv -> inv.getArgument(0));

        Resource resource = dataExportService.downloadExport(token.toString(), "127.0.0.1");

        assertNotNull(resource);
        assertEquals(ExportStatus.DOWNLOADED, export.getStatus());
        assertNotNull(export.getDownloadedAt());
        verify(dataExportRepository).save(export);
        verify(auditLogService).log("data_export_downloaded", export.getUserId(), "127.0.0.1");

        // File should be deleted after download (AC4: single-use)
        assertFalse(Files.exists(tempFile));
    }

    @Test
    void downloadExportShouldThrowWhenExpired() {
        DataExport export = createTestExport();
        export.setStatus(ExportStatus.READY);
        export.setExpiresAt(LocalDateTime.now().minusHours(1));

        UUID token = export.getDownloadToken();
        when(dataExportRepository.findByDownloadToken(token)).thenReturn(Optional.of(export));
        when(dataExportRepository.save(any(DataExport.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThrows(ResourceNotFoundException.class, () ->
            dataExportService.downloadExport(token.toString(), "127.0.0.1"));
    }

    @Test
    void downloadExportShouldThrowWhenAlreadyDownloaded() {
        DataExport export = createTestExport();
        export.setStatus(ExportStatus.DOWNLOADED);

        UUID token = export.getDownloadToken();
        when(dataExportRepository.findByDownloadToken(token)).thenReturn(Optional.of(export));

        assertThrows(ResourceNotFoundException.class, () ->
            dataExportService.downloadExport(token.toString(), "127.0.0.1"));
    }

    @Test
    void getExportStatusShouldReturnLatestExport() {
        DataExport export = createTestExport();
        export.setStatus(ExportStatus.READY);

        when(dataExportRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(export));

        var response = dataExportService.getExportStatus(userId);

        assertNotNull(response);
        assertEquals("READY", response.getStatus());
    }

    @Test
    void getExportStatusShouldReturnNullWhenNoExport() {
        when(dataExportRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.empty());

        var response = dataExportService.getExportStatus(userId);

        assertNull(response);
    }

    @Test
    void cleanupExpiredExportsShouldDeleteFilesAndUpdateStatus() throws IOException {
        DataExport export = createTestExport();
        export.setStatus(ExportStatus.READY);
        Path tempFile = Files.createTempFile("test-expired", ".json");
        Files.writeString(tempFile, "{}");
        export.setFilePath(tempFile.toString());

        when(dataExportRepository.findByStatusAndExpiresAtBefore(eq(ExportStatus.READY), any()))
            .thenReturn(List.of(export));
        when(dataExportRepository.save(any(DataExport.class))).thenAnswer(inv -> inv.getArgument(0));

        dataExportService.cleanupExpiredExports();

        assertEquals(ExportStatus.EXPIRED, export.getStatus());
        assertFalse(Files.exists(tempFile));
        verify(dataExportRepository).save(export);
    }

    private DataExport createTestExport() {
        DataExport export = new DataExport(userId);
        export.setId(UUID.randomUUID());
        export.setDownloadToken(UUID.randomUUID());
        export.setCreatedAt(LocalDateTime.now());
        export.setExpiresAt(LocalDateTime.now().plusHours(24));
        return export;
    }

    @Test
    void collectUserDataShouldIncludeBillingTransactions() {
        // Story 2.6: payments exported (LGPD portability), in the repository's newest-first order
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        OffsetDateTime paidAt = OffsetDateTime.parse("2026-11-30T12:00:00Z");
        UUID subscriptionId = UUID.randomUUID();
        BillingTransaction newer = new BillingTransaction(subscriptionId, userId, new java.math.BigDecimal("19.90"),
            "brl", BillingTransaction.Status.PAID, "pm_1", "in_2", paidAt);
        newer.setId(UUID.randomUUID());
        newer.setCreatedAt(paidAt);
        BillingTransaction older = new BillingTransaction(subscriptionId, userId, new java.math.BigDecimal("19.90"),
            "brl", BillingTransaction.Status.PAID, "pm_1", "in_1", paidAt.minusMonths(1));
        older.setId(UUID.randomUUID());
        when(billingTransactionRepository.findByUserIdOrderByPaidAtDesc(userId)).thenReturn(List.of(newer, older));

        Map<String, Object> data = dataExportService.collectUserData(userId);

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) data.get("metadata");
        assertTrue(((List<?>) metadata.get("exported_tables")).contains("billing_transactions"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> payments = (List<Map<String, Object>>) data.get("billing_transactions");
        assertEquals(2, payments.size());
        Map<String, Object> first = payments.get(0);
        assertEquals(newer.getId().toString(), first.get("id"));
        assertEquals(subscriptionId.toString(), first.get("subscription_id"));
        assertEquals(new java.math.BigDecimal("19.90"), first.get("amount"));
        assertEquals("brl", first.get("currency"));
        assertEquals("PAID", first.get("status"));
        assertEquals("pm_1", first.get("payment_method"));
        assertEquals("in_2", first.get("stripe_transaction_id"));
        assertEquals(paidAt.toString(), first.get("paid_at"));
        assertEquals(paidAt.toString(), first.get("created_at"));
        assertEquals("in_1", payments.get(1).get("stripe_transaction_id"));
        assertNull(payments.get(1).get("created_at"));
        assertEquals(userId.toString(), first.get("user_id"));
        assertExportsEveryColumn(BillingTransaction.class, first);
    }

    @Test
    void collectUserDataShouldExportEmptyBillingTransactionsWhenNone() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        Map<String, Object> data = dataExportService.collectUserData(userId);

        assertEquals(List.of(), data.get("billing_transactions"));
    }
}
