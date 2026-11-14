package com.porquinho.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.porquinho.dto.DataExportResponse;
import com.porquinho.entity.Account;
import com.porquinho.entity.AccountBalanceHistory;
import com.porquinho.entity.AiClassificationHistory;
import com.porquinho.entity.BudgetAllocation;
import com.porquinho.entity.BudgetReallocation;
import com.porquinho.entity.UserClassificationPattern;
import com.porquinho.entity.Category;
import com.porquinho.entity.CreditCard;
import com.porquinho.entity.Goal;
import com.porquinho.entity.GoalAllocation;
import com.porquinho.entity.Reconciliation;
import com.porquinho.entity.Transaction;
import com.porquinho.entity.TransactionImport;
import com.porquinho.entity.Transfer;
import com.porquinho.entity.AuditLog;
import com.porquinho.entity.BillingTransaction;
import com.porquinho.entity.DataExport;
import com.porquinho.entity.DataExport.ExportStatus;
import com.porquinho.entity.User;
import com.porquinho.entity.UserSession;
import com.porquinho.exception.RateLimitExceededException;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.repository.AccountBalanceHistoryRepository;
import com.porquinho.repository.AccountRepository;
import com.porquinho.repository.AiClassificationHistoryRepository;
import com.porquinho.repository.BudgetAllocationRepository;
import com.porquinho.repository.GoalAllocationRepository;
import com.porquinho.repository.BudgetReallocationRepository;
import com.porquinho.repository.UserClassificationPatternRepository;
import com.porquinho.repository.CategoryRepository;
import com.porquinho.repository.CreditCardRepository;
import com.porquinho.repository.GoalRepository;
import com.porquinho.repository.AuditLogRepository;
import com.porquinho.repository.BillingTransactionRepository;
import com.porquinho.repository.DataExportRepository;
import com.porquinho.repository.OnboardingProgressRepository;
import com.porquinho.repository.ReconciliationRepository;
import com.porquinho.repository.TransactionImportRepository;
import com.porquinho.repository.TransactionRepository;
import com.porquinho.repository.TransferRepository;
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
    private final AccountBalanceHistoryRepository accountBalanceHistoryRepository;
    private final TransferRepository transferRepository;
    private final TransactionImportRepository transactionImportRepository;
    private final ReconciliationRepository reconciliationRepository;
    private final TransactionRepository transactionRepository;
    private final AiClassificationHistoryRepository aiClassificationHistoryRepository;
    private final UserClassificationPatternRepository userClassificationPatternRepository;
    private final BudgetAllocationRepository budgetAllocationRepository;
    private final BudgetReallocationRepository budgetReallocationRepository;
    private final GoalAllocationRepository goalAllocationRepository;
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
            GoalRepository goalRepository,
            AccountBalanceHistoryRepository accountBalanceHistoryRepository,
            TransferRepository transferRepository,
            TransactionImportRepository transactionImportRepository,
            ReconciliationRepository reconciliationRepository,
            TransactionRepository transactionRepository,
            AiClassificationHistoryRepository aiClassificationHistoryRepository,
            UserClassificationPatternRepository userClassificationPatternRepository,
            BudgetAllocationRepository budgetAllocationRepository,
            BudgetReallocationRepository budgetReallocationRepository,
            GoalAllocationRepository goalAllocationRepository) {
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
        this.accountBalanceHistoryRepository = accountBalanceHistoryRepository;
        this.transferRepository = transferRepository;
        this.transactionImportRepository = transactionImportRepository;
        this.reconciliationRepository = reconciliationRepository;
        this.transactionRepository = transactionRepository;
        this.aiClassificationHistoryRepository = aiClassificationHistoryRepository;
        this.userClassificationPatternRepository = userClassificationPatternRepository;
        this.budgetAllocationRepository = budgetAllocationRepository;
        this.budgetReallocationRepository = budgetReallocationRepository;
        this.goalAllocationRepository = goalAllocationRepository;
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
            "billing_transactions", "onboarding_progress", "accounts", "account_balance_history", "transfers", "reconciliation_history", "credit_cards", "transaction_imports", "transactions", "ai_classification_history", "user_classification_patterns", "budget_allocations", "budget_reallocations", "categories", "goals", "goal_allocations"));
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
            accountMap.put("deleted_by", account.getDeletedBy() != null ? account.getDeletedBy().toString() : null);
            accountMap.put("last_reconciliation_date",
                account.getLastReconciliationDate() != null ? account.getLastReconciliationDate().toString() : null);
            accountMap.put("version", account.getVersion());
            accountList.add(accountMap);
        }
        data.put("accounts", accountList);

        // Account balance history (Story 4.3) - every stored column, oldest first; balance as a JSON number
        List<Map<String, Object>> historyList = new ArrayList<>();
        for (AccountBalanceHistory snapshot : accountBalanceHistoryRepository.findByUserIdOrdered(userId)) {
            Map<String, Object> snapshotMap = new LinkedHashMap<>();
            snapshotMap.put("id", snapshot.getId() != null ? snapshot.getId().toString() : null);
            snapshotMap.put("account_id", snapshot.getAccountId() != null ? snapshot.getAccountId().toString() : null);
            snapshotMap.put("user_id", snapshot.getUserId() != null ? snapshot.getUserId().toString() : null);
            snapshotMap.put("balance_snapshot", snapshot.getBalanceSnapshot());
            snapshotMap.put("snapshot_date", snapshot.getSnapshotDate() != null ? snapshot.getSnapshotDate().toString() : null);
            snapshotMap.put("created_by_event", snapshot.getCreatedByEvent());
            snapshotMap.put("created_at", snapshot.getCreatedAt() != null ? snapshot.getCreatedAt().toString() : null);
            snapshotMap.put("transfer_id", snapshot.getTransferId() != null ? snapshot.getTransferId().toString() : null);
            historyList.add(snapshotMap);
        }
        data.put("account_balance_history", historyList);

        // Transfers (Story 4.5) - every stored column, oldest first, removed transfers included; amount as a JSON number
        List<Map<String, Object>> transferList = new ArrayList<>();
        for (Transfer transfer : transferRepository.findByUserIdOrdered(userId)) {
            Map<String, Object> transferMap = new LinkedHashMap<>();
            transferMap.put("id", transfer.getId() != null ? transfer.getId().toString() : null);
            transferMap.put("user_id", transfer.getUserId() != null ? transfer.getUserId().toString() : null);
            transferMap.put("from_account_id",
                transfer.getFromAccountId() != null ? transfer.getFromAccountId().toString() : null);
            transferMap.put("to_account_id", transfer.getToAccountId() != null ? transfer.getToAccountId().toString() : null);
            transferMap.put("amount", transfer.getAmount());
            transferMap.put("transfer_date", transfer.getTransferDate() != null ? transfer.getTransferDate().toString() : null);
            transferMap.put("description", transfer.getDescription());
            transferMap.put("created_at", transfer.getCreatedAt() != null ? transfer.getCreatedAt().toString() : null);
            transferMap.put("deleted_at", transfer.getDeletedAt() != null ? transfer.getDeletedAt().toString() : null);
            transferList.add(transferMap);
        }
        data.put("transfers", transferList);

        // Transaction imports (Story 5.1) - every stored column, oldest first; the file itself is never stored
        List<Map<String, Object>> importList = new ArrayList<>();
        for (TransactionImport transactionImport : transactionImportRepository.findByUserIdOrdered(userId)) {
            Map<String, Object> importMap = new LinkedHashMap<>();
            importMap.put("id", transactionImport.getId() != null ? transactionImport.getId().toString() : null);
            importMap.put("user_id", transactionImport.getUserId() != null ? transactionImport.getUserId().toString() : null);
            importMap.put("account_id",
                transactionImport.getAccountId() != null ? transactionImport.getAccountId().toString() : null);
            importMap.put("credit_card_id",
                transactionImport.getCreditCardId() != null ? transactionImport.getCreditCardId().toString() : null);
            importMap.put("file_name", transactionImport.getFileName());
            importMap.put("file_type", transactionImport.getFileType() != null ? transactionImport.getFileType().name() : null);
            importMap.put("bank_name", transactionImport.getBankName());
            importMap.put("file_size_kb", transactionImport.getFileSizeKb());
            importMap.put("status", transactionImport.getStatus() != null ? transactionImport.getStatus().name() : null);
            importMap.put("total_transactions", transactionImport.getTotalTransactions());
            importMap.put("imported_transactions", transactionImport.getImportedTransactions());
            importMap.put("duplicates_found", transactionImport.getDuplicatesFound());
            importMap.put("transfers_found", transactionImport.getTransfersFound());
            importMap.put("skipped_rows", transactionImport.getSkippedRows());
            importMap.put("classification_total", transactionImport.getClassificationTotal());
            importMap.put("classified_count", transactionImport.getClassifiedCount());
            importMap.put("error_message", transactionImport.getErrorMessage());
            importMap.put("created_at",
                transactionImport.getCreatedAt() != null ? transactionImport.getCreatedAt().toString() : null);
            importMap.put("updated_at",
                transactionImport.getUpdatedAt() != null ? transactionImport.getUpdatedAt().toString() : null);
            importList.add(importMap);
        }
        data.put("transaction_imports", importList);

        // Transactions (Story 5.4) - every stored column, oldest first, removed ones included; amount as a JSON number
        List<Map<String, Object>> transactionList = new ArrayList<>();
        for (Transaction transaction : transactionRepository.findByUserIdOrdered(userId)) {
            Map<String, Object> transactionMap = new LinkedHashMap<>();
            transactionMap.put("id", transaction.getId() != null ? transaction.getId().toString() : null);
            transactionMap.put("user_id", transaction.getUserId() != null ? transaction.getUserId().toString() : null);
            transactionMap.put("account_id", transaction.getAccountId() != null ? transaction.getAccountId().toString() : null);
            transactionMap.put("credit_card_id",
                transaction.getCreditCardId() != null ? transaction.getCreditCardId().toString() : null);
            transactionMap.put("import_id", transaction.getImportId() != null ? transaction.getImportId().toString() : null);
            transactionMap.put("external_id", transaction.getExternalId());
            transactionMap.put("transaction_date",
                transaction.getTransactionDate() != null ? transaction.getTransactionDate().toString() : null);
            transactionMap.put("description", transaction.getDescription());
            transactionMap.put("original_description", transaction.getOriginalDescription());
            transactionMap.put("amount", transaction.getAmount());
            transactionMap.put("transaction_type",
                transaction.getTransactionType() != null ? transaction.getTransactionType().name() : null);
            transactionMap.put("kind", transaction.getKind() != null ? transaction.getKind().name() : null);
            transactionMap.put("category_id", transaction.getCategoryId() != null ? transaction.getCategoryId().toString() : null);
            transactionMap.put("classification_source",
                transaction.getClassificationSource() != null ? transaction.getClassificationSource().name() : null);
            transactionMap.put("classification_confidence", transaction.getClassificationConfidence());
            transactionMap.put("review_status", transaction.getReviewStatus() != null ? transaction.getReviewStatus().name() : null);
            transactionMap.put("affects_balance", transaction.isAffectsBalance());
            transactionMap.put("is_pix", transaction.isPix());
            transactionMap.put("pix_details", pixDetailsJson(transaction.getPixDetails()));
            transactionMap.put("is_installment", transaction.isInstallment());
            transactionMap.put("installment_number", transaction.getInstallmentNumber());
            transactionMap.put("installment_total", transaction.getInstallmentTotal());
            transactionMap.put("installment_group_id",
                transaction.getInstallmentGroupId() != null ? transaction.getInstallmentGroupId().toString() : null);
            transactionMap.put("is_future_installment", transaction.isFutureInstallment());
            transactionMap.put("confirmed_by_import_id",
                transaction.getConfirmedByImportId() != null ? transaction.getConfirmedByImportId().toString() : null);
            transactionMap.put("installment_futures_removed_at", transaction.getInstallmentFuturesRemovedAt() != null
                ? transaction.getInstallmentFuturesRemovedAt().toString() : null);
            transactionMap.put("created_at", transaction.getCreatedAt() != null ? transaction.getCreatedAt().toString() : null);
            transactionMap.put("updated_at", transaction.getUpdatedAt() != null ? transaction.getUpdatedAt().toString() : null);
            transactionMap.put("deleted_at", transaction.getDeletedAt() != null ? transaction.getDeletedAt().toString() : null);
            transactionList.add(transactionMap);
        }
        data.put("transactions", transactionList);

        // AI classification history (Story 5.8) - every stored column, oldest first
        List<Map<String, Object>> classificationHistoryList = new ArrayList<>();
        for (AiClassificationHistory history : aiClassificationHistoryRepository.findByUserIdOrdered(userId)) {
            Map<String, Object> historyMap = new LinkedHashMap<>();
            historyMap.put("id", history.getId() != null ? history.getId().toString() : null);
            historyMap.put("user_id", history.getUserId() != null ? history.getUserId().toString() : null);
            historyMap.put("transaction_id", history.getTransactionId() != null ? history.getTransactionId().toString() : null);
            historyMap.put("predicted_category_id",
                history.getPredictedCategoryId() != null ? history.getPredictedCategoryId().toString() : null);
            historyMap.put("confidence", history.getConfidence());
            historyMap.put("was_corrected", history.isWasCorrected());
            historyMap.put("corrected_category_id",
                history.getCorrectedCategoryId() != null ? history.getCorrectedCategoryId().toString() : null);
            historyMap.put("created_at", history.getCreatedAt() != null ? history.getCreatedAt().toString() : null);
            classificationHistoryList.add(historyMap);
        }
        data.put("ai_classification_history", classificationHistoryList);

        // Classification patterns (Story 5.11) - every stored column, oldest first
        List<Map<String, Object>> patternList = new ArrayList<>();
        for (UserClassificationPattern pattern : userClassificationPatternRepository.findByUserIdOrdered(userId)) {
            Map<String, Object> patternMap = new LinkedHashMap<>();
            patternMap.put("id", pattern.getId() != null ? pattern.getId().toString() : null);
            patternMap.put("user_id", pattern.getUserId() != null ? pattern.getUserId().toString() : null);
            patternMap.put("description_pattern", pattern.getDescriptionPattern());
            patternMap.put("preferred_category_id",
                pattern.getPreferredCategoryId() != null ? pattern.getPreferredCategoryId().toString() : null);
            patternMap.put("usage_count", pattern.getUsageCount());
            patternMap.put("last_used_at", pattern.getLastUsedAt() != null ? pattern.getLastUsedAt().toString() : null);
            patternMap.put("created_at", pattern.getCreatedAt() != null ? pattern.getCreatedAt().toString() : null);
            patternList.add(patternMap);
        }
        data.put("user_classification_patterns", patternList);

        // Budget allocations (Story 6.3) - every stored column, oldest first; money as JSON numbers
        List<Map<String, Object>> allocationList = new ArrayList<>();
        for (BudgetAllocation allocation : budgetAllocationRepository.findByUserIdOrdered(userId)) {
            Map<String, Object> allocationMap = new LinkedHashMap<>();
            allocationMap.put("id", allocation.getId() != null ? allocation.getId().toString() : null);
            allocationMap.put("user_id", allocation.getUserId() != null ? allocation.getUserId().toString() : null);
            allocationMap.put("category_id",
                allocation.getCategoryId() != null ? allocation.getCategoryId().toString() : null);
            allocationMap.put("month_year", allocation.getMonthYear() != null ? allocation.getMonthYear().toString() : null);
            allocationMap.put("allocated_amount", allocation.getAllocatedAmount());
            allocationMap.put("created_at", allocation.getCreatedAt() != null ? allocation.getCreatedAt().toString() : null);
            allocationMap.put("updated_at", allocation.getUpdatedAt() != null ? allocation.getUpdatedAt().toString() : null);
            allocationList.add(allocationMap);
        }
        data.put("budget_allocations", allocationList);

        // Budget reallocations (Story 6.5) - every stored column, oldest first; money as JSON numbers
        List<Map<String, Object>> reallocationList = new ArrayList<>();
        for (BudgetReallocation reallocation : budgetReallocationRepository.findByUserIdOrdered(userId)) {
            Map<String, Object> reallocationMap = new LinkedHashMap<>();
            reallocationMap.put("id", reallocation.getId() != null ? reallocation.getId().toString() : null);
            reallocationMap.put("user_id", reallocation.getUserId() != null ? reallocation.getUserId().toString() : null);
            reallocationMap.put("from_category_id",
                reallocation.getFromCategoryId() != null ? reallocation.getFromCategoryId().toString() : null);
            reallocationMap.put("to_category_id",
                reallocation.getToCategoryId() != null ? reallocation.getToCategoryId().toString() : null);
            reallocationMap.put("month_year",
                reallocation.getMonthYear() != null ? reallocation.getMonthYear().toString() : null);
            reallocationMap.put("amount", reallocation.getAmount());
            reallocationMap.put("created_at",
                reallocation.getCreatedAt() != null ? reallocation.getCreatedAt().toString() : null);
            reallocationList.add(reallocationMap);
        }
        data.put("budget_reallocations", reallocationList);

        // Reconciliations (Story 4.6) - every stored column, oldest first; money as JSON numbers
        List<Map<String, Object>> reconciliationList = new ArrayList<>();
        for (Reconciliation reconciliation : reconciliationRepository.findByUserIdOrdered(userId)) {
            Map<String, Object> reconciliationMap = new LinkedHashMap<>();
            reconciliationMap.put("id", reconciliation.getId() != null ? reconciliation.getId().toString() : null);
            reconciliationMap.put("account_id",
                reconciliation.getAccountId() != null ? reconciliation.getAccountId().toString() : null);
            reconciliationMap.put("user_id", reconciliation.getUserId() != null ? reconciliation.getUserId().toString() : null);
            reconciliationMap.put("previous_balance", reconciliation.getPreviousBalance());
            reconciliationMap.put("bank_balance", reconciliation.getBankBalance());
            reconciliationMap.put("difference", reconciliation.getDifference());
            reconciliationMap.put("reason", reconciliation.getReason());
            reconciliationMap.put("reconciled_at",
                reconciliation.getReconciledAt() != null ? reconciliation.getReconciledAt().toString() : null);
            reconciliationMap.put("created_at",
                reconciliation.getCreatedAt() != null ? reconciliation.getCreatedAt().toString() : null);
            reconciliationList.add(reconciliationMap);
        }
        data.put("reconciliation_history", reconciliationList);

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
            cardMap.put("deleted_by", card.getDeletedBy() != null ? card.getDeletedBy().toString() : null);
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
            goalMap.put("template", goal.getTemplate());
            goalMap.put("target_amount", goal.getTargetAmount());
            goalMap.put("current_amount", goal.getCurrentAmount());
            goalMap.put("target_date", goal.getTargetDate() != null ? goal.getTargetDate().toString() : null);
            goalMap.put("monthly_allocation", goal.getMonthlyAllocation());
            goalMap.put("is_completed", goal.isCompleted());
            goalMap.put("completed_at", goal.getCompletedAt() != null ? goal.getCompletedAt().toString() : null);
            goalMap.put("created_at", goal.getCreatedAt() != null ? goal.getCreatedAt().toString() : null);
            goalMap.put("updated_at", goal.getUpdatedAt() != null ? goal.getUpdatedAt().toString() : null);
            goalMap.put("deleted_at", goal.getDeletedAt() != null ? goal.getDeletedAt().toString() : null);
            goalList.add(goalMap);
        }
        data.put("goals", goalList);

        // Goal allocations (Story 8.2) - every stored column, oldest first; money as JSON numbers
        List<Map<String, Object>> goalAllocationList = new ArrayList<>();
        for (GoalAllocation allocation : goalAllocationRepository.findByUserIdOrdered(userId)) {
            Map<String, Object> allocationMap = new LinkedHashMap<>();
            allocationMap.put("id", allocation.getId() != null ? allocation.getId().toString() : null);
            allocationMap.put("user_id", allocation.getUserId() != null ? allocation.getUserId().toString() : null);
            allocationMap.put("goal_id", allocation.getGoalId() != null ? allocation.getGoalId().toString() : null);
            allocationMap.put("month_year", allocation.getMonthYear() != null ? allocation.getMonthYear().toString() : null);
            allocationMap.put("allocated_amount", allocation.getAllocatedAmount());
            allocationMap.put("contributed_amount", allocation.getContributedAmount());
            allocationMap.put("created_at", allocation.getCreatedAt() != null ? allocation.getCreatedAt().toString() : null);
            allocationMap.put("updated_at", allocation.getUpdatedAt() != null ? allocation.getUpdatedAt().toString() : null);
            goalAllocationList.add(allocationMap);
        }
        data.put("goal_allocations", goalAllocationList);

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

    /** Story 5.6: {@code pix_details} goes out as a JSON object, not as an escaped string. */
    private JsonNode pixDetailsJson(String pixDetails) {
        if (pixDetails == null) {
            return null;
        }
        try {
            return objectMapper.readTree(pixDetails);
        } catch (JsonProcessingException e) {
            // never expected (the column is jsonb): keep the stored text rather than failing the export
            return objectMapper.getNodeFactory().textNode(pixDetails);
        }
    }
}
