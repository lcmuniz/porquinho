package com.porquinho.dto;

import java.math.BigDecimal;

/**
 * What removing a child category takes along (retro Epic 6 A4, Pre-10.3): its active transactions and its allocation
 * of the current month and of the future ones.
 */
public record CategoryUsageResponse(long transactionCount, BigDecimal allocatedAmount) {
}
