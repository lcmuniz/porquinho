package com.porquinho.dto;

import java.util.List;

/**
 * GET /api/v1/dashboard/layer3 (Story 7.6): page {@code page} (0-based) of the user's confirmed transactions that match
 * the filters, 50 per page. A page past the end comes empty.
 */
public record DashboardLayer3Response(
    List<TransactionResponse> items,
    int page,
    long totalItems,
    int totalPages
) {
}
