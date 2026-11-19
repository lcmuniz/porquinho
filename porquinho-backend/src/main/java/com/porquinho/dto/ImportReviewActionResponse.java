package com.porquinho.dto;

import java.util.UUID;

/**
 * Result of an action on the review of an import (Story 5.9): the import status afterwards and how many of its rows
 * were approved or discarded. On the conclusion of an account import, {@code beforeBalanceTransactions} counts the rows
 * dated up to the balance reference, which do not change the balance; 0 in every other action.
 */
public record ImportReviewActionResponse(UUID importId, String status, int affectedTransactions,
                                         int beforeBalanceTransactions) {

    public ImportReviewActionResponse(UUID importId, String status, int affectedTransactions) {
        this(importId, status, affectedTransactions, 0);
    }
}
