package com.porquinho.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One uploaded bank statement (Story 5.1, V25). The target is an account OR a credit card of the user (decisions
 * D1/D2 of Epic 5). The file content is never stored: only its name, type and size. Story 5.1 only records the
 * upload in PROCESSING; reading the entries and the other statuses come with Stories 5.3–5.10.
 */
@Entity
@Table(name = "transaction_imports")
public class TransactionImport {

    public enum FileType { OFX, CSV }

    public enum Status { PROCESSING, REVIEW, COMPLETED, FAILED, DISCARDED }

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "account_id", updatable = false)
    private UUID accountId;

    @Column(name = "credit_card_id", updatable = false)
    private UUID creditCardId;

    @Column(name = "file_name", nullable = false, updatable = false, length = 255)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false, updatable = false, length = 10)
    private FileType fileType;

    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Column(name = "file_size_kb", nullable = false, updatable = false)
    private int fileSizeKb;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.PROCESSING;

    @Column(name = "total_transactions", nullable = false)
    private int totalTransactions;

    @Column(name = "imported_transactions", nullable = false)
    private int importedTransactions;

    @Column(name = "duplicates_found", nullable = false)
    private int duplicatesFound;

    @Column(name = "skipped_rows", nullable = false)
    private int skippedRows;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public TransactionImport() {
    }

    public TransactionImport(UUID userId, UUID accountId, UUID creditCardId, String fileName, FileType fileType,
                             int fileSizeKb) {
        this.userId = userId;
        this.accountId = accountId;
        this.creditCardId = creditCardId;
        this.fileName = fileName;
        this.fileType = fileType;
        this.fileSizeKb = fileSizeKb;
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

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public FileType getFileType() { return fileType; }
    public void setFileType(FileType fileType) { this.fileType = fileType; }

    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }

    public int getFileSizeKb() { return fileSizeKb; }
    public void setFileSizeKb(int fileSizeKb) { this.fileSizeKb = fileSizeKb; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public int getTotalTransactions() { return totalTransactions; }
    public void setTotalTransactions(int totalTransactions) { this.totalTransactions = totalTransactions; }

    public int getImportedTransactions() { return importedTransactions; }
    public void setImportedTransactions(int importedTransactions) { this.importedTransactions = importedTransactions; }

    public int getDuplicatesFound() { return duplicatesFound; }
    public void setDuplicatesFound(int duplicatesFound) { this.duplicatesFound = duplicatesFound; }

    public int getSkippedRows() { return skippedRows; }
    public void setSkippedRows(int skippedRows) { this.skippedRows = skippedRows; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
