package com.porquinho.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.porquinho.dto.DataExportResponse;
import com.porquinho.entity.Account;
import com.porquinho.entity.Category;
import com.porquinho.entity.CreditCard;
import com.porquinho.entity.Goal;
import com.porquinho.entity.AuditLog;
import com.porquinho.entity.BillingTransaction;
import com.porquinho.entity.DataExport;
import com.porquinho.entity.DataExport.ExportStatus;
import com.porquinho.entity.User;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Service for managing user data exports (LGPD compliance).
 * Handles export request, async processing, file generation, download, and cleanup.
 */
@Service
public class DataExportService {

    private static final Logger log = LoggerFactory.getLogger(DataExportService.class);

    private final DataExportRepository dataExportRepository;
    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final AuditLogRepository auditLogRepository;
    private final RateLimitService rateLimitService;
    private final EmailService emailService;
    private final AuditLogService auditLogService;
    private final SubscriptionService subscriptionService;
    private final BillingTransactionRepository billingTransactionRepository;
    private final OnboardingProgressRepository onboardingProgressRepository;
    private final AccountRepository accountRepository;
    private final CreditCardRepository creditCardRepository;
    private final CategoryRepository categoryRepository;
    private final GoalRepository goalRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.export.directory:./exports}")
    private String exportDirectory;

    @Value("${app.export.expiration-hours:24}")
    private int expirationHours;

    @Value("${app.export.base-url:http://localhost:8080}")
    private String baseUrl;

    public DataExportService(
            DataExportRepository dataExportRepository,
            UserRepository userRepository,
            UserSessionRepository userSessionRepository,
            AuditLogRepository auditLogRepository,
            RateLimitService rateLimitService,
            EmailService emailService,
            AuditLogService auditLogService,
            SubscriptionService subscriptionService,
            BillingTransactionRepository billingTransactionRepository,
            OnboardingProgressRepository onboardingProgressRepository,
            AccountRepository accountRepository,
            CreditCardRepository creditCardRepository,
            CategoryRepository categoryRepository,
            GoalRepository goalRepository) {
        this.dataExportRepository = dataExportRepository;
        this.userRepository = userRepository;
        this.userSessionRepository = userSessionRepository;
        this.auditLogRepository = auditLogRepository;
        this.rateLimitService = rateLimitService;
        this.emailService = emailService;
        this.auditLogService = auditLogService;
        this.subscriptionService = subscriptionService;
        this.billingTransactionRepository = billingTransactionRepository;
        this.onboardingProgressRepository = onboardingProgressRepository;
        this.accountRepository = accountRepository;
        this.creditCardRepository = creditCardRepository;
        this.categoryRepository = categoryRepository;
        this.goalRepository = goalRepository;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Request a new data export for the user.
     * Rate limited to 1 export per hour per user.
     */
    @Transactional
    public DataExportResponse requestExport(UUID userId, String ipAddress) {
        if (!rateLimitService.allowRequest("data_export:" + userId, 1, 3600)) {
            throw new RateLimitExceededException("Data export rate limit exceeded. Maximum 1 export per hour.");
        }

        DataExport export = new DataExport(userId);
        export.setDownloadToken(UUID.randomUUID());
        export.setExpiresAt(LocalDateTime.now().plusHours(expirationHours));
        export = dataExportRepository.save(export);

        processExport(export.getId());

        return DataExportResponse.fromEntity(export);
    }

    /**
     * Process the data export asynchronously.
     * Collects user data, generates JSON file, updates status.
     */
    @Async
    public void processExport(UUID exportId) {
        DataExport export = dataExportRepository.findById(exportId)
                .orElseThrow(() -> new ResourceNotFoundException("Export not found: " + exportId));

        try {
            Map<String, Object> userData = collectUserData(export.getUserId());
            String json = objectMapper.writeValueAsString(userData);

            Path exportDir = Paths.get(exportDirectory);
            Files.createDirectories(exportDir);

            String filename = "export_" + export.getUserId() + "_" +
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".json";
            Path filePath = exportDir.resolve(filename);
            Files.writeString(filePath, json);

            export.setFilePath(filePath.toString());
            export.setStatus(ExportStatus.READY);
            dataExportRepository.save(export);

            // Send email notification (best-effort)
            User user = userRepository.findById(export.getUserId()).orElse(null);
            if (user != null) {
                String downloadUrl = baseUrl + "/api/v1/data-export/download/" + export.getDownloadToken();
                emailService.sendDataExportReady(user.getEmail(), downloadUrl);
            }

            log.info("Data export completed for user {}: {}", export.getUserId(), filePath);
        } catch (IOException e) {
            log.error("Failed to process data export {}: {}", exportId, e.getMessage());
            export.setStatus(ExportStatus.FAILED);
            dataExportRepository.save(export);
        }
    }

    /**
     * Collect all user data for export, sanitizing sensitive fields.
     */
    public Map<String, Object> collectUserData(UUID userId) {
        Map<String, Object> data = new LinkedHashMap<>();

        // Metadata
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("export_date", LocalDateTime.now().toString());
        metadata.put("user_id", userId.toString());
        metadata.put("data_version", "1.0");
        metadata.put("exported_tables", List.of("users", "user_sessions", "audit_logs", "subscriptions",
            "billing_transactions", "onboarding_progress", "accounts", "credit_cards", "categories", "goals"));
        data.put("metadata", metadata);

        // User profile (sanitized)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        Map<String, Object> userProfile = new LinkedHashMap<>();
        userProfile.put("id", user.getId().toString());
        userProfile.put("email", user.getEmail());
        userProfile.put("auth_provider", user.getAuthProvider().name());
        userProfile.put("google_id", user.getGoogleId());
        userProfile.put("created_at", user.getCreatedAt() != null ? user.getCreatedAt().toString() : null);
        userProfile.put("updated_at", user.getUpdatedAt() != null ? user.getUpdatedAt().toString() : null);
        userProfile.put("lgpd_consent_at", user.getLgpdConsentAt() != null ? user.getLgpdConsentAt().toString() : null);
        // Excluded: deleted_at, deleted_by, failed_login_attempts, locked_until
        data.put("user_profile", userProfile);

        // Sessions (sanitized - exclude jwt_token_hash)
        List<UserSession> sessions = userSessionRepository.findAllByUserId(userId);
        List<Map<String, Object>> sessionList = new ArrayList<>();
        for (UserSession session : sessions) {
            Map<String, Object> sessionMap = new LinkedHashMap<>();
            sessionMap.put("id", session.getId().toString());
            sessionMap.put("device_info", session.getDeviceInfo());
            sessionMap.put("ip_address", session.getIpAddress());
            sessionMap.put("created_at", session.getCreatedAt() != null ? session.getCreatedAt().toString() : null);
            sessionMap.put("last_activity", session.getLastActivity() != null ? session.getLastActivity().toString() : null);
            sessionMap.put("expires_at", session.getExpiresAt() != null ? session.getExpiresAt().toString() : null);
            // Excluded: jwt_token_hash
            sessionList.add(sessionMap);
        }
        data.put("sessions", sessionList);

        // Audit logs
        List<AuditLog> auditLogs = auditLogRepository.findByUserIdOrderByTimestampDesc(userId);
        List<Map<String, Object>> auditLogList = new ArrayList<>();
        for (AuditLog auditLog : auditLogs) {
            Map<String, Object> logMap = new LinkedHashMap<>();
            logMap.put("id", auditLog.getId().toString());
            logMap.put("event_type", auditLog.getEventType());
            logMap.put("ip_address", auditLog.getIpAddress());
            logMap.put("metadata", auditLog.getMetadata());
            logMap.put("created_at", auditLog.getTimestamp() != null ? auditLog.getTimestamp().toString() : null);
            auditLogList.add(logMap);
        }
        data.put("audit_logs", auditLogList);

        // Subscription (Story 2.1) - 0..1 item: every stored column plus computed days_remaining/has_access
        // (DataExportServiceTest checks the keys against the entity's @Column names)
        List<Map<String, Object>> subscriptionList = new ArrayList<>();
        subscriptionService.findByUserId(userId).ifPresent(sub -> {
            Map<String, Object> subMap = new LinkedHashMap<>();
            subMap.put("id", sub.getId() != null ? sub.getId().toString() : null);
            subMap.put("user_id", sub.getUserId() != null ? sub.getUserId().toString() : null);
            subMap.put("plan_type", sub.getPlanType().name());
            subMap.put("status", sub.getStatus().name());
            subMap.put("trial_start_date", sub.getTrialStartDate() != null ? sub.getTrialStartDate().toString() : null);
            subMap.put("trial_end_date", sub.getTrialEndDate() != null ? sub.getTrialEndDate().toString() : null);
            subMap.put("next_billing_date", sub.getNextBillingDate() != null ? sub.getNextBillingDate().toString() : null);
            subMap.put("payment_method_id", sub.getPaymentMethodId());
            subMap.put("stripe_customer_id", sub.getStripeCustomerId());
            subMap.put("stripe_subscription_id", sub.getStripeSubscriptionId());
            subMap.put("trial_reminder_7d_sent_at", sub.getTrialReminder7dSentAt() != null ? sub.getTrialReminder7dSentAt().toString() : null);
            subMap.put("trial_reminder_1d_sent_at", sub.getTrialReminder1dSentAt() != null ? sub.getTrialReminder1dSentAt().toString() : null);
            subMap.put("cancellation_date", sub.getCancellationDate() != null ? sub.getCancellationDate().toString() : null);
            subMap.put("failed_payment_attempts", sub.getFailedPaymentAttempts());
            subMap.put("payment_update_url", sub.getPaymentUpdateUrl());
            subMap.put("created_at", sub.getCreatedAt() != null ? sub.getCreatedAt().toString() : null);
            subMap.put("updated_at", sub.getUpdatedAt() != null ? sub.getUpdatedAt().toString() : null);
            subMap.put("deleted_at", sub.getDeletedAt() != null ? sub.getDeletedAt().toString() : null);
            subMap.put("days_remaining", subscriptionService.daysRemaining(sub));
            subMap.put("has_access", subscriptionService.hasAccess(sub));
            subscriptionList.add(subMap);
        });
        data.put("subscriptions", subscriptionList);

        // Payments (Story 2.6) - newest first; amount as a JSON number
        List<Map<String, Object>> paymentList = new ArrayList<>();
        for (BillingTransaction tx : billingTransactionRepository.findByUserIdOrderByPaidAtDesc(userId)) {
            Map<String, Object> txMap = new LinkedHashMap<>();
            txMap.put("id", tx.getId() != null ? tx.getId().toString() : null);
            txMap.put("subscription_id", tx.getSubscriptionId() != null ? tx.getSubscriptionId().toString() : null);
            txMap.put("user_id", tx.getUserId() != null ? tx.getUserId().toString() : null);
            txMap.put("amount", tx.getAmount());
            txMap.put("currency", tx.getCurrency());
            txMap.put("status", tx.getStatus() != null ? tx.getStatus().name() : null);
            txMap.put("payment_method", tx.getPaymentMethod());
            txMap.put("stripe_transaction_id", tx.getStripeTransactionId());
            txMap.put("paid_at", tx.getPaidAt() != null ? tx.getPaidAt().toString() : null);
            txMap.put("created_at", tx.getCreatedAt() != null ? tx.getCreatedAt().toString() : null);
            paymentList.add(txMap);
        }
        data.put("billing_transactions", paymentList);

        // Onboarding progress (Story 3.1) - 0..1 item, every stored column; steps_completed as a JSON array
        List<Map<String, Object>> onboardingList = new ArrayList<>();
        onboardingProgressRepository.findByUserId(userId).ifPresent(progress -> {
            Map<String, Object> progressMap = new LinkedHashMap<>();
            progressMap.put("id", progress.getId() != null ? progress.getId().toString() : null);
            progressMap.put("user_id", progress.getUserId() != null ? progress.getUserId().toString() : null);
            progressMap.put("current_step", progress.getCurrentStep());
            progressMap.put("steps_completed", progress.getSteps());
            progressMap.put("is_completed", progress.isCompleted());
            progressMap.put("created_at", progress.getCreatedAt() != null ? progress.getCreatedAt().toString() : null);
            progressMap.put("updated_at", progress.getUpdatedAt() != null ? progress.getUpdatedAt().toString() : null);
            onboardingList.add(progressMap);
        });
        data.put("onboarding_progress", onboardingList);

        // Bank accounts (Story 3.2) - every stored column, removed accounts included (deleted_at filled)
        List<Map<String, Object>> accountList = new ArrayList<>();
        for (Account account : accountRepository.findByUserIdOrderByCreatedAtAsc(userId)) {
            Map<String, Object> accountMap = new LinkedHashMap<>();
            accountMap.put("id", account.getId() != null ? account.getId().toString() : null);
            accountMap.put("user_id", account.getUserId() != null ? account.getUserId().toString() : null);
            accountMap.put("account_name", account.getAccountName());
            accountMap.put("bank_name", account.getBankName());
            accountMap.put("account_type", account.getAccountType() != null ? account.getAccountType().name() : null);
            accountMap.put("initial_balance", account.getInitialBalance());
            accountMap.put("current_balance", account.getCurrentBalance());
            accountMap.put("created_at", account.getCreatedAt() != null ? account.getCreatedAt().toString() : null);
            accountMap.put("updated_at", account.getUpdatedAt() != null ? account.getUpdatedAt().toString() : null);
            accountMap.put("deleted_at", account.getDeletedAt() != null ? account.getDeletedAt().toString() : null);
            accountList.add(accountMap);
        }
        data.put("accounts", accountList);

        // Credit cards (Story 3.3) - every stored column, removed cards included (deleted_at filled)
        List<Map<String, Object>> cardList = new ArrayList<>();
        for (CreditCard card : creditCardRepository.findByUserIdOrderByCreatedAtAsc(userId)) {
            Map<String, Object> cardMap = new LinkedHashMap<>();
            cardMap.put("id", card.getId() != null ? card.getId().toString() : null);
            cardMap.put("user_id", card.getUserId() != null ? card.getUserId().toString() : null);
            cardMap.put("card_name", card.getCardName());
            cardMap.put("bank_name", card.getBankName());
            cardMap.put("card_limit", card.getCardLimit());
            cardMap.put("due_day", card.getDueDay());
            cardMap.put("created_at", card.getCreatedAt() != null ? card.getCreatedAt().toString() : null);
            cardMap.put("updated_at", card.getUpdatedAt() != null ? card.getUpdatedAt().toString() : null);
            cardMap.put("deleted_at", card.getDeletedAt() != null ? card.getDeletedAt().toString() : null);
            cardList.add(cardMap);
        }
        data.put("credit_cards", cardList);

        // Categories (Story 3.4) - every stored column, removed categories included (deleted_at filled)
        List<Map<String, Object>> categoryList = new ArrayList<>();
        for (Category category : categoryRepository.findByUserIdOrderByCreatedAtAsc(userId)) {
            Map<String, Object> categoryMap = new LinkedHashMap<>();
            categoryMap.put("id", category.getId() != null ? category.getId().toString() : null);
            categoryMap.put("user_id", category.getUserId() != null ? category.getUserId().toString() : null);
            categoryMap.put("category_name", category.getCategoryName());
            categoryMap.put("parent_category_id",
                category.getParentCategoryId() != null ? category.getParentCategoryId().toString() : null);
            categoryMap.put("color_hex", category.getColorHex());
            categoryMap.put("icon_name", category.getIconName());
            categoryMap.put("is_system_default", category.isSystemDefault());
            categoryMap.put("created_at", category.getCreatedAt() != null ? category.getCreatedAt().toString() : null);
            categoryMap.put("updated_at", category.getUpdatedAt() != null ? category.getUpdatedAt().toString() : null);
            categoryMap.put("deleted_at", category.getDeletedAt() != null ? category.getDeletedAt().toString() : null);
            categoryList.add(categoryMap);
        }
        data.put("categories", categoryList);

        // Goals (Story 3.8) - every stored column, removed goals included (deleted_at filled)
        List<Map<String, Object>> goalList = new ArrayList<>();
        for (Goal goal : goalRepository.findByUserIdOrderByCreatedAtAsc(userId)) {
            Map<String, Object> goalMap = new LinkedHashMap<>();
            goalMap.put("id", goal.getId() != null ? goal.getId().toString() : null);
            goalMap.put("user_id", goal.getUserId() != null ? goal.getUserId().toString() : null);
            goalMap.put("goal_name", goal.getGoalName());
            goalMap.put("description", goal.getDescription());
            goalMap.put("target_amount", goal.getTargetAmount());
            goalMap.put("current_amount", goal.getCurrentAmount());
            goalMap.put("target_date", goal.getTargetDate() != null ? goal.getTargetDate().toString() : null);
            goalMap.put("monthly_allocation", goal.getMonthlyAllocation());
            goalMap.put("is_completed", goal.isCompleted());
            goalMap.put("created_at", goal.getCreatedAt() != null ? goal.getCreatedAt().toString() : null);
            goalMap.put("updated_at", goal.getUpdatedAt() != null ? goal.getUpdatedAt().toString() : null);
            goalMap.put("deleted_at", goal.getDeletedAt() != null ? goal.getDeletedAt().toString() : null);
            goalList.add(goalMap);
        }
        data.put("goals", goalList);

        return data;
    }

    /**
     * Get the most recent export status for the user.
     */
    public DataExportResponse getExportStatus(UUID userId) {
        DataExport export = dataExportRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .orElse(null);
        if (export == null) {
            return null;
        }
        return DataExportResponse.fromEntity(export);
    }

    /**
     * Download an export by its download token.
     * Validates token, checks expiration, returns file content, marks as downloaded, deletes file.
     */
    @Transactional
    public Resource downloadExport(String downloadToken, String ipAddress) {
        UUID token = UUID.fromString(downloadToken);
        DataExport export = dataExportRepository.findByDownloadToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Export not found for token"));

        if (export.getStatus() == ExportStatus.EXPIRED) {
            throw new ResourceNotFoundException("Export has expired");
        }

        if (export.getStatus() == ExportStatus.DOWNLOADED) {
            throw new ResourceNotFoundException("Export has already been downloaded");
        }

        if (export.getStatus() != ExportStatus.READY) {
            throw new ResourceNotFoundException("Export is not ready for download");
        }

        if (export.getExpiresAt() != null && export.getExpiresAt().isBefore(LocalDateTime.now())) {
            export.setStatus(ExportStatus.EXPIRED);
            dataExportRepository.save(export);
            throw new ResourceNotFoundException("Export has expired");
        }

        Path filePath = Paths.get(export.getFilePath());
        if (!Files.exists(filePath)) {
            throw new ResourceNotFoundException("Export file not found");
        }

        // Read file into memory and delete from filesystem (AC4: single-use download)
        byte[] content;
        try {
            content = Files.readAllBytes(filePath);
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.error("Failed to read/delete export file {}: {}", filePath, e.getMessage());
            throw new ResourceNotFoundException("Export file could not be read");
        }

        export.setStatus(ExportStatus.DOWNLOADED);
        export.setDownloadedAt(LocalDateTime.now());
        dataExportRepository.save(export);

        auditLogService.log("data_export_downloaded", export.getUserId(), ipAddress);

        return new ByteArrayResource(content);
    }

    /**
     * Cleanup expired exports: delete files and update status.
     * Runs every hour.
     */
    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void cleanupExpiredExports() {
        LocalDateTime now = LocalDateTime.now();

        // Clean READY exports that have expired
        List<DataExport> expiredReady = dataExportRepository
                .findByStatusAndExpiresAtBefore(ExportStatus.READY, now);

        // Clean stuck PROCESSING exports that have expired
        List<DataExport> stuckProcessing = dataExportRepository
                .findByStatusAndExpiresAtBefore(ExportStatus.PROCESSING, now);

        List<DataExport> toCleanup = new ArrayList<>();
        toCleanup.addAll(expiredReady);
        toCleanup.addAll(stuckProcessing);

        for (DataExport export : toCleanup) {
            if (export.getFilePath() != null) {
                try {
                    Files.deleteIfExists(Paths.get(export.getFilePath()));
                } catch (IOException e) {
                    log.warn("Failed to delete export file {}: {}", export.getFilePath(), e.getMessage());
                }
            }
            export.setStatus(ExportStatus.EXPIRED);
            dataExportRepository.save(export);
        }

        if (!toCleanup.isEmpty()) {
            log.info("Cleaned up {} expired/stuck data exports", toCleanup.size());
        }
    }
}
