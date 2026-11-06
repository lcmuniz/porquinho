package com.porquinho.dto;

import java.util.UUID;

/**
 * Result of an action on the review of an import (Story 5.9): the import status afterwards and how many of its rows
 * were approved or discarded.
 */
public record ImportReviewActionResponse(UUID importId, String status, int affectedTransactions) {
}
