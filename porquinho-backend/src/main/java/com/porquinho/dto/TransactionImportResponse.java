package com.porquinho.dto;

import com.porquinho.entity.TransactionImport;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Import returned by /api/v1/imports (Story 5.1), with the name of the target account or card (removed ones
 * included, so the history stays readable). Exactly one of accountId / creditCardId is set.
 */
public record TransactionImportResponse(
    UUID id,
    UUID accountId,
    UUID creditCardId,
    String targetName,
    String fileName,
    String fileType,
    String bankName,
    int fileSizeKb,
    String status,
    int totalTransactions,
    int importedTransactions,
    int duplicatesFound,
    int transfersFound,
    int skippedRows,
    String errorMessage,
    OffsetDateTime createdAt
) {
    public static TransactionImportResponse of(TransactionImport transactionImport, String targetName) {
        return new TransactionImportResponse(
            transactionImport.getId(),
            transactionImport.getAccountId(),
            transactionImport.getCreditCardId(),
            targetName,
            transactionImport.getFileName(),
            transactionImport.getFileType().name(),
            transactionImport.getBankName(),
            transactionImport.getFileSizeKb(),
            transactionImport.getStatus().name(),
            transactionImport.getTotalTransactions(),
            transactionImport.getImportedTransactions(),
            transactionImport.getDuplicatesFound(),
            transactionImport.getTransfersFound(),
            transactionImport.getSkippedRows(),
            transactionImport.getErrorMessage(),
            transactionImport.getCreatedAt()
        );
    }
}
