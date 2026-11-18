package com.porquinho.service;

/**
 * Story 10.2: why an import failed, read from its error message (always one of the processor's fixed messages). The
 * reader has no failure of its own for encoding, so there is no encoding type.
 */
public enum ImportErrorType {
    FORMAT("Problema de formato"),
    PARSING("Nenhuma transação lida"),
    INTERRUPTED("Interrompida"),
    UNEXPECTED("Erro inesperado"),
    OTHER("Outro");

    private final String label;

    ImportErrorType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static ImportErrorType of(String message) {
        if (message == null) return OTHER;
        if (message.equals(TransactionImportService.UNKNOWN_CSV_MESSAGE)
            || message.equals(TransactionImportProcessor.TRUNCATED_MESSAGE)) return FORMAT;
        if (message.equals(TransactionImportProcessor.NO_TRANSACTIONS_MESSAGE)) return PARSING;
        if (message.equals(TransactionImportProcessor.INTERRUPTED_MESSAGE)) return INTERRUPTED;
        if (message.equals(TransactionImportProcessor.UNEXPECTED_ERROR_MESSAGE)) return UNEXPECTED;
        return OTHER;
    }
}
