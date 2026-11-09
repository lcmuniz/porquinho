package com.porquinho.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One entry of an account or a credit card (Story 5.4, V26). Exactly one of account_id / credit_card_id is set
 * (decision D1). The amount is always positive; the direction is in {@link #transactionType}. Imported entries are
 * born PENDING with {@code affectsBalance = false}: they show up in no list, total or balance until the import is
 * finished (Story 5.10). Pix, installments and classification come in Stories 5.6–5.8; the review, APPROVED (Story 5.9).
 */
@Entity
@Table(name = "transactions")
@DynamicUpdate
public class Transaction {

    public enum Type { DEBIT, CREDIT }

    public enum Kind { REGULAR, CARD_PAYMENT }

    public enum ClassificationSource { AI, PATTERN, USER, NONE }

    public enum ReviewStatus { PENDING, APPROVED, CONFIRMED }

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "credit_card_id")
    private UUID creditCardId;

    @Column(name = "import_id", updatable = false)
    private UUID importId;

    @Column(name = "external_id", length = 255)
    private String externalId;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "original_description", nullable = false, length = 500)
    private String originalDescription;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 10)
    private Type transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private Kind kind = Kind.REGULAR;

    @Column(name = "category_id")
    private UUID categoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "classification_source", nullable = false, length = 10)
    private ClassificationSource classificationSource = ClassificationSource.NONE;

    @Column(name = "classification_confidence")
    private Integer classificationConfidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 20)
    private ReviewStatus reviewStatus = ReviewStatus.PENDING;

    @Column(name = "affects_balance", nullable = false)
    private boolean affectsBalance;

    @Column(name = "is_pix", nullable = false)
    private boolean pix;

    /** Raw JSON (Story 5.6). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pix_details")
    private String pixDetails;

    @Column(name = "is_installment", nullable = false)
    private boolean installment;

    @Column(name = "installment_number")
    private Integer installmentNumber;

    @Column(name = "installment_total")
    private Integer installmentTotal;

    @Column(name = "installment_group_id")
    private UUID installmentGroupId;

    @Column(name = "is_future_installment", nullable = false)
    private boolean futureInstallment;

    /** The import that confirmed this future installment, when another one created it (Story 5.9, decision 3A). */
    @Column(name = "confirmed_by_import_id")
    private UUID confirmedByImportId;

    /** When the user removed the future installments of this purchase (Story 5.12, decision Q2); null otherwise. */
    @Column(name = "installment_futures_removed_at")
    private OffsetDateTime installmentFuturesRemovedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    public Transaction() {
    }

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getAccountId() { return accountId; }
    public void setAccountId(UUID accountId) { this.accountId = accountId; }

    public UUID getCreditCardId() { return creditCardId; }
    public void setCreditCardId(UUID creditCardId) { this.creditCardId = creditCardId; }

    public UUID getImportId() { return importId; }
    public void setImportId(UUID importId) { this.importId = importId; }

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }

    public LocalDate getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDate transactionDate) { this.transactionDate = transactionDate; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getOriginalDescription() { return originalDescription; }
    public void setOriginalDescription(String originalDescription) { this.originalDescription = originalDescription; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public Type getTransactionType() { return transactionType; }
    public void setTransactionType(Type transactionType) { this.transactionType = transactionType; }

    public Kind getKind() { return kind; }
    public void setKind(Kind kind) { this.kind = kind; }

    public UUID getCategoryId() { return categoryId; }
    public void setCategoryId(UUID categoryId) { this.categoryId = categoryId; }

    public ClassificationSource getClassificationSource() { return classificationSource; }
    public void setClassificationSource(ClassificationSource classificationSource) { this.classificationSource = classificationSource; }

    public Integer getClassificationConfidence() { return classificationConfidence; }
    public void setClassificationConfidence(Integer classificationConfidence) { this.classificationConfidence = classificationConfidence; }

    public ReviewStatus getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(ReviewStatus reviewStatus) { this.reviewStatus = reviewStatus; }

    public boolean isAffectsBalance() { return affectsBalance; }
    public void setAffectsBalance(boolean affectsBalance) { this.affectsBalance = affectsBalance; }

    public boolean isPix() { return pix; }
    public void setPix(boolean pix) { this.pix = pix; }

    public String getPixDetails() { return pixDetails; }
    public void setPixDetails(String pixDetails) { this.pixDetails = pixDetails; }

    public boolean isInstallment() { return installment; }
    public void setInstallment(boolean installment) { this.installment = installment; }

    public Integer getInstallmentNumber() { return installmentNumber; }
    public void setInstallmentNumber(Integer installmentNumber) { this.installmentNumber = installmentNumber; }

    public Integer getInstallmentTotal() { return installmentTotal; }
    public void setInstallmentTotal(Integer installmentTotal) { this.installmentTotal = installmentTotal; }

    public UUID getInstallmentGroupId() { return installmentGroupId; }
    public void setInstallmentGroupId(UUID installmentGroupId) { this.installmentGroupId = installmentGroupId; }

    public boolean isFutureInstallment() { return futureInstallment; }
    public void setFutureInstallment(boolean futureInstallment) { this.futureInstallment = futureInstallment; }

    public UUID getConfirmedByImportId() { return confirmedByImportId; }
    public void setConfirmedByImportId(UUID confirmedByImportId) { this.confirmedByImportId = confirmedByImportId; }

    public OffsetDateTime getInstallmentFuturesRemovedAt() { return installmentFuturesRemovedAt; }
    public void setInstallmentFuturesRemovedAt(OffsetDateTime installmentFuturesRemovedAt) { this.installmentFuturesRemovedAt = installmentFuturesRemovedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; }
}
