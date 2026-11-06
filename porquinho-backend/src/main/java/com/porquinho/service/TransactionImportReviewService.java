package com.porquinho.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.porquinho.dto.ImportReviewActionResponse;
import com.porquinho.dto.ImportTransactionResponse;
import com.porquinho.entity.Category;
import com.porquinho.entity.Transaction;
import com.porquinho.entity.TransactionImport;
import com.porquinho.exception.ConflictException;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.repository.CategoryRepository;
import com.porquinho.repository.TransactionImportRepository;
import com.porquinho.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Review of an import (Story 5.9): lists its rows, approves the high-confidence ones and discards it. None of this
 * touches balances: that is the conclusion of the import (Story 5.10). An import of another user is 404 with nothing
 * written; approving or discarding outside REVIEW is 409. Logs and audit carry only ids and counts.
 */
@Service
public class TransactionImportReviewService {

    private static final Logger log = LoggerFactory.getLogger(TransactionImportReviewService.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    public static final String AUDIT_HIGH_CONFIDENCE_APPROVED = "import_high_confidence_approved";
    public static final String AUDIT_DISCARDED = "import_discarded";

    public static final String NOT_IN_REVIEW_MESSAGE = "Esta importação não está em revisão";

    /** From here on the review shows a row as high confidence. */
    static final int HIGH_CONFIDENCE = 80;

    private final TransactionImportRepository transactionImportRepository;
    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final AuditLogService auditLogService;

    public TransactionImportReviewService(TransactionImportRepository transactionImportRepository,
                                          TransactionRepository transactionRepository,
                                          CategoryRepository categoryRepository, AuditLogService auditLogService) {
        this.transactionImportRepository = transactionImportRepository;
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.auditLogService = auditLogService;
    }

    /** The rows of the import without the future installments (decision 2A), with their categories resolved. */
    @Transactional(readOnly = true)
    public List<ImportTransactionResponse> list(UUID userId, UUID importId) {
        transactionImportRepository.findByIdAndUserId(importId, userId).orElseThrow(TransactionImportReviewService::notFound);
        List<Transaction> rows = transactionRepository.findForReviewByImport(userId, importId);
        Map<UUID, Category> categories = categoriesOf(userId, rows);
        return rows.stream().map(row -> toResponse(row, categories)).toList();
    }

    /**
     * Marks the high-confidence rows as APPROVED (decision 1A). The import row stays locked meanwhile, so a discard at
     * the same time either comes after this or makes it 409. A second call changes nothing and audits nothing.
     */
    @Transactional
    public ImportReviewActionResponse approveHighConfidence(UUID userId, UUID importId, String ipAddress) {
        TransactionImport transactionImport = transactionImportRepository.findByIdAndUserIdForUpdate(importId, userId)
            .orElseThrow(TransactionImportReviewService::notFound);
        if (transactionImport.getStatus() != TransactionImport.Status.REVIEW) {
            throw new ConflictException(NOT_IN_REVIEW_MESSAGE);
        }
        int approved = transactionRepository.approveHighConfidence(userId, importId, HIGH_CONFIDENCE, OffsetDateTime.now());
        if (approved > 0) {
            auditLogService.log(AUDIT_HIGH_CONFIDENCE_APPROVED, userId, ipAddress,
                "{\"import_id\":\"" + importId + "\",\"approved_transactions\":" + approved + "}");
        }
        log.info("Import {}: {} high-confidence transaction(s) approved", importId, approved);
        return new ImportReviewActionResponse(importId, TransactionImport.Status.REVIEW.name(), approved);
    }

    /**
     * Discards the import and soft-deletes its rows, future installments included, so they stop being candidates of the
     * duplicate check and of the installment groups. Under the advisory lock of the target, like the import itself: a
     * file of the same target being saved sees either all of this or none of it. Decision 3A: what another import
     * confirmed goes to it first, and what this one confirmed for another import goes back to being future.
     */
    @Transactional
    public ImportReviewActionResponse discard(UUID userId, UUID importId, String ipAddress) {
        TransactionImport transactionImport = transactionImportRepository.findByIdAndUserId(importId, userId)
            .orElseThrow(TransactionImportReviewService::notFound);
        UUID targetId = transactionImport.getCreditCardId() != null
            ? transactionImport.getCreditCardId() : transactionImport.getAccountId();
        transactionImportRepository.lockTarget(TransactionImportProcessor.lockKey(targetId));
        OffsetDateTime now = OffsetDateTime.now();
        if (transactionImportRepository.markDiscarded(importId, now) == 0) {
            throw new ConflictException(NOT_IN_REVIEW_MESSAGE);
        }
        int transferred = transactionRepository.transferConfirmedInstallments(userId, importId, now);
        int reverted = transactionRepository.revertConfirmedInstallments(userId, importId, now);
        int discarded = transactionRepository.softDeleteByImport(userId, importId, now);
        auditLogService.log(AUDIT_DISCARDED, userId, ipAddress, "{\"import_id\":\"" + importId
            + "\",\"discarded_transactions\":" + discarded + ",\"installments_transferred\":" + transferred
            + ",\"installments_reverted\":" + reverted + "}");
        log.info("Import {} discarded: {} transaction(s) removed, {} installment(s) transferred, {} reverted", importId,
            discarded, transferred, reverted);
        return new ImportReviewActionResponse(importId, TransactionImport.Status.DISCARDED.name(), discarded);
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException(TransactionImportService.NOT_FOUND_MESSAGE);
    }

    /** The categories of {@code rows} and their parents, by id, removed ones included. */
    private Map<UUID, Category> categoriesOf(UUID userId, List<Transaction> rows) {
        Set<UUID> ids = rows.stream().map(Transaction::getCategoryId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Category> categories = new HashMap<>(categoryRepository.findByUserIdAndIdIn(userId, ids).stream()
            .collect(Collectors.toMap(Category::getId, Function.identity())));
        Set<UUID> parents = new HashSet<>();
        for (Category category : categories.values()) {
            if (category.getParentCategoryId() != null && !categories.containsKey(category.getParentCategoryId())) {
                parents.add(category.getParentCategoryId());
            }
        }
        if (!parents.isEmpty()) {
            categoryRepository.findByUserIdAndIdIn(userId, parents).forEach(parent -> categories.put(parent.getId(), parent));
        }
        return categories;
    }

    private static ImportTransactionResponse toResponse(Transaction row, Map<UUID, Category> categories) {
        return new ImportTransactionResponse(row.getId(), row.getTransactionDate(), row.getDescription(), row.getAmount(),
            row.getTransactionType().name(), row.getKind().name(), category(row.getCategoryId(), categories),
            row.getClassificationSource().name(), row.getClassificationConfidence(), row.getReviewStatus().name(),
            row.isPix(), pixDetails(row), row.isInstallment(), row.getInstallmentNumber(), row.getInstallmentTotal(),
            row.isFutureInstallment());
    }

    private static ImportTransactionResponse.Category category(UUID categoryId, Map<UUID, Category> categories) {
        Category category = categoryId == null ? null : categories.get(categoryId);
        if (category == null) {
            return null;
        }
        Category parent = category.getParentCategoryId() == null ? null : categories.get(category.getParentCategoryId());
        return new ImportTransactionResponse.Category(category.getId(), category.getCategoryName(),
            parent == null ? null : parent.getCategoryName());
    }

    /** The stored JSON as an object; never expected to fail (the column is jsonb), and a marker is not worth a 500. */
    private static JsonNode pixDetails(Transaction row) {
        if (row.getPixDetails() == null) {
            return null;
        }
        try {
            return JSON.readTree(row.getPixDetails());
        } catch (JsonProcessingException e) {
            log.warn("Transaction {} has unreadable pix_details", row.getId());
            return null;
        }
    }
}
