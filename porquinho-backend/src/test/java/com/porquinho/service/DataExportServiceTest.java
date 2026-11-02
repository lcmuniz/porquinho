package com.porquinho.service;

import com.porquinho.entity.Account;
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

        Map<String, Object> second = rows.get(1);
        assertExportsEveryColumn(Account.class, second);
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

    /** LGPD portability: every mapped column of the entity appears as a key in the exported map. */
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
